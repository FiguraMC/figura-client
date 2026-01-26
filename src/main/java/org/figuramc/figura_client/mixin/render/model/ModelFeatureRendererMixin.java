package org.figuramc.figura_client.mixin.render.model;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
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

        // If at any point we fail this check, we need to disable figura involvement in all model parts,
        // so stale state from a previous render doesn't affect this one.

        AvatarView<?> avatarView = ((ModelSubmitAccess) (Object) modelSubmit).figura_client$getAvatar();
        if (avatarView == null) {
            for (ModelPart part : modelSubmit.model().allParts())
                ((ModelPartAccess) (Object) part).figura_client$setEnabled(false);
            return;
        }

        avatarView.use(avatar -> {
            // If we don't have a vanilla rendering component, ignore this
            VanillaRendering vanillaRendering = avatar.getComponent(VanillaRendering.TYPE);
            if (vanillaRendering == null) {
                for (ModelPart part : modelSubmit.model().allParts())
                    ((ModelPartAccess) (Object) part).figura_client$setEnabled(false);
                return;
            }

            // Math objects.
            // We'll invert some things if required for the model...
            Vector3f temp = new Vector3f();
            float inv = ((ModelSubmitAccess) (Object) modelSubmit).figura_client$isLivingEntity() ? -1.0f : 1.0f;

            // Fetch vanilla model parts, inject their transforms into the model
            for (ModelPart vanillaPart : modelSubmit.model().allParts()) {
                // Access helper
                ModelPartAccess access = ((ModelPartAccess) (Object) vanillaPart);

                // Get its interop key, if any
                VanillaPart interopKey = access.figura_client$getVanillaPart();
                if (interopKey == null) { access.figura_client$setEnabled(false); continue; }
                // Using the interop key, apply script part changes
                VanillaRendering.ScriptVanillaPart scriptPart = vanillaRendering.vanillaPartToScriptPart.get(interopKey);
                if (scriptPart == null) { access.figura_client$setEnabled(false); continue; }

                // If we have a script part, Figura is enabled for this model part! Yay!
                access.figura_client$setEnabled(true);

                // We must save stored values.
                // Stored vanilla values are used as *offsets* for mimic parts.
                // Therefore, they should be in figura's coordinate space, and *relative* to default position.
                // They should also be affected by figura's modifications, so animations can move them.

                // Set visibility
                access.figura_client$setVisible(!vanillaRendering.hideAllModelParts && scriptPart.figuraTransform.getVisible());

                // Process transform
                PartPose initialPose = vanillaPart.getInitialPose();

                // Origin
                scriptPart.storedOrigin.set( // Set stored value (not including offsets)
                        (vanillaPart.x - initialPose.x()) * inv,
                        (vanillaPart.y - initialPose.y()) * inv,
                        vanillaPart.z - initialPose.z()
                );
                temp.set(scriptPart.figuraTransform.totalOrigin()); // Compute offset
                scriptPart.fullStoredOrigin.set(scriptPart.storedOrigin).add(temp); // Set the full value to stored value + offsets
                // Apply to vanilla, taking into account whether it's canceled
                if (scriptPart.cancelVanillaOrigin) access.figura_client$getOrigin().set(initialPose.x(), initialPose.y(), initialPose.z());
                else access.figura_client$getOrigin().set(vanillaPart.x, vanillaPart.y, vanillaPart.z);
                access.figura_client$getOrigin().add(temp.x * inv, temp.y * inv, temp.z);

                if (!scriptPart.cancelVanillaOrigin) ;

                // Rotation
                scriptPart.storedRotation.set((vanillaPart.xRot - initialPose.xRot()) * inv, (vanillaPart.yRot - initialPose.yRot()) * inv, vanillaPart.zRot - initialPose.zRot());
                temp.set(scriptPart.figuraTransform.totalEulerRad());
                scriptPart.fullStoredRotation.set(scriptPart.storedRotation).add(temp);
                if (scriptPart.cancelVanillaRotation) access.figura_client$getRotation().set(initialPose.xRot(), initialPose.yRot(), initialPose.zRot());
                else access.figura_client$getRotation().set(vanillaPart.xRot, vanillaPart.yRot, vanillaPart.zRot);
                access.figura_client$getRotation().add(temp.x * inv, temp.y * inv, temp.z);

                // Scale
                scriptPart.storedScale.set(vanillaPart.xScale / initialPose.xScale(), vanillaPart.yScale / initialPose.yScale(), vanillaPart.zScale / initialPose.zScale());
                temp.set(scriptPart.figuraTransform.totalScale());
                scriptPart.fullStoredScale.set(scriptPart.storedScale).mul(temp);
                if (scriptPart.cancelVanillaScale) access.figura_client$getScale().set(initialPose.xScale(), initialPose.yScale(), initialPose.zScale());
                else access.figura_client$getScale().set(vanillaPart.xScale, vanillaPart.yScale, vanillaPart.zScale);
                access.figura_client$getScale().mul(temp);

                // Position (not present in minecraft, so just use zeros for Minecraft's values)
                scriptPart.storedPosition.set(0);
                temp.set(scriptPart.figuraTransform.totalPosition());
                scriptPart.fullStoredPosition.set(scriptPart.storedPosition).add(temp);
                access.figura_client$getPosition().set(temp.x * inv, temp.y * inv, temp.z);
            }
        });

    }

}
