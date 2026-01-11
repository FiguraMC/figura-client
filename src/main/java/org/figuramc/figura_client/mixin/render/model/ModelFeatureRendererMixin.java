package org.figuramc.figura_client.mixin.render.model;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.OutlineBufferSource;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.SubmitNodeStorage;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import org.figuramc.figura_client.ducks.ModelPartAccess;
import org.figuramc.figura_client.ducks.ModelSubmitAccess;
import org.figuramc.figura_core.avatars.components.VanillaRendering;
import org.figuramc.figura_core.manage.AvatarView;
import org.figuramc.figura_core.minecraft_interop.vanilla_parts.VanillaPart;
import org.joml.Vector3f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ModelFeatureRenderer.class)
public class ModelFeatureRendererMixin {

    // Inject after Minecraft sets up the model.
    // Figura will be able to read/write from/to it now.
    @Inject(method = "renderModel", at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/model/Model;setupAnim(Ljava/lang/Object;)V",
            shift = At.Shift.AFTER
    ))
    public <S> void afterSetupModel(SubmitNodeStorage.ModelSubmit<S> modelSubmit, RenderType renderType, VertexConsumer vertexConsumer, OutlineBufferSource outlineBufferSource, MultiBufferSource.BufferSource bufferSource, CallbackInfo ci) {

        AvatarView<?> avatarView = ((ModelSubmitAccess) (Object) modelSubmit).figura_client$getAvatar();
        if (avatarView == null) return;

        avatarView.use(avatar -> {
            // If we don't have a vanilla rendering component, ignore this
            VanillaRendering vanillaRendering = avatar.getComponent(VanillaRendering.TYPE);
            if (vanillaRendering == null) return;

            // Math objects.
            // We'll invert some things if required for the model...
            Vector3f temp = new Vector3f();
            float inv = ((ModelSubmitAccess) (Object) modelSubmit).figura_client$isLivingEntity() ? -1.0f : 1.0f;

            // Fetch vanilla model parts, inject their transforms into the model
            for (ModelPart vanillaPart : modelSubmit.model().allParts()) {
                // Get its interop key, if any
                VanillaPart interopKey = ((ModelPartAccess) (Object) vanillaPart).figura_client$getVanillaPart();
                if (interopKey == null) continue;
                // Using the interop key, apply script part changes!
                VanillaRendering.ScriptVanillaPart scriptPart = vanillaRendering.vanillaPartToScriptPart.get(interopKey);
                if (scriptPart == null) continue;

                // We must save stored values.
                // Stored vanilla values are used as *offsets* for mimic parts.
                // Therefore, they should be in figura's coordinate space, and *relative* to default position.
                // They should also be affected by figura's modifications, so animations can move them.

                // Visibility
                if (vanillaRendering.hideAllModelParts || !scriptPart.figuraTransform.getVisible())
                    vanillaPart.visible = false;

                // Origin
                temp.set(scriptPart.figuraTransform.totalOrigin());
                scriptPart.storedVanillaOrigin.set(temp);
                if (scriptPart.cancelVanillaOrigin) {
                    vanillaPart.setPos(vanillaPart.getInitialPose().x(), vanillaPart.getInitialPose().y(), vanillaPart.getInitialPose().z());
                } else {
                    scriptPart.storedVanillaOrigin.x += (vanillaPart.x - vanillaPart.getInitialPose().x()) * inv;
                    scriptPart.storedVanillaOrigin.y += (vanillaPart.y - vanillaPart.getInitialPose().y()) * inv;
                    scriptPart.storedVanillaOrigin.z += (vanillaPart.z - vanillaPart.getInitialPose().z());
                }
                vanillaPart.x += temp.x * inv;
                vanillaPart.y += temp.y * inv;
                vanillaPart.z += temp.z;

                // Rotation
                temp.set(scriptPart.figuraTransform.totalEulerRad());
                scriptPart.storedVanillaRotation.set(temp);
                if (scriptPart.cancelVanillaRotation) {
                    vanillaPart.setRotation(vanillaPart.getInitialPose().xRot(), vanillaPart.getInitialPose().yRot(), vanillaPart.getInitialPose().zRot());
                } else {
                    scriptPart.storedVanillaRotation.x += (vanillaPart.xRot - vanillaPart.getInitialPose().xRot()) * inv;
                    scriptPart.storedVanillaRotation.y += (vanillaPart.yRot - vanillaPart.getInitialPose().yRot()) * inv;
                    scriptPart.storedVanillaRotation.z += (vanillaPart.zRot - vanillaPart.getInitialPose().zRot());
                }
                vanillaPart.xRot += temp.x * inv;
                vanillaPart.yRot += temp.y * inv;
                vanillaPart.zRot += temp.z;

                // Scale
                temp.set(scriptPart.figuraTransform.totalScale());
                scriptPart.storedVanillaScale.set(temp);
                if (scriptPart.cancelVanillaScale) {
                    vanillaPart.xScale = vanillaPart.getInitialPose().xScale();
                    vanillaPart.yScale = vanillaPart.getInitialPose().yScale();
                    vanillaPart.zScale = vanillaPart.getInitialPose().zScale();
                } else {
                    scriptPart.storedVanillaScale.x *= vanillaPart.xScale / vanillaPart.getInitialPose().xScale();
                    scriptPart.storedVanillaScale.y *= vanillaPart.yScale / vanillaPart.getInitialPose().yScale();
                    scriptPart.storedVanillaScale.z *= vanillaPart.zScale / vanillaPart.getInitialPose().zScale();
                }
                vanillaPart.xScale *= temp.x;
                vanillaPart.yScale *= temp.y;
                vanillaPart.zScale *= temp.z;

                // Position (doesn't have a stored value counterpart, so just set it)
                ((ModelPartAccess) (Object) vanillaPart).figura_client$getPosition()
                        .set(scriptPart.figuraTransform.totalPosition())
                        .mul(inv, inv, 1);
            }
        });

    }

}
