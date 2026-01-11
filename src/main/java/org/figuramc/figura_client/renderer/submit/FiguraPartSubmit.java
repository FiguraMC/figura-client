package org.figuramc.figura_client.renderer.submit;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import org.figuramc.figura_client.renderer.part.FiguraClientPartRenderer;
import org.figuramc.figura_client.util.RenderUtils;
import org.figuramc.figura_core.manage.AvatarView;
import org.figuramc.figura_core.util.data_structures.FiguraTransformStack;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;

public record FiguraPartSubmit(
        AvatarView<?> avatar, // Avatar view
        @Nullable FiguraClientPartRenderer partRenderer, // The part renderer
        Matrix4f rootMatrix,
        int light,
        int overlay
) implements SubmitNodeCollector.CustomGeometryRenderer {

    // Dummy render type to ensure the Figura part submissions happen without interrupting existing render types
    // This solution kinda sucks but it's at least not too invasive with mixins
    public static final RenderType DUMMY_RENDER_TYPE = RenderTypes.entitySolid(RenderUtils.ZERO_PIXEL_LOC);

    @Override
    public void render(PoseStack.Pose pose, VertexConsumer vertexConsumer) {
        // Vertex Consumer here is useless, it's for the dummy fake render type. Don't submit anything to it.

        // Render the avatar:
        if (partRenderer != null) {
            avatar.use(avatar -> avatar.tryRenderModelPart(() -> {
                // Current drawing pose times the root matrix we were created with
                Matrix4f transform = new Matrix4f().set(pose.pose());
                transform.mul(rootMatrix);
                MultiBufferSource.BufferSource bufferSource = Minecraft.getInstance().renderBuffers().bufferSource();
                partRenderer.render(bufferSource, transform, light, overlay);
            }));
        }

    }
}
