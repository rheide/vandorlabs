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
    private BlockPos placedOffset;
    // Absolute cells that may still hold copied contents after a failed rollback.
    // These links deliberately do not translate with a dropped recovery item.
    private final Map<Integer,BlockPos> ownershipJournal=new LinkedHashMap<>();
    private int journalDimension;
    public boolean hasOwnershipJournal(){return !ownershipJournal.isEmpty();}
    public int ownershipJournalSize(){return ownershipJournal.size();}
    private void journal(World world,BlockPos origin,Collection<VehicleStructure.Cell> affected){
        journalDimension=world.provider.getDimension();
        for(VehicleStructure.Cell cell:affected){BlockPos at=origin.add(cell.pos);if(!world.isAirBlock(at))ownershipJournal.put(blocks.cells.indexOf(cell),at.toImmutable());}
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
            BlockPos at=entry.getValue();if(!GearAssembly.loaded(world,at)||!matchesCell(world,at,blocks.cells.get(entry.getKey())))return false;
            VehicleService.permission(actor,at);
            if(net.minecraftforge.common.MinecraftForge.EVENT_BUS.post(new net.minecraftforge.event.world.BlockEvent.BreakEvent(world,at,world.getBlockState(at),actor)))return false;
        }
        Iterator<Map.Entry<Integer,BlockPos>> entries=ownershipJournal.entrySet().iterator();
        while(entries.hasNext()){
            Map.Entry<Integer,BlockPos> entry=entries.next();BlockPos at=entry.getValue();
            try{world.removeTileEntity(at);if(!world.setBlockState(at,Blocks.AIR.getDefaultState(),2)&&!world.isAirBlock(at))return false;entries.remove();}catch(RuntimeException | LinkageError failed){return false;}
        }
        materialized=false;return true;
    }

    private ArmPayload(VehicleStructure blocks,BlockPos offset,boolean materialized,BlockPos placed){this.blocks=blocks;this.offset=offset;this.materialized=materialized;this.placedOffset=placed;}
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
        return new ArmPayload(VehicleStructure.display(normalized),origin.subtract(arm.getPos()),true,origin.subtract(arm.getPos()));
    }
    /** Captured vehicle cells have already passed assembly authorization. */
    public Set<BlockPos> placedCells(GearTileEntity arm){Set<BlockPos> result=new HashSet<>();for(VehicleStructure.Cell cell:blocks.cells)result.add(arm.getPos().add(placedOffset).add(cell.pos));return result;}
    public ArmPayload capturedCopy(){if(hasOwnershipJournal())throw new IllegalArgumentException("Reconcile original attachment cells before moving this snapshot");return new ArmPayload(blocks,offset,false,placedOffset);}
    interface FailureInjector {void check(String stage,BlockPos at);}
    static FailureInjector failureInjector;
    private static void failure(String stage,BlockPos at){if(failureInjector!=null&&System.getProperty("vandorlabs.reprolab")!=null)failureInjector.check(stage,at);}
    /** Adopt edits to the already materialized cells without expanding their authorized footprint. */
    public ArmPayload refreshPlaced(GearTileEntity arm,EntityPlayerMP actor){
        if(hasOwnershipJournal())throw new IllegalArgumentException("Original attachment cells need recovery");
        if(!materialized)return this;World world=arm.getWorld();BlockPos origin=arm.getPos().add(placedOffset);List<VehicleStructure.Cell> refreshed=new ArrayList<>();
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
        return new ArmPayload(VehicleStructure.display(refreshed),offset,true,placedOffset);
    }
    public boolean matchesPlaced(GearTileEntity arm){return materialized&&blocks.matches(arm.getWorld(),arm.getPos().add(placedOffset));}
    public boolean detach(GearTileEntity arm){
        if(hasOwnershipJournal())return false;
        if(!materialized)return true;World world=arm.getWorld();BlockPos origin=arm.getPos().add(placedOffset);
        if(!matchesPlaced(arm))throw new IllegalArgumentException("The arm attachment changed; return to its construction pose before replacing it");
        EntityPlayerMP authorizer=com.vandorlabs.tiles.WorldActionAuthorization.actor(arm);for(VehicleStructure.Cell cell:blocks.cells){BlockPos at=origin.add(cell.pos);VehicleService.permission(authorizer,at);if(net.minecraftforge.common.MinecraftForge.EVENT_BUS.post(new net.minecraftforge.event.world.BlockEvent.BreakEvent(world,at,world.getBlockState(at),authorizer)))throw new IllegalArgumentException("A protection rule prevented moving the attachment");}
        Map<VehicleStructure.Cell,TileEntity> rollback=prepareTiles(world,origin);
        List<VehicleStructure.Cell> removed=new ArrayList<>();
        try{
            for(VehicleStructure.Cell cell:blocks.cells){
                removed.add(cell);BlockPos at=origin.add(cell.pos);world.removeTileEntity(at);
                if(!world.setBlockState(at,Blocks.AIR.getDefaultState(),2)&&!world.isAirBlock(at))throw new IllegalArgumentException("Attachment source could not be cleared");failure("clear",at);
            }
            materialized=false;return true;
        }catch(RuntimeException | LinkageError failure){
            materialized=false;boolean restored=true;
            for(VehicleStructure.Cell cell:removed)try{install(world,origin,cell,rollback.get(cell));}catch(RuntimeException | LinkageError rollbackFailure){restored=false;failure.addSuppressed(rollbackFailure);}
            if(restored)materialized=true;else journal(world,origin,blocks.cells);
            throw failure;
        }
    }
    private Map<VehicleStructure.Cell,TileEntity> prepareTiles(World world,BlockPos origin){
        Map<VehicleStructure.Cell,TileEntity> prepared=new IdentityHashMap<>();
        for(VehicleStructure.Cell cell:blocks.cells)if(cell.hasTileData()){
            NBTTagCompound tag=VehicleTileData.translated(cell.tileData(),origin);failure("prepare-tile",origin.add(cell.pos));TileEntity tile=TileEntity.create(null,tag);
            if(tile==null)throw new IllegalArgumentException("Cannot restore attachment block data");
            prepared.put(cell,tile);
        }
        return prepared;
    }
    private static void install(World world,BlockPos origin,VehicleStructure.Cell cell,TileEntity prepared){
        BlockPos at=origin.add(cell.pos);
        if(world.getBlockState(at)!=cell.state&&!world.setBlockState(at,cell.state,2))throw new IllegalArgumentException("Attachment target could not be installed");
        if(cell.hasTileData()){
            if(prepared==null)throw new IllegalArgumentException("Attachment tile was not prepared");
            world.removeTileEntity(at);prepared.setWorld(world);prepared.validate();world.setTileEntity(at,prepared);
            if(world.getTileEntity(at)!=prepared)throw new IllegalArgumentException("Attachment tile installation failed");
        }
        failure("install",at);
    }
    /** Restore only exact translation endpoints; arbitrary rotations remain an inert captured snapshot. */
    public boolean restore(GearTileEntity arm,double progress){
        if(hasOwnershipJournal())return false;
        if(materialized)return true;
        com.vandorlabs.gear.core.GearMotion.Pose pose=arm.pose(progress);
        if(Math.abs(pose.angleDegrees())>1e-8)return false;
        Vec3d displacement=ArmOrientation.vector(arm.mountDirection(),0,pose.footOffsetY(),0);
        if(Math.abs(displacement.x-Math.rint(displacement.x))>1e-8||Math.abs(displacement.y-Math.rint(displacement.y))>1e-8||Math.abs(displacement.z-Math.rint(displacement.z))>1e-8)return false;
        BlockPos target=offset.add(Math.round(displacement.x),Math.round(displacement.y),Math.round(displacement.z));World world=arm.getWorld();BlockPos origin=arm.getPos().add(target);
        for(VehicleStructure.Cell cell:blocks.cells){BlockPos at=origin.add(cell.pos);if(!GearAssembly.loaded(world,at)||!world.getWorldBorder().contains(at)||!world.isAirBlock(at))return false;}
        EntityPlayerMP authorizer=com.vandorlabs.tiles.WorldActionAuthorization.actor(arm);
        for(VehicleStructure.Cell cell:blocks.cells)VehicleService.permission(authorizer,origin.add(cell.pos));
        Map<VehicleStructure.Cell,TileEntity> prepared=prepareTiles(world,origin);
        List<VehicleStructure.Cell> installed=new ArrayList<>();
        try{for(VehicleStructure.Cell cell:blocks.cells){installed.add(cell);install(world,origin,cell,prepared.get(cell));}}
        catch(RuntimeException | LinkageError failure){
            for(VehicleStructure.Cell cell:installed)try{BlockPos at=origin.add(cell.pos);failure("restore-cleanup",at);world.removeTileEntity(at);if(!world.setBlockState(at,Blocks.AIR.getDefaultState(),2)&&!world.isAirBlock(at))failure.addSuppressed(new IllegalStateException("Attachment rollback blocked"));}catch(RuntimeException | LinkageError rollbackFailure){failure.addSuppressed(rollbackFailure);}
            journal(world,origin,installed);
            throw failure;
        }
        if(world instanceof net.minecraft.world.WorldServer)com.vandorlabs.tiles.WorldActionAuthorization.installed((net.minecraft.world.WorldServer)world,blocks,origin,authorizer.getUniqueID());
        placedOffset=target;materialized=true;return true;
    }
    public Vec3d transform(GearTileEntity arm,double progress,double x,double y,double z){
        Vec3d local=ArmOrientation.vector(inverseFace(arm.mountDirection()),x+offset.getX()-.5,y+offset.getY()-.5,z+offset.getZ()-.5).addVector(.5,.5,.5);
        com.vandorlabs.gear.core.GearMotion.Vec moved=arm.pose(progress).transformFootVertex(local.x,local.y,local.z);
        return ArmOrientation.point(arm.mountDirection(),moved.x,moved.y,moved.z);
    }
    private static EnumFacing inverseFace(EnumFacing face){switch(face){case EAST:return EnumFacing.WEST;case WEST:return EnumFacing.EAST;case NORTH:return EnumFacing.SOUTH;case SOUTH:return EnumFacing.NORTH;default:return face;}}
    public List<AxisAlignedBB> boxes(GearTileEntity arm,double progress){
        List<AxisAlignedBB> boxes=new ArrayList<>();VehicleWorld world=view(arm);
        for(VehicleStructure.Cell cell:blocks.cells){List<AxisAlignedBB> parts=new ArrayList<>();cell.state.addCollisionBoxToList(world,cell.pos,new AxisAlignedBB(cell.pos).grow(2),parts,null,false);
            for(AxisAlignedBB b:parts){double lx=Double.POSITIVE_INFINITY,ly=lx,lz=lx,hx=Double.NEGATIVE_INFINITY,hy=hx,hz=hx;for(int n=0;n<8;n++){Vec3d v=transform(arm,progress,(n&1)==0?b.minX:b.maxX,(n&2)==0?b.minY:b.maxY,(n&4)==0?b.minZ:b.maxZ);lx=Math.min(lx,v.x);ly=Math.min(ly,v.y);lz=Math.min(lz,v.z);hx=Math.max(hx,v.x);hy=Math.max(hy,v.y);hz=Math.max(hz,v.z);}boxes.add(new AxisAlignedBB(lx,ly,lz,hx,hy,hz).offset(arm.getPos()));}
        }return boxes;
    }
    private static BlockPos yaw(BlockPos p,int turns){switch(Math.floorMod(turns,4)){case 1:return new BlockPos(-p.getZ(),p.getY(),p.getX());case 2:return new BlockPos(-p.getX(),p.getY(),-p.getZ());case 3:return new BlockPos(p.getZ(),p.getY(),-p.getX());default:return p;}}
    public static NBTTagCompound rotate(NBTTagCompound tag,int turns,World parent){
        ArmPayload before=read(tag);if(before.hasOwnershipJournal())throw new IllegalArgumentException("Reconcile attachment ownership before rotating the craft");VehicleStructure after=VehicleRotation.rotate(before.blocks,new VehicleWorld(parent,before.blocks),turns);
        BlockPos zero=VehicleRotation.cell(BlockPos.ORIGIN,(int)before.blocks.bounds.maxX,(int)before.blocks.bounds.maxZ,turns);
        return new ArmPayload(after,yaw(before.offset,turns).subtract(zero),before.materialized,yaw(before.placedOffset,turns).subtract(zero)).write();
    }
    public NBTTagCompound write(){NBTTagCompound tag=new NBTTagCompound();tag.setTag("Blocks",blocks.write());tag.setLong("Offset",offset.toLong());tag.setLong("PlacedOffset",placedOffset.toLong());tag.setBoolean("Materialized",materialized);
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
        if(payload.hasOwnershipJournal())payload.materialized=false;
        return payload;
    }
}
