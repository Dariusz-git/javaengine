# 3D Physics Engine - Solar System Simulator

A Java-based 3D physics engine that simulates gravitational interactions between celestial bodies. The project demonstrates Newton's law of universal gravitation and orbital mechanics.

## Features

- **Gravitational Physics**: Accurate simulation of gravitational forces between bodies
- **3D Rendering**: OpenGL-based visualization using LWJGL
- **Extensible Architecture**: Easy to add new celestial bodies and features
- **Solar System Simulation**: Pre-configured with Sun, Earth, and Mars

## Requirements

- Java 17 or higher
- Maven 3.6+
- OpenGL 3.3+ compatible graphics card

## Building

```bash
mvn clean package
```

## Running

```bash
mvn exec:java -Dexec.mainClass="com.physics3d.Main"
```

Or run the packaged JAR:

```bash
java -jar target/physics-engine-3d-1.0.0-shaded.jar
```

## Controls

- **ESC**: Exit the simulation
- **Close Window**: Exit the simulation

## Project Structure

```
src/
├── main/java/com/physics3d/
│   ├── Main.java                 # Entry point
│   ├── engine/
│   │   └── PhysicsEngine.java    # Core physics calculations
│   ├── model/
│   │   └── CelestialBody.java    # Celestial body representation
│   └── graphics/
│       └── Renderer.java         # OpenGL rendering
└── test/java/com/physics3d/
    └── engine/
        └── PhysicsEngineTest.java # Unit tests
```

## Physics Implementation

The engine uses Newton's law of universal gravitation:

```
F = G * (m1 * m2) / r²
```

Where:

- G = 6.674 × 10⁻¹¹ N·m²/kg²
- m1, m2 = masses of the bodies
- r = distance between bodies

## Future Enhancements

- [x] Add more celestial bodies (Venus, Jupiter, Saturn, etc.)
- [ ] Implement collision detection
- [x] Add camera controls for better visualization
- [x] Implement orbital trails
- [x] Add time acceleration/deceleration controls
- [x] Improve rendering with textures and lighting
- [x] Dynamic orbit fading (1 years) and white color
- [ ] Add configuration file support for custom scenarios

## Dependencies

- **LWJGL 3.3.3**: OpenGL bindings for Java
- **JOML 1.10.5**: Java OpenGL Math Library
- **JUnit 4.13.2**: Testing framework

## License

MIT License

Plan

Collision detection - wykrywanie zderzeń między planetami
Konfiguracja ze scenariuszy - wczytać z pliku JSON/XML zamiast hardcode'u
Lepsza fizyka - Verlet integration zamiast Eulera

Shader rendering - OpenGL shaders zamiast fixed-function pipeline
Textury planet - dodać realističtyczne tekstury
Barycentric system - symulacja systemów binarnych
Particle effects - efekty dla zderzeń lub atmosfery
