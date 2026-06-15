package com.physics3d.engine;

import com.physics3d.model.CelestialBody;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;

/**
 * Core physics engine that handles gravitational interactions
 * Uses Newton's law of universal gravitation: F = G * (m1 * m2) / r^2
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

    private int recordCounter = 0;
    private static final int RECORD_INTERVAL = 5; // Zapisuj co 5 iteracji


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
            // Record trail position for rendering
            // body.recordPosition(); funkcja wyłączona, orbita jest teraz generowana zały czas nie trzeba zapisywać
            recordCounter++;
            if (recordCounter % RECORD_INTERVAL == 0) {
                body.recordPosition();
            }
        }

    }

    private void calculateGravitationalForces() {

        for (int i = 0; i < bodies.size(); i++) {
            for (int j = i + 1; j < bodies.size(); j++) {
                CelestialBody body1 = bodies.get(i);
                CelestialBody body2 = bodies.get(j);

                // ⭐ NOWE: Tylko Słońce przyciąga inne obiekty
                String name1 = body1.getName();
                String name2 = body2.getName();

                // Jeśli ani body1 ani body2 to nie Słońce, pomiń
                if (!name1.equals("Sun") && !name2.equals("Sun")) {
                    continue;
                }

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

    /**
     * Pre-compute orbital path for a body over a given time period
     * Returns the positions along the orbit
     */
    public Queue<Vector3f> computeOrbitalPath(CelestialBody body, double timespan, int numPoints) {
        // Save original state
        Vector3f origPos = new Vector3f(body.getPosition());
        Vector3f origVel = new Vector3f(body.getVelocity());

        Queue<Vector3f> orbitPositions = new LinkedList<>();
        double dt = timespan / numPoints;

        // Zmień na (mniejszy timestep = większa dokładność):
        double smallDt = dt / TIME_SCALE / 10.0; // 10x mniejszy krok
        for (int j = 0; j < 10; j++) {
            this.update(smallDt);
        }
        orbitPositions.add(new Vector3f(body.getPosition()));

        // Simulate forward and record positions
        for (int i = 0; i < numPoints; i++) {
            orbitPositions.add(new Vector3f(body.getPosition()));
            this.update(dt / TIME_SCALE); // Use smaller timestep for accuracy
        }

        // Restore original state
        body.setPosition(origPos);
        body.setVelocity(origVel);

        return orbitPositions;
    }


    /**
     * Generate perfect elliptical orbit using Kepler's equation
     * @param body Body to generate orbit for
     * @param sunPosition Position of the sun
     * @param sunMass Mass of the sun
     * @param numPoints Number of points to generate
     * @return Queue of positions forming the elliptical orbit
     */
    public Queue<Vector3f> generateKeplerianOrbit(CelestialBody body, Vector3f sunPosition, double sunMass, int numPoints) {
        Queue<Vector3f> orbitPositions = new LinkedList<>();

        double a = body.getSemiMajorAxis();
        double e = body.getEccentricity();
        double i = body.getInclination();        // Inclination
        double ascNode = body.getAscendingNode(); // Ascending node
        double argPeri = body.getArgOfPericenter(); // Arg of pericenter
        double meanAnom = body.getMeanAnomaly();  // Mean anomaly (start position)

        if (a < 1e6) {
            System.out.println("WARNING: " + body.getName() + " has invalid orbit");
            return orbitPositions;
        }

        // Generate orbit
        for (int idx = 0; idx <= numPoints; idx++) {
            double theta = meanAnom + (idx / (double) numPoints) * 2 * Math.PI;

            // Kepler equation: r = a(1-e²) / (1 + e*cos(θ))
            double r_polar = (a * (1 - e * e)) / (1 + e * Math.cos(theta));

            // Współrzędne w płaszczyźnie orbity (XZ plane)
            double x_orb = r_polar * Math.cos(theta);
            double z_orb = r_polar * Math.sin(theta);
            double y_orb = 0;

            // ⭐ Rotacja o argument pericenter (ω) - wokół Y osi
            double x_rot = x_orb * Math.cos(argPeri) - z_orb * Math.sin(argPeri);
            double z_rot = x_orb * Math.sin(argPeri) + z_orb * Math.cos(argPeri);
            double y_rot = y_orb;

            // ⭐ Rotacja o inclination (i) - wokół X osi (pochyla XZ->XY)
            double x_3d = x_rot;
            double y_3d = y_rot * Math.cos(i) - z_rot * Math.sin(i);
            double z_3d = y_rot * Math.sin(i) + z_rot * Math.cos(i);

            // ⭐ Rotacja o ascending node (Ω) - wokół Y osi
            double x_final = x_3d * Math.cos(ascNode) - z_3d * Math.sin(ascNode);
            double z_final = x_3d * Math.sin(ascNode) + z_3d * Math.cos(ascNode);
            double y_final = y_3d;

            // Przesuń do pozycji Słońca
            Vector3f point = new Vector3f(
                    (float)(sunPosition.x + x_final),
                    (float)(sunPosition.y + y_final),
                    (float)(sunPosition.z + z_final)
            );

            orbitPositions.add(point);
        }

        return orbitPositions;
    }
}