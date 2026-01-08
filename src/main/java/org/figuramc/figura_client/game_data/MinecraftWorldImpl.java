package org.figuramc.figura_client.game_data;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.LightLayer;
import org.figuramc.figura_client.FiguraClient;
import org.figuramc.figura_core.minecraft_interop.game_data.MinecraftIdentifier;
import org.figuramc.figura_core.minecraft_interop.game_data.block.MinecraftBlockState;
import org.figuramc.figura_core.minecraft_interop.game_data.entity.MinecraftEntity;
import org.figuramc.figura_core.minecraft_interop.game_data.world.MinecraftWorld;
import org.figuramc.figura_core.util.ListUtils;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.UUID;

public record MinecraftWorldImpl(ClientLevel level) implements MinecraftWorld {


    @Override public List<MinecraftEntity> getEntities() { return ListUtils.map(level.entitiesForRendering(), MinecraftEntityImpl::new); }
    @Override public List<MinecraftEntity> getPlayers() { return ListUtils.map(level.players(), MinecraftEntityImpl::new); }
    @Override public MinecraftBlockState getBlockState(int x, int y, int z) {
        BlockPos pos = new BlockPos(x, y, z);
        return new MinecraftBlockStateImpl(level.getBlockState(pos), level, pos);
    }
    @Override public @Nullable MinecraftEntity getEntity(UUID uuid) { Entity e = level.getEntity(uuid); return e == null ? null : new MinecraftEntityImpl(e); }

    @Override public MinecraftIdentifier getDimension() { return FiguraClient.coreIdent(level.dimension().identifier()); }
    @Override public int getHeight() { return level.getHeight(); }

    private static final float MAX_REDSTONE_POWER = 15f;
    @Override public float getRedstonePower(int x, int y, int z) { return level.getBestNeighborSignal(new BlockPos(x, y, z)) / MAX_REDSTONE_POWER; }
    @Override public float getStrongRedstonePower(int x, int y, int z) { return level.getDirectSignalTo(new BlockPos(x, y, z)) / MAX_REDSTONE_POWER; }

    private static final float MAX_LIGHT_LEVEL = 15f;
    @Override public float getLight(int x, int y, int z) { return level.getRawBrightness(new BlockPos(x, y, z), level.getSkyDarken()) / MAX_LIGHT_LEVEL; }
    @Override public float getSkyLight(int x, int y, int z) { return level.getBrightness(LightLayer.SKY, new BlockPos(x, y, z)) / MAX_LIGHT_LEVEL; }
    @Override public float getBlockLight(int x, int y, int z) { return level.getBrightness(LightLayer.BLOCK, new BlockPos(x, y, z)) / MAX_LIGHT_LEVEL; }

    @Override public long getTime() { return level.getGameTime(); }
    @Override public long getTimeOfDay() { return level.getDayTime() % 24000L; }
    @Override public long getDay() { return level.getDayTime() / 24000L; }

    @Override public int getMoonPhase() { throw new UnsupportedOperationException("TODO make this an enumlike"); }

    @Override public float getRainGradient(float tickDelta) { return level.getRainLevel(tickDelta); }
    @Override public boolean isThundering() { return level.isThundering(); }
    @Override public boolean isOpenSky(int x, int y, int z) { return level.canSeeSky(new BlockPos(x, y, z)); }

}
