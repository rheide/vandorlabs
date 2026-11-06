package com.vandorlabs.neoforge;
import com.google.gson.*;
import java.nio.file.*;
import java.util.*;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.core.Direction;
public final class NativeDoorXMeshChecks {
 public static void main(String[] args)throws Exception{int profiles=0,vertices=0;try(var lines=Files.lines(Path.of(args[0]))){for(String line:(Iterable<String>)lines::iterator){var row=JsonParser.parseString(line).getAsJsonObject();var values=row.getAsJsonArray("source");int[] raw=new int[32];for(int v=0;v<4;v++){for(int a=0;a<3;a++)raw[v*8+a]=values.get(v*5+a).getAsInt();raw[v*8+3]=-1;raw[v*8+4]=values.get(v*5+3).getAsInt();raw[v*8+5]=values.get(v*5+4).getAsInt();raw[v*8+7]=127<<16;}var actual=NativeDoorXPanels.build(List.of(new BakedQuad(raw,-1,Direction.SOUTH,null,false,false)),row.get("right").getAsBoolean(),row.get("caps").getAsBoolean());var expected=row.getAsJsonArray("panels");for(int panel=0;panel<4;panel++){var data=expected.get(panel).getAsJsonArray();if(data.size()!=actual[panel].size()*24)throw new AssertionError("Source X panel vertices profile="+profiles+" panel="+panel);int index=0;for(var q:actual[panel])for(int v=0;v<4;v++){int[] packed=q.getVertices();for(int field:new int[]{0,1,2,4,5,7}){int value=packed[v*8+field];if(field==7)value&=0xffffff;if(value!=data.get(index++).getAsInt())throw new AssertionError("Source X mesh raw payload profile="+profiles+" panel="+panel+" field="+field);}vertices++;}}profiles++;}}if(profiles!=8)throw new AssertionError("Complete source X mesh profiles");System.out.println("[vandorlabs][door-xmesh] CPU PASS actualForge profiles="+profiles+" vertices="+vertices+" clipping/caps exact raw position/UV/normal bytes");}
}
