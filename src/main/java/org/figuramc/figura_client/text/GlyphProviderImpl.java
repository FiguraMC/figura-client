package org.figuramc.figura_client.text;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.font.CodepointMap;
import net.minecraft.client.gui.font.glyphs.BakedGlyph;
import net.minecraft.client.gui.font.glyphs.BakedSheetGlyph;
import net.minecraft.network.chat.FontDescription;
import org.figuramc.figura_client.textures.StandaloneMinecraftTextureImpl;
import org.figuramc.figura_core.minecraft_interop.text.MinecraftGlyphProvider;
import org.figuramc.figura_core.minecraft_interop.texture.MinecraftTexture;
import org.figuramc.figura_core.model.rendering.FiguraRenderType;
import org.joml.Vector4f;

public class GlyphProviderImpl implements MinecraftGlyphProvider {

    private final CodepointMap<GlyphInfo> CACHE = new CodepointMap<>(GlyphInfo[]::new, GlyphInfo[][]::new);

    @Override
    public float getDefaultLineHeight() {
        return 9;
    }

    @Override
    public GlyphInfo getGlyphInfo(int codepoint, boolean obfuscated) {
        if (obfuscated) {
            throw new UnsupportedOperationException("TODO: Support obfuscated chars");
        } else {
            return CACHE.computeIfAbsent(codepoint, c -> {
                BakedGlyph glyph = Minecraft.getInstance().font.getGlyphSource(FontDescription.DEFAULT).getGlyph(c);
                return switch (glyph) {
                    case BakedSheetGlyph bakedSheetGlyph -> {
                        MinecraftTexture tex = new StandaloneMinecraftTextureImpl(bakedSheetGlyph.textureView);
                        Vector4f uvModifier = new Vector4f(bakedSheetGlyph.u0, bakedSheetGlyph.v0, bakedSheetGlyph.u1 - bakedSheetGlyph.u0, bakedSheetGlyph.v1 - bakedSheetGlyph.v0);
                        FiguraRenderType.TextureBinding binding = new FiguraRenderType.TextureBinding(tex, uvModifier);
                        yield new MinecraftGlyphProvider.GlyphInfo(binding, bakedSheetGlyph.left, bakedSheetGlyph.right, bakedSheetGlyph.up, bakedSheetGlyph.down, bakedSheetGlyph.info().getAdvance(), bakedSheetGlyph.info().getBoldOffset());
                    }
                    default -> throw new UnsupportedOperationException("TODO: Support other glyph kinds than baked sheet glyphs");
                };
            });
        }
    }
}
