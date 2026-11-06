package com.vandorlabs.neoforge.light;

import com.google.gson.*;
import com.vandorlabs.light.*;
import java.nio.file.*;
import java.util.*;
import net.minecraft.core.BlockPos;

/** Actual loaded-source groups and settlements; no world, registry or native lifecycle is activated. */
public final class LightJoiningChecks {
    private static int checks,reads;
    private static void need(boolean value,String why){checks++;if(!value)throw new AssertionError(why);}
    private static BlockPos pos(JsonElement value){var a=value.getAsJsonArray();return new BlockPos(a.get(0).getAsInt(),a.get(1).getAsInt(),a.get(2).getAsInt());}
    private static List<BlockPos> positions(JsonArray array){List<BlockPos> out=new ArrayList<>();for(var value:array)out.add(pos(value));return out;}
    private static Map<BlockPos,JsonObject> data(JsonArray array){Map<BlockPos,JsonObject> out=new LinkedHashMap<>();for(var value:array){var row=value.getAsJsonObject();out.put(pos(row.get("pos")),row);}return out;}
    private static ProgrammableLightJoining.Node node(JsonObject row){return row==null?null:new ProgrammableLightJoining.Node(row.get("block").getAsString(),row.get("facing").getAsInt(),row.get("half").getAsInt(),row.get("face").getAsInt(),row.get("slab").getAsBoolean(),row.get("available").getAsBoolean(),row.get("join").getAsBoolean(),row.get("small").getAsBoolean(),row.get("on").getAsBoolean());}
    private static final class Graph implements ProgrammableLightJoining.Graph<BlockPos> {
        final Map<BlockPos,JsonObject> cells;final List<BlockPos> directions;final boolean limited;
        Graph(Map<BlockPos,JsonObject> cells,JsonObject row){this.cells=cells;directions=positions(row.getAsJsonArray("directions"));limited=row.get("variant").getAsInt()==12;}
        public BlockPos neighbor(BlockPos pos,int index){var d=directions.get(index);return pos.offset(d.getX(),d.getY(),d.getZ());}
        public boolean loaded(BlockPos pos){return pos.getY()>=0&&pos.getY()<256&&(!limited||(pos.getX()>>4)==0&&(pos.getZ()>>4)==0);}
        public ProgrammableLightJoining.Node node(BlockPos pos){need(loaded(pos),"metadata read across cold boundary "+pos);reads++;return LightJoiningChecks.node(cells.get(pos));}
    }
    public static void main(String[] args)throws Exception{int rows=0;try(var lines=Files.lines(Path.of(args[0]))){for(String line:(Iterable<String>)lines::iterator){var row=JsonParser.parseString(line).getAsJsonObject();var before=data(row.getAsJsonArray("before"));var after=data(row.getAsJsonArray("after"));var start=before.keySet().iterator().next();var first=before.get(start);var graph=new Graph(before,row);var manual=ProgrammableLightJoining.members(start,node(first),graph,true);var power=ProgrammableLightJoining.members(start,node(first),graph,false);need(manual.equals(positions(row.getAsJsonArray("manualMembers")))&&power.equals(positions(row.getAsJsonArray("powerMembers"))),"actual ordered membership row="+rows);need(ProgrammableLightJoining.members(start,node(after.get(start)),new Graph(after,row),true).equals(positions(row.getAsJsonArray("afterMembers"))),"actual post-callback ordered membership row="+rows);
        int operation=row.get("operation").getAsInt();boolean toggles=operation==2&&ProgrammableLightPolicy.manualToggleAllowed(first.get("trigger").getAsInt());boolean next=!first.get("on").getAsBoolean(),changed=false;for(var entry:before.entrySet()){var source=entry.getValue();boolean target=toggles&&manual.contains(entry.getKey())&&ProgrammableLightPolicy.manualToggleAllowed(source.get("trigger").getAsInt())?next:source.get("manualOn").getAsBoolean();changed|=target!=source.get("manualOn").getAsBoolean();need(after.get(entry.getKey()).get("manualOn").getAsBoolean()==target,"actual per-member manual write row="+rows);}
        boolean settle=operation==1||toggles&&changed;
        if(operation==3||operation==4){var peer=before.keySet().stream().skip(1).findFirst().orElseThrow();if(graph.loaded(peer)){var peerGroup=ProgrammableLightJoining.members(peer,node(before.get(peer)),graph,false);settle=peer.distManhattan(start)==1||peerGroup.contains(start);}}
        var actual=after.get(start);int level=first.get("joinedLevel").getAsInt();boolean powered=first.get("joinedPower").getAsBoolean();if(settle){List<ProgrammableLightJoining.Input> inputs=new ArrayList<>();for(var cell:power){var source=after.get(cell);inputs.add(new ProgrammableLightJoining.Input(source.get("numeric").getAsBoolean(),source.get("directLevel").getAsInt(),source.get("directPower").getAsBoolean()));}var result=ProgrammableLightJoining.settle(inputs);if(result.level>=0)level=result.level;powered=result.powered;}
        need(level==actual.get("joinedLevel").getAsInt()&&powered==actual.get("joinedPower").getAsBoolean(),"actual root joined settlement row="+rows+" operation="+operation+" variant="+row.get("variant")+" expected="+actual+" level="+level+" power="+powered);need(row.get("result").getAsBoolean(),"manual callback consumed");rows++;
    }}need(rows==9722,"source joining coverage");System.out.println("Native inactive Light joining PASS rows="+rows+" checks="+checks+" metadataReads="+reads+" ordered groups/manual/channel-settlement/4095+4096/cold boundaries; source samplers remain separate");}
}
