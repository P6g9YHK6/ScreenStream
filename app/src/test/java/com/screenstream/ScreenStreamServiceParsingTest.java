package com.screenstream;

import org.junit.Test;

import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class ScreenStreamServiceParsingTest {

    @Test
    public void extractQueryParamFindsValue() {
        assertEquals("123456", ScreenStreamService.extractQueryParam("pin=123456", "pin"));
        assertEquals("123456", ScreenStreamService.extractQueryParam("t=1&pin=123456", "pin"));
        assertEquals("123456", ScreenStreamService.extractQueryParam("pin=123456&t=1", "pin"));
    }

    @Test
    public void extractQueryParamMissingReturnsNull() {
        assertNull(ScreenStreamService.extractQueryParam("t=1", "pin"));
        assertNull(ScreenStreamService.extractQueryParam("", "pin"));
        assertNull(ScreenStreamService.extractQueryParam(null, "pin"));
    }

    @Test
    public void extractQueryParamEmptyValue() {
        assertEquals("", ScreenStreamService.extractQueryParam("pin=&t=1", "pin"));
    }

    @Test
    public void constantTimeEqualsMatchesAndMismatches() {
        assertTrue(ScreenStreamService.constantTimeEquals("secret", "secret"));
        assertTrue(ScreenStreamService.constantTimeEquals("", ""));
        assertTrue(ScreenStreamService.constantTimeEquals(null, null));
        assertTrue(ScreenStreamService.constantTimeEquals(null, ""));
    }

    @Test
    public void constantTimeEqualsDetectsDifference() {
        org.junit.Assert.assertFalse(ScreenStreamService.constantTimeEquals("secret", "different"));
        org.junit.Assert.assertFalse(ScreenStreamService.constantTimeEquals("secret", "secre"));
        org.junit.Assert.assertFalse(ScreenStreamService.constantTimeEquals("secret", null));
    }

    @Test
    public void parseHeadersReadsKeyValuePairsCaseInsensitively() {
        String[] lines = {
            "GET /events HTTP/1.1",
            "Host: 192.168.1.5:8080",
            "Authorization: Basic dXNlcjpwYXNz",
            "",
            "ignored body"
        };
        Map<String, String> headers = ScreenStreamService.parseHeaders(lines);
        assertEquals("192.168.1.5:8080", headers.get("host"));
        assertEquals("Basic dXNlcjpwYXNz", headers.get("authorization"));
        assertEquals(2, headers.size());
    }

    @Test
    public void parseHeadersHandlesMalformedLinesGracefully() {
        String[] lines = {
            "GET / HTTP/1.1",
            "not-a-header-line",
            ": no key",
            "Valid: yes",
            ""
        };
        Map<String, String> headers = ScreenStreamService.parseHeaders(lines);
        assertEquals("yes", headers.get("valid"));
        assertEquals(1, headers.size());
    }

    @Test
    public void parseHeadersOnFirstLineOnlyReturnsEmptyMap() {
        String[] lines = {"GET / HTTP/1.1"};
        Map<String, String> headers = ScreenStreamService.parseHeaders(lines);
        assertTrue(headers.isEmpty());
    }

    @Test
    public void bindFailureMessageDetectsPermissionDenied() {
        String msg = ScreenStreamService.bindFailureMessage(80, "bind failed: EACCES (Permission denied)");
        assertTrue(msg.contains("needs root"));
        assertTrue(msg.contains("80"));
    }

    @Test
    public void bindFailureMessageDetectsAddressInUse() {
        String msg = ScreenStreamService.bindFailureMessage(8080, "bind failed: EADDRINUSE (Address already in use)");
        assertTrue(msg.contains("already in use"));
        assertTrue(msg.contains("8080"));
    }

    @Test
    public void bindFailureMessageFallsBackForUnknownCause() {
        String msg = ScreenStreamService.bindFailureMessage(443, "something unexpected happened");
        assertTrue(msg.contains("Failed to bind port 443"));
        assertTrue(msg.contains("something unexpected happened"));
    }

    @Test
    public void bindFailureMessageHandlesNullExceptionMessage() {
        String msg = ScreenStreamService.bindFailureMessage(21, null);
        assertTrue(msg.contains("Failed to bind port 21"));
    }
}
