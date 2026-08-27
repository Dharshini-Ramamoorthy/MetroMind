package com.kce.kmrl.user.repository;

import com.kce.kmrl.user.entity.PendingRegistration;
import com.kce.kmrl.user.entity.RegistrationStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PendingRegistrationRepository extends JpaRepository<PendingRegistration, Long> {

    @Query("SELECT CASE WHEN COUNT(p) > 0 THEN true ELSE false END FROM PendingRegistration p " +
           "WHERE p.username = :username AND p.status = com.kce.kmrl.user.entity.RegistrationStatus.PENDING")
    Boolean existsPendingByUsername(@Param("username") String username);

    @Query("SELECT CASE WHEN COUNT(p) > 0 THEN true ELSE false END FROM PendingRegistration p " +
           "WHERE p.email = :email AND p.status = com.kce.kmrl.user.entity.RegistrationStatus.PENDING")
    Boolean existsPendingByEmail(@Param("email") String email);

    Optional<PendingRegistration> findByIdAndStatus(Long id, RegistrationStatus status);
}
