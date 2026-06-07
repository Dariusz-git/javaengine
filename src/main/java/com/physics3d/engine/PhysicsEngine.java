package com.physics3d.engine;

import com.physics3d.model.CelestialBody;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

/**
 * Core physics engine that handles gravitational interactions
 * Uses Newton's law of universal gravitation: F = G * (m1 * m2) / r^2
 *
 * All calculations are done in double precision. Astronomical masses
 * (e.g. the Sun is ~2e30 kg) overflow a float when multiplied together,
 * producing Infinity and then NaN, so float must not be used here.
 */
public class PhysicsEngine {
    private static final double GRAVITATIONAL_CONSTANT = 6.674e-11;
    private static final double TIME_SCALE = 1e5; // Speed up simulation
    private static final double MIN_DISTANCE = 1e6; // Avoid singularity

    private final List<CelestialBody> bodies;
    private double[][] forces; // [bodyIndex][x,y,z]

    public PhysicsEngine() {
        this.bodies = new ArrayList<>();
    }

    public void addBody(CelestialBody body) {
        bodies.add(body);
    }

    public void update(double deltaTime) {
        if (bodies.isEmpty()) return;

        int n = bodies.size();
        forces = new double[n][3];

        // Calculate gravitational forces between all bodies
        calculateGravitationalForces();

        double dt = deltaTime * TIME_SCALE;

        // Update positions and velocities (Euler integration, in double)
        for (int i = 0; i < n; i++) {
            CelestialBody body = bodies.get(i);
            double mass = body.getMass();

            // F = ma, so a = F/m
            double ax = forces[i][0] / mass;
            double ay = forces[i][1] / mass;
            double az = forces[i][2] / mass;

            Vector3f v = body.getVelocity();
            // v = v + a*dt
            double vx = v.x + ax * dt;
            double vy = v.y + ay * dt;
            double vz = v.z + az * dt;
            body.setVelocity(new Vector3f((float) vx, (float) vy, (float) vz));

            Vector3f p = body.getPosition();
            // p = p + v*dt
            double px = p.x + vx * dt;
            double py = p.y + vy * dt;
            double pz = p.z + vz * dt;
            body.setPosition(new Vector3f((float) px, (float) py, (float) pz));
        }
    }

    private void calculateGravitationalForces() {
        for (int i = 0; i < bodies.size(); i++) {
            for (int j = i + 1; j < bodies.size(); j++) {
                CelestialBody body1 = bodies.get(i);
                CelestialBody body2 = bodies.get(j);

                // Vector from body1 to body2 (double precision)
                double dx = (double) body2.getPosition().x - body1.getPosition().x;
                double dy = (double) body2.getPosition().y - body1.getPosition().y;
                double dz = (double) body2.getPosition().z - body1.getPosition().z;

                double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);

                // Avoid division by zero / singularity
                if (distance < MIN_DISTANCE) continue;

                // F = G * (m1 * m2) / r^2  (computed in double to avoid overflow)
                double forceMagnitude = GRAVITATIONAL_CONSTANT
                    * ((double) body1.getMass() * (double) body2.getMass())
                    / (distance * distance);

                // Normalized direction * magnitude
                double fx = (dx / distance) * forceMagnitude;
                double fy = (dy / distance) * forceMagnitude;
                double fz = (dz / distance) * forceMagnitude;

                // Newton's third law
                forces[i][0] += fx;
                forces[i][1] += fy;
                forces[i][2] += fz;
                forces[j][0] -= fx;
                forces[j][1] -= fy;
                forces[j][2] -= fz;
            }
        }
    }

    public List<CelestialBody> getBodies() {
        return bodies;
    }
}