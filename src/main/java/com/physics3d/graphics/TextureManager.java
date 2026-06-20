package com.physics3d.graphics;

import com.physics3d.model.BodyType;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL30;
import org.lwjgl.stb.STBImage;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.util.HashMap;
import java.util.Map;

/**
 * Loads and caches OpenGL textures for celestial bodies.
 *
 * <p>For each body the manager first tries to load an image file from the
 * classpath under {@code /textures/}.  The lookup tries a list of common
 * filename patterns so that textures downloaded from
 * <a href="https://www.solarsystemscope.com/textures/">Solar System Scope</a>
 * work without renaming:
 *
 * <ol>
 *   <li>{@code <name>.jpg} / {@code <name>.png} — exact match</li>
 *   <li>{@code <name>_<suffix>.jpg} / {@code <name>_<suffix>.png} — suffix
 *       (e.g. {@code earth_daymap.jpg})</li>
 *   <li>{@code 8k_<name>.jpg} / {@code 4k_<name>.jpg} / {@code 2k_<name>.jpg}
 *       — resolution prefix</li>
 *   <li>{@code 8k_<name>_<suffix>.jpg} / {@code 4k_<name>_<suffix>.jpg} /
 *       {@code 2k_<name>_<suffix>.jpg} — resolution prefix + suffix</li>
 * </ol>
 *
 * <p>JPG and PNG are both supported (STBImage decodes them natively).  TIFF
 * files are <em>not</em> supported by STBImage, so the normal/specular maps
 * from Solar System Scope must be skipped or converted to PNG.
 *
 * <p>If no file is found the manager falls back to a procedurally generated
 * texture produced by {@link ProceduralTexture}.
 *
 * <p>Usage:
 * <pre>{@code
 *     TextureManager tm = new TextureManager();
 *     tm.init();
 *     int texId = tm.getTextureId("Earth", BodyType.TERRESTRIAL);
 *     // ... later ...
 *     tm.cleanup();
 * }</pre>
 *
 * <p>Must be created <em>after</em> the OpenGL context exists (i.e. after the
 * GLFW window has been made current).
 */
public class TextureManager {

    /** Cache: body name → OpenGL texture id. */
    private final Map<String, Integer> cache = new HashMap<>();

    /** Whether {@link #init()} has been called. */
    private boolean initialized = false;

    /** How many textures were loaded from disk (for diagnostics). */
    private int loadedCount = 0;

    /** How many textures were generated procedurally (for diagnostics). */
    private int proceduralCount = 0;

    /** File extensions we try, in order. */
    private static final String[] EXTENSIONS = { ".jpg", ".png" };

    /** Resolution prefixes we try, in order (highest first). */
    private static final String[] RESOLUTION_PREFIXES = { "8k_", "4k_", "2k_" };

    /**
     * Common suffixes used by Solar System Scope and similar texture packs.
     * The first match wins, so list more-specific suffixes first.
     */
    private static final String[] COMMON_SUFFIXES = {
            "daymap", "nightmap", "clouds", "surface", "atmosphere",
            "normal_map", "specular_map", "ring_alpha"
    };

    /**
     * One-time OpenGL state setup.  Enables texturing and sets sane defaults
     * for filtering / wrapping.  Safe to call multiple times.
     */
    public void init() {
        if (initialized) return;
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        // Defaults applied per-texture in uploadTexture(); these are just fallbacks.
        GL11.glPixelStorei(GL11.GL_UNPACK_ALIGNMENT, 1);
        initialized = true;
    }

    /**
     * Returns the OpenGL texture id for the given body, loading it on demand.
     * Returns {@code -1} if the name is {@code null}.
     */
    public int getTextureId(String name, BodyType type) {
        if (name == null) return -1;
        String key = name.toLowerCase();
        Integer cached = cache.get(key);
        if (cached != null) return cached;

        int id;
        try {
            id = loadFirstAvailable(key);
            loadedCount++;
        } catch (IOException | RuntimeException e) {
            // File missing or unreadable → procedural fallback.
            id = generateProcedural(name, type);
            proceduralCount++;
        }
        cache.put(key, id);
        return id;
    }

    /**
     * Returns the OpenGL texture id for a specific named variant of a body
     * (e.g. {@code "earth_daymap"}, {@code "earth_nightmap"},
     * {@code "earth_clouds"}).  Returns {@code -1} if the name is {@code null}
     * or no file is found.
     *
     * <p>Unlike {@link #getTextureId(String, BodyType)} this method does
     * <em>not</em> fall back to a procedural texture — a missing variant is
     * a legitimate case (e.g. only Earth has a night map) and the caller
     * should check for {@code -1}.
     */
    public int getTextureVariant(String variantName) {
        if (variantName == null) return -1;
        String key = variantName.toLowerCase();
        Integer cached = cache.get(key);
        if (cached != null) return cached;

        try {
            int id = loadFirstAvailable(key);
            loadedCount++;
            cache.put(key, id);
            return id;
        } catch (IOException | RuntimeException e) {
            return -1;
        }
    }

    /** Number of textures successfully loaded from disk. */
    public int getLoadedCount() {
        return loadedCount;
    }

    /** Number of textures generated procedurally (no file was found). */
    public int getProceduralCount() {
        return proceduralCount;
    }

    /** Releases all GPU textures.  Call from the renderer's cleanup hook. */
    public void cleanup() {
        for (Integer id : cache.values()) {
            if (id != null && id > 0) GL11.glDeleteTextures(id);
        }
        cache.clear();
    }

    // ---------------------------------------------------------------------
    // Internals
    // ---------------------------------------------------------------------

    /**
     * Try every plausible filename pattern for {@code key} and return the
     * first one that exists on the classpath.  Throws if none are found.
     *
     * <p>The patterns are tried in this order:
     * <ol>
     *   <li>{@code <key><ext>}</li>
     *   <li>{@code <key>_<suffix><ext>}</li>
     *   <li>{@code <res><key><ext>}</li>
     *   <li>{@code <res><key>_<suffix><ext>}</li>
     * </ol>
     * where {@code <ext>} is {@code .jpg} or {@code .png} and {@code <res>}
     * is {@code 8k_}, {@code 4k_} or {@code 2k_}.
     *
     * <p>Because we can't list directory contents from inside a JAR, the
     * "suffix" patterns are implemented by trying a small set of well-known
     * suffixes (daymap, nightmap, clouds, surface, atmosphere, …) for each
     * body.  This covers the Solar System Scope naming convention without
     * needing filesystem access.
     */
    private int loadFirstAvailable(String key) throws IOException {
        // 1. Exact match: <key>.jpg / <key>.png
        for (String ext : EXTENSIONS) {
            try {
                return loadFromResource("/textures/" + key + ext);
            } catch (IOException ignored) {
                // try next extension
            }
        }

        // 2. Suffix match: <key>_<suffix>.jpg / .png
        for (String suffix : COMMON_SUFFIXES) {
            for (String ext : EXTENSIONS) {
                try {
                    return loadFromResource("/textures/" + key + "_" + suffix + ext);
                } catch (IOException ignored) {
                    // try next combination
                }
            }
        }

        // 3. Resolution prefix: <res><key>.jpg / .png
        for (String res : RESOLUTION_PREFIXES) {
            for (String ext : EXTENSIONS) {
                try {
                    return loadFromResource("/textures/" + res + key + ext);
                } catch (IOException ignored) {
                    // try next combination
                }
            }
        }

        // 4. Resolution prefix + suffix: <res><key>_<suffix>.jpg / .png
        for (String res : RESOLUTION_PREFIXES) {
            for (String suffix : COMMON_SUFFIXES) {
                for (String ext : EXTENSIONS) {
                    try {
                        return loadFromResource("/textures/" + res + key + "_" + suffix + ext);
                    } catch (IOException ignored) {
                        // try next combination
                    }
                }
            }
        }

        throw new IOException("No texture file found for '" + key
                + "' under /textures/ (tried " + EXTENSIONS.length
                + " extensions × " + RESOLUTION_PREFIXES.length
                + " resolutions × " + (COMMON_SUFFIXES.length + 1) + " name patterns)");
    }

    /**
     * Load an image from the classpath via STBImage.  Throws if the file is
     * missing or STBImage fails to decode it.
     */
    private int loadFromResource(String resourcePath) throws IOException {
        try (InputStream is = TextureManager.class.getResourceAsStream(resourcePath)) {
            if (is == null) {
                throw new IOException("Resource not found: " + resourcePath);
            }
            byte[] bytes = is.readAllBytes();

            ByteBuffer imageBuffer = BufferUtils.createByteBuffer(bytes.length).put(bytes);
            imageBuffer.flip();

            IntBuffer w = BufferUtils.createIntBuffer(1);
            IntBuffer h = BufferUtils.createIntBuffer(1);
            IntBuffer channels = BufferUtils.createIntBuffer(1);

            // Force 4 channels (RGBA) so we always know the pixel layout.
            ByteBuffer pixels = STBImage.stbi_load_from_memory(imageBuffer, w, h, channels, 4);
            if (pixels == null) {
                throw new IOException("STBImage failed to decode " + resourcePath
                        + ": " + STBImage.stbi_failure_reason());
            }

            int width = w.get();
            int height = h.get();
            int texId = uploadTexture(pixels, width, height);
            STBImage.stbi_image_free(pixels);
            return texId;
        }
    }

    /** Generate a procedural texture and upload it to the GPU. */
    private int generateProcedural(String name, BodyType type) {
        long seed = name.hashCode(); // deterministic per body
        ByteBuffer pixels = ProceduralTexture.generate(type, seed,
                ProceduralTexture.DEFAULT_WIDTH,
                ProceduralTexture.DEFAULT_HEIGHT);
        return uploadTexture(pixels, ProceduralTexture.DEFAULT_WIDTH, ProceduralTexture.DEFAULT_HEIGHT);
    }

    /** Upload an RGBA8 pixel buffer to a new OpenGL texture and return its id. */
    private int uploadTexture(ByteBuffer pixels, int width, int height) {
        int texId = GL11.glGenTextures();
        GL13.glActiveTexture(GL13.GL_TEXTURE0);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, texId);

        // Upload pixels.
        GL11.glTexImage2D(
                GL11.GL_TEXTURE_2D,
                0,
                GL11.GL_RGBA8,
                width, height,
                0,
                GL11.GL_RGBA,
                GL11.GL_UNSIGNED_BYTE,
                pixels);

        // Trilinear filtering for nice-looking planets at any zoom.
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR_MIPMAP_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL11.GL_REPEAT);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL11.GL_CLAMP);

        // Anisotropic filtering if available (improves grazing-angle quality).
        try {
            float maxAniso = GL11.glGetFloat(0x84FF /* GL_MAX_TEXTURE_MAX_ANISOTROPY_EXT */);
            if (maxAniso > 1.0f) {
                GL11.glTexParameterf(GL11.GL_TEXTURE_2D, 0x84FE /* GL_TEXTURE_MAX_ANISOTROPY_EXT */,
                        Math.min(8.0f, maxAniso));
            }
        } catch (Throwable ignored) {
            // Not all drivers expose this; safe to ignore.
        }

        GL30.glGenerateMipmap(GL11.GL_TEXTURE_2D);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, 0);
        return texId;
    }
}
