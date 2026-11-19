package com.vandorlabs.neoforge.copier;

import com.vandorlabs.neoforge.credentials.NativeCredentialReaderBlock;
import com.vandorlabs.neoforge.screen.NativeScreenBlock;
import com.vandorlabs.neoforge.screen.NativeScreenEntity;
import com.vandorlabs.neoforge.screen.NativeScreenOwnership;
import com.vandorlabs.neoforge.transfer.NativeRawCell;
import com.vandorlabs.redstone.TransmissionSettings;
import com.vandorlabs.screen.ScreenJoinGeometry;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;

/** Exact translation of closed, stationary Screen neighborhoods with no item or signal output authority. */
final class NativeCopyScreens {
    static void requireStatic(NativeScreenEntity screen,CompoundTag raw){
        if(!NativeScreenOwnership.installable(raw)||raw.contains("ChipDisplayPage")
            ||opaque(raw,"ForgeCaps")||opaque(raw,"NeoForgeData")||opaque(raw,"neoforge:attachments")||opaque(raw,"components")
            ||!raw.getCompound("ForgeData").getAllKeys().equals(Set.of(TransmissionSettings.SETTINGS))
            ||!raw.getCompound("ForgeData").getCompound(TransmissionSettings.SETTINGS).getAllKeys().equals(Set.of("main"))
            ||!raw.getCompound("ForgeData").getCompound(TransmissionSettings.SETTINGS).getCompound("main")
                .getAllKeys().equals(Set.of("Range","Local","Channels"))
            ||!screen.transmission().channels().isEmpty()
            ||screen.getRedstoneChannels().isEmpty()&&screen.screenSettings().channelLevel()!=0
            ||!screen.controls(0).rows().isEmpty()||!screen.controls(1).rows().isEmpty()
            ||screen.controls(0).enabled()||screen.controls(1).enabled()){
            throw new IllegalArgumentException("Screen contains credentials, a private surface, or channel controls");
        }
    }

    static void resetReceived(CompoundTag raw){
        // Before receiver support, the no-bank envelope was already journaled byte for byte.
        if(raw.getIntArray("RedstoneChannels").length==0)return;
        raw.putInt("ChannelLevel",0);
        raw.putBoolean(com.vandorlabs.persistence.SaveSchema.Redstone.SIGNAL,false);
    }

    private static boolean opaque(CompoundTag raw,String key){
        return raw.contains(key)&&(!(raw.get(key) instanceof CompoundTag tag)||!tag.isEmpty());
    }

    static Set<BlockPos> boundary(List<NativeRawCell> cells,boolean constructionChip){
        var screens=new HashMap<BlockPos,NativeRawCell>();
        for(var cell:cells)if(cell.state.getBlock() instanceof NativeScreenBlock)screens.put(cell.pos,cell);
        if(constructionChip&&!screens.isEmpty())throw new IllegalArgumentException("Copy Screens from a complete stationary input area");
        var boundary=new HashSet<BlockPos>();
        for(var cell:screens.values()){
            for(var side:Direction.values())boundary.add(cell.pos.relative(side));
            var block=(NativeScreenBlock)cell.state.getBlock();
            var raw=cell.tile();
            var basis=ScreenJoinGeometry.basis(block.family(),block.screenState(cell.state),raw.getBoolean("CeilingMounted"));
            for(int step=0;step<4;step++)boundary.add(cell.pos.offset(basis.dx(step),basis.dy(step),basis.dz(step)));
        }
        boundary.removeAll(screens.keySet());
        return boundary;
    }

    static void requireIsolated(ServerLevel world,Set<BlockPos> boundary){
        for(var pos:boundary){
            if(world.isOutsideBuildHeight(pos))throw new IllegalArgumentException("Screen boundary outside build height");
            var chunk=world.getChunkSource().getChunkNow(pos.getX()>>4,pos.getZ()>>4);
            if(chunk==null)throw new IllegalArgumentException("Load the complete Screen boundary");
            var block=chunk.getBlockState(pos).getBlock();
            if(block instanceof NativeScreenBlock||block instanceof NativeCredentialReaderBlock)
                throw new IllegalArgumentException("Capture the complete Screen group and remove adjacent credential readers");
        }
    }

    private NativeCopyScreens(){}
}
