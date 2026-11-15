package com.vandorlabs.neoforge.copier;

import com.vandorlabs.neoforge.transfer.NativeRawCell;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.*;

/** Explicit copying adapters; unknown ownership is refused rather than treated as a free block item. */
final class NativeCopyTiles {
    static boolean supported(BlockEntity tile){
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
        if(tile instanceof com.vandorlabs.neoforge.ProgrammableBlockEntity&&!(tile instanceof com.vandorlabs.neoforge.storage.NativeStorageEntity)&&!(tile instanceof com.vandorlabs.neoforge.reservoir.NativeBufferEntity)){
            var raw=cell.tile();if(!com.vandorlabs.neoforge.screen.NativeScreenOwnership.installable(raw))throw new IllegalArgumentException("Owned building controls require their complete copy adapter");var transmission=com.vandorlabs.neoforge.redstone.NativeTransmissionMigration.read(raw);if(!transmission.installable()||!transmission.install().equals(raw))throw new IllegalArgumentException("Owned building transmission requires its copy adapter");
        }
        return tile;
    }
    static NativeRawCell at(NativeRawCell source,BlockPos target){
        var raw=source.tile();if(raw!=null){raw.putInt("x",target.getX());raw.putInt("y",target.getY());raw.putInt("z",target.getZ());
            // A drawing claim is not a transferable authority over an unrelated destination controller.
            if(source.state.getBlock() instanceof com.vandorlabs.neoforge.surface.NativeSurfaceBlock){for(String field:List.of("Owner","OwnerKey","OwnerKeyMost","OwnerKeyLeast"))raw.remove(field);}
        }
        return new NativeRawCell(target,source.state,raw,2*1024*1024);
    }
    private NativeCopyTiles(){}
}
