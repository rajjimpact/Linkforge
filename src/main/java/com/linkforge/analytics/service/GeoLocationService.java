package com.linkforge.analytics.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * IP Geolocation service.
 * Production: Replace with MaxMind GeoLite2 database for offline lookup.
 * The embedded DB approach gives: ~1ms lookup, no network dependency, GDPR-friendly.
 *
 * To enable MaxMind: add `com.maxmind.geoip2:geoip2` to pom.xml,
 * download GeoLite2-City.mmdb, and update this service.
 */
@Service
@Slf4j
public class GeoLocationService {

    // TODO: Inject MaxMind DatabaseReader here when GeoLite2 DB is downloaded
    // @Autowired private DatabaseReader maxMindReader;

    public GeoLocation lookup(String ip) {
        if (ip == null || ip.isBlank() || isPrivateIp(ip)) {
            return GeoLocation.unknown();
        }

        try {
            // Placeholder — replace with MaxMind GeoLite2 lookup:
            // InetAddress address = InetAddress.getByName(ip);
            // CityResponse response = maxMindReader.city(address);
            // return new GeoLocation(
            //     response.getCountry().getName(),
            //     response.getCountry().getIsoCode(),
            //     response.getCity().getName(),
            //     response.getMostSpecificSubdivision().getName(),
            //     response.getLocation().getLatitude(),
            //     response.getLocation().getLongitude()
            // );
            return GeoLocation.unknown();
        } catch (Exception e) {
            log.debug("Geolocation lookup failed for IP {}: {}", ip, e.getMessage());
            return GeoLocation.unknown();
        }
    }

    private boolean isPrivateIp(String ip) {
        return ip.startsWith("127.") || ip.startsWith("192.168.") ||
               ip.startsWith("10.") || ip.equals("::1") || ip.startsWith("172.16.");
    }

    public record GeoLocation(
        String country,
        String countryCode,
        String city,
        String region,
        Double latitude,
        Double longitude
    ) {
        public static GeoLocation unknown() {
            return new GeoLocation(null, null, null, null, null, null);
        }
    }
}
