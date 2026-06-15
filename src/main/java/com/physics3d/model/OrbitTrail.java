package com.physics3d.model;

import org.joml.Vector3f;
import java.util.LinkedList;
import java.util.Queue;

/**
 * Tracks the orbital path of a celestial body
 */
public class OrbitTrail {
    private final Queue<Vector3f> positions;
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

    public void addPosition(Vector3f position) {
        positions.add(new Vector3f(position));
        if (positions.size() > maxLength) {
            positions.poll();
        }
    }

    public Queue<Vector3f> getPositions() {
        return positions;
    }

    public Queue<Vector3f> getTheoreticalOrbit() {
        return theoreticalOrbit;
    }

    public void clear() {
        positions.clear();
    }

}