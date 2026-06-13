package com.hospital.msauth.service;

import com.hospital.msauth.dto.request.LoginRequestDTO;
import com.hospital.msauth.dto.request.RegisterCredentialRequestDTO;
import com.hospital.msauth.dto.response.LoginResponseDTO;
import com.hospital.msauth.dto.response.TokenValidationResponseDTO;
import com.hospital.msauth.entity.UserCredential;
import com.hospital.msauth.entity.enums.UserRole;
import com.hospital.msauth.exception.CredentialAlreadyExistsException;
import com.hospital.msauth.exception.InvalidCredentialsException;
import com.hospital.msauth.repository.CredentialRepository;
import java.util.UUID;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {

    private final CredentialRepository credentialRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

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

        credentialRepository.save(Objects.requireNonNull(credential));
        log.info("Credenciales registradas para userId: {}", dto.getUserId());
    }

    public LoginResponseDTO login(LoginRequestDTO dto) {
        UserCredential credential = credentialRepository.findByEmail(dto.getEmail())
                .orElseThrow(() -> new InvalidCredentialsException("Credenciales invalidas"));

        if (!credential.getIsActive()) {
            throw new InvalidCredentialsException("Usuario inactivo");
        }

        if (!passwordEncoder.matches(dto.getPassword(), credential.getPassword())) {
            throw new InvalidCredentialsException("Credenciales invalidas");
        }

        String token = jwtService.generateToken(credential);

        log.info("Login exitoso para: {}", dto.getEmail());

        return LoginResponseDTO.builder()
                .token(token)
                .userId(credential.getUserId())
                .email(credential.getEmail())
                .role(credential.getRole().name())
                .build();
    }

    public TokenValidationResponseDTO validateToken(String token) {
        try {
            Jwt jwt = jwtService.decode(token);

            String userIdValue = jwt.getClaimAsString("userId");
            UUID userId = userIdValue != null ? UUID.fromString(userIdValue) : null;

            return TokenValidationResponseDTO.builder()
                    .valid(true)
                    .email(jwt.getSubject())
                    .role(jwt.getClaimAsString("role"))
                    .userId(userId)
                    .build();
        } catch (RuntimeException e) {
            return TokenValidationResponseDTO.builder().valid(false).build();
        }
    }
}
