package com.physics3d;

import com.physics3d.engine.PhysicsEngine;
import com.physics3d.graphics.Renderer;
import com.physics3d.model.BodyType;
import com.physics3d.model.CelestialBody;
import org.joml.Vector3f;


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

        // Compute initial position and velocity from orbital elements so that
        // every planet starts exactly on its theoretical Keplerian orbit.
        // This makes the placement consistent with inclination, ascending node
        // and argument of pericenter — no matter what those values are.
        engine.computeOrbitalState(mercury, sun.getMass());
        engine.computeOrbitalState(venus,   sun.getMass());
        engine.computeOrbitalState(earth,   sun.getMass());
        engine.computeOrbitalState(mars,    sun.getMass());
        engine.computeOrbitalState(jupiter, sun.getMass());
        engine.computeOrbitalState(saturn,  sun.getMass());
        engine.computeOrbitalState(uranus,  sun.getMass());
        engine.computeOrbitalState(neptune, sun.getMass());

        // Generate theoretical orbits at startup
        System.out.println("Generating Keplerian orbits...");
        for (CelestialBody body : engine.getBodies()) {
            if (!body.getName().equals("Sun")) {
                body.recalculateOrbit(engine, sun);
            }
        }
        System.out.println("Orbits generated!");

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
}