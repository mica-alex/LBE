package com.micatechnologies.minecraft.lbe.client.render;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GLAllocation;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.WorldVertexBufferUploader;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.opengl.GL11;

/**
 * Collects everything drawn on one flat screen — coloured shapes, one texture's quads, and text —
 * and draws each kind in a single call when {@link #flush()} is reached.
 *
 * <p>Why it exists: drawing each rectangle with its own {@code Tessellator.draw()}, and each
 * character with the font renderer's own begin/end, made a room of twenty machines issue thousands
 * of draw calls a frame. The pixels are the same either way, because every screen already keeps
 * its layers apart by depth rather than by drawing order: shapes, then the textured quads above
 * them, then text in front. Within a kind, primitives are drawn in the order they were added, so
 * two shapes on one layer overlap exactly as they did.
 *
 * <p>Vertices are in the model-view matrix current at {@link #flush()}. Callers flush before they
 * change the matrix; nothing here can check that for them.
 *
 * <p>Text is laid out from the vanilla font sheet with vanilla's own glyph widths and texture
 * coordinates ({@code FontRenderer.renderDefaultChar}). Anything that sheet cannot draw the same
 * way — the unicode font, right-to-left text, formatting codes, characters outside printable ASCII
 * — goes through {@link FontRenderer#drawString} instead, after a flush, exactly as before.
 *
 * <p><b>Client only</b>, and render thread only: one batch is reused for every screen.
 */
final class ScreenBatch {

    private static final ResourceLocation ASCII = new ResourceLocation("textures/font/ascii.png");

    private final BufferBuilder shapes = new BufferBuilder(8192);
    private final BufferBuilder textured = new BufferBuilder(1024);
    private final BufferBuilder text = new BufferBuilder(8192);
    private final WorldVertexBufferUploader uploader = new WorldVertexBufferUploader();

    private boolean shapesOpen;
    private boolean texturedOpen;
    private boolean textOpen;
    private ResourceLocation texture;

    /** The buffer for coloured triangles ({@code POSITION_COLOR}, {@code GL_TRIANGLES}). */
    BufferBuilder shapes() {
        if (!shapesOpen) {
            shapes.begin(GL11.GL_TRIANGLES, DefaultVertexFormats.POSITION_COLOR);
            shapesOpen = true;
        }
        return shapes;
    }

    /** An axis-aligned rectangle at depth {@code z}, as two triangles. */
    void rect(float x1, float y1, float x2, float y2, int argb, float z) {
        BufferBuilder buffer = shapes();
        vertex(buffer, x1, y2, z, argb);
        vertex(buffer, x2, y2, z, argb);
        vertex(buffer, x2, y1, z, argb);
        vertex(buffer, x1, y2, z, argb);
        vertex(buffer, x2, y1, z, argb);
        vertex(buffer, x1, y1, z, argb);
    }

    static void vertex(BufferBuilder buffer, double x, double y, float z, int argb) {
        buffer.pos(x, y, z)
            .color((argb >> 16) & 0xFF, (argb >> 8) & 0xFF, argb & 0xFF, (argb >>> 24) & 0xFF)
            .endVertex();
    }

    /**
     * The buffer for quads of {@code location} ({@code POSITION_TEX}, {@code GL_QUADS}), drawn in
     * white. One texture per flush: asking for another flushes what came before.
     */
    BufferBuilder textured(ResourceLocation location) {
        if (texturedOpen && !location.equals(texture)) {
            flush();
        }
        if (!texturedOpen) {
            textured.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_TEX);
            texturedOpen = true;
            texture = location;
        }
        return textured;
    }

    /**
     * Text as {@code font.drawString} would draw it in a matrix of rotate({@code degrees} about z),
     * translate({@code x}, {@code y}, {@code z}), scale({@code scale}): from {@code x}, or centred
     * on it the way {@code drawString(s, -getStringWidth(s) / 2, 0, ...)} is.
     */
    void text(FontRenderer font, String s, float x, float y, float z, float scale, float degrees,
              int rgb, boolean centred) {
        int start = centred ? -font.getStringWidth(s) / 2 : 0;
        if (!canBatch(font, s)) {
            flush();
            GlStateManager.pushMatrix();
            if (degrees != 0.0F) {
                GlStateManager.rotate(degrees, 0.0F, 0.0F, 1.0F);
            }
            GlStateManager.translate(x, y, z);
            GlStateManager.scale(scale, scale, 1.0F);
            font.drawString(s, start, 0, rgb);
            GlStateManager.popMatrix();
            GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
            return;
        }
        if (!textOpen) {
            text.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_TEX_COLOR);
            textOpen = true;
        }
        double radians = Math.toRadians(degrees);
        float cos = (float) Math.cos(radians);
        float sin = (float) Math.sin(radians);
        int r = (rgb >> 16) & 0xFF;
        int g = (rgb >> 8) & 0xFF;
        int b = rgb & 0xFF;
        float pen = start;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            int width = font.getCharWidth(c);
            if (c != ' ') {
                // FontRenderer.renderDefaultChar, as a quad instead of a strip.
                float u = (c % 16) * 8;
                float v = (c / 16) * 8;
                float w = width - 0.01F - 1.0F;
                glyphVertex(x, y, z, scale, cos, sin, pen, 0.0F, u / 128.0F, v / 128.0F, r, g, b);
                glyphVertex(x, y, z, scale, cos, sin, pen, 7.99F, u / 128.0F, (v + 7.99F) / 128.0F,
                    r, g, b);
                glyphVertex(x, y, z, scale, cos, sin, pen + w, 7.99F, (u + w) / 128.0F,
                    (v + 7.99F) / 128.0F, r, g, b);
                glyphVertex(x, y, z, scale, cos, sin, pen + w, 0.0F, (u + w) / 128.0F, v / 128.0F,
                    r, g, b);
            }
            pen += width;
        }
    }

    private void glyphVertex(float x, float y, float z, float scale, float cos, float sin,
                             float gx, float gy, float u, float v, int r, int g, int b) {
        float px = x + gx * scale;
        float py = y + gy * scale;
        text.pos(px * cos - py * sin, px * sin + py * cos, z).tex(u, v).color(r, g, b, 255)
            .endVertex();
    }

    /** Whether the vanilla font sheet draws {@code s} exactly as the font renderer would. */
    private static boolean canBatch(FontRenderer font, String s) {
        if (font.getUnicodeFlag() || font.getBidiFlag()) {
            return false;
        }
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c < 32 || c > 126) {
                return false;
            }
        }
        return true;
    }

    /**
     * Whether text can be recorded into a display list: only when it would be laid out from the
     * sheet here, since the font renderer's own drawing changes GL state a list must not hold.
     */
    static boolean canRecord(FontRenderer font) {
        return !font.getUnicodeFlag() && !font.getBidiFlag();
    }

    /**
     * Records the shapes and text {@code fill} adds into a pair of display lists instead of
     * drawing them, and returns the first. For a face that never changes, only moves: a wheel
     * that turns, a board whose lanes stay put. Replay it with {@link #replay(int)} under whatever
     * matrix it should appear in. Text in {@code fill} must pass {@link #canRecord}.
     *
     * <p>The lists hold vertices and nothing else. Texturing, the font sheet's binding and the
     * colour are set around them on every replay, never inside, because a list replays real GL
     * state behind GlStateManager's back.
     */
    int record(Runnable fill) {
        flush();
        fill.run();
        int list = GLAllocation.generateDisplayLists(2);
        GlStateManager.glNewList(list, GL11.GL_COMPILE);
        if (shapesOpen) {
            shapesOpen = false;
            shapes.finishDrawing();
            uploader.draw(shapes);
        }
        GlStateManager.glEndList();
        GlStateManager.glNewList(list + 1, GL11.GL_COMPILE);
        if (textOpen) {
            textOpen = false;
            text.finishDrawing();
            uploader.draw(text);
        }
        GlStateManager.glEndList();
        return list;
    }

    /** Draws a {@link #record recording} in the current matrix: its shapes, then its text. */
    void replay(int list) {
        flush();
        GlStateManager.disableTexture2D();
        GlStateManager.callList(list);
        GlStateManager.enableTexture2D();
        GlStateManager.enableAlpha();
        Minecraft.getMinecraft().getTextureManager().bindTexture(ASCII);
        GlStateManager.callList(list + 1);
        GlStateManager.resetColor();
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
    }

    /**
     * Draws everything collected, in layer order: shapes, the textured quads, then text. Leaves
     * texturing on and the colour white, as the renderers expect.
     */
    void flush() {
        if (shapesOpen) {
            shapesOpen = false;
            GlStateManager.disableTexture2D();
            shapes.finishDrawing();
            uploader.draw(shapes);
            GlStateManager.enableTexture2D();
        }
        if (texturedOpen) {
            texturedOpen = false;
            Minecraft.getMinecraft().getTextureManager().bindTexture(texture);
            GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
            textured.finishDrawing();
            uploader.draw(textured);
        }
        if (textOpen) {
            textOpen = false;
            // As FontRenderer.drawString leaves things: the sheet bound, the alpha test on.
            GlStateManager.enableAlpha();
            Minecraft.getMinecraft().getTextureManager().bindTexture(ASCII);
            text.finishDrawing();
            uploader.draw(text);
        }
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
    }
}
