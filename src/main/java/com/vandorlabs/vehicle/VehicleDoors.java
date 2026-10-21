package com.vandorlabs.vehicle;

import com.vandorlabs.blocks.*;
import com.vandorlabs.tiles.TileEntityLargeProgrammableDoor;
import net.minecraft.block.Block;
import net.minecraft.block.BlockDoor;
import net.minecraft.block.BlockTrapDoor;
import com.vandorlabs.tiles.TileEntityProgrammableTrapdoor;
import net.minecraft.block.state.IBlockState;
import net.minecraft.util.math.*;
import java.util.*;

/** Server-owned manual door interaction, without ticking foreign tiles or redstone networks. */
public final class VehicleDoors {
    private static boolean malisis(net.minecraft.tileentity.TileEntity tile) {
        if(tile==null)return false;
        String name=tile.getClass().getName();
        return name.equals("net.malisis.doors.tileentity.DoorTileEntity") || name.equals("net.malisis.doors.tileentity.TrapDoorTileEntity");
    }
    public static boolean supports(Block b){return b instanceof BlockDoor || b instanceof BlockVandorDoor || b instanceof BlockTrapDoor;}
    public static boolean toggle(EntityGroundVehicle craft,BlockPos hit) {return change(craft,hit,null);}
    public static boolean securitySet(EntityGroundVehicle craft,BlockPos hit,boolean open){return change(craft,hit,open);}
    private static boolean change(EntityGroundVehicle craft,BlockPos hit,Boolean forced) {
        if(craft.world.isRemote || craft.view==null || craft.structure==null || craft.isDead)return false;
        IBlockState state=craft.view.getBlockState(hit);Block block=state.getBlock();if(!supports(block))return false;
        Set<BlockPos> group=new HashSet<>();
        if(block instanceof BlockTrapDoor) {
            if(block instanceof BlockProgrammableTrapdoor) {
                if(!(craft.view.getTileEntity(hit) instanceof TileEntityProgrammableTrapdoor))return false;
                TileEntityProgrammableTrapdoor tile=(TileEntityProgrammableTrapdoor)craft.view.getTileEntity(hit);
                for(TileEntityProgrammableTrapdoor leaf:tile.group())group.add(leaf.getPos());
                // Reject broken saved links instead of silently toggling a surviving leaf.
                for(BlockPos member:new ArrayList<>(group)) {
                    net.minecraft.nbt.NBTTagCompound data=craft.structure.at(member)==null?null:craft.structure.at(member).tileData();
                    if(data==null)return false;
                    if(data.hasKey("TrapdoorPartner",4) && !group.contains(BlockPos.fromLong(data.getLong("TrapdoorPartner"))))return false;
                    if(data.hasKey("TrapdoorSquare",4) && group.size()!=4)return false;
                    net.minecraft.nbt.NBTTagList saved=data.getTagList("TrapdoorAssembly",4);
                    if(saved.tagCount()>0 && saved.tagCount()!=group.size())return false;
                    for(int n=0;n<saved.tagCount();n++)if(!group.contains(BlockPos.fromLong(((net.minecraft.nbt.NBTTagLong)saved.get(n)).getLong())))return false;
                }
            } else group.add(hit);
            if(group.isEmpty() || group.size()>64)return false;
        } else if(block instanceof BlockLargeProgrammableDoor) {
            TileEntityLargeProgrammableDoor root=((BlockLargeProgrammableDoor)block).root(craft.view,hit);if(root==null)return false;
            for(VehicleStructure.Cell c:craft.structure.cells)if(c.state.getBlock()==block && craft.view.getTileEntity(c.pos) instanceof TileEntityLargeProgrammableDoor
                    && ((TileEntityLargeProgrammableDoor)craft.view.getTileEntity(c.pos)).anchorPos().equals(root.getPos()))group.add(c.pos);
        } else {
            BlockPos lower=state.getValue(BlockDoor.HALF)==BlockDoor.EnumDoorHalf.UPPER?hit.down():hit;
            group.add(lower);group.add(lower.up());
            if(block instanceof BlockVandorDoor) {
                IBlockState actual=block.getActualState(craft.view.getBlockState(lower),craft.view,lower);
                for(net.minecraft.util.EnumFacing side:new net.minecraft.util.EnumFacing[]{actual.getValue(BlockDoor.FACING).rotateY(),actual.getValue(BlockDoor.FACING).rotateYCCW()}) {
                    BlockPos adjacent=lower.offset(side);IBlockState other=craft.view.getBlockState(adjacent);
                    if(other.getBlock()==block && other.getValue(BlockDoor.HALF)==BlockDoor.EnumDoorHalf.LOWER) {
                        other=block.getActualState(other,craft.view,adjacent);
                        if(other.getValue(BlockDoor.FACING)==actual.getValue(BlockDoor.FACING) && other.getValue(BlockDoor.HINGE)!=actual.getValue(BlockDoor.HINGE)){group.add(adjacent);group.add(adjacent.up());}
                    }
                }
            }
        }
        if(!(block instanceof BlockTrapDoor) && (block instanceof BlockLargeProgrammableDoor ? group.size()!=9 : group.size()!=2 && group.size()!=4))return false;
        for(BlockPos position:group) {
            VehicleStructure.Cell cell=craft.structure.at(position);
            if(cell==null || cell.state.getBlock()!=block)return false;
        }
        if(forced==null)for(BlockPos position:group){net.minecraft.tileentity.TileEntity tile=craft.view.getTileEntity(position);if(tile instanceof com.vandorlabs.tiles.TileEntitySpaceDoor&&((com.vandorlabs.tiles.TileEntitySpaceDoor)tile).securityMode()>0)return true;}
        boolean open=forced==null?!block.getActualState(state,craft.view,hit).getValue(BlockDoor.OPEN):forced;
        if(forced!=null){boolean different=false;for(BlockPos position:group)different|=craft.view.getBlockState(position).getValue(BlockDoor.OPEN)!=open;if(!different)return true;}
        if(!open&&craft.view.getTileEntity(hit) instanceof TileEntityProgrammableTrapdoor&&com.vandorlabs.tiles.LandingGearCovers.needsOpen((TileEntityProgrammableTrapdoor)craft.view.getTileEntity(hit)))return false;
        Map<BlockPos,Boolean> desired=new LinkedHashMap<>();for(BlockPos position:group)desired.put(position,open);return set(craft,desired);
    }
    /** Publish a checked group update through the same snapshot/network path as a manual click. */
    public static boolean set(EntityGroundVehicle craft,Map<BlockPos,Boolean> desired){
        if(craft.world.isRemote||desired.isEmpty()||desired.size()>64)return false;
        craft.signals.persist();java.nio.ByteBuffer patch=java.nio.ByteBuffer.allocate(desired.size()*9);
        for(Map.Entry<BlockPos,Boolean> entry:desired.entrySet()){patch.putLong(entry.getKey().toLong());patch.put((byte)(entry.getValue()?1:0));}
        if(!apply(craft,patch.array()))return false;
        VehicleNetwork.Packet packet=new VehicleNetwork.Packet(VehicleNetwork.DOORS);packet.entity=craft.getEntityId();packet.token=craft.getUniqueID();packet.data=patch.array();
        com.vandorlabs.network.PacketHandler.INSTANCE.sendToAllTracking(packet,craft);
        if(craft.getControllingPassenger() instanceof net.minecraft.entity.player.EntityPlayerMP)
            com.vandorlabs.network.PacketHandler.INSTANCE.sendTo(packet,(net.minecraft.entity.player.EntityPlayerMP)craft.getControllingPassenger());
        return true;
    }
    public static boolean apply(EntityGroundVehicle craft,byte[] bytes) {
        if(craft.structure==null || bytes.length==0 || bytes.length>64*9 || bytes.length%9!=0)return false;
        Map<BlockPos,Boolean> states=new HashMap<>();java.nio.ByteBuffer patch=java.nio.ByteBuffer.wrap(bytes);
        while(patch.hasRemaining()) {
            BlockPos position=BlockPos.fromLong(patch.getLong());byte value=patch.get();boolean open=value!=0;
            VehicleStructure.Cell cell=craft.structure.at(position);
            if(value<0 || value>1 || states.containsKey(position) || cell==null || !supports(cell.state.getBlock()))return false;
            states.put(position,open);
        }
        boolean different=false;
        for(Map.Entry<BlockPos,Boolean> entry:states.entrySet())
            different|=!craft.structure.at(entry.getKey()).state.getValue(BlockDoor.OPEN).equals(entry.getValue());
        if(!different)return true;
        List<VehicleStructure.Cell> changed=new ArrayList<>();
        for(VehicleStructure.Cell cell:craft.structure.cells) {
            net.minecraft.nbt.NBTTagCompound data=cell.tileData();
            if(states.containsKey(cell.pos) && data!=null && malisis(craft.view.getTileEntity(cell.pos)))
                // Malisis renders its saved stable state separately from the block's OPEN property.
                data.setInteger("state",states.get(cell.pos)?2:0);
            changed.add(new VehicleStructure.Cell(cell.pos,
                    states.containsKey(cell.pos)?cell.state.withProperty(BlockDoor.OPEN,states.get(cell.pos)):cell.state,data));
        }
        craft.install(new VehicleStructure(changed,craft.structure.seat,craft.structure.facing,craft.structure.gearCount));
        for(BlockPos position:states.keySet())if(malisis(craft.view.getTileEntity(position)))
            craft.view.getTileEntity(position).readFromNBT(craft.structure.at(position).tileData());
        return true;
    }
}
