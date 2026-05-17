package com.hospital.msauth.client;

import com.hospital.msauth.dto.request.LoginRequestDTO;
import com.hospital.msauth.dto.request.RegisterCredentialRequestDTO;
import com.hospital.msauth.dto.response.AuthResponseDTO;
import com.hospital.msauth.dto.response.TokenValidationResponseDTO;
import com.hospital.msauth.entity.UserCredential;
import com.hospital.msauth.entity.enums.UserRole;
import com.hospital.msauth.exception.CredentialAlreadyExistsException;
import com.hospital.msauth.exception.InvalidCredentialsException;
import com.hospital.msauth.repository.CredentialRepository;
import com.hospital.msauth.util.JwtUtil;
import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.UUID;

/*
 * Patron Facade: oculta la complejidad de BCrypt, JWT y el repositorio
 * detras de metodos simples para el Controller.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AuthClient {

    private final CredentialRepository credentialRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;

    public void registerCredential(RegisterCredentialRequestDTO dto) {
        if (credentialRepository.existsByEmail(dto.getEmail())) {
            throw new CredentialAlreadyExistsException("Credenciales ya registradas: " + dto.getEmail());
        }

        UserCredential credential = UserCredential.builder()
                .email(dto.getEmail())
                .password(passwordEncoder.encode(dto.getPassword()))
                .role(UserRole.valueOf(dto.getRole()))
                .userId(dto.getUserId())
                .build();

        credentialRepository.save(credential);
        log.info("Credenciales registradas para userId: {}", dto.getUserId());
    }

    public AuthResponseDTO login(LoginRequestDTO dto) {
        UserCredential credential = credentialRepository.findByEmail(dto.getEmail())
                .orElseThrow(() -> new InvalidCredentialsException("Credenciales invalidas"));

        if (!credential.getIsActive()) {
            throw new InvalidCredentialsException("Usuario inactivo");
        }

        if (!passwordEncoder.matches(dto.getPassword(), credential.getPassword())) {
            throw new InvalidCredentialsException("Credenciales invalidas");
        }

        String token = jwtUtil.generateToken(
                credential.getEmail(),
                credential.getRole().name(),
                credential.getUserId()
        );

        log.info("Login exitoso para: {}", dto.getEmail());

        return AuthResponseDTO.builder()
                .token(token)
                .userId(credential.getUserId())
                .email(credential.getEmail())
                .role(credential.getRole().name())
                .build();
    }

    public TokenValidationResponseDTO validateToken(String token) {
        if (!jwtUtil.isTokenValid(token)) {
            return TokenValidationResponseDTO.builder().valid(false).build();
        }

        Claims claims = jwtUtil.extractClaims(token);

        return TokenValidationResponseDTO.builder()
                .valid(true)
                .email(claims.getSubject())
                .role(claims.get("role", String.class))
                .userId(UUID.fromString(claims.get("userId", String.class)))
                .build();
    }
}
