package org.figuramc.figura_client.textures;

import com.mojang.blaze3d.textures.GpuTextureView;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.resources.Identifier;
import org.figuramc.figura_client.util.RenderUtils;
import org.figuramc.figura_core.minecraft_interop.texture.MinecraftTexture;
import org.figuramc.figura_core.minecraft_interop.texture.ReadableMinecraftTexture;
import org.figuramc.figura_core.util.data_structures.Either;
import org.figuramc.memory_tracker.AllocationTracker;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.CompletableFuture;

public class StandaloneMinecraftTextureImpl implements MinecraftTexture {

    private Identifier identifier;
    public GpuTextureView textureView;

    public StandaloneMinecraftTextureImpl(Identifier identifier) {
        this.identifier = identifier;
    }
    public StandaloneMinecraftTextureImpl(GpuTextureView textureView) {
        this.textureView = textureView;
    }

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
        if (textureView != null) return CompletableFuture.completedFuture(null);
        else return RenderUtils.runOnRenderThread(() -> {
            this.textureView = Minecraft.getInstance().getTextureManager().getTexture(identifier).getTextureView();
        });
    }

    @Override
    public <E extends Throwable> ReadableMinecraftTexture makeReadable(@Nullable AllocationTracker<E> allocationTracker) throws E {
        throw new UnsupportedOperationException("TODO");
    }

}
