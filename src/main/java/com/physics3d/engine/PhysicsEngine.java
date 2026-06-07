package com.physics3d.engine;

import com.physics3d.model.CelestialBody;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

/**
 * Core physics engine that handles gravitational interactions
 * Uses Newton's law of universal gravitation: F = G * (m1 * m2) / r^2
 */
public class PhysicsEngine {
    private static final float GRAVITATIONAL_CONSTANT = 6.674e-11f;
    private static final float TIME_SCALE = 1e6f; // Speed up simulation
    
    private List<CelestialBody> bodies;
    private Vector3f[] forces;
    
    public PhysicsEngine() {
        this.bodies = new ArrayList<>();
    }
    
    public void addBody(CelestialBody body) {
        bodies.add(body);
    }
    
    public void update(double deltaTime) {
        if (bodies.isEmpty()) return;
        
        // Initialize forces array
        forces = new Vector3f[bodies.size()];
        for (int i = 0; i < forces.length; i++) {
            forces[i] = new Vector3f(0, 0, 0);
        }
        
        // Calculate gravitational forces between all bodies
        calculateGravitationalForces();
        
        // Update positions and velocities
        for (int i = 0; i < bodies.size(); i++) {
            CelestialBody body = bodies.get(i);
            Vector3f force = forces[i];
            
            // F = ma, so a = F/m
            Vector3f acceleration = new Vector3f(force).div(body.getMass());
            
            // Update velocity: v = v + a*dt
            Vector3f newVelocity = new Vector3f(body.getVelocity())
                .add(new Vector3f(acceleration).mul((float) deltaTime * TIME_SCALE));
            body.setVelocity(newVelocity);
            
            // Update position: p = p + v*dt
            Vector3f newPosition = new Vector3f(body.getPosition())
                .add(new Vector3f(newVelocity).mul((float) deltaTime * TIME_SCALE));
            body.setPosition(newPosition);
        }
    }
    
    private void calculateGravitationalForces() {
        for (int i = 0; i < bodies.size(); i++) {
            for (int j = i + 1; j < bodies.size(); j++) {
                CelestialBody body1 = bodies.get(i);
                CelestialBody body2 = bodies.get(j);
                
                // Vector from body1 to body2
                Vector3f direction = new Vector3f(body2.getPosition())
                    .sub(body1.getPosition());
                float distance = direction.length();
                
                // Avoid division by zero
                if (distance < 1e6f) continue;
                
                // Normalize direction
                direction.normalize();
                
                // Calculate force magnitude: F = G * (m1 * m2) / r^2
                float forceMagnitude = GRAVITATIONAL_CONSTANT * 
                    (body1.getMass() * body2.getMass()) / 
                    (distance * distance);
                
                // Apply force to both bodies (Newton's third law)
                Vector3f force = new Vector3f(direction).mul(forceMagnitude);
                forces[i].add(force);
                forces[j].sub(force);
            }
        }
    }
    
    public List<CelestialBody> getBodies() {
        return bodies;
    }
}