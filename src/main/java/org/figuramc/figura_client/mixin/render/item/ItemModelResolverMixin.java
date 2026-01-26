package org.figuramc.figura_client.mixin.render.item;

import net.minecraft.client.renderer.block.model.ItemTransform;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ItemOwner;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.figuramc.figura_client.FiguraClient;
import org.figuramc.figura_client.ducks.ItemStackRenderStateAccess;
import org.figuramc.figura_client.game_data.MinecraftEntityImpl;
import org.figuramc.figura_client.game_data.MinecraftItemStackImpl;
import org.figuramc.figura_client.renderer.submit.FiguraPartSubmit;
import org.figuramc.figura_core.avatars.components.CustomItems;
import org.figuramc.figura_core.manage.AvatarManagers;
import org.figuramc.figura_core.manage.AvatarView;
import org.figuramc.figura_core.minecraft_interop.ItemRenderContext;
import org.figuramc.figura_core.model.part.parts.CustomItemModelPart;
import org.figuramc.figura_core.model.part.parts.FiguraModelPart;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.UUID;

/**
 * When we update the item stack render state, run our own callbacks and extract our own fields.
 */
@Mixin(ItemModelResolver.class)
public class ItemModelResolverMixin {

    @Inject(method = "updateForTopItem", at = @At("RETURN"))
    public void onExtractItemStack(ItemStackRenderState itemStackRenderState, ItemStack itemStack, ItemDisplayContext itemDisplayContext, Level level, ItemOwner itemOwner, int i, CallbackInfo ci) {
        ItemStackRenderStateAccess access = ((ItemStackRenderStateAccess) itemStackRenderState);
        access.figura_client$clear();

        // Make checks, see if there's an avatar
        if (!(itemOwner instanceof Entity entity)) return;
        AvatarView<UUID> view = AvatarManagers.tryGetEntityAvatar(new MinecraftEntityImpl(entity));
        if (view == null) return;

        view.use(avatar -> {
            // If we have an avatar, fill in some render state.
            // TODO: an item-render event should be called here (returning callbacks, etc. like for entity rendering,
            //       or returning a model part which should be rendered similar to current Figura)

            CustomItems customItems = avatar.getComponent(CustomItems.TYPE);
            if (customItems == null) return;

            ItemRenderContext renderContext = FiguraClient.RENDER_CONTEXTS.get(itemDisplayContext);
            FiguraModelPart modelPart = customItems.getModelPart(new MinecraftItemStackImpl(itemStack), renderContext);
            if (modelPart == null) return;

            // Figure out the transforms, store in root matrix
            Matrix4f rootMatrix = new Matrix4f();
            if (modelPart instanceof CustomItemModelPart customModel && customModel.itemTransforms.get(renderContext) instanceof Matrix4f customTransform) {
                // We have a custom transform; apply it
                rootMatrix.mul(customTransform);
            } else {
                // Either we have no custom transform, or this is a generic PNG model, so just use the basic transform instead
                ItemTransform vanillaTransform = itemStackRenderState.layers[0].transform;
                applyVanillaTransform(rootMatrix, vanillaTransform, itemDisplayContext.leftHand());
            }

            // Light and overlay are set at submission time apparently?
            // This might change, but we'll edit this later on actual submission :P
            access.figura_client$setPartSubmit(new FiguraPartSubmit(view, modelPart, rootMatrix, -1, -1));
        });
    }

    // Apply vanilla transform to the matrix
    @Unique
    private static void applyVanillaTransform(Matrix4f matrix, ItemTransform vanillaTransform, boolean leftHanded) {
        if (vanillaTransform != ItemTransform.NO_TRANSFORM) {
            float inv = leftHanded ? -1f : 1f;
            matrix.translate(
                    vanillaTransform.translation().x() * inv,
                    vanillaTransform.translation().y(),
                    vanillaTransform.translation().z()
            );
            matrix.rotate(new Quaternionf().rotationXYZ(
                    vanillaTransform.rotation().x() * Mth.DEG_TO_RAD,
                    vanillaTransform.rotation().y() * Mth.DEG_TO_RAD * inv,
                    vanillaTransform.rotation().z() * Mth.DEG_TO_RAD * inv
            ));
            matrix.scale(vanillaTransform.scale());
        }
        matrix.translate(-0.5f, -0.5f, -0.5f);
    }

}
