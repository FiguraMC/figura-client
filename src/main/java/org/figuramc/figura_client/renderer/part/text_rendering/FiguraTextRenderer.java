package org.figuramc.figura_client.renderer.part.text_rendering;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GlyphSource;
import net.minecraft.client.gui.font.TextRenderable;
import net.minecraft.client.gui.font.glyphs.BakedGlyph;
import net.minecraft.client.gui.font.glyphs.EffectGlyph;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import org.figuramc.figura_core.text.FormattedText;
import org.figuramc.figura_core.text.TextStyle;
import org.figuramc.figura_core.util.data_structures.FiguraTransformStack;
import org.joml.Matrix4f;
import org.joml.Vector2f;
import org.joml.Vector4f;

import java.util.ArrayList;

/**
 * Class containing logic for rendering text.
 */
public class FiguraTextRenderer {

    // Exposed method to do rendering.
    public static void render(FormattedText formattedText, MultiBufferSource bufferSource, Matrix4f worldRelativeMatrix, int light, int overlay) {
        FiguraTextRenderer renderer = new FiguraTextRenderer(bufferSource, worldRelativeMatrix, light, overlay);
        renderer.processChars(formattedText, 0);
        renderer.renderLine(); // Flush the final line
    }

    private static final char UNKNOWN = '�';

    private final MultiBufferSource bufferSource;
    private final Matrix4f matrix; // Main matrix, set once and never modified
    private final Matrix4f tempMatrix = new Matrix4f(); // Temp matrix, modified for each char as needed
    private final Matrix4f skewMatrix = new Matrix4f();
    private final int light, overlay;
    private final CursedCustomStyle cursedCustomStyle = new CursedCustomStyle();

    // Cur vars
    private TextStyle style;
    private final Font font;
    private GlyphSource glyphSource;
    private EffectGlyph effectGlyph;

    // Accumulation throughout line(s)
    private ArrayList<QueuedGlyph> line = new ArrayList<>();
    private float lineHeight;
    private float y = 0;

    private FiguraTextRenderer(MultiBufferSource bufferSource, Matrix4f matrix, int light, int overlay) {
        this.font = Minecraft.getInstance().font;
        this.bufferSource = bufferSource;
        this.matrix = matrix;
        this.light = light;
        this.overlay = overlay;
    }

    /**
     * Process chars for the given text piece, with the given index. Return the # of chars rendered. Recursive.
     */
    private int processChars(FormattedText text, int charIndex) {
        // TODO make font configurable? Wouldn't be controllable by molang since it's not a float, though :/
        glyphSource = font.getGlyphSource(new FontDescription.Resource(Identifier.parse("minecraft:default")));
        effectGlyph = font.provider.effect();
        style = text.style;
        // Process codepoints
        for (int codepoint : text.codepoints)
            processChar(charIndex++, codepoint);
        // Process children
        for (FormattedText child : text.children)
            charIndex = processChars(child, charIndex);
        // Return index
        return charIndex;
    }

    /**
     * Process the given char
     */
    private void processChar(int charIndex, int codepoint) {
        Vector2f scale = style.scale.value(charIndex);
        float glyphHeight = font.lineHeight * scale.y;
        this.lineHeight = Math.max(this.lineHeight, glyphHeight);
        // If the codepoint is a newline, flush this line rendering; otherwise process the char
        if (codepoint == '\n') {
            renderLine();
        } else {
            // Process the char and add it to the current line, baking its size.
            BakedGlyph glyph = glyphSource.getGlyph(codepoint);
            if (style.obfuscated.value(charIndex)) {
                int baseWidth = Mth.ceil(glyph.info().getAdvance(false));
                glyph = glyphSource.getRandomGlyph(font.random, baseWidth);
            }
            boolean bold = style.bold.value(charIndex);
            float width = glyph.info().getAdvance(bold) * scale.x;

            line.add(new QueuedGlyph(glyph, bold, scale.x, scale.y, width, glyphHeight, style, charIndex));
        }
    }

    /**
     * Flush the queue and render chars for this line
     */
    private void renderLine() {
        boolean empty = line.isEmpty();
        float x = 0;
        for (QueuedGlyph queuedGlyph : line) {
            renderGlyph(x, queuedGlyph);
            x += queuedGlyph.width;
        }
        y += empty ? font.lineHeight : lineHeight;
        line.clear();
        lineHeight = 0;
    }

    /**
     * Render a queued glyph
     */
    private void renderGlyph(float x, QueuedGlyph queuedGlyph) {
        // Localize vars
        float charIndex = queuedGlyph.charIndex;
        TextStyle style = queuedGlyph.style;
        BakedGlyph glyph = queuedGlyph.glyph;
        float width = queuedGlyph.width;
        float height = queuedGlyph.height;
        float scaleX = queuedGlyph.scaleX;
        float scaleY = queuedGlyph.scaleY;
        float y = this.y;
        final float initialX = x, initialY = y;

        // Background color
//        Vector4f backgroundColor = style.backgroundColor.value(charIndex);
//        if (backgroundColor.w != 0) renderEffect(initialX, initialY, initialX + width, initialY + height, backgroundColor);

        // Colors
        int color = toIntColor(style.color.value(charIndex));
        int backgroundColor = toIntColor(style.backgroundColor.value(charIndex));
        int shadowColor = toIntColor(style.shadowColor.value(charIndex));
        int outlineColor = toIntColor(style.outlineColor.value(charIndex));
        int strikethroughColor = toIntColor(style.strikethroughColor.value(charIndex));
        int underlineColor = toIntColor(style.underlineColor.value(charIndex));

        // Offset (does not affect location of extra effects like background, underline, and strikethrough)
        Vector2f offset = style.offset.value(charIndex);
        x += offset.x; y += offset.y;

        // Alignment
        y += (lineHeight - height) * style.verticalAlignment.value(charIndex);

        // Skew
        float skewX = 0;
        if (style.italic.value(charIndex))
            skewX += 1;
        Vector2f skew = style.skew.value(charIndex);
        skewX += skew.x;
        float skewY = skew.y;

        // Modify temp matrix.
        // tempMatrix includes translation, so coordinates passed to rendering functions can be relative to 0,0.
        matrix.translate(x, y, 0, tempMatrix); // Translate to glyph's location
        tempMatrix.scale(scaleX, scaleY, 1); // Scale around glyph location
        applySkew(skewX, skewY); // Apply skew around glyph location

        // Prepare the char to be rendered
        cursedCustomStyle.overrideBoldValue = queuedGlyph.bold;
        float shadowOffset = glyph.info().getShadowOffset();

        // Outline
        if (outlineColor != 0) {
            Vector2f outlineScale = style.outlineScale.value(charIndex);
            for (int oY = -1; oY <= 1; oY++) {
                for (int oX = -1; oX <= 1; oX++) {
                    if (oX == 0 && oY == 0) continue;
                    float cX = oX * outlineScale.x;
                    float cY = oY * outlineScale.y;
                    TextRenderable outlineChar = glyph.createGlyph(cX, cY, outlineColor, shadowColor, cursedCustomStyle, glyph.info().getBoldOffset(), shadowOffset);
                    if (outlineChar != null) outlineChar.render(tempMatrix, bufferSource.getBuffer(outlineChar.renderType(Font.DisplayMode.NORMAL)), light, false);
                }
            }
        }

        // Params: x, y, color, shadowColor, style, boldOffset, shadowOffset
        TextRenderable character = glyph.createGlyph(0, 0, color, shadowColor, cursedCustomStyle, glyph.info().getBoldOffset(), shadowOffset);
        if (character != null) character.render(tempMatrix, bufferSource.getBuffer(character.renderType(Font.DisplayMode.NORMAL)), light, false);

        // Render strikethrough
        if (strikethroughColor != 0) {
            float cY1 = initialY + (lineHeight / 2) - 1;
            float cY2 = cY1 + 1;
            // Params: x0, y0, x1, y1, depth, color, shadowColor, shadowOffset
            TextRenderable strikethrough = effectGlyph.createEffect(initialX, cY1, initialX + width, cY2, 0.01f, strikethroughColor, shadowColor, shadowOffset);
            strikethrough.render(matrix, bufferSource.getBuffer(strikethrough.renderType(Font.DisplayMode.NORMAL)), light, false);
        }

        // Render underline
        if (underlineColor != 0) {
            float cY2 = initialY + lineHeight;
            float cY1 = cY2 - 1;
            // Params: x0, y0, x1, y1, depth, color, shadowColor, shadowOffset
            TextRenderable underline = effectGlyph.createEffect(initialX, cY1, initialX + width, cY2, 0.01f, underlineColor, shadowColor, shadowOffset);
            underline.render(matrix, bufferSource.getBuffer(underline.renderType(Font.DisplayMode.NORMAL)), light, false);
        }
    }

    private static int toIntColor(Vector4f rgbaColor) {
        if (rgbaColor.w == 0) return 0; // Zero alpha is all the same, just return 0 immediately.
        return ARGB.colorFromFloat(clamp01(rgbaColor.w), clamp01(rgbaColor.x), clamp01(rgbaColor.y), clamp01(rgbaColor.z));
    }
    private static float clamp01(float val) {
        return Math.clamp(val, 0, 1);
    }

    // Apply skew into skewMatrix, then multiply into tempMatrix
    private float lastSkewX, lastSkewY;
    private void applySkew(float skewX, float skewY) {
        // Set up the skew matrix if needed
        if (skewX != lastSkewX || skewY != lastSkewY) {
            skewMatrix.identity();
            skewMatrix.m10(-0.25f * skewX).m30(skewX);
            skewMatrix.m01(0.25f * skewY).m31(-skewY);
            lastSkewX = skewX;
            lastSkewY = skewY;
        }
        // Apply it to the temp matrix if needed
        if (skewX != 0 || skewY != 0) tempMatrix.mul(skewMatrix);
    }

    private record QueuedGlyph(BakedGlyph glyph, boolean bold, float scaleX, float scaleY, float width, float height, TextStyle style, int charIndex) {

    }

}
