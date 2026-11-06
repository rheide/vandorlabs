package com.vandorlabs.neoforge.trapdoor;

import com.google.gson.*;
import java.nio.file.*;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.*;

/** Actual released coordinates and saved relation bounds, followed by a marked native save/reload. */
public final class TrapdoorLinksChecks {
    private static int checks;
    private static void need(boolean result,String message){checks++;if(!result)throw new AssertionError(message);}
    private record Leaf(BlockPos position,NativeTrapdoorLinks links,boolean diagonal,boolean cover,int geometryPosition,boolean sliding) implements NativeTrapdoorLifecycle.Leaf {}
    public static void main(String[] args)throws Exception{
        
        var rows=JsonParser.parseString(Files.readString(Path.of(args[0]))).getAsJsonArray();int tiles=0;
        for(var element:rows){var row=element.getAsJsonObject();Map<BlockPos,Leaf> world=new LinkedHashMap<>();int index=0;for(var raw:row.getAsJsonArray("input")){var tag=TagParser.parseTag(raw.getAsString());var links=NativeTrapdoorLinks.read(tag);boolean diagonal=row.get("diagonal").getAsBoolean()^(row.get("fault").getAsInt()==9&&index>0);var position=new BlockPos(tag.getInt("x"),tag.getInt("y"),tag.getInt("z"));world.put(position,new Leaf(position,links,diagonal,!diagonal&&tag.getBoolean("TrapdoorCover"),tag.getInt("TrapdoorPosition"),tag.getBoolean("TrapdoorSliding")));index++;var members=tag.getList("TrapdoorAssembly",Tag.TAG_LONG);need(links.assembly().size()==(members.size()>64?0:members.size()),"released member cap/order");for(int i=0;i<links.assembly().size();i++)need(NativeTrapdoorLinks.releasedPosition(links.assembly().get(i))==((LongTag)members.get(i)).getAsLong(),"released packed assembly coordinate");if(links.partner()!=null)need(NativeTrapdoorLinks.releasedPosition(links.partner())==tag.getLong("TrapdoorPartner"),"released packed partner coordinate");if(links.square()!=null)need(NativeTrapdoorLinks.releasedPosition(links.square())==tag.getLong("TrapdoorSquare"),"released packed square coordinate");var saved=tag.copy();links.write(saved);need(NativeTrapdoorLinks.read(saved).equals(links),"native marked links survive process format");need(saved.getInt("NativeTrapdoorLinks")==1,"distinct native coordinate marker");tiles++;}var owner=world.values().iterator().next();var access=new NativeTrapdoorLifecycle.Loaded(){public boolean loaded(BlockPos at){return row.get("fault").getAsInt()!=3||(at.getX()>>4)==0&&(at.getZ()>>4)==0;}public NativeTrapdoorLifecycle.Leaf leaf(BlockPos at){return world.get(at);}};var group=NativeTrapdoorLifecycle.group(owner,access);var expected=row.getAsJsonObject("before").getAsJsonArray("group");need(group.size()==expected.size(),"source group size "+row);for(int i=0;i<group.size();i++)need(NativeTrapdoorLinks.releasedPosition(group.get(i).position())==expected.get(i).getAsLong(),"source group member/order");}
        for(int marker:new int[]{-1,0,2,Integer.MAX_VALUE}){var tag=new CompoundTag();tag.putInt("NativeTrapdoorLinks",marker);boolean rejected=false;try{NativeTrapdoorLinks.read(tag);}catch(IllegalArgumentException expected){rejected=true;}need(rejected,"future/invalid native coordinate marker refused");}
        need(rows.size()==9504,"frozen source row count");System.out.println("Native Trapdoor links PASS rows="+rows.size()+" tiles="+tiles+" checks="+checks);
    }
}
