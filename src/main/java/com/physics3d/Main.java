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
            6.96e8f     // Radius in meters
        );
        
        // Earth
        CelestialBody earth = new CelestialBody(
            "Earth",
            new Vector3f(1.496e11f, 0, 0),  // 1 AU from sun
            new Vector3f(0, 0, 29780),      // Orbital velocity
            5.972e24f,  // Mass in kg
            6.371e6f    // Radius in meters
        );
        
        // Mars
        CelestialBody mars = new CelestialBody(
            "Mars",
            new Vector3f(2.279e11f, 0, 0),  // 1.52 AU from sun
            new Vector3f(0, 0, 24070),      // Orbital velocity
            6.417e23f,  // Mass in kg
            3.389e6f    // Radius in meters
        );
        
        // Add bodies to engine
        engine.addBody(sun);
        engine.addBody(earth);
        engine.addBody(mars);
        
        // Initialize renderer
        Renderer renderer = new Renderer("Solar System Simulator", 1280, 720);
        
        // Main simulation loop
        double deltaTime = 0.01; // 10ms per frame
        long lastTime = System.nanoTime();
        
        while (!renderer.shouldClose()) {
            long currentTime = System.nanoTime();
            deltaTime = (currentTime - lastTime) / 1e9;
            lastTime = currentTime;
            
            // Update physics
            engine.update(deltaTime);
            
            // Render
            renderer.render(engine.getBodies());
        }
        
        renderer.cleanup();
        System.out.println("Simulation ended");
    }
}