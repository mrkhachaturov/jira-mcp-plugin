package com.atlassian.mcp.plugin.config;

import javax.inject.Named;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Named
public class OAuthStateStore {

    private static final Logger log = LoggerFactory.getLogger(OAuthStateStore.class);

    private static final long EXPIRY_MS = 10 * 60 * 1000; // 10 minutes
    private static final long CLIENT_EXPIRY_MS = 24 * 60 * 60 * 1000; // 24 hours
    private static final int MAX_PENDING_AUTHS = 500;
    private static final int MAX_PROXY_CODES = 500;
    private static final int MAX_REGISTERED_CLIENTS = 1000;

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
        public final long createdAt;

        public RegisteredClient(String clientId, String clientName, List<String> redirectUris) {
            this.clientId = clientId;
            this.clientName = clientName;
            this.redirectUris = redirectUris;
            this.createdAt = System.currentTimeMillis();
        }

        public boolean isExpired() {
            return System.currentTimeMillis() - createdAt > CLIENT_EXPIRY_MS;
        }
    }

    private final Map<String, PendingAuth> pendingAuths = new ConcurrentHashMap<>();
    private final Map<String, ProxyCode> proxyCodes = new ConcurrentHashMap<>();
    private final Map<String, RegisteredClient> registeredClients = new ConcurrentHashMap<>();

    public String createPendingAuth(String clientRedirectUri, String clientState,
                                     String codeChallenge, String codeChallengeMethod, String clientId) {
        cleanup();
        if (pendingAuths.size() >= MAX_PENDING_AUTHS) {
            log.warn("[MCP-SEC] Pending auth capacity reached ({}), rejecting", MAX_PENDING_AUTHS);
            return null;
        }
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
        if (proxyCodes.size() >= MAX_PROXY_CODES) {
            log.warn("[MCP-SEC] Proxy code capacity reached ({}), rejecting", MAX_PROXY_CODES);
            return null;
        }
        String code = UUID.randomUUID().toString();
        proxyCodes.put(code, new ProxyCode(accessToken, clientId, redirectUri,
                codeChallenge, codeChallengeMethod));
        return code;
    }

    public ProxyCode consumeProxyCode(String code) {
        ProxyCode pc = proxyCodes.remove(code);
        return (pc != null && !pc.isExpired()) ? pc : null;
    }

    /** Register a dynamic client. Returns null if capacity reached. */
    public RegisteredClient registerClient(String clientName, List<String> redirectUris) {
        cleanup();
        if (registeredClients.size() >= MAX_REGISTERED_CLIENTS) {
            log.warn("[MCP-SEC] Client registration capacity reached ({}), rejecting", MAX_REGISTERED_CLIENTS);
            return null;
        }
        String clientId = UUID.randomUUID().toString();
        RegisteredClient client = new RegisteredClient(clientId, clientName, redirectUris);
        registeredClients.put(clientId, client);
        return client;
    }

    public RegisteredClient getClient(String clientId) {
        RegisteredClient client = registeredClients.get(clientId);
        if (client != null && client.isExpired()) {
            registeredClients.remove(clientId);
            return null;
        }
        return client;
    }

    /** Verify PKCE code_verifier against stored code_challenge. Rejects if no challenge was set. */
    public static boolean verifyPkce(String codeVerifier, String codeChallenge, String method) {
        if (codeChallenge == null || codeChallenge.isEmpty()) {
            return false; // PKCE is mandatory
        }
        if (codeVerifier == null || codeVerifier.isEmpty()) {
            return false;
        }
        if (!"S256".equals(method)) {
            return false; // only S256 allowed
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
        registeredClients.entrySet().removeIf(e -> e.getValue().isExpired());
    }
}
