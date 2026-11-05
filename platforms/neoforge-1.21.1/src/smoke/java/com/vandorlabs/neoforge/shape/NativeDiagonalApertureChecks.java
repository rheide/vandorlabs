package com.vandorlabs.neoforge.shape;

import com.google.gson.*;
import com.vandorlabs.persistence.WallSettings;
import java.nio.file.*;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/** Exact replay of captured Forge aperture layouts, local reflections, solid nodes and direct contacts. */
public final class NativeDiagonalApertureChecks {
 record Member(BlockPos pos,BlockState state,WallSettings settings,int art){}
 static long checks,queries,caps,solids,flips;static String key;
 static void need(boolean value,String why){checks++;if(!value)throw new AssertionError(key+" "+why);}
 static BlockPos pos(JsonArray a){return new BlockPos(a.get(0).getAsInt(),a.get(1).getAsInt(),a.get(2).getAsInt());}
 static final class Access implements NativeDiagonalLayouts.Access<Member>,NativeDiagonalWallTopology.Access {
  final Map<BlockPos,Member> members=new HashMap<>();boolean limited,closed;int reads;
  public boolean loaded(BlockPos p){return !closed&&(!limited||(p.getX()>>4)==0&&(p.getZ()>>4)==0);}
  void check(BlockPos p){need(loaded(p),"unloaded read "+p);reads++;}
  public Member member(BlockPos p){check(p);return members.get(p);}
  public BlockState state(BlockPos p){check(p);Member m=members.get(p);return m==null?Blocks.AIR.defaultBlockState():m.state;}
  public BlockState localState(Member m){check(m.pos);return m.state;}
  public WallSettings settings(Member m){check(m.pos);return m.settings;}
  public WallSettings settings(BlockPos p){check(p);Member m=members.get(p);return m==null?new WallSettings(false,false,0,0,false,0):m.settings;}
  public int mainTexture(Member m){throw new AssertionError("Aperture must ignore artwork");}
  public int textureLayout(Member m){throw new AssertionError("Aperture must ignore texture layout");}
  public boolean fullBlock(BlockPos p,BlockState s){throw new AssertionError("Aperture solid-node discovery must not query collision");}
  public boolean solid(BlockPos p,Member m){check(p);return NativeDiagonalWallTopology.solid(this,p,m.state);}
 }
 public static void run(NativeDiagonalWallBlock block)throws Exception{
  int scenes=0;try(var lines=Files.lines(Paths.get(System.getProperty("vandorlabs.diagonalApertureReference")))){for(String line:(Iterable<String>)lines::iterator){var scene=JsonParser.parseString(line).getAsJsonObject();key=scene.get("profile").getAsString();var access=new Access();access.limited=scene.get("chunkLimited").getAsBoolean();
   for(var value:scene.getAsJsonArray("cells")){var c=value.getAsJsonObject();var p=pos(c.getAsJsonArray("pos"));var state=block.defaultBlockState().setValue(NativePanelBlock.FACING,Direction.from3DDataValue(c.get("facing").getAsInt())).setValue(NativeDiagonalWallBlock.INVERTED,c.get("inverted").getAsBoolean());access.members.put(p,new Member(p,state,new WallSettings(c.get("full").getAsBoolean(),c.get("half").getAsBoolean(),c.get("fill").getAsInt(),c.get("diagonalJoin").getAsInt(),c.get("join").getAsBoolean(),c.get("shape").getAsInt()),c.get("art").getAsInt()));need(access.loaded(p)==c.get("loaded").getAsBoolean(),"loaded boundary");}
   for(var value:scene.getAsJsonArray("queries")){var q=value.getAsJsonObject();var p=pos(q.getAsJsonArray("pos"));var root=access.members.get(p);var capture=NativeDiagonalLayouts.capture(access,p,root,NativePanelGroups.Mode.APERTURE);var actual=capture.layouts().get(p.asLong());var a=q.getAsJsonArray("layout");var expected=new NativePanelLayout(a.get(0).getAsInt(),a.get(1).getAsInt(),a.get(2).getAsInt(),a.get(3).getAsInt(),a.get(4).getAsInt()!=0,a.get(5).getAsInt()!=0);need(actual.equals(expected),"layout "+p+" expected="+expected+" actual="+actual);need(NativeDiagonalLayouts.contacts(access,p,root)==q.get("mask").getAsInt(),"direct contacts independent of rectangle and cap "+p);boolean solid=access.solid(p,root);need(solid==q.get("solid").getAsBoolean(),"actual Auto solid node "+p);need(capture.members().get(p)==root,"original identity");for(var e:capture.members().entrySet())need(access.loaded(e.getKey())&&access.members.get(e.getKey())==e.getValue(),"loaded original members only");if(capture.capped())caps++;if(solid)solids++;if(actual.flipU()||actual.flipV())flips++;queries++;
   }
   var root=access.members.values().stream().filter(m->access.loaded(m.pos)).findFirst().orElseThrow();int reads=access.reads;access.closed=true;try{NativeDiagonalLayouts.capture(access,root.pos,root,NativePanelGroups.Mode.APERTURE);throw new AssertionError("Closed root accepted");}catch(IllegalArgumentException expected){}need(access.reads==reads,"closed discovery local only");scenes++;
  }}need(scenes==377&&queries==2619&&solids==32&&flips==313&&caps==18,"complete frozen solid/reflected/capped cases required");System.out.println("[vandorlabs][diagonal-aperture] dedicated PASS actualForgeScenes="+scenes+" queries="+queries+" checks="+checks+" solids="+solids+" flips="+flips+" capped="+caps+" raw flags, direct contacts, local reflection and raw UV; map replay only");
 }
}
