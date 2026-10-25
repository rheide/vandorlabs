package com.vandorlabs.neoforge.fluid;

import com.vandorlabs.fluids.FluidVolume;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.material.FlowingFluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.SoundActions;
import net.neoforged.neoforge.common.Tags;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.FluidType;
import net.neoforged.neoforge.fluids.FluidUtil;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;

/** Loaded-world lifecycle and existing bucket operations over shared, immutable partial volumes. */
@EventBusSubscriber(modid="vandorlabs")
public final class NativeFluidWorld {
    private static final Map<Level,Set<ContainedFluidHost>> PENDING=Collections.synchronizedMap(new IdentityHashMap<>());
    private static final Map<Level,Set<Long>> CHUNK_PENDING=Collections.synchronizedMap(new IdentityHashMap<>());
    private static final Map<Level,Long> CHUNK_INVALIDATIONS=Collections.synchronizedMap(new IdentityHashMap<>());
    private record Revision(long fluid,long geometry){}
    private static final Map<ContainedFluidHost,Revision> PUBLISHED=Collections.synchronizedMap(new java.util.WeakHashMap<>());
    private NativeFluidWorld(){}
    /** Local-only onLoad operation: identity and geometry are checked after loading has finished. */
    public static void queue(ContainedFluidHost host){Level level=host.fluidOwner().getLevel();if(level!=null)PENDING.computeIfAbsent(level,key->Collections.newSetFromMap(new IdentityHashMap<>())).add(host);}
    public static void removed(ContainedFluidHost host){PUBLISHED.remove(host);Level level=host.fluidOwner().getLevel();if(level!=null)queueNeighbors(level,host.fluidOwner().getBlockPos());Set<ContainedFluidHost> pending=PENDING.get(level);if(pending!=null)pending.remove(host);if(level!=null&&level.isClientSide)net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(new NativeFluidRenderSnapshot.Remove(host));if(level!=null&&loaded(level,host.fluidOwner().getBlockPos())){var manager=level.getAuxLightManager(host.fluidOwner().getBlockPos());if(manager!=null)manager.removeLightAt(host.fluidOwner().getBlockPos());}}
    public static void unloaded(ContainedFluidHost host){PUBLISHED.remove(host);Level level=host.fluidOwner().getLevel();Set<ContainedFluidHost> pending=PENDING.get(level);if(pending!=null)pending.remove(host);if(level!=null&&level.isClientSide)net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(new NativeFluidRenderSnapshot.Remove(host));}
    public static boolean loaded(Level level,BlockPos pos){return level.getChunkSource().hasChunk(pos.getX()>>4,pos.getZ()>>4);}
    public static boolean installed(ContainedFluidHost host){BlockEntity owner=host.fluidOwner();Level level=owner.getLevel();return level!=null&&!owner.isRemoved()&&loaded(level,owner.getBlockPos())&&level.getBlockEntity(owner.getBlockPos())==owner;}
    @SubscribeEvent public static void tick(LevelTickEvent.Post event){
        Level level=event.getLevel();Set<Long> chunks=CHUNK_PENDING.remove(level);
        if(chunks!=null)for(long packed:chunks){int cx=net.minecraft.world.level.ChunkPos.getX(packed),cz=net.minecraft.world.level.ChunkPos.getZ(packed);
            for(int dx=-1;dx<=1;dx++)for(int dz=-1;dz<=1;dz++){
                var chunk=level.getChunkSource().getChunkNow(cx+dx,cz+dz);if(chunk==null)continue;
                for(var owner:chunk.getBlockEntities().values())if(owner instanceof ContainedFluidHost host){
                    int x=owner.getBlockPos().getX()&15,z=owner.getBlockPos().getZ()&15;
                    if(dx<0&&x!=15||dx>0&&x!=0||dz<0&&z!=15||dz>0&&z!=0)continue;
                    if(installed(host)){queue(host);CHUNK_INVALIDATIONS.merge(level,1L,Long::sum);}
                }
            }
        }
        Set<ContainedFluidHost> pending=PENDING.remove(level);if(pending!=null){
            // Resolve all queued geometry before any snapshot samples a neighbouring host.
            for(var host:pending)if(installed(host))host.refreshFluidGeometry();
            for(var host:pending)if(installed(host))changed(host);
        }
    }
    /** Chunk events only enqueue coordinates; neighbour reads wait for the post-load level tick. */
    @SubscribeEvent public static void chunkLoaded(ChunkEvent.Load event){chunkChanged(event);}
    @SubscribeEvent public static void chunkUnloaded(ChunkEvent.Unload event){chunkChanged(event);}
    private static void chunkChanged(ChunkEvent event){if(event.getLevel() instanceof Level level)CHUNK_PENDING.computeIfAbsent(level,key->new java.util.HashSet<>()).add(event.getChunk().getPos().toLong());}
    public static long chunkInvalidations(Level level){return CHUNK_INVALIDATIONS.getOrDefault(level,0L);}
    @SubscribeEvent public static void unload(LevelEvent.Unload event){PENDING.remove(event.getLevel());CHUNK_PENDING.remove(event.getLevel());CHUNK_INVALIDATIONS.remove(event.getLevel());PUBLISHED.keySet().removeIf(host->host.fluidOwner().getLevel()==event.getLevel());}
    public static void changed(ContainedFluidHost host){if(!installed(host))return;host.refreshFluidGeometry();BlockEntity owner=host.fluidOwner();Level level=owner.getLevel();
        Revision next=new Revision(host.containedFluids().revision(),host.fluidGeometryRevision());
        if(!next.equals(PUBLISHED.put(host,next)))queueNeighbors(level,owner.getBlockPos());
        int light=0;for(int side=0;side<2;side++){var fluid=host.containedFluids().get(side);if(!fluid.isEmpty())light=Math.max(light,fluid.getFluidType().getLightLevel(fluid));}var manager=level.getAuxLightManager(owner.getBlockPos());if(manager!=null&&manager.getLightAt(owner.getBlockPos())!=light){manager.setLightAt(owner.getBlockPos(),light);level.getChunkSource().getLightEngine().checkBlock(owner.getBlockPos());}if(level.isClientSide){owner.requestModelDataUpdate();level.setBlocksDirty(owner.getBlockPos(),owner.getBlockState(),owner.getBlockState());net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(new NativeFluidRenderSnapshot.Update(host));}}
    /** Block callbacks only enqueue an already-installed loaded host. */
    public static void queueAt(net.minecraft.world.level.LevelAccessor accessor,BlockPos pos){if(accessor instanceof Level level&&loaded(level,pos)&&level.getBlockEntity(pos) instanceof ContainedFluidHost host)queue(host);}
    public static void blockChanged(Level level,BlockPos pos){queueAt(level,pos);queueNeighbors(level,pos);}
    private static void queueNeighbors(Level level,BlockPos pos){
        // Corner heights sample diagonal columns and their above cells as well as direct faces.
        for(int x=-1;x<=1;x++)for(int y=-1;y<=1;y++)for(int z=-1;z<=1;z++){
            if(x==0&&y==0&&z==0)continue;BlockPos neighbor=pos.offset(x,y,z);
            if(loaded(level,neighbor)&&level.getBlockEntity(neighbor) instanceof ContainedFluidHost other)queue(other);
        }
    }
    /** Lighting threads consult the auxiliary manager, never a block entity or neighbouring chunk. */
    public static int light(BlockGetter level,BlockPos pos){var manager=level.getAuxLightManager(pos);return manager==null?0:manager.getLightAt(pos);}
    public static boolean gaseous(FluidStack fluid){return !fluid.isEmpty()&&(fluid.is(Tags.Fluids.GASEOUS)||fluid.getFluidType().getDensity(fluid)<=0);}
    public static FluidStack fromState(FluidState state){if(state.isEmpty())return FluidStack.EMPTY;var fluid=state.getType();if(fluid instanceof FlowingFluid flowing)fluid=flowing.getSource();return new FluidStack(fluid,1000);}
    public static FluidStack atPoint(Level level,Vec3 point){BlockPos pos=BlockPos.containing(point);if(!loaded(level,pos)||!(level.getBlockEntity(pos) instanceof ContainedFluidHost host))return FluidStack.EMPTY;int side=host.fluidVolumeSnapshot().sideAt(point.x-pos.getX(),point.y-pos.getY(),point.z-pos.getZ());return side<0?FluidStack.EMPTY:host.containedFluids().get(side);}
    public static net.minecraft.world.level.block.state.BlockState viewpoint(net.minecraft.world.level.block.state.BlockState original,BlockGetter getter,BlockPos pos,Vec3 point){if(!(getter instanceof Level level)||!BlockPos.containing(point).equals(pos))return original;FluidStack fluid=atPoint(level,point);if(fluid.isEmpty())return original;var state=fluid.getFluid().defaultFluidState().createLegacyBlock();return state.isAir()?original:state;}
    /** Per-type heights follow only actual collision-free cells, with no world reads outside loaded chunks. */
    public static void immersion(Level level,AABB bounds,java.util.function.BiConsumer<FluidType,Double> consumer){
        int x0=Mth.floor(bounds.minX),x1=Mth.ceil(bounds.maxX),y0=Mth.floor(bounds.minY),y1=Mth.ceil(bounds.maxY),z0=Mth.floor(bounds.minZ),z1=Mth.ceil(bounds.maxZ);BlockPos.MutableBlockPos pos=new BlockPos.MutableBlockPos();Map<FluidType,Double> heights=new IdentityHashMap<>();
        for(int x=x0;x<x1;x++)for(int y=y0;y<y1;y++)for(int z=z0;z<z1;z++){pos.set(x,y,z);if(!loaded(level,pos)||!(level.getBlockEntity(pos) instanceof ContainedFluidHost host))continue;var data=host.containedFluids().snapshot();for(var cell:host.fluidVolumeSnapshot().cells){var type=data.side(cell.side).fluidType();if(type==null)continue;var b=cell.box;if(bounds.maxX>x+b.minX&&bounds.minX<x+b.maxX&&bounds.maxY>y+b.minY&&bounds.minY<y+b.maxY&&bounds.maxZ>z+b.minZ&&bounds.minZ<z+b.maxZ)heights.merge(type,y+b.maxY-bounds.minY,Math::max);}}
        heights.forEach(consumer);
    }
    @SubscribeEvent(priority=EventPriority.LOWEST) public static void bucket(PlayerInteractEvent.RightClickBlock event){
        var player=event.getEntity();Level level=player.level();BlockPos pos=event.getPos();if(!loaded(level,pos)||!(level.getBlockEntity(pos) instanceof ContainedFluidHost host))return;
        ItemStack held=player.getItemInHand(event.getHand()),single=held.copyWithCount(1);var optional=FluidUtil.getFluidHandler(single);if(optional.isEmpty())return;var container=optional.get();Direction face=event.getHitVec().getDirection();
        if(!player.isAlive()||player.isSpectator()||!level.mayInteract(player,pos)||!player.mayUseItemAt(pos,face,held))return;
        Vec3 hit=event.getHitVec().getLocation().subtract(pos.getX(),pos.getY(),pos.getZ()).add(face.getStepX()*.001,face.getStepY()*.001,face.getStepZ()*.001);int side=host.containedFluidSide(hit.x,hit.y,hit.z);if(side<0||side>1)return;
        FluidStack incoming=container.drain(1000,IFluidHandler.FluidAction.SIMULATE),stored=host.containedFluids().get(side);boolean fill=!incoming.isEmpty();event.setCanceled(true);event.setCancellationResult(InteractionResult.FAIL);
        if(fill){if(!FluidVolume.canFill(incoming.getAmount(),gaseous(incoming),host.containedFluids().occupied(side),host.fluidVolumeSnapshot().hasSide(side),host.openingFluidVolume()))return;}
        else if(!FluidVolume.canDrain(!stored.isEmpty(),container.fill(stored,IFluidHandler.FluidAction.SIMULATE)))return;
        event.setCancellationResult(InteractionResult.SUCCESS);if(level.isClientSide)return;if(!installed(host))return;
        if(fill)container.drain(1000,IFluidHandler.FluidAction.EXECUTE);else container.fill(stored,IFluidHandler.FluidAction.EXECUTE);
        host.containedFluids().set(side,fill?incoming:FluidStack.EMPTY);
        if(!player.getAbilities().instabuild){ItemStack result=container.getContainer();held.shrink(1);if(held.isEmpty())player.setItemInHand(event.getHand(),result);else if(!player.getInventory().add(result))player.drop(result,false);}
        var sound=(fill?incoming:stored).getFluidType().getSound(fill?incoming:stored,fill?SoundActions.BUCKET_EMPTY:SoundActions.BUCKET_FILL);if(sound!=null)level.playSound(null,pos,sound,SoundSource.BLOCKS,1,1);
    }
    @SubscribeEvent(priority=EventPriority.LOWEST) public static void placed(BlockEvent.EntityPlaceEvent event){
        if(!(event.getLevel() instanceof Level level)||level.isClientSide)return;
        var snapshots=event instanceof BlockEvent.EntityMultiPlaceEvent multi?multi.getReplacedBlockSnapshots():java.util.List.of(event.getBlockSnapshot());Map<ContainedFluidHost,Placement> fills=new IdentityHashMap<>();
        for(var snapshot:snapshots){BlockPos pos=snapshot.getPos();if(!loaded(level,pos)||!(level.getBlockEntity(pos) instanceof ContainedFluidHost host))continue;var fill=fills.computeIfAbsent(host,key->new Placement());FluidStack replaced=fromState(snapshot.getState().getFluidState());if(!replaced.isEmpty()&&!gaseous(replaced)&&fill.fallback.isEmpty())fill.fallback=replaced;
            for(Direction direction:host.fluidPlacementFaces()){BlockPos next=pos.relative(direction);if(!loaded(level,next))continue;FluidStack adjacent=fromState(level.getFluidState(next));if(adjacent.isEmpty()||gaseous(adjacent))continue;int side=host.containedFluidSide(.5+direction.getStepX()*.51,.5+direction.getStepY()*.51,.5+direction.getStepZ()*.51);if(side>=0&&side<2)fill.sides[side]=adjacent;}}
        for(var entry:fills.entrySet()){var host=entry.getKey();var fill=entry.getValue();if(!installed(host))continue;boolean boundary=!fill.sides[0].isEmpty()||!fill.sides[1].isEmpty();for(int side=0;side<2;side++){FluidStack fluid=boundary?fill.sides[side]:fill.fallback;if(!fluid.isEmpty()&&!host.containedFluids().occupied(side)&&(host.openingFluidVolume()||host.fluidVolumeSnapshot().hasSide(side)))host.containedFluids().set(side,fluid);}}
    }
    private static final class Placement {FluidStack fallback=FluidStack.EMPTY;final FluidStack[] sides={FluidStack.EMPTY,FluidStack.EMPTY};}
}
