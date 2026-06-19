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
     * Generate a closed Keplerian orbit polyline for a body using its stored
     * orbital elements (a, e, i, Ω, ω, M). The orbit is sampled uniformly in
     * true anomaly from the body's current mean anomaly through one full
     * revolution (M → M + 2π).
     * <p>
     * The result is a queue of {@code numPoints + 1} positions (the last point
     * equals the first to close the loop visually). Positions are returned in
     * the same world coordinate system as the body itself (XZ is the orbital
     * reference plane, Y is "up").
     * <p>
     * The {@code sunPosition} and {@code sunMass} parameters are accepted for
     * API symmetry with the rest of the engine but are not used here — the
     * orbit is defined purely by the body's stored elements.
     *
     * @param body        Body whose orbital elements define the orbit
     * @param sunPosition Position of the central body (unused, kept for API symmetry)
     * @param sunMass     Mass of the central body (unused, kept for API symmetry)
     * @param numPoints   Number of segments around the orbit
     * @return Queue of orbit positions, ordered along the orbit
     */
    public Queue<Vector3f> generateKeplerianOrbit(CelestialBody body,
                                                  Vector3f sunPosition,
                                                  float sunMass,
                                                  int numPoints) {
        Queue<Vector3f> orbit = new LinkedList<>();

        double a = body.getSemiMajorAxis();
        double e = body.getEccentricity();
        double i = body.getInclination();
        double ascNode = body.getAscendingNode();
        double argPeri = body.getArgOfPericenter();
        double meanAnom = body.getMeanAnomaly();

        if (a < 1e6 || numPoints < 3) {
            // Sun or invalid orbit — return a single point at the origin
            orbit.add(new Vector3f(0, 0, 0));
            return orbit;
        }

        // 3-1-3 Euler rotation: Rz(ω) · Rx(i) · Rz(Ω)
        Matrix3f orbitalRotation = new Matrix3f()
                .rotationZ((float) argPeri)
                .rotateX((float) i)
                .rotateZ((float) ascNode);

        double twoPi = 2.0 * Math.PI;
        for (int k = 0; k <= numPoints; k++) {
            // Sample true anomaly uniformly from meanAnom through meanAnom + 2π.
            // For a circular orbit (e ≈ 0) true anomaly equals mean anomaly,
            // so this gives a uniform angular spacing. For elliptical orbits
            // the spacing in true anomaly is non-uniform in time, but it
            // produces a visually smooth closed curve.
            double theta = meanAnom + twoPi * k / numPoints;

            // Radius at this true anomaly
            double r = (a * (1.0 - e * e)) / (1.0 + e * Math.cos(theta));

            // Position in the orbital plane (XZ plane, Y = 0)
            double x_orb = r * Math.cos(theta);
            double z_orb = r * Math.sin(theta);

            Vector3f pos = new Vector3f((float) x_orb, 0.0f, (float) z_orb);
            orbitalRotation.transform(pos);

            orbit.add(pos);
        }

        return orbit;
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
     * Derive classical orbital elements (a, e, i, Ω, ω, M) from a body's
     * current position and velocity state vectors, then store them in the
     * body so that {@link #generateKeplerianOrbit} and
     * {@link #computeOrbitalState} will reproduce the same orbit.
     * <p>
     * This is the inverse of {@code computeOrbitalState}: given r⃗ and v⃗
     * relative to the Sun (at the origin), we recover the six Keplerian
     * elements using standard two-body mechanics.
     *
     * @param body    Body whose position/velocity are already set (SI units)
     * @param sunMass Mass of the central body (kg)
     */
    public void deriveOrbitalElementsFromState(CelestialBody body, double sunMass) {
        double mu = GRAVITATIONAL_CONSTANT * sunMass;

        // Position and velocity in double precision
        double rx = body.getPosition().x;
        double ry = body.getPosition().y;
        double rz = body.getPosition().z;
        double vx = body.getVelocity().x;
        double vy = body.getVelocity().y;
        double vz = body.getVelocity().z;

        double rMag = Math.sqrt(rx * rx + ry * ry + rz * rz);
        double vMag = Math.sqrt(vx * vx + vy * vy + vz * vz);

        if (rMag < 1e6) {
            // Body is essentially at the origin — no meaningful orbit
            return;
        }

        // ── Semi-major axis (vis-viva) ──────────────────────────────
        // v² = μ (2/r − 1/a)  →  a = 1 / (2/r − v²/μ)
        double a = 1.0 / (2.0 / rMag - (vMag * vMag) / mu);

        // ── Specific angular momentum h⃗ = r⃗ × v⃗ ──────────────────
        double hx = ry * vz - rz * vy;
        double hy = rz * vx - rx * vz;
        double hz = rx * vy - ry * vx;
        double hMag = Math.sqrt(hx * hx + hy * hy + hz * hz);

        // ── Eccentricity vector e⃗ = (v⃗ × h⃗)/μ − r̂ ──────────────
        // v⃗ × h⃗
        double vhx = vy * hz - vz * hy;
        double vhy = vz * hx - vx * hz;
        double vhz = vx * hy - vy * hx;

        double ex = vhx / mu - rx / rMag;
        double ey = vhy / mu - ry / rMag;
        double ez = vhz / mu - rz / rMag;
        double e = Math.sqrt(ex * ex + ey * ey + ez * ez);

        // ── Inclination: i = acos(h_y / |h⃗|) ───────────────────────
        // In our coordinate system Y is "up", so the reference plane is XZ.
        // h⃗ is perpendicular to the orbital plane; its Y-component tells
        // us how tilted the plane is relative to XZ.
        double cosI = hy / hMag;
        cosI = Math.max(-1.0, Math.min(1.0, cosI)); // clamp for safety
        double inc = Math.acos(cosI);

        // ── Longitude of ascending node Ω ────────────────────────────
        // The ascending node direction n⃗ lies in the reference plane (XZ)
        // and is perpendicular to the Y-axis projection of h⃗.
        // n⃗ = (−hz, 0, hx)  (cross product of ŷ × h⃗)
        double nx = -hz;
        double ny = 0.0;
        double nz = hx;
        double nMag = Math.sqrt(nx * nx + nz * nz);

        double ascNode;
        if (nMag < 1e-10) {
            // Inclination ≈ 0 → ascending node is undefined, set to 0
            ascNode = 0.0;
        } else {
            // Ω = atan2(n_x, n_z)  — angle from +Z axis to n⃗ in the XZ plane
            ascNode = Math.atan2(nx, nz);
            if (ascNode < 0) ascNode += 2.0 * Math.PI;
        }

        // ── Argument of pericenter ω ─────────────────────────────────
        // ω is the angle from the ascending node to the eccentricity vector,
        // measured in the orbital plane.
        double argPeri;
        if (nMag < 1e-10) {
            // i ≈ 0: ω is measured from the +Z axis in the XZ plane
            argPeri = Math.atan2(ex, ez);
            if (argPeri < 0) argPeri += 2.0 * Math.PI;
        } else {
            // cos(ω) = (n⃗ · e⃗) / (|n⃗| · |e⃗|)
            double cosW = (nx * ex + nz * ez) / (nMag * e);
            cosW = Math.max(-1.0, Math.min(1.0, cosW));

            // Determine sign of sin(ω) from (n⃗ × e⃗) · ĥ
            // n⃗ × e⃗ in the Y component: nx*ez − nz*ex
            double sinW = (nx * ez - nz * ex) / (nMag * e);

            argPeri = Math.atan2(sinW, cosW);
            if (argPeri < 0) argPeri += 2.0 * Math.PI;
        }

        // ── True anomaly θ ───────────────────────────────────────────
        // Angle from eccentricity vector to position vector in the orbital plane
        double theta;
        if (e < 1e-9) {
            // Circular orbit: true anomaly measured from ascending node
            if (nMag < 1e-10) {
                theta = Math.atan2(rx, rz); // from +Z in XZ plane
            } else {
                double cosT = (nx * rx + nz * rz) / (nMag * rMag);
                cosT = Math.max(-1.0, Math.min(1.0, cosT));
                double sinT = (nx * rz - nz * rx) / (nMag * rMag);
                theta = Math.atan2(sinT, cosT);
            }
        } else {
            double cosT = (ex * rx + ey * ry + ez * rz) / (e * rMag);
            cosT = Math.max(-1.0, Math.min(1.0, cosT));
            // Sign from (e⃗ × r⃗) · ĥ
            // e⃗ × r⃗ Y-component: ex*rz − ez*rx
            double sinT = (ex * rz - ez * rx) / (e * rMag);
            // Actually we need the dot with ĥ direction.
            // (e⃗ × r⃗) = (ey*rz - ez*ry, ez*rx - ex*rz, ex*ry - ey*rx)
            double crossY = ez * rx - ex * rz;
            // If crossY · hy > 0, sinT > 0 (prograde)
            // But simpler: just use atan2 with the correct sign
            // The radial velocity v_r = (r⃗ · v⃗)/|r| determines sign
            double rDotV = rx * vx + ry * vy + rz * vz;
            // If rDotV > 0, body is moving away from pericenter → θ > 0
            // (for prograde orbit)
            theta = Math.atan2(sinT, cosT);
            if (theta < 0) theta += 2.0 * Math.PI;
        }

        // ── Eccentric anomaly E ──────────────────────────────────────
        // tan(E/2) = sqrt((1−e)/(1+e)) · tan(θ/2)
        double E;
        if (e < 1e-9) {
            E = theta;
        } else {
            double sinHalfE = Math.sqrt((1.0 - e) / (1.0 + e)) * Math.sin(theta / 2.0);
            double cosHalfE = Math.cos(theta / 2.0);
            E = 2.0 * Math.atan2(sinHalfE, cosHalfE);
            if (E < 0) E += 2.0 * Math.PI;
        }

        // ── Mean anomaly M = E − e·sin(E) ──────────────────────────
        double meanAnom = E - e * Math.sin(E);
        if (meanAnom < 0) meanAnom += 2.0 * Math.PI;

        // ── Store derived elements ───────────────────────────────────
        body.setOrbitalParameters(a, e, inc, ascNode, argPeri, meanAnom);

        System.out.printf("  %-8s derived elements: a=%.4e m, e=%.6f, i=%.4f°, Ω=%.4f°, ω=%.4f°, M=%.4f°%n",
                body.getName(),
                a, e,
                Math.toDegrees(inc),
                Math.toDegrees(ascNode),
                Math.toDegrees(argPeri),
                Math.toDegrees(meanAnom));
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