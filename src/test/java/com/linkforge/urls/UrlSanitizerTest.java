package com.linkforge.urls;

import com.linkforge.exception.UrlValidationException;
import com.linkforge.util.UrlSanitizerUtil;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.*;

@DisplayName("URL Sanitizer Unit Tests")
class UrlSanitizerTest {

    private final UrlSanitizerUtil sanitizer = new UrlSanitizerUtil();

    @Test
    @DisplayName("Valid HTTPS URL passes sanitization")
    void validHttpsUrl_passes() {
        String result = sanitizer.sanitize("https://www.google.com");
        assertThat(result).isEqualTo("https://www.google.com");
    }

    @Test
    @DisplayName("URL without scheme gets https:// prepended")
    void urlWithoutScheme_getsHttpsPrepended() {
        String result = sanitizer.sanitize("www.google.com");
        assertThat(result).startsWith("https://");
    }

    @ParameterizedTest(name = "Blocked scheme: {0}")
    @ValueSource(strings = {
        "javascript:alert(1)",
        "data:text/html,<h1>xss</h1>",
        "file:///etc/passwd",
        "ftp://example.com",
        "vbscript:msgbox('xss')"
    })
    @DisplayName("Dangerous URL schemes are blocked")
    void dangerousScheme_throwsException(String url) {
        assertThatThrownBy(() -> sanitizer.sanitize(url))
                .isInstanceOf(UrlValidationException.class);
    }

    @ParameterizedTest(name = "Private host: {0}")
    @ValueSource(strings = {
        "http://localhost/malware",
        "http://127.0.0.1/secret",
        "http://192.168.1.1/admin",
        "http://10.0.0.1/internal"
    })
    @DisplayName("Private and localhost URLs are blocked")
    void privateHosts_areBlocked(String url) {
        assertThatThrownBy(() -> sanitizer.sanitize(url))
                .isInstanceOf(UrlValidationException.class);
    }

    @Test
    @DisplayName("Null URL throws exception")
    void nullUrl_throwsException() {
        assertThatThrownBy(() -> sanitizer.sanitize(null))
                .isInstanceOf(UrlValidationException.class);
    }
}
