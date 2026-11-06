package com.vandorlabs.neoforge.screen;

import com.vandorlabs.neoforge.client.NativeScreenClientPower;
import com.vandorlabs.screen.*;
import java.lang.ref.WeakReference;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

/** Main-thread, loaded-only render rectangles. Cache values retain neither worlds nor hosts. */
public final class NativeScreenRenderGroups {
 private NativeScreenRenderGroups(){}
 private enum Kind {ARTWORK,FRAME,EDIT}
 private static final long BUDGET=4L*1024*1024,BASE_BYTES=1024;
 private record Group(NativeScreenClientPower.Lease lease){boolean touch(){return lease==null||lease.touch();}}
 private record Entry(WeakReference<NativeScreenEntity> owner,NativeScreenRenderProjection.Rectangle rectangle,Group group){}
 private record Published(WeakReference<NativeScreenEntity> owner,NativeScreenRenderProjection projection,Group artwork,Group frame){}
 private static final class Cache {
  long epoch,seen=-1,bytes;
  Set<Group> groups=Collections.newSetFromMap(new IdentityHashMap<>());
  final EnumMap<Kind,Map<BlockPos,Entry>> maps=new EnumMap<>(Kind.class);
  Map<BlockPos,Published> projections=new HashMap<>();
  Cache(){for(var kind:Kind.values())maps.put(kind,new HashMap<>());}
  void ready(){if(epoch!=seen){clear();seen=epoch;}}
  void clear(){for(var group:groups)if(group.lease!=null)group.lease.close();groups=Collections.newSetFromMap(new IdentityHashMap<>());for(var kind:Kind.values())maps.put(kind,new HashMap<>());projections=new HashMap<>();bytes=0;}
  void reserve(long next){if(next>BUDGET-BASE_BYTES)throw new IllegalStateException("Screen render group exceeds byte budget");if(bytes+next>BUDGET-BASE_BYTES)clear();}
 }
 private static final Map<Level,Cache> CACHES=new WeakHashMap<>();
 private static boolean bound;
 /** Explicit opt-in: production client bindings remain the caller's responsibility. */
 public static void bind(){thread();if(bound)return;NativeScreenPowerViews.bindRender(tile->{var cache=CACHES.get(tile.getLevel());if(cache!=null)cache.epoch++;});bound=true;}
 public static void clear(){thread();for(var cache:CACHES.values())cache.clear();CACHES.clear();}
 public static long retainedBytes(){thread();long bytes=0;for(var cache:CACHES.values())bytes+=BASE_BYTES+cache.bytes;return bytes;}
 private static void thread(){if(!Minecraft.getInstance().isSameThread())throw new IllegalStateException("Screen render grouping requires the client thread");}
 private static Cache cache(Level level){Cache cache=CACHES.get(level);if(cache==null){if(CACHES.size()>=2)clear();cache=new Cache();CACHES.put(level,cache);}cache.ready();return cache;}
 /** Query only the branches actually reached by the released renderer. */
 public static NativeScreenRenderProjection.Rectangle artwork(NativeScreenEntity tile){thread();return rectangle(tile,Kind.ARTWORK);}
 public static NativeScreenRenderProjection.Rectangle frame(NativeScreenEntity tile){thread();return rectangle(tile,Kind.FRAME);}
 /** Convenience for callers which explicitly need both independent layouts. */
 public static NativeScreenRenderProjection projection(NativeScreenEntity tile){
  thread();var level=tile.getLevel();if(level==null||!level.isClientSide||!installed(level,tile))return NativeScreenRenderProjection.SINGLE;var cache=cache(level);var previous=cache.projections.get(tile.getBlockPos());if(previous!=null&&previous.owner.get()==tile&&previous.artwork.touch()&&previous.frame.touch())return previous.projection;
  var artwork=rectangle(tile,Kind.ARTWORK);var frame=rectangle(tile,Kind.FRAME);cache.ready();var projection=new NativeScreenRenderProjection(artwork,frame);if(cache.bytes+192>BUDGET-BASE_BYTES){cache.clear();return projection(tile);}cache.reserve(192);var artEntry=cache.maps.get(Kind.ARTWORK).get(tile.getBlockPos());var frameEntry=cache.maps.get(Kind.FRAME).get(tile.getBlockPos());var artGroup=artEntry==null?new Group(null):artEntry.group;var frameGroup=frameEntry==null?new Group(null):frameEntry.group;var old=cache.projections.put(tile.getBlockPos(),new Published(new WeakReference<>(tile),projection,artGroup,frameGroup));if(old==null)cache.bytes+=192;return projection;
 }
 /** Released editor aspect uses authored membership, never power-dependent render membership. */
 public static boolean wideDisplay(NativeScreenEntity tile){thread();if(tile.family()!=ScreenFamily.SCREEN&&tile.family()!=ScreenFamily.INPUT)return false;var rectangle=rectangle(tile,Kind.EDIT);return rectangle.columns()>rectangle.rows();}
 private static NativeScreenRenderProjection.Rectangle rectangle(NativeScreenEntity origin,Kind kind){
  var level=origin.getLevel();if(level==null||!level.isClientSide||!installed(level,origin))return NativeScreenRenderProjection.Rectangle.SINGLE;var cache=cache(level);var map=cache.maps.get(kind);var hit=map.get(origin.getBlockPos());if(hit!=null&&hit.owner.get()==origin){if(hit.group.touch())return hit.rectangle;cache.clear();}
  var queries=new IdentityHashMap<NativeScreenEntity,Integer>();var visual=origin.visual();var state=origin.getBlockState();var block=(NativeScreenBlock)state.getBlock();var basis=ScreenJoinGeometry.basis(origin.family(),block.screenState(state),visual.settings().ceilingMounted());boolean eligible=ScreenJoinGeometry.supports(origin.family(),visual.settings().scalar().smallInput);var firstSignature=kind==Kind.EDIT?origin.joinSignature():null;if(kind==Kind.EDIT)eligible&=firstSignature.editEligible(origin.family());
  var access=new ScreenRectangleGroups.Access<NativeScreenEntity>(){
   public int x(NativeScreenEntity tile){return tile.getBlockPos().getX();}public int y(NativeScreenEntity tile){return tile.getBlockPos().getY();}public int z(NativeScreenEntity tile){return tile.getBlockPos().getZ();}
   public boolean loaded(int x,int y,int z){return !level.isOutsideBuildHeight(y)&&level.getChunkSource().getChunkNow(x>>4,z>>4)!=null;}
   public NativeScreenEntity at(int x,int y,int z){var chunk=level.getChunkSource().getChunkNow(x>>4,z>>4);if(chunk==null)return null;var raw=chunk.getBlockEntities().get(new BlockPos(x,y,z));return raw instanceof NativeScreenEntity tile?tile:null;}
   public boolean compatible(NativeScreenEntity ignored,NativeScreenEntity candidate){if(!installed(level,candidate)||candidate.getBlockState()!=state||candidate.family()!=origin.family())return false;if(kind==Kind.EDIT)return firstSignature.editMatches(candidate.joinSignature());return kind==Kind.FRAME?NativeScreenRenderMatching.matches(origin.visual(),candidate.visual(),true):matchingPrepared(origin,candidate,queries);}
  };
  var result=ScreenRectangleGroups.discover(origin,basis,access,eligible);cache.ready();map=cache.maps.get(kind);
  List<NativeScreenClientPower.Query> requested=new ArrayList<>(queries.size());for(var query:queries.entrySet())requested.add(new NativeScreenClientPower.Query(query.getKey(),query.getValue()));
  long estimated=256L+result.discovered.size()*192L+queries.size()*64L;cache.reserve(estimated);NativeScreenClientPower.Lease lease=null;if(!requested.isEmpty()){lease=NativeScreenClientPower.lease(requested);if(lease==null){cache.clear();lease=NativeScreenClientPower.lease(requested);}if(lease==null)throw new IllegalStateException("Screen prepared-power lease capacity exhausted");}map=cache.maps.get(kind);var group=new Group(lease);cache.groups.add(group);cache.bytes+=128+(lease==null?0:lease.retainedBytes());
  for(var members:result.rectangles){int minX=Integer.MAX_VALUE,minY=minX,maxX=Integer.MIN_VALUE,maxY=maxX;for(var tile:members){long cell=basis.cell(tile.getBlockPos().getX(),tile.getBlockPos().getY(),tile.getBlockPos().getZ());int x=(int)(cell>>32),y=(int)cell;minX=Math.min(minX,x);minY=Math.min(minY,y);maxX=Math.max(maxX,x);maxY=Math.max(maxY,y);}for(var tile:members){long cell=basis.cell(tile.getBlockPos().getX(),tile.getBlockPos().getY(),tile.getBlockPos().getZ());var rectangle=new NativeScreenRenderProjection.Rectangle((int)(cell>>32)-minX,(int)cell-minY,maxX-minX+1,maxY-minY+1);var old=map.put(tile.getBlockPos(),new Entry(new WeakReference<>(tile),rectangle,group));if(old==null)cache.bytes+=192;}}
  return map.get(origin.getBlockPos()).rectangle;
 }
 private static boolean matchingPrepared(NativeScreenEntity a,NativeScreenEntity b,IdentityHashMap<NativeScreenEntity,Integer> queries){
  var av=a.visual();var bv=b.visual();if(!NativeScreenRenderMatching.sameShape(av,bv)||av.frames().displayFit!=bv.frames().displayFit)return false;
  av=prepared(a,queries,NativeScreenClientPower.SPLIT);bv=prepared(b,queries,NativeScreenClientPower.SPLIT);if(av.appearance().split!=bv.appearance().split)return false;if(av.appearance().split&&(av.frames().splitTop!=bv.frames().splitTop||av.frames().splitBottom!=bv.frames().splitBottom))return false;
  av=prepared(a,queries,NativeScreenClientPower.PRIMARY_ARTWORK);bv=prepared(b,queries,NativeScreenClientPower.PRIMARY_ARTWORK);if(av.appearance().primaryArtwork!=bv.appearance().primaryArtwork)return false;
  av=prepared(a,queries,NativeScreenClientPower.EFFECTIVE_MODE);bv=prepared(b,queries,NativeScreenClientPower.EFFECTIVE_MODE);if(av.appearance().effectiveMode!=bv.appearance().effectiveMode)return false;
  av=prepared(a,queries,NativeScreenClientPower.ANIMATION_SPEED);bv=prepared(b,queries,NativeScreenClientPower.ANIMATION_SPEED);return av.appearance().animationSpeed==bv.appearance().animationSpeed;
 }
 private static NativeScreenEntity.Visual prepared(NativeScreenEntity tile,IdentityHashMap<NativeScreenEntity,Integer> queries,int mask){queries.merge(tile,mask,(a,b)->a|b);if(queries.size()>4096)throw new IllegalStateException("Screen render getter member bound exceeded");return NativeScreenClientPower.prepare(tile,mask);}
 private static boolean installed(Level level,NativeScreenEntity tile){if(level!=Minecraft.getInstance().level||tile.isRemoved()||tile.inert()||tile.getLevel()!=level)return false;var pos=tile.getBlockPos();var chunk=level.getChunkSource().getChunkNow(pos.getX()>>4,pos.getZ()>>4);return chunk!=null&&chunk.getBlockEntities().get(pos)==tile&&chunk.getBlockState(pos)==tile.getBlockState()&&tile.getBlockState().getBlock() instanceof NativeScreenBlock block&&block.family()==tile.family();}
}
