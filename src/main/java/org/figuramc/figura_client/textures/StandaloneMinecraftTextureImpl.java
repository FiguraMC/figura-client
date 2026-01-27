package org.figuramc.figura_client.textures;

import com.mojang.blaze3d.textures.GpuTextureView;
import org.figuramc.figura_core.minecraft_interop.texture.MinecraftTexture;
import org.figuramc.figura_core.minecraft_interop.texture.ReadableMinecraftTexture;
import org.figuramc.memory_tracker.AllocationTracker;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.CompletableFuture;

public record StandaloneMinecraftTextureImpl(GpuTextureView textureView) implements MinecraftTexture {

    @Override
    public int width() {
        return textureView.getWidth(0);
    }

    @Override
    public int height() {
        return textureView.getHeight(0);
    }

    @Override
    public CompletableFuture<Void> readyToUse() {
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public <E extends Throwable> ReadableMinecraftTexture makeReadable(@Nullable AllocationTracker<E> allocationTracker) throws E {
        throw new UnsupportedOperationException("TODO");
    }

}
