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
import org.figuramc.figura_core.model.rendering.FiguraRenderType;
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

    @Override
    public FiguraRenderType.TextureBinding getBuiltinTexture(ModuleMaterials.BuiltinTextureBinding builtin) {
        return switch (builtin) {
            case NONE -> new FiguraRenderType.TextureBinding(
                    new StandaloneMinecraftTextureImpl(RenderUtils.ZERO_PIXEL.getTextureView()),
                    new Vector4f(0, 0, 1, 1)
            );
            case LIGHTMAP -> new FiguraRenderType.TextureBinding(
                    new StandaloneMinecraftTextureImpl(Minecraft.getInstance().gameRenderer.lightTexture().getTextureView()),
                    new Vector4f(0, 0, 1, 1)
            );
        };
    }
}
