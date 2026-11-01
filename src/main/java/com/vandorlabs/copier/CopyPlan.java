package com.vandorlabs.copier;

import com.vandorlabs.surface.SquareLayout;
import com.vandorlabs.turntable.TurntableBoundaries;
import com.vandorlabs.vehicle.*;
import java.util.*;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.*;
import net.minecraft.world.WorldServer;

/** Immutable full-volume preflight; source cells and inventory contents are never sampled or approximated. */
public final class CopyPlan {
    public final VehicleStructure original,result;
    public final BlockPos sourceOrigin,targetOrigin,sourceMax,targetMax;
    public final CopySupply supply;
    public final List<CopyMaterials.Need> materials;
    private CopyPlan(VehicleStructure source,VehicleStructure result,BlockPos sourceOrigin,BlockPos targetOrigin,BlockPos sourceMax,BlockPos targetMax,CopySupply supply,List<CopyMaterials.Need> needs){original=source;this.result=result;this.sourceOrigin=sourceOrigin;this.targetOrigin=targetOrigin;this.sourceMax=sourceMax;this.targetMax=targetMax;this.supply=supply;materials=needs;}
    public static BlockPos[] volume(SquareLayout area,int height){BlockPos a=area.cell(0,0,1),b=area.cell(area.size-1,area.size-1,height);return new BlockPos[]{new BlockPos(Math.min(a.getX(),b.getX()),Math.min(a.getY(),b.getY()),Math.min(a.getZ(),b.getZ())),new BlockPos(Math.max(a.getX(),b.getX()),Math.max(a.getY(),b.getY()),Math.max(a.getZ(),b.getZ()))};}
    public static CopyPlan prepare(CopyControllerTile owner,EntityPlayerMP actor)throws Exception{
        WorldServer world=(WorldServer)owner.getWorld();authorize(world,actor);if(owner.input==null||owner.output==null)throw new IllegalArgumentException("Complete both square surfaces first");
        BlockPos[] from=volume(owner.input,owner.height),to=volume(owner.output,owner.height);
        if(new AxisAlignedBB(from[0].getX(),from[0].getY(),from[0].getZ(),from[1].getX()+1,from[1].getY()+1,from[1].getZ()+1).intersects(new AxisAlignedBB(to[0].getX(),to[0].getY(),to[0].getZ(),to[1].getX()+1,to[1].getY()+1,to[1].getZ()+1)))throw new IllegalArgumentException("Input and output scan volumes overlap");
        loaded(world,from);loaded(world,to);TurntableBoundaries boundaries=new TurntableBoundaries(world,from[0],from[1]);List<VehicleStructure.Cell> cells=new ArrayList<>();
        for(BlockPos cursor:BlockPos.getAllInBox(from[0],from[1])){
            BlockPos at=cursor.toImmutable();VehicleService.permission(actor,at);IBlockState state=world.getBlockState(at);if(state.getBlock().isAir(state,world,at))continue;
            if(at.equals(owner.getPos())||at.equals(owner.chest()))throw new IllegalArgumentException("Keep the controller and material chest outside the input volume");
            if(net.minecraftforge.common.MinecraftForge.EVENT_BUS.post(new net.minecraftforge.event.world.BlockEvent.BreakEvent(world,at,state,actor)))throw new IllegalArgumentException("A protection rule prevents reading the input at "+coordinates(at));
            TileEntity tile=world.getTileEntity(at);boundaries.check(at,state,tile);
            if(tile instanceof CopyControllerTile&&(((CopyControllerTile)tile).input!=null||((CopyControllerTile)tile).output!=null||((CopyControllerTile)tile).busy()))throw new IllegalArgumentException("Disconnect other Copy Controllers from their surfaces before copying them");
            NBTTagCompound data=tile==null?null:VehicleTileData.translated(tile.writeToNBT(new NBTTagCompound()),BlockPos.ORIGIN.subtract(from[0]));
            cells.add(new VehicleStructure.Cell(at.subtract(from[0]),state,data));
        }
        if(cells.isEmpty())throw new IllegalArgumentException("The input area contains no blocks");
        VehicleStructure source=VehicleStructure.display(cells);VehicleWorld view=new VehicleWorld(world,source);view.origin=from[0];MovingBlockSupport.validate(source,view);
        for(VehicleStructure.Cell cell:source.cells)if(com.vandorlabs.vehicle.BuildingBlockCompat.furniture(view.getTileEntity(cell.pos)))throw new IllegalArgumentException("Furniture tile data can move as vehicles; copying these tiles needs a separate material adapter");
        for(VehicleStructure.Cell cell:source.cells)if(MekanismTankCompat.tank(view.getTileEntity(cell.pos)))throw new IllegalArgumentException("Mekanism Dynamic Tanks can be moved as vehicles; copying their shared inventory is not supported");
        VehicleStructure result=CopyChannels.increment(source,owner.channelIncrement);VehicleWorld copied=new VehicleWorld(world,result);copied.origin=to[0];MovingBlockSupport.validate(result,copied);
        empty(world,to,actor);List<CopyMaterials.Need> needs=CopyMaterials.forSnapshot(source,view,actor.capabilities.isCreativeMode);
        CopySupply supply=actor.capabilities.isCreativeMode?null:CopySupply.prepare(owner,actor,needs);
        result.encode(); // Bound the complete nested NBT before allocating a transaction journal.
        return new CopyPlan(source,result,from[0],to[0],from[1],to[1],supply,needs);
    }
    public void validate(WorldServer world,EntityPlayerMP actor){authorize(world,actor);loaded(world,new BlockPos[]{sourceOrigin,sourceMax});loaded(world,new BlockPos[]{targetOrigin,targetMax});if(!original.matchesExact(world,sourceOrigin))throw new IllegalArgumentException("Input blocks or stored contents changed; copy again");empty(world,new BlockPos[]{targetOrigin,targetMax},actor);if(supply!=null)supply.validate(world);}
    private static void authorize(WorldServer world,EntityPlayerMP actor){if(actor==null||actor.world!=world||actor.isDead||actor.isSpectator())throw new IllegalArgumentException("The configuring player must remain alive in this dimension to authorize the copy");}
    private static void loaded(WorldServer world,BlockPos[] box){if(box[0].getY()<0||box[1].getY()>=world.getHeight()||!world.getWorldBorder().contains(box[0])||!world.getWorldBorder().contains(box[1])||!world.isAreaLoaded(box[0].add(-1,-1,-1),box[1].add(1,1,1),false))throw new IllegalArgumentException("Load the complete copy areas and their boundaries inside the world");}
    private static void empty(WorldServer world,BlockPos[] box,EntityPlayerMP actor){for(BlockPos at:BlockPos.getAllInBox(box[0],box[1])){VehicleService.permission(actor,at);if(!world.isAirBlock(at)||world.getTileEntity(at)!=null)throw new IllegalArgumentException("Output area is occupied at "+coordinates(at));}AxisAlignedBB bounds=new AxisAlignedBB(box[0].getX(),box[0].getY(),box[0].getZ(),box[1].getX()+1,box[1].getY()+1,box[1].getZ()+1);for(net.minecraft.entity.Entity entity:world.getEntitiesWithinAABBExcludingEntity(null,bounds))if(!entity.isDead)throw new IllegalArgumentException("Clear players and entities from the output area");}
    private static String coordinates(BlockPos p){return p.getX()+", "+p.getY()+", "+p.getZ();}
}
