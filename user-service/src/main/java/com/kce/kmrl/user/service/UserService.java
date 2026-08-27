package com.kce.kmrl.user.service;

import com.kce.kmrl.user.dto.ChangePasswordRequest;
import com.kce.kmrl.user.dto.ForgotPasswordRequest;
import com.kce.kmrl.user.dto.LoginRequest;
import com.kce.kmrl.user.dto.ResetPasswordRequest;
import com.kce.kmrl.user.dto.SignupRequest;
import com.kce.kmrl.user.dto.UpdateProfileRequest;
import com.kce.kmrl.user.dto.UserManagementDTO;
import com.kce.kmrl.user.entity.User;

import java.util.List;
import java.util.Optional;

public interface UserService {

    Optional<User> authenticateUser(LoginRequest loginRequest);

    void submitRegistration(SignupRequest signupRequest);

    void approveRegistration(Long pendingRegistrationId);

    void rejectRegistration(Long pendingRegistrationId, String reason);

    void processForgotPassword(ForgotPasswordRequest request, String frontendUrl) throws Exception;

    void processResetPassword(ResetPasswordRequest request) throws Exception;

    Optional<User> findById(Long id);

    User updateProfile(Long userId, UpdateProfileRequest request);

    void changePassword(Long userId, ChangePasswordRequest request);

    List<UserManagementDTO> listAllUsers();

    UserManagementDTO changeUserRole(Long userId, String newRole);

    UserManagementDTO setUserActive(Long userId, boolean active);
}