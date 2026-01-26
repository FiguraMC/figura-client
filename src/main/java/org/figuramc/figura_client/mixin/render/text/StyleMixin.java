package org.figuramc.figura_client.mixin.render.text;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.network.chat.*;
import net.minecraft.util.ARGB;
import org.figuramc.figura_client.ducks.StyleAccess;
import org.figuramc.figura_core.text.TextStyle;
import org.joml.Vector2f;
import org.joml.Vector4f;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(Style.class)
public class StyleMixin implements StyleAccess {

    // Add field for Figura style
    @Unique TextStyle figuraStyle;

    // Shadows
    @Shadow @Final @Nullable TextColor color;
    @Shadow @Final @Nullable ClickEvent clickEvent;
    @Shadow @Final @Nullable HoverEvent hoverEvent;
    @Shadow @Final @Nullable String insertion;
    @Shadow @Final @Nullable FontDescription font;

    // Need to inject at tons of different places to ensure our custom fields are kept through any immutable copies
    @ModifyReturnValue(method = "withColor(Lnet/minecraft/network/chat/TextColor;)Lnet/minecraft/network/chat/Style;", at = @At("RETURN")) public Style copyStyle0(Style original) { return copyStyle(original); }
    @ModifyReturnValue(method = "withShadowColor", at = @At("RETURN")) public Style copyStyle1(Style original) { return copyStyle(original); }
    @ModifyReturnValue(method = "withBold", at = @At("RETURN")) public Style copyStyle2(Style original) { return copyStyle(original); }
    @ModifyReturnValue(method = "withItalic", at = @At("RETURN")) public Style copyStyle3(Style original) { return copyStyle(original); }
    @ModifyReturnValue(method = "withUnderlined", at = @At("RETURN")) public Style copyStyle4(Style original) { return copyStyle(original); }
    @ModifyReturnValue(method = "withStrikethrough", at = @At("RETURN")) public Style copyStyle5(Style original) { return copyStyle(original); }
    @ModifyReturnValue(method = "withObfuscated", at = @At("RETURN")) public Style copyStyle6(Style original) { return copyStyle(original); }
    @ModifyReturnValue(method = "withClickEvent", at = @At("RETURN")) public Style copyStyle7(Style original) { return copyStyle(original); }
    @ModifyReturnValue(method = "withHoverEvent", at = @At("RETURN")) public Style copyStyle8(Style original) { return copyStyle(original); }
    @ModifyReturnValue(method = "withInsertion", at = @At("RETURN")) public Style copyStyle9(Style original) { return copyStyle(original); }
    @ModifyReturnValue(method = "withFont", at = @At("RETURN")) public Style copyStyle10(Style original) { return copyStyle(original); }
    @ModifyReturnValue(method = "applyFormat", at = @At("RETURN")) public Style copyStyle11(Style original) { return copyStyle(original); }
    @ModifyReturnValue(method = "applyLegacyFormat", at = @At("RETURN")) public Style copyStyle12(Style original) { return copyStyle(original); }
    @ModifyReturnValue(method = "applyFormats", at = @At("RETURN")) public Style copyStyle13(Style original) { return copyStyle(original); }
    @ModifyReturnValue(method = "applyTo", at = @At("RETURN")) public Style copyStyle14(Style original, Style other) { return copyStyle(original, other); }

    @Unique
    private Style copyStyle(Style result) {
        ((StyleMixin) (Object) result).figuraStyle = this.figuraStyle;
        return result;
    }

    @Unique
    private Style copyStyle(Style result, Style other) {
        ((StyleMixin) (Object) result).figuraStyle = this.figuraStyle != null ? this.figuraStyle : ((StyleMixin) (Object) other).figuraStyle;
        return result;
    }

    // Custom new style fields
    @Unique private Vector2f offset;

    @Override public Vector2f figura_client$getOffset() { return offset; }

    // Baking

    @Override
    public Style figura_client$bakeFiguraStyle(int charIndex) {
        if (figuraStyle == null) return (Style) (Object) this;

        // TODO probably cache some stuff to improve performance, for example if figuraStyle is non-dynamic
        Vector4f textColor = figuraStyle.color.value(charIndex);
        Vector4f shadowColor = figuraStyle.shadowColor.value(charIndex);
        Vector4f underlineColor = figuraStyle.underlineColor.value(charIndex);
        Vector4f strikethroughColor = figuraStyle.strikethroughColor.value(charIndex);

        // TODO: Add more fields
        Style bakedStyle = new Style(
                TextColor.fromRgb(ARGB.colorFromFloat(textColor.w, textColor.x, textColor.y, textColor.z)),
                shadowColor.w == 0 ? 0 : ARGB.colorFromFloat(shadowColor.w, shadowColor.x, shadowColor.y, shadowColor.z),
                figuraStyle.bold.value(charIndex),
                figuraStyle.italic.value(charIndex),
                underlineColor.w != 0, // TODO underline color
                strikethroughColor.w != 0, // TODO strikethrough color
                figuraStyle.obfuscated.value(charIndex),
                // TODO: should these be null-ed? Or kept?
                this.clickEvent,
                this.hoverEvent,
                this.insertion,
                this.font
        );
        // Bake custom fields
        ((StyleMixin) (Object) bakedStyle).offset = figuraStyle.offset.value(charIndex);

        return bakedStyle;
    }

    @Override
    public void figura_client$setFiguraStyle(TextStyle figuraStyle) {
        this.figuraStyle = figuraStyle;
    }
}
