package com.kce.kmrl.user.service;

import com.kce.kmrl.user.dto.ChangePasswordRequest;
import com.kce.kmrl.user.dto.ForgotPasswordRequest;
import com.kce.kmrl.user.dto.LoginRequest;
import com.kce.kmrl.user.dto.ResetPasswordRequest;
import com.kce.kmrl.user.dto.SignupRequest;
import com.kce.kmrl.user.dto.UpdateProfileRequest;
import com.kce.kmrl.user.entity.ERole;
import com.kce.kmrl.user.entity.PendingRegistration;
import com.kce.kmrl.user.entity.RegistrationStatus;
import com.kce.kmrl.user.entity.User;
import com.kce.kmrl.user.repository.PendingRegistrationRepository;
import com.kce.kmrl.user.repository.UserRepository;
import com.kce.kmrl.user.util.UserConstants;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
public class UserServiceImpl implements UserService {

    private static final Logger log = LoggerFactory.getLogger(UserServiceImpl.class);

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PendingRegistrationRepository pendingRegistrationRepository;

    @Autowired
    private EmailService emailService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private RestTemplate restTemplate;

    @Value("${approver.service.url:http://localhost:8087}")
    private String approverServiceUrl;

    @Value("${app.frontend-url:http://localhost:5173}")
    private String defaultFrontendUrl;

    @Override
    public Optional<User> authenticateUser(LoginRequest loginRequest) {
        if (loginRequest.getUsername() == null || loginRequest.getPassword() == null) {
            return Optional.empty();
        }

        String identifier = loginRequest.getUsername().trim();
        String rawPassword = loginRequest.getPassword().trim();

        Optional<User> userOpt = userRepository.findByUsername(identifier);
        if (userOpt.isEmpty()) {
            userOpt = userRepository.findByEmail(identifier);
        }

        if (userOpt.isPresent()) {
            User user = userOpt.get();
            if (!user.isActive()) {
                return Optional.empty();
            }
            if (passwordEncoder.matches(rawPassword, user.getPassword())) {
                return Optional.of(user);
            }
        }

        return Optional.empty();
    }

    @Override
    public void submitRegistration(SignupRequest signUpRequest) {
        String username = signUpRequest.getUsername().trim();
        String email = signUpRequest.getEmail().trim();

        ERole requestedRole = resolveRole(signUpRequest.getRole());
        if (requestedRole == ERole.ADMIN) {
            throw new RuntimeException("Self-registration as ADMIN is not permitted.");
        }

        if (userRepository.existsByUsername(username)) {
            throw new RuntimeException(UserConstants.ERR_USERNAME_TAKEN);
        }
        if (userRepository.existsByEmail(email)) {
            throw new RuntimeException(UserConstants.ERR_EMAIL_TAKEN);
        }

        if (Boolean.TRUE.equals(pendingRegistrationRepository.existsPendingByUsername(username))) {
            throw new RuntimeException(UserConstants.ERR_USERNAME_PENDING);
        }
        if (Boolean.TRUE.equals(pendingRegistrationRepository.existsPendingByEmail(email))) {
            throw new RuntimeException(UserConstants.ERR_EMAIL_PENDING);
        }

        PendingRegistration pending = new PendingRegistration(
                username, email, passwordEncoder.encode(signUpRequest.getPassword().trim()), requestedRole);
        pending = pendingRegistrationRepository.save(pending);

        submitApprovalTask(pending);
    }

    private void submitApprovalTask(PendingRegistration pending) {
        try {
            Map<String, Object> payload = new HashMap<>();
            payload.put("targetEntityId", String.valueOf(pending.getId()));
            payload.put("requestType", "USER_REGISTRATION");
            payload.put("title", "New Registration: " + pending.getUsername() + " (" + pending.getRequestedRole() + ")");
            payload.put("description",
                    "Username: " + pending.getUsername() +
                    " | Email: " + pending.getEmail() +
                    " | Requested role: " + pending.getRequestedRole());
            payload.put("priority", "MEDIUM");
            payload.put("requestedBy", pending.getUsername());

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(org.springframework.http.MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(payload, headers);

            restTemplate.postForEntity(approverServiceUrl + "/api/approver/tasks/submit", entity, Void.class);
        } catch (Exception e) {
            log.warn("Failed to submit USER_REGISTRATION approval task for pending registration {} ({}): {}",
                    pending.getId(), e.getClass().getSimpleName(), e.getMessage());
        }
    }

    @Override
    public void approveRegistration(Long pendingRegistrationId) {
        PendingRegistration pending = pendingRegistrationRepository.findByIdAndStatus(pendingRegistrationId, RegistrationStatus.PENDING)
                .orElseThrow(() -> new RuntimeException(
                        pendingRegistrationRepository.existsById(pendingRegistrationId)
                                ? UserConstants.ERR_REGISTRATION_ALREADY_DECIDED
                                : UserConstants.ERR_REGISTRATION_NOT_FOUND));

        if (userRepository.existsByUsername(pending.getUsername())) {
            throw new RuntimeException(UserConstants.ERR_USERNAME_TAKEN);
        }
        if (userRepository.existsByEmail(pending.getEmail())) {
            throw new RuntimeException(UserConstants.ERR_EMAIL_TAKEN);
        }

        User user = new User();
        user.setUsername(pending.getUsername());
        user.setEmail(pending.getEmail());

        user.setPassword(pending.getPassword());
        user.setRole(pending.getRequestedRole());
        userRepository.save(user);

        pending.setStatus(RegistrationStatus.APPROVED);
        pendingRegistrationRepository.save(pending);

        try {
            emailService.sendRegistrationApprovedEmail(pending.getEmail(), pending.getUsername(), defaultFrontendUrl + "/signin");
        } catch (Exception e) {
            log.error("Registration-approved email failed for {}: {}: {}",
                    pending.getEmail(), e.getClass().getName(), e.getMessage(), e);
        }
    }

    @Override
    public void rejectRegistration(Long pendingRegistrationId, String reason) {
        PendingRegistration pending = pendingRegistrationRepository.findByIdAndStatus(pendingRegistrationId, RegistrationStatus.PENDING)
                .orElseThrow(() -> new RuntimeException(
                        pendingRegistrationRepository.existsById(pendingRegistrationId)
                                ? UserConstants.ERR_REGISTRATION_ALREADY_DECIDED
                                : UserConstants.ERR_REGISTRATION_NOT_FOUND));

        pending.setStatus(RegistrationStatus.REJECTED);
        pendingRegistrationRepository.save(pending);

        try {
            emailService.sendRegistrationRejectedEmail(pending.getEmail(), pending.getUsername(), reason);
        } catch (Exception e) {
            log.error("Registration-rejected email failed for {}: {}: {}",
                    pending.getEmail(), e.getClass().getName(), e.getMessage(), e);
        }
    }

    private ERole resolveRole(String requestedRole) {
        String strRole = (requestedRole != null && !requestedRole.isBlank())
                ? requestedRole.trim()
                : UserConstants.DEFAULT_ROLE;

        try {
            return ERole.valueOf(strRole.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new RuntimeException("Error: Invalid role '" + requestedRole + "'. Must be one of: OC, MDS, SADA.");
        }
    }

    @Override
    public void processForgotPassword(ForgotPasswordRequest request, String frontendUrl) throws Exception {
        Optional<User> userOptional = userRepository.findByEmail(request.getEmail().trim());

        if (userOptional.isPresent()) {
            User user = userOptional.get();
            String token = UUID.randomUUID().toString();
            user.setResetPasswordToken(token);
            user.setResetPasswordExpires(LocalDateTime.now().plusMinutes(15));
            userRepository.save(user);

            String resetUrl = frontendUrl + "/reset-password?token=" + token;
            emailService.sendResetPasswordEmail(user.getEmail(), resetUrl);
        }
    }

    @Override
    public void processResetPassword(ResetPasswordRequest request) throws Exception {
        Optional<User> userOptional = userRepository.findByResetPasswordToken(request.getToken());

        if (userOptional.isEmpty()) {
            throw new IllegalArgumentException("Error: Invalid or expired reset token.");
        }

        User user = userOptional.get();

        if (user.getResetPasswordExpires() == null || user.getResetPasswordExpires().isBefore(LocalDateTime.now())) {
            throw new IllegalArgumentException("Error: Reset link has expired. Please request a new one.");
        }

        user.setPassword(passwordEncoder.encode(request.getNewPassword().trim()));
        user.setResetPasswordToken(null);
        user.setResetPasswordExpires(null);
        userRepository.save(user);
    }

    @Override
    public Optional<User> findById(Long id) {
        return userRepository.findById(id);
    }

    @Override
    public User updateProfile(Long userId, UpdateProfileRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException(UserConstants.ERR_USER_NOT_FOUND));

        if (request.getUsername() != null && !request.getUsername().isBlank()) {
            String newUsername = request.getUsername().trim();
            if (!newUsername.equals(user.getUsername())) {
                if (userRepository.existsByUsername(newUsername)) {
                    throw new RuntimeException("Error: Username '" + newUsername + "' is already taken!");
                }
                user.setUsername(newUsername);
            }
        }
        if (request.getEmail() != null && !request.getEmail().isBlank()) {
            String newEmail = request.getEmail().trim();
            if (!newEmail.equals(user.getEmail())) {
                if (userRepository.existsByEmail(newEmail)) {
                    throw new RuntimeException("Error: Email '" + newEmail + "' is already in use!");
                }
                user.setEmail(newEmail);
            }
        }

        return userRepository.save(user);
    }

    @Override
    public void changePassword(Long userId, ChangePasswordRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException(UserConstants.ERR_USER_NOT_FOUND));

        if (!passwordEncoder.matches(request.getCurrentPassword(), user.getPassword())) {
            throw new RuntimeException("Error: Current password is incorrect.");
        }

        user.setPassword(passwordEncoder.encode(request.getNewPassword().trim()));
        userRepository.save(user);
    }

    @Override
    public java.util.List<com.kce.kmrl.user.dto.UserManagementDTO> listAllUsers() {
        return userRepository.findAll().stream()
                .map(this::toManagementDTO)
                .collect(java.util.stream.Collectors.toList());
    }

    @Override
    public com.kce.kmrl.user.dto.UserManagementDTO changeUserRole(Long userId, String newRole) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException(UserConstants.ERR_USER_NOT_FOUND));
        ERole role;
        try {
            role = ERole.valueOf(newRole.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new RuntimeException("Error: Invalid role '" + newRole + "'. Must be one of: ADMIN, OC, MDS, SADA.");
        }
        user.setRole(role);
        return toManagementDTO(userRepository.save(user));
    }

    @Override
    public com.kce.kmrl.user.dto.UserManagementDTO setUserActive(Long userId, boolean active) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException(UserConstants.ERR_USER_NOT_FOUND));
        user.setActive(active);
        return toManagementDTO(userRepository.save(user));
    }

    private com.kce.kmrl.user.dto.UserManagementDTO toManagementDTO(User u) {
        return new com.kce.kmrl.user.dto.UserManagementDTO(
                u.getId(), u.getUsername(), u.getEmail(),
                u.getRole().name(), u.isActive());
    }
}