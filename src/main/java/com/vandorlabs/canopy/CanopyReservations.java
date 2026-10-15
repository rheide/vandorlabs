package com.vandorlabs.canopy;

import com.vandorlabs.tiles.*;
import com.vandorlabs.ramp.RampCanopyCoordination;
import net.minecraft.block.state.IBlockState;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.*;
import net.minecraft.world.World;
import net.minecraftforge.event.world.BlockEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import java.lang.ref.WeakReference;
import java.util.*;

/** Physical helpers may yield to other mechanisms while the authoritative shell stays open. */
@Mod.EventBusSubscriber(modid="vandorlabs")
public final class CanopyReservations {
    private static final Map<World,Map<BlockPos,List<WeakReference<TileEntityCanopy>>>> WAITS=new WeakHashMap<>();
    private CanopyReservations(){}
    /** Logical seams survive an intentionally absent physical helper. */
    public static TileEntityCanopy logicalAt(net.minecraft.world.IBlockAccess access,BlockPos pos){
        TileEntityCanopy direct=TileEntityCanopy.at(access,pos);if(direct!=null)return direct;
        // Only Canopy/Long Canopy have a fixed-glass seam through a released
        // rear proxy. Their preserved constituent anchor is at most two cells
        // away; never scan chunk tile maps from a rendering connection query.
        for(net.minecraft.util.EnumFacing direction:net.minecraft.util.EnumFacing.HORIZONTALS)for(int distance=1;distance<=2;distance++){
            TileEntityCanopy candidate=TileEntityCanopy.at(access,pos.offset(direction,distance));
            if(candidate!=null&&!candidate.removing&&candidate.block()!=null&&candidate.block().opening()&&candidate.constituent()==candidate&&candidate.isReleasedCell(pos))return candidate;
        }
        return null;
    }

    private static boolean real(TileEntityCanopy owner){return owner.getWorld()!=null&&!owner.getWorld().isRemote&&!(owner.getWorld() instanceof com.vandorlabs.vehicle.VehicleWorld);}
    public static void clearWait(TileEntityCanopy owner){
        World world=owner.getWorld();Map<BlockPos,List<WeakReference<TileEntityCanopy>>> cells=WAITS.get(world);
        if(cells!=null){for(List<WeakReference<TileEntityCanopy>> refs:cells.values())refs.removeIf(ref->ref.get()==null||ref.get()==owner);cells.values().removeIf(List::isEmpty);}
        RampCanopyCoordination.clearCanopyWait(owner);
    }
    public static void targetChanged(TileEntityCanopy owner){if(real(owner))RampCanopyCoordination.changed(owner.getWorld(),owner.getPos());}
    public static void loaded(TileEntityCanopy owner){if(real(owner)){TileEntityCanopy root=owner.owner();root.reservationGeometryChanged();targetChanged(root);}}
    public static void removed(TileEntityCanopy owner){if(real(owner)){clearWait(owner);targetChanged(owner);}}
    private static TileEntityCanopy pendingAware(World world,BlockPos cell){TileEntity tile=world.getTileEntity(cell);return tile instanceof TileEntityCanopy&&!tile.isInvalid()?(TileEntityCanopy)tile:null;}
    // Mutation paths must see World.addedTileEntityList while tile ticks are in
    // progress. The general at() accessor intentionally reads only chunk maps.
    private static boolean own(TileEntityCanopy member,BlockPos cell){TileEntityCanopy tile=pendingAware(member.getWorld(),cell);return tile!=null&&tile.block()==member.block()&&member.getPos().equals(tile.anchor);}
    public static void releaseOpen(TileEntityCanopy owner){
        if(!real(owner)||!owner.draws()||owner.progress!=1||!owner.targetOpen)return;
        World world=owner.getWorld();List<AxisAlignedBB> shell=owner.boxes();boolean changed=false;
        for(TileEntityCanopy member:owner.reservationMembers()){
            for(BlockPos cell:member.cells()){
                if(cell.equals(member.getPos())||member.isReleasedCell(cell)||!world.isBlockLoaded(cell)||!own(member,cell))continue;
                AxisAlignedBB volume=new AxisAlignedBB(cell).shrink(.00001);boolean intersects=false;for(AxisAlignedBB box:shell)if(box.intersects(volume)){intersects=true;break;}
                if(intersects)continue;
                TileEntityCanopy helper=pendingAware(world,cell);member.releasedCell(cell,true);helper.removing=true;
                if(!world.setBlockToAir(cell)){helper.removing=false;member.releasedCell(cell,false);}else changed=true;
            }
            member.sync();
        }
        if(changed)targetChanged(owner);
    }
    /** Reacquire only air or exact owned helpers, after checking every affected cell. */
    public static boolean restoreFootprint(TileEntityCanopy owner){
        if(!real(owner))return true;World world=owner.getWorld();List<TileEntityCanopy> members=owner.reservationMembers();
        for(TileEntityCanopy member:members)for(BlockPos cell:member.cells())if(member.isReleasedCell(cell)&&(!world.isBlockLoaded(cell)||!world.isAirBlock(cell)&&!own(member,cell)))return false;
        if(members.stream().noneMatch(TileEntityCanopy::hasReleasedCells))return true;
        List<BlockPos> added=new ArrayList<>();List<TileEntityCanopy> parents=new ArrayList<>();
        try{
            for(TileEntityCanopy member:members)for(BlockPos cell:member.cells())if(member.isReleasedCell(cell)&&!own(member,cell)){
                if(!world.setBlockState(cell,world.getBlockState(member.getPos()),2))throw new IllegalStateException("Canopy helper placement refused");
                added.add(cell);parents.add(member);TileEntityCanopy helper=pendingAware(world,cell);if(helper==null)throw new IllegalStateException("Canopy helper tile missing");
                helper.anchor=member.getPos();helper.centeredPlacement=member.centeredPlacement;helper.hollowPlacement=member.hollowPlacement;net.minecraft.nbt.NBTTagCompound settings=new net.minecraft.nbt.NBTTagCompound();member.large.write(settings);helper.large.read(settings);helper.sync();
            }
            for(TileEntityCanopy member:members){for(BlockPos cell:member.cells())if(member.isReleasedCell(cell))member.releasedCell(cell,false);member.sync();}
            return true;
        }catch(RuntimeException failed){
            for(int i=0;i<added.size();i++){
                BlockPos cell=added.get(i);TileEntityCanopy parent=parents.get(i),helper=pendingAware(world,cell);
                // Only cells created by this transaction may be rolled back.
                // Also clean an uninitialized/missing tile if creation failed
                // before assigning its anchor, never an unrelated placed root.
                if(world.getBlockState(cell)==world.getBlockState(parent.getPos())&&(helper==null||parent.getPos().equals(helper.anchor)||helper.anchor==null&&!helper.hasSavedFootprint())){
                    if(helper!=null)helper.removing=true;world.setBlockToAir(cell);
                }
            }
            return false;
        }
    }
    public static boolean prepareClose(TileEntityCanopy owner){
        if(!real(owner))return true;clearWait(owner);World world=owner.getWorld();Set<BlockPos> blocked=new HashSet<>(),ramps=new HashSet<>();
        for(TileEntityCanopy member:owner.reservationMembers())for(BlockPos cell:member.cells())if(member.isReleasedCell(cell)&&(!world.isBlockLoaded(cell)||!world.isAirBlock(cell)&&!own(member,cell)))blocked.add(cell);
        // Ordinary closes do not tessellate a sweep. First inspect only loaded
        // ramp helper tiles in a conservative radius; ordinary bodywork retains
        // the established canopy policy.
        double radius=owner.width()+(owner.resizable()?owner.large.depth/16D:owner.block().length)+2;
        AxisAlignedBB area=new AxisAlignedBB(owner.getPos()).grow(radius);Set<BlockPos> candidates=new HashSet<>();
        for(int x=MathHelper.floor(area.minX)>>4;x<=MathHelper.floor(area.maxX)>>4;x++)for(int z=MathHelper.floor(area.minZ)>>4;z<=MathHelper.floor(area.maxZ)>>4;z++){
            net.minecraft.world.chunk.Chunk chunk=world.getChunkProvider().getLoadedChunk(x,z);if(chunk==null)continue;
            for(TileEntity tile:chunk.getTileEntityMap().values())if(tile instanceof TileEntityControlledRamp&&area.intersects(new AxisAlignedBB(tile.getPos())))candidates.add(tile.getPos());
        }
        if(!candidates.isEmpty()){
            int samples=64;double margin=radius*3D/samples;
            for(int sample=0;sample<=samples&&!candidates.isEmpty();sample++)for(CanopyMesh.Face face:owner.faces(owner.progress*sample/samples)){
                AxisAlignedBB bounds=null;for(Vec3d local:face.vertices){Vec3d point=CanopyMesh.rotate(local,owner.facing()).add(new Vec3d(owner.getPos()));AxisAlignedBB vertex=new AxisAlignedBB(point,point);bounds=bounds==null?vertex:bounds.union(vertex);}
                if(bounds==null)continue;AxisAlignedBB swept=bounds.grow(margin);
                for(java.util.Iterator<BlockPos> pending=candidates.iterator();pending.hasNext();){BlockPos cell=pending.next();if(swept.intersects(new AxisAlignedBB(cell))){blocked.add(cell);pending.remove();}}
            }
        }
        if(blocked.isEmpty()){if(restoreFootprint(owner))return true;for(TileEntityCanopy member:owner.reservationMembers())for(BlockPos cell:member.cells())if(member.isReleasedCell(cell))blocked.add(cell);}
        Map<BlockPos,List<WeakReference<TileEntityCanopy>>> waits=WAITS.computeIfAbsent(world,k->new HashMap<>());
        for(BlockPos cell:blocked){waits.computeIfAbsent(cell,k->new ArrayList<>()).add(new WeakReference<>(owner));TileEntity tile=world.isBlockLoaded(cell)?world.getTileEntity(cell):null;if(tile instanceof TileEntityControlledRamp)ramps.add(((TileEntityControlledRamp)tile).controller);}
        RampCanopyCoordination.waitingCanopy(owner,ramps);return false;
    }
    @SubscribeEvent public static void chunkLoaded(net.minecraftforge.event.world.ChunkEvent.Load event){
        if(event.getWorld().isRemote)return;Map<BlockPos,List<WeakReference<TileEntityCanopy>>> waits=WAITS.get(event.getWorld());if(waits==null)return;
        for(Map.Entry<BlockPos,List<WeakReference<TileEntityCanopy>>> entry:new ArrayList<>(waits.entrySet()))if((entry.getKey().getX()>>4)==event.getChunk().x&&(entry.getKey().getZ()>>4)==event.getChunk().z){
            waits.remove(entry.getKey());for(WeakReference<TileEntityCanopy> ref:entry.getValue()){TileEntityCanopy owner=ref.get();if(owner!=null&&!owner.isInvalid())owner.retryReservationClose();}
        }
    }
    @SubscribeEvent public static void neighbors(BlockEvent.NeighborNotifyEvent event){
        if(event.getWorld().isRemote)return;Map<BlockPos,List<WeakReference<TileEntityCanopy>>> waits=WAITS.get(event.getWorld());if(waits==null)return;
        Set<BlockPos> changed=new HashSet<>();changed.add(event.getPos());for(net.minecraft.util.EnumFacing side:event.getNotifiedSides())changed.add(event.getPos().offset(side));
        for(BlockPos pos:changed){List<WeakReference<TileEntityCanopy>> refs=waits.remove(pos);if(refs!=null)for(WeakReference<TileEntityCanopy> ref:refs){TileEntityCanopy owner=ref.get();if(owner!=null&&!owner.isInvalid())owner.retryReservationClose();}}
    }
}
