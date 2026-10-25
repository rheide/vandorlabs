package com.vandorlabs.gear.track;

import com.vandorlabs.gear.GearTileEntity;
import com.vandorlabs.gear.core.GearTopology;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import java.util.ArrayList;
import java.util.List;

/** Select saved artwork on the same track blocks and tile instances. */
public final class TrackStyles {
    private TrackStyles(){}
    public static int style(GearTileEntity host){
        if(host instanceof TrackTile)return ((TrackTile)host).style().ordinal();
        if(host.assembly()!=null&&host.assembly().kind()==GearTopology.FootKind.TRACK)for(GearTopology.Cell foot:host.assembly().feet())if(host.getWorld().getBlockState(host.localCell(foot)).getBlock() instanceof TrackBlock)return TrackBlock.style(host.getWorld(),host.localCell(foot)).ordinal();
        return -1;
    }
    public static void apply(GearTileEntity host,int value){
        if(value<0||value>=TrackRegistry.BLOCKS.length)throw new IllegalArgumentException("Invalid track style");
        if(host.getWorld()==null||host.getWorld().isRemote||style(host)<0)return;
        List<BlockPos> positions=new ArrayList<>();
        if(host.assembly()!=null&&host.assembly().kind()==GearTopology.FootKind.TRACK)for(GearTopology.Cell foot:host.assembly().feet())positions.add(host.localCell(foot));
        else if(host instanceof TrackTile){
            TrackTile track=(TrackTile)host;TrackTile leader=track.leader()==null?track:TrackWorld.tile(host.getWorld(),track.leader());
            if(leader!=null&&leader.hasCompleteAssembly())positions.addAll(leader.trackMembers());else positions.add(host.getPos());
        }
        for(BlockPos at:positions){if(!TrackWorld.loaded(host.getWorld(),at)||!(host.getWorld().getBlockState(at).getBlock() instanceof TrackBlock)||!(host.getWorld().getTileEntity(at) instanceof TrackTile))throw new IllegalArgumentException("Load the complete track group first");}
        for(BlockPos at:positions)((TrackTile)host.getWorld().getTileEntity(at)).style(value);
        host.scheduleRebuild();
    }
}
