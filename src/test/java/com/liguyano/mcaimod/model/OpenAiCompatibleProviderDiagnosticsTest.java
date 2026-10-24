package com.liguyano.mcaimod.model;

import org.junit.jupiter.api.Test;
import java.io.EOFException;
import java.net.ConnectException;
import java.net.UnknownHostException;
import java.net.http.HttpTimeoutException;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeoutException;
import static org.junit.jupiter.api.Assertions.*;

class OpenAiCompatibleProviderDiagnosticsTest {
    @Test void unwrapsAndClassifiesErrorsWithoutLeakingRawMessages() {
        String timeout = OpenAiCompatibleProvider.networkDiagnostic(
                new CompletionException(new HttpTimeoutException("https://secret.example/path?api_key=secret")));
        assertTrue(timeout.contains("timed out"));
        assertFalse(timeout.contains("secret"));
        assertTrue(OpenAiCompatibleProvider.networkDiagnostic(new CompletionException(
                new UnknownHostException("host"))).contains("DNS"));
        assertTrue(OpenAiCompatibleProvider.networkDiagnostic(new ConnectException("host"))
                .contains("connection failed"));
        assertTrue(OpenAiCompatibleProvider.networkDiagnostic(new EOFException("closed"))
                .contains("closed early"));
        assertTrue(OpenAiCompatibleProvider.networkDiagnostic(new TimeoutException("timeout"))
                .contains("timed out"));
    }
}
