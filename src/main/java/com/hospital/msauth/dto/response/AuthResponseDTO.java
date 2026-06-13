package com.hospital.msauth.dto.response;


import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuthResponseDTO {

    private String token;
    @Builder.Default
    private String tokenType = "Bearer";
    private UUID userId;
    private String email;
    private String role;
}
