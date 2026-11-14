package com.vandorlabs.neoforge.reservoir;
import java.util.*;import java.lang.ref.WeakReference;import java.util.concurrent.atomic.AtomicLong;import net.minecraft.core.*;import net.minecraft.world.level.Level;import com.vandorlabs.render.ConnectedSquare;import net.neoforged.neoforge.fluids.FluidStack;
/** Same bounded complete 2/3-cell cross sections and axial grouping as Source. No chunk loading. */
public final class NativeTankGroups {
 private static final int MAX_PARTS=4096,MAX_DESCRIPTORS=1024,MAX_RETAINED_CELLS=16384;
 private static final Map<Level,Cache> CACHES=new WeakHashMap<>();
 private static final AtomicLong MEMBERSHIP_BUILDS=new AtomicLong();
 private static final class Cache {
  long tick=Long.MIN_VALUE;int retainedCells;
  final Map<BlockPos,ConnectedSquare.Part> parts=new HashMap<>();
  final LinkedHashMap<BlockPos,Descriptor> members=new LinkedHashMap<>(16,.75f,true);
  void clear(){parts.clear();members.clear();retainedCells=0;}
  void epoch(Level level){if(tick!=level.getGameTime()){tick=level.getGameTime();clear();}}
 }
 /** Weak cells keep the weak world key from being retained through its block entities.
  * Indexed capability inspection remains O(1), without cloning an entire group per getter. */
 private static final class Descriptor extends AbstractList<NativeHexTankEntity> implements RandomAccess {
  final WeakReference<NativeHexTankEntity> owner;
  final List<WeakReference<NativeHexTankEntity>> cells;
  Descriptor(NativeHexTankEntity owner,List<NativeHexTankEntity> cells){this.owner=new WeakReference<>(owner);this.cells=cells.stream().map(WeakReference::new).toList();}
  @Override public int size(){return cells.size();}
  @Override public NativeHexTankEntity get(int index){return Objects.requireNonNull(cells.get(index).get(),"Unloaded tank membership");}
 }
 public static void invalidate(Level level){if(level!=null){Cache found;synchronized(CACHES){found=CACHES.get(level);}if(found!=null)synchronized(found){found.clear();}}}
 public static void clear(Level level){Cache found;synchronized(CACHES){found=CACHES.remove(level);}if(found!=null)synchronized(found){found.clear();}}
 private static Cache cache(Level level){synchronized(CACHES){return CACHES.computeIfAbsent(level,unused->new Cache());}}
 /** Structural diagnostics only; amount reads and indexed getters do not increment this. */
 public static long membershipBuildCount(){return MEMBERSHIP_BUILDS.get();}
 public static int maxRetainedCells(){return MAX_RETAINED_CELLS;}
 public static int retainedCells(Level level){Cache found;synchronized(CACHES){found=CACHES.get(level);}if(found==null)return 0;synchronized(found){found.epoch(level);return found.retainedCells;}}

 @net.neoforged.fml.common.EventBusSubscriber(modid="vandorlabs")
 public static final class Lifecycle {
  @net.neoforged.bus.api.SubscribeEvent public static void unload(net.neoforged.neoforge.event.level.LevelEvent.Unload event){if(event.getLevel() instanceof Level level)clear(level);}
  @net.neoforged.bus.api.SubscribeEvent public static void chunkLoaded(net.neoforged.neoforge.event.level.ChunkEvent.Load event){if(event.getLevel() instanceof Level level)invalidate(level);}
  @net.neoforged.bus.api.SubscribeEvent public static void chunkUnloaded(net.neoforged.neoforge.event.level.ChunkEvent.Unload event){if(event.getLevel() instanceof Level level)invalidate(level);}
  private Lifecycle(){}
 }

 public static Direction right(Direction.Axis axis){return axis==Direction.Axis.X?Direction.SOUTH:Direction.EAST;}public static Direction up(Direction.Axis axis){return axis==Direction.Axis.Y?Direction.SOUTH:Direction.UP;}
 private static boolean same(NativeHexTankEntity owner,BlockPos pos){var level=owner.getLevel();return level!=null&&level.hasChunkAt(pos)&&level.getBlockEntity(pos) instanceof NativeHexTankEntity next&&!next.inert()&&!next.isRemoved()&&(level.isClientSide||next.installed())&&next.facing().getAxis()==owner.facing().getAxis();}
 private static ConnectedSquare.Part find(NativeHexTankEntity owner){if(owner.getLevel()==null||owner.inert()||owner.blockShape())return ConnectedSquare.Part.SINGLE;var level=owner.getLevel();var origin=owner.getBlockPos();var axis=owner.facing().getAxis();boolean[] unloaded={false};var part=ConnectedSquare.findPartitioned(3,(x,y)->{var pos=origin.relative(right(axis),x).relative(up(axis),y);if(!level.hasChunkAt(pos)){unloaded[0]=true;return false;}if(!same(owner,pos))return false;var next=(NativeHexTankEntity)level.getBlockEntity(pos);return !next.blockShape()&&next.settings().mainTexture==owner.settings().mainTexture&&next.settings().layout==owner.settings().layout;});if(unloaded[0])return ConnectedSquare.Part.SINGLE;FluidStack contents=FluidStack.EMPTY;var root=root(owner,part);for(int x=0;x<part.size;x++)for(int y=0;y<part.size;y++){var next=(NativeHexTankEntity)level.getBlockEntity(root.relative(right(axis),x).relative(up(axis),y));var fluid=next.storedFluid();if(!fluid.isEmpty()){if(!contents.isEmpty()&&!FluidStack.isSameFluidSameComponents(contents,fluid))return ConnectedSquare.Part.SINGLE;contents=fluid;}}return part;}
 public static ConnectedSquare.Part part(NativeHexTankEntity owner){if(owner.getLevel()==null)return ConnectedSquare.Part.SINGLE;var level=owner.getLevel();if(com.vandorlabs.neoforge.ramp.NativeRampHeldOwners.active(level))return find(owner);var cache=cache(level);synchronized(cache){cache.epoch(level);var found=cache.parts.get(owner.getBlockPos());if(found!=null)return found;var part=find(owner);if(cache.parts.size()+part.size*part.size>MAX_PARTS)cache.parts.clear();var root=root(owner,part);var axis=owner.facing().getAxis();for(int x=0;x<part.size;x++)for(int y=0;y<part.size;y++)cache.parts.put(root.relative(right(axis),x).relative(up(axis),y),new ConnectedSquare.Part(part.size,x,y));return part;}}
 public static BlockPos root(NativeHexTankEntity owner,ConnectedSquare.Part part){return owner.getBlockPos().relative(right(owner.facing().getAxis()),-part.x).relative(up(owner.facing().getAxis()),-part.y);}
 public static List<NativeHexTankEntity> members(NativeHexTankEntity owner){
  var level=owner.getLevel();if(owner.inert()||level==null||owner.isRemoved()||!level.isClientSide&&!owner.installed())return List.of(owner);
  // Ownership holds may change inside this tick without a block mutation. Never cache their admission result.
  if(com.vandorlabs.neoforge.ramp.NativeRampHeldOwners.active(level)){invalidate(level);return buildMembers(owner);}
  var cache=cache(level);synchronized(cache){
   cache.epoch(level);var found=cache.members.get(owner.getBlockPos());
   if(found!=null&&found.owner.get()==owner)return found;
   if(found!=null){cache.members.remove(owner.getBlockPos());cache.retainedCells-=found.size();}
   var cells=buildMembers(owner);var descriptor=new Descriptor(owner,cells);
   while(!cache.members.isEmpty()&&(cache.members.size()>=MAX_DESCRIPTORS||cache.retainedCells+descriptor.size()>MAX_RETAINED_CELLS)){
    var iterator=cache.members.entrySet().iterator();var oldest=iterator.next();cache.retainedCells-=oldest.getValue().size();iterator.remove();
   }
   cache.members.put(owner.getBlockPos().immutable(),descriptor);cache.retainedCells+=descriptor.size();return descriptor;
  }
 }
 private static List<NativeHexTankEntity> buildMembers(NativeHexTankEntity owner){
  MEMBERSHIP_BUILDS.incrementAndGet();
  var result=new LinkedHashMap<BlockPos,NativeHexTankEntity>();var pending=new ArrayDeque<NativeHexTankEntity>();pending.add(owner);
  var axis=owner.facing().getAxis();var step=Direction.get(Direction.AxisDirection.POSITIVE,axis);var reverse=step.getOpposite();
  while(!pending.isEmpty()&&result.size()<256){
   var cell=pending.remove();if(result.putIfAbsent(cell.getBlockPos(),cell)!=null)continue;
   var part=part(cell);var root=root(cell,part);
   for(int x=0;x<part.size;x++)for(int y=0;y<part.size;y++)enqueue(owner,root.relative(right(axis),x).relative(up(axis),y),result,pending);
   enqueue(owner,cell.getBlockPos().relative(step),result,pending);enqueue(owner,cell.getBlockPos().relative(reverse),result,pending);
  }
  var cells=new ArrayList<>(result.values());cells.sort(Comparator.comparingInt((NativeHexTankEntity cell)->axis==Direction.Axis.X?cell.getBlockPos().getX():axis==Direction.Axis.Y?cell.getBlockPos().getY():cell.getBlockPos().getZ()).thenComparingLong(cell->legacyOrder(cell.getBlockPos())));return cells;
 }
 private static void enqueue(NativeHexTankEntity owner,BlockPos pos,Map<BlockPos,NativeHexTankEntity> result,ArrayDeque<NativeHexTankEntity> pending){if(!result.containsKey(pos)&&same(owner,pos))pending.add((NativeHexTankEntity)owner.getLevel().getBlockEntity(pos));}
 private static long legacyOrder(BlockPos p){return ((long)p.getX()&0x3ffffffL)<<38|((long)p.getY()&0xfffL)<<26|((long)p.getZ()&0x3ffffffL);}
 private NativeTankGroups(){}
}
