package com.physics3d.engine;

import com.physics3d.model.CelestialBody;
import org.joml.Matrix3f;
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
    private static final double DEFAULT_TIME_SCALE = 1e5; // Default simulation speed
    private static final double MIN_TIME_SCALE = 0;       // Pause
    private static final double MAX_TIME_SCALE = 1e8;    // Upper limit to prevent instability
    private static final double MIN_DISTANCE = 1e6; // Avoid singularity

    private double timeScale = DEFAULT_TIME_SCALE;

    // Universe age tracking - starts at the current real age of the universe (~13.82 billion years)
    // and accumulates simulated time as the simulation runs.
    private static final double SECONDS_PER_YEAR = 365.25 * 24.0 * 3600.0;
    private static final double INITIAL_UNIVERSE_AGE_YEARS = 13.823473323e9;
    private double universeAgeSeconds = INITIAL_UNIVERSE_AGE_YEARS * SECONDS_PER_YEAR;

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

        double dt = deltaTime * timeScale;

        // Accumulate simulated time into the universal age tracker
        universeAgeSeconds += dt;

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
                body.recordPosition(getUniverseAgeYears());
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
        double smallDt = dt / timeScale / 10.0; // 10x mniejszy krok
        for (int j = 0; j < 10; j++) {
            this.update(smallDt);
        }
        orbitPositions.add(new Vector3f(body.getPosition()));

        // Simulate forward and record positions
        for (int i = 0; i < numPoints; i++) {
            orbitPositions.add(new Vector3f(body.getPosition()));
            this.update(dt / timeScale); // Use smaller timestep for accuracy
        }

        // Restore original state
        body.setPosition(origPos);
        body.setVelocity(origVel);

        return orbitPositions;
    }


    /**
     * Compute the initial position and velocity of a body so that it lies exactly
     * on its Keplerian orbit defined by the orbital elements (a, e, i, Ω, ω, M).
     *
     * Position is evaluated at the true anomaly θ = M (mean anomaly), which is
     * exact when M = 0 (body starts at pericenter). For non-zero M the same
     * formula still gives a point on the orbit, just at a different phase.
     *
     * Velocity magnitude comes from the vis-viva equation at the body's current
     * radius: v = sqrt(GM · (2/r − 1/a)). The velocity is perpendicular to the
     * position vector in the orbital plane and points in the direction of motion.
     *
     * @param body    Body whose position/velocity should be set
     * @param sunMass Mass of the central body (kg)
     */
    public void computeOrbitalState(CelestialBody body, double sunMass) {
        double a = body.getSemiMajorAxis();
        double e = body.getEccentricity();
        double i = body.getInclination();
        double ascNode = body.getAscendingNode();
        double argPeri = body.getArgOfPericenter();
        double meanAnom = body.getMeanAnomaly();

        if (a < 1e6) {
            // Sun or invalid orbit — leave at origin
            body.setPosition(new Vector3f(0, 0, 0));
            body.setVelocity(new Vector3f(0, 0, 0));
            return;
        }

        // True anomaly θ equals mean anomaly M only for circular orbits (e = 0).
        // For elliptical orbits we solve Kepler's equation M = E − e·sin(E).
        double theta;
        if (e < 1e-9) {
            theta = meanAnom;
        } else {
            double E = meanAnom;
            // Newton-Raphson iteration (5 iterations is plenty for any planetary e)
            for (int k = 0; k < 5; k++) {
                double dE = (E - e * Math.sin(E) - meanAnom) / (1.0 - e * Math.cos(E));
                E -= dE;
            }
            theta = 2.0 * Math.atan2(Math.sqrt(1 + e) * Math.sin(E / 2.0),
                                     Math.sqrt(1 - e) * Math.cos(E / 2.0));
        }

        // Radius at this true anomaly
        double r = (a * (1 - e * e)) / (1 + e * Math.cos(theta));

        // Position in the orbital plane (XZ plane, Y = 0)
        double x_orb = r * Math.cos(theta);
        double z_orb = r * Math.sin(theta);

        // Velocity in the orbital plane — derived from the specific angular
        // momentum h = sqrt(GM · a · (1 − e²)). The radial and tangential
        // components of velocity are:
        //   v_r = (GM/h) · e · sin θ
        //   v_θ = (GM/h) · (1 + e · cos θ)
        // In Cartesian form (with θ measured from pericenter, in the XZ plane):
        //   v_x = v_r·cos θ − v_θ·sin θ = −(GM/h) · sin θ
        //   v_z = v_r·sin θ + v_θ·cos θ =  (GM/h) · (e + cos θ)
        //
        // This is the correct closed-form velocity. It is equivalent to the
        // vis-viva speed applied to a UNIT direction vector, so it produces
        // a velocity whose magnitude matches vis-viva exactly. The previous
        // implementation multiplied vis-viva by the non-unit vector
        // (−sin θ, e + cos θ), whose length is sqrt(1 + e² + 2e·cos θ),
        // which over-estimates the speed (by ~9% near apocenter for Mars)
        // and causes the dynamic orbit to drift outward.
        double h = Math.sqrt(GRAVITATIONAL_CONSTANT * sunMass * a * (1.0 - e * e));
        double vFactor = (GRAVITATIONAL_CONSTANT * sunMass) / h;
        double vx_orb = -vFactor * Math.sin(theta);
        double vz_orb =  vFactor * (e + Math.cos(theta));

        // Same 3-1-3 rotation used by generateKeplerianOrbit
        Matrix3f orbitalRotation = new Matrix3f()
                .rotationZ((float) argPeri)
                .rotateX((float) i)
                .rotateZ((float) ascNode);

        Vector3f pos = new Vector3f((float) x_orb, 0.0f, (float) z_orb);
        Vector3f vel = new Vector3f((float) vx_orb, 0.0f, (float) vz_orb);
        orbitalRotation.transform(pos);
        orbitalRotation.transform(vel);

        body.setPosition(pos);
        body.setVelocity(vel);
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
        double i = body.getInclination();
        double ascNode = body.getAscendingNode();
        double argPeri = body.getArgOfPericenter();
        double meanAnom = body.getMeanAnomaly();

        if (a < 1e6) {
            System.out.println("WARNING: " + body.getName() + " has invalid orbit");
            return orbitPositions;
        }

        // Pre-compute the orbital rotation matrix: Rz(Ω) · Rx(i) · Rz(ω)
        // This is the standard 3-1-3 Euler sequence used in celestial mechanics.
        // JOML Matrix3f uses float, so we cast the double angles once here.
        Matrix3f orbitalRotation = new Matrix3f()
                .rotationZ((float) argPeri)   // Rz(ω): rotate by argument of pericenter
                .rotateX((float) i)           // Rx(i): tilt by inclination
                .rotateZ((float) ascNode);    // Rz(Ω): rotate by longitude of ascending node

        for (int idx = 0; idx <= numPoints; idx++) {
            double theta = meanAnom + (idx / (double) numPoints) * 2 * Math.PI;
            double r_polar = (a * (1 - e * e)) / (1 + e * Math.cos(theta));

            // Position in the orbital plane (XZ plane, Y=0)
            float x_orb = (float) (r_polar * Math.cos(theta));
            float z_orb = (float) (r_polar * Math.sin(theta));

            // Apply the pre-computed 3D rotation, then offset to sun position
            Vector3f point = new Vector3f(x_orb, 0.0f, z_orb);
            orbitalRotation.transform(point);
            point.add(sunPosition);

            orbitPositions.add(point);
        }

        return orbitPositions;
    }

    /** Get the current time scale multiplier. */
    public double getTimeScale() {
        return timeScale;
    }

    /** Set the time scale multiplier, clamped to [MIN_TIME_SCALE, MAX_TIME_SCALE]. */
    public void setTimeScale(double scale) {
        timeScale = Math.max(MIN_TIME_SCALE, Math.min(MAX_TIME_SCALE, scale));
    }

    /** Multiply the time scale by a factor, clamped to limits. */
    public void multiplyTimeScale(double factor) {
        setTimeScale(timeScale * factor);
    }

    /** Get the minimum allowed time scale. */
    public double getMinTimeScale() {
        return MIN_TIME_SCALE;
    }

    /** Get the maximum allowed time scale. */
    public double getMaxTimeScale() {
        return MAX_TIME_SCALE;
    }

    /** Get the default time scale. */
    public double getDefaultTimeScale() {
        return DEFAULT_TIME_SCALE;
    }

    /** Get the current age of the universe in years (accumulated simulated time). */
    public double getUniverseAgeYears() {
        return universeAgeSeconds / SECONDS_PER_YEAR;
    }

    /** Reset the universe age back to its initial value. */
    public void resetUniverseAge() {
        universeAgeSeconds = INITIAL_UNIVERSE_AGE_YEARS * SECONDS_PER_YEAR;
    }
}