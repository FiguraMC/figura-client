package org.figuramc.figura_client.game_data;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.commands.arguments.blocks.BlockStateParser;
import net.minecraft.core.BlockPos;
import net.minecraft.util.ARGB;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.figuramc.figura_client.FiguraClient;
import org.figuramc.figura_core.minecraft_interop.game_data.MinecraftIdentifier;
import org.figuramc.figura_core.minecraft_interop.game_data.block.MinecraftBlockState;
import org.figuramc.figura_core.minecraft_interop.game_data.item.MinecraftItemStack;
import org.figuramc.figura_core.minecraft_interop.game_data.types.AABB;
import org.figuramc.figura_core.util.ListUtils;
import org.joml.Vector3f;
import org.joml.Vector3i;

import java.util.ArrayList;
import java.util.List;

public record MinecraftBlockStateImpl(BlockState blockState, ClientLevel level, BlockPos blockPos) implements MinecraftBlockState {


    @Override public MinecraftBlockState withPos(int x, int y, int z) { return new MinecraftBlockStateImpl(blockState, level, new BlockPos(x, y, z)); }
    @Override public MinecraftIdentifier getId() { return blockState.getBlockHolder().unwrapKey().map(key -> FiguraClient.coreIdent(key.identifier())).orElse(FiguraClient.UNKNOWN); }
    @Override public Vector3i getPos(Vector3i out) { return out.set(blockPos.getX(), blockPos.getY(), blockPos.getZ()); }
    @Override public MinecraftItemStack asItem() { throw new UnsupportedOperationException("TODO"); }

    @Override public List<AABB> getCollisionShape() { return voxelShapeToCore(blockState.getCollisionShape(level, blockPos)); }
    @Override public List<AABB> getOutlineShape() { return voxelShapeToCore(blockState.getShape(level, blockPos)); }
    @Override public boolean hasCollision() { return !blockState.getCollisionShape(level, blockPos).isEmpty(); }
    @Override public boolean isFullCube() { return blockState.isCollisionShapeFullBlock(level, blockPos); }
    @Override public boolean isOpaque() { return blockState.canOcclude(); }

    @Override public List<String> getProperties() { return ListUtils.map(blockState.getProperties(), Property::getName); }
    @Override public List<MinecraftIdentifier> getTags() { return blockState.getTags().map(tag -> FiguraClient.coreIdent(tag.location())).toList(); }
    @Override public List<MinecraftIdentifier> getFluidTags() { return blockState.getFluidState().getTags().map(tag -> FiguraClient.coreIdent(tag.location())).toList(); }
    @Override public boolean hasBlockEntity() { return blockState.hasBlockEntity(); }
    @Override public String getStateString() {
        BlockEntity entity = level.getBlockEntity(blockPos);
        ValueOutput output = TagValueOutput.createWithoutContext(ProblemReporter.DISCARDING);
        if (entity != null) entity.saveWithoutMetadata(output);
        return BlockStateParser.serialize(blockState) +  output;
    }

    @Override public boolean conductsRedstone() { return blockState.isRedstoneConductor(level, blockPos); }

    @Override public float getFriction() { return blockState.getBlock().getFriction(); }
    @Override public float getVelocityModifier() { return blockState.getBlock().getSpeedFactor(); }
    @Override public float getJumpModifier() { return blockState.getBlock().getJumpFactor(); }

    @Override public float getHardness() { return blockState.getDestroySpeed(level, blockPos); }
    @Override public float getBlastResistance() { return blockState.getBlock().getExplosionResistance(); }

    @Override public Vector3f getMapColor(Vector3f out) {
        int argbColor = blockState.getMapColor(level, blockPos).col;
        return out.set(ARGB.redFloat(argbColor), ARGB.greenFloat(argbColor), ARGB.blueFloat(argbColor));
    }

    private static final float MAX_LIGHT_LEVEL = 15f;
    @Override public float getLightBlocked() { return blockState.getLightBlock() / MAX_LIGHT_LEVEL; }
    @Override public float getLightEmitted() { return blockState.getLightEmission() / MAX_LIGHT_LEVEL; }
    @Override public boolean usesEmissiveRendering() { return blockState.emissiveRendering(level, blockPos); }

    // Helper to convert a VoxelShape to the expected core representation
    private static List<AABB> voxelShapeToCore(VoxelShape voxelShape) {
        List<AABB> aabbs = new ArrayList<>();
        for (net.minecraft.world.phys.AABB aabb : voxelShape.toAabbs())
            aabbs.add(new AABB(aabb.minX, aabb.minY, aabb.minZ, aabb.maxX, aabb.maxY, aabb.maxZ));
        return aabbs;
    }
}
