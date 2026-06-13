package com.hospital.msauth.dto.response;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LoginResponseDTO {

    private String token;
    @Builder.Default
    private String tokenType = "Bearer";
    private UUID userId;
    private String email;
    private String role;
}
