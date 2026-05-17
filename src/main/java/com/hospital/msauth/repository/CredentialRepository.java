package com.hospital.msauth.repository;

import com.hospital.msauth.entity.UserCredential;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

// Patron Repository: abstrae el acceso a credenciales en DB
@Repository
public interface CredentialRepository extends JpaRepository<UserCredential, UUID> {

    Optional<UserCredential> findByEmail(String email);

    boolean existsByEmail(String email);
}
