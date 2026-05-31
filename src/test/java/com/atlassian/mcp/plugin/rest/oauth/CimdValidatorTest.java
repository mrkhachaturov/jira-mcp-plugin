package com.atlassian.mcp.plugin.rest.oauth;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.Test;
import static org.junit.Assert.*;

public class CimdValidatorTest {

    private final CimdValidator validator =
            new CimdValidator(java.net.http.HttpClient.newHttpClient(), new ObjectMapper(), true);

    @Test
    public void rejectsLocalhostClientId() {
        try {
            validator.resolve("https://localhost/.well-known/client");
            fail("expected CimdException for localhost");
        } catch (CimdValidator.CimdException e) {
            assertTrue(e.getMessage().toLowerCase().contains("blocked")
                    || e.getMessage().toLowerCase().contains("resolve"));
        }
    }

    @Test
    public void rejectsMetadataIpClientId() {
        try {
            validator.resolve("https://169.254.169.254/latest/meta-data");
            fail("expected CimdException for metadata IP");
        } catch (CimdValidator.CimdException e) {
            assertTrue(e.getMessage().toLowerCase().contains("blocked"));
        }
    }

    @Test
    public void rejectsNonCimdClientId() {
        try {
            validator.resolve("not-a-url");
            fail("expected CimdException");
        } catch (CimdValidator.CimdException e) {
            assertTrue(e.getMessage().contains("not a valid CIMD URL"));
        }
    }

    @Test
    public void cacheStaysBounded() {
        for (int i = 0; i < 5000; i++) {
            try { validator.resolve("https://blocked.invalid.test/" + i); }
            catch (CimdValidator.CimdException ignored) {}
        }
        assertTrue("cache must stay bounded", validator.cacheSize() <= 1000);
    }

    @Test
    public void redirectUriHostMustMatchExactly() {
        assertTrue(CimdValidator.isAllowedRedirectUri("https://app.example.com/cb"));
        assertTrue(CimdValidator.isAllowedRedirectUri("http://localhost:1234/cb"));
        assertTrue(CimdValidator.isAllowedRedirectUri("http://127.0.0.1/cb"));
        assertFalse(CimdValidator.isAllowedRedirectUri("http://localhost.evil.example/cb"));
        assertFalse(CimdValidator.isAllowedRedirectUri("http://127.0.0.1.evil.example/cb"));
        assertFalse(CimdValidator.isAllowedRedirectUri("http://user@localhost/cb"));
        assertFalse(CimdValidator.isAllowedRedirectUri("http://example.com/cb"));
    }
}
