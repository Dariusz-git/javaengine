package com.physics3d.model;

import com.physics3d.engine.PhysicsEngine;
import org.joml.Vector3f;

import java.util.Queue;

/**
 * Represents a celestial body (planet, star, etc.) in the simulation
 */
public class CelestialBody {
    private String name;
    private Vector3f position;
    private Vector3f velocity;
    private float mass;        // in kg
    private float radius;      // in meters
    private BodyType bodyType; // classification (star, terrestrial, gas/ice giant)

    private double semiMajorAxis;      // a
    private double eccentricity;       // e
    private double inclination;        // i (radians)
    private double ascendingNode;      // Ω (radians)
    private double argOfPericenter;    // ω (radians)
    private double meanAnomaly;        // M (radians)
    // Orbital trail tracking
    private OrbitTrail trail = new OrbitTrail(50000); // Keep last 1000 positions

    public OrbitTrail getTrail() {
        return trail;
    }

    public void recordPosition() {
        trail.addPosition(position);
    }

    public CelestialBody(String name, Vector3f position, Vector3f velocity,
                         float mass, float radius,
                         double semiMajorAxis, double eccentricity,
                         double inclination, double ascendingNode,
                         double argOfPericenter, double meanAnomaly,
                         BodyType bodyType) {
        this.name = name;
        this.position = new Vector3f(position);
        this.velocity = new Vector3f(velocity);
        this.mass = mass;
        this.radius = radius;
        this.semiMajorAxis = semiMajorAxis;
        this.eccentricity = eccentricity;
        this.inclination = inclination;
        this.ascendingNode = ascendingNode;
        this.argOfPericenter = argOfPericenter;
        this.meanAnomaly = meanAnomaly;
        this.bodyType = bodyType;
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
    public double getEccentricity() { return eccentricity; }
    public double getSemiMajorAxis() { return semiMajorAxis; }
    public double getInclination() { return inclination; }
    public double getAscendingNode() { return ascendingNode; }
    public double getArgOfPericenter() { return argOfPericenter; }
    public double getMeanAnomaly() { return meanAnomaly; }

    public BodyType getBodyType() { return bodyType; }

    public void setOrbitalParameters(double a, double e, double i,
                                     double ascNode, double argPeri, double meanAnom) {
        this.semiMajorAxis = a;
        this.eccentricity = e;
        this.inclination = i;
        this.ascendingNode = ascNode;
        this.argOfPericenter = argPeri;
        this.meanAnomaly = meanAnom;
    }
    @Override
    public String toString() {
        return String.format("%s: pos=%.2e, vel=%.2e, mass=%.2e",
            name, position.length(), velocity.length(), mass);
    }

    public void recalculateOrbit(PhysicsEngine engine, CelestialBody sun) {
        Queue<Vector3f> newOrbit = engine.generateKeplerianOrbit(
                this,
                sun.getPosition(),
                sun.getMass(),
                10000
        );
        trail.setTheoreticalOrbit(newOrbit);
        System.out.println(this.name + " orbit recalculated!");
    }

}