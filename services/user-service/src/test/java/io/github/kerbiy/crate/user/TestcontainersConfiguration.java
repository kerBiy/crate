package io.github.kerbiy.crate.user;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.DynamicPropertyRegistrar;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Shared by every test class: all of them @Import this, so Spring caches one context and
 * starts one Postgres container for the whole test run.
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

    public static final String INVITE_CODE = "test-invite";

    // Same image as infra/compose. @ServiceConnection points the datasource at this container,
    // so the tests need no URL/password properties and never read infra/compose/.env.
    @Bean
    @ServiceConnection
    PostgreSQLContainer postgres() {
        return new PostgreSQLContainer("postgres:17.11-alpine");
    }

    // A throwaway RSA key generated per test run: no key, not even a test one, is committed.
    @Bean
    DynamicPropertyRegistrar authProperties() {
        return registry -> {
            registry.add("crate.auth.jwt.private-key-location", TestcontainersConfiguration::writeTestPrivateKey);
            registry.add("crate.auth.invite-codes", () -> INVITE_CODE);
        };
    }

    private static String writeTestPrivateKey() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            // getEncoded() on an RSA private key is PKCS#8 DER; PEM is that, base64'd between markers.
            byte[] pkcs8 = generator.generateKeyPair().getPrivate().getEncoded();
            String pem = "-----BEGIN PRIVATE KEY-----\n"
                    + Base64.getMimeEncoder(64, "\n".getBytes()).encodeToString(pkcs8)
                    + "\n-----END PRIVATE KEY-----\n";
            Path file = Files.createTempFile("jwt-test-key", ".pem");
            file.toFile().deleteOnExit();
            Files.writeString(file, pem);
            return file.toUri().toString();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
