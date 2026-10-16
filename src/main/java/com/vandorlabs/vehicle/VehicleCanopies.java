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
    public static void forget(EntityGroundVehicle craft){corrections.remove(craft);}
    private static final Map<EntityGroundVehicle,Map<BlockPos,Float>> corrections=Collections.synchronizedMap(new WeakHashMap<EntityGroundVehicle,Map<BlockPos,Float>>());
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
            for(BlockPos pos:member.reservedCells()){
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
        tile.targetOpen=target==1;
        Map<BlockPos,Float> desired=corrections.get(craft);if(desired==null)desired=new LinkedHashMap<>();else desired=new LinkedHashMap<>(desired);
        desired.put(pos,progress);
        boolean correctionMoves=false;for(Map.Entry<BlockPos,Float> entry:desired.entrySet()){TileEntityCanopy member=owner(craft,entry.getKey());if(member!=null&&member.progress!=entry.getValue())correctionMoves=true;}
        if(correctionMoves&&!craft.collisionCache.readyForCanopyPose(craft.view,desired)){
            corrections.put(craft,desired);save(craft,false);return true;
        }
        corrections.remove(craft);boolean moved=false;
        for(Map.Entry<BlockPos,Float> correction:desired.entrySet()){TileEntityCanopy member=owner(craft,correction.getKey());if(member!=null){moved|=member.progress!=correction.getValue();member.previous=member.progress=correction.getValue();}}
        save(craft,moved);
        return true;
    }
    public static void tick(EntityGroundVehicle craft){
        if(craft.structure==null || craft.view==null || craft.ownershipBlocked)return;
        for(TileEntity raw:craft.view.tiles.values())if(raw instanceof TileEntityCanopy)((TileEntityCanopy)raw).previous=((TileEntityCanopy)raw).progress;
        Map<BlockPos,Float> correction=corrections.get(craft);
        if(correction!=null){
            if(!craft.collisionCache.readyForCanopyPose(craft.view,correction))return;
            boolean corrected=false;for(Map.Entry<BlockPos,Float> entry:correction.entrySet()){TileEntityCanopy member=owner(craft,entry.getKey());if(member!=null){corrected|=member.progress!=entry.getValue();member.previous=member.progress=entry.getValue();}}
            corrections.remove(craft);if(corrected)save(craft,true);
        }
        boolean anyMoving=false;for(TileEntity raw:craft.view.tiles.values())if(raw instanceof TileEntityCanopy){TileEntityCanopy tile=(TileEntityCanopy)raw;if(tile.draws()&&tile.block()!=null&&tile.opening()&&tile.progress!=(tile.targetOpen?1:0)){anyMoving=true;break;}}
        if(anyMoving&&!craft.collisionCache.readyForCanopyStep(craft.view))return;
        boolean changed=false;int moving=0;
        List<TileEntityCanopy> settled=new ArrayList<>();
        for(TileEntity raw:craft.view.tiles.values())if(raw instanceof TileEntityCanopy){
            TileEntityCanopy tile=(TileEntityCanopy)raw;
            if(!tile.draws() || tile.block()==null || !tile.opening())continue;
            tile.previous=tile.progress;
            float next=Math.max(0,Math.min(1,tile.progress+(tile.targetOpen?1:-1)/16F));
            if(next==tile.progress)continue;
            tile.progress=next;changed=true;moving++;
            if(next==0 || next==1)settled.add(tile);
        }
        if(VehicleAnimationDiagnostics.start(craft)!=0)VehicleAnimationDiagnostics.value((craft.world.isRemote?"client/":"server/")+"moving-canopies-count",moving);
        if(changed)save(craft,true);
        if(!craft.world.isRemote)for(TileEntityCanopy tile:settled)send(craft,tile);
    }
    private static void save(EntityGroundVehicle craft,boolean moved){
        long started=VehicleAnimationDiagnostics.start(craft);
        List<VehicleStructure.Cell> cells=new ArrayList<>();
        for(VehicleStructure.Cell cell:craft.structure.cells){
            TileEntity tile=craft.view.getTileEntity(cell.pos);
            cells.add(tile instanceof TileEntityCanopy
                    ?new VehicleStructure.Cell(cell.pos,cell.state,tile.writeToNBT(new net.minecraft.nbt.NBTTagCompound())):cell);
        }
        VehicleAnimationDiagnostics.elapsed(craft,"canopy-NBT-and-cells",started);
        started=VehicleAnimationDiagnostics.start(craft);
        VehicleStructure snapshot=new VehicleStructure(cells,craft.structure.seat,craft.structure.facing,craft.structure.gearCount);
        VehicleAnimationDiagnostics.elapsed(craft,"canopy-snapshot-constructor",started);
        craft.updateCanopies(snapshot,moved);
    }
}
