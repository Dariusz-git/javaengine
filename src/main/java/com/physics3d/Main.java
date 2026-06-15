package com.physics3d;

import com.physics3d.engine.PhysicsEngine;
import com.physics3d.graphics.Renderer;
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
                0           // MeanAnomaly
        );
        
        // Earth
        CelestialBody earth = new CelestialBody(
                "Earth",
                new Vector3f(1.471e11f, 0, 0),  // Pericenter: a*(1-e) = 1.496e11 * (1-0.0167)
                new Vector3f(0, 0, 30290),      // Prędkość na pericenter (szybciej)
                5.972e24f,
                6.371e6f,
                1.496e11,   // SemiMajorAxis
                0.0167,     // Eccentricity
                0,          // Inclination
                0,          // AscendingNode
                0,          // ArgOfPericenter
                0           // MeanAnomaly (= 0 na pericenter)
        );
        
        // Mars
        CelestialBody mars = new CelestialBody(
                "Mars",
                new Vector3f(2.066e11f, 0, 0),  // Pericenter: a*(1-e) = 2.279e11 * (1-0.0934)
                new Vector3f(0, 0, 26500),      // Prędkość na pericenter
                6.417e23f,
                3.389e6f,
                2.279e11,   // SemiMajorAxis
                0.0934,     // Eccentricity
                0,          // Inclination
                0,          // AscendingNode
                0,          // ArgOfPericenter
                0           // MeanAnomaly
        );

        // Add bodies
        engine.addBody(sun);
        engine.addBody(earth);
        engine.addBody(mars);

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