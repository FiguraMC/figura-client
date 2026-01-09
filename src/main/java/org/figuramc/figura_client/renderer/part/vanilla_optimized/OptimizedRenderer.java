package org.figuramc.figura_client.renderer.part.vanilla_optimized;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.buffers.Std140SizeCalculator;
import com.mojang.blaze3d.opengl.GlBuffer;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import org.figuramc.figura_client.FiguraClient;
import org.figuramc.figura_client.renderer.part.FiguraClientPartRenderer;
import org.figuramc.figura_client.renderer.part.text_rendering.FiguraTextRenderer;
import org.figuramc.figura_client.util.RenderUtils;
import org.figuramc.figura_core.avatars.errors.AvatarError;
import org.figuramc.figura_core.avatars.errors.AvatarOutOfMemoryError;
import org.figuramc.figura_core.data.materials.ModuleMaterials;
import org.figuramc.figura_core.minecraft_interop.FiguraConnectionPoint;
import org.figuramc.figura_core.minecraft_interop.texture.MinecraftTexture;
import org.figuramc.figura_core.model.part.tasks.TextTask;
import org.figuramc.figura_core.model.rendering.FiguraRenderType;
import org.figuramc.figura_core.model.rendering.PartDataStruct;
import org.figuramc.figura_core.model.rendering.RenderingRoot;
import org.figuramc.figura_core.model.rendering.vertex.FiguraVertexFormat;
import org.figuramc.figura_core.util.ListUtils;
import org.figuramc.figura_core.util.data_structures.FiguraTransformStack;
import org.figuramc.figura_core.util.data_structures.Pair;
import org.figuramc.figura_core.util.exception.FiguraException;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector2f;
import org.joml.Vector4f;
import org.lwjgl.opengl.GL46;

import java.nio.ByteBuffer;
import java.util.*;

public class OptimizedRenderer extends FiguraClientPartRenderer {

    private static final int FIGURA_UNIFORMS_SIZE = new Std140SizeCalculator()
            .putMat4f().putMat4f() // Matrices
            .putVec4() // Overlay color
            .putVec4().putVec4().putVec4().putVec4() // UV modifiers
            .putVec2() // ScreenSize
            .putFloat() // GameTime
            .align(16) // Align by 16 since there's vec4s in here
            .align(RenderSystem.getDevice().getUniformOffsetAlignment()) // Align by impl-dependent offset alignment
            .get();

    private @Nullable State state;

    public OptimizedRenderer(RenderingRoot<?> root) {
        super(root);
    }

    private record State(
            GpuBuffer transformsBuffer,
            GpuBuffer figuraUniformsBuffer,
            List<DrawCallState> drawCallInfos
    ) implements AutoCloseable {
        @Override
        public void close() {
            transformsBuffer.close();
            figuraUniformsBuffer.close();
            drawCallInfos.forEach(DrawCallState::close);
        }
    }
    private record DrawCallState(
            RenderingRoot.DrawCall base,
            RenderPipeline pipeline,
            GpuBuffer vertexBuffer
    ) implements AutoCloseable {
        @Override
        public void close() {
            vertexBuffer.close();
        }
    }

    // Re-create the state if it was lost
    private void rebuild() throws AvatarError {
        assert state == null;
        try {
            root.rebuildVertices();
        } catch (AvatarOutOfMemoryError avatarOOM) {
            throw new AvatarError(FiguraException.INTERNAL_ERROR, "TODO: OOM Errors");
        }
        if (!root.builtVertexData.isEmpty()) {
            // Set up shared buffers
            GpuBuffer transformsBuffer = RenderSystem.getDevice().createBuffer(
                    () -> "Figura Transforms Buffer", GpuBuffer.USAGE_MAP_WRITE, (long) root.transformCount * PartDataStruct.GPU_SIZE);
            GpuBuffer figuraUniformsBuffer = RenderSystem.getDevice().createBuffer(
                    () -> "Figura Uniforms Buffer", GpuBuffer.USAGE_MAP_WRITE, (long) this.root.drawCalls.size() * FIGURA_UNIFORMS_SIZE); // Separate buffer range for each draw call
            // Generate draw call infos
            List<DrawCallState> drawCallInfos = new ArrayList<>();
            // Cache VBOs
            Map<Pair<FiguraVertexFormat, Integer>, GpuBuffer> vertexBuffers = new HashMap<>();
            // Create draw call infos
            for (RenderingRoot.DrawCall drawCall : this.root.drawCalls) {
                // Render pipeline
                RenderPipeline pipeline = CustomRenderPipelines.create(drawCall.renderType().shader());
                // Vertex buffer
                FiguraVertexFormat vertexFormat = drawCall.renderType().shader().vertexFormat();
                var formatKey = new Pair<>(vertexFormat, drawCall.start());
                GpuBuffer vertexBuffer = vertexBuffers.computeIfAbsent(formatKey, k -> RenderSystem.getDevice().createBuffer(
                                () -> "Figura Vertex Buffer", GpuBuffer.USAGE_VERTEX, root.builtVertexData.get(vertexFormat).slice(drawCall.start(), drawCall.length())));
                drawCallInfos.add(new DrawCallState(drawCall, pipeline, vertexBuffer));
            }

            state = new State(transformsBuffer, figuraUniformsBuffer, drawCallInfos);
        }
    }

    @Override
    public void render(MultiBufferSource bufferSource, FiguraTransformStack transformStack, int light, int overlay) throws AvatarError {
        // Ensure we have valid state before moving on
        if (state == null) rebuild();
        if (state == null) return;
        // Compute transforms
        FiguraTransformStack newStack = new FiguraTransformStack();
        newStack.light(transformStack.peekLight());
        newStack.color(transformStack.peekColor());
        root.extractTransforms(newStack, (renderTask, matrixStack) -> {
            // Code to handle render tasks. Just draw them as we encounter them.
            matrixStack.push();
            matrixStack.preMultiply(transformStack.peekPosition(), transformStack.peekNormal()); // Apply initial transforms from outside!
            switch (renderTask) {
                case TextTask textTask -> FiguraTextRenderer.render(textTask.formattedText, bufferSource, matrixStack, light, overlay);
            }
            matrixStack.pop();
        });
        // Map the transforms buffer and put data inside
        CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
        try (var transformsView = encoder.mapBuffer(state.transformsBuffer, false, true)) {
            ByteBuffer buf = transformsView.data();
            for (int i = 0; i < root.transformCount; i++)
                root.transforms[i].write(buf, i * PartDataStruct.GPU_SIZE);
        }

        // Loop over draw calls

        for (int drawIndex = 0; drawIndex < state.drawCallInfos.size(); drawIndex++) {
            DrawCallState drawCall = state.drawCallInfos.get(drawIndex);

            // Figura uniforms
            GpuBufferSlice uniformsBufferSlice = state.figuraUniformsBuffer.slice((long) drawIndex * FIGURA_UNIFORMS_SIZE, FIGURA_UNIFORMS_SIZE);
            try (var figuraUniformsView = encoder.mapBuffer(uniformsBufferSlice, false, true)) {
                ByteBuffer buf = figuraUniformsView.data();
                transformStack.peekPosition().get(0, buf); // CamRelWorldMat
                RenderSystem.getModelViewMatrix().get(64, buf); // ViewMat
                // Calculate overlay color... :P
                if (overlay >> 16 < 8) {
                    new Vector4f(1f, 0f, 0f, 178f / 255f).get(128, buf);
                } else {
                    float u = (overlay & 0xFFFF) / 15.0f;
                    float alpha = 1f - u * 0.75f;
                    new Vector4f(1f, 1f, 1f, alpha).get(128, buf);
                }
                // Apply UV modifiers (There's exactly 4 of them... TODO is this too hardcoded? Should we increase the number of modifiers or try to make it dynamic somehow?)
                for (int i = 0; i < 4; i++) {
                    drawCall.base.renderType().textureBindings().get(i).uvModifier().get(144 + i * 16, buf);
                }
                // Screen size
                new Vector2f(Minecraft.getInstance().getWindow().getWidth(), Minecraft.getInstance().getWindow().getHeight()).get(208, buf);
                // Game time
                long l = Minecraft.getInstance().level == null ? 0L : Minecraft.getInstance().level.getGameTime();
                float gameTime = ((float)(l % 24000L) + Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(false)) / 24000.0F;
                buf.putFloat(216, gameTime);
            }

            // Run the render pass
            try (RenderPass pass = encoder.createRenderPass(
                    () -> "Figura Render Pass",
                    Minecraft.getInstance().getMainRenderTarget().getColorTextureView(),
                    OptionalInt.empty(), // Don't clear color texture
                    Minecraft.getInstance().getMainRenderTarget().getDepthTextureView(),
                    OptionalDouble.empty() // Don't clear depth texture
            )) {
                // Vertex buffer:
                pass.setVertexBuffer(0, drawCall.vertexBuffer);
                // Index buffer:
                int vertexCount = drawCall.base.length() / drawCall.base.renderType().shader().vertexFormat().vertexSize;
                int indexCount = VertexFormat.Mode.QUADS.indexCount(vertexCount);
                RenderSystem.AutoStorageIndexBuffer indexBuffer = RenderSystem.getSequentialBuffer(VertexFormat.Mode.QUADS);
                pass.setIndexBuffer(indexBuffer.getBuffer(indexCount), indexBuffer.type());
                // Pipeline:
                pass.setPipeline(drawCall.pipeline);
                // Uniforms
                RenderSystem.bindDefaultUniforms(pass);
                pass.setUniform("FiguraUniforms", uniformsBufferSlice);
                // Iterate textures and bind them
                for (int i = 0; i < drawCall.base.renderType().textureBindings().size(); i++) {
                    String name = drawCall.base.renderType().shader().textureBindingPoints().get(i);
                    FiguraRenderType.TextureBinding binding = drawCall.base.renderType().textureBindings().get(i);
                    GpuTextureView gpuTextureView = RenderUtils.texToGpuTextureView(binding.textureHandle());
                    FilterMode filterMode = i == 3 ? FilterMode.LINEAR : FilterMode.NEAREST; // TODO: Non-hardcoded smooth lighting for the lightmap texture!
                    pass.bindTexture(name, gpuTextureView, RenderSystem.getSamplerCache().getRepeat(filterMode));
                }

                // TODO: Add workaround for if SSBO isn't supported (or we're somehow not using OpenGL backend?)
                GL46.glBindBufferBase(GL46.GL_SHADER_STORAGE_BUFFER, 0, ((GlBuffer) state.transformsBuffer).handle);

                // Draw! (Base vertex, Base index, Index Count, Instance Count)
                pass.drawIndexed(0, 0, indexCount, 1);
            }
        }
    }

    @Override
    public void invalidate() {
        if (this.state != null) {
            this.state.close();
            this.state = null;
        }
    }

    @Override
    public void destroy() {
        invalidate();
    }
}
