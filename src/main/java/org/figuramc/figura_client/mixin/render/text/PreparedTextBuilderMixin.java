package org.figuramc.figura_client.mixin.render.text;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.font.TextRenderable;
import net.minecraft.client.gui.font.glyphs.BakedGlyph;
import net.minecraft.network.chat.Style;
import org.figuramc.figura_client.ducks.StyleAccess;
import org.joml.Vector2f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(Font.PreparedTextBuilder.class)
public abstract class PreparedTextBuilderMixin {

    // State used for baking the style
    @Unique private int charIndex = 0;

    // Bake figura style before calling the original method
    @WrapMethod(method = "accept(ILnet/minecraft/network/chat/Style;Lnet/minecraft/client/gui/font/glyphs/BakedGlyph;)Z")
    public boolean maybeReplace(int i, Style style, BakedGlyph bakedGlyph, Operation<Boolean> original) {
        return original.call(i, ((StyleAccess) style).figura_client$bakeFiguraStyle(charIndex++), bakedGlyph);
    }

    // Apply offset to the baked glyph
    @WrapOperation(method = "accept(ILnet/minecraft/network/chat/Style;Lnet/minecraft/client/gui/font/glyphs/BakedGlyph;)Z", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/font/glyphs/BakedGlyph;createGlyph(FFIILnet/minecraft/network/chat/Style;FF)Lnet/minecraft/client/gui/font/TextRenderable$Styled;"))
    public TextRenderable.Styled applyOffset(BakedGlyph instance, float x, float y, int textColor, int shadowColor, Style style, float boldOffset, float shadowOffset, Operation<TextRenderable.Styled> original) {
        Vector2f offset = ((StyleAccess) style).figura_client$getOffset();
        if (offset != null) {
            x += offset.x;
            y += offset.y;
        }
        return original.call(instance, x, y, textColor, shadowColor, style, boldOffset, shadowOffset);
    }

}
