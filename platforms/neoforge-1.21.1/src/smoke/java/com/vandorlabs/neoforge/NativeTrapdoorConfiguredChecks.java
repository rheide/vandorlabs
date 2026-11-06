package com.vandorlabs.neoforge;
import com.google.gson.*;
import com.mojang.blaze3d.vertex.*;
import com.vandorlabs.neoforge.trapdoor.*;
import com.vandorlabs.persistence.TrapdoorData;
import com.vandorlabs.redstone.ChannelList;
import com.vandorlabs.render.TrapdoorLeafPose;
import com.vandorlabs.segmented.SegmentedPanelMesh;
import java.nio.file.*;
import java.util.*;
import java.lang.reflect.*;
import net.minecraft.core.BlockPos;
/** Complete native scalar/material/layout/pose dispatch against actual released drawConfiguredLeaf. */
public final class NativeTrapdoorConfiguredChecks {
 private record Frame(NativeTrapdoorFrameBorders.Borders borders,double aspect,boolean border){}
 public static void main(String[] args)throws Exception{
  Map<Integer,Frame> frames=new HashMap<>();for(String line:Files.readAllLines(Path.of(args[1]))){var row=JsonParser.parseString(line).getAsJsonObject();var b=row.getAsJsonArray("borders");frames.put(row.get("choice").getAsInt(),new Frame(new NativeTrapdoorFrameBorders.Borders(b.get(0).getAsDouble(),b.get(1).getAsDouble(),b.get(2).getAsDouble(),b.get(3).getAsDouble()),row.get("aspect").getAsDouble(),row.get("border").getAsBoolean()));}
  Map<Integer,NativeTrapdoorMaterials.Surface> materials=new HashMap<>();var metal=texture(48,80,16);var bake=new NativeTrapdoorRenderer.Bake(new NativeTrapdoorRenderer.Materials(){public NativeTrapdoorMaterials.Surface surface(int choice){return Objects.requireNonNull(materials.get(choice));}public SegmentedPanelMesh.Texture metal(){return metal;}});var poses=new PoseStack();int profiles=0;long vertices=0;
  try(var reader=Files.newBufferedReader(Path.of(args[0]))){for(String line;(line=reader.readLine())!=null;){var row=JsonParser.parseString(line).getAsJsonObject();int choice=row.get("choice").getAsInt(),kind=row.get("material").getAsInt();if(!materials.containsKey(choice)){var image=texture(kind==0?32:kind==1?64:kind==2?96:kind==3?112:kind==4?128:192,kind==4?16:64,kind==1?32:16);var frame=frames.get(choice);materials.put(choice,new NativeTrapdoorMaterials.Surface(kind==4?texture(160,16,16):image,image,kind==1?.5:1,kind==1||kind==4,kind==4,kind==2||kind==3?frame.borders():null,frame==null?1:frame.aspect()));if(kind==5)need(frame!=null&&!frame.border(),"solid Frame source branch");}
   var pose=new TrapdoorLeafPose(bool(row,"diagonal"),integer(row,"position"),integer(row,"turns"),bool(row,"inverted"),bool(row,"sliding"),bool(row,"cover"),bool(row,"surface"),bool(row,"intoWall"),bool(row,"reverse"),bool(row,"closedReverse"),integer(row,"lift"),number(row,"hinge"),number(row,"travel"),number(row,"overhang"));
   var cfg=new TrapdoorData(choice,pose.position,integer(row,"layout"),1,bool(row,"rawTile"),pose.cover,pose.sliding,pose.intoWall,pose.surface);var settings=new NativeTrapdoorSettings(cfg,ChannelList.of(0),0,0,false,pose.diagonal);var b=row.getAsJsonArray("artwork");var visual=new NativeTrapdoorVisual(settings,pose,new NativeTrapdoorVisual.ArtworkBounds(b.get(0).getAsDouble(),b.get(1).getAsDouble(),b.get(2).getAsDouble(),b.get(3).getAsDouble()),integer(row,"joins"),false);var origin=row.getAsJsonArray("origin");var owner=new BlockPos(origin.get(0).getAsInt(),origin.get(1).getAsInt(),origin.get(2).getAsInt());var expected=row.getAsJsonArray("vertices");int[] vertex={-1},calls={0};int profile=profiles;
   VertexConsumer target=(VertexConsumer)Proxy.newProxyInstance(VertexConsumer.class.getClassLoader(),new Class[]{VertexConsumer.class},(proxy,method,values)->{if(method.isDefault())return InvocationHandler.invokeDefault(proxy,method,values);int at=vertex[0]*8;switch(method.getName()){
    case "addVertex":at=++vertex[0]*8;for(int a=0;a<3;a++)need((Float)values[a]==Float.intBitsToFloat(expected.get(at+a).getAsInt()),"position profile="+profile+" vertex="+vertex[0]+" axis="+a);break;
    case "setColor":for(Object value:values)need((Integer)value==255,"RGBA");break;
    case "setUv":for(int a=0;a<2;a++)need(Float.floatToRawIntBits((Float)values[a])==expected.get(at+4+a).getAsInt(),"UV profile="+profile+" vertex="+vertex[0]+" axis="+a);break;
    case "setLight":need((Integer)values[0]==expected.get(at+6).getAsInt(),"light");break;
    case "setOverlay":need((Integer)values[0]==123,"overlay");break;
    case "setNormal":int normal=0;for(int a=0;a<3;a++)normal|=(((byte)((Float)values[a]*127))&255)<<(a*8);need(normal==(expected.get(at+7).getAsInt()&0xffffff),"normal profile="+profile+" vertex="+vertex[0]);break;
   }calls[0]++;return method.getReturnType()==VertexConsumer.class?proxy:null;});NativeTrapdoorRenderer.emitAt(bake,visual,owner,number(row,"progress"),poses.last(),target,0xD00070,123);need((vertex[0]+1)*8==expected.size()&&calls[0]==(vertex[0]+1)*6,"complete native configured records");profiles++;vertices+=vertex[0]+1;
  }}need(profiles==10368,"complete source branches "+profiles);need(bake.retainedBytes()<=16*1024*1024,"byte-bound owner cache");System.out.println("[vandorlabs][trapdoor-configured] CPU PASS profiles="+profiles+" vertices="+vertices+" actual released full emitter/custom halves/frame and solid choices/all layout flags/group joins/cover/surface/into-wall/large coordinates; native complete consumer dispatch; unlit layer checked by GPU fixture");
 }
 private static SegmentedPanelMesh.Texture texture(int x,int y,int height){float u0=x/256F,u1=(x+16)/256F,v0=y/256F,v1=(y+height)/256F;return new SegmentedPanelMesh.Texture(){public float u(double pixel){return u0+(u1-u0)*(float)pixel/16F;}public float v(double pixel){return v0+(v1-v0)*(float)pixel/16F;}};}
 private static int integer(JsonObject row,String key){return row.get(key).getAsInt();}private static double number(JsonObject row,String key){return row.get(key).getAsDouble();}private static boolean bool(JsonObject row,String key){return row.get(key).getAsBoolean();}private static void need(boolean value,String message){if(!value)throw new AssertionError(message);}
}
