package org.figuramc.figura_client.mixin.render.model;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.geom.ModelPart;
import org.figuramc.figura_client.ducks.ModelPartAccess;
import org.figuramc.figura_core.minecraft_interop.vanilla_parts.VanillaPart;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ModelPart.class)
public class ModelPartMixin implements ModelPartAccess {

    // Corresponding VanillaPart
    @Unique public @Nullable VanillaPart vanillaPart;

    // Add new variables for figura's modified values
    // It's in Minecraft's coordinate space, meaning x/y values are negated if it's for a LivingEntity
    @Unique public boolean figuraEnabled; // Whether Figura's changes are enabled on this part
    @Unique public boolean figuraVisible; // Whether Figura says this part should be visible
    @Unique public final Vector3f figuraOrigin = new Vector3f();
    @Unique public final Vector3f figuraRotation = new Vector3f();
    @Unique public final Vector3f figuraScale = new Vector3f();
    @Unique public final Vector3f figuraPosition = new Vector3f();

    // Get/set
    @Override public @Nullable VanillaPart figura_client$getVanillaPart() { return vanillaPart; }
    @Override public void figura_client$setVanillaPart(@Nullable VanillaPart part) { this.vanillaPart = part; }
    @Override public void figura_client$setEnabled(boolean figuraEnabled) { this.figuraEnabled = figuraEnabled; }
    @Override public void figura_client$setVisible(boolean figuraVisible) { this.figuraVisible = figuraVisible; }

    // Mutable vector getters
    @Override public Vector3f figura_client$getOrigin() { return figuraOrigin; }
    @Override public Vector3f figura_client$getRotation() { return figuraRotation; }
    @Override public Vector3f figura_client$getScale() { return figuraScale; }
    @Override public Vector3f figura_client$getPosition() { return figuraPosition; }

    // If Figura is enabled, add an extra visibility requirement to render()
    @Inject(method = "render(Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;III)V", at = @At("HEAD"), cancellable = true)
    public void maybeMakeInvisible(PoseStack poseStack, VertexConsumer vertexConsumer, int i, int j, int k, CallbackInfo ci) {
        // Cancel rendering if figura is enabled for this part, and figura says the part should not be visible
        if (figuraEnabled && !figuraVisible) ci.cancel();
    }

    // If Figura is enabled, do our custom transform; otherwise, do the original operation.
    @WrapMethod(method = "translateAndRotate")
    public void overrideTransforms(PoseStack poseStack, Operation<Void> original) {
        if (figuraEnabled) {
            poseStack.translate(figuraOrigin.x / 16.0F, figuraOrigin.y / 16.0F, figuraOrigin.z / 16.0F);
            if (figuraRotation.x != 0.0F || figuraRotation.y != 0.0F || figuraRotation.z != 0.0F)
                poseStack.mulPose(new Quaternionf().rotationZYX(figuraRotation.z, figuraRotation.y, figuraRotation.x));
            if (figuraScale.x != 1.0F || figuraScale.y != 1.0F || figuraScale.z != 1.0F)
                poseStack.scale(figuraScale.x, figuraScale.y, figuraScale.z);
            poseStack.translate(figuraPosition.x / 16.0F, figuraPosition.y / 16.0F, figuraPosition.z / 16.0F);
        } else {
            original.call(poseStack);
        }
    }

}
