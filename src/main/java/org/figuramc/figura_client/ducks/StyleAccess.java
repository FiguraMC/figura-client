package org.figuramc.figura_client.ducks;

import net.minecraft.network.chat.Style;
import org.figuramc.figura_core.text.TextStyle;
import org.joml.Vector2f;

/**
 * Accessors for the custom fields and methods we add to Style
 */
public interface StyleAccess {

    // Bake the style using the given char index as state.
    // If this isn't a Figura style, style should just return itself.
    Style figura_client$bakeFiguraStyle(int charIndex);

    // Fetch values from a baked figura style
    Vector2f figura_client$getOffset();

    // Use the given Figura Style on this style
    // (TODO: This mutates the style... should we change it?)
    void figura_client$setFiguraStyle(TextStyle figuraStyle);

}
