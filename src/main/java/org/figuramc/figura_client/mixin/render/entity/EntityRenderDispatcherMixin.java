package org.figuramc.figura_client.mixin.render.entity;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.EnderDragonRenderState;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.state.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Pose;
import org.figuramc.figura_client.ducks.EntityRenderStateAccess;
import org.figuramc.figura_client.game_data.MinecraftEntityImpl;
import org.figuramc.figura_client.game_data.MinecraftWorldImpl;
import org.figuramc.figura_client.renderer.submit.FiguraCallbackSubmit;
import org.figuramc.figura_client.renderer.submit.FiguraPartSubmit;
import org.figuramc.figura_client.util.RenderUtils;
import org.figuramc.figura_client.vanilla_model.VanillaModelCache;
import org.figuramc.figura_core.avatars.components.EntityRoot;
import org.figuramc.figura_core.manage.AvatarManagers;
import org.figuramc.figura_core.manage.AvatarView;
import org.figuramc.figura_core.script_hooks.Event;
import org.figuramc.figura_core.script_hooks.callback.items.CallbackItem;
import org.figuramc.figura_core.script_hooks.callback.items.EntityView;
import org.figuramc.figura_core.script_hooks.callback.items.WorldView;
import org.figuramc.figura_core.script_hooks.timing.ProfilingCategory;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.UUID;

/**
 * - Manages the Avatar rendering stack, for vanilla parts usage
 */
@Mixin(EntityRenderDispatcher.class)
public abstract class EntityRenderDispatcherMixin {

    @Shadow public abstract <T extends Entity> EntityRenderer<? super T, ?> getRenderer(T entity);

    // Inject after extracting an entity to run callbacks extract our own data
    @ModifyReturnValue(method = "extractEntity", at = @At("RETURN"))
    public EntityRenderState fillFiguraStateFields(EntityRenderState entityRenderState, Entity entity, float delta) {
        // Fetch avatar and generate submissions
        EntityRenderStateAccess access = (EntityRenderStateAccess) entityRenderState;
        access.figura_client$reset();

        AvatarView<UUID> view = AvatarManagers.tryGetEntityAvatar(new MinecraftEntityImpl(entity));
        if (view == null) return entityRenderState;

        // We have an avatar here.
        access.figura_client$setAvatarView(view);

        view.use(avatar -> {
            try (
                    EntityView<?> entityView = new EntityView<>(new MinecraftEntityImpl(entity));
                    WorldView<MinecraftWorldImpl> worldView = new WorldView<>(new MinecraftWorldImpl((ClientLevel) entity.level()))
            ) {
                FiguraCallbackSubmit submission = RenderUtils.invokeRenderEvent(avatar, ProfilingCategory.ENTITY_RENDER_EVENT, ProfilingCategory.ENTITY_RENDER_EVENT, Event.ENTITY_RENDER, new CallbackItem.Tuple3<>(
                        new CallbackItem.F32(delta),
                        entityView,
                        worldView
                ));
                access.figura_client$setCodeSubmit(submission);
            }

            // Set up the part submission too, if needed
            EntityRoot root = avatar.getComponent(EntityRoot.TYPE);
            if (root == null) return;

            // Set up root matrix:
            Matrix4f rootMatrix = new Matrix4f();
            switch (getRenderer(entity)) {
                // Copy some logic from LivingEntityRenderer, skipping x/y inversion and 1.5 block translation
                case LivingEntityRenderer living -> {
                    LivingEntityRenderState livingEntityRenderState = (LivingEntityRenderState) entityRenderState;
                    PoseStack stack = new PoseStack();
                    if (livingEntityRenderState.hasPose(Pose.SLEEPING)) {
                        Direction direction = livingEntityRenderState.bedOrientation;
                        if (direction != null) {
                            float f = livingEntityRenderState.eyeHeight - 0.1F;
                            stack.translate(-direction.getStepX() * f, 0.0F, -direction.getStepZ() * f);
                        }
                    }
                    float g = livingEntityRenderState.scale;
                    stack.scale(g, g, g);
                    living.setupRotations(livingEntityRenderState, stack, livingEntityRenderState.bodyRot, g);
                    living.scale(livingEntityRenderState, stack);
                    rootMatrix.set(stack.last().pose());
                }
                default -> {}
            }

            // Overlay:
            int overlay = OverlayTexture.NO_OVERLAY;
            if (entityRenderState instanceof LivingEntityRenderState livingEntityRenderState && getRenderer(entity) instanceof LivingEntityRenderer livingEntityRenderer)
                overlay = LivingEntityRenderer.getOverlayCoords(livingEntityRenderState, livingEntityRenderer.getWhiteOverlayProgress(livingEntityRenderState));

            // Finally create the submission.
            access.figura_client$setPartSubmit(new FiguraPartSubmit(view, root.root, rootMatrix, entityRenderState.lightCoords, overlay));
        });
        // Return original
        return entityRenderState;
    }

    // x, y, z are the entity's position in world space relative to the camera.
    // Not relevant to the mixin, just felt like explaining it.
    @WrapMethod(method = "submit")
    public void pushPopAvatar(EntityRenderState entityRenderState, CameraRenderState cameraRenderState, double x, double y, double z, PoseStack poseStack, SubmitNodeCollector submitNodeCollector, Operation<Void> original) {
        // Push the avatar before rendering, and pop afterward.
        try {
            AvatarView<UUID> view = ((EntityRenderStateAccess) entityRenderState).figura_client$getAvatarView(); // We *want* to push null here if the view is null!
            boolean shouldFlip = entityRenderState instanceof LivingEntityRenderState || entityRenderState instanceof EnderDragonRenderState; // Flimsy
            // Push and call
            RenderUtils.AVATAR_SUBMITTING_STACK.push(view);
            RenderUtils.IS_LIVING_ENTITY_STACK.push(shouldFlip);
            original.call(entityRenderState, cameraRenderState, x, y, z, poseStack, submitNodeCollector);
        } finally {
            // Pop
            RenderUtils.AVATAR_SUBMITTING_STACK.pop();
            RenderUtils.IS_LIVING_ENTITY_STACK.pop();
        }
    }

    // Inject before we submit the entity to submit our own figura task alongside it
    @Inject(method = "submit", at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/entity/EntityRenderer;submit(Lnet/minecraft/client/renderer/entity/state/EntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/CameraRenderState;)V",
            shift = At.Shift.BEFORE
    ))
    public void submitAvatarNode(EntityRenderState entityRenderState, CameraRenderState cameraRenderState, double x, double y, double z, PoseStack poseStack, SubmitNodeCollector submitNodeCollector, CallbackInfo ci) {
        EntityRenderStateAccess access = (EntityRenderStateAccess) entityRenderState;

        // Submit custom figura rendering tasks.
        // We actually want multiple tasks here with different order priorities!!
        // We want the script callbacks to run *before* ModelPart are drawn, so setters can write to the current frame's vanilla parts.
        // We want the actual geometry to be drawn *after* ModelPart are drawn, so mimic parts can read from the current frame's vanilla parts.
        // Part-specific callbacks are the only way to both read vanilla ModelParts in script and then write to them in the same frame.
        // These are handled with separate tasks by injecting into ModelSubmit.

        // If we have a callback, submit the callback to run before everything else
        if (access.figura_client$getCodeSubmit() != null)
            submitNodeCollector.order(Integer.MIN_VALUE).submitCustomGeometry(poseStack, FiguraPartSubmit.DUMMY_RENDER_TYPE, access.figura_client$getCodeSubmit());
        // Submit the actual rendering to run after everything else
        if (access.figura_client$getPartSubmit() != null)
            submitNodeCollector.order(Integer.MAX_VALUE).submitCustomGeometry(poseStack, FiguraPartSubmit.DUMMY_RENDER_TYPE, access.figura_client$getPartSubmit());
    }

    @Inject(method = "onResourceManagerReload", at = @At("TAIL"))
    public void clearModelCache(ResourceManager resourceManager, CallbackInfo ci) {
        VanillaModelCache.clearCache();
    }

}
