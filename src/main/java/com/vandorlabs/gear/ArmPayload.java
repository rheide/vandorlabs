package com.vandorlabs.gear;

import com.vandorlabs.vehicle.*;
import java.util.*;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.*;
import net.minecraft.world.World;

/** Bounded inert attachment snapshot. World cells are restored at integral translation endpoints. */
public final class ArmPayload {
    public static final int MAX_BLOCKS=64,MAX_SPAN=16;
    private static final ThreadLocal<Integer> READ_DEPTH=ThreadLocal.withInitial(()->0);
    public final VehicleStructure blocks;
    /** Construction origin relative to the arm; cell positions are normalized. */
    public final BlockPos offset;
    private VehicleWorld view;
    private boolean materialized;
    private boolean gridAligned=true;
    private BlockPos placedOffset;
    private VehicleStructure placedBlocks;
    private VehicleStructure placed(){return placedBlocks==null?blocks:placedBlocks;}
    // Absolute cells that may still hold copied contents after a failed rollback.
    // These links deliberately do not translate with a dropped recovery item.
    private final Map<Integer,BlockPos> ownershipJournal=new LinkedHashMap<>();
    private int journalDimension;
    public boolean hasOwnershipJournal(){return !ownershipJournal.isEmpty();}
    public int ownershipJournalSize(){return ownershipJournal.size();}
    private void journal(World world,BlockPos origin,Collection<VehicleStructure.Cell> affected){
        journalDimension=world.provider.getDimension();
        for(VehicleStructure.Cell cell:affected){BlockPos at=origin.add(cell.pos);if(!world.isAirBlock(at))ownershipJournal.put(placed().cells.indexOf(cell),at.toImmutable());}
    }
    private boolean matchesCell(World world,BlockPos at,VehicleStructure.Cell cell){
        if(world.getBlockState(at)!=cell.state)return false;TileEntity tile=world.getTileEntity(at);
        if(!cell.hasTileData())return tile==null;
        return tile!=null&&VehicleTileData.translated(tile.writeToNBT(new NBTTagCompound()),BlockPos.ORIGIN.subtract(at).add(cell.pos)).equals(cell.tileData());
    }
    /** Reconcile exact copied cells before a recovery snapshot can move or materialize anywhere. */
    public boolean reconcileOwnership(World world,EntityPlayerMP actor){
        if(!hasOwnershipJournal())return true;
        if(world instanceof VehicleWorld||actor==null||world.provider.getDimension()!=journalDimension)return false;
        for(Map.Entry<Integer,BlockPos> entry:ownershipJournal.entrySet()){
            BlockPos at=entry.getValue();if(!GearAssembly.loaded(world,at)||!matchesCell(world,at,placed().cells.get(entry.getKey())))return false;
            VehicleService.permission(actor,at);
            if(net.minecraftforge.common.MinecraftForge.EVENT_BUS.post(new net.minecraftforge.event.world.BlockEvent.BreakEvent(world,at,world.getBlockState(at),actor)))return false;
        }
        Iterator<Map.Entry<Integer,BlockPos>> entries=ownershipJournal.entrySet().iterator();
        while(entries.hasNext()){
            Map.Entry<Integer,BlockPos> entry=entries.next();BlockPos at=entry.getValue();
            try{retireTile(world,at);if(!world.setBlockState(at,Blocks.AIR.getDefaultState(),2)&&!world.isAirBlock(at))return false;entries.remove();}catch(RuntimeException | LinkageError failed){return false;}
        }
        materialized=false;return true;
    }

    private ArmPayload(VehicleStructure blocks,BlockPos offset,boolean materialized,BlockPos placed){this.blocks=blocks;this.offset=offset;this.materialized=materialized;this.placedOffset=placed;}
    private ArmPayload preserveAlignment(ArmPayload next){next.gridAligned=gridAligned;next.placedBlocks=placedBlocks;return next;}
    public VehicleWorld view(GearTileEntity arm){if(view==null||view.parent!=arm.getWorld()){VehicleWorld installed=new VehicleWorld(arm.getWorld(),blocks);for(VehicleStructure.Cell cell:blocks.cells)if(cell.hasTileData()&&!installed.tiles.containsKey(cell.pos))throw new IllegalArgumentException("Attachment tile cannot be installed; snapshot retained");view=installed;view.origin=arm.getPos().add(offset);}return view;}
    public boolean materialized(){return materialized;}
    public static ArmPayload discover(GearTileEntity arm,EntityPlayerMP actor){
        World world=arm.getWorld();BlockPos start=arm.localCell(0,-arm.assembly().columnHeight(),0);
        if(world.isAirBlock(start))return null;
        ArrayDeque<BlockPos> pending=new ArrayDeque<>();Set<BlockPos> visited=new LinkedHashSet<>();pending.add(start);
        int minX=start.getX(),minY=start.getY(),minZ=start.getZ(),maxX=minX,maxY=minY,maxZ=minZ;
        List<VehicleStructure.Cell> captured=new ArrayList<>();
        while(!pending.isEmpty()){
            BlockPos at=pending.removeFirst();if(!visited.add(at))continue;
            if(!GearAssembly.loaded(world,at))throw new IllegalArgumentException("Load the complete arm attachment first");
            IBlockState state=world.getBlockState(at);if(state.getBlock().isAir(state,world,at)||arm.members().contains(at))continue;
            BlockPos canonical=ArmOrientation.inverse(arm.mountDirection(),at.subtract(arm.getPos()));
            if(canonical.getY()>-arm.assembly().columnHeight())throw new IllegalArgumentException("Separate the attachment from the arm's mounting structure");
            if(state.getMaterial().isLiquid()||state.getBlockHardness(world,at)<0)throw new IllegalArgumentException("Unsupported arm attachment: "+state.getBlock().getLocalizedName());
            if(!(world instanceof VehicleWorld)){
                if(actor==null)throw new IllegalArgumentException("Use the Configurizer before moving the attachment");
                VehicleService.permission(actor,at);
                if(net.minecraftforge.common.MinecraftForge.EVENT_BUS.post(new net.minecraftforge.event.world.BlockEvent.BreakEvent(world,at,state,actor)))throw new IllegalArgumentException("A protection rule prevented moving the attachment");
            }
            TileEntity tile=world.getTileEntity(at);
            if(tile instanceof net.minecraft.tileentity.TileEntityPiston)throw new IllegalArgumentException("Wait for the piston to stop before moving it");
            if(state.getBlock().hasTileEntity(state)&&tile==null)throw new IllegalArgumentException("Attachment block data is still loading");
            minX=Math.min(minX,at.getX());minY=Math.min(minY,at.getY());minZ=Math.min(minZ,at.getZ());maxX=Math.max(maxX,at.getX());maxY=Math.max(maxY,at.getY());maxZ=Math.max(maxZ,at.getZ());
            if(captured.size()>=MAX_BLOCKS||maxX-minX>=MAX_SPAN||maxY-minY>=MAX_SPAN||maxZ-minZ>=MAX_SPAN)throw new IllegalArgumentException("Arm attachment exceeds 64 blocks or a 16-block span");
            captured.add(new VehicleStructure.Cell(at,state,tile==null?null:tile.writeToNBT(new NBTTagCompound())));
            // A landing foot terminates its own outward branch, allowing nested gear above it.
            boolean foot=GearAssembly.isFoot(state);
            for(EnumFacing side:EnumFacing.values()){
                if(foot&&side==arm.mountDirection())continue;
                BlockPos next=at.offset(side);if(arm.members().contains(next))continue;
                if(VehicleScan.touches(world,at,state,side))pending.add(next);
            }
        }
        if(captured.isEmpty())return null;
        BlockPos origin=new BlockPos(minX,minY,minZ);List<VehicleStructure.Cell> normalized=new ArrayList<>();
        for(VehicleStructure.Cell cell:captured)normalized.add(new VehicleStructure.Cell(cell.pos.subtract(origin),cell.state,cell.hasTileData()?VehicleTileData.translated(cell.tileData(),BlockPos.ORIGIN.subtract(origin)):null));
        VehicleStructure snapshot=VehicleStructure.display(normalized);try{snapshot.encode();}catch(java.io.IOException tooLarge){throw new IllegalArgumentException("Arm attachment exceeds the saved-data limit",tooLarge);}
        return new ArmPayload(snapshot,origin.subtract(arm.getPos()),true,origin.subtract(arm.getPos()));
    }
    /** Captured vehicle cells have already passed assembly authorization. */
    public Set<BlockPos> placedCells(GearTileEntity arm){Set<BlockPos> result=new HashSet<>();for(VehicleStructure.Cell cell:placed().cells)result.add(arm.getPos().add(placedOffset).add(cell.pos));return result;}
    public ArmPayload capturedCopy(){if(hasOwnershipJournal())throw new IllegalArgumentException("Reconcile original attachment cells before moving this snapshot");return preserveAlignment(new ArmPayload(blocks,offset,false,placedOffset));}
    interface FailureInjector {void check(String stage,BlockPos at);}
    static FailureInjector failureInjector;
    private static void failure(String stage,BlockPos at){if(failureInjector!=null&&System.getProperty("vandorlabs.reprolab")!=null)failureInjector.check(stage,at);}
    /** Adopt edits to the already materialized cells without expanding their authorized footprint. */
    public ArmPayload refreshPlaced(GearTileEntity arm,EntityPlayerMP actor){
        if(hasOwnershipJournal())throw new IllegalArgumentException("Original attachment cells need recovery");
        if(!materialized)return this;if(placedBlocks!=null){if(!matchesPlaced(arm))throw new IllegalArgumentException("Restore the rotated attachment before replacing its blocks");return this;}World world=arm.getWorld();BlockPos origin=arm.getPos().add(placedOffset);List<VehicleStructure.Cell> refreshed=new ArrayList<>();
        for(VehicleStructure.Cell old:blocks.cells){
            BlockPos at=origin.add(old.pos);if(!GearAssembly.loaded(world,at))throw new IllegalArgumentException("Load every attachment cell before moving it");IBlockState state=world.getBlockState(at);
            if(state.getBlock().isAir(state,world,at)||state.getMaterial().isLiquid()||state.getBlockHardness(world,at)<0)throw new IllegalArgumentException("Restore the complete attachment before moving it");
            if(!(world instanceof VehicleWorld)){
                if(actor==null)throw new IllegalArgumentException("Use the Configurizer before moving the changed attachment");VehicleService.permission(actor,at);
                if(net.minecraftforge.common.MinecraftForge.EVENT_BUS.post(new net.minecraftforge.event.world.BlockEvent.BreakEvent(world,at,state,actor)))throw new IllegalArgumentException("A protection rule prevented moving the changed attachment");
            }
            TileEntity tile=world.getTileEntity(at);if(state.getBlock().hasTileEntity(state)&&tile==null||tile instanceof net.minecraft.tileentity.TileEntityPiston)throw new IllegalArgumentException("Attachment tile is unavailable or moving");
            refreshed.add(new VehicleStructure.Cell(old.pos,state,tile==null?null:VehicleTileData.translated(tile.writeToNBT(new NBTTagCompound()),BlockPos.ORIGIN.subtract(origin))));
        }
        return preserveAlignment(new ArmPayload(VehicleStructure.display(refreshed),offset,true,placedOffset));
    }
    public boolean matchesPlaced(GearTileEntity arm){return materialized&&placed().matches(arm.getWorld(),arm.getPos().add(placedOffset));}
    /** Materialized cells belong to the world. Mining them must not leave a second retained owner. */
    public ArmPayload pruneRemoved(GearTileEntity arm){
        if(!materialized||hasOwnershipJournal())return this;World world=arm.getWorld();BlockPos origin=arm.getPos().add(placedOffset);
        for(VehicleStructure.Cell cell:placed().cells)if(!GearAssembly.loaded(world,origin.add(cell.pos)))return this;
        List<VehicleStructure.Cell> retained=new ArrayList<>(),positions=new ArrayList<>();for(int i=0;i<blocks.cells.size();i++)if(!world.isAirBlock(origin.add(placed().cells.get(i).pos))){retained.add(blocks.cells.get(i));positions.add(placed().cells.get(i));}
        if(retained.size()==blocks.cells.size())return this;if(retained.isEmpty())return null;
        ArmPayload next=preserveAlignment(new ArmPayload(VehicleStructure.display(retained),offset,true,placedOffset));if(placedBlocks!=null)next.placedBlocks=VehicleStructure.display(positions);return next;
    }
    public boolean detach(GearTileEntity arm){try(ArmTransactions.Scope scope=ArmTransactions.authorizing(arm)){return detachAuthorized(arm);}catch(RuntimeException|LinkageError failure){ArmTransactions.failed(arm);throw failure;}}
    private boolean detachAuthorized(GearTileEntity arm){
        if(hasOwnershipJournal())return false;
        if(!materialized)return true;World world=arm.getWorld();BlockPos origin=arm.getPos().add(placedOffset);
        if(!matchesPlaced(arm))throw new IllegalArgumentException("The arm attachment changed; return to its construction pose before replacing it");
        EntityPlayerMP authorizer=com.vandorlabs.tiles.WorldActionAuthorization.actor(arm);for(VehicleStructure.Cell cell:placed().cells){BlockPos at=origin.add(cell.pos);VehicleService.permission(authorizer,at);if(net.minecraftforge.common.MinecraftForge.EVENT_BUS.post(new net.minecraftforge.event.world.BlockEvent.BreakEvent(world,at,world.getBlockState(at),authorizer)))throw new IllegalArgumentException("A protection rule prevented moving the attachment");}
        Map<VehicleStructure.Cell,TileEntity> rollback=prepareTiles(world,origin,placed());
        MovingBlockSupport.validate(blocks,view(arm));
        if(!ArmTransactions.ready(arm,"detach",placedCells(arm)))return false;
        List<VehicleStructure.Cell> removed=new ArrayList<>();
        try{
            for(VehicleStructure.Cell cell:placed().cells){
                removed.add(cell);BlockPos at=origin.add(cell.pos);retireTile(world,at);
                if(!world.setBlockState(at,Blocks.AIR.getDefaultState(),2)&&!world.isAirBlock(at))throw new IllegalArgumentException("Attachment source could not be cleared");failure("clear",at);
            }
            materialized=false;ArmTransactions.applied(arm);return true;
        }catch(RuntimeException | LinkageError failure){
            ArmTransactions.failed(arm);
            materialized=false;boolean restored=true;
            for(VehicleStructure.Cell cell:removed)try{install(world,origin,cell,rollback.get(cell));}catch(RuntimeException | LinkageError rollbackFailure){restored=false;failure.addSuppressed(rollbackFailure);}
            if(restored)materialized=true;else journal(world,origin,placed().cells);
            throw failure;
        }
    }
    private Map<VehicleStructure.Cell,TileEntity> prepareTiles(World world,BlockPos origin,VehicleStructure structure){
        Map<VehicleStructure.Cell,TileEntity> prepared=new IdentityHashMap<>();
        for(VehicleStructure.Cell cell:structure.cells)if(cell.hasTileData()){
            NBTTagCompound tag=VehicleTileData.translated(cell.tileData(),origin);failure("prepare-tile",origin.add(cell.pos));TileEntity tile=TileEntity.create(null,tag);
            if(tile==null)throw new IllegalArgumentException("Cannot restore attachment block data");
            prepared.put(cell,tile);
        }
        return prepared;
    }
    private static void retireTile(World world,BlockPos at){com.vandorlabs.vehicle.VehicleForeignCompat.clearDetachedInventory(world.getTileEntity(at));world.removeTileEntity(at);}
    private static void install(World world,BlockPos origin,VehicleStructure.Cell cell,TileEntity prepared){
        BlockPos at=origin.add(cell.pos);
        if(world.getBlockState(at)!=cell.state&&!world.setBlockState(at,cell.state,2))throw new IllegalArgumentException("Attachment target could not be installed");
        if(cell.hasTileData()){
            if(prepared==null)throw new IllegalArgumentException("Attachment tile was not prepared");
            retireTile(world,at);prepared.setWorld(world);prepared.validate();world.setTileEntity(at,prepared);
            if(world.getTileEntity(at)!=prepared)throw new IllegalArgumentException("Attachment tile installation failed");
        }
        failure("install",at);
    }
    /** Restore only exact translation endpoints; arbitrary rotations remain an inert captured snapshot. */
    public boolean restore(GearTileEntity arm,double progress){try(ArmTransactions.Scope scope=ArmTransactions.authorizing(arm)){return restoreAuthorized(arm,progress);}catch(RuntimeException|LinkageError failure){ArmTransactions.failed(arm);throw failure;}}
    private boolean restoreAuthorized(GearTileEntity arm,double progress){
        if(hasOwnershipJournal())return false;
        if(materialized)return true;
        com.vandorlabs.gear.core.GearMotion.Pose pose=arm.pose(progress);
        VehicleStructure destination=blocks;BlockPos target;
        if(Math.abs(pose.angleDegrees())>1e-8&&pose.mode()!=com.vandorlabs.gear.core.GearMotion.Mode.ROTATE_LEVEL){
            Placement placement=rotatedPlacement(arm,progress);if(placement==null)return false;destination=placement.blocks;target=placement.origin;
        }else{
            Vec3d displacement=transform(arm,progress,0,0,0).subtract(new Vec3d(offset));
            if(Math.abs(displacement.x-Math.rint(displacement.x))>1e-8||Math.abs(displacement.y-Math.rint(displacement.y))>1e-8||Math.abs(displacement.z-Math.rint(displacement.z))>1e-8)return false;
            target=offset.add(Math.round(displacement.x),Math.round(displacement.y),Math.round(displacement.z));
        }
        World world=arm.getWorld();BlockPos origin=arm.getPos().add(target);
        for(VehicleStructure.Cell cell:destination.cells){BlockPos at=origin.add(cell.pos);if(!GearAssembly.loaded(world,at)||!world.getWorldBorder().contains(at)||!world.isAirBlock(at))return false;}
        EntityPlayerMP authorizer=com.vandorlabs.tiles.WorldActionAuthorization.actor(arm);
        for(VehicleStructure.Cell cell:destination.cells)VehicleService.permission(authorizer,origin.add(cell.pos));
        Map<VehicleStructure.Cell,TileEntity> prepared=prepareTiles(world,origin,destination);
        MovingBlockSupport.validate(blocks,view(arm));java.util.List<BlockPos> targets=new java.util.ArrayList<>();for(VehicleStructure.Cell cell:destination.cells)targets.add(origin.add(cell.pos));if(!ArmTransactions.ready(arm,"restore",targets))return false;
        placedBlocks=destination==blocks?null:destination;
        List<VehicleStructure.Cell> installed=new ArrayList<>();
        try{for(VehicleStructure.Cell cell:destination.cells){installed.add(cell);install(world,origin,cell,prepared.get(cell));}}
        catch(RuntimeException | LinkageError failure){
            ArmTransactions.failed(arm);
            for(VehicleStructure.Cell cell:installed)try{BlockPos at=origin.add(cell.pos);failure("restore-cleanup",at);retireTile(world,at);if(!world.setBlockState(at,Blocks.AIR.getDefaultState(),2)&&!world.isAirBlock(at))failure.addSuppressed(new IllegalStateException("Attachment rollback blocked"));}catch(RuntimeException | LinkageError rollbackFailure){failure.addSuppressed(rollbackFailure);}
            journal(world,origin,installed);
            throw failure;
        }
        if(world instanceof net.minecraft.world.WorldServer)com.vandorlabs.tiles.WorldActionAuthorization.installed((net.minecraft.world.WorldServer)world,destination,origin,authorizer.getUniqueID());
        placedOffset=target;materialized=true;ArmTransactions.applied(arm);return true;
    }
    private static final class Placement {final VehicleStructure blocks;final BlockPos origin;Placement(VehicleStructure blocks,BlockPos origin){this.blocks=blocks;this.origin=origin;}}
    /** General quarter-turn placement for representable, tile-free full blocks. Other payloads stay retained. */
    @SuppressWarnings({"rawtypes","unchecked"}) private Placement rotatedPlacement(GearTileEntity arm,double progress){
        Vec3d zero=transform(arm,progress,0,0,0);Vec3d[] axes={transform(arm,progress,1,0,0).subtract(zero),transform(arm,progress,0,1,0).subtract(zero),transform(arm,progress,0,0,1).subtract(zero)};
        for(Vec3d axis:axes)if(Math.abs(Math.abs(axis.x)+Math.abs(axis.y)+Math.abs(axis.z)-1)>1e-8)return null;
        List<VehicleStructure.Cell> transformed=new ArrayList<>();int minX=Integer.MAX_VALUE,minY=minX,minZ=minX;
        for(VehicleStructure.Cell cell:blocks.cells){if(cell.hasTileData()||!cell.state.isFullCube())return null;IBlockState state=cell.state;
            for(net.minecraft.block.properties.IProperty property:state.getPropertyKeys()){
                Comparable value=state.getValue(property),next=value;
                if(value instanceof EnumFacing){net.minecraft.util.math.Vec3i vector=((EnumFacing)value).getDirectionVec();Vec3d out=axes[0].scale(vector.getX()).add(axes[1].scale(vector.getY())).add(axes[2].scale(vector.getZ()));next=EnumFacing.getFacingFromVector((float)out.x,(float)out.y,(float)out.z);}
                else if(value instanceof EnumFacing.Axis){Vec3d out=axes[value==EnumFacing.Axis.X?0:value==EnumFacing.Axis.Y?1:2];next=EnumFacing.getFacingFromVector((float)out.x,(float)out.y,(float)out.z).getAxis();}
                else if(!java.util.Arrays.asList("variant","type","color","decayable","check_decay","snowy","wet").contains(property.getName()))return null;
                if(!property.getAllowedValues().contains(next))return null;state=state.withProperty(property,next);
            }
            if(state.getBlock().getStateFromMeta(state.getBlock().getMetaFromState(state))!=state)return null;
            double x=Double.POSITIVE_INFINITY,y=x,z=x;for(int corner=0;corner<8;corner++){Vec3d v=transform(arm,progress,cell.pos.getX()+(corner&1),cell.pos.getY()+((corner>>1)&1),cell.pos.getZ()+((corner>>2)&1));x=Math.min(x,v.x);y=Math.min(y,v.y);z=Math.min(z,v.z);}
            if(Math.abs(x-Math.rint(x))+Math.abs(y-Math.rint(y))+Math.abs(z-Math.rint(z))>1e-8)return null;BlockPos at=new BlockPos(Math.rint(x),Math.rint(y),Math.rint(z));minX=Math.min(minX,at.getX());minY=Math.min(minY,at.getY());minZ=Math.min(minZ,at.getZ());transformed.add(new VehicleStructure.Cell(at,state,null));
        }
        BlockPos origin=new BlockPos(minX,minY,minZ);List<VehicleStructure.Cell> normalized=new ArrayList<>();for(VehicleStructure.Cell cell:transformed)normalized.add(new VehicleStructure.Cell(cell.pos.subtract(origin),cell.state,null));return new Placement(VehicleStructure.display(normalized),origin);
    }
    public Vec3d transform(GearTileEntity arm,double progress,double x,double y,double z){
        Vec3d result=rawTransform(arm,progress,x,y,z);
        if(gridAligned&&arm.isSwingArmController()&&Math.abs(arm.activeAngle()/90-Math.rint(arm.activeAngle()/90))<1e-8){
            Vec3d endpoint=rawTransform(arm,0,0,0,0);double blend=1-progress;blend=blend*blend*(3-2*blend);
            result=result.addVector((Math.rint(endpoint.x)-endpoint.x)*blend,(Math.rint(endpoint.y)-endpoint.y)*blend,(Math.rint(endpoint.z)-endpoint.z)*blend);
        }
        return result;
    }
    private Vec3d rawTransform(GearTileEntity arm,double progress,double x,double y,double z){
        Vec3d local=ArmOrientation.vector(inverseFace(arm.mountDirection()),x+offset.getX()-.5,y+offset.getY()-.5,z+offset.getZ()-.5).addVector(.5,.5,.5);
        com.vandorlabs.gear.core.GearMotion.Vec moved=arm.pose(progress).transformFootVertex(local.x,local.y,local.z);
        return ArmOrientation.point(arm.mountDirection(),moved.x,moved.y,moved.z);
    }
    private static EnumFacing inverseFace(EnumFacing face){switch(face){case EAST:return EnumFacing.WEST;case WEST:return EnumFacing.EAST;case NORTH:return EnumFacing.SOUTH;case SOUTH:return EnumFacing.NORTH;default:return face;}}
    public List<AxisAlignedBB> boxes(GearTileEntity arm,double progress){
        // At an installed endpoint vanilla owns these actual blocks, including
        // their normal hit identity and immediate removal/collision lifecycle.
        if(materialized)return Collections.emptyList();
        List<AxisAlignedBB> boxes=new ArrayList<>();VehicleWorld world=view(arm);
        for(VehicleStructure.Cell cell:blocks.cells){List<AxisAlignedBB> parts=new ArrayList<>();cell.state.addCollisionBoxToList(world,cell.pos,new AxisAlignedBB(cell.pos).grow(2),parts,null,false);
            for(AxisAlignedBB b:parts){double lx=Double.POSITIVE_INFINITY,ly=lx,lz=lx,hx=Double.NEGATIVE_INFINITY,hy=hx,hz=hx;for(int n=0;n<8;n++){Vec3d v=transform(arm,progress,(n&1)==0?b.minX:b.maxX,(n&2)==0?b.minY:b.maxY,(n&4)==0?b.minZ:b.maxZ);lx=Math.min(lx,v.x);ly=Math.min(ly,v.y);lz=Math.min(lz,v.z);hx=Math.max(hx,v.x);hy=Math.max(hy,v.y);hz=Math.max(hz,v.z);}boxes.add(new AxisAlignedBB(lx,ly,lz,hx,hy,hz).offset(arm.getPos()));}
        }return boxes;
    }
    private static BlockPos yaw(BlockPos p,int turns){switch(Math.floorMod(turns,4)){case 1:return new BlockPos(-p.getZ(),p.getY(),p.getX());case 2:return new BlockPos(-p.getX(),p.getY(),-p.getZ());case 3:return new BlockPos(p.getZ(),p.getY(),-p.getX());default:return p;}}
    public static NBTTagCompound rotate(NBTTagCompound tag,int turns,World parent){
        ArmPayload before=read(tag);if(before.hasOwnershipJournal())throw new IllegalArgumentException("Reconcile attachment ownership before rotating the craft");VehicleStructure after=VehicleRotation.rotate(before.blocks,new VehicleWorld(parent,before.blocks),turns);
        BlockPos zero=VehicleRotation.cell(BlockPos.ORIGIN,(int)before.blocks.bounds.maxX,(int)before.blocks.bounds.maxZ,turns);
        ArmPayload result=before.preserveAlignment(new ArmPayload(after,yaw(before.offset,turns).subtract(zero),before.materialized,yaw(before.placedOffset,turns).subtract(zero)));if(before.placedBlocks!=null){BlockPos placedZero=VehicleRotation.cell(BlockPos.ORIGIN,(int)before.placedBlocks.bounds.maxX,(int)before.placedBlocks.bounds.maxZ,turns);result.placedBlocks=VehicleRotation.rotate(before.placedBlocks,new VehicleWorld(parent,before.placedBlocks),turns);result.placedOffset=yaw(before.placedOffset,turns).subtract(placedZero);}return result.write();
    }
    public NBTTagCompound write(){NBTTagCompound tag=new NBTTagCompound();tag.setTag("Blocks",blocks.write());tag.setLong("Offset",offset.toLong());tag.setLong("PlacedOffset",placedOffset.toLong());tag.setBoolean("Materialized",materialized);if(gridAligned)tag.setBoolean("GridAligned",true);if(placedBlocks!=null)tag.setTag("PlacedBlocks",placedBlocks.write());
        if(hasOwnershipJournal()){tag.setInteger("OwnershipDimension",journalDimension);net.minecraft.nbt.NBTTagList list=new net.minecraft.nbt.NBTTagList();for(Map.Entry<Integer,BlockPos> entry:ownershipJournal.entrySet()){NBTTagCompound cell=new NBTTagCompound();cell.setInteger("Cell",entry.getKey());cell.setLong("Pos",entry.getValue().toLong());list.appendTag(cell);}tag.setTag("OwnershipJournal",list);}
        return tag;}
    public static ArmPayload read(NBTTagCompound tag){
        int depth=READ_DEPTH.get();if(depth>=8)throw new IllegalArgumentException("Arm payload nesting exceeds eight snapshots");READ_DEPTH.set(depth+1);
        try{return readBounded(tag);}finally{READ_DEPTH.set(depth);}
    }
    private static ArmPayload readBounded(NBTTagCompound tag){
        VehicleStructure structure=VehicleStructure.readDisplay(tag.getCompoundTag("Blocks"));if(structure.cells.isEmpty()||structure.cells.size()>MAX_BLOCKS)throw new IllegalArgumentException("Oversized arm payload");
        for(VehicleStructure.Cell cell:structure.cells)if(cell.hasTileData()&&TileEntity.create(null,cell.tileData())==null)throw new IllegalArgumentException("Attachment tile cannot be loaded; original snapshot retained");
        BlockPos offset=BlockPos.fromLong(tag.getLong("Offset")),placed=BlockPos.fromLong(tag.getLong("PlacedOffset"));
        for(BlockPos p:new BlockPos[]{offset,placed})if(Math.abs((long)p.getX())>32||Math.abs((long)p.getY())>32||Math.abs((long)p.getZ())>32)throw new IllegalArgumentException("Arm payload is too far from its controller");
        ArmPayload payload=new ArmPayload(structure,offset,tag.getBoolean("Materialized"),placed);
        net.minecraft.nbt.NBTTagList journal=tag.getTagList("OwnershipJournal",10);if(journal.tagCount()>structure.cells.size())throw new IllegalArgumentException("Invalid attachment ownership journal");
        payload.journalDimension=tag.getInteger("OwnershipDimension");
        for(int i=0;i<journal.tagCount();i++){NBTTagCompound entry=journal.getCompoundTagAt(i);int cell=entry.getInteger("Cell");if(cell<0||cell>=structure.cells.size()||!entry.hasKey("Pos",4)||payload.ownershipJournal.put(cell,BlockPos.fromLong(entry.getLong("Pos")))!=null)throw new IllegalArgumentException("Invalid attachment ownership entry");}
        if(tag.hasKey("PlacedBlocks",10)){payload.placedBlocks=VehicleStructure.readDisplay(tag.getCompoundTag("PlacedBlocks"));if(payload.placedBlocks.cells.size()!=structure.cells.size())throw new IllegalArgumentException("Invalid rotated attachment mapping");for(VehicleStructure.Cell cell:payload.placedBlocks.cells)if(cell.hasTileData())throw new IllegalArgumentException("Unsupported rotated attachment tile");}
        payload.gridAligned=tag.getBoolean("GridAligned");
        if(payload.hasOwnershipJournal())payload.materialized=false;
        return payload;
    }
}
