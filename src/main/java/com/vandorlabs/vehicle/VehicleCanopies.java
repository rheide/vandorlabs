package com.vandorlabs.vehicle;

import com.vandorlabs.blocks.BlockCanopy;
import com.vandorlabs.tiles.TileEntityCanopy;
import net.minecraft.block.Block;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import java.nio.ByteBuffer;
import java.util.*;

/** Explicit canopy motion in the local vehicle world; unrelated tiles never tick. */
public final class VehicleCanopies {
    private VehicleCanopies() {}
    public static boolean supports(Block block) {
        return block instanceof BlockCanopy && (((BlockCanopy) block).opening() || com.vandorlabs.canopy.LargeCanopySettings.supports(((BlockCanopy)block).kind));
    }
    private static TileEntityCanopy owner(EntityGroundVehicle craft, BlockPos hit) {
        if(craft.view==null || craft.structure==null)return null;
        TileEntity tile=craft.view.getTileEntity(hit);
        if(!(tile instanceof TileEntityCanopy))return null;
        TileEntityCanopy owner=((TileEntityCanopy)tile).owner();
        if(owner.block()==null || !owner.opening() || !owner.draws())return null;
        // Require every constituent and occupied proxy, including all joined leaves.
        int width=owner.block().width;
        for(int x=0;x<owner.width();x+=width){
            BlockPos anchor=owner.getPos().offset(owner.facing().rotateY(),x);
            TileEntity part=craft.view.getTileEntity(anchor);
            if(!(part instanceof TileEntityCanopy))return null;
            TileEntityCanopy member=(TileEntityCanopy)part;
            if(member.constituent()!=member || member.owner()!=owner)return null;
            for(BlockPos pos:member.cells()){
                VehicleStructure.Cell cell=craft.structure.at(pos);
                TileEntity proxy=craft.view.getTileEntity(pos);
                if(cell==null || cell.state.getBlock()!=owner.block()
                        || !(proxy instanceof TileEntityCanopy)
                        || ((TileEntityCanopy)proxy).constituent()!=member)return null;
            }
        }
        return owner;
    }
    public static boolean toggle(EntityGroundVehicle craft,BlockPos hit){
        if(craft.world.isRemote || craft.isDead || craft.ownershipBlocked)return false;
        TileEntityCanopy tile=owner(craft,hit);
        if(tile==null)return false;
        tile.targetOpen=!tile.targetOpen;
        save(craft,false);
        send(craft,tile);
        return true;
    }
    private static void send(EntityGroundVehicle craft,TileEntityCanopy tile){
        VehicleNetwork.Packet packet=new VehicleNetwork.Packet(VehicleNetwork.CANOPIES);
        packet.entity=craft.getEntityId();packet.token=craft.getUniqueID();
        packet.data=ByteBuffer.allocate(13).putLong(tile.getPos().toLong())
                .put((byte)(tile.targetOpen?1:0)).putFloat(tile.progress).array();
        com.vandorlabs.network.PacketHandler.INSTANCE.sendToAllTracking(packet,craft);
        if(craft.getControllingPassenger() instanceof net.minecraft.entity.player.EntityPlayerMP)
            com.vandorlabs.network.PacketHandler.INSTANCE.sendTo(packet,(net.minecraft.entity.player.EntityPlayerMP)craft.getControllingPassenger());
    }
    public static boolean apply(EntityGroundVehicle craft,byte[] bytes){
        if(bytes.length!=13)return false;
        ByteBuffer data=ByteBuffer.wrap(bytes);
        BlockPos pos=BlockPos.fromLong(data.getLong());byte target=data.get();float progress=data.getFloat();
        if(target<0 || target>1 || !Float.isFinite(progress) || progress<0 || progress>1)return false;
        TileEntityCanopy tile=owner(craft,pos);
        if(tile==null || !tile.getPos().equals(pos))return false;
        boolean moved=tile.progress!=progress;
        tile.targetOpen=target==1;tile.previous=tile.progress=progress;
        save(craft,moved);
        return true;
    }
    public static void tick(EntityGroundVehicle craft){
        if(craft.structure==null || craft.view==null || craft.ownershipBlocked)return;
        boolean changed=false;
        List<TileEntityCanopy> settled=new ArrayList<>();
        for(TileEntity raw:craft.view.tiles.values())if(raw instanceof TileEntityCanopy){
            TileEntityCanopy tile=(TileEntityCanopy)raw;
            if(!tile.draws() || tile.block()==null || !tile.opening())continue;
            tile.previous=tile.progress;
            float next=Math.max(0,Math.min(1,tile.progress+(tile.targetOpen?1:-1)/16F));
            if(next==tile.progress)continue;
            tile.progress=next;changed=true;
            if(next==0 || next==1)settled.add(tile);
        }
        if(changed)save(craft,true);
        if(!craft.world.isRemote)for(TileEntityCanopy tile:settled)send(craft,tile);
    }
    private static void save(EntityGroundVehicle craft,boolean moved){
        List<VehicleStructure.Cell> cells=new ArrayList<>();
        for(VehicleStructure.Cell cell:craft.structure.cells){
            TileEntity tile=craft.view.getTileEntity(cell.pos);
            cells.add(tile instanceof TileEntityCanopy
                    ?new VehicleStructure.Cell(cell.pos,cell.state,tile.writeToNBT(new net.minecraft.nbt.NBTTagCompound())):cell);
        }
        craft.updateCanopies(new VehicleStructure(cells,craft.structure.seat,craft.structure.facing,craft.structure.gearCount),moved);
    }
}
