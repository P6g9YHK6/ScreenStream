package com.screenstream;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ErrorReporterJsonTest {

    @Test
    public void escapesQuotesAndBackslashes() {
        ErrorReporter.AppError err = new ErrorReporter.AppError(
            ErrorReporter.Level.ERROR, ErrorReporter.Source.SYSTEM,
            "bad \"quote\" and \\backslash", null);
        String json = err.toJson();
        assertTrue(json.contains("bad \\\"quote\\\" and \\\\backslash"));
    }

    @Test
    public void escapesNewlinesAndTabsFromStackTraces() {
        String detail = "line1\nline2\r\nline3\ttabbed";
        ErrorReporter.AppError err = new ErrorReporter.AppError(
            ErrorReporter.Level.FATAL, ErrorReporter.Source.SYSTEM, "msg", detail);
        String json = err.toJson();
        assertFalse(json.contains("\n"));
        assertFalse(json.contains("\r"));
        assertFalse(json.contains("\t"));
        assertTrue(json.contains("line1\\nline2\\r\\nline3\\ttabbed"));
    }

    @Test
    public void escapesControlCharacters() {
        String bell = "bell" + (char) 7 + "char";
        ErrorReporter.AppError err = new ErrorReporter.AppError(
            ErrorReporter.Level.WARNING, ErrorReporter.Source.SYSTEM, bell, null);
        String json = err.toJson();
        assertTrue(json.contains("bell\\u0007char"));
    }

    @Test
    public void nullDetailProducesEmptyJsonString() {
        ErrorReporter.AppError err = new ErrorReporter.AppError(
            ErrorReporter.Level.INFO, ErrorReporter.Source.SYSTEM, "hello", null);
        String json = err.toJson();
        assertTrue(json.contains("\"detail\":\"\""));
    }

    @Test
    public void producesWellFormedJsonFields() {
        ErrorReporter.AppError err = new ErrorReporter.AppError(
            ErrorReporter.Level.WARNING, ErrorReporter.Source.NETWORK, "hi", "bye");
        String json = err.toJson();
        assertTrue(json.startsWith("{\"ts\":" + err.timestamp));
        assertTrue(json.contains("\"level\":\"WARNING\""));
        assertTrue(json.contains("\"source\":\"" + ErrorReporter.Source.NETWORK.label + "\""));
        assertTrue(json.endsWith("}"));
    }

    @Test
    public void realStackTraceProducesParseableJson() {
        Throwable t;
        try {
            throw new RuntimeException("boom");
        } catch (RuntimeException e) {
            t = e;
        }
        java.io.StringWriter sw = new java.io.StringWriter();
        t.printStackTrace(new java.io.PrintWriter(sw));
        ErrorReporter.AppError err = new ErrorReporter.AppError(
            ErrorReporter.Level.FATAL, ErrorReporter.Source.SYSTEM, "boom", sw.toString());
        String json = err.toJson();
        assertEquals(-1, json.indexOf('\n'));
        assertEquals(-1, json.indexOf('\r'));
    }
}
