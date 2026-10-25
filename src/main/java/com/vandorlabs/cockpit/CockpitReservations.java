package com.vandorlabs.cockpit;

import com.vandorlabs.tiles.*;
import com.vandorlabs.vehicle.*;
import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.*;
import net.minecraft.world.World;
import java.util.*;

/** Claim the rear seat sweep before movement; release only after the seat returns. */
public final class CockpitReservations {
    private CockpitReservations(){}
    private static final Map<TileEntityCockpit,RearCache> REAR=Collections.synchronizedMap(new WeakHashMap<>());
    private static final class RearCache {final BlockPos pos;final EnumFacing facing;final CockpitData data;final Set<BlockPos> cells;RearCache(TileEntityCockpit tile,EnumFacing facing,Set<BlockPos> cells){pos=tile.getPos();this.facing=facing;data=tile.data();this.cells=Collections.unmodifiableSet(cells);}}

    public static Set<BlockPos> rear(TileEntityCockpit tile,EnumFacing facing){
        RearCache cached=REAR.get(tile);if(cached!=null&&cached.pos.equals(tile.getPos())&&cached.facing==facing&&cached.data==tile.data())return cached.cells;
        Set<BlockPos> cells=new LinkedHashSet<>();
        for(Map.Entry<AxisAlignedBB,Integer> part:tile.data().moving.entrySet())if(part.getValue()==1){
            AxisAlignedBB b=CockpitData.rotate(part.getKey().union(part.getKey().offset(0,0,1)),facing).offset(tile.getPos());
            for(BlockPos p:BlockPos.getAllInBox(new BlockPos(b.minX+1e-6,b.minY+1e-6,b.minZ+1e-6),new BlockPos(b.maxX-1e-6,b.maxY-1e-6,b.maxZ-1e-6)))cells.add(p.toImmutable());
        }
        cells.removeAll(tile.data().cells(tile.getPos(),facing));RearCache next=new RearCache(tile,facing,cells);REAR.put(tile,next);return next.cells;
    }
    public static Set<BlockPos> allCells(TileEntityCockpit tile,EnumFacing facing){Set<BlockPos> cells=new LinkedHashSet<>(tile.data().cells(tile.getPos(),facing));if(tile.design().equals("cockpit"))cells.addAll(rear(tile,facing));return cells;}
    public static boolean clear(TileEntityCockpit tile,EntityPlayer actor,boolean returning){
        World world=tile.getWorld();EntityGroundVehicle craft=world instanceof VehicleWorld?((VehicleWorld)world).craft:null;
        for(BlockPos p:rear(tile,tile.facing())){
            if(!world.isBlockLoaded(p)||p.getY()<0||p.getY()>=world.getHeight())return blocked(tile,actor,"unavailable rear space");
            if(!world.isAirBlock(p)&&CockpitAssembly.root(world,p)!=tile&&!clearCanopy(tile,p))return blocked(tile,actor,"a block behind the seat: "+world.getBlockState(p).getBlock().getLocalizedName());
            if(craft==null&&actor!=null&&(!world.isBlockModifiable(actor,p)||!actor.canPlayerEdit(p,EnumFacing.UP,actor.getHeldItemMainhand())))return blocked(tile,actor,"protected rear space");
        }
        for(Map.Entry<AxisAlignedBB,Integer> part:tile.data().moving.entrySet())if(part.getValue()==1){
            AxisAlignedBB sweep=CockpitData.rotate(part.getKey().union(part.getKey().offset(0,0,1)),tile.facing()).offset(tile.getPos());
            World physical=world;AxisAlignedBB absolute=sweep;
            if(craft!=null){physical=craft.world;absolute=new VehiclePose(craft.posX,craft.posY,craft.posZ,craft.rotationYaw).world(sweep,craft.structure.bounds.maxX/2,craft.structure.bounds.maxZ/2);}
            for(BlockPos p:BlockPos.getAllInBox(new BlockPos(absolute.minX,absolute.minY,absolute.minZ),new BlockPos(absolute.maxX,absolute.maxY,absolute.maxZ)))if(!physical.isBlockLoaded(p))return blocked(tile,actor,"unloaded terrain");
            if(craft!=null&&terrainBlocked(physical,craft,absolute.shrink(1e-5)))return blocked(tile,actor,"terrain in the seat path");
            for(net.minecraft.entity.Entity entity:physical.getEntitiesWithinAABBExcludingEntity(craft,absolute))if(entity.canBeCollidedWith()&&(craft==null||!craft.getPassengers().contains(entity))&&!(entity.getRidingEntity() instanceof com.vandorlabs.entity.EntityChairSeat&&tile.getPos().equals(((com.vandorlabs.entity.EntityChairSeat)entity.getRidingEntity()).getChairPos())))return blocked(tile,actor,"an occupant in the seat path");
        }
        return true;
    }
    private static boolean clearCanopy(TileEntityCockpit tile,BlockPos pos){
        // Match the existing Pilot Seat backrest rule. Upper shell cells share
        // seat space even where conservative trim boxes touch the backrest.
        return pos.getY()==tile.getPos().getY()+1
                && tile.getWorld().getBlockState(pos).getBlock() instanceof com.vandorlabs.blocks.BlockCanopy
                && ((com.vandorlabs.blocks.BlockCanopy)tile.getWorld().getBlockState(pos).getBlock()).supportsSeatBackrest();
    }
    private static boolean terrainBlocked(World world,EntityGroundVehicle craft,AxisAlignedBB box){
        List<AxisAlignedBB> collisions=new ArrayList<>();
        for(BlockPos p:BlockPos.getAllInBox(new BlockPos(box.minX,box.minY,box.minZ),new BlockPos(box.maxX,box.maxY,box.maxZ))){world.getBlockState(p).addCollisionBoxToList(world,p,box,collisions,craft,false);if(!collisions.isEmpty())return true;}return false;
    }
    private static boolean blocked(TileEntityCockpit tile,EntityPlayer actor,String reason){tile.seatBlockReason=reason;if(actor!=null)actor.sendStatusMessage(new net.minecraft.util.text.TextComponentString("Seat movement is blocked by "+reason+"."),true);return false;}
    public static void claim(TileEntityCockpit tile){
        World world=tile.getWorld();if(world instanceof VehicleWorld){VehicleCockpits.reserve(((VehicleWorld)world).craft,tile);return;}
        for(BlockPos p:rear(tile,tile.facing()))if(world.isAirBlock(p)){
            world.setBlockState(p,Block.getBlockFromName("vandorlabs:cockpit_part").getDefaultState(),2);
            TileEntityCockpitPart part=(TileEntityCockpitPart)world.getTileEntity(p);part.anchor=tile.getPos().toImmutable();part.markDirty();world.notifyBlockUpdate(p,world.getBlockState(p),world.getBlockState(p),3);
        }
    }
    public static void tick(TileEntityCockpit tile){
        if(tile.seatSlide.target()||tile.seatSlide.moving(tile.getWorld().getTotalWorldTime())||tile.getWorld() instanceof VehicleWorld||tile.getWorld().getTileEntity(tile.getPos())!=tile)return;
        for(BlockPos p:rear(tile,tile.facing()))if(tile.getWorld().getTileEntity(p) instanceof TileEntityCockpitPart && ((TileEntityCockpitPart)tile.getWorld().getTileEntity(p)).anchor.equals(tile.getPos())){
            tile.getWorld().removeTileEntity(p);tile.getWorld().setBlockState(p,net.minecraft.init.Blocks.AIR.getDefaultState(),3);
        }
    }
}
