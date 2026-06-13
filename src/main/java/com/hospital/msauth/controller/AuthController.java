package com.hospital.msauth.controller;

import com.hospital.msauth.dto.request.LoginRequestDTO;
import com.hospital.msauth.dto.request.RegisterCredentialRequestDTO;
import com.hospital.msauth.dto.response.LoginResponseDTO;
import com.hospital.msauth.dto.response.TokenValidationResponseDTO;
import com.hospital.msauth.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    // Llamado internamente por MS-USER al registrar un usuario
    @PostMapping("/register")
    public ResponseEntity<Void> register(@Valid @RequestBody RegisterCredentialRequestDTO dto) {
        authService.registerCredential(dto);
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    // Llamado por el cliente (frontend/app)
    @PostMapping("/login")
    public ResponseEntity<LoginResponseDTO> login(@Valid @RequestBody LoginRequestDTO dto) {
        return ResponseEntity.ok(authService.login(dto));
    }

    // Llamado por otros microservicios para validar el token
    @GetMapping("/validate")
    public ResponseEntity<TokenValidationResponseDTO> validate(@RequestParam String token) {
        return ResponseEntity.ok(authService.validateToken(token));
    }
}
