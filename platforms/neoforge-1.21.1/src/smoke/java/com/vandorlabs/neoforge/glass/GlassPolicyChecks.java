package com.vandorlabs.neoforge.glass;

import com.google.gson.*;
import com.vandorlabs.persistence.ProgrammableGlassSettings;
import com.vandorlabs.render.*;
import java.nio.file.*;
import net.minecraft.nbt.TagParser;

/** Scalar replay against actual released callbacks, including float ties and diagonal endpoint inheritance. */
public final class GlassPolicyChecks {
 private static int checks;
 private static void need(boolean yes,String text){checks++;if(!yes)throw new AssertionError("Flat glass "+text);}
 private static void pose(JsonObject expected,FlatGlassPolicy.Pose value){need(expected.get("rotated").getAsBoolean()==value.rotated,"plane");need(expected.get("depth").getAsInt()==value.depth,"depth");need(expected.get("size").getAsInt()==value.size,"detail size");need(expected.get("encoded").getAsInt()==value.metadata(),"metadata");}
 private static void settings(JsonObject e,ProgrammableGlassSettings g){need(e.get("shade").getAsInt()==g.shade&&e.get("material").getAsInt()==g.material&&e.get("layout").getAsInt()==g.layout&&e.get("effectiveLayout").getAsInt()==g.textureLayout(),"artwork scalars");need(e.get("stretch").getAsBoolean()==g.stretch&&e.get("tint").getAsInt()==g.tint&&e.get("framed").getAsBoolean()==g.framed,"appearance");need(e.get("size").getAsInt()==g.size&&e.get("join").getAsBoolean()==g.join&&e.get("panelFacing").getAsInt()==g.panelFacing&&e.get("legacySize").getAsBoolean()==g.legacySize,"saved migration state");}
 public static void main(String[] args)throws Exception {int metadata=0,loads=0,placements=0;try(var reader=Files.newBufferedReader(Path.of(args[0]))){for(String line;(line=reader.readLine())!=null;){var row=JsonParser.parseString(line).getAsJsonObject();switch(row.get("kind").getAsString()){
  case "metadata"->{var p=FlatGlassPolicy.metadata(row.get("meta").getAsInt());pose(row.getAsJsonObject("state"),p);for(int i=0;i<6;i++)need(Double.doubleToLongBits(p.coordinate(i))==Double.doubleToLongBits(row.getAsJsonArray("bounds").get(i).getAsDouble()),"exact bound");metadata++;}
  case "load"->{var g=NativeGlassSettings.read(TagParser.parseTag(row.get("tag").getAsString()));settings(row.getAsJsonObject("before"),g.glass());settings(row.getAsJsonObject("after"),g.resolveLegacySize(FlatGlassPolicy.metadata(row.get("meta").getAsInt()).size).glass());loads++;}
  case "placement"->{int facing=row.get("facing").getAsInt(),side=row.get("side").getAsInt(),neighbor=row.get("neighbor").getAsInt();float x=row.get("x").getAsFloat(),z=row.get("z").getAsFloat();FlatGlassPolicy.Pose p;
   int profile=neighbor-19,mode=profile<0?-1:profile%6/2;boolean inverted=profile>=0&&(profile%6&1)!=0;
   if(profile>=0&&mode!=2&&side<2){int supportFacing=new int[]{3,4,2,5}[profile/6];double endpoint=16*DiagonalWallGeometry.near(mode,inverted,side==1?1:0);p=FlatGlassPolicy.diagonalEndpoint(supportFacing,endpoint);}else p=FlatGlassPolicy.placement(facing==1||facing==3,neighbor>0&&neighbor<7?neighbor>3?1:0:-1,x,z);
   pose(row.getAsJsonObject("state"),p);placements++;}
  default->throw new AssertionError("Unknown source row");
 }}}need(metadata==19&&loads==494&&placements==66048,"source coverage");System.out.println("[vandorlabs][flat-glass-policy] PASS metadata="+metadata+" loads="+loads+" placements="+placements+" fields="+checks);}
}
