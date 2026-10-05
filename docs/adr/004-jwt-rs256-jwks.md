# ADR-004: JWT (RS256) issued by user-service, verified via JWKS

Date: 2026-10-05 · Status: accepted

## Context

Every request beyond login needs to say who the user is. The gateway and, later, other services must be able to check that identity without calling user-service on every request. Registration must be invite-only, and the whole thing has to be simple enough to get right while learning Spring Security.

## Decision

- **user-service is the only token issuer.** `POST /auth/login` checks the BCrypt hash and returns a JWT signed with **RS256**, valid for **12 hours**, with claims `sub` (user id), `username`, `iss`, `iat`, `exp`.
- **The public key is published as a JWK Set** at `/.well-known/jwks.json` (internal only, not routed by the gateway). Each key has a `kid` (the RFC 7638 thumbprint), which also appears in the token header.
- **Only the private key is configured**, as a PKCS#8 PEM file (`crate.auth.jwt.private-key-location`). The public key is derived from it. Locally, `make jwt-keys` creates it in a git-ignored folder; in production it is a file mounted into the container. Tests generate a throwaway key on every run.
- **Spring Security is used as a library** in user-service (`spring-security-crypto` for BCrypt, `spring-security-oauth2-jose` for signing), without its filter chain. The gateway validates tokens (OAuth2 Resource Server, next task); services trust `X-User-Id` from the gateway until Phase 3.
- **Login failures look identical** whether the account exists or not: same status, same body, and a BCrypt check against a dummy hash so timing matches too. The invite code is checked before username/email uniqueness, so only invite holders can learn whether a name is taken.

## Alternatives considered

- **HS256 (shared secret).** Simpler, but every verifier would hold a key that can mint tokens for any user. Rejected.
- **Public key copied into the gateway's config instead of JWKS.** Works, but every key rotation means redeploying all verifiers. JWKS + `kid` lets old and new keys coexist.
- **Opaque session tokens checked against user-service.** Revocable, but adds a network call to every request and makes user-service a hard dependency of everything.
- **Spring Authorization Server.** A full OAuth2/OIDC server is far more than an invite-only friends app needs.

## Consequences

Good:
- Only user-service can create tokens; a leaked gateway config can't forge identities.
- Verification needs no database lookup and no call to user-service.
- Key rotation is possible without redeploying verifiers.

Bad:
- A token can't be revoked before it expires (12 h). Logout is client-side only. Fixed in Phase 2 with short access tokens and a refresh token (SPEC 3.6).
- The private key is a real secret to manage on the server and to back up.
- Until Phase 3, services trust the gateway's headers (SPEC 3.6).
- Invite codes are reusable and live in an env var; changing them means a restart.
