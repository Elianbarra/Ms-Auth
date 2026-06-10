package com.hospital.msauth.controller;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class JwksController {

    private final RSAKey rsaPublicJwk;

    public JwksController(RSAKey rsaPublicJwk) {
        this.rsaPublicJwk = rsaPublicJwk;
    }

    @GetMapping("/.well-known/jwks.json")
    public Map<String, Object> jwks() {
        return new JWKSet(rsaPublicJwk.toPublicJWK()).toJSONObject();
    }
}
