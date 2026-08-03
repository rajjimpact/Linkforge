package com.linkforge.analytics.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * User-Agent parser service.
 * Extracts browser, OS, and device type from User-Agent header.
 * Uses heuristic matching without heavy external library dependencies.
 */
@Service
@Slf4j
public class UserAgentParserService {

    public ParsedUserAgent parse(String userAgent) {
        if (userAgent == null || userAgent.isBlank()) {
            return new ParsedUserAgent(ClickEvent_DeviceType.UNKNOWN, "Unknown", null, "Unknown", null);
        }

        String ua = userAgent.toLowerCase();

        ClickEvent_DeviceType deviceType = detectDevice(ua);
        String[] browserInfo = detectBrowser(ua);
        String[] osInfo = detectOs(ua);

        return new ParsedUserAgent(deviceType, browserInfo[0], browserInfo[1], osInfo[0], osInfo[1]);
    }

    private ClickEvent_DeviceType detectDevice(String ua) {
        if (ua.contains("bot") || ua.contains("crawler") || ua.contains("spider") ||
            ua.contains("googlebot") || ua.contains("bingbot") || ua.contains("slurp")) {
            return ClickEvent_DeviceType.BOT;
        }
        if (ua.contains("tablet") || ua.contains("ipad") || (ua.contains("android") && !ua.contains("mobile"))) {
            return ClickEvent_DeviceType.TABLET;
        }
        if (ua.contains("mobile") || ua.contains("iphone") || ua.contains("ipod") ||
            ua.contains("android") || ua.contains("blackberry") || ua.contains("windows phone")) {
            return ClickEvent_DeviceType.MOBILE;
        }
        return ClickEvent_DeviceType.DESKTOP;
    }

    private String[] detectBrowser(String ua) {
        if (ua.contains("edg/")) return new String[]{"Edge", extractVersion(ua, "edg/")};
        if (ua.contains("opr/") || ua.contains("opera")) return new String[]{"Opera", extractVersion(ua, "opr/")};
        if (ua.contains("chrome/") && !ua.contains("chromium")) return new String[]{"Chrome", extractVersion(ua, "chrome/")};
        if (ua.contains("firefox/")) return new String[]{"Firefox", extractVersion(ua, "firefox/")};
        if (ua.contains("safari/") && !ua.contains("chrome")) return new String[]{"Safari", extractVersion(ua, "version/")};
        if (ua.contains("msie") || ua.contains("trident/")) return new String[]{"Internet Explorer", null};
        return new String[]{"Other", null};
    }

    private String[] detectOs(String ua) {
        if (ua.contains("windows nt 10")) return new String[]{"Windows", "10"};
        if (ua.contains("windows nt 11")) return new String[]{"Windows", "11"};
        if (ua.contains("windows")) return new String[]{"Windows", null};
        if (ua.contains("mac os x")) return new String[]{"macOS", extractVersion(ua, "mac os x ")};
        if (ua.contains("iphone")) return new String[]{"iOS", extractVersion(ua, "iphone os ")};
        if (ua.contains("ipad")) return new String[]{"iOS", extractVersion(ua, "cpu os ")};
        if (ua.contains("android")) return new String[]{"Android", extractVersion(ua, "android ")};
        if (ua.contains("linux")) return new String[]{"Linux", null};
        if (ua.contains("chromeos")) return new String[]{"Chrome OS", null};
        return new String[]{"Unknown", null};
    }

    private String extractVersion(String ua, String marker) {
        int idx = ua.indexOf(marker);
        if (idx == -1) return null;
        int start = idx + marker.length();
        int end = start;
        while (end < ua.length() && (Character.isDigit(ua.charAt(end)) || ua.charAt(end) == '.' || ua.charAt(end) == '_')) {
            end++;
        }
        String version = ua.substring(start, end).replace('_', '.');
        return version.isEmpty() ? null : version;
    }

    private enum ClickEvent_DeviceType {
        DESKTOP, MOBILE, TABLET, BOT, UNKNOWN
    }

    public record ParsedUserAgent(
        ClickEvent_DeviceType deviceTypeRaw,
        String browser,
        String browserVersion,
        String os,
        String osVersion
    ) {
        public com.linkforge.analytics.entity.ClickEvent.DeviceType deviceType() {
            return com.linkforge.analytics.entity.ClickEvent.DeviceType.valueOf(deviceTypeRaw.name());
        }
    }
}
