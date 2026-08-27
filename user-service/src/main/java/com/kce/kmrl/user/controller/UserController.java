package com.kce.kmrl.user.controller;

import com.kce.kmrl.user.dto.ChangePasswordRequest;
import com.kce.kmrl.user.dto.ForgotPasswordRequest;
import com.kce.kmrl.user.dto.LoginRequest;
import com.kce.kmrl.user.dto.ResetPasswordRequest;
import com.kce.kmrl.user.dto.SignupRequest;
import com.kce.kmrl.user.dto.UpdateProfileRequest;
import com.kce.kmrl.user.entity.User;
import com.kce.kmrl.user.repository.UserRepository;
import com.kce.kmrl.user.security.jwt.JwtUtils;
import com.kce.kmrl.user.service.UserService;
import com.kce.kmrl.user.util.UserConstants;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/v1/auth")
public class UserController {

    @Autowired
    private UserService userService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JwtUtils jwtUtils;

    @Value("${app.internal-secret:local-dev-internal-secret-change-me}")
    private String internalSecret;

    private boolean isValidInternalSecret(String provided) {
        return provided != null && !provided.isBlank() && provided.equals(internalSecret);
    }

    private Optional<User> resolveUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.isAuthenticated()
                && authentication.getPrincipal() instanceof UserDetails userDetails) {
            return userRepository.findByUsername(userDetails.getUsername());
        }
        return Optional.empty();
    }

    @PostMapping("/register")
    public ResponseEntity<?> registerUser(@Valid @RequestBody SignupRequest signUpRequest) {
        try {
            userService.submitRegistration(signUpRequest);
            return ResponseEntity.ok(Map.of("message", UserConstants.MSG_REGISTRATION_SUBMITTED));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    @PostMapping("/internal/registrations/{id}/approve")
    public ResponseEntity<?> approveRegistration(
            @PathVariable Long id,
            @RequestHeader(value = "X-Internal-Secret", required = false) String secret) {
        if (!isValidInternalSecret(secret)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("message", "Forbidden."));
        }
        try {
            userService.approveRegistration(id);
            return ResponseEntity.ok(Map.of("message", "Registration approved."));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    @PostMapping("/internal/registrations/{id}/reject")
    public ResponseEntity<?> rejectRegistration(
            @PathVariable Long id,
            @RequestBody(required = false) Map<String, String> body,
            @RequestHeader(value = "X-Internal-Secret", required = false) String secret) {
        if (!isValidInternalSecret(secret)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("message", "Forbidden."));
        }
        try {
            String reason = body != null ? body.get("reason") : null;
            userService.rejectRegistration(id, reason);
            return ResponseEntity.ok(Map.of("message", "Registration rejected."));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    @PostMapping("/login")
    public ResponseEntity<?> authenticateUser(@Valid @RequestBody LoginRequest loginRequest) {
        try {
            Optional<User> userOpt = userService.authenticateUser(loginRequest);

            if (userOpt.isPresent()) {
                User user = userOpt.get();
                String jwtToken = jwtUtils.generateTokenForUsername(user.getUsername(), user.getRole().name());

                return ResponseEntity.ok(Map.of(
                    "message", "Login successful",
                    "token", jwtToken,
                    "user", Map.of(
                        "id", user.getId(),
                        "username", user.getUsername(),
                        "email", user.getEmail(),
                        "role", user.getRole().name()
                    )
                ));
            }

            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("message", "Invalid username/email or password."));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("message", "Login error: " + e.getMessage(), "error", e.getClass().getName()));
        }
    }

    @GetMapping("/me")
    public ResponseEntity<?> getMyProfile() {
        Optional<User> userOpt = resolveUser();
        if (userOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("message", "Not authenticated. Please sign in."));
        }
        User user = userOpt.get();

        Optional<User> freshUser = userService.findById(user.getId());
        if (freshUser.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("message", UserConstants.ERR_USER_NOT_FOUND));
        }

        User u = freshUser.get();
        return ResponseEntity.ok(Map.of(
            "id", u.getId(),
            "username", u.getUsername(),
            "email", u.getEmail(),
            "role", u.getRole().name()
        ));
    }

    @PutMapping("/me")
    public ResponseEntity<?> updateMyProfile(@Valid @RequestBody UpdateProfileRequest request) {
        Optional<User> userOpt = resolveUser();
        if (userOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("message", "Not authenticated. Please sign in."));
        }
        User user = userOpt.get();

        try {
            User updated = userService.updateProfile(user.getId(), request);

            return ResponseEntity.ok(Map.of(
                "message", "Profile updated successfully!",
                "user", Map.of(
                    "id", updated.getId(),
                    "username", updated.getUsername(),
                    "email", updated.getEmail(),
                    "role", updated.getRole().name()
                )
            ));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    @PutMapping("/me/password")
    public ResponseEntity<?> changePassword(@Valid @RequestBody ChangePasswordRequest request) {
        Optional<User> userOpt = resolveUser();
        if (userOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("message", "Not authenticated. Please sign in."));
        }

        try {
            userService.changePassword(userOpt.get().getId(), request);
            return ResponseEntity.ok(Map.of("message", "Password changed successfully!"));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    @GetMapping("/session")
    public ResponseEntity<?> checkSession() {
        Optional<User> userOpt = resolveUser();
        if (userOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("authenticated", false, "message", "No active session."));
        }

        User user = userOpt.get();
        return ResponseEntity.ok(Map.of(
            "authenticated", true,
            "user", Map.of(
                "id", user.getId(),
                "username", user.getUsername(),
                "email", user.getEmail(),
                "role", user.getRole().name()
            )
        ));
    }

    @PostMapping("/logout")
    public ResponseEntity<?> logout() {
        return ResponseEntity.ok(Map.of("message", "Logged out successfully."));
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<?> forgotPassword(
            @Valid @RequestBody ForgotPasswordRequest request,
            HttpServletRequest httpRequest) {
        try {
            String origin = httpRequest.getHeader("Origin");
            String frontendUrl = (origin != null && !origin.isBlank()) ? origin : "http://localhost:5173";

            userService.processForgotPassword(request, frontendUrl);

            return ResponseEntity.ok(Map.of("message", "If an account exists for that email, a password reset link has been sent."));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    @PostMapping("/reset-password")
    public ResponseEntity<?> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        try {
            userService.processResetPassword(request);
            return ResponseEntity.ok(Map.of("message", "Password has been reset successfully!"));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("message", "An unexpected error occurred while resetting the password."));
        }
    }
}