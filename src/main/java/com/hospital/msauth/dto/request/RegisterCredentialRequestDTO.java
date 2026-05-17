package com.hospital.msauth.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RegisterCredentialRequestDTO {

    @Email
    @NotBlank(message = "El email es obligatorio")
    private String email;

    @NotBlank(message = "La contrasena es obligatoria")
    private String password;

    @NotBlank(message = "El rol es obligatorio")
    private String role;

    @NotNull(message = "El userId es obligatorio")
    private UUID userId;
}
