package com.vandorlabs.neoforge.copier;

import com.vandorlabs.neoforge.transfer.NativeRawCell;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.ChestType;

/** Explicit copying adapters; unknown ownership is refused rather than treated as a free block item. */
final class NativeCopyTiles {
    static boolean pairedChest(BlockState state) {
        return state.getBlock() instanceof ChestBlock && state.getValue(BlockStateProperties.CHEST_TYPE) != ChestType.SINGLE;
    }

    static boolean chestPartners(BlockPos firstPos, BlockState first, BlockPos secondPos, BlockState second) {
        return pairedChest(first) && second.getBlock() == first.getBlock() && pairedChest(second)
            && second.getValue(BlockStateProperties.CHEST_TYPE) != first.getValue(BlockStateProperties.CHEST_TYPE)
            && second.getValue(ChestBlock.FACING) == first.getValue(ChestBlock.FACING)
            && firstPos.relative(ChestBlock.getConnectedDirection(first)).equals(secondPos)
            && secondPos.relative(ChestBlock.getConnectedDirection(second)).equals(firstPos);
    }

    static void requireCompleteChests(List<NativeRawCell> source) {
        if (source.stream().noneMatch(cell -> pairedChest(cell.state))) return;
        var chests = new HashMap<BlockPos, NativeRawCell>();
        for (var cell : source) {
            if (pairedChest(cell.state)) chests.put(cell.pos, cell);
        }
        for (var cell : chests.values()) {
            var partner = chests.get(cell.pos.relative(ChestBlock.getConnectedDirection(cell.state)));
            if (!cell.hasTile() || partner == null || !chestPartners(cell.pos, cell.state, partner.pos, partner.state)) {
                throw new IllegalArgumentException("Capture both matching halves of the input chest at " + cell.pos.toShortString());
            }
        }
    }

    static boolean supported(BlockEntity tile){
        if(tile instanceof com.vandorlabs.neoforge.navigation.observer.NativeObserverEntity observer)return !observer.inert();
        if(tile instanceof com.vandorlabs.neoforge.navigation.observer.NativeObserverPartEntity)return true;
        if(tile instanceof com.vandorlabs.neoforge.navigation.NativeNavigationTableEntity table)return !table.inert();
        if(tile instanceof com.vandorlabs.neoforge.storage.NativeStorageEntity storage)return !storage.inert();
        if(tile instanceof com.vandorlabs.neoforge.rack.NativeRackEntity rack)return !rack.inert();
        if(tile instanceof com.vandorlabs.neoforge.surface.NativeSurfaceEntity surface)return !surface.inert();
        if(tile instanceof com.vandorlabs.neoforge.ports.NativeResourcePortEntity port)return !port.inert();
        if(tile instanceof com.vandorlabs.neoforge.reservoir.NativeBufferEntity buffer)return !buffer.inert();
        if(tile instanceof com.vandorlabs.neoforge.ProgrammableBlockEntity){var block=tile.getBlockState().getBlock();return block==com.vandorlabs.neoforge.VandorLabsBootstrap.PROGRAMMABLE_BLOCK.get()||block==com.vandorlabs.neoforge.VandorLabsBootstrap.PROGRAMMABLE_SLAB.get()||block==com.vandorlabs.neoforge.VandorLabsBootstrap.PROGRAMMABLE_STAIRS.get();}
        return tile.getClass()==ChestBlockEntity.class||tile.getClass()==TrappedChestBlockEntity.class||tile.getClass()==BarrelBlockEntity.class||tile.getClass()==SignBlockEntity.class||tile.getClass()==HangingSignBlockEntity.class;
    }
    static boolean same(CompoundTag first,CompoundTag second){
        if(Objects.equals(first,second))return true;if(first==null||second==null)return false;var a=first.copy();var b=second.copy();for(var tag:List.of(a,b)){com.vandorlabs.surface.TextureJoinSettings.normalizeLegacy(new com.vandorlabs.neoforge.ModernHousingTags(tag));if(tag.contains("keepPacked",Tag.TAG_BYTE)&&!tag.getBoolean("keepPacked"))tag.remove("keepPacked");if(tag.contains("NeoForgeData",Tag.TAG_COMPOUND)&&tag.getCompound("NeoForgeData").isEmpty())tag.remove("NeoForgeData");}return a.equals(b);
    }
    static BlockEntity stage(ServerLevel world,NativeRawCell cell){
        if(cell.hasTile()){var raw=cell.tile();if(raw.contains("LootTable"))throw new IllegalArgumentException("Open loot containers before copying");}
        var tile=cell.stage(world,NativeCopyTiles::supported,NativeCopyTiles::same);
        if(tile instanceof com.vandorlabs.neoforge.navigation.observer.NativeObserverPartEntity part&&part.inert())
            throw new IllegalArgumentException("Observer part failed ownership staging");
        if(tile instanceof com.vandorlabs.neoforge.ProgrammableBlockEntity&&!(tile instanceof com.vandorlabs.neoforge.storage.NativeStorageEntity)&&!(tile instanceof com.vandorlabs.neoforge.reservoir.NativeBufferEntity)){
            var raw=cell.tile();if(!com.vandorlabs.neoforge.screen.NativeScreenOwnership.installable(raw))throw new IllegalArgumentException("Owned building controls require their complete copy adapter");var transmission=com.vandorlabs.neoforge.redstone.NativeTransmissionMigration.read(raw);if(!transmission.installable()||!transmission.install().equals(raw))throw new IllegalArgumentException("Owned building transmission requires its copy adapter");
        }
        return tile;
    }
    static NativeRawCell at(NativeRawCell source,BlockPos target,UUID copyOwner){
        var raw=source.tile();if(raw!=null){raw.putInt("x",target.getX());raw.putInt("y",target.getY());raw.putInt("z",target.getZ());NativeCopyObservers.translateIdentity(source,target,copyOwner,raw);
            // A drawing claim is not a transferable authority over an unrelated destination controller.
            if(source.state.getBlock() instanceof com.vandorlabs.neoforge.surface.NativeSurfaceBlock){for(String field:List.of("Owner","OwnerKey","OwnerKeyMost","OwnerKeyLeast"))raw.remove(field);}
        }
        return new NativeRawCell(target,source.state,raw,2*1024*1024);
    }
    private NativeCopyTiles(){}
}
