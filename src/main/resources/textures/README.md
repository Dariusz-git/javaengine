# Planet Textures

Place PNG textures for each celestial body in this directory. The
`TextureManager` looks up files by the body's name in **lowercase**:

| Body    | Expected filename |
| ------- | ----------------- |
| Sun     | `sun.png`         |
| Mercury | `mercury.png`     |
| Venus   | `venus.png`       |
| Earth   | `earth.png`       |
| Mars    | `mars.png`        |
| Jupiter | `jupiter.png`     |
| Saturn  | `saturn.png`      |
| Uranus  | `uranus.png`      |
| Neptune | `neptune.png`     |

## Recommended sources

- **Solar System Scope** — https://www.solarsystemscope.com/textures/
  (2K textures are available for every planet; 8K for most)
- **NASA Visible Earth** — https://visibleearth.nasa.gov/

## Format

- PNG, sRGB color space
- Equirectangular projection (longitude → U, latitude → V)
- Any resolution works — 2K, 4K, 8K are all fine. OpenGL handles
  different sizes transparently and mipmaps are generated automatically.
- The texture wraps horizontally (longitude 0°–360°); the poles may
  show a small seam, which is normal for equirectangular maps.

## Fallback

If a file is missing, the engine generates a procedural texture using
Simplex noise (see `ProceduralTexture.java`). This means you can drop
in textures one at a time and the simulator keeps working.
