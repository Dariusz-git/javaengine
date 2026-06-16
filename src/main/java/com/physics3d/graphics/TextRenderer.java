package com.physics3d.graphics;

import org.lwjgl.opengl.GL11;

import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Texture-based text renderer for LWJGL using AWT fonts.
 *
 * <p>Each ASCII character (32..127) is rasterised once into a single OpenGL
 * texture atlas. Glyphs are drawn as textured quads in screen space. This
 * approach is robust across drivers and avoids the pitfalls of the legacy
 * {@code glBitmap} path (raster-position clipping, 1-bit packing, etc.).</p>
 *
 * <p>Usage:</p>
 * <pre>
 *   TextRenderer tr = new TextRenderer(new Font("SansSerif", Font.PLAIN, 18));
 *   tr.beginFrame(width, height);
 *   tr.drawString("Hello", 10, 20, new float[]{1, 1, 1, 1});
 *   tr.endFrame();
 * </pre>
 */
public class TextRenderer {

    private static final int FIRST_CHAR = 32;
    private static final int LAST_CHAR = 127;
    private static final int CHAR_COUNT = LAST_CHAR - FIRST_CHAR + 1;

    /** Width of each glyph cell in pixels. */
    private final int charWidth;
    /** Height of each glyph cell in pixels. */
    private final int charHeight;
    /** Per-character horizontal advance (pixels). */
    private final int[] charWidths = new int[CHAR_COUNT];

    /** OpenGL texture id for the glyph atlas. */
    private int textureId;

    /** UV coordinates for each glyph in the atlas (u0, v0, u1, v1). */
    private final float[][] uvs = new float[CHAR_COUNT][4];

    private final Font font;

    public TextRenderer(Font font) {
        this.font = font;

        // Measure the font to size the glyph atlas.
        BufferedImage tmp = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = tmp.createGraphics();
        g.setFont(font);
        FontMetrics fm = g.getFontMetrics();
        this.charHeight = fm.getHeight();
        // Use the maximum advance as a uniform cell width for simplicity.
        this.charWidth = fm.getMaxAdvance();
        for (int i = 0; i < CHAR_COUNT; i++) {
            charWidths[i] = fm.charWidth((char) (FIRST_CHAR + i));
        }
        g.dispose();

        buildTexture();
    }

    /**
     * Rasterise every supported glyph into a single RGBA texture atlas.
     * Each glyph occupies a {@code charWidth x charHeight} cell.
     */
    private void buildTexture() {
        int atlasWidth = charWidth * CHAR_COUNT;
        int atlasHeight = charHeight;

        BufferedImage atlas = new BufferedImage(atlasWidth, atlasHeight, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = atlas.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
        g.setFont(font);
        g.setBackground(new Color(0, 0, 0, 0));
        g.clearRect(0, 0, atlasWidth, atlasHeight);
        g.setColor(Color.WHITE);

        // Draw each glyph individually at its cell origin so the rasterised
        // position matches the UV mapping (i * charWidth). Drawing them all
        // in one drawString() call would use each glyph's actual advance,
        // which is narrower than charWidth for most characters and would
        // cause the wrong glyphs to be sampled at runtime.
        int baseline = fmBaseline(font);
        for (int i = 0; i < CHAR_COUNT; i++) {
            char c = (char) (FIRST_CHAR + i);
            int cellX = i * charWidth;
            g.drawString(String.valueOf(c), cellX, baseline);
        }
        g.dispose();

        // Convert ARGB to RGBA byte buffer (AWT top-left -> GL bottom-left).
        byte[] pixels = new byte[atlasWidth * atlasHeight * 4];
        int[] argb = atlas.getRGB(0, 0, atlasWidth, atlasHeight, null, 0, atlasWidth);
        for (int y = 0; y < atlasHeight; y++) {
            int srcRow = (atlasHeight - 1 - y) * atlasWidth;
            int dstRow = y * atlasWidth;
            for (int x = 0; x < atlasWidth; x++) {
                int c = argb[srcRow + x];
                int j = (dstRow + x) * 4;
                pixels[j + 0] = (byte) ((c >> 16) & 0xFF); // R
                pixels[j + 1] = (byte) ((c >> 8) & 0xFF);  // G
                pixels[j + 2] = (byte) (c & 0xFF);         // B
                pixels[j + 3] = (byte) ((c >> 24) & 0xFF); // A
            }
        }

        ByteBuffer pixelBuffer = ByteBuffer.allocateDirect(pixels.length).order(ByteOrder.nativeOrder());
        pixelBuffer.put(pixels).flip();

        // Upload as a single 2D texture.
        textureId = GL11.glGenTextures();
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, textureId);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL11.GL_CLAMP);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL11.GL_CLAMP);
        GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA,
                atlasWidth, atlasHeight, 0,
                GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, pixelBuffer);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, 0);

        // Compute UVs for each glyph.
        float invW = 1.0f / atlasWidth;
        float invH = 1.0f / atlasHeight;
        for (int i = 0; i < CHAR_COUNT; i++) {
            float u0 = i * charWidth * invW;
            float u1 = (i + 1) * charWidth * invW;
            float v0 = 0.0f;
            float v1 = 1.0f;
            uvs[i][0] = u0;
            uvs[i][1] = v0;
            uvs[i][2] = u1;
            uvs[i][3] = v1;
        }
    }

    /** Compute the baseline Y for drawing text in the atlas. */
    private static int fmBaseline(Font font) {
        BufferedImage tmp = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = tmp.createGraphics();
        g.setFont(font);
        int baseline = g.getFontMetrics().getAscent();
        g.dispose();
        return baseline;
    }

    /**
     * Switch to an orthographic 2D projection suitable for HUD rendering.
     * Must be paired with {@link #endFrame()} to restore the previous state.
     */
    public void beginFrame(int screenWidth, int screenHeight) {
        GL11.glMatrixMode(GL11.GL_PROJECTION);
        GL11.glPushMatrix();
        GL11.glLoadIdentity();
        GL11.glOrtho(0, screenWidth, 0, screenHeight, -1, 1);

        GL11.glMatrixMode(GL11.GL_MODELVIEW);
        GL11.glPushMatrix();
        GL11.glLoadIdentity();

        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_COLOR_BUFFER_BIT);
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glEnable(GL11.GL_TEXTURE_2D);
    }

    /** Restore the 3D projection / modelview after HUD rendering. */
    public void endFrame() {
        GL11.glPopAttrib();
        GL11.glMatrixMode(GL11.GL_MODELVIEW);
        GL11.glPopMatrix();
        GL11.glMatrixMode(GL11.GL_PROJECTION);
        GL11.glPopMatrix();
    }

    /**
     * Draw a single line of text at the given screen coordinates.
     * Origin is the bottom-left corner of the first character.
     *
     * @param text  the string to render (only ASCII 32..127 is supported)
     * @param x     x position in screen pixels (from the left)
     * @param y     y position in screen pixels (from the bottom)
     * @param rgba  text colour, components in [0, 1]
     */
    public void drawString(String text, int x, int y, float[] rgba) {
        if (text == null || text.isEmpty()) return;

        GL11.glColor4f(rgba[0], rgba[1], rgba[2], rgba.length > 3 ? rgba[3] : 1.0f);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, textureId);

        int cursorX = x;
        for (int i = 0; i < text.length(); i++) {
            int c = text.charAt(i);
            if (c < FIRST_CHAR || c > LAST_CHAR) continue;
            int idx = c - FIRST_CHAR;
            float[] uv = uvs[idx];

            GL11.glBegin(GL11.GL_QUADS);
            GL11.glTexCoord2f(uv[0], uv[1]); GL11.glVertex2i(cursorX, y);
            GL11.glTexCoord2f(uv[2], uv[1]); GL11.glVertex2i(cursorX + charWidth, y);
            GL11.glTexCoord2f(uv[2], uv[3]); GL11.glVertex2i(cursorX + charWidth, y + charHeight);
            GL11.glTexCoord2f(uv[0], uv[3]); GL11.glVertex2i(cursorX, y + charHeight);
            GL11.glEnd();

            cursorX += charWidths[idx];
        }

        GL11.glBindTexture(GL11.GL_TEXTURE_2D, 0);
    }

    /** Convenience: draw a string with a solid background panel behind it. */
    public void drawStringWithBackground(String text, int x, int y, float[] textColor, float[] bgColor, int padding) {
        int textWidth = stringWidth(text);
        int textHeight = charHeight;

        // Background panel (drawn without texturing).
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glColor4f(bgColor[0], bgColor[1], bgColor[2], bgColor.length > 3 ? bgColor[3] : 0.6f);
        GL11.glBegin(GL11.GL_QUADS);
        GL11.glVertex2i(x - padding, y - padding);
        GL11.glVertex2i(x + textWidth + padding, y - padding);
        GL11.glVertex2i(x + textWidth + padding, y + textHeight + padding);
        GL11.glVertex2i(x - padding, y + textHeight + padding);
        GL11.glEnd();
        GL11.glEnable(GL11.GL_TEXTURE_2D);

        drawString(text, x, y, textColor);
    }

    /** Compute the pixel width of a string if rendered with this font. */
    public int stringWidth(String text) {
        int w = 0;
        for (int i = 0; i < text.length(); i++) {
            int c = text.charAt(i);
            if (c < FIRST_CHAR || c > LAST_CHAR) continue;
            w += charWidths[c - FIRST_CHAR];
        }
        return w;
    }

    public int getCharHeight() {
        return charHeight;
    }

    /** Free the texture. Call before destroying the GL context. */
    public void dispose() {
        if (textureId != 0) {
            GL11.glDeleteTextures(textureId);
            textureId = 0;
        }
    }
}
