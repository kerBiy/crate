package io.github.kerbiy.crate.user.auth.token;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import io.github.kerbiy.crate.user.auth.AuthProperties;
import java.io.IOException;
import java.io.InputStream;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.interfaces.RSAPrivateCrtKey;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.RSAPublicKeySpec;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.security.converter.RsaKeyConverters;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

/**
 * Loads the RSA signing key. Only the private key is configured: the public half is derived
 * from it, so the two can never mismatch.
 */
@Configuration(proxyBeanMethods = false)
class JwtKeyConfig {

    /** The key pair as a JWK. It holds the private key: never serialize it, use toPublicJWK(). */
    @Bean
    RSAKey signingKey(AuthProperties properties) throws IOException, GeneralSecurityException, JOSEException {
        RSAPrivateKey privateKey = readPrivateKey(properties.jwt().privateKeyLocation());
        return new RSAKey.Builder(derivePublicKey(privateKey))
                .privateKey(privateKey)
                // kid = RFC 7638 thumbprint: a stable id computed from the public key itself.
                .keyIDFromThumbprint()
                .build();
    }

    @Bean
    JwtEncoder jwtEncoder(RSAKey signingKey) {
        return new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(signingKey)));
    }

    private static RSAPrivateKey readPrivateKey(Resource location) throws IOException {
        if (location == null || !location.exists()) {
            throw new IllegalStateException(
                    "crate.auth.jwt.private-key-location is missing or points to no file: " + location
                            + ". For local development run `make jwt-keys`.");
        }
        try (InputStream in = location.getInputStream()) {
            // Expects PKCS#8 PEM ("BEGIN PRIVATE KEY"), which `openssl genpkey` writes.
            return RsaKeyConverters.pkcs8().convert(in);
        }
    }

    private static RSAPublicKey derivePublicKey(RSAPrivateKey privateKey) throws GeneralSecurityException {
        // A PKCS#8 RSA key stores its CRT parameters, which include the public exponent.
        if (!(privateKey instanceof RSAPrivateCrtKey crtKey)) {
            throw new IllegalStateException("RSA private key has no public exponent (not a CRT key)");
        }
        var spec = new RSAPublicKeySpec(crtKey.getModulus(), crtKey.getPublicExponent());
        return (RSAPublicKey) KeyFactory.getInstance("RSA").generatePublic(spec);
    }
}
