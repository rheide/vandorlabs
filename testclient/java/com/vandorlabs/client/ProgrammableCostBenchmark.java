package com.vandorlabs.client;
import com.vandorlabs.blocks.*;
import com.vandorlabs.tiles.*;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.math.BlockPos;
import java.util.*;
import java.lang.reflect.*;
/** Isolated state-build and owned-object measurements, not a complete chunk renderer. */
public class ProgrammableCostBenchmark {
 static volatile Object sink;
 static java.lang.reflect.Method size;
 static class Samples extends NonRenderingChecks.MemoryWorld {
  boolean memo; long reads, lights; Map<BlockPos,IBlockState> statesMemo=new HashMap<>(); Map<BlockPos,Integer> lightMemo=new HashMap<>();
  Samples(){super(true);}
  void reset(boolean enabled){memo=enabled;reads=lights=0;statesMemo.clear();lightMemo.clear();}
  @Override public IBlockState getBlockState(BlockPos p){if(memo){IBlockState s=statesMemo.get(p);if(s!=null)return s;}reads++;IBlockState s=super.getBlockState(p);if(memo)statesMemo.put(p.toImmutable(),s);return s;}
  @Override public int getCombinedLight(BlockPos p,int min){if(memo){Integer v=lightMemo.get(p);if(v!=null)return v;}lights++;int v=super.getCombinedLight(p,min);if(memo)lightMemo.put(p.toImmutable(),v);return v;}
 }
 static long graph(Object[] roots)throws Exception{
  Set<Object> seen=Collections.newSetFromMap(new IdentityHashMap<Object,Boolean>());ArrayDeque<Object> todo=new ArrayDeque<>();Collections.addAll(todo,roots);long bytes=0;
  while(!todo.isEmpty()){Object o=todo.removeLast();if(!seen.add(o)||o instanceof String||o instanceof Enum||o instanceof Class||o instanceof Block||o instanceof net.minecraft.world.World)continue;bytes+=(Long)size.invoke(null,o);Class<?> c=o.getClass();if(c.isArray()){if(!c.getComponentType().isPrimitive())for(Object v:(Object[])o)if(v!=null)todo.add(v);continue;}for(;c!=null;c=c.getSuperclass())for(Field f:c.getDeclaredFields())if(!Modifier.isStatic(f.getModifiers())&&!f.getType().isPrimitive()){f.setAccessible(true);Object v=f.get(o);if(v!=null)todo.add(v);}}
  return bytes;
 }
 public static void main(String[] args)throws Exception{
  net.minecraft.init.Bootstrap.register();TileEntity.register("vandorlabs:cost_fixture",TileEntityAnimatedScreenSelector.class);size=Class.forName("SizeAgent").getMethod("size",Object.class);
  System.out.println("MEMORY excludes shared strings/enums/classes/blocks/world; fleet incremental owned graph, includes positions");
  for(Block block:new Block[]{new BlockProgrammableBlock(),new BlockProgrammableSlab(),new BlockProgrammableStairs()})for(boolean mixed:new boolean[]{false,true}){
   Samples w=new Samples();IBlockState state=block.getDefaultState();BlockPos[] positions=new BlockPos[4096];TileEntityAnimatedScreenSelector[] fresh=new TileEntityAnimatedScreenSelector[4096],loaded=new TileEntityAnimatedScreenSelector[4096];
   for(int i=0;i<4096;i++){BlockPos p=new BlockPos(i&15,80+((i>>8)&15),(i>>4)&15);positions[i]=p;TileEntityAnimatedScreenSelector t=(TileEntityAnimatedScreenSelector)block.createTileEntity(w,state);t.setHousingTexture(mixed?i%32:0);t.setPos(p);fresh[i]=t;NBTTagCompound n=t.writeToNBT(new NBTTagCompound());TileEntityAnimatedScreenSelector l=new TileEntityAnimatedScreenSelector();l.readFromNBT(n);loaded[i]=l;w.states.put(p,state);w.tiles.put(p,l);}
   long freshBytes=graph(fresh),loadedBytes=graph(loaded);
   System.out.printf("COMPONENT tile_shallow=%d frame=%d fluids_and_slots=%d face=%d%n",(Long)size.invoke(null,loaded[0]),graph(new Object[]{loaded[0].frameAppearance}),(Long)size.invoke(null,loaded[0].fluids)+(Long)size.invoke(null,(Object)new NBTTagCompound[2]),graph(new Object[]{loaded[0].getFaceTextures()}));Set<FaceTextures> faceObjects=Collections.newSetFromMap(new IdentityHashMap<FaceTextures,Boolean>());for(TileEntityAnimatedScreenSelector t:loaded)faceObjects.add(t.getFaceTextures());
   System.out.printf(Locale.ROOT,"MEMORY %s %s n=4096 fresh=%d loaded=%d loadedFaceObjects=%d sharedFacePotential=%d%n",block.getClass().getSimpleName(),mixed?"mixed32":"same",freshBytes,loadedBytes,faceObjects.size(),graph(faceObjects.toArray())-graph(new Object[]{loaded[0].getFaceTextures()}));
   double[][] times=new double[2][31];long[][] alloc=new long[2][31];
   for(int round=-100;round<31;round++)for(int order=0;order<2;order++){int a=(round+order)&1;w.reset(a==1);long before=BenchmarkAllocations.currentThreadBytes(),start=System.nanoTime();for(BlockPos p:positions)sink=block.getExtendedState(block.getActualState(state,w,p),w,p);long elapsed=System.nanoTime()-start,bytes=BenchmarkAllocations.currentThreadBytes()-before;if(round>=0){times[a][round]=elapsed/1e6;alloc[a][round]=bytes;}if(round==30)System.out.printf("READS %s %s memo=%s state=%d light=%d%n",block.getClass().getSimpleName(),mixed?"mixed32":"same",a==1,w.reads,w.lights);}
   for(int a=0;a<2;a++){Arrays.sort(times[a]);Arrays.sort(alloc[a]);System.out.printf(Locale.ROOT,"STATE %s %s memo=%s p50_ms=%.6f p95_ms=%.6f alloc=%d%n",block.getClass().getSimpleName(),mixed?"mixed32":"same",a==1,times[a][15],times[a][29],alloc[a][15]);}
  }
 }
}
