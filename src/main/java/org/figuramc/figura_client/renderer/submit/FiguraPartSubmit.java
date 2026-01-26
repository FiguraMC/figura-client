package org.figuramc.figura_client.renderer.submit;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import org.figuramc.figura_client.renderer.part.ClientRendererState;
import org.figuramc.figura_client.util.RenderUtils;
import org.figuramc.figura_core.manage.AvatarView;
import org.figuramc.figura_core.model.part.parts.FiguraModelPart;
import org.figuramc.figura_core.util.data_structures.FiguraTransformStack;
import org.jetbrains.annotations.NotNull;
import org.joml.Matrix4f;
import org.joml.Vector2f;

public record FiguraPartSubmit(
        AvatarView<?> avatar, // Avatar view
        @NotNull FiguraModelPart modelPart, // The part to render
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
        avatar.use(avatar -> avatar.tryRenderModelPart(() -> {
            // Apply light to initial transform stack
            FiguraTransformStack transformStack = new FiguraTransformStack();
            transformStack.light(new Vector2f(LightTexture.block(light) / 15.0f, LightTexture.sky(light) / 15.0f));
            // The pose shouldn't be given in the transform stack; the transform stack's initial state should be purely identity for this call
            Matrix4f rootAndViewMatrix = pose.pose().mul(rootMatrix, new Matrix4f());
            // Draw!
            modelPart.render(transformStack, new ClientRendererState(rootAndViewMatrix, overlay));
        }));

    }
}
