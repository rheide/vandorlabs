package com.vandorlabs.neoforge.shape;
import com.google.gson.*;
import com.vandorlabs.persistence.WallSettings;
import java.nio.file.*;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.*;
/** Reconstruct actual Forge neighbour fixtures through the production loaded-access query. */
public final class NativeDiagonalTopologyChecks {
 static final BlockPos ORIGIN=BlockPos.ZERO;static final WallSettings DEFAULT=new WallSettings(false,false,0,0,false,0);static String key;static long checks;
 static void need(boolean ok,String why){checks++;if(!ok)throw new AssertionError(key+" "+why);}
 static final class MapAccess implements NativeDiagonalWallTopology.Access {
  final Map<BlockPos,BlockState> states=new HashMap<>();final Map<BlockPos,WallSettings> choices=new HashMap<>();boolean onlyRoot,closed;int reads;
  public boolean loaded(BlockPos p){return !closed&&(!onlyRoot||p.equals(ORIGIN));}
  void check(BlockPos p){need(loaded(p),"read unloaded "+p);reads++;}
  public BlockState state(BlockPos p){check(p);return states.getOrDefault(p,Blocks.AIR.defaultBlockState());}
  public WallSettings settings(BlockPos p){check(p);return choices.getOrDefault(p,DEFAULT);}
  public boolean fullBlock(BlockPos p,BlockState state){check(p);return state.is(Blocks.STONE);}
 }
 static void install(MapAccess a,BlockPos p,NativeDiagonalWallBlock block,Direction face,boolean inverted,int mode,int fill,int join){a.states.put(p,block.defaultBlockState().setValue(NativePanelBlock.FACING,face).setValue(NativeDiagonalWallBlock.INVERTED,inverted));a.choices.put(p,new WallSettings(mode!=0,mode==2,fill,join,true,0));}
 static void flat(MapAccess a,BlockPos p,NativeFlatWallBlock flat,Direction face,int depth){a.states.put(p,flat.defaultBlockState().setValue(NativePanelBlock.FACING,face).setValue(NativeFlatWallBlock.DEPTH,depth));}
 static Double number(JsonObject o,String k){return !o.has(k)||o.get(k).isJsonNull()?null:o.get(k).getAsDouble();}
 static Boolean option(JsonObject o,String k){return !o.has(k)||o.get(k).isJsonNull()?null:o.get(k).getAsBoolean();}
 static void equal(double actual,Double wanted,String label){double expected=wanted==null?Double.NaN:wanted;need(Double.doubleToRawLongBits(actual)==Double.doubleToRawLongBits(expected),label+" expected="+expected+" actual="+actual);}
 public static void run(NativeDiagonalWallBlock plain,NativeDiagonalWallBlock port,NativeFlatWallBlock flat)throws Exception{
  JsonObject reference;try(var reader=Files.newBufferedReader(Paths.get(System.getProperty("vandorlabs.diagonalReference")))){reference=JsonParser.parseReader(reader).getAsJsonObject();}key="reference";need(reference.get("golden").getAsString().equals("e5e8e78899db52f45e7c3f74888b9b39eb8e6dc1f92722206c24beedee9e2062"),"actual Forge frozen reference");int cases=0;
  for(var element:reference.getAsJsonArray("cases")){JsonObject r=element.getAsJsonObject();key="case"+cases+" mode"+r.get("mode")+" fill"+r.get("fill")+" profile"+r.get("profile")+" join"+r.get("joinChoice");var access=new MapAccess();int mode=r.get("mode").getAsInt(),fill=r.get("fill").getAsInt(),profile=r.get("profile").getAsInt();Direction face=Direction.byName(r.get("facing").getAsString()),right=face.getClockWise();boolean inverted=r.get("inverted").getAsBoolean();var block=r.get("inset").getAsBoolean()?port:plain;install(access,ORIGIN,block,face,inverted,mode,fill,r.get("joinChoice").getAsInt());
   if(profile==1||profile==3)for(int offset=-1;offset<=1;offset++)flat(access,ORIGIN.below().relative(face.getOpposite(),offset),flat,face,1);
   if(profile==2||profile==3)for(int offset=-1;offset<=1;offset++)flat(access,ORIGIN.above().relative(face.getOpposite(),offset),flat,face,2);
   if(profile==4)for(Direction d:Direction.values())access.states.put(ORIGIN.relative(d),Blocks.STONE.defaultBlockState());
   if(profile==5)for(Direction d:Direction.values())install(access,ORIGIN.relative(d),block,face,!inverted,mode,0,1);
   if(profile==6){install(access,ORIGIN.relative(face),block,right,inverted,mode,0,0);install(access,ORIGIN.relative(face.getOpposite()),block,right.getOpposite(),inverted,mode,0,0);install(access,ORIGIN.relative(right),block,face,inverted,mode,0,0);}
   if(profile==7){flat(access,ORIGIN.relative(face),flat,right,0);flat(access,ORIGIN.relative(face.getOpposite()),flat,right.getOpposite(),2);access.states.put(ORIGIN.relative(right),Blocks.STONE.defaultBlockState());}
   var layout=new NativePanelLayout(1,1,3,2,true,false);var pose=NativeDiagonalWallTopology.capture(access,ORIGIN,access.state(ORIGIN),layout);need(pose.layout()==layout,"retain live supplied UV layout");need(pose.facing()==face&&pose.inverted()==inverted&&pose.mode()==mode&&pose.fill()==fill,"root settings");equal(pose.lowerEnd(),number(r,"lower"),"lower end");equal(pose.upperEnd(),number(r,"upper"),"upper end");need(pose.hideLower()==r.get("hideLower").getAsBoolean()&&pose.hideUpper()==r.get("hideUpper").getAsBoolean(),"hidden end flags");
   NativeDiagonalWallGeometry.Join join=null;if(r.has("corner")){var c=r.getAsJsonObject("corner");join=new NativeDiagonalWallGeometry.Join(option(c,"frontRight"),option(c,"backRight"),c.get("straightLeft").getAsBoolean(),c.get("straightRight").getAsBoolean(),number(c,"frontFlat"),number(c,"backFlat"));}need(Objects.equals(join,pose.join()),"corner expected="+join+" actual="+pose.join());var clip=r.getAsJsonArray("clip");need(clip.size()==pose.clip().size(),"clip count");for(int i=0;i<clip.size();i++)equal(pose.clip().at(i),clip.get(i).getAsDouble(),"clip"+i);
   access.onlyRoot=true;NativeDiagonalWallTopology.capture(access,ORIGIN,access.state(ORIGIN),layout);int before=access.reads;access.closed=true;try{NativeDiagonalWallTopology.capture(access,ORIGIN,block.defaultBlockState(),layout);throw new AssertionError("unloaded root accepted");}catch(IllegalArgumentException expected){}need(access.reads==before,"closed root never queried state/settings");cases++;
  }
  need(cases==3648,"complete Forge profiles");System.out.println("[vandorlabs][diagonal-topology] dedicated PASS actualForgeProfiles="+cases+" checks="+checks+" exact corner/end/clip/settings, suppliedUVlayout, unloaded-neighbour guards; registry context only; map access, no placement or world reads");
 }
}
