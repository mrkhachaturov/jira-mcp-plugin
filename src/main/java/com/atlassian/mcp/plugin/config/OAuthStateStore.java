package com.atlassian.mcp.plugin.config;

import javax.inject.Named;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Named
public class OAuthStateStore {

    private static final long EXPIRY_MS = 10 * 60 * 1000; // 10 minutes

    public static class PendingAuth {
        public final String clientRedirectUri;
        public final String clientState;
        public final String codeChallenge;
        public final String codeChallengeMethod;
        public final String clientId;
        public final long createdAt;

        public PendingAuth(String clientRedirectUri, String clientState,
                           String codeChallenge, String codeChallengeMethod, String clientId) {
            this.clientRedirectUri = clientRedirectUri;
            this.clientState = clientState;
            this.codeChallenge = codeChallenge;
            this.codeChallengeMethod = codeChallengeMethod;
            this.clientId = clientId;
            this.createdAt = System.currentTimeMillis();
        }

        public boolean isExpired() {
            return System.currentTimeMillis() - createdAt > EXPIRY_MS;
        }
    }

    public static class ProxyCode {
        public final String accessToken;
        public final String clientId;
        public final String redirectUri;
        public final String codeChallenge;
        public final String codeChallengeMethod;
        public final long createdAt;

        public ProxyCode(String accessToken, String clientId, String redirectUri,
                         String codeChallenge, String codeChallengeMethod) {
            this.accessToken = accessToken;
            this.clientId = clientId;
            this.redirectUri = redirectUri;
            this.codeChallenge = codeChallenge;
            this.codeChallengeMethod = codeChallengeMethod;
            this.createdAt = System.currentTimeMillis();
        }

        public boolean isExpired() {
            return System.currentTimeMillis() - createdAt > EXPIRY_MS;
        }
    }

    public static class RegisteredClient {
        public final String clientId;
        public final String clientName;
        public final List<String> redirectUris;

        public RegisteredClient(String clientId, String clientName, List<String> redirectUris) {
            this.clientId = clientId;
            this.clientName = clientName;
            this.redirectUris = redirectUris;
        }
    }

    private final Map<String, PendingAuth> pendingAuths = new ConcurrentHashMap<>();
    private final Map<String, ProxyCode> proxyCodes = new ConcurrentHashMap<>();
    private final Map<String, RegisteredClient> registeredClients = new ConcurrentHashMap<>();

    public String createPendingAuth(String clientRedirectUri, String clientState,
                                     String codeChallenge, String codeChallengeMethod, String clientId) {
        cleanup();
        String internalState = UUID.randomUUID().toString();
        pendingAuths.put(internalState, new PendingAuth(clientRedirectUri, clientState,
                codeChallenge, codeChallengeMethod, clientId));
        return internalState;
    }

    public PendingAuth consumePendingAuth(String internalState) {
        PendingAuth auth = pendingAuths.remove(internalState);
        return (auth != null && !auth.isExpired()) ? auth : null;
    }

    public String createProxyCode(String accessToken, String clientId, String redirectUri,
                                   String codeChallenge, String codeChallengeMethod) {
        cleanup();
        String code = UUID.randomUUID().toString();
        proxyCodes.put(code, new ProxyCode(accessToken, clientId, redirectUri,
                codeChallenge, codeChallengeMethod));
        return code;
    }

    public ProxyCode consumeProxyCode(String code) {
        ProxyCode pc = proxyCodes.remove(code);
        return (pc != null && !pc.isExpired()) ? pc : null;
    }

    public RegisteredClient registerClient(String clientName, List<String> redirectUris) {
        String clientId = UUID.randomUUID().toString();
        RegisteredClient client = new RegisteredClient(clientId, clientName, redirectUris);
        registeredClients.put(clientId, client);
        return client;
    }

    public RegisteredClient getClient(String clientId) {
        return registeredClients.get(clientId);
    }

    /** Verify PKCE code_verifier against stored code_challenge */
    public static boolean verifyPkce(String codeVerifier, String codeChallenge, String method) {
        if (codeChallenge == null || codeChallenge.isEmpty()) {
            return true; // no PKCE was used
        }
        if (codeVerifier == null || codeVerifier.isEmpty()) {
            return false; // challenge was set but no verifier provided
        }
        if ("plain".equals(method)) {
            return codeChallenge.equals(codeVerifier);
        }
        // S256: BASE64URL(SHA256(code_verifier)) == code_challenge
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(codeVerifier.getBytes(StandardCharsets.US_ASCII));
            String computed = Base64.getUrlEncoder().withoutPadding().encodeToString(hash);
            return codeChallenge.equals(computed);
        } catch (NoSuchAlgorithmException e) {
            return false;
        }
    }

    private void cleanup() {
        pendingAuths.entrySet().removeIf(e -> e.getValue().isExpired());
        proxyCodes.entrySet().removeIf(e -> e.getValue().isExpired());
    }
}
