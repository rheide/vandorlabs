package com.vandorlabs.vehicle;

import com.vandorlabs.blocks.*;
import com.vandorlabs.tiles.TileEntityLargeProgrammableDoor;
import net.minecraft.block.Block;
import net.minecraft.block.BlockDoor;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.math.*;
import java.util.*;

/** Server-owned manual door interaction, without ticking foreign tiles or redstone networks. */
public final class VehicleDoors {
    private static boolean door(Block b){return b instanceof BlockDoor || b instanceof BlockVandorDoor;}
    public static boolean interact(EntityGroundVehicle craft,EntityPlayer player) {
        if(craft.view==null)return false;
        Vec3d start=craft.toLocal(player.getPositionEyes(1)),end=craft.toLocal(player.getPositionEyes(1).add(player.getLook(1).scale(6)));
        RayTraceResult nearest=null;double distance=Double.MAX_VALUE;
        // Pick the first actual block surface; hull cells occlude doors behind them.
        for(VehicleStructure.Cell cell:craft.structure.cells) {
            try {
                RayTraceResult hit=craft.view.getBlockState(cell.pos).collisionRayTrace(craft.view,cell.pos,start,end);
                if(hit!=null && start.squareDistanceTo(hit.hitVec)<distance){nearest=hit;distance=start.squareDistanceTo(hit.hitVec);}
            }catch(RuntimeException | LinkageError error){VehicleCompatibility.warn(cell.state.getBlock(),"selection",error);}
        }
        if(nearest==null || !door(craft.view.getBlockState(nearest.getBlockPos()).getBlock()))return false;
        if(!craft.world.isRemote && !player.isSpectator())toggle(craft,nearest.getBlockPos());
        return true;
    }
    public static boolean toggle(EntityGroundVehicle craft,BlockPos hit) {
        IBlockState state=craft.view.getBlockState(hit);Block block=state.getBlock();if(!door(block))return false;
        Set<BlockPos> group=new HashSet<>();
        if(block instanceof BlockLargeProgrammableDoor) {
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
        boolean open=!block.getActualState(state,craft.view,hit).getValue(BlockDoor.OPEN);
        java.nio.ByteBuffer patch=java.nio.ByteBuffer.allocate(group.size()*9);
        for(BlockPos position:group){patch.putLong(position.toLong());patch.put((byte)(open?1:0));}
        apply(craft,patch.array());
        VehicleNetwork.Packet packet=new VehicleNetwork.Packet(VehicleNetwork.DOORS);packet.entity=craft.getEntityId();packet.token=craft.getUniqueID();packet.data=patch.array();
        com.vandorlabs.network.PacketHandler.INSTANCE.sendToAllTracking(packet,craft);
        return true;
    }
    public static void apply(EntityGroundVehicle craft,byte[] bytes) {
        if(bytes.length==0 || bytes.length>18*9 || bytes.length%9!=0)return;
        Map<BlockPos,Boolean> states=new HashMap<>();java.nio.ByteBuffer patch=java.nio.ByteBuffer.wrap(bytes);
        while(patch.hasRemaining()) {
            BlockPos position=BlockPos.fromLong(patch.getLong());boolean open=patch.get()!=0;
            VehicleStructure.Cell cell=craft.structure.at(position);if(cell==null || !door(cell.state.getBlock()))return;
            states.put(position,open);
        }
        List<VehicleStructure.Cell> changed=new ArrayList<>();
        for(VehicleStructure.Cell cell:craft.structure.cells)changed.add(new VehicleStructure.Cell(cell.pos,
                states.containsKey(cell.pos)?cell.state.withProperty(BlockDoor.OPEN,states.get(cell.pos)):cell.state,cell.tileData()));
        craft.install(new VehicleStructure(changed,craft.structure.seat,craft.structure.facing,craft.structure.gearCount));
    }
}
