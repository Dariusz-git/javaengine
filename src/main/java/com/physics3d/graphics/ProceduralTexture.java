package com.physics3d.graphics;

import com.physics3d.model.BodyType;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Generates RGBA texture data procedurally for celestial bodies that don't have
 * a downloaded texture file.  The output is a {@link ByteBuffer} suitable for
 * uploading directly to OpenGL via {@code glTexImage2D}.
 *
 * <p>Each body type uses a different recipe:
 * <ul>
 *   <li>{@link BodyType#STAR}        — turbulent orange/yellow plasma</li>
 *   <li>{@link BodyType#TERRESTRIAL} — blue oceans, green/brown continents</li>
 *   <li>{@link BodyType#GAS_GIANT}   — horizontal banded stripes</li>
 *   <li>{@link BodyType#ICE_GIANT}   — smooth cyan/blue gradients</li>
 * </ul>
 *
 * <p>All textures are generated as equirectangular maps (u = longitude 0..1,
 * v = latitude 0..1) so they map cleanly onto the {@code drawSphere} UV grid.
 */
public final class ProceduralTexture {

    private ProceduralTexture() {}

    /** Default texture resolution.  512×256 keeps VRAM low while still looking decent. */
    public static final int DEFAULT_WIDTH = 512;
    public static final int DEFAULT_HEIGHT = 256;

    /**
     * Generate a procedural texture for the given body type.
     *
     * @param type     body type
     * @param seed     deterministic seed (use the body's name hash for stability)
     * @param width    texture width in pixels
     * @param height   texture height in pixels
     * @return ByteBuffer of {@code width*height*4} bytes in RGBA8 format
     */
    public static ByteBuffer generate(BodyType type, long seed, int width, int height) {
        ByteBuffer buf = ByteBuffer.allocateDirect(width * height * 4).order(ByteOrder.nativeOrder());
        SimplexNoise noise = new SimplexNoise(seed);

        for (int y = 0; y < height; y++) {
            float v = (float) y / (float) height;          // 0..1, top→bottom
            float lat = (v - 0.5f) * (float) Math.PI;      // -π/2 .. π/2
            for (int x = 0; x < width; x++) {
                float u = (float) x / (float) width;       // 0..1, left→right
                float lon = u * (float) Math.PI * 2.0f;    // 0..2π

                float r, g, b;
                switch (type) {
                    case STAR        -> { float[] c = star(noise, lat, lon, seed);        r = c[0]; g = c[1]; b = c[2]; }
                    case TERRESTRIAL -> { float[] c = terrestrial(noise, lat, lon, seed); r = c[0]; g = c[1]; b = c[2]; }
                    case GAS_GIANT   -> { float[] c = gasGiant(noise, lat, lon, seed);    r = c[0]; g = c[1]; b = c[2]; }
                    case ICE_GIANT   -> { float[] c = iceGiant(noise, lat, lon, seed);    r = c[0]; g = c[1]; b = c[2]; }
                    default          -> { r = 0.5f; g = 0.5f; b = 0.5f; }
                }

                buf.put((byte) (clamp01(r) * 255.0f));
                buf.put((byte) (clamp01(g) * 255.0f));
                buf.put((byte) (clamp01(b) * 255.0f));
                buf.put((byte) 255); // full alpha
            }
        }
        buf.flip();
        return buf;
    }

    // ---------------------------------------------------------------------
    // Per-type recipes
    // ---------------------------------------------------------------------

    /** Turbulent plasma: warm yellows/oranges with darker sunspots. */
    private static float[] star(SimplexNoise n, float lat, float lon, long seed) {
        float nx = (float) Math.cos(lat) * (float) Math.cos(lon) * 2.0f;
        float ny = (float) Math.sin(lat) * 2.0f;
        float nz = (float) Math.cos(lat) * (float) Math.sin(lon) * 2.0f;

        float t = n.fbm(nx, ny, 5, 2.0f, 0.55f);          // -1..1
        float spots = n.fbm(nx * 3.0f, nz * 3.0f, 3, 2.0f, 0.5f);

        // Map noise to a yellow→orange→deep-red gradient.
        float k = clamp01(t * 0.5f + 0.5f);                // 0..1
        float r = mix(0.95f, 1.00f, k);
        float g = mix(0.30f, 0.85f, k);
        float b = mix(0.05f, 0.20f, k);

        // Darken sunspots.
        float spotMask = clamp01(spots * 0.5f + 0.5f);
        float darken = mix(0.55f, 1.0f, spotMask);
        return new float[]{ r * darken, g * darken, b * darken };
    }

    /** Earth-like: blue oceans, green/brown continents, polar ice caps. */
    private static float[] terrestrial(SimplexNoise n, float lat, float lon, long seed) {
        // Use spherical-ish coordinates so the noise wraps without seams.
        float nx = (float) Math.cos(lat) * (float) Math.cos(lon) * 1.5f;
        float ny = (float) Math.sin(lat) * 1.5f;
        float nz = (float) Math.cos(lat) * (float) Math.sin(lon) * 1.5f;

        float continent = n.fbm(nx, nz, 5, 2.0f, 0.5f);    // -1..1
        float detail    = n.fbm(nx * 4.0f, ny * 4.0f, 4, 2.0f, 0.5f);

        float h = continent + detail * 0.25f;              // pseudo-elevation

        float r, g, b;
        if (h < -0.05f) {
            // Deep ocean → shallow ocean.
            float k = clamp01((h + 0.5f) / 0.45f);
            r = mix(0.05f, 0.20f, k);
            g = mix(0.10f, 0.40f, k);
            b = mix(0.30f, 0.65f, k);
        } else if (h < 0.05f) {
            // Coast / beach.
            r = 0.76f; g = 0.70f; b = 0.50f;
        } else {
            // Land: green lowlands → brown highlands.
            float k = clamp01((h - 0.05f) / 0.6f);
            r = mix(0.20f, 0.55f, k);
            g = mix(0.45f, 0.40f, k);
            b = mix(0.15f, 0.25f, k);
        }

        // Polar ice caps based on latitude.
        float polar = clamp01((Math.abs(lat) - 1.05f) / 0.5f);
        r = mix(r, 0.95f, polar);
        g = mix(g, 0.97f, polar);
        b = mix(b, 1.00f, polar);

        return new float[]{ r, g, b };
    }

    /** Banded gas giant: horizontal stripes with turbulent eddies. */
    private static float[] gasGiant(SimplexNoise n, float lat, float lon, long seed) {
        // Latitude bands: sine of latitude plus noise distortion.
        float bandFreq = 8.0f;
        float band = (float) Math.sin(lat * bandFreq + n.noise(lon * 2.0f, lat * 2.0f) * 0.6f);
        float t = clamp01(band * 0.5f + 0.5f);

        // Base palette: tan → cream → pale orange.
        float r = mix(0.55f, 0.95f, t);
        float g = mix(0.40f, 0.85f, t);
        float b = mix(0.25f, 0.65f, t);

        // Add swirling storms.
        float storm = n.fbm(lon * 6.0f, lat * 6.0f, 3, 2.0f, 0.5f);
        float stormMask = clamp01(storm * 1.5f);
        r = mix(r, 0.85f, stormMask * 0.4f);
        g = mix(g, 0.55f, stormMask * 0.4f);
        b = mix(b, 0.30f, stormMask * 0.4f);

        return new float[]{ r, g, b };
    }

    /** Smooth ice giant: cyan/blue with subtle banding. */
    private static float[] iceGiant(SimplexNoise n, float lat, float lon, long seed) {
        float band = (float) Math.sin(lat * 5.0f + n.noise(lon * 1.5f, lat * 1.5f) * 0.4f);
        float t = clamp01(band * 0.5f + 0.5f);

        float r = mix(0.30f, 0.75f, t);
        float g = mix(0.55f, 0.90f, t);
        float b = mix(0.75f, 1.00f, t);

        // Soft cloud overlay.
        float cloud = n.fbm(lon * 3.0f, lat * 3.0f, 3, 2.0f, 0.5f);
        float cloudMask = clamp01(cloud * 0.5f + 0.5f);
        r = mix(r, 0.95f, cloudMask * 0.25f);
        g = mix(g, 0.98f, cloudMask * 0.25f);
        b = mix(b, 1.00f, cloudMask * 0.25f);

        return new float[]{ r, g, b };
    }

    // ---------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------

    private static float clamp01(float v) {
        if (v < 0.0f) return 0.0f;
        if (v > 1.0f) return 1.0f;
        return v;
    }

    private static float mix(float a, float b, float t) {
        return a + (b - a) * t;
    }
}
