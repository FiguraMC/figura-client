package org.figuramc.figura_client.renderer.text;

import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.util.ARGB;
import org.figuramc.figura_core.text.TextStyle;
import org.joml.Vector4f;

/**
 * Extend Minecraft "style" using our own stuff.
 * We do this so we can access a Figura TextStyle within mixins.
 */
public class FiguraStyle extends Style {

    public final TextStyle figuraStyle;

    public FiguraStyle(TextStyle figuraStyle) {
        super(null, null, null, null, null, null, null, null, null, null, null);
        this.figuraStyle = figuraStyle;
    }

    @Override
    public Style applyTo(Style other) {
        // Just always return this. We say Figura styles override any other styles.
        return this;
    }

    /**
     * Bake this to a locked Style using the given dynamic state (char index).
     * TODO probably cache some stuff to improve performance, for example if figuraStyle is non-dynamic
     */
    public Style bakeStyle(int charIndex) {
        Vector4f textColor = figuraStyle.color.value(charIndex);
        Vector4f shadowColor = figuraStyle.shadowColor.value(charIndex);
        Vector4f underlineColor = figuraStyle.underlineColor.value(charIndex);
        Vector4f strikethroughColor = figuraStyle.strikethroughColor.value(charIndex);

        // TODO: Add more fields!
        Style bakedStyle = new Style(
                TextColor.fromRgb(ARGB.colorFromFloat(textColor.w, textColor.x, textColor.y, textColor.z)),
                shadowColor.w == 0 ? 0 : ARGB.colorFromFloat(shadowColor.w, shadowColor.x, shadowColor.y, shadowColor.z),
                figuraStyle.bold.value(charIndex),
                figuraStyle.italic.value(charIndex),
                underlineColor.w != 0, // TODO underline color
                strikethroughColor.w != 0, // TODO strikethrough color
                figuraStyle.obfuscated.value(charIndex),
                null,
                null,
                null,
                null
        );

        return bakedStyle;
    }

}
