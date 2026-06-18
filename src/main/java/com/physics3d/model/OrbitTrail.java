package com.physics3d.model;

import org.joml.Vector3f;
import java.util.LinkedList;
import java.util.Queue;

/**
 * Tracks the orbital path of a celestial body
 */
public class OrbitTrail {
    private final Queue<TrailPoint> positions;
    private final Queue<Vector3f> theoreticalOrbit; // Idealna orbita
    private final int maxLength;

    public OrbitTrail(int maxLength) {
        this.maxLength = maxLength;
        this.positions = new LinkedList<>();
        this.theoreticalOrbit = new LinkedList<>();
    }

    public void setTheoreticalOrbit(Queue<Vector3f> orbit) {
        this.theoreticalOrbit.clear();
        this.theoreticalOrbit.addAll(orbit);
    }

    /**
     * Adds a position with the given simulation time (in years) for age-based fading.
     */
    public void addPosition(Vector3f position, double simulationTimeYears) {
        positions.add(new TrailPoint(new Vector3f(position), simulationTimeYears));
        if (positions.size() > maxLength) {
            positions.poll();
        }
    }

    /**
     * @deprecated Use {@link #addPosition(Vector3f, double)} to record simulation time.
     */
    @Deprecated
    public void addPosition(Vector3f position) {
        addPosition(position, 0.0);
    }

    public Queue<TrailPoint> getPositions() {
        return positions;
    }

    public Queue<Vector3f> getTheoreticalOrbit() {
        return theoreticalOrbit;
    }

    public void clear() {
        positions.clear();
    }

    /**
     * A single point on the dynamic orbit trail, with the simulation time at which it was recorded.
     */
    public static class TrailPoint {
        public final Vector3f position;
        public final double timeYears;

        public TrailPoint(Vector3f position, double timeYears) {
            this.position = position;
            this.timeYears = timeYears;
        }
    }
}