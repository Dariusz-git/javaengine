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
- [x] Dynamic distance grid with screen-space adaptive LOD (G to toggle)
- [ ] Add configuration file support for custom scenarios

## Texture System

Place planet textures in `src/main/resources/textures/`. The `TextureManager` searches for files using multiple patterns (JPG first, then PNG; highest resolution prefix first):

| Pattern           | Example filenames tried                        |
| ----------------- | ---------------------------------------------- |
| Exact name        | `earth.jpg`, `earth.png`                       |
| Common suffix     | `earth_daymap.jpg`, `earth_surface.jpg`        |
| Resolution prefix | `8k_earth.jpg`, `4k_earth.jpg`, `2k_earth.jpg` |
| Prefix + suffix   | `8k_earth_daymap.jpg`, `4k_earth_surface.jpg`  |

### Recommended textures (from [Solar System Scope](https://www.solarsystemscope.com/textures/))

| File                       | Body               | Notes                               |
| -------------------------- | ------------------ | ----------------------------------- |
| `8k_sun.jpg`               | Sun                |                                     |
| `8k_mercury.jpg`           | Mercury            |                                     |
| `8k_venus_surface.jpg`     | Venus (surface)    |                                     |
| `4k_venus_atmosphere.jpg`  | Venus (atmosphere) | Optional overlay                    |
| `8k_earth_daymap.jpg`      | Earth (day)        | Primary Earth texture               |
| `8k_earth_nightmap.jpg`    | Earth (night)      | City lights on dark side            |
| `8k_earth_clouds.jpg`      | Earth (clouds)     | Semi-transparent cloud overlay      |
| `8k_mars.jpg`              | Mars               |                                     |
| `8k_jupiter.jpg`           | Jupiter            |                                     |
| `8k_saturn.jpg`            | Saturn             |                                     |
| `8k_saturn_ring_alpha.png` | Saturn rings       | Alpha channel for ring transparency |
| `2k_uranus.jpg`            | Uranus             |                                     |
| `2k_neptune.jpg`           | Neptune            |                                     |
| `8k_moon.jpg`              | Moon               |                                     |
| `8k_stars.jpg`             | Star background    |                                     |
| `8k_stars_milky_way.jpg`   | Milky Way          | Optional skybox                     |

> **Note:** `8k_earth_normal_map.tif` and `8k_earth_specular_map.tif` are **not** supported — STBImage cannot decode TIFF. Convert to PNG/JPG if needed.

If a texture file is not found, the engine generates a procedural texture automatically using Simplex noise.

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
