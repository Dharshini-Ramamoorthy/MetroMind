package com.kce.kmrl.user.util;

public class UserConstants {
    public static final String DEFAULT_ROLE = "OC";
    public static final String MSG_USER_REGISTERED = "User registered successfully!";
    public static final String ERR_USERNAME_TAKEN = "Error: Username is already taken!";
    public static final String ERR_EMAIL_TAKEN = "Error: Email is already in use!";
    public static final String ERR_USER_NOT_FOUND = "Error: User not found.";

    public static final String MSG_REGISTRATION_SUBMITTED =
        "Registration submitted! An administrator will review your request, and " +
        "you'll get an email once it's approved or rejected.";
    public static final String ERR_USERNAME_PENDING =
        "Error: A registration request for this username is already pending approval.";
    public static final String ERR_EMAIL_PENDING =
        "Error: A registration request for this email is already pending approval.";

    public static final String ERR_CANNOT_SELF_REGISTER_AS_ADMIN =
        "Error: The System Admin role cannot be self-registered. Contact an existing administrator.";
    public static final String ERR_REGISTRATION_NOT_FOUND = "Error: Registration request not found.";
    public static final String ERR_REGISTRATION_ALREADY_DECIDED =
        "Error: This registration request has already been decided.";
}