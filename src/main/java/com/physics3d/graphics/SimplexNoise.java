package com.physics3d.graphics;

/**
 * Lightweight 2D Simplex noise implementation for procedural texture generation.
 *
 * <p>Based on the public-domain reference implementation by Stefan Gustavson
 * (https://github.com/stegu/perlin-noise).  We only need 2D noise here, so the
 * 3D / 4D code paths have been stripped out to keep the class small and fast.
 *
 * <p>Typical use:
 * <pre>{@code
 *     SimplexNoise n = new SimplexNoise(42L);
 *     float v = n.noise(x, y);              // -1..1
 *     float f = n.fbm(x, y, 5, 2.0f, 0.5f); // fractal Brownian motion, 5 octaves
 * }</pre>
 */
public class SimplexNoise {

    /** Permutation table — duplicated to avoid index wrapping. */
    private final int[] perm = new int[512];
    private final int[] permMod12 = new int[512];

    /** Gradient lookup table for 2D (8 directions). */
    private static final float[][] GRAD2 = {
            {1, 1}, {-1, 1}, {1, -1}, {-1, -1},
            {1, 0}, {-1, 0}, {0, 1}, {0, -1}
    };

    public SimplexNoise(long seed) {
        // Build a permutation table from a seeded RNG so textures are deterministic.
        int[] p = new int[256];
        for (int i = 0; i < 256; i++) p[i] = i;
        java.util.Random rng = new java.util.Random(seed);
        // Fisher–Yates shuffle
        for (int i = 255; i > 0; i--) {
            int j = rng.nextInt(i + 1);
            int tmp = p[i];
            p[i] = p[j];
            p[j] = tmp;
        }
        for (int i = 0; i < 512; i++) {
            perm[i] = p[i & 255];
            permMod12[i] = perm[i] % 8;
        }
    }

    /** Single-octave 2D Simplex noise.  Output range is approximately [-1, 1]. */
    public float noise(float xin, float yin) {
        // Skewing factors for 2D.
        final float F2 = 0.5f * ((float) Math.sqrt(3.0) - 1.0f);
        final float G2 = (3.0f - (float) Math.sqrt(3.0)) / 6.0f;

        float s = (xin + yin) * F2;
        int i = fastFloor(xin + s);
        int j = fastFloor(yin + s);
        float t = (i + j) * G2;
        float X0 = i - t;
        float Y0 = j - t;
        float x0 = xin - X0;
        float y0 = yin - Y0;

        // Determine which simplex we are in.
        int i1, j1;
        if (x0 > y0) { i1 = 1; j1 = 0; } else { i1 = 0; j1 = 1; }

        float x1 = x0 - i1 + G2;
        float y1 = y0 - j1 + G2;
        float x2 = x0 - 1.0f + 2.0f * G2;
        float y2 = y0 - 1.0f + 2.0f * G2;

        int ii = i & 255;
        int jj = j & 255;
        int gi0 = permMod12[ii + perm[jj]];
        int gi1 = permMod12[ii + i1 + perm[jj + j1]];
        int gi2 = permMod12[ii + 1 + perm[jj + 1]];

        float n0, n1, n2;

        float t0 = 0.5f - x0 * x0 - y0 * y0;
        if (t0 < 0) n0 = 0.0f;
        else {
            t0 *= t0;
            n0 = t0 * t0 * dot2(GRAD2[gi0], x0, y0);
        }

        float t1 = 0.5f - x1 * x1 - y1 * y1;
        if (t1 < 0) n1 = 0.0f;
        else {
            t1 *= t1;
            n1 = t1 * t1 * dot2(GRAD2[gi1], x1, y1);
        }

        float t2 = 0.5f - x2 * x2 - y2 * y2;
        if (t2 < 0) n2 = 0.0f;
        else {
            t2 *= t2;
            n2 = t2 * t2 * dot2(GRAD2[gi2], x2, y2);
        }

        // Scale to keep result in roughly [-1, 1].
        return 70.0f * (n0 + n1 + n2);
    }

    /**
     * Fractal Brownian motion: sum of {@code octaves} noise layers with
     * geometrically increasing frequency and decreasing amplitude.
     *
     * @param x        sample x
     * @param y        sample y
     * @param octaves  number of layers (1..8 typical)
     * @param lacunarity frequency multiplier per octave (e.g. 2.0)
     * @param gain     amplitude multiplier per octave (e.g. 0.5)
     * @return sum, roughly in [-1, 1]
     */
    public float fbm(float x, float y, int octaves, float lacunarity, float gain) {
        float sum = 0.0f;
        float amp = 1.0f;
        float freq = 1.0f;
        float norm = 0.0f;
        for (int o = 0; o < octaves; o++) {
            sum += amp * noise(x * freq, y * freq);
            norm += amp;
            amp *= gain;
            freq *= lacunarity;
        }
        return sum / norm;
    }

    private static int fastFloor(float v) {
        int i = (int) v;
        return v < i ? i - 1 : i;
    }

    private static float dot2(float[] g, float x, float y) {
        return g[0] * x + g[1] * y;
    }
}
