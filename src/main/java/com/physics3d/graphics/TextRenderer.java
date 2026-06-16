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
import java.nio.IntBuffer;

/**
 * Bitmap font renderer for LWJGL using AWT fonts and OpenGL display lists.
 *
 * <p>Each ASCII character (32..127) is rasterised once into a small RGBA texture
 * and bound to a display list that calls {@code glDrawPixels} / {@code glBitmap}
 * to render glyphs. This is the classic fixed-function pipeline approach and
 * works well for HUD overlays because it does not require shaders.</p>
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

    /** OpenGL display list base id for the glyphs. */
    private int listBase;

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

        buildDisplayLists();
    }

    /**
     * Rasterise every supported glyph into an OpenGL display list.
     * Each list issues a {@code glBitmap} call that draws the glyph at the
     * current raster position and advances the cursor.
     */
    private void buildDisplayLists() {
        // Render every glyph into one wide image, then upload as a single
        // bitmap. We use glBitmap (not textures) so no shaders are needed.
        int atlasWidth = charWidth * CHAR_COUNT;
        int atlasHeight = charHeight;

        BufferedImage atlas = new BufferedImage(atlasWidth, atlasHeight, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = atlas.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
        g.setFont(font);
        g.setColor(Color.WHITE);
        g.setBackground(new Color(0, 0, 0, 0));
        g.clearRect(0, 0, atlasWidth, atlasHeight);
        g.setColor(Color.WHITE);
        g.drawString(new String(toCharArray(FIRST_CHAR, LAST_CHAR)), 0, font.getSize());

        // Convert to a packed RGBA byte buffer (top-left origin in AWT -> bottom-left in GL).
        byte[] pixels = new byte[atlasWidth * atlasHeight * 4];
        int[] argb = atlas.getRGB(0, 0, atlasWidth, atlasHeight, null, 0, atlasWidth);
        for (int i = 0; i < argb.length; i++) {
            int c = argb[i];
            int j = i * 4;
            pixels[j + 0] = (byte) ((c >> 16) & 0xFF); // R
            pixels[j + 1] = (byte) ((c >> 8) & 0xFF);  // G
            pixels[j + 2] = (byte) (c & 0xFF);         // B
            pixels[j + 3] = (byte) ((c >> 24) & 0xFF); // A
        }

        ByteBuffer pixelBuffer = ByteBuffer.allocateDirect(pixels.length).order(ByteOrder.nativeOrder());
        pixelBuffer.put(pixels).flip();

        // Allocate display lists, one per character.
        listBase = GL11.glGenLists(CHAR_COUNT);
        for (int i = 0; i < CHAR_COUNT; i++) {
            GL11.glNewList(listBase + i, GL11.GL_COMPILE);
            GL11.glBitmap(charWidth, charHeight,
                    0.0f, 0.0f,
                    charWidths[i], 0.0f,
                    extractGlyphRow(pixelBuffer, atlasWidth, atlasHeight, i));
            GL11.glEndList();
        }

        g.dispose();
    }

    /** Build a char[] containing every supported glyph for atlas rasterisation. */
    private static char[] toCharArray(int first, int last) {
        char[] chars = new char[last - first + 1];
        for (int i = 0; i < chars.length; i++) {
            chars[i] = (char) (first + i);
        }
        return chars;
    }

    /**
     * Extract a single glyph row from the atlas as a packed GL bitmap.
     * The bitmap is stored bottom-up, so we flip the Y axis.
     */
    private ByteBuffer extractGlyphRow(ByteBuffer atlas, int atlasWidth, int atlasHeight, int charIndex) {
        int glyphWidth = charWidth;
        int glyphHeight = charHeight;
        int srcX = charIndex * charWidth;

        // GL_BITMAP expects rows padded to multiples of 8 bits (1 byte).
        int paddedWidth = (glyphWidth + 7) & ~7;
        byte[] data = new byte[paddedWidth * glyphHeight];

        int[] src = new int[glyphWidth * glyphHeight];
        // We re-read from the atlas's underlying image; simpler: re-rasterise
        // the single glyph into its own image and convert.
        BufferedImage glyphImg = newBufferedImage(glyphWidth, glyphHeight);
        Graphics2D gg = glyphImg.createGraphics();
        gg.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        gg.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
        gg.setFont(font);
        gg.setColor(Color.WHITE);
        gg.clearRect(0, 0, glyphWidth, glyphHeight);
        gg.drawString(String.valueOf((char) (FIRST_CHAR + charIndex)), 0, font.getSize());
        gg.dispose();

        int[] argb = glyphImg.getRGB(0, 0, glyphWidth, glyphHeight, null, 0, glyphWidth);
        // Convert alpha to 8-bit grayscale, then pack into the GL bitmap.
        // GL_BITMAP interprets each byte as 8 pixels (1 bit each).
        for (int y = 0; y < glyphHeight; y++) {
            int srcRow = (glyphHeight - 1 - y); // flip Y
            for (int x = 0; x < paddedWidth; x++) {
                int bit = 0;
                if (x < glyphWidth) {
                    int a = (argb[srcRow * glyphWidth + x] >>> 24) & 0xFF;
                    bit = a > 64 ? 1 : 0;
                }
                if ((x & 7) == 0) {
                    data[y * paddedWidth + (x >> 3)] = 0;
                }
                if (bit != 0) {
                    data[y * paddedWidth + (x >> 3)] |= (byte) (1 << (7 - (x & 7)));
                }
            }
        }

        ByteBuffer buf = ByteBuffer.allocateDirect(data.length).order(ByteOrder.nativeOrder());
        buf.put(data).flip();
        return buf;
    }

    private static BufferedImage newBufferedImage(int w, int h) {
        return new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
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

        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_COLOR_BUFFER_BIT | GL11.GL_PIXEL_MODE_BIT);
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glPixelStorei(GL11.GL_UNPACK_ALIGNMENT, 1);
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

        // glBitmap uses the current raster position; set it in pixels.
        GL11.glRasterPos2i(x, y);

        // Issue one display list call per character.
        GL11.glPushAttrib(GL11.GL_CURRENT_BIT);
        for (int i = 0; i < text.length(); i++) {
            int c = text.charAt(i);
            if (c < FIRST_CHAR || c > LAST_CHAR) continue;
            GL11.glCallList(listBase + (c - FIRST_CHAR));
        }
        GL11.glPopAttrib();
    }

    /** Convenience: draw a string with a solid background panel behind it. */
    public void drawStringWithBackground(String text, int x, int y, float[] textColor, float[] bgColor, int padding) {
        int textWidth = stringWidth(text);
        int textHeight = charHeight;

        // Background panel
        GL11.glColor4f(bgColor[0], bgColor[1], bgColor[2], bgColor.length > 3 ? bgColor[3] : 0.6f);
        GL11.glBegin(GL11.GL_QUADS);
        GL11.glVertex2i(x - padding, y - padding);
        GL11.glVertex2i(x + textWidth + padding, y - padding);
        GL11.glVertex2i(x + textWidth + padding, y + textHeight + padding);
        GL11.glVertex2i(x - padding, y + textHeight + padding);
        GL11.glEnd();

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

    /** Free the display lists. Call before destroying the GL context. */
    public void dispose() {
        if (listBase != 0) {
            GL11.glDeleteLists(listBase, CHAR_COUNT);
            listBase = 0;
        }
    }
}
