package com.vandorlabs.neoforge.vehicle;

import com.vandorlabs.neoforge.credentials.NativeCredentialReaderBlock;
import com.vandorlabs.neoforge.credentials.NativeCredentialReaderEntity;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public final class NativeVehicleControllerEntity extends BlockEntity {
    private UUID owner;
    private boolean security;

    public NativeVehicleControllerEntity(BlockPos pos,BlockState state) {
        super(NativeVehicleBindings.CONTROLLER_ENTITY,pos,state);
    }

    void placedBy(UUID id) {
        if (owner==null) { owner=id; setChanged(); }
    }

    public boolean securityEnabled(){return security;}
    public boolean mayChangeSecurity(Player player) {
        return owner!=null && owner.equals(player.getUUID());
    }
    boolean setSecurity(ServerPlayer player,boolean enabled) {
        if(!mayChangeSecurity(player)||!com.vandorlabs.neoforge.NativeConfigurationAccess.canEditDialog(player,this))return false;
        security=enabled;setChanged();return true;
    }

    public UUID ownerId() { return owner; }

    public boolean mayOperate(ServerPlayer player) {
        if (player.level()!=level || player.isSpectator() || !player.isAlive()) return false;
        if (!security) return true;
        for (Direction side: Direction.values()) {
            BlockPos pos=worldPosition.relative(side);
            if (!level.hasChunkAt(pos) || !(level.getBlockEntity(pos) instanceof NativeCredentialReaderEntity reader)) continue;
            if (reader.getBlockState().getBlock() instanceof NativeCredentialReaderBlock block
                    && block.supportDirection(reader.getBlockState())==side.getOpposite()
                    && reader.permitsVehicleOperation()) return true;
        }
        return false;
    }

    @Override protected void saveAdditional(CompoundTag tag,HolderLookup.Provider registries) {
        super.saveAdditional(tag,registries);
        if (owner!=null) tag.putUUID("VehicleOwner",owner);
        if (security) tag.putBoolean("VehicleSecurity",true);
    }

    @Override protected void loadAdditional(CompoundTag tag,HolderLookup.Provider registries) {
        super.loadAdditional(tag,registries);
        owner=tag.hasUUID("VehicleOwner")?tag.getUUID("VehicleOwner"):null;
        security=tag.getBoolean("VehicleSecurity");
    }
}
