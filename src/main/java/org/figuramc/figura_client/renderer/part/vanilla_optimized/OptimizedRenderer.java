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
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import org.figuramc.figura_client.renderer.part.FiguraClientPartRenderer;
import org.figuramc.figura_client.renderer.part.text_rendering.FiguraTextRenderer;
import org.figuramc.figura_client.util.RenderUtils;
import org.figuramc.figura_core.avatars.errors.AvatarError;
import org.figuramc.figura_core.avatars.errors.AvatarOutOfMemoryError;
import org.figuramc.figura_core.minecraft_interop.texture.MinecraftTexture;
import org.figuramc.figura_core.model.part.tasks.TextTask;
import org.figuramc.figura_core.model.rendering.PartDataStruct;
import org.figuramc.figura_core.model.rendering.RenderingRoot;
import org.figuramc.figura_core.model.rendering.vertex.FiguraVertexFormat;
import org.figuramc.figura_core.util.data_structures.FiguraTransformStack;
import org.figuramc.figura_core.util.data_structures.Pair;
import org.figuramc.figura_core.util.exception.FiguraException;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Vector2f;
import org.joml.Vector4f;
import org.joml.Vector4i;
import org.lwjgl.opengl.GL46;

import java.nio.ByteBuffer;
import java.util.*;

public class OptimizedRenderer extends FiguraClientPartRenderer {

    private static final int FIGURA_UNIFORMS_SIZE = new Std140SizeCalculator()
            .putMat4f().putMat4f() // Matrices
            .putVec4() // Overlay color
            .putVec2() // ScreenSize
            .putFloat() // GameTime
            .align(16) // Align by 16
            .align(RenderSystem.getDevice().getUniformOffsetAlignment()) // Align by impl-dependent offset alignment
            .get();

    private @Nullable State state;

    public OptimizedRenderer(RenderingRoot<?> root) {
        super(root);
    }

    private record State(
            GpuBuffer transformsBuffer,
            GpuBuffer figuraUniformsBuffer,
            List<DrawCallState> drawCallStates
    ) implements AutoCloseable {
        @Override
        public void close() {
            transformsBuffer.close();
            figuraUniformsBuffer.close();
            drawCallStates.forEach(DrawCallState::close);
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
            // Generate draw call states
            List<DrawCallState> drawCallStates = new ArrayList<>();
            // Cache VBOs
            Map<Pair<FiguraVertexFormat, Integer>, GpuBuffer> vertexBuffers = new HashMap<>();
            // Create draw call infos
            for (RenderingRoot.DrawCall drawCall : this.root.drawCalls) {
                // Render pipeline
                RenderPipeline pipeline = CustomRenderPipelines.create(drawCall.drawCallInfo().shader());
                // Vertex buffer
                FiguraVertexFormat vertexFormat = drawCall.drawCallInfo().shader().vertexFormat();
                var formatKey = new Pair<>(vertexFormat, drawCall.start());
                GpuBuffer vertexBuffer = vertexBuffers.computeIfAbsent(formatKey, k -> RenderSystem.getDevice().createBuffer(
                        () -> "Figura Vertex Buffer",
                        GpuBuffer.USAGE_VERTEX,
                        root.builtVertexData.get(vertexFormat).slice(drawCall.start(), drawCall.length())
                ));
                drawCallStates.add(new DrawCallState(drawCall, pipeline, vertexBuffer));
            }

            state = new State(transformsBuffer, figuraUniformsBuffer, drawCallStates);
        }
    }

    @Override
    public void render(MultiBufferSource bufferSource, Matrix4f transform, int light, int overlay) throws AvatarError {
        // Ensure we have valid state before moving on
        if (state == null) rebuild();
        if (state == null) return;
        // Compute transforms
        FiguraTransformStack newStack = new FiguraTransformStack();
        newStack.light(new Vector2f(LightTexture.block(light) / 15.0f, LightTexture.sky(light) / 15.0f)); // Set up initial light value
        root.extractTransforms(newStack, (renderTask, matrixStack) -> {
            // Code to handle render tasks. Just draw them as we encounter them.
            matrixStack.push();
            // Apply initial transforms from outside, since we're handing this to minecraft-y text rendering...
            // Minecraft rendering expects its inputs in world space, but the matrixStack is currently in model space.
            // TODO: How feasible is it to change this? Do we care?
            matrixStack.peekPosition().mulLocal(transform);
            switch (renderTask) {
                case TextTask textTask -> FiguraTextRenderer.render(textTask.formattedText, bufferSource, matrixStack.peekPosition(), light, overlay);
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

        for (int drawIndex = 0; drawIndex < state.drawCallStates.size(); drawIndex++) {
            DrawCallState drawCall = state.drawCallStates.get(drawIndex);

            // Figura uniforms
            GpuBufferSlice uniformsBufferSlice = state.figuraUniformsBuffer.slice((long) drawIndex * FIGURA_UNIFORMS_SIZE, FIGURA_UNIFORMS_SIZE);
            try (var figuraUniformsView = encoder.mapBuffer(uniformsBufferSlice, false, true)) {
                ByteBuffer buf = figuraUniformsView.data();
                transform.get(0, buf); // CamRelWorldMat
                RenderSystem.getModelViewMatrix().get(64, buf); // ViewMat
                // Calculate overlay color... :P
                if (overlay >> 16 < 8) {
                    new Vector4f(1f, 0f, 0f, 178f / 255f).get(128, buf);
                } else {
                    float u = (overlay & 0xFFFF) / 15.0f;
                    float alpha = 1f - u * 0.75f;
                    new Vector4f(1f, 1f, 1f, alpha).get(128, buf);
                }
                // Screen size
                new Vector2f(Minecraft.getInstance().getWindow().getWidth(), Minecraft.getInstance().getWindow().getHeight()).get(144, buf);
                // Game time
                long l = Minecraft.getInstance().level == null ? 0L : Minecraft.getInstance().level.getGameTime();
                float gameTime = ((float)(l % 24000L) + Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(false)) / 24000.0F;
                buf.putFloat(152, gameTime);
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
                int vertexCount = drawCall.base.length() / drawCall.base.drawCallInfo().shader().vertexFormat().vertexSize;
                int indexCount = VertexFormat.Mode.QUADS.indexCount(vertexCount);
                RenderSystem.AutoStorageIndexBuffer indexBuffer = RenderSystem.getSequentialBuffer(VertexFormat.Mode.QUADS);
                pass.setIndexBuffer(indexBuffer.getBuffer(indexCount), indexBuffer.type());
                // Pipeline:
                pass.setPipeline(drawCall.pipeline);
                // Uniforms
                RenderSystem.bindDefaultUniforms(pass);
                pass.setUniform("FiguraUniforms", uniformsBufferSlice);
                // Iterate textures and bind them
                for (int i = 0; i < drawCall.base.drawCallInfo().textureHandles().size(); i++) {
                    String name = drawCall.base.drawCallInfo().shader().textureBindingPoints().get(i);
                    MinecraftTexture bindingHandle = drawCall.base.drawCallInfo().textureHandles().get(i);
                    GpuTextureView gpuTextureView = RenderUtils.texToGpuTextureView(bindingHandle);
                    FilterMode filterMode = i == drawCall.base.drawCallInfo().textureHandles().size() - 1 ? FilterMode.LINEAR : FilterMode.NEAREST; // TODO: Non-hardcoded smooth lighting for the lightmap texture
                    pass.bindTexture(name, gpuTextureView, RenderSystem.getSamplerCache().getRepeat(filterMode));
                }

                // TODO: Add workaround for if SSBO isn't supported (or we're somehow not using OpenGL backend?)
                GL46.glBindBufferBase(GL46.GL_SHADER_STORAGE_BUFFER, 0, ((GlBuffer) state.transformsBuffer).handle);

                // Scissor state
                if (drawCall.base.drawCallInfo().scissors().isActive()) {
                    Vector4i scissorState = drawCall.base.drawCallInfo().scissors().get(new Vector4i());
                    pass.enableScissor(scissorState.x, scissorState.y, scissorState.z, scissorState.w);
                }

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
