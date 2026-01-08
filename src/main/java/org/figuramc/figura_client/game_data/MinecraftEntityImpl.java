package org.figuramc.figura_client.game_data;

import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.ClientAvatarEntity;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.player.PlayerModelType;
import net.minecraft.world.phys.Vec3;
import org.figuramc.figura_client.FiguraClient;
import org.figuramc.figura_client.vanilla_model.VanillaModelCache;
import org.figuramc.figura_core.minecraft_interop.game_data.MinecraftIdentifier;
import org.figuramc.figura_core.minecraft_interop.game_data.entity.EntityPose;
import org.figuramc.figura_core.minecraft_interop.game_data.entity.MinecraftEntity;
import org.figuramc.figura_core.minecraft_interop.game_data.item.EquipmentSlot;
import org.figuramc.figura_core.minecraft_interop.game_data.item.MinecraftItemStack;
import org.figuramc.figura_core.minecraft_interop.game_data.types.AABB;
import org.figuramc.figura_core.minecraft_interop.game_data.world.MinecraftWorld;
import org.figuramc.figura_core.minecraft_interop.vanilla_parts.VanillaModel;
import org.figuramc.figura_core.util.ListUtils;
import org.figuramc.figura_core.util.enumlike.IdMap;
import org.joml.Vector2f;
import org.joml.Vector3d;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.UUID;

public record MinecraftEntityImpl(Entity entity) implements MinecraftEntity {


    @Override public VanillaModel getModel() { return VanillaModelCache.get(entity); }
    @Override public boolean isGone() { return entity.isRemoved() || entity.level() != Minecraft.getInstance().level; }

    @Override public MinecraftWorld getWorld() { return new MinecraftWorldImpl((ClientLevel) entity.level()); }

    @Override public String getName() { return entity.getPlainTextName(); }
    @Override public UUID getUUID() { return entity.getUUID(); }

    @Override public MinecraftIdentifier getType() { return FiguraClient.coreIdent(EntityType.getKey(entity.getType())); }
    @Override public boolean isPlayer() { return entity instanceof Player; }
    @Override public boolean isLivingEntity() { return entity instanceof LivingEntity; }
    @Override public boolean hasInventory() { return entity instanceof HasCustomInventoryScreen; }

    @Override public Vector3d getPosition(float tickDelta, Vector3d out) { return convertVec(entity.getPosition(tickDelta), out); }
    @Override public Vector2f getRotation(float tickDelta, Vector2f out) { return out.set(entity.getViewXRot(tickDelta), entity.getViewYRot(tickDelta)); }
    @Override public Vector3d getVelocity(Vector3d out) { return out.set(entity.getX() - entity.xOld, entity.getY() - entity.yOld, entity.getZ() - entity.zOld); }
    @Override public Vector3d getLookDirection(float tickDelta, Vector3d out) { return convertVec(entity.getViewVector(tickDelta), out); }
    @Override public double getEyeHeight() { return entity.getEyeHeight(); }
    @Override public AABB getBoundingBox() {
        var mcbb = entity.getBoundingBox();
        return new AABB(mcbb.minX, mcbb.minY, mcbb.minZ, mcbb.maxX, mcbb.maxY, mcbb.maxZ);
    }

    @Override public float getFreezeTime() { return entity.getTicksFrozen(); }
    @Override public float getFreezeDuration() { return entity.getTicksRequiredToFreeze(); }
    @Override public float getAir() { return entity.getAirSupply(); }
    @Override public float getMaxAir() { return entity.getMaxAirSupply(); }
    @Override public float getHealth() { return (entity instanceof LivingEntity living) ? living.getHealth() : 1f; }
    @Override public float getMaxHealth() { return (entity instanceof LivingEntity living) ? living.getMaxHealth() : 1f; }
    @Override public float getArmor() { return (entity instanceof LivingEntity living) ? living.getArmorValue() : 0f; }
    @Override public float getAbsorption() { return (entity instanceof LivingEntity living) ? living.getAbsorptionAmount() : 0f; }
    @Override public float getDyingTime() { return (entity instanceof LivingEntity living) ? living.deathTime : 0; }
    @Override public float getDyingDuration() { return 20; }
    @Override public boolean isGlowing() { return entity.isCurrentlyGlowing(); }
    @Override public boolean isInvisible() { return entity.isInvisible(); }
    @Override public boolean isSilent() { return entity.isSilent(); }
    @Override public boolean isOnFire() { return entity.isOnFire(); }
    @Override public int getArrowCount() { return (entity instanceof LivingEntity living) ? living.getArrowCount() : 0; }
    @Override public int getStingerCount() { return (entity instanceof LivingEntity living) ? living.getStingerCount() : 0; }

    @Override public EntityPose getPose() {
        return switch (entity.getPose()) {
            case STANDING -> EntityPose.STANDING;
            case FALL_FLYING -> EntityPose.FALL_FLYING;
            case SLEEPING -> EntityPose.SLEEPING;
            case SWIMMING -> EntityPose.SWIMMING;
            case SPIN_ATTACK -> EntityPose.SPIN_ATTACK;
            case CROUCHING -> EntityPose.CROUCHING;
            case LONG_JUMPING -> EntityPose.LONG_JUMPING;
            case DYING -> EntityPose.DYING;
            case CROAKING -> EntityPose.CROAKING;
            case USING_TONGUE -> EntityPose.USING_TONGUE;
            case SITTING -> EntityPose.SITTING;
            case ROARING -> EntityPose.ROARING;
            case SNIFFING -> EntityPose.SNIFFING;
            case EMERGING -> EntityPose.EMERGING;
            case DIGGING -> EntityPose.DIGGING;
            case SLIDING -> EntityPose.SLIDING;
            case SHOOTING -> EntityPose.SHOOTING;
            case INHALING -> EntityPose.INHALING;
        };
    }
    @Override public boolean isSneaking() { return entity.isShiftKeyDown(); }
    @Override public boolean isSprinting() { return entity.isSprinting(); }
    @Override public boolean isMoving() { return entity.getX() != entity.xOld || entity.getY() != entity.yOld || entity.getZ() != entity.zOld; }
    @Override public boolean isMovingHorizontally() { return entity.getX() != entity.xOld || entity.getZ() != entity.zOld; }
    @Override public boolean isOnGround() { return entity.onGround(); }
    @Override public boolean isFalling() { return !entity.onGround() && entity.getY() < entity.yOld; }
    @Override public boolean isClimbing() { return entity instanceof LivingEntity living && living.onClimbable(); }
    @Override public boolean isGliding() { return entity instanceof LivingEntity living && living.isFallFlying(); }
    @Override public boolean isBlocking() { return entity instanceof LivingEntity living && living.isBlocking(); }
    @Override public boolean isSwimming() { return entity instanceof LivingEntity living && living.isVisuallySwimming(); }
    @Override public boolean isCrawling() { return entity instanceof LivingEntity living && living.isVisuallyCrawling(); }
    @Override public boolean isRiptideSpinning() { return entity instanceof LivingEntity living && living.isAutoSpinAttack(); }

    @Override public boolean isLeftHanded() { return entity instanceof LivingEntity living && living.getMainArm() == HumanoidArm.LEFT; }
    @Override public boolean isSwinging() { return entity instanceof LivingEntity living && living.swinging; }
    @Override public float getSwingTime() { return entity instanceof LivingEntity living ? living.swingTime : 0; }
    @Override public float getSwingDuration() { return entity instanceof LivingEntity living ? living.getCurrentSwingDuration() : 6; }
    @Override public boolean swingingOffHand() { return entity instanceof LivingEntity living && living.swinging && living.swingingArm == InteractionHand.OFF_HAND; }

    @Override public boolean isSlim() { return entity instanceof ClientAvatarEntity player && player.getSkin().model() == PlayerModelType.SLIM; }

    @Override public boolean isInWater() { return entity.isInWater(); }
    @Override public boolean isUnderwater() { return entity.isUnderWater(); }
    @Override public boolean isInLava() { return entity.isInLava(); }
    @Override public boolean isInRain() { return entity.isInRain(); }

    @Override public @Nullable MinecraftEntity getVehicle() { var vehicle = entity.getVehicle(); return vehicle == null ? null : new MinecraftEntityImpl(vehicle); }
    @Override public @Nullable MinecraftEntity getControlledVehicle() { var vehicle = entity.getControlledVehicle(); return vehicle == null ? null : new MinecraftEntityImpl(vehicle); }
    @Override public List<MinecraftEntity> getPassengers() { return ListUtils.map(entity.getPassengers(), MinecraftEntityImpl::new); }
    @Override public @Nullable MinecraftEntity getControllingPassenger() { var passenger = entity.getControllingPassenger(); return passenger == null ? null : new MinecraftEntityImpl(passenger); }

    @Override public @Nullable MinecraftItemStack getItem(EquipmentSlot equipmentSlot) {
        if (!(entity instanceof LivingEntity living)) return null;
        var item = living.getItemBySlot(equipSlotToMinecraft.get(equipmentSlot));
        if (item == null || item.isEmpty()) return null;
        return new MinecraftItemStackImpl(item);
    }
    @Override public @Nullable MinecraftItemStack getActiveItem() {
        if (!(entity instanceof LivingEntity living)) return null;
        var item = living.getActiveItem();
        if (item == null || item.isEmpty()) return null;
        return new MinecraftItemStackImpl(item);
    }
    @Override public int getItemUseTicks() {
        if (!(entity instanceof LivingEntity living)) return 0;
        return living.getTicksUsingItem();
    }
    @Override public boolean isFishing() { return entity instanceof Player player && player.fishing != null; }


    // Converting helpers
    private static Vector3d convertVec(Vec3 minecraftVec, Vector3d out) {
        return out.set(minecraftVec.x, minecraftVec.y, minecraftVec.z);
    }

    private static final IdMap<EquipmentSlot, net.minecraft.world.entity.EquipmentSlot> equipSlotToMinecraft = new IdMap<>(EquipmentSlot.class);
    static {
        equipSlotToMinecraft.put(EquipmentSlot.MAINHAND, net.minecraft.world.entity.EquipmentSlot.MAINHAND);
        equipSlotToMinecraft.put(EquipmentSlot.OFFHAND, net.minecraft.world.entity.EquipmentSlot.OFFHAND);
        equipSlotToMinecraft.put(EquipmentSlot.FEET, net.minecraft.world.entity.EquipmentSlot.FEET);
        equipSlotToMinecraft.put(EquipmentSlot.LEGS, net.minecraft.world.entity.EquipmentSlot.LEGS);
        equipSlotToMinecraft.put(EquipmentSlot.CHEST, net.minecraft.world.entity.EquipmentSlot.CHEST);
        equipSlotToMinecraft.put(EquipmentSlot.HEAD, net.minecraft.world.entity.EquipmentSlot.HEAD);
        equipSlotToMinecraft.put(EquipmentSlot.BODY, net.minecraft.world.entity.EquipmentSlot.BODY);
        equipSlotToMinecraft.put(EquipmentSlot.SADDLE, net.minecraft.world.entity.EquipmentSlot.SADDLE);
    }


}
