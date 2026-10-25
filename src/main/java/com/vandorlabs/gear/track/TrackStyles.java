package com.vandorlabs.gear.track;

import com.vandorlabs.gear.GearTileEntity;
import com.vandorlabs.gear.core.GearTopology;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import java.util.ArrayList;
import java.util.List;

/** Swap the style without dropping or recreating the retained mechanism settings. */
public final class TrackStyles {
    private TrackStyles(){}
    public static int style(GearTileEntity host){
        if(host.getBlockType() instanceof TrackBlock)return ((TrackBlock)host.getBlockType()).style().ordinal();
        if(host.assembly()!=null&&host.assembly().kind()==GearTopology.FootKind.TRACK)for(GearTopology.Cell foot:host.assembly().feet())if(host.getWorld().getBlockState(host.localCell(foot)).getBlock() instanceof TrackBlock)return ((TrackBlock)host.getWorld().getBlockState(host.localCell(foot)).getBlock()).style().ordinal();
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
        for(BlockPos at:positions){if(!TrackWorld.loaded(host.getWorld(),at)||!(host.getWorld().getBlockState(at).getBlock() instanceof TrackBlock))throw new IllegalArgumentException("Load the complete track group first");}
        for(BlockPos at:positions){
            net.minecraft.block.state.IBlockState state=host.getWorld().getBlockState(at);if(state.getBlock()==TrackRegistry.BLOCKS[value])continue;
            TileEntity tile=host.getWorld().getTileEntity(at);NBTTagCompound data=tile.writeToNBT(new NBTTagCompound());
            // Remove the old tile first so breakBlock cannot release its shared arm controller.
            host.getWorld().removeTileEntity(at);
            host.getWorld().setBlockState(at,TrackRegistry.BLOCKS[value].getDefaultState().withProperty(TrackBlock.FACING,state.getValue(TrackBlock.FACING)).withProperty(TrackBlock.ASSEMBLED,state.getValue(TrackBlock.ASSEMBLED)),2);
            TileEntity replacement=host.getWorld().getTileEntity(at);if(replacement!=null)host.getWorld().removeTileEntity(at);tile.validate();tile.readFromNBT(data);tile.updateContainingBlockInfo();host.getWorld().setTileEntity(at,tile);tile.markDirty();host.getWorld().notifyBlockUpdate(at,state,host.getWorld().getBlockState(at),2);
            TrackWorld.requestAround(host.getWorld(),at);
        }
        host.scheduleRebuild();
    }
}
