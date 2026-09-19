package com.kce.kmrl.user.config;

import com.kce.kmrl.user.entity.ERole;
import com.kce.kmrl.user.entity.User;
import com.kce.kmrl.user.repository.UserRepository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public class AdminAccountSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminAccountSeeder.class);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.admin.username:admin}")
    private String adminUsername;

    @Value("${app.admin.email:admin@kmrl.co.in}")
    private String adminEmail;

    @Value("${app.admin.password:ChangeMe123!}")
    private String adminPassword;

    public AdminAccountSeeder(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(String... args) {
        boolean adminExists = userRepository.existsByRole(ERole.ADMIN);
        if (adminExists) {
            return;
        }

        Optional<User> existingUser = userRepository.findByUsername(adminUsername);
        if (existingUser.isEmpty()) {
            existingUser = userRepository.findByEmail(adminEmail);
        }

        if (existingUser.isPresent()) {
            User admin = existingUser.get();
            admin.setRole(ERole.ADMIN);
            userRepository.save(admin);
            log.info("Updated existing user '{}' to ADMIN role.", admin.getUsername());
        } else {
            User admin = new User();
            admin.setUsername(adminUsername);
            admin.setEmail(adminEmail);
            admin.setPassword(passwordEncoder.encode(adminPassword));
            admin.setRole(ERole.ADMIN);
            admin.setActive(true);
            userRepository.save(admin);
            log.info("No ADMIN account existed - seeded new system admin (username='{}').", adminUsername);
        }
    }
}
