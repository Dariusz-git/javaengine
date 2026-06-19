package com.physics3d;

import com.physics3d.data.EphemerisClient;
import com.physics3d.engine.PhysicsEngine;
import com.physics3d.graphics.Renderer;
import com.physics3d.model.BodyType;
import com.physics3d.model.CelestialBody;
import org.joml.Vector3f;

import java.util.Map;


/**
 * Main entry point for the 3D Physics Engine
 * Simulates gravitational interactions between celestial bodies
 */
public class Main {
    public static void main(String[] args) {
        // Initialize the physics engine
        PhysicsEngine engine = new PhysicsEngine();
        
        // Create celestial bodies (simplified solar system)
        // Sun
        CelestialBody sun = new CelestialBody(
                "Sun",
                new Vector3f(0, 0, 0),
                new Vector3f(0, 0, 0),
                1.989e30f,  // Mass in kg
                6.96e8f,    // Radius in meters
                0,          // SemiMajorAxis (Słońce nie orbituje)
                0,          // Eccentricity
                0,          // Inclination
                0,          // AscendingNode
                0,          // ArgOfPericenter
                0,          // MeanAnomaly
                BodyType.STAR
        );

        // Mercury — terrestrial planet, position/velocity computed from elements
        CelestialBody mercury = new CelestialBody(
                "Mercury",
                new Vector3f(0, 0, 0),  // placeholder, set by computeOrbitalState
                new Vector3f(0, 0, 0),  // placeholder, set by computeOrbitalState
                3.301e23f,
                2.439e6f,
                5.791e10,   // SemiMajorAxis
                0.2056,     // Eccentricity
                Math.toRadians(7.00),    // Inclination
                Math.toRadians(48.33),   // AscendingNode
                Math.toRadians(29.12),   // ArgOfPericenter
                0,          // MeanAnomaly (= 0 na pericenter)
                BodyType.TERRESTRIAL
        );

        // Venus — terrestrial planet
        CelestialBody venus = new CelestialBody(
                "Venus",
                new Vector3f(0, 0, 0),  // placeholder, set by computeOrbitalState
                new Vector3f(0, 0, 0),  // placeholder, set by computeOrbitalState
                4.867e24f,
                6.052e6f,
                1.082e11,   // SemiMajorAxis
                0.0068,     // Eccentricity
                Math.toRadians(3.39),    // Inclination
                Math.toRadians(76.68),   // AscendingNode
                Math.toRadians(55.0),    // ArgOfPericenter
                0,          // MeanAnomaly
                BodyType.TERRESTRIAL
        );

        // Earth — position and velocity are computed from orbital elements below
        CelestialBody earth = new CelestialBody(
                "Earth",
                new Vector3f(0, 0, 0),  // placeholder, set by computeOrbitalState
                new Vector3f(0, 0, 0),  // placeholder, set by computeOrbitalState
                5.972e24f,
                6.371e6f,
                1.496e11,   // SemiMajorAxis
                0.0167,     // Eccentricity
                Math.toRadians(0.0),     // Inclination (płaszczyzna odniesienia)
                Math.toRadians(0.0),     // AscendingNode
                Math.toRadians(102.9),   // ArgOfPericenter
                0,          // MeanAnomaly (= 0 na pericenter)
                BodyType.TERRESTRIAL
        );

        // Mars — terrestrial planet
        CelestialBody mars = new CelestialBody(
                "Mars",
                new Vector3f(0, 0, 0),  // placeholder, set by computeOrbitalState
                new Vector3f(0, 0, 0),  // placeholder, set by computeOrbitalState
                6.417e23f,
                3.389e6f,
                2.279e11,   // SemiMajorAxis
                0.0934,     // Eccentricity
                Math.toRadians(1.85),    // Inclination (1.85° do ekliptyki)
                Math.toRadians(49.6),    // AscendingNode (49.6°)
                Math.toRadians(286.5),   // ArgOfPericenter (286.5°)
                0,          // MeanAnomaly (= 0 na pericenter)
                BodyType.TERRESTRIAL
        );

        // Jupiter — gas giant
        CelestialBody jupiter = new CelestialBody(
                "Jupiter",
                new Vector3f(0, 0, 0),  // placeholder, set by computeOrbitalState
                new Vector3f(0, 0, 0),  // placeholder, set by computeOrbitalState
                1.898e27f,
                6.991e7f,
                7.785e11,   // SemiMajorAxis
                0.0489,     // Eccentricity
                Math.toRadians(1.30),    // Inclination
                Math.toRadians(100.5),   // AscendingNode
                Math.toRadians(273.9),   // ArgOfPericenter
                0,          // MeanAnomaly
                BodyType.GAS_GIANT
        );

        // Saturn — gas giant
        CelestialBody saturn = new CelestialBody(
                "Saturn",
                new Vector3f(0, 0, 0),  // placeholder, set by computeOrbitalState
                new Vector3f(0, 0, 0),  // placeholder, set by computeOrbitalState
                5.683e26f,
                5.823e7f,
                1.434e12,   // SemiMajorAxis
                0.0565,     // Eccentricity
                Math.toRadians(2.49),    // Inclination
                Math.toRadians(113.7),   // AscendingNode
                Math.toRadians(339.3),   // ArgOfPericenter
                0,          // MeanAnomaly
                BodyType.GAS_GIANT
        );

        // Uranus — ice giant
        CelestialBody uranus = new CelestialBody(
                "Uranus",
                new Vector3f(0, 0, 0),  // placeholder, set by computeOrbitalState
                new Vector3f(0, 0, 0),  // placeholder, set by computeOrbitalState
                8.681e25f,
                2.536e7f,
                2.871e12,   // SemiMajorAxis
                0.0457,     // Eccentricity
                Math.toRadians(0.77),    // Inclination
                Math.toRadians(74.0),    // AscendingNode
                Math.toRadians(96.9),    // ArgOfPericenter
                0,          // MeanAnomaly
                BodyType.ICE_GIANT
        );

        // Neptune — ice giant
        CelestialBody neptune = new CelestialBody(
                "Neptune",
                new Vector3f(0, 0, 0),  // placeholder, set by computeOrbitalState
                new Vector3f(0, 0, 0),  // placeholder, set by computeOrbitalState
                1.024e26f,
                2.462e7f,
                4.495e12,   // SemiMajorAxis
                0.0113,     // Eccentricity
                Math.toRadians(1.77),    // Inclination
                Math.toRadians(131.8),   // AscendingNode
                Math.toRadians(272.8),   // ArgOfPericenter
                0,          // MeanAnomaly
                BodyType.ICE_GIANT
        );

        // Initialize ephemeris client (NASA JPL Horizons / ESA stub)
        EphemerisClient ephemerisClient = new EphemerisClient();
        System.out.println("EphemerisClient ready — fetchPositions() returned: "
                + ephemerisClient.fetchPositions().size() + " entries (stub mode)");

        // Fetch LIVE heliocentric positions + velocities from NASA JPL Horizons.
        // These represent the *real* solar-system state for today, so seeding
        // the simulator with them makes the on-screen layout match the actual
        // sky.  Newtonian physics then continues the motion from there.
        Map<String, EphemerisClient.Ephemeris> liveEphemeris =
                ephemerisClient.fetchPositionsAndVelocities();
        System.out.println("Live Horizons ephemeris fetched for "
                + liveEphemeris.size() + " bodies — seeding simulator with real positions.");

        // Add bodies
        engine.addBody(sun);
        engine.addBody(mercury);
        engine.addBody(venus);
        engine.addBody(earth);
        engine.addBody(mars);
        engine.addBody(jupiter);
        engine.addBody(saturn);
        engine.addBody(uranus);
        engine.addBody(neptune);

        // Seed every planet with its LIVE position + velocity from Horizons.
        // If a particular body failed to fetch (network / parse error) we fall
        // back to the Keplerian orbital elements so the simulation still runs.
        applyLiveEphemeris(mercury, liveEphemeris.get("Mercury"), engine, sun);
        applyLiveEphemeris(venus,   liveEphemeris.get("Venus"),   engine, sun);
        applyLiveEphemeris(earth,   liveEphemeris.get("Earth"),   engine, sun);
        applyLiveEphemeris(mars,    liveEphemeris.get("Mars"),    engine, sun);
        applyLiveEphemeris(jupiter, liveEphemeris.get("Jupiter"), engine, sun);
        applyLiveEphemeris(saturn,  liveEphemeris.get("Saturn"),  engine, sun);
        applyLiveEphemeris(uranus,  liveEphemeris.get("Uranus"),  engine, sun);
        applyLiveEphemeris(neptune, liveEphemeris.get("Neptune"), engine, sun);

        // Derive orbital elements from live state vectors, then generate
        // theoretical Keplerian orbits that match the live data.
        System.out.println("Deriving orbital elements from live Horizons state...");
        for (CelestialBody body : engine.getBodies()) {
            if (!body.getName().equals("Sun")) {
                engine.deriveOrbitalElementsFromState(body, sun.getMass());
                body.recalculateOrbit(engine, sun);
            }
        }
        System.out.println("Orbits generated from live data!");

        // Initialize renderer
        Renderer renderer = new Renderer("Solar System Simulator", 1280, 720);
        renderer.setPhysicsEngine(engine);
        
        // Main simulation loop
        double deltaTime = 0.01; // 10ms per frame
        long lastTime = System.nanoTime();
        
        while (!renderer.shouldClose()) {
            long currentTime = System.nanoTime();
            deltaTime = (currentTime - lastTime) / 1e9;
            lastTime = currentTime;
            
            // Clamp to avoid a huge first/lag frame blowing up the integration
            if (deltaTime > 0.05) {
                deltaTime = 0.05;
            }
            
            // Update physics
            engine.update(deltaTime);
            
            // Render
            renderer.render(engine.getBodies());
        }
        
        renderer.cleanup();
        System.out.println("Simulation ended");
    }

    /** 1 AU in metres — matches the metre-based coordinate system used by
     *  PhysicsEngine (G = 6.674e-11, semiMajorAxis in metres, etc.). */
    private static final double AU_TO_METERS = 149_597_870_700.0;

    /** Convert AU/day → m/s  (AU_TO_METERS / seconds-per-day). */
    private static final double AU_PER_DAY_TO_M_PER_S = AU_TO_METERS / 86_400.0;

    /**
     * Seed a planet with its live position + velocity from NASA JPL Horizons.
     * Horizons returns position in AU and velocity in AU/day, but our physics
     * engine works in metres and m/s, so we convert here.
     *
     * <p><b>Coordinate-system note.</b>  Horizons uses the J2000 ecliptic
     * frame: the ecliptic plane is XY and Z points toward the north ecliptic
     * pole (i.e. Z is "up").  Our simulator uses XZ as the orbital reference
     * plane and Y as "up" (the angular-momentum axis for prograde orbits).
     * To make the live data line up with the simulation's convention we swap
     * the Y and Z axes when seeding — Horizons' Z (north ecliptic pole) maps
     * to the simulation's +Y (up), and Horizons' Y maps to the simulation's
     * Z.  X is unchanged because both frames share the vernal-equinox
     * direction along X.
     *
     * <p><b>Handedness note.</b>  The J2000 ecliptic frame is right-handed
     * (X × Y = Z), but our simulator's XZ-plane / Y-up frame is left-handed
     * (X × Z = −Y).  To preserve a prograde orbit (angular momentum along
     * +Y) we therefore negate the simulation's Z component when seeding.
     *
     * <p>If the live fetch failed (network error, parse error, missing entry)
     * we fall back to the Keplerian orbital elements so the simulation still
     * has a sensible starting state.
     */
    private static void applyLiveEphemeris(CelestialBody body,
                                           EphemerisClient.Ephemeris live,
                                           PhysicsEngine engine,
                                           CelestialBody sun) {
        if (live != null && live.position != null && live.velocity != null) {
            // Convert AU → metres and AU/day → m/s, AND swap Y/Z to map the
            // J2000 ecliptic frame (XY plane, Z up, right-handed) onto the
            // simulator's XZ-plane / Y-up, left-handed convention.  Negating
            // the simulation Z keeps the angular momentum pointing along +Y
            // (prograde) instead of −Y (retrograde).
            Vector3f posMeters = new Vector3f(
                    (float)(live.position.x * AU_TO_METERS),   // X stays X (vernal equinox)
                    (float)(live.position.z * AU_TO_METERS),   // Horizons Z → sim Y (up)
                    (float)(-live.position.y * AU_TO_METERS)); // Horizons Y → −sim Z (handedness flip)
            Vector3f velMps = new Vector3f(
                    (float)(live.velocity.x * AU_PER_DAY_TO_M_PER_S),
                    (float)(live.velocity.z * AU_PER_DAY_TO_M_PER_S),
                    (float)(-live.velocity.y * AU_PER_DAY_TO_M_PER_S));
            body.setPosition(posMeters);
            body.setVelocity(velMps);
            System.out.printf("  %-8s seeded from Horizons: pos=(%.3e, %.3e, %.3e) m, vel=(%.3e, %.3e, %.3e) m/s%n",
                    body.getName(),
                    posMeters.x, posMeters.y, posMeters.z,
                    velMps.x, velMps.y, velMps.z);
        } else {
            engine.computeOrbitalState(body, sun.getMass());
            System.out.printf("  %-8s live fetch unavailable — using Keplerian fallback%n",
                    body.getName());
        }
    }
}