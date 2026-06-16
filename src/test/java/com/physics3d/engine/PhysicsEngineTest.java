package com.physics3d.engine;

import com.physics3d.model.CelestialBody;
import org.joml.Vector3f;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Unit tests for the physics engine
 */
public class PhysicsEngineTest {
    private PhysicsEngine engine;
    
    @Before
    public void setUp() {
        engine = new PhysicsEngine();
    }
    
    @Test
    public void testAddBody() {
        CelestialBody body = new CelestialBody(
            "Test",
            new Vector3f(0, 0, 0),
            new Vector3f(0, 0, 0),
            1e24f,
            1e6f,
            0,  // SemiMajorAxis
            0,  // Eccentricity
            0,  // Inclination
            0,  // AscendingNode
            0,  // ArgOfPericenter
            0   // MeanAnomaly
        );

        engine.addBody(body);
        assertEquals(1, engine.getBodies().size());
    }

    @Test
    public void testMultipleBodies() {
        CelestialBody body1 = new CelestialBody(
            "Body1",
            new Vector3f(0, 0, 0),
            new Vector3f(0, 0, 0),
            1e24f,
            1e6f,
            0,  // SemiMajorAxis
            0,  // Eccentricity
            0,  // Inclination
            0,  // AscendingNode
            0,  // ArgOfPericenter
            0   // MeanAnomaly
        );

        CelestialBody body2 = new CelestialBody(
            "Body2",
            new Vector3f(1e11f, 0, 0),
            new Vector3f(0, 0, 0),
            1e24f,
            1e6f,
            0,  // SemiMajorAxis
            0,  // Eccentricity
            0,  // Inclination
            0,  // AscendingNode
            0,  // ArgOfPericenter
            0   // MeanAnomaly
        );

        engine.addBody(body1);
        engine.addBody(body2);

        assertEquals(2, engine.getBodies().size());
    }

    @Test
    public void testPhysicsUpdate() {
        CelestialBody body = new CelestialBody(
            "Test",
            new Vector3f(0, 0, 0),
            new Vector3f(10, 0, 0),
            1e24f,
            1e6f,
            0,  // SemiMajorAxis
            0,  // Eccentricity
            0,  // Inclination
            0,  // AscendingNode
            0,  // ArgOfPericenter
            0   // MeanAnomaly
        );

        engine.addBody(body);
        Vector3f initialPos = new Vector3f(body.getPosition());

        engine.update(0.01);

        // Position should have changed due to velocity
        assertNotEquals(initialPos.x, body.getPosition().x, 0.1f);
    }
}