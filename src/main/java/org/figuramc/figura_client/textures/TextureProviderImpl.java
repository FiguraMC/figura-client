package org.figuramc.figura_client.textures;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.*;
import net.minecraft.client.renderer.texture.atlas.SpriteSourceList;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.PreparableReloadListener;
import org.figuramc.figura_client.util.RenderUtils;
import org.figuramc.figura_core.data.materials.ModuleMaterials;
import org.figuramc.figura_core.minecraft_interop.game_data.MinecraftIdentifier;
import org.figuramc.figura_core.minecraft_interop.texture.*;
import org.figuramc.figura_core.util.data_structures.Pair;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector4f;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class TextureProviderImpl implements MinecraftTextureProvider {

    @Override
    public OwnedMinecraftTexture createBlankTexture(int width, int height) {
        return new OwnedMinecraftTextureImpl(width, height);
    }

    @Override
    public OwnedMinecraftTexture createTextureFromPng(byte[] pngBytes) throws IOException {
        return new OwnedMinecraftTextureImpl(pngBytes);
    }

//    @Override
//    public @Nullable Pair<MinecraftTexture, Vector4f> getVanillaTexture(MinecraftIdentifier location) {
//        // Translate our MinecraftIdentifier into a real Identifier
//        Identifier textureLocation = Identifier.fromNamespaceAndPath(location.namespace(), location.name());
//        // Look for it in atlases
//        List<TextureAtlas> atlases = new ArrayList<>();
//        Minecraft.getInstance().getAtlasManager().forEach((__, atlas) -> atlases.add(atlas));
//        for (TextureAtlas atlas : atlases) {
//            TextureAtlasSprite sprite = atlas.getSprite(textureLocation);
//            if (sprite != atlas.missingSprite()) {
//                // We found it in an atlas, so return as an atlas
//                AtlasedMinecraftTextureImpl atlasedImpl = new AtlasedMinecraftTextureImpl(atlas, sprite);
//                return new Pair<>(atlasedImpl, atlasedImpl.getUvModifier());
//            }
//        }
//        // It was not found in an atlas, so return a standalone one
//        return new Pair<>(
//                new StandaloneMinecraftTextureImpl(textureLocation.withPrefix("textures/").withSuffix(".png")),
//                new Vector4f(0, 0, 1, 1)
//        );
//    }

    @Override
    public Pair<MinecraftTexture, Vector4f> getBuiltinTexture(ModuleMaterials.BuiltinTextureBinding builtin) {
        return new Pair<>(switch (builtin) {
            case NONE -> new StandaloneMinecraftTextureImpl(RenderUtils.ZERO_PIXEL.getTextureView());
            case LIGHTMAP -> new StandaloneMinecraftTextureImpl(Minecraft.getInstance().gameRenderer.lightTexture().getTextureView());
        }, new Vector4f(0, 0, 1, 1));
    }
}
