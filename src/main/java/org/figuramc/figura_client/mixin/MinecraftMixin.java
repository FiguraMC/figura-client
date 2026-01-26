package org.figuramc.figura_client.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import org.figuramc.figura_client.game_data.MinecraftWorldImpl;
import org.figuramc.figura_core.avatars.components.AvatarEvents;
import org.figuramc.figura_core.manage.AvatarManagers;
import org.figuramc.figura_core.script_hooks.Event;
import org.figuramc.figura_core.script_hooks.callback.items.CallbackItem;
import org.figuramc.figura_core.script_hooks.callback.items.WorldView;
import org.figuramc.figura_core.script_hooks.timing.AvatarTimeTracker;
import org.figuramc.figura_core.script_hooks.timing.ProfilingCategory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public class MinecraftMixin {

    @Inject(method = "tick", at = @At("RETURN"))
    private void endOfTick(CallbackInfo ci) {
        // Tick the things that need ticking
        AvatarManagers.pollAll();
        AvatarManagers.forEachAvatar(avatar -> {
            // Ensure it has event listeners
            AvatarEvents events = avatar.getComponent(AvatarEvents.TYPE);
            if (events == null) return;

            // Always invoke CLIENT_TICK:
            // 1 second limit by default, TODO configurable
            var eventListener1 = events.getEventListener(Event.CLIENT_TICK);
            AvatarTimeTracker.getInstance().runTimed(avatar, ProfilingCategory.CLIENT_TICK_EVENT, 1_000_000_000L, () -> eventListener1.invoke(CallbackItem.Unit.INSTANCE));

            // Invoke WORLD_TICK if the world is non-null:
            ClientLevel level = Minecraft.getInstance().level;
            if (level != null) {
                try (WorldView<MinecraftWorldImpl> worldView = new WorldView<>(new MinecraftWorldImpl(level))) {
                    // 1 second limit by default, TODO configurable
                    var eventListener2 = events.getEventListener(Event.WORLD_TICK);
                    AvatarTimeTracker.getInstance().runTimed(avatar, ProfilingCategory.WORLD_TICK_EVENT, 1_000_000_000L, () -> eventListener2.invoke(worldView));
                }
            }
        });
    }

    // Clear avatars when leaving the level. TODO come up with a cleaner way?
    @Inject(method = "updateLevelInEngines(Lnet/minecraft/client/multiplayer/ClientLevel;Z)V", at = @At("HEAD"))
    private void clearLevelHook(ClientLevel clientLevel, boolean stopSounds, CallbackInfo ci) {
        if (clientLevel == null) {
            AvatarManagers.ENTITIES.clear();
            AvatarManagers.GUIS.clear();
        }
    }

}
