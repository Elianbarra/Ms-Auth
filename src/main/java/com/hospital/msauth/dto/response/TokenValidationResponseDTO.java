package com.hospital.msauth.dto.response;

import lombok.*;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TokenValidationResponseDTO {

    private boolean valid;
    private String email;
    private String role;
    private UUID userId;
}
