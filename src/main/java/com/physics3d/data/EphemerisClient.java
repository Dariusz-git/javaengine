package com.physics3d.data;

import org.joml.Vector3f;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.time.LocalDate;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

/**
 * Skeleton client for NASA JPL Horizons / ESA ephemeris APIs.
 *
 * Purpose: fetch real planet positions so the simulator can eventually
 * reflect actual orbital locations instead of purely computed ones.
 *
 * Current state: stub implementation — {@link #fetchPositions()} logs a
 * message and returns an empty map.  Replace the internals when the
 * integration is ready to go live.
 *
 * API reference (no key required for basic queries):
 *   https://ssd.jpl.nasa.gov/api/horizons.api
 *
 * Example query for Earth's position (heliocentric, ecliptic):
 *   ?format=text&OBJ_DATA=YES&MAKE_EPHEM=YES&EPHEM_TYPE=OBSERVER&
 *    COMMAND='399'&CENTER='500@10'&START_TIME='2026-06-19'&
 *    STOP_TIME='2026-06-20'&STEP_SIZE='1 d'&QUANTITIES='1'
 */
public class EphemerisClient {

    private static final Logger LOG = Logger.getLogger(EphemerisClient.class.getName());

    /** NASA JPL Horizons API endpoint (no API key needed for basic queries). */
    private static final String HORIZONS_API = "https://ssd.jpl.nasa.gov/api/horizons.api";

    /** ESA SSO portal — placeholder for future integration. */
    private static final String ESA_SSO_API = "https://ssa.esa.int/ssodm/rest";

    /** 1 AU in km — used to convert Horizons km output to AU. */
    private static final double AU_KM = 149_597_870.7;

    /** HTTP client reused across requests. */
    private final HttpClient httpClient;

    public EphemerisClient() {
        HttpClient built;
        try {
            // DEV-ONLY SSL bypass: trust every certificate.
            // Used because the JDK 17 cacerts on this machine does not include
            // the intermediate CA in NASA's certificate chain, which causes
            // PKIX path building failures.  Replace with a proper truststore
            // before shipping to production.
            TrustManager[] trustAllCerts = new TrustManager[] {
                new X509TrustManager() {
                    @Override public X509Certificate[] getAcceptedIssuers() { return new X509Certificate[0]; }
                    @Override public void checkClientTrusted(X509Certificate[] certs, String authType) { }
                    @Override public void checkServerTrusted(X509Certificate[] certs, String authType) { }
                }
            };
            SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(null, trustAllCerts, new SecureRandom());

            built = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(10))
                    .followRedirects(HttpClient.Redirect.NORMAL)
                    .sslContext(sslContext)
                    .build();
            LOG.info("EphemerisClient initialised — live NASA JPL Horizons mode (SSL bypass active, dev only)");
        } catch (Exception e) {
            LOG.log(Level.SEVERE, "Failed to build SSL-bypass HttpClient, falling back to default", e);
            built = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(10))
                    .followRedirects(HttpClient.Redirect.NORMAL)
                    .build();
        }
        this.httpClient = built;
    }

    // ------------------------------------------------------------------ public API

    /**
     * Fetch heliocentric positions (AU, ecliptic frame) for the major planets.
     *
     * @return map from planet name (e.g. "Earth", "Mars") to its XYZ position;
     *         empty map in stub mode
     */
    public Map<String, Vector3f> fetchPositions() {
        LOG.info("fetchPositions() called — querying NASA JPL Horizons for all planets");
        Map<String, Integer> planetCodes = new LinkedHashMap<>();
        planetCodes.put("Mercury", 199);
        planetCodes.put("Venus",   299);
        planetCodes.put("Earth",   399);
        planetCodes.put("Mars",    499);
        planetCodes.put("Jupiter", 599);
        planetCodes.put("Saturn",  699);
        planetCodes.put("Uranus",  799);
        planetCodes.put("Neptune", 899);

        String today = LocalDate.now().toString();
        String tomorrow = LocalDate.now().plusDays(1).toString();

        Map<String, Vector3f> results = new LinkedHashMap<>();
        for (Map.Entry<String, Integer> entry : planetCodes.entrySet()) {
            String name = entry.getKey();
            int code = entry.getValue();
            try {
                Vector3f pos = fetchBodyPosition(code, today, tomorrow);
                results.put(name, pos);
                LOG.info(name + " (code " + code + "): " + pos);
            } catch (Exception e) {
                LOG.log(Level.WARNING, "Failed to fetch position for " + name, e);
                results.put(name, new Vector3f(0, 0, 0));
            }
        }
        return results;
    }

    /**
     * Fetch heliocentric positions AND velocities (AU, AU/day, ecliptic J2000)
     * for the major planets.  This is the data the simulator needs to seed
     * itself with real-time solar-system state.
     *
     * @return map from planet name to a {@link Ephemeris} record containing
     *         position (AU) and velocity (AU/day)
     */
    public Map<String, Ephemeris> fetchPositionsAndVelocities() {
        LOG.info("fetchPositionsAndVelocities() called — querying NASA JPL Horizons for all planets");
        Map<String, Integer> planetCodes = new LinkedHashMap<>();
        planetCodes.put("Mercury", 199);
        planetCodes.put("Venus",   299);
        planetCodes.put("Earth",   399);
        planetCodes.put("Mars",    499);
        planetCodes.put("Jupiter", 599);
        planetCodes.put("Saturn",  699);
        planetCodes.put("Uranus",  799);
        planetCodes.put("Neptune", 899);

        String today = LocalDate.now().toString();
        String tomorrow = LocalDate.now().plusDays(1).toString();

        Map<String, Ephemeris> results = new LinkedHashMap<>();
        for (Map.Entry<String, Integer> entry : planetCodes.entrySet()) {
            String name = entry.getKey();
            int code = entry.getValue();
            try {
                Ephemeris eph = fetchBodyEphemeris(code, today, tomorrow);
                results.put(name, eph);
                LOG.info(name + " (code " + code + "): pos=" + eph.position
                        + " AU, vel=" + eph.velocity + " AU/day");
            } catch (Exception e) {
                LOG.log(Level.WARNING, "Failed to fetch ephemeris for " + name, e);
                results.put(name, new Ephemeris(new Vector3f(0, 0, 0), new Vector3f(0, 0, 0)));
            }
        }
        return results;
    }

    /**
     * Fetch position + velocity for a single body by its JPL Horizons command code.
     *
     * @param commandCode JPL body code (e.g. 399 for Earth, 499 for Mars)
     * @param startDate   observation start in YYYY-MM-DD format
     * @param stopDate    observation stop  in YYYY-MM-DD format
     * @return {@link Ephemeris} with position (AU) and velocity (AU/day)
     */
    public Ephemeris fetchBodyEphemeris(int commandCode, String startDate, String stopDate) {
        LOG.info("fetchBodyEphemeris(" + commandCode + ", " + startDate + ", " + stopDate + ")");

        // Build Horizons VECTORS query URL (heliocentric ecliptic J2000)
        String url = HORIZONS_API
                + "?format=text"
                + "&MAKE_EPHEM=YES"
                + "&EPHEM_TYPE=VECTORS"
                + "&COMMAND='" + commandCode + "'"
                + "&CENTER='500@10'"          // Sun-centered (body 10)
                + "&START_TIME='" + URLEncoder.encode(startDate, StandardCharsets.UTF_8) + "'"
                + "&STOP_TIME='" + URLEncoder.encode(stopDate, StandardCharsets.UTF_8) + "'"
                + "&STEP_SIZE='1%20d'"         // 1 day step
                + "&QUANTITIES='1'"            // position + velocity
                + "&OBJ_DATA=NO"              // skip object data block to reduce size
                + "&CSV_FORMAT=NO";            // standard text format

        String rawResponse = sendGet(url);
        if (rawResponse.isEmpty()) {
            LOG.warning("Empty response from Horizons for body " + commandCode);
            return new Ephemeris(new Vector3f(0, 0, 0), new Vector3f(0, 0, 0));
        }

        Ephemeris eph = parseHorizonsResponseWithVelocity(rawResponse);
        LOG.info("Body " + commandCode + " ephemeris: pos=" + eph.position
                + " AU, vel=" + eph.velocity + " AU/day");
        return eph;
    }

    /**
     * Fetch positions for a single body by its JPL Horizons command code.
     *
     * @param commandCode JPL body code (e.g. 399 for Earth, 499 for Mars)
     * @param startDate   observation start in YYYY-MM-DD format
     * @param stopDate    observation stop  in YYYY-MM-DD format
     * @return position vector in AU (ecliptic), or zero vector in stub mode
     */
    public Vector3f fetchBodyPosition(int commandCode, String startDate, String stopDate) {
        LOG.info("fetchBodyPosition(" + commandCode + ", " + startDate + ", " + stopDate + ")");

        // Build Horizons VECTORS query URL (heliocentric ecliptic J2000)
        String url = HORIZONS_API
                + "?format=text"
                + "&MAKE_EPHEM=YES"
                + "&EPHEM_TYPE=VECTORS"
                + "&COMMAND='" + commandCode + "'"
                + "&CENTER='500@10'"          // Sun-centered (body 10)
                + "&START_TIME='" + URLEncoder.encode(startDate, StandardCharsets.UTF_8) + "'"
                + "&STOP_TIME='" + URLEncoder.encode(stopDate, StandardCharsets.UTF_8) + "'"
                + "&STEP_SIZE='1%20d'"         // 1 day step
                + "&QUANTITIES='1'"            // position + velocity
                + "&OBJ_DATA=NO"              // skip object data block to reduce size
                + "&CSV_FORMAT=NO";            // standard text format

        String rawResponse = sendGet(url);
        if (rawResponse.isEmpty()) {
            LOG.warning("Empty response from Horizons for body " + commandCode);
            return new Vector3f(0, 0, 0);
        }

        Vector3f position = parseHorizonsResponse(rawResponse);
        LOG.info("Body " + commandCode + " position (AU): " + position);
        return position;
    }

    // ------------------------------------------------------------------ ESA placeholder

    /**
     * Placeholder for ESA Space Situational Awareness portal integration.
     *
     * @return empty map in stub mode
     */
    public Map<String, Vector3f> fetchPositionsESA() {
        // TODO: Implement ESA SSO API integration
        //  - ESA provides REST endpoints under the SSA portal
        //  - May require API key / registration
        //  - Endpoint: ESA_SSO_API + "/ephemeris/..."
        LOG.info("fetchPositionsESA() called — stub mode, returning empty map");
        return Collections.emptyMap();
    }

    // ------------------------------------------------------------------ internal helpers

    /**
     * Send a GET request and return the response body as a String.
     * Visible for testing / future override.
     */
    String sendGet(String url) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(30))
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(
                    request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                return response.body();
            } else {
                LOG.warning("HTTP " + response.statusCode() + " from " + url);
                return "";
            }
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Failed to fetch " + url, e);
            return "";
        }
    }

    /**
     * Parse the Horizons API text response and extract heliocentric XYZ in AU.
     *
     * @param rawResponse full text response from Horizons API
     * @return position vector, or zero vector if parsing fails
     */
    Vector3f parseHorizonsResponse(String rawResponse) {
        // Find the ephemeris data block between $$SOE and $$EOE
        int soeIdx = rawResponse.indexOf("$$SOE");
        int eoeIdx = rawResponse.indexOf("$$EOE");

        if (soeIdx < 0 || eoeIdx < 0 || eoeIdx <= soeIdx) {
            LOG.warning("Could not find $$SOE/$$EOE markers in Horizons response");
            return new Vector3f(0, 0, 0);
        }

        String dataBlock = rawResponse.substring(soeIdx + 5, eoeIdx).trim();
        if (dataBlock.isEmpty()) {
            LOG.warning("Empty data block between $$SOE/$$EOE");
            return new Vector3f(0, 0, 0);
        }

        // Horizons VECTORS output format (QUANTITIES='1'):
        //   JDTDB line
        //   Calendar date line
        //   X =... Y =... Z =...     (all on one line)
        //   VX=... VY=... VZ=...
        //   LT=... RG=... RR=...
        // We only need the first X/Y/Z line from the first entry.

        java.util.regex.Pattern xyzPattern = java.util.regex.Pattern.compile(
                "X\\s*=\\s*([+-]?\\d+\\.\\d+E[+-]\\d+)\\s+"
              + "Y\\s*=\\s*([+-]?\\d+\\.\\d+E[+-]\\d+)\\s+"
              + "Z\\s*=\\s*([+-]?\\d+\\.\\d+E[+-]\\d+)"
        );

        java.util.regex.Matcher m = xyzPattern.matcher(dataBlock);
        if (!m.find()) {
            LOG.warning("Could not parse X/Y/Z from Horizons data block");
            return new Vector3f(0, 0, 0);
        }

        try {
            double xKm = Double.parseDouble(m.group(1));
            double yKm = Double.parseDouble(m.group(2));
            double zKm = Double.parseDouble(m.group(3));

            // Convert km → AU
            float xAu = (float) (xKm / AU_KM);
            float yAu = (float) (yKm / AU_KM);
            float zAu = (float) (zKm / AU_KM);

            LOG.fine(String.format("Parsed position (km): X=%.3e Y=%.3e Z=%.3e → AU: (%.6f, %.6f, %.6f)",
                    xKm, yKm, zKm, xAu, yAu, zAu));

            return new Vector3f(xAu, yAu, zAu);
        } catch (NumberFormatException e) {
            LOG.log(Level.WARNING, "Failed to parse X/Y/Z numeric values", e);
            return new Vector3f(0, 0, 0);
        }
    }

    /**
     * Parse the Horizons API text response and extract heliocentric XYZ (in AU)
     * AND VX/VY/VZ (in km/s, converted to AU/day).
     *
     * <p>Horizons VECTORS output (QUANTITIES='1') looks like:
     * <pre>
     *   JDTDB
     *   Calendar Date (TDB)
     *   X = 1.234E+07 Y = 2.345E+07 Z = 3.456E+07
     *   VX= 1.234E+01 VY= 2.345E+01 VZ= 3.456E+01
     *   LT= ... RG= ... RR= ...
     * </pre>
     *
     * @param rawResponse full text response from Horizons API
     * @return {@link Ephemeris} with position (AU) and velocity (AU/day),
     *         or zero ephemeris if parsing fails
     */
    Ephemeris parseHorizonsResponseWithVelocity(String rawResponse) {
        int soeIdx = rawResponse.indexOf("$$SOE");
        int eoeIdx = rawResponse.indexOf("$$EOE");

        if (soeIdx < 0 || eoeIdx < 0 || eoeIdx <= soeIdx) {
            LOG.warning("Could not find $$SOE/$$EOE markers in Horizons response");
            return new Ephemeris(new Vector3f(0, 0, 0), new Vector3f(0, 0, 0));
        }

        String dataBlock = rawResponse.substring(soeIdx + 5, eoeIdx).trim();
        if (dataBlock.isEmpty()) {
            LOG.warning("Empty data block between $$SOE/$$EOE");
            return new Ephemeris(new Vector3f(0, 0, 0), new Vector3f(0, 0, 0));
        }

        // Match X/Y/Z (position in km)
        java.util.regex.Pattern xyzPattern = java.util.regex.Pattern.compile(
                "X\\s*=\\s*([+-]?\\d+\\.\\d+E[+-]\\d+)\\s+"
              + "Y\\s*=\\s*([+-]?\\d+\\.\\d+E[+-]\\d+)\\s+"
              + "Z\\s*=\\s*([+-]?\\d+\\.\\d+E[+-]\\d+)"
        );

        // Match VX/VY/VZ (velocity in km/s)
        java.util.regex.Pattern vxyzPattern = java.util.regex.Pattern.compile(
                "VX\\s*=\\s*([+-]?\\d+\\.\\d+E[+-]\\d+)\\s+"
              + "VY\\s*=\\s*([+-]?\\d+\\.\\d+E[+-]\\d+)\\s+"
              + "VZ\\s*=\\s*([+-]?\\d+\\.\\d+E[+-]\\d+)"
        );

        java.util.regex.Matcher posMatcher = xyzPattern.matcher(dataBlock);
        if (!posMatcher.find()) {
            LOG.warning("Could not parse X/Y/Z from Horizons data block");
            return new Ephemeris(new Vector3f(0, 0, 0), new Vector3f(0, 0, 0));
        }

        Vector3f position;
        try {
            double xKm = Double.parseDouble(posMatcher.group(1));
            double yKm = Double.parseDouble(posMatcher.group(2));
            double zKm = Double.parseDouble(posMatcher.group(3));
            position = new Vector3f(
                    (float) (xKm / AU_KM),
                    (float) (yKm / AU_KM),
                    (float) (zKm / AU_KM)
            );
        } catch (NumberFormatException e) {
            LOG.log(Level.WARNING, "Failed to parse X/Y/Z numeric values", e);
            return new Ephemeris(new Vector3f(0, 0, 0), new Vector3f(0, 0, 0));
        }

        // Velocity is optional — Horizons always returns it for VECTORS but be defensive
        Vector3f velocity = new Vector3f(0, 0, 0);
        java.util.regex.Matcher velMatcher = vxyzPattern.matcher(dataBlock);
        if (velMatcher.find()) {
            try {
                double vxKmS = Double.parseDouble(velMatcher.group(1));
                double vyKmS = Double.parseDouble(velMatcher.group(2));
                double vzKmS = Double.parseDouble(velMatcher.group(3));
                // Convert km/s → AU/day: 1 km/s = 86400 km/day, divide by AU_KM
                double kmPerDayToAuPerDay = 86400.0 / AU_KM;
                velocity = new Vector3f(
                        (float) (vxKmS * kmPerDayToAuPerDay),
                        (float) (vyKmS * kmPerDayToAuPerDay),
                        (float) (vzKmS * kmPerDayToAuPerDay)
                );
                LOG.fine(String.format(
                        "Parsed velocity (km/s): VX=%.3e VY=%.3e VZ=%.3e → AU/day: (%.10f, %.10f, %.10f)",
                        vxKmS, vyKmS, vzKmS, velocity.x, velocity.y, velocity.z));
            } catch (NumberFormatException e) {
                LOG.log(Level.WARNING, "Failed to parse VX/VY/VZ numeric values, using zero velocity", e);
            }
        } else {
            LOG.warning("Could not parse VX/VY/VZ from Horizons data block, using zero velocity");
        }

        return new Ephemeris(position, velocity);
    }

    /**
     * Immutable record holding a body's heliocentric position (AU) and
     * velocity (AU/day) at a given instant.
     */
    public static final class Ephemeris {
        public final Vector3f position;
        public final Vector3f velocity;

        public Ephemeris(Vector3f position, Vector3f velocity) {
            this.position = position;
            this.velocity = velocity;
        }

        @Override
        public String toString() {
            return "Ephemeris{pos=" + position + " AU, vel=" + velocity + " AU/day}";
        }
    }
}
