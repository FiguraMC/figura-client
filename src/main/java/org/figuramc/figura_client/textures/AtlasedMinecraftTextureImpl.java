package org.figuramc.figura_client.textures;

import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import org.figuramc.figura_core.minecraft_interop.texture.MinecraftTexture;
import org.figuramc.figura_core.minecraft_interop.texture.ReadableMinecraftTexture;
import org.figuramc.memory_tracker.AllocationTracker;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector4f;

import java.util.concurrent.CompletableFuture;

public class AtlasedMinecraftTextureImpl implements MinecraftTexture {

    public TextureAtlas atlas;
    public TextureAtlasSprite atlasSprite;

    public AtlasedMinecraftTextureImpl(TextureAtlas atlas, TextureAtlasSprite atlasSprite) {
        this.atlas = atlas;
        this.atlasSprite = atlasSprite;
    }

    // Compute uv modifier for this texture
    public Vector4f getUvModifier() {
        return new Vector4f(atlasSprite.getU0(), atlasSprite.getV0(), atlasSprite.getU1() - atlasSprite.getU0(), atlasSprite.getV1() - atlasSprite.getV0());
    }

    @Override
    public int width() {
        return atlasSprite.contents().width();
    }

    @Override
    public int height() {
        return atlasSprite.contents().height();
    }

    @Override
    public CompletableFuture<Void> readyToUse() {
        // Ready immediately, since the atlas is already uploaded
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public <E extends Throwable> ReadableMinecraftTexture makeReadable(@Nullable AllocationTracker<E> allocationTracker) throws E {
        throw new UnsupportedOperationException("TODO");
    }

}
