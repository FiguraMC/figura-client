package org.figuramc.figura_client.mixin.render.item;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import org.figuramc.figura_client.ducks.ItemStackRenderStateAccess;
import org.figuramc.figura_client.renderer.submit.FiguraPartSubmit;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// Add a field containing the ItemStack itself, so we can access it
@Mixin(ItemStackRenderState.class)
public class ItemStackRenderStateMixin implements ItemStackRenderStateAccess {

    @Unique public FiguraPartSubmit partSubmit;
    @Override public @Nullable FiguraPartSubmit figura_client$getPartSubmit() { return partSubmit; }
    @Override public void figura_client$setPartSubmit(@Nullable FiguraPartSubmit submit) { this.partSubmit = submit; }

    // If we have a figura rendering task for this item, submit that instead
    @Inject(method = "submit", at = @At("HEAD"), cancellable = true)
    public void onSubmit(PoseStack poseStack, SubmitNodeCollector submitNodeCollector, int light, int overlay, int k, CallbackInfo ci) {
        if (partSubmit != null) {
            // Fill in light/overlay values before submitting
            partSubmit = new FiguraPartSubmit(partSubmit.avatar(), partSubmit.modelPart(), partSubmit.rootMatrix(), light, overlay);
            submitNodeCollector.submitCustomGeometry(poseStack, FiguraPartSubmit.DUMMY_RENDER_TYPE, partSubmit);
            ci.cancel();
        }
    }

}
