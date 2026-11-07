package com.vandorlabs.neoforge.screen;

import com.vandorlabs.neoforge.NativeMountedSwitchBlock;
import com.vandorlabs.neoforge.credentials.*;
import com.vandorlabs.screen.*;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

/** Loaded, Source-authored regular-Screen chip rectangles. No inventory or host enters cached values. */
public final class NativeScreenChipGroups {
 private NativeScreenChipGroups(){}
 /** NONE, EMPTY or OCCUPIED; implementations must use only an installed loaded-local reader. */
 @FunctionalInterface public interface ReaderLookup {int at(Level level,BlockPos position,Direction outward);}
 public static final int NONE=0,EMPTY=1,OCCUPIED=2;
 private static final Direction[] SIDES=Direction.values();
 public record Group(List<BlockPos> members,BlockPos owner,BlockPos reader,int columns,int rows,int minColumn,int minRow,AABB bounds,int facing){
  public Group{members=List.copyOf(members);Objects.requireNonNull(owner);Objects.requireNonNull(bounds);}
  /** Released page-hit conversion; the physical leaf hit column remains in authored 0..128 pixels. */
  public double column(BlockPos member,double localColumn){var basis=ScreenJoinGeometry.basis(ScreenFamily.SCREEN,new ScreenFamily.State(facing,false,false,false),false);int col=(int)(basis.cell(member.getX(),member.getY(),member.getZ())>>32)-minColumn;if(facing==1)col=columns-1-col;return(columns-1-col+localColumn/128)*128/columns;}
 }
 private static int reader(Level level,BlockPos pos,Direction outward){var chunk=level.getChunkSource().getChunkNow(pos.getX()>>4,pos.getZ()>>4);if(chunk==null)return NONE;var state=chunk.getBlockState(pos);if(!(state.getBlock() instanceof NativeCredentialReaderBlock block)||!block.chip()||state.getValue(NativeMountedSwitchBlock.FACING)!=outward)return NONE;var raw=chunk.getBlockEntities().get(pos);if(!(raw instanceof NativeCredentialReaderEntity tile)||tile.isRemoved()||tile.inert()||!tile.chip()||tile.getLevel()!=level||tile.getBlockState()!=state)return NONE;return tile.visual().contents().occupied()?OCCUPIED:EMPTY;}
 private static java.util.function.BooleanSupplier clientThread=()->false;
 /** Explicit client binding; the supplier must capture no world or host. */
 public static void bindClientThread(java.util.function.BooleanSupplier check){clientThread=Objects.requireNonNull(check);}
 private static final Service DEFAULT=new Service(NativeScreenChipGroups::reader);
 public static Group group(NativeScreenEntity owner){return DEFAULT.group(owner);}
 /** Invalidation has no world/neighbor queries and is safe during local-only onLoad. */
 public static void changed(Level level,BlockPos position){DEFAULT.changed(level,position);}
 public static void clear(Level level){DEFAULT.clear(level);}
 public static long retainedBytes(){return DEFAULT.retainedBytes();}
 public static final class Service {
  private static final long BUDGET=4L*1024*1024,BASE=1024;
  private static final class Cache {Map<BlockPos,Group> groups=new HashMap<>();long bytes=BASE;void clear(){groups=new HashMap<>();bytes=BASE;}}
  private final ReaderLookup readers;private final Map<Level,Cache> worlds=new WeakHashMap<>();
  public Service(ReaderLookup readers){this.readers=Objects.requireNonNull(readers);}
  public synchronized void changed(Level level,BlockPos position){var cache=worlds.get(level);if(cache!=null)cache.clear();}
  public synchronized void clear(Level level){worlds.remove(level);}
  public synchronized long retainedBytes(){long bytes=0;for(var cache:worlds.values())bytes+=cache.bytes;return bytes;}
  private static void thread(Level level){if(level instanceof ServerLevel server&&!server.getServer().isSameThread())throw new IllegalStateException("Screen chip discovery requires owning server thread");if(level.isClientSide&&!clientThread.getAsBoolean())throw new IllegalStateException("Screen chip discovery requires owning client thread");}
  private static boolean installed(Level level,NativeScreenEntity tile){if(tile==null||tile.inert()||tile.isRemoved()||tile.getLevel()!=level||tile.family()!=ScreenFamily.SCREEN)return false;var pos=tile.getBlockPos();var chunk=level.getChunkSource().getChunkNow(pos.getX()>>4,pos.getZ()>>4);return chunk!=null&&chunk.getBlockEntities().get(pos)==tile&&chunk.getBlockState(pos)==tile.getBlockState()&&tile.getBlockState().getBlock() instanceof NativeScreenBlock block&&block.family()==ScreenFamily.SCREEN;}
  public synchronized Group group(NativeScreenEntity origin){if(origin==null||origin.getLevel()==null)return null;var level=origin.getLevel();thread(level);if(!installed(level,origin))return null;var cache=worlds.get(level);if(cache==null){if(worlds.size()>=8)worlds.clear();cache=new Cache();worlds.put(level,cache);}var previous=cache.groups.get(origin.getBlockPos());if(previous!=null)return previous;
   var state=origin.getBlockState();var block=(NativeScreenBlock)state.getBlock();var pose=block.screenState(state);var basis=ScreenJoinGeometry.basis(ScreenFamily.SCREEN,pose,origin.screenSettings().ceilingMounted());var signature=origin.joinSignature();
   var discovery=ScreenRectangleGroups.discover(origin,basis,new ScreenRectangleGroups.Access<NativeScreenEntity>(){
    public int x(NativeScreenEntity tile){return tile.getBlockPos().getX();}public int y(NativeScreenEntity tile){return tile.getBlockPos().getY();}public int z(NativeScreenEntity tile){return tile.getBlockPos().getZ();}
    public boolean loaded(int x,int y,int z){return y>=level.getMinBuildHeight()&&y<level.getMaxBuildHeight()&&level.getChunkSource().getChunkNow(x>>4,z>>4)!=null;}
    public NativeScreenEntity at(int x,int y,int z){var chunk=level.getChunkSource().getChunkNow(x>>4,z>>4);if(chunk==null)return null;var raw=chunk.getBlockEntities().get(new BlockPos(x,y,z));return raw instanceof NativeScreenEntity tile?tile:null;}
    public boolean compatible(NativeScreenEntity ignored,NativeScreenEntity candidate){return installed(level,candidate)&&candidate.getBlockState()==state&&signature.editMatches(candidate.joinSignature());}
   },signature.editEligible(ScreenFamily.SCREEN));
   // Source returns only the queried singleton on overflow; it does not populate unqueried members.
   var selected=discovery.members(origin);if(discovery.overflow)selected=List.of(origin);var positions=new ArrayList<BlockPos>();int minX=Integer.MAX_VALUE,minY=minX,maxX=Integer.MIN_VALUE,maxY=maxX;BlockPos reader=null;int readerState=NONE;AABB bounds=null;
   for(var member:selected){var pos=member.getBlockPos().immutable();positions.add(pos);long cell=basis.cell(pos.getX(),pos.getY(),pos.getZ());int x=(int)(cell>>32),y=(int)cell;minX=Math.min(minX,x);minY=Math.min(minY,y);maxX=Math.max(maxX,x);maxY=Math.max(maxY,y);var box=new AABB(pos);bounds=bounds==null?box:bounds.minmax(box);for(var side:SIDES){var next=pos.relative(side);if(next.getY()<level.getMinBuildHeight()||next.getY()>=level.getMaxBuildHeight()||level.getChunkSource().getChunkNow(next.getX()>>4,next.getZ()>>4)==null)continue;int candidate=readers.at(level,next,side);if(candidate<NONE||candidate>OCCUPIED)throw new IllegalStateException("Invalid Screen reader projection");if(candidate==NONE)continue;if(reader==null||readerState==EMPTY&&candidate==OCCUPIED){reader=next;readerState=candidate;}break;}}
   int anchorX=pose.facing==1?maxX:minX,anchorY=pose.facing==0?maxY:minY;BlockPos owner=positions.get(0);for(var pos:positions){long cell=basis.cell(pos.getX(),pos.getY(),pos.getZ());if((int)(cell>>32)==anchorX&&(int)cell==anchorY){owner=pos;break;}}
   var group=new Group(positions,owner,reader,maxX-minX+1,maxY-minY+1,minX,minY,bounds.inflate(.1),pose.facing);long bytes=320L+positions.size()*128L;if(bytes>BUDGET-BASE)throw new IllegalStateException("Screen chip group exceeds retained budget");if(cache.bytes+bytes>BUDGET)cache.clear();cache.bytes+=bytes;for(var pos:positions)cache.groups.put(pos,group);return group;
  }
 }
}
