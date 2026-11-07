package com.vandorlabs.neoforge.canopy;

import com.google.gson.*;
import com.vandorlabs.canopy.CanopyPlacementPolicy;
import com.vandorlabs.persistence.CanopySettingsData;
import java.nio.file.*;

/** Released specialized item callbacks establish anchor and mount arithmetic. */
public final class CanopyPlacementChecks {
    private static int checks;
    private static void need(boolean value,String message){checks++;if(!value)throw new AssertionError(message);}
    public static void main(String[] args)throws Exception {
        int callbacks=0,successes=0,fixedCells=0,variable=0;
        try(var reader=Files.newBufferedReader(Path.of(args[0]))) {
            for(String line;(line=reader.readLine())!=null;) {
                var row=JsonParser.parseString(line).getAsJsonObject();String kind=row.get("id").getAsString();
                int width=row.get("width").getAsInt(),length=row.get("length").getAsInt(),height=row.get("height").getAsInt();
                var p=CanopyPlacementPolicy.initial(kind,width,length,height,row.get("playerFacing").getAsInt(),row.get("side").getAsInt(),false);
                var input=row.get("input");var tag=input==null||input.isJsonNull()?new net.minecraft.nbt.CompoundTag():CanopySettingsChecks.tags(input.getAsJsonObject());tag.putInt("CanopyMount",p.mount);
                var settings=NativeCanopySettings.read(tag);var anchor=new net.minecraft.core.BlockPos(8+p.x,80+p.y,8+p.z);boolean centered=com.vandorlabs.persistence.CanopySettingsData.supports(kind)||kind.startsWith("space_visor");
                var links=new NativeCanopyLinks(anchor,null,1,centered,com.vandorlabs.persistence.CanopySettingsData.supports(kind),null,java.util.Set.of(),false,false,0,false,false,false);
                var nativeCells=NativeCanopyFootprint.cells(kind,anchor,net.minecraft.core.Direction.from3DDataValue(p.facing),settings,links);
                need(!nativeCells.contains(new net.minecraft.core.BlockPos(8,80,8))==row.get("result").getAsString().equals("SUCCESS"),"actual Source whole-footprint admission "+kind);
                callbacks++;if(!row.get("result").getAsString().equals("SUCCESS"))continue;successes++;
                var placed=row.getAsJsonObject("placed");var root=placed.getAsJsonArray("root");
                need(root.get(0).getAsInt()==8+p.x&&root.get(1).getAsInt()==80+p.y&&root.get(2).getAsInt()==8+p.z,"actual Source item anchor "+row);
                var saved=CanopySettingsChecks.tags(placed.getAsJsonObject("saved"));
                need(saved.getInt("CanopyMount")==p.mount,"actual Source mount");
                var cells=placed.getAsJsonArray("cells");for(var raw:cells){var cell=raw.getAsJsonObject();need(cell.get("facing").getAsInt()==p.facing,"actual Source member facing");need(cell.getAsJsonArray("anchor").equals(root),"Source member anchor");}
                java.util.Set<net.minecraft.core.BlockPos> actualCells=new java.util.HashSet<>(nativeCells);need(actualCells.size()==cells.size(),"actual Source shell/fixed footprint size");for(var raw:cells){var q=raw.getAsJsonObject().getAsJsonArray("pos");need(actualCells.remove(new net.minecraft.core.BlockPos(q.get(0).getAsInt(),q.get(1).getAsInt(),q.get(2).getAsInt())),"actual Source shell/fixed member");}need(actualCells.isEmpty(),"complete actual Source shell/fixed footprint");
                if(!CanopySettingsData.supports(kind)) {
                    need(cells.size()==p.occupiedWidth*length*height,"actual fixed Source footprint count");
                    int[] dx={0,0,0,0,-1,1},dz={0,0,-1,1,0,0};int cross=switch(p.facing){case 2->5;case 3->4;case 4->2;case 5->3;default->throw new AssertionError();};
                    java.util.Set<String> expected=new java.util.HashSet<>();
                    for(int x=0;x<p.occupiedWidth;x++)for(int z=0;z<length;z++)for(int y=0;y<height;y++)expected.add((8+p.x+dx[cross]*x-dx[p.facing]*z)+","+(80+p.y+y)+","+(8+p.z+dz[cross]*x-dz[p.facing]*z));
                    for(var raw:cells){var pos=raw.getAsJsonObject().getAsJsonArray("pos");need(expected.remove(pos.get(0).getAsInt()+","+pos.get(1).getAsInt()+","+pos.get(2).getAsInt()),"actual fixed Source member");fixedCells++;}
                    need(expected.isEmpty(),"complete fixed Source footprint");
                }else variable++;
            }
        }
        need(callbacks==1056&&successes==912&&variable>0,"frozen actual Source item coverage");
        System.out.println("[vandorlabs][canopy-placement] PASS SourceCallbacks="+callbacks+" successful="+successes+" fixedCells="+fixedCells+" variableAnchors="+variable+" fields="+checks+" exactWholeFootprintEligibility");
    }
}
