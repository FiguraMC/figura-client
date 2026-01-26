package org.figuramc.figura_client.renderer.text;

import net.minecraft.network.chat.*;
import org.jetbrains.annotations.Nullable;

// Cursed Style impl which only cares about boldness
public class CursedCustomStyle extends Style {

    public CursedCustomStyle() {
        this(null, null, null, null, null, null, null, null, null, null, null);
    }

    public CursedCustomStyle(
            @Nullable TextColor textColor,
            @Nullable Integer integer,
            @Nullable Boolean boolean_,
            @Nullable Boolean boolean2,
            @Nullable Boolean boolean3,
            @Nullable Boolean boolean4,
            @Nullable Boolean boolean5,
            @Nullable ClickEvent clickEvent,
            @Nullable HoverEvent hoverEvent,
            @Nullable String string,
            @Nullable FontDescription fontDescription
    ) {
        super(textColor, integer, boolean_, boolean2, boolean3, boolean4, boolean5, clickEvent, hoverEvent, string, fontDescription);
    }

    public boolean overrideBoldValue;

    @Override
    public boolean isBold() {
        return overrideBoldValue;
    }
}
