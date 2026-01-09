package org.figuramc.figura_client.textures;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.TextureFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.SimpleTexture;
import net.minecraft.client.renderer.texture.TextureContents;
import net.minecraft.resources.Identifier;
import org.figuramc.figura_client.FiguraClient;
import org.figuramc.figura_client.util.RenderUtils;
import org.figuramc.figura_core.minecraft_interop.texture.OwnedMinecraftTexture;
import org.figuramc.figura_core.minecraft_interop.texture.ReadableMinecraftTexture;

import java.io.IOException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicLong;

/**
 * This feels like it's full of race conditions, I'm scared
 */
public class OwnedMinecraftTextureImpl extends AbstractTexture implements OwnedMinecraftTexture {

    private static final AtomicLong nextID = new AtomicLong();

    private final int width, height;
    public final Identifier location;
    private NativeImage backingTexture;

    // Create blank texture from width/height
    public OwnedMinecraftTextureImpl(int width, int height) {
        String name = "figura_texture_" + nextID.getAndIncrement();
        this.location = Identifier.fromNamespaceAndPath(FiguraClient.MOD_ID, name);
        this.backingTexture = new NativeImage(width, height, true); // Boolean param = whether to zero it, which we will
        this.width = width;
        this.height = height;
    }

    // Create texture from PNG
    public OwnedMinecraftTextureImpl(byte[] pngBytes) throws IOException {
        String name = "figura_texture_" + nextID.getAndIncrement();
        this.location = Identifier.fromNamespaceAndPath(FiguraClient.MOD_ID, name);
        this.backingTexture = NativeImage.read(pngBytes);
        this.width = backingTexture.getWidth();
        this.height = backingTexture.getHeight();
    }

    private boolean isClosed() {
        return backingTexture == null;
    }

    @Override
    public int width() {
        return width;
    }

    @Override
    public int height() {
        return height;
    }

    @Override
    public int getPixel(int x, int y) {
        return backingTexture.getPixel(x, y);
    }

    @Override
    public void setPixel(int x, int y, int color) {
        backingTexture.setPixel(x, y, color);
    }

    private void createGpuTexIfNeeded() {
        if (texture == null) {
            // Create texture on GPU
            GpuDevice gpuDevice = RenderSystem.getDevice();
            int usage = GpuTexture.USAGE_COPY_DST | GpuTexture.USAGE_COPY_SRC | GpuTexture.USAGE_TEXTURE_BINDING | GpuTexture.USAGE_RENDER_ATTACHMENT;
            this.texture = gpuDevice.createTexture(location.getPath(), usage, TextureFormat.RGBA8, width, height, 1, 1);
            gpuDevice.createCommandEncoder().clearColorTexture(this.texture, 0); // Clear to zeros
            this.textureView = gpuDevice.createTextureView(this.texture);
            // Register this to the texture manager
            Minecraft.getInstance().getTextureManager().register(location, this);
        }
    }

    @Override
    public CompletableFuture<Void> commit() {
        return RenderUtils.runOnRenderThread(() -> {
            if (!isClosed()) {
                createGpuTexIfNeeded();
                RenderSystem.getDevice().createCommandEncoder().writeToTexture(this.texture, this.backingTexture);
            }
        });
    }

    @Override
    public CompletableFuture<Void> commitRegion(int x, int y, int width, int height) {
        return RenderUtils.runOnRenderThread(() -> {
            if (!isClosed()) {
                createGpuTexIfNeeded();
                RenderSystem.getDevice().createCommandEncoder().writeToTexture(this.texture, this.backingTexture, 0, 0, x, y, width, height, x, y);
            }
        });
    }

    @Override
    public void paste(ReadableMinecraftTexture texture, int x, int y, int srcX, int srcY, int width, int height) {
        if (texture instanceof OwnedMinecraftTextureImpl ourImpl) {
            // Fast nativeimage copy
            NativeImage src = ourImpl.backingTexture;
            NativeImage dst = this.backingTexture;
            // args are (dst, srcX, srcY, dstX, dstY, width, height, flipX, flipY)
            src.copyRect(dst, srcX, srcY, x, y, width, height, false, false);
        } else {
            // Default to potentially slower(?) path (not sure, copyRect is also just a double for-loop :P)
            for (int j = 0; j < width; j++) {
                for (int i = 0; i < height; i++) {
                    int pix = texture.getPixel(srcX + i, srcY + j);
                    this.setPixel(x + i, y + j, pix);
                }
            }
        }
    }

    @Override
    public void destroy() {
        this.close();
    }

    @Override
    public void close() {
        RenderUtils.runOnRenderThread(() -> {
            if (isClosed()) return;
            this.backingTexture.close();
            this.backingTexture = null;
            super.close();
            Minecraft.getInstance().getTextureManager().release(location);
        });
    }
}
