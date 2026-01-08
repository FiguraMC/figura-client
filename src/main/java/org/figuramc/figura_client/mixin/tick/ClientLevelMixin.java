package org.figuramc.figura_client.mixin.tick;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.Entity;
import org.figuramc.figura_client.game_data.MinecraftEntityImpl;
import org.figuramc.figura_client.game_data.MinecraftWorldImpl;
import org.figuramc.figura_core.avatars.components.AvatarEvents;
import org.figuramc.figura_core.manage.AvatarManagers;
import org.figuramc.figura_core.manage.AvatarView;
import org.figuramc.figura_core.script_hooks.Event;
import org.figuramc.figura_core.script_hooks.callback.items.CallbackItem;
import org.figuramc.figura_core.script_hooks.callback.items.EntityView;
import org.figuramc.figura_core.script_hooks.callback.items.WorldView;
import org.figuramc.figura_core.script_hooks.timing.AvatarTimeTracker;
import org.figuramc.figura_core.script_hooks.timing.ProfilingCategory;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.UUID;

// Mixin to run the entity_tick event
@Mixin(ClientLevel.class)
public class ClientLevelMixin {

    @Inject(method = "tickNonPassenger", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;tick()V", shift = At.Shift.AFTER))
    public void afterTick(Entity entity, CallbackInfo ci) {
        callTickMethod(entity);
    }

    @Inject(method = "tickPassenger", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;rideTick()V", shift = At.Shift.AFTER))
    public void afterRideTick(Entity vehicle, Entity rider, CallbackInfo ci) {
        callTickMethod(rider);
    }

    @Unique
    private void callTickMethod(Entity entity) {
        AvatarView<UUID> avatarView = AvatarManagers.tryGetEntityAvatar(new MinecraftEntityImpl(entity));

        if (avatarView == null) return;
        avatarView.use(avatar -> {
            try (
                    EntityView<MinecraftEntityImpl> entityView = new EntityView<>(new MinecraftEntityImpl(entity));
                    WorldView<MinecraftWorldImpl> worldView = new WorldView<>(new MinecraftWorldImpl((ClientLevel) (Object) this))
            ) {
                @Nullable AvatarEvents events = avatar.getComponent(AvatarEvents.TYPE);
                if (events == null) return;
                // 20 ms limit default, TODO configurable
                var eventListener = events.getEventListener(Event.ENTITY_TICK);
                CallbackItem.Tuple2<EntityView<?>, WorldView<?>> args = new CallbackItem.Tuple2<>(entityView, worldView);
                AvatarTimeTracker.getInstance().runTimed(avatar, ProfilingCategory.ENTITY_TICK_EVENT, 20_000_000L, () -> eventListener.invoke(args));
            }
        });
    }
}
