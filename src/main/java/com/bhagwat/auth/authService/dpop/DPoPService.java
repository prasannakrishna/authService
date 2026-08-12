package com.bhagwat.auth.authService.dpop;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.interfaces.ECPublicKey;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * DPoP (Demonstration of Proof-of-Possession) Proof Validation Service.
 * Validates DPoP proof JWTs per RFC 9449.
 */
@Service
@Slf4j
public class DPoPService {

    private final ObjectMapper objectMapper = new ObjectMapper();

    // Replay cache — stores jti values to prevent replay attacks (5 min TTL)
    private final Map<String, Long> replayCache = new ConcurrentHashMap<>();
    private static final long REPLAY_WINDOW_MS = 300_000; // 5 minutes
    private static final long IAT_TOLERANCE_SECONDS = 120; // 2 minutes tolerance

    @PostConstruct
    public void init() {
        // Start a cleanup thread for replay cache
        Timer timer = new Timer(true);
        timer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                long now = System.currentTimeMillis();
                replayCache.entrySet().removeIf(e -> now - e.getValue() > REPLAY_WINDOW_MS);
            }
        }, 60_000, 60_000);
    }

    /**
     * Validate a DPoP proof JWT and return the JWK Thumbprint.
     *
     * @param dpopProof the DPoP proof JWT from the DPoP header
     * @param httpMethod expected HTTP method (POST, GET, etc.)
     * @param httpUri expected HTTP URI
     * @param accessToken optional — if present, validates ath claim
     * @return JWK Thumbprint (SHA-256 hash of the public key) for token binding
     * @throws DPoPValidationException if proof is invalid
     */
    public String validateProof(String dpopProof, String httpMethod, String httpUri, String accessToken) {
        if (dpopProof == null || dpopProof.isBlank()) {
            throw new DPoPValidationException("Missing DPoP proof");
        }

        try {
            // 1. Split JWT into parts
            String[] parts = dpopProof.split("\\.");
            if (parts.length != 3) {
                throw new DPoPValidationException("Invalid DPoP JWT structure");
            }

            // 2. Parse header
            String headerJson = new String(Base64.getUrlDecoder().decode(parts[0]), StandardCharsets.UTF_8);
            Map<String, Object> header = objectMapper.readValue(headerJson, Map.class);

            // 3. Verify typ = "dpop+jwt"
            if (!"dpop+jwt".equals(header.get("typ"))) {
                throw new DPoPValidationException("Invalid DPoP typ — must be 'dpop+jwt'");
            }

            // 4. Extract JWK from header
            Map<String, Object> jwk = (Map<String, Object>) header.get("jwk");
            if (jwk == null) {
                throw new DPoPValidationException("Missing jwk in DPoP header");
            }

            String alg = (String) header.get("alg");
            if (alg == null) {
                throw new DPoPValidationException("Missing alg in DPoP header");
            }

            // 5. Parse payload
            String payloadJson = new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8);
            Map<String, Object> payload = objectMapper.readValue(payloadJson, Map.class);

            // 6. Validate claims
            String jti = (String) payload.get("jti");
            String htm = (String) payload.get("htm");
            String htu = (String) payload.get("htu");
            Number iatNum = (Number) payload.get("iat");

            if (jti == null || jti.isBlank()) {
                throw new DPoPValidationException("Missing jti claim");
            }
            if (htm == null || !htm.equalsIgnoreCase(httpMethod)) {
                throw new DPoPValidationException("htm mismatch: expected " + httpMethod + ", got " + htm);
            }
            if (htu == null || !normalizeUri(htu).equals(normalizeUri(httpUri))) {
                throw new DPoPValidationException("htu mismatch: expected " + httpUri + ", got " + htu);
            }
            if (iatNum == null) {
                throw new DPoPValidationException("Missing iat claim");
            }

            // 7. Check iat is recent
            long iat = iatNum.longValue();
            long now = System.currentTimeMillis() / 1000;
            if (Math.abs(now - iat) > IAT_TOLERANCE_SECONDS) {
                throw new DPoPValidationException("DPoP proof expired or too far in the future");
            }

            // 8. Check replay (jti uniqueness)
            if (replayCache.putIfAbsent(jti, System.currentTimeMillis()) != null) {
                throw new DPoPValidationException("DPoP proof replay detected (duplicate jti)");
            }

            // 9. If access token provided, validate ath claim
            if (accessToken != null && !accessToken.isBlank()) {
                String ath = (String) payload.get("ath");
                String expectedAth = computeAth(accessToken);
                if (ath == null || !ath.equals(expectedAth)) {
                    throw new DPoPValidationException("ath claim mismatch");
                }
            }

            // 10. Verify signature using the embedded public key
            PublicKey publicKey = parseJwkToPublicKey(jwk, alg);
            verifySignature(parts[0] + "." + parts[1], parts[2], publicKey, alg);

            // 11. Compute and return JWK Thumbprint
            return computeJwkThumbprint(jwk);

        } catch (DPoPValidationException e) {
            throw e;
        } catch (Exception e) {
            log.error("DPoP validation failed: {}", e.getMessage());
            throw new DPoPValidationException("DPoP validation failed: " + e.getMessage());
        }
    }

    /**
     * Compute JWK Thumbprint (SHA-256) per RFC 7638.
     */
    public String computeJwkThumbprint(Map<String, Object> jwk) throws Exception {
        // Build canonical JSON based on key type
        String kty = (String) jwk.get("kty");
        String canonicalJson;

        if ("EC".equals(kty)) {
            canonicalJson = String.format("{\"crv\":\"%s\",\"kty\":\"%s\",\"x\":\"%s\",\"y\":\"%s\"}",
                    jwk.get("crv"), kty, jwk.get("x"), jwk.get("y"));
        } else if ("RSA".equals(kty)) {
            canonicalJson = String.format("{\"e\":\"%s\",\"kty\":\"%s\",\"n\":\"%s\"}",
                    jwk.get("e"), kty, jwk.get("n"));
        } else {
            throw new DPoPValidationException("Unsupported key type: " + kty);
        }

        MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
        byte[] hash = sha256.digest(canonicalJson.getBytes(StandardCharsets.UTF_8));
        return Base64.getUrlEncoder().withoutPadding().encodeToString(hash);
    }

    /**
     * Compute access token hash (ath claim).
     */
    private String computeAth(String accessToken) throws Exception {
        MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
        byte[] hash = sha256.digest(accessToken.getBytes(StandardCharsets.US_ASCII));
        return Base64.getUrlEncoder().withoutPadding().encodeToString(hash);
    }

    private PublicKey parseJwkToPublicKey(Map<String, Object> jwk, String alg) throws Exception {
        String kty = (String) jwk.get("kty");

        if ("EC".equals(kty)) {
            String crv = (String) jwk.get("crv");
            String x = (String) jwk.get("x");
            String y = (String) jwk.get("y");

            byte[] xBytes = Base64.getUrlDecoder().decode(x);
            byte[] yBytes = Base64.getUrlDecoder().decode(y);

            // Build EC public key from coordinates
            java.security.spec.ECPoint point = new java.security.spec.ECPoint(
                    new java.math.BigInteger(1, xBytes),
                    new java.math.BigInteger(1, yBytes));

            java.security.spec.ECParameterSpec params = getECParameterSpec(crv);
            java.security.spec.ECPublicKeySpec keySpec = new java.security.spec.ECPublicKeySpec(point, params);
            KeyFactory kf = KeyFactory.getInstance("EC");
            return kf.generatePublic(keySpec);

        } else if ("RSA".equals(kty)) {
            String n = (String) jwk.get("n");
            String e = (String) jwk.get("e");

            byte[] nBytes = Base64.getUrlDecoder().decode(n);
            byte[] eBytes = Base64.getUrlDecoder().decode(e);

            java.security.spec.RSAPublicKeySpec keySpec = new java.security.spec.RSAPublicKeySpec(
                    new java.math.BigInteger(1, nBytes),
                    new java.math.BigInteger(1, eBytes));
            KeyFactory kf = KeyFactory.getInstance("RSA");
            return kf.generatePublic(keySpec);

        } else {
            throw new DPoPValidationException("Unsupported key type: " + kty);
        }
    }

    private void verifySignature(String signingInput, String signatureB64, PublicKey publicKey, String alg)
            throws Exception {
        byte[] signatureBytes = Base64.getUrlDecoder().decode(signatureB64);
        String jcaAlg = mapAlgToJca(alg);

        // JWS uses raw R||S format, but Java's ECDSA Signature expects DER encoding
        if (alg.startsWith("ES")) {
            signatureBytes = rawToDer(signatureBytes);
        }

        Signature sig = Signature.getInstance(jcaAlg);
        sig.initVerify(publicKey);
        sig.update(signingInput.getBytes(StandardCharsets.UTF_8));

        if (!sig.verify(signatureBytes)) {
            throw new DPoPValidationException("DPoP signature verification failed");
        }
    }

    /**
     * Convert raw R||S ECDSA signature to DER encoding.
     * JWS (RFC 7515) uses raw concatenation, Java Signature uses DER.
     */
    private byte[] rawToDer(byte[] raw) {
        int halfLen = raw.length / 2;
        byte[] r = trimLeadingZeros(raw, 0, halfLen);
        byte[] s = trimLeadingZeros(raw, halfLen, halfLen);

        // If high bit set, prepend 0x00 to keep positive
        boolean rPad = (r[0] & 0x80) != 0;
        boolean sPad = (s[0] & 0x80) != 0;

        int rLen = r.length + (rPad ? 1 : 0);
        int sLen = s.length + (sPad ? 1 : 0);
        int totalLen = 2 + rLen + 2 + sLen;

        byte[] der = new byte[2 + totalLen];
        int offset = 0;
        der[offset++] = 0x30;
        der[offset++] = (byte) totalLen;

        der[offset++] = 0x02;
        der[offset++] = (byte) rLen;
        if (rPad) der[offset++] = 0x00;
        System.arraycopy(r, 0, der, offset, r.length);
        offset += r.length;

        der[offset++] = 0x02;
        der[offset++] = (byte) sLen;
        if (sPad) der[offset++] = 0x00;
        System.arraycopy(s, 0, der, offset, s.length);

        return der;
    }

    private byte[] trimLeadingZeros(byte[] data, int start, int length) {
        int i = start;
        while (i < start + length - 1 && data[i] == 0) i++;
        return Arrays.copyOfRange(data, i, start + length);
    }

    private String mapAlgToJca(String alg) {
        return switch (alg) {
            case "ES256" -> "SHA256withECDSA";
            case "ES384" -> "SHA384withECDSA";
            case "ES512" -> "SHA512withECDSA";
            case "RS256" -> "SHA256withRSA";
            case "RS384" -> "SHA384withRSA";
            case "RS512" -> "SHA512withRSA";
            default -> throw new DPoPValidationException("Unsupported algorithm: " + alg);
        };
    }

    private java.security.spec.ECParameterSpec getECParameterSpec(String crv) {
        try {
            String stdName = switch (crv) {
                case "P-256" -> "secp256r1";
                case "P-384" -> "secp384r1";
                case "P-521" -> "secp521r1";
                default -> throw new DPoPValidationException("Unsupported curve: " + crv);
            };
            java.security.AlgorithmParameters params = java.security.AlgorithmParameters.getInstance("EC");
            params.init(new java.security.spec.ECGenParameterSpec(stdName));
            return params.getParameterSpec(java.security.spec.ECParameterSpec.class);
        } catch (Exception e) {
            throw new DPoPValidationException("Failed to get EC parameters for curve: " + crv);
        }
    }

    private String normalizeUri(String uri) {
        // Remove trailing slash and query parameters for comparison
        if (uri == null) return "";
        int queryIdx = uri.indexOf('?');
        if (queryIdx > 0) uri = uri.substring(0, queryIdx);
        return uri.endsWith("/") ? uri.substring(0, uri.length() - 1) : uri;
    }
}
