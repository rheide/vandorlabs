package com.vandorlabs.client;
import com.google.gson.*;
import com.vandorlabs.blocks.*;
import com.vandorlabs.tiles.TileEntityAnimatedScreenSelector;
import java.nio.file.*;
import java.util.*;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;

/** Actual current artwork groups; native fixtures consume captured cells rather than copying discovery. */
public final class DiagonalLayoutLegacyChecks {
 static final String GOLDEN="9a5cb9aea4d6b4c2d6aebae9131c680f53b490174c4403b4711e3e518c13a717";static java.security.MessageDigest digest;static int cases,queries,flips;static long cells;
 static BlockProgrammableWall PLAIN,PORT;
 static final class Scene {final NonRenderingChecks.MemoryWorld world=new NonRenderingChecks.MemoryWorld(false);final Set<BlockPos> cells=new LinkedHashSet<>();}
 static void put(long n){for(int i=56;i>=0;i-=8)digest.update((byte)(n>>>i));}
 static void cell(Scene scene,BlockPos pos,BlockProgrammableWall block,EnumFacing face,boolean inverted,int mode,int fill,int art,int layout){
  scene.world.setBlockState(pos,block.getDefaultState().withProperty(BlockProgrammableWall.FACING,face).withProperty(BlockProgrammableWall.INVERTED,inverted),2);TileEntityAnimatedScreenSelector tile=(TileEntityAnimatedScreenSelector)scene.world.getTileEntity(pos);tile.setDiagonalGeometry(mode,fill);tile.setHousingTexture(art);tile.setMainTextureLayout(layout);scene.cells.add(pos.toImmutable());
 }
 static BlockPos row(Scene scene,BlockPos previous,int mode,boolean opposite,boolean bend){
  IBlockState a=scene.world.getBlockState(previous);EnumFacing face=a.getValue(BlockProgrammableWall.FACING);BlockPos fallback=null;IBlockState fallbackState=null;
  for(int pass=0;pass<2;pass++)for(BlockPos candidate:DiagonalPanelGeometry.rowCandidates(previous,a,mode,mode!=2||face==scene.world.getBlockState(scene.cells.iterator().next()).getValue(BlockProgrammableWall.FACING)))for(EnumFacing facing:new EnumFacing[]{opposite?face.getOpposite():face,opposite?face:face.getOpposite()})for(boolean inv:new boolean[]{false,true}){
   IBlockState b=a.withProperty(BlockProgrammableWall.FACING,facing).withProperty(BlockProgrammableWall.INVERTED,inv);boolean same=DiagonalPanelGeometry.samePlane(previous,a,candidate,b,mode),meet=DiagonalPanelGeometry.rowsMeet(previous,a,candidate,b,mode);if(!(same||meet)||pass==0&&(bend?same:!same))continue;
   if(fallback==null){fallback=candidate;fallbackState=b;}if((facing!=face)==opposite){scene.world.setBlockState(candidate,b,2);return candidate;}
  }
  if(fallback==null)throw new AssertionError("no connected diagonal row");scene.world.setBlockState(fallback,fallbackState,2);return fallback;
 }
 static JsonArray xyz(BlockPos p){JsonArray a=new JsonArray();a.add(p.getX());a.add(p.getY());a.add(p.getZ());return a;}
 static void output(Scene scene,String name,List<BlockPos> selected,java.io.PrintWriter writer){
  JsonObject record=new JsonObject();record.addProperty("profile",name);record.addProperty("chunkLimited",scene.world.chunkLimit);JsonArray inputs=new JsonArray(),outputs=new JsonArray();
  for(BlockPos pos:scene.cells){boolean loaded=scene.world.isBlockLoaded(pos);boolean limit=scene.world.chunkLimit;scene.world.chunkLimit=false;IBlockState state=scene.world.getBlockState(pos);if(!(state.getBlock() instanceof BlockProgrammableWall)){scene.world.chunkLimit=limit;continue;}TileEntityAnimatedScreenSelector tile=(TileEntityAnimatedScreenSelector)scene.world.getTileEntity(pos);JsonObject in=new JsonObject();in.add("pos",xyz(pos));in.addProperty("block",state.getBlock()==PORT?1:0);in.addProperty("facing",state.getValue(BlockProgrammableWall.FACING).getIndex());in.addProperty("inverted",state.getValue(BlockProgrammableWall.INVERTED));in.addProperty("mode",BlockProgrammableWall.geometry(scene.world,pos));in.addProperty("fill",tile.getDiagonalFill());in.addProperty("art",tile.getHousingTexture());in.addProperty("layout",tile.getMainTextureLayout());in.addProperty("loaded",loaded);inputs.add(in);scene.world.chunkLimit=limit;cells++;
   put(pos.toLong());put(in.get("block").getAsInt());put(in.get("facing").getAsInt());put(in.get("inverted").getAsBoolean()?1:0);put(in.get("mode").getAsInt());put(tile.getDiagonalFill());put(tile.getHousingTexture());put(tile.getMainTextureLayout());put(loaded?1:0);
  }
  for(BlockPos pos:selected){if(!scene.world.isBlockLoaded(pos)||scene.world.getTileEntity(pos)==null)continue;GlassSurfaceGroups.Layout group=GlassSurfaceGroups.mainLayout(scene.world.getTileEntity(pos));if(group==null)throw new AssertionError("missing actual layout");JsonObject out=new JsonObject();out.add("pos",xyz(pos));JsonArray layout=new JsonArray();long[] values={group.column,group.row,group.columns,group.rows,group.flipU?1:0,group.flipV?1:0};for(long n:values){layout.add(n);put(n);}put(pos.toLong());out.add("layout",layout);outputs.add(out);queries++;if(group.flipU||group.flipV)flips++;}
  record.add("cells",inputs);record.add("queries",outputs);writer.println(new Gson().toJson(record));cases++;GlassSurfaceGroups.unload(scene.world);
 }
 public static void main(String[] args)throws Exception{
  net.minecraft.init.Bootstrap.register();PLAIN=new BlockProgrammableWall("diagonal_wall",BlockProgrammableWall.Shape.DIAGONAL);PORT=new BlockProgrammableWall("diagonal_porthole_wall",BlockProgrammableWall.Shape.DIAGONAL_PORTHOLE);digest=java.security.MessageDigest.getInstance("SHA-256");Path file=Paths.get(System.getProperty("vandorlabs.diagonalLayoutExport","build/test-artifacts/diagonal-layout-forge.jsonl"));Files.createDirectories(file.toAbsolutePath().getParent());
  try(java.io.PrintWriter writer=new java.io.PrintWriter(Files.newBufferedWriter(file))){
   for(EnumFacing face:EnumFacing.HORIZONTALS)for(boolean inverted:new boolean[]{false,true})for(int mode=0;mode<3;mode++)for(int fill=0;fill<4;fill++)for(int layout=1;layout<3;layout++)for(int profile=0;profile<11;profile++){
    Scene scene=new Scene();BlockPos origin=profile==10?new BlockPos(14,80,14):new BlockPos(-7,80,-11);BlockPos base=origin,changed=null;
    for(int y=0;y<3;y++){if(y>0){BlockPos previous=base;base=row(scene,previous,mode,profile==1,profile==2);}
     IBlockState state=scene.world.getBlockState(base);EnumFacing f=y==0?face:state.getValue(BlockProgrammableWall.FACING);boolean inv=y==0?inverted:state.getValue(BlockProgrammableWall.INVERTED);
     // Install the first row before asking actual rowCandidates/plane policy for successors.
     for(int x=0;x<3;x++){BlockPos p=base.offset(face.rotateY(),x);cell(scene,p,PLAIN,f,inv,mode,fill,0,layout);if(x==1&&y==1)changed=p;}
    }
    if(profile==3)scene.world.setBlockState(changed,Blocks.AIR.getDefaultState(),2);
    if(profile==4)cell(scene,origin.offset(face.rotateY(),6),PLAIN,face,inverted,mode,fill,0,layout);
    if(profile>=5&&profile<=9){IBlockState old=scene.world.getBlockState(changed);cell(scene,changed,profile==9?PORT:PLAIN,old.getValue(BlockProgrammableWall.FACING),old.getValue(BlockProgrammableWall.INVERTED),profile==8?(mode+1)%3:mode,profile==7?(fill+1)%4:fill,profile==5?4:0,profile==6?3-layout:layout);}
    if(profile==10)scene.world.chunkLimit=true;
    output(scene,"matrix/"+face+"/"+inverted+"/"+mode+"/"+fill+"/"+layout+"/"+profile,new ArrayList<>(scene.cells),writer);
   }
   for(int mode=0;mode<3;mode++)for(int size:new int[]{4095,4096,4097}){Scene scene=new Scene();BlockPos start=new BlockPos(-2048,80,-32);for(int x=0;x<size;x++)cell(scene,start.east(x),PLAIN,EnumFacing.NORTH,false,mode,0,0,1);output(scene,"cap/"+mode+"/"+size,Arrays.asList(start,start.east(size/2),start.east(size-1)),writer);}
  }
  StringBuilder hash=new StringBuilder();for(byte b:digest.digest())hash.append(String.format("%02x",b&255));if(!GOLDEN.equals(hash.toString()))throw new AssertionError("Actual diagonal grouping changed "+hash);if(flips==0)throw new AssertionError("opposite-basis UV flips not exercised");System.out.println("Actual Forge diagonal artwork layouts PASS cases="+cases+" queries="+queries+" cells="+cells+" flipped="+flips+" golden="+hash);
 }
}
