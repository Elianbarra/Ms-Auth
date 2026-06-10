package com.hospital.msauth;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest
@TestPropertySource(properties = {
    "security.jwt.private-key-path=/Users/elian/Documents/Ms-Auth/src/test/resources/keys/private_key.pem",
    "security.jwt.public-key-path=/Users/elian/Documents/Ms-Auth/src/test/resources/keys/public_key.pem"
})
class MsAuthApplicationTests {

    @Test
    void contextLoads() {
    }
}
