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
    private float drawRadius;  // scaled display radius in scene units (set manually)
    private BodyType bodyType; // classification (star, terrestrial, gas/ice giant)

    private double semiMajorAxis;      // a
    private double eccentricity;       // e
    private double inclination;        // i (radians)
    private double ascendingNode;      // Ω (radians)
    private double argOfPericenter;    // ω (radians)
    private double meanAnomaly;        // M (radians)

    // ---- Axial rotation (spin around the body's own axis) ----
    // Tilt of the rotation axis from the orbital-plane normal (radians).
    // 0 = axis perpendicular to orbital plane (no tilt).
    private double axialTilt;
    // Sidereal rotation period in seconds. Negative = retrograde spin
    // (Venus, Uranus). 0 = no rotation (e.g. tidally-locked body).
    private double rotationPeriod;
    // Accumulated rotation angle around the body's spin axis (radians).
    // Wraps naturally via the advanceRotation() method.
    private double rotationAngle;
    // Direction of the spin axis in the body's local frame. Defaults to +Y
    // (the "north pole" of the unit sphere drawn by drawSphere). The tilt
    // is applied as a rotation of this axis away from the orbital-plane
    // normal at render time.
    private final Vector3f spinAxis = new Vector3f(0, 1, 0);

    // OpenGL texture id assigned by TextureManager; -1 means "not yet loaded".
    private int textureId = -1;
    // Earth-specific multi-texture ids (day texture is the primary textureId above)
    private int nightTextureId = -1;
    private int cloudTextureId = -1;
    // Saturn ring texture id (alpha-blended ring overlay). -1 = no rings.
    private int ringTextureId = -1;
    // Orbital trail tracking
    private OrbitTrail trail = new OrbitTrail(50000); // Keep last 1000 positions

    public OrbitTrail getTrail() {
        return trail;
    }

    public void recordPosition() {
        trail.addPosition(position, 0.0);
    }

    /**
     * Records the current position with the given simulation time (in years) for age-based fading.
     */
    public void recordPosition(double simulationTimeYears) {
        trail.addPosition(position, simulationTimeYears);
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

    /** Update the body's mass (kg). Affects gravitational interactions in the physics engine. */
    public void setMass(float mass) {
        this.mass = mass;
    }

    public float getRadius() {
        return radius;
    }

    /** Scaled display radius in scene units (set per-body for visual proportion). */
    public float getDrawRadius() {
        return drawRadius;
    }
    public void setDrawRadius(float drawRadius) {
        this.drawRadius = drawRadius;
    }
    public double getEccentricity() { return eccentricity; }
    public double getSemiMajorAxis() { return semiMajorAxis; }
    public double getInclination() { return inclination; }
    public double getAscendingNode() { return ascendingNode; }
    public double getArgOfPericenter() { return argOfPericenter; }
    public double getMeanAnomaly() { return meanAnomaly; }

    public BodyType getBodyType() { return bodyType; }

    public int getTextureId() { return textureId; }
    public void setTextureId(int textureId) { this.textureId = textureId; }

    public int getNightTextureId() { return nightTextureId; }
    public void setNightTextureId(int nightTextureId) { this.nightTextureId = nightTextureId; }

    public int getCloudTextureId() { return cloudTextureId; }
    public void setCloudTextureId(int cloudTextureId) { this.cloudTextureId = cloudTextureId; }

    public int getRingTextureId() { return ringTextureId; }
    public void setRingTextureId(int ringTextureId) { this.ringTextureId = ringTextureId; }

    public void setOrbitalParameters(double a, double e, double i,
                                     double ascNode, double argPeri, double meanAnom) {
        this.semiMajorAxis = a;
        this.eccentricity = e;
        this.inclination = i;
        this.ascendingNode = ascNode;
        this.argOfPericenter = argPeri;
        this.meanAnomaly = meanAnom;
    }

    // ---- Axial rotation accessors ----
    public double getAxialTilt() { return axialTilt; }
    public void setAxialTilt(double axialTilt) { this.axialTilt = axialTilt; }

    public double getRotationPeriod() { return rotationPeriod; }
    public void setRotationPeriod(double rotationPeriod) { this.rotationPeriod = rotationPeriod; }

    public double getRotationAngle() { return rotationAngle; }
    public void setRotationAngle(double rotationAngle) { this.rotationAngle = rotationAngle; }

    /**
     * Advance the body's spin by {@code deltaTime} seconds. The angular
     * velocity is {@code 2π / rotationPeriod}; a negative period yields
     * retrograde rotation. Bodies with a period of zero (or matching the
     * deltaTime exactly) are left untouched.
     */
    public void advanceRotation(double deltaTime) {
        if (rotationPeriod == 0.0) {
            return;
        }
        rotationAngle += (2.0 * Math.PI / rotationPeriod) * deltaTime;
        // Keep the angle bounded to avoid float-precision drift over very long runs.
        if (rotationAngle > 1e6 || rotationAngle < -1e6) {
            rotationAngle %= (2.0 * Math.PI);
        }
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