package org.figuramc.figura_client.game_data;

import net.minecraft.client.Minecraft;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.equipment.Equippable;
import org.figuramc.figura_client.FiguraClient;
import org.figuramc.figura_core.minecraft_interop.game_data.MinecraftIdentifier;
import org.figuramc.figura_core.minecraft_interop.game_data.item.EquipmentSlot;
import org.figuramc.figura_core.minecraft_interop.game_data.item.ItemRarity;
import org.figuramc.figura_core.minecraft_interop.game_data.item.ItemUseAction;
import org.figuramc.figura_core.minecraft_interop.game_data.item.MinecraftItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public record MinecraftItemStackImpl(ItemStack stack) implements MinecraftItemStack {

    @Override public MinecraftIdentifier getId() { return stack.getItemHolder().unwrapKey().map(key -> FiguraClient.coreIdent(key.identifier())).orElse(FiguraClient.UNKNOWN); }
    @Override public List<MinecraftIdentifier> getTags() { return stack.getTags().map(tag -> FiguraClient.coreIdent(tag.location())).toList(); }

    @Override public String getName() { return stack.getHoverName().getString(); }
    @Override public String getStackString() { throw new UnsupportedOperationException("TODO"); }

    @Override public ItemUseAction getUseAction() {
        return switch (stack.getUseAnimation()) {
            case NONE -> ItemUseAction.NONE;
            case EAT -> ItemUseAction.EAT;
            case DRINK -> ItemUseAction.DRINK;
            case BLOCK -> ItemUseAction.BLOCK;
            case BOW -> ItemUseAction.BOW;
            case TRIDENT -> ItemUseAction.TRIDENT;
            case CROSSBOW -> ItemUseAction.CROSSBOW;
            case SPYGLASS -> ItemUseAction.SPYGLASS;
            case TOOT_HORN -> ItemUseAction.TOOT_HORN;
            case BRUSH -> ItemUseAction.BRUSH;
            case BUNDLE -> ItemUseAction.BUNDLE;
            case SPEAR -> ItemUseAction.SPEAR;
        };
    }
    @Override public ItemRarity getRarity() {
        return switch (stack.getRarity()) {
            case COMMON -> ItemRarity.COMMON;
            case UNCOMMON -> ItemRarity.UNCOMMON;
            case RARE -> ItemRarity.RARE;
            case EPIC -> ItemRarity.EPIC;
        };
    }
    @Override public @Nullable EquipmentSlot getEquipmentSlot() {
        Equippable equippable = stack.get(DataComponents.EQUIPPABLE);
        if (equippable == null) return null;
        return switch (equippable.slot()) {
            case MAINHAND -> EquipmentSlot.MAINHAND;
            case OFFHAND -> EquipmentSlot.OFFHAND;
            case FEET -> EquipmentSlot.FEET;
            case LEGS -> EquipmentSlot.LEGS;
            case CHEST -> EquipmentSlot.CHEST;
            case HEAD -> EquipmentSlot.HEAD;
            case BODY -> EquipmentSlot.BODY;
            case SADDLE -> EquipmentSlot.SADDLE;
        };
    }

    @Override public int getCount() { return stack.getCount(); }
    @Override public int getDurability() { return stack.getMaxDamage() - stack.getDamageValue(); }
    @Override public int getMaxDurability() { return stack.getMaxDamage(); }
    @Override public int getPopTime() { return stack.getPopTime(); }
    @Override public int getRepairCost() { return stack.get(DataComponents.REPAIR_COST) instanceof Integer i ? i : 0; }
    @Override public int getUseDuration() { return Minecraft.getInstance().player instanceof LivingEntity e ? stack.getUseDuration(e) : 0; }

    @Override public boolean hasGlint() { return stack.hasFoil(); }
    @Override public boolean isBlockItem() { return stack.getItem() instanceof BlockItem; }
    @Override public boolean isFood() { return stack.get(DataComponents.FOOD) != null; }
    @Override public boolean isEnchantable() { return stack.isEnchantable(); }
    @Override public boolean isDamageable() { return stack.isDamageableItem(); }
    @Override public boolean isStackable() { return stack.isStackable(); }
    @Override public boolean isArmor() { return stack.get(DataComponents.EQUIPPABLE) != null; }
    @Override public boolean isTool() { return stack.get(DataComponents.TOOL) != null; }

}
