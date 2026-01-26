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
import org.figuramc.figura_client.renderer.part.ClientRendererState;
import org.figuramc.figura_client.util.RenderUtils;
import org.figuramc.figura_core.avatars.errors.AvatarOutOfMemoryError;
import org.figuramc.figura_core.minecraft_interop.render.ClientPartRenderer;
import org.figuramc.figura_core.minecraft_interop.texture.MinecraftTexture;
import org.figuramc.figura_core.model.rendering.PartDataStruct;
import org.figuramc.figura_core.model.rendering.RenderData;
import org.figuramc.figura_core.model.rendering.vertex.FiguraVertexFormat;
import org.figuramc.figura_core.util.data_structures.FiguraTransformStack;
import org.figuramc.figura_core.util.data_structures.Pair;
import org.figuramc.memory_tracker.AllocationTracker;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Vector2f;
import org.joml.Vector4f;
import org.joml.Vector4i;
import org.lwjgl.opengl.GL46;

import java.nio.ByteBuffer;
import java.util.*;
import java.util.concurrent.CompletableFuture;

public class OptimizedRenderer implements ClientPartRenderer {

    private static final int FIGURA_UNIFORMS_SIZE = new Std140SizeCalculator()
            .putMat4f().putMat4f() // Matrices
            .putVec4() // Overlay color
            .putVec2() // ScreenSize
            .putFloat() // GameTime
            .align(16) // Align by 16
            .align(RenderSystem.getDevice().getUniformOffsetAlignment()) // Align by impl-dependent offset alignment
            .get();

    // RenderData known
    private final RenderData renderData;

    // Graphics items, created on render thread, so they're in a CompletableFuture
    private final CompletableFuture<GraphicsItems> graphicsItems;
    private record GraphicsItems(
            GpuBuffer transformsBuffer,
            GpuBuffer figuraUniformsBuffer, // One uniform buffer, with many subbuffers, one for each draw call
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
            RenderData.DrawCall base,
            RenderPipeline pipeline,
            GpuBuffer vertexBuffer
    ) implements AutoCloseable {
        @Override
        public void close() {
            vertexBuffer.close();
        }
    }

    // Constructor just creates the graphics state
    // TODO: Track memory. Make sure the allocation error isn't thrown on the render thread, this will be hard to recover from.
    public OptimizedRenderer(RenderData renderData, @Nullable AllocationTracker<AvatarOutOfMemoryError> allocationTracker) {
        this.renderData = renderData;
        // Make graphics items on render thread
        graphicsItems = RenderUtils.makeOnRenderThread(() -> {
            // Set up shared buffers
            GpuBuffer transformsBuffer = RenderSystem.getDevice().createBuffer(() -> "Figura Transforms Buffer", GpuBuffer.USAGE_MAP_WRITE, (long) renderData.partData.length * PartDataStruct.GPU_SIZE);
            GpuBuffer figuraUniformsBuffer = RenderSystem.getDevice().createBuffer(() -> "Figura Uniforms Buffer", GpuBuffer.USAGE_MAP_WRITE, (long) renderData.drawCalls.size() * FIGURA_UNIFORMS_SIZE); // Separate buffer range for each draw call
            // Generate draw call states
            List<DrawCallState> drawCallStates = new ArrayList<>(renderData.drawCalls.size());
            // Cache VBOs
            Map<Pair<FiguraVertexFormat, Integer>, GpuBuffer> vertexBuffers = new HashMap<>();
            // Create draw call infos
            for (RenderData.DrawCall drawCall : renderData.drawCalls) {
                // Render pipeline
                RenderPipeline pipeline = CustomRenderPipelines.create(drawCall.drawCallInfo().shader());
                // Vertex buffer
                FiguraVertexFormat vertexFormat = drawCall.drawCallInfo().shader().vertexFormat();
                var formatKey = new Pair<>(vertexFormat, drawCall.start());
                GpuBuffer vertexBuffer = vertexBuffers.computeIfAbsent(formatKey, k -> RenderSystem.getDevice().createBuffer(
                        () -> "Figura Vertex Buffer",
                        GpuBuffer.USAGE_VERTEX,
                        renderData.builtData.get(vertexFormat).slice(drawCall.start(), drawCall.length())
                ));
                drawCallStates.add(new DrawCallState(drawCall, pipeline, vertexBuffer));
            }
            // Return
            return new GraphicsItems(transformsBuffer, figuraUniformsBuffer, drawCallStates);
        });
    }



    // Draw a given RenderData with the given transform and other context
    @Override
    public void draw(FiguraTransformStack transform, Object unknownState) {
        // Ensure state is the appropriate type
        if (!(unknownState instanceof ClientRendererState(Matrix4f rootAndViewMatrix, int overlay))) throw new IllegalArgumentException("State object expected to be ClientRendererState");
        // Fetch graphics items, if they're somehow not ready yet then let's just skip
        if (!(graphicsItems.getNow(null) instanceof GraphicsItems(GpuBuffer transformsBuffer, GpuBuffer figuraUniformsBuffer, List<DrawCallState> drawCallStates))) return;

        // Map the transforms buffer and put data inside
        CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
        try (var transformsView = encoder.mapBuffer(transformsBuffer, false, true)) {
            ByteBuffer buf = transformsView.data();
            for (int i = 0; i < renderData.partData.length; i++)
                renderData.partData[i].write(buf, i * PartDataStruct.GPU_SIZE);
        }

        // Loop over draw calls
        for (int drawIndex = 0; drawIndex < drawCallStates.size(); drawIndex++) {
            DrawCallState drawCall = drawCallStates.get(drawIndex);

            // Set up Figura uniforms
            GpuBufferSlice uniformsBufferSlice = figuraUniformsBuffer.slice((long) drawIndex * FIGURA_UNIFORMS_SIZE, FIGURA_UNIFORMS_SIZE);
            try (var figuraUniformsView = encoder.mapBuffer(uniformsBufferSlice, false, true)) {
                ByteBuffer buf = figuraUniformsView.data();
                rootAndViewMatrix.get(0, buf); // CamRelWorldMat
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

                // TODO: Add a workaround for if SSBO isn't supported (or we're somehow not using OpenGL backend?)
                GL46.glBindBufferBase(GL46.GL_SHADER_STORAGE_BUFFER, 0, ((GlBuffer) transformsBuffer).handle);

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
    public void close() {
        // Close the items when done.
        graphicsItems.thenAccept(GraphicsItems::close);
    }
}
