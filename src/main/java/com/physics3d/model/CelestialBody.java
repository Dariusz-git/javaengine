package com.physics3d.model;

import org.joml.Vector3f;

/**
 * Represents a celestial body (planet, star, etc.) in the simulation
 */
public class CelestialBody {
    private String name;
    private Vector3f position;
    private Vector3f velocity;
    private float mass;        // in kg
    private float radius;      // in meters
    
    public CelestialBody(String name, Vector3f position, Vector3f velocity, float mass, float radius) {
        this.name = name;
        this.position = new Vector3f(position);
        this.velocity = new Vector3f(velocity);
        this.mass = mass;
        this.radius = radius;
    }
    
    // Getters and setters
    public String getName() {
        return name;
    }
    
    public Vector3f getPosition() {
        return position;
    }
    
    public void setPosition(Vector3f position) {
        this.position = new Vector3f(position);
    }
    
    public Vector3f getVelocity() {
        return velocity;
    }
    
    public void setVelocity(Vector3f velocity) {
        this.velocity = new Vector3f(velocity);
    }
    
    public float getMass() {
        return mass;
    }
    
    public float getRadius() {
        return radius;
    }
    
    @Override
    public String toString() {
        return String.format("%s: pos=%.2e, vel=%.2e, mass=%.2e",
            name, position.length(), velocity.length(), mass);
    }
}