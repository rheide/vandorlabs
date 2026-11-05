package com.vandorlabs.neoforge.shape;
import com.google.gson.*;
import com.vandorlabs.persistence.WallSettings;
import java.nio.file.*;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
/** Read-only grouping replay over actual captured Forge cells; no copied profile/plane algorithm. */
public final class NativeDiagonalLayoutChecks {
 record Member(BlockPos pos,BlockState state,WallSettings settings,int art,int layout){}
 static long checks,queries,capped,flipped;static String key;
 static void need(boolean ok,String why){checks++;if(!ok)throw new AssertionError(key+" "+why);}
 static BlockPos pos(JsonArray a){return new BlockPos(a.get(0).getAsInt(),a.get(1).getAsInt(),a.get(2).getAsInt());}
 static final class MapAccess implements NativeDiagonalLayouts.Access<Member>{
  final Map<BlockPos,Member> members=new HashMap<>();boolean limited,closed;int reads;
  public boolean loaded(BlockPos p){return !closed&&(!limited||(p.getX()>>4)==0&&(p.getZ()>>4)==0);}
  void check(BlockPos p){need(loaded(p),"unloaded read "+p);reads++;}
  public Member member(BlockPos p){check(p);return members.get(p);}
  public BlockState state(BlockPos p){check(p);Member m=members.get(p);return m==null?Blocks.AIR.defaultBlockState():m.state;}
  public BlockState localState(Member m){check(m.pos);return m.state;}
  public WallSettings settings(Member m){check(m.pos);return m.settings;}
  public int mainTexture(Member m){check(m.pos);return m.art;}
  public int textureLayout(Member m){check(m.pos);return m.layout;}
 }
 public static void run(NativeDiagonalWallBlock plain,NativeDiagonalWallBlock port)throws Exception{
  int scenes=0;try(var lines=Files.lines(Paths.get(System.getProperty("vandorlabs.diagonalLayoutReference")))){for(String line:(Iterable<String>)lines::iterator){JsonObject scene=JsonParser.parseString(line).getAsJsonObject();key="scene"+scenes+" "+scene.get("profile");MapAccess access=new MapAccess();access.limited=scene.get("chunkLimited").getAsBoolean();
   for(var value:scene.getAsJsonArray("cells")){var c=value.getAsJsonObject();BlockPos pos=pos(c.getAsJsonArray("pos"));var block=c.get("block").getAsInt()==0?plain:port;var state=block.defaultBlockState().setValue(NativePanelBlock.FACING,Direction.from3DDataValue(c.get("facing").getAsInt())).setValue(NativeDiagonalWallBlock.INVERTED,c.get("inverted").getAsBoolean());int mode=c.get("mode").getAsInt();access.members.put(pos,new Member(pos,state,new WallSettings(mode!=0,mode==2,c.get("fill").getAsInt(),0,true,0),c.get("art").getAsInt(),c.get("layout").getAsInt()));need(access.loaded(pos)==c.get("loaded").getAsBoolean(),"captured loaded boundary");}
   for(var value:scene.getAsJsonArray("queries")){var query=value.getAsJsonObject();BlockPos root=pos(query.getAsJsonArray("pos"));Member member=access.members.get(root);var capture=NativeDiagonalLayouts.capture(access,root,member);var actual=capture.layouts().get(root.asLong());need(actual!=null,"root descriptor missing "+root);JsonArray a=query.getAsJsonArray("layout");var expected=new NativePanelLayout(a.get(0).getAsInt(),a.get(1).getAsInt(),a.get(2).getAsInt(),a.get(3).getAsInt(),a.get(4).getAsInt()!=0,a.get(5).getAsInt()!=0);need(actual.equals(expected),"root "+root+" expected="+expected+" actual="+actual);need(capture.members().get(root)==member,"original member identity");for(var entry:capture.members().entrySet())need(access.loaded(entry.getKey())&&access.members.get(entry.getKey())==entry.getValue(),"loaded original members only");
    if(capture.capped())capped++;if(actual.flipU()||actual.flipV())flipped++;queries++;
    if(scene.get("profile").getAsString().startsWith("cap/")){int size=access.members.size();need(capture.capped()==(size>=4096)&&capture.members().size()==Math.min(size,4096),"4096 cap boundary");if(capture.capped())for(var layout:capture.layouts().values())need(layout.equals(NativePanelLayout.UNIT),"capped unit fallback");}
   }
   Member root=access.members.values().stream().filter(m->access.loaded(m.pos)).findFirst().orElseThrow();int before=access.reads;access.closed=true;try{NativeDiagonalLayouts.capture(access,root.pos,root);throw new AssertionError("closed root accepted");}catch(IllegalArgumentException expected){}need(access.reads==before,"closed discovery never reads state/members/settings");scenes++;
  }}need(scenes==2121&&capped==18&&flipped>0,"complete profiles/cap/flips scenes="+scenes+" capped="+capped+" flipped="+flipped);System.out.println("[vandorlabs][diagonal-layout] dedicated PASS actualForgeScenes="+scenes+" queries="+queries+" checks="+checks+" capped="+capped+" flipped="+flipped+" exact artwork rectangles/UVflips and loaded guards; map replay only");
 }
}
