package com.kce.kmrl.user.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "pending_registrations", uniqueConstraints = {
        @UniqueConstraint(columnNames = "username"),
        @UniqueConstraint(columnNames = "email")
})
public class PendingRegistration {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "pending_reg_seq_gen")
    @SequenceGenerator(name = "pending_reg_seq_gen", sequenceName = "pending_registrations_seq", allocationSize = 1)
    private Long id;

    @Column(nullable = false)
    private String username;

    @Column(nullable = false)
    private String email;

    @Column(nullable = false)
    private String password;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ERole requestedRole;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RegistrationStatus status = RegistrationStatus.PENDING;

    @Column(name = "approval_task_id")
    private String approvalTaskId;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    public PendingRegistration() {}

    public PendingRegistration(String username, String email, String password, ERole requestedRole) {
        this.username = username;
        this.email = email;
        this.password = password;
        this.requestedRole = requestedRole;
        this.createdAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }

    public ERole getRequestedRole() { return requestedRole; }
    public void setRequestedRole(ERole requestedRole) { this.requestedRole = requestedRole; }

    public RegistrationStatus getStatus() { return status; }
    public void setStatus(RegistrationStatus status) { this.status = status; }

    public String getApprovalTaskId() { return approvalTaskId; }
    public void setApprovalTaskId(String approvalTaskId) { this.approvalTaskId = approvalTaskId; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
