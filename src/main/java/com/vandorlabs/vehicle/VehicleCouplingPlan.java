package com.vandorlabs.vehicle;

import com.vandorlabs.coupler.*;
import java.util.*;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.*;
import net.minecraft.world.WorldServer;

/** A complete, immutable preflight for one world-section ownership change. */
public final class VehicleCouplingPlan {
    public final EntityGroundVehicle craft;
    public final VehicleStructure before,after,fragment;
    public final BlockPos fragmentOrigin,primary,afterPrimary;
    public final boolean attaching;
    public final double x,y,z,beforeX,beforeY,beforeZ;
    public final float beforeYaw;
    public final VehicleWorld afterView;
    public final VehicleCollision afterCollision;
    public final UUID actor;
    private final Set<BlockPos> airBoundary;
    private VehicleCouplingPlan(EntityGroundVehicle craft,EntityPlayerMP actor,VehicleStructure before,List<VehicleStructure.Cell> cells,int supports,boolean requiredSupports,VehicleStructure fragment,BlockPos origin,boolean attach,BlockPos primary,Set<BlockPos> boundary)throws Exception{
        this.craft=craft;this.actor=actor.getUniqueID();this.before=before;this.fragment=fragment;fragmentOrigin=origin;attaching=attach;this.primary=primary;airBoundary=new HashSet<>(boundary);
        beforeX=craft.posX;beforeY=craft.posY;beforeZ=craft.posZ;beforeYaw=craft.rotationYaw;
        int minX=0,minY=0,minZ=0;for(VehicleStructure.Cell cell:cells){minX=Math.min(minX,cell.pos.getX());minY=Math.min(minY,cell.pos.getY());minZ=Math.min(minZ,cell.pos.getZ());}
        BlockPos shift=new BlockPos(-minX,-minY,-minZ);List<VehicleStructure.Cell> normalized=new ArrayList<>();int controllers=0,autopilots=0;
        for(VehicleStructure.Cell cell:cells){if(cell.state.getBlock() instanceof com.vandorlabs.blocks.BlockVehicleController)controllers++;if(cell.tileData()!=null&&cell.tileData().getString("id").equals("vandorlabs:autopilot"))autopilots++;normalized.add(new VehicleStructure.Cell(cell.pos.add(shift),cell.state,cell.hasTileData()?VehicleTileData.translated(cell.tileData(),shift):null));}
        require(controllers==1,"Keep exactly one Vehicle Controller in the retained vehicle");require(autopilots<=1,"A joined vehicle can contain at most one Autopilot");require(cells.size()<=com.vandorlabs.config.BlockAvailability.vehicleMaxBlocks(),"Joined vehicle exceeds the configured block limit");
        after=new VehicleStructure(normalized,before.seat.add(shift),before.facing,supports);BlockPos selected=primary.add(shift);if(after.at(selected)==null){VehicleStructure.Cell old=before.at(primary);if(old!=null&&old.state.getBlock() instanceof BlockCoupler)selected=primary.offset(old.state.getValue(BlockCoupler.FACING)).add(shift);}afterPrimary=selected;afterView=new VehicleWorld(craft.world,after);MovingBlockSupport.validate(after,afterView);after.encode();afterCollision=new VehicleCollision(afterView);
        require(requiredSupports,"Keep at least three supports or two Track blocks on the retained vehicle");VehicleFlight.requireThruster(after);require(!craft.hover||VehicleFlight.canHover(after),"Keep three lift blocks on the retained Hover vehicle");require(craft.hover||!after.hasNonWheeledGear||after.hasSkisOnly,"The retained Taxi vehicle needs wheels or skis");
        double oldX=before.bounds.maxX/2,oldZ=before.bounds.maxZ/2,newX=after.bounds.maxX/2,newZ=after.bounds.maxZ/2,a=Math.toRadians(beforeYaw),c=Math.cos(a),s=Math.sin(a),dx=newX-oldX-shift.getX(),dz=newZ-oldZ-shift.getZ();
        x=beforeX+oldX-newX+c*dx-s*dz;y=beforeY-shift.getY();z=beforeZ+oldZ-newZ+s*dx+c*dz;
        validate(actor);
    }
    public static void require(boolean ok,String error){if(!ok)throw new IllegalArgumentException(error);}
    public static final class Alignment {
        public final int turns;public final BlockPos origin;public final VehicleStructure placed;
        Alignment(EntityGroundVehicle craft,VehicleStructure source){this(craft,source,true);}
        Alignment(EntityGroundVehicle craft,VehicleStructure source,boolean exact){
            require(craft.stopped(),"Stop the vehicle before coupling or detaching");turns=VehicleRotation.turns(craft.rotationYaw);placed=VehicleRotation.rotate(source,craft.view,turns);
            double cx=craft.posX+source.bounds.maxX/2,cz=craft.posZ+source.bounds.maxZ/2;origin=new BlockPos(Math.round(cx-placed.bounds.maxX/2),Math.round(craft.posY),Math.round(cz-placed.bounds.maxZ/2));
            double angle=Math.toRadians(turns*90),c=Math.cos(angle),s=Math.sin(angle);
            if(exact)for(int n=0;n<8;n++){Vec3d local=new Vec3d((n&1)==0?0:source.bounds.maxX,(n&2)==0?0:source.bounds.maxY,(n&4)==0?0:source.bounds.maxZ);Vec3d actual=craft.toWorld(local);double lx=local.x-source.bounds.maxX/2,lz=local.z-source.bounds.maxZ/2;Vec3d snapped=new Vec3d(origin.getX()+placed.bounds.maxX/2+c*lx-s*lz,origin.getY()+local.y,origin.getZ()+placed.bounds.maxZ/2+s*lx+c*lz);require(actual.squareDistanceTo(snapped)<=.075*.075,"Align the stopped vehicle precisely to the block grid before coupling");}
        }
        public BlockPos worldCell(VehicleStructure source,BlockPos local){return origin.add(VehicleRotation.cell(local,(int)source.bounds.maxX,(int)source.bounds.maxZ,turns));}
    }
    public static VehicleCouplingPlan attach(EntityGroundVehicle craft,EntityPlayerMP actor,BlockPos primary,VehicleScan scan)throws Exception{
        craft.signals.persist();VehicleStructure before=craft.structure;Alignment alignment=new Alignment(craft,before);require(scan.result!=null&&scan.result.displayOnly,"The complete world section has not been scanned");
        VehicleStructure rotated=VehicleRotation.rotate(scan.result,new VehicleWorld(craft.world,scan.result),-alignment.turns);
        double lx=Double.POSITIVE_INFINITY,ly=lx,lz=lx;for(int n=0;n<8;n++){Vec3d corner=new Vec3d(scan.origin.getX()+((n&1)==0?0:scan.result.bounds.maxX),scan.origin.getY()+((n&2)==0?0:scan.result.bounds.maxY),scan.origin.getZ()+((n&4)==0?0:scan.result.bounds.maxZ));Vec3d local=craft.toLocal(corner);lx=Math.min(lx,local.x);ly=Math.min(ly,local.y);lz=Math.min(lz,local.z);}
        BlockPos localOrigin=new BlockPos(Math.round(lx),Math.round(ly),Math.round(lz));List<VehicleStructure.Cell> cells=new ArrayList<>(before.cells);Set<BlockPos> positions=new HashSet<>();for(VehicleStructure.Cell cell:before.cells)positions.add(cell.pos);
        for(VehicleStructure.Cell cell:rotated.cells){BlockPos at=localOrigin.add(cell.pos);require(positions.add(at),"The world section overlaps the existing vehicle");cells.add(new VehicleStructure.Cell(at,cell.state,cell.hasTileData()?VehicleTileData.translated(cell.tileData(),localOrigin):null));}
        return new VehicleCouplingPlan(craft,actor,before,cells,before.gearCount+scan.supportCount(),true,scan.result,scan.origin,true,primary,scan.airBoundary);
    }
    /** A scan of the controller component on a temporary view with the proposed coupling cuts. */
    public static VehicleCouplingPlan detach(EntityGroundVehicle craft,EntityPlayerMP actor,BlockPos primary,VehicleStructure proposed,VehicleScan retained)throws Exception{
        craft.signals.persist();VehicleStructure before=craft.structure;Alignment alignment=new Alignment(craft,before,false);require(retained.result!=null,"The retained vehicle component has not been scanned");Set<BlockPos> keep=new HashSet<>();for(VehicleStructure.Cell cell:retained.result.cells)keep.add(retained.origin.add(cell.pos));require(keep.contains(before.seat),"Keep the main Pilot Seat with its Vehicle Controller");
        List<VehicleStructure.Cell> kept=new ArrayList<>(),removed=new ArrayList<>();for(VehicleStructure.Cell cell:proposed.cells)(keep.contains(cell.pos)?kept:removed).add(cell);require(!removed.isEmpty(),"The released section remains attached through another block or coupler");
        for(Entity passenger:craft.getPassengers())require(keep.contains(craft.seatForPassenger(passenger)),"Empty the detached section's seats before releasing it");
        List<VehicleStructure.Cell> worldCells=new ArrayList<>();
        // Rotate using the complete original hull dimensions so section coordinates retain their world placement.
        VehicleStructure full=VehicleRotation.rotate(proposed,new VehicleWorld(craft.world,proposed),alignment.turns);for(VehicleStructure.Cell cell:removed){BlockPos p=VehicleRotation.cell(cell.pos,(int)proposed.bounds.maxX,(int)proposed.bounds.maxZ,alignment.turns);worldCells.add(full.at(p));}
        int mx=Integer.MAX_VALUE,my=mx,mz=mx;for(VehicleStructure.Cell cell:worldCells){mx=Math.min(mx,cell.pos.getX());my=Math.min(my,cell.pos.getY());mz=Math.min(mz,cell.pos.getZ());}BlockPos min=new BlockPos(mx,my,mz);List<VehicleStructure.Cell> normalized=new ArrayList<>();for(VehicleStructure.Cell cell:worldCells)normalized.add(new VehicleStructure.Cell(cell.pos.subtract(min),cell.state,cell.hasTileData()?VehicleTileData.translated(cell.tileData(),BlockPos.ORIGIN.subtract(min)):null));
        return new VehicleCouplingPlan(craft,actor,before,kept,retained.supportCount(),retained.hasRequiredSupports(),VehicleStructure.display(normalized),alignment.origin.add(min),false,primary,Collections.emptySet());
    }
    public void validate(EntityPlayerMP player)throws Exception{
        WorldServer world=(WorldServer)craft.world;require(player!=null&&player.getUniqueID().equals(actor)&&player.world==world&&!player.isDead&&!player.isSpectator()&&player.connection!=null&&player.connection.netManager.isChannelOpen(),"The authorizing player must remain online and alive in this dimension");require(!craft.isDead&&craft.structure!=null&&!craft.ownershipBlocked,"Vehicle ownership is unavailable");
        require(craft.structure.write().equals(before.write()),"Vehicle blocks, settings or stored contents changed before coupling");require(Math.abs(craft.posX-beforeX)<1e-6&&Math.abs(craft.posY-beforeY)<1e-6&&Math.abs(craft.posZ-beforeZ)<1e-6&&Math.abs(MathHelper.wrapDegrees(craft.rotationYaw-beforeYaw))<1e-5,"Vehicle pose changed before coupling");
        require(afterCollision.loaded(world,x,y,z,0,0,0,beforeYaw),"Load the complete joined vehicle and its boundary inside the world");
        for(VehicleStructure.Cell cell:fragment.cells){BlockPos at=fragmentOrigin.add(cell.pos);VehicleService.permission(player,at);if(attaching){require(world.getBlockState(at)==cell.state,"World section changed before coupling");require(!net.minecraftforge.common.MinecraftForge.EVENT_BUS.post(new net.minecraftforge.event.world.BlockEvent.BreakEvent(world,at,cell.state,player)),"A protection rule prevents coupling this section");}else require(world.isAirBlock(at)&&world.getTileEntity(at)==null,"Detaching is blocked at "+at.getX()+", "+at.getY()+", "+at.getZ());}
        if(attaching){require(fragment.matches(world,fragmentOrigin),"World section contents changed before coupling");for(BlockPos at:airBoundary)require(world.isBlockLoaded(at)&&world.isAirBlock(at),"World section connections changed before coupling");}
        VehicleWorld fragmentView=new VehicleWorld(world,fragment);fragmentView.origin=fragmentOrigin;MovingBlockSupport.validate(fragment,fragmentView);VehicleCollision geometry=new VehicleCollision(fragmentView);AxisAlignedBB area=fragment.bounds.offset(fragmentOrigin).grow(.01);
        for(Entity entity:world.getEntitiesWithinAABBExcludingEntity(craft,area))require(entity.isDead,"Clear entities and occupied seats from the coupled section");
        for(EntityGroundVehicle other:VehicleLookup.near(world,area))require(other==craft,"Move other vehicle entities clear; coupling world blocks does not merge vehicles");
        if(!attaching){VehiclePose retainedPose=new VehiclePose(x,y,z,beforeYaw);for(AxisAlignedBB part:geometry.boxes){AxisAlignedBB actual=part.offset(fragmentOrigin);require(world.getCollisionBoxes(craft,actual.shrink(.001)).isEmpty(),"Detached section would collide with terrain or another mechanism");for(AxisAlignedBB box:afterCollision.boxes)require(!retainedPose.world(box,after.bounds.maxX/2,after.bounds.maxZ/2).shrink(.001).intersects(actual.shrink(.001)),"Align the detached section clear of the retained hull");}}
    }
}
