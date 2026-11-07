package com.vandorlabs.neoforge.copifier;

import com.google.gson.*;
import com.vandorlabs.neoforge.segmented.*;
import java.nio.file.*;
import java.util.*;
import java.util.zip.GZIPInputStream;
import net.minecraft.core.*;
import net.minecraft.nbt.*;
import net.minecraft.server.level.*;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStartedEvent;

/** Canonical sections and actual linked-door lifecycle, shared history and process restart. */
@EventBusSubscriber(modid="vandorlabs")
public final class NativeCopifierSegmentedWorldChecks {
    private static final BlockPos BASE=new BlockPos(20904,100,20904);
    private static final List<BlockPos> previous=new ArrayList<>();private static int checks;
    private static void need(boolean yes,String why){checks++;if(!yes)throw new AssertionError("Copied Segmented Door: "+why);}
    private static List<NativeSegmentedDoorEntity> put(ServerLevel level,JsonObject row){
        for(var pos:previous)level.setBlock(pos,Blocks.AIR.defaultBlockState(),18);previous.clear();
        var owners=new ArrayList<NativeSegmentedDoorEntity>();int width=row.get("width").getAsInt(),height=row.get("height").getAsInt();var face=Direction.from3DDataValue(row.get("face").getAsInt());var state=NativeSegmentedRegistrations.BLOCK.get().defaultBlockState().setValue(NativeSegmentedDoorBlock.FACING,face);
        for(int x=0;x<width;x++)for(int y=0;y<height;y++){
            var pos=BASE.relative(face.getClockWise(),x).below(y);level.getChunkAt(pos);level.setBlock(pos,state,18);previous.add(pos);
            var owner=(NativeSegmentedDoorEntity)level.getBlockEntity(pos);try{var data=TagParser.parseTag(row.getAsJsonArray("input").get(x*height+y).getAsString());data.putString("CopiedSectionMarker","retained");owner.loadWithComponents(data,level.registryAccess());}catch(Exception malformed){throw new IllegalArgumentException(malformed);}owners.add(owner);
        }
        return owners;
    }
    private static void matches(ServerLevel level,ServerPlayer actor,List<NativeSegmentedDoorEntity> owners,JsonArray expected,String why)throws Exception{
        for(int i=0;i<owners.size();i++){var owner=owners.get(i);need(level.getBlockEntity(owner.getBlockPos())==owner,why+" same physical owner section="+i);need(TagParser.parseTag(expected.get(i).getAsString()).equals(NativeCopifierHousingWorld.capture(level,owner.getBlockPos(),actor,Direction.NORTH)),why+" Source choices section="+i);need(owner.saveWithoutMetadata(level.registryAccess()).getString("CopiedSectionMarker").equals("retained"),why+" opaque section fields="+i);}
    }
    private static void write(ServerLevel level)throws Exception{
        var actor=new net.neoforged.neoforge.common.util.FakePlayer(level,new com.mojang.authlib.GameProfile(new UUID(0,1892),"CopiedNativeSections"));actor.setGameMode(GameType.SURVIVAL);actor.setPos(BASE.getX()+.5,BASE.getY(),BASE.getZ()-2);
        byte[] raw;try(var stream=new GZIPInputStream(Files.newInputStream(Path.of(System.getProperty("vandorlabs.copifierSegmentedReference"))))){raw=stream.readAllBytes();}int count=0;JsonObject last=null;
        for(var element:JsonParser.parseString(new String(raw,java.nio.charset.StandardCharsets.UTF_8)).getAsJsonArray()){
            var row=element.getAsJsonObject();last=row;var owners=put(level,row);matches(level,actor,owners,row.getAsJsonArray("before"),"before case="+count);var selected=owners.get(row.get("selected").getAsInt());var before=NativeCopifierHousingWorld.capture(level,selected.getBlockPos(),actor,Direction.NORTH);var values=TagParser.parseTag(row.get("values").getAsString());var original=values.copy();NativeCopifierUndo.begin(actor,level);boolean applied;
            try{applied=NativeCopifierHousingWorld.applySingle(level,selected.getBlockPos(),actor,Direction.NORTH,values);}finally{NativeCopifierUndo.finish(actor);}
            need(applied==row.get("applied").getAsBoolean(),"Source result case="+count);need(values.equals(original),"caller compound case="+count);matches(level,actor,owners,row.getAsJsonArray("after"),"after case="+count);
            if(applied&&!before.equals(NativeCopifierHousingWorld.capture(level,selected.getBlockPos(),actor,Direction.NORTH))){need(NativeCopifierUndo.undo(actor),"shared history undo case="+count);matches(level,actor,owners,row.getAsJsonArray("undo"),"undo case="+count);}count++;
        }
        need(count==576,"all Source cases");var owners=put(level,last);var selected=owners.getFirst();var values=new CompoundTag();values.putIntArray("redstone_channel",new int[]{41,43});var original=selected.saveWithoutMetadata(level.registryAccess());need(!java.util.concurrent.CompletableFuture.supplyAsync(()->selected.configureCopied(values,actor)).join()&&selected.saveWithoutMetadata(level.registryAccess()).equals(original),"worker refuses before world reads");
        var protectedPos=owners.getLast().getBlockPos();var denied=new net.neoforged.neoforge.common.util.FakePlayer(level,new com.mojang.authlib.GameProfile(new UUID(0,1893),"DeniedSection")){@Override public boolean mayUseItemAt(BlockPos pos,Direction side,net.minecraft.world.item.ItemStack stack){return !pos.equals(protectedPos);}};denied.setPos(actor.position());denied.setGameMode(GameType.SURVIVAL);var whole=NativeCopifierSegmentedEdit.capture(selected.copiedState());whole.getCompound("segmented_door").putInt("SegmentedSpeed",27);need(!selected.configureCopied(whole,denied)&&selected.saveWithoutMetadata(level.registryAccess()).equals(original),"protected remote section refuses before any mutation");
        need(selected.configureCopied(values,actor)&&selected.getRedstoneChannels().equals(com.vandorlabs.redstone.ChannelList.of(41,43)),"saved selected channel edit");for(var pos:previous)level.setChunkForced(pos.getX()>>4,pos.getZ()>>4,true);
        var failed=owners.getLast();var recovery=failed.saveWithoutMetadata(level.registryAccess());recovery.putBoolean("SegmentedPlacementRecovery",true);failed.loadWithComponents(recovery,level.registryAccess());need(failed.inert()&&!failed.configureCopied(values,actor)&&NativeCopifierHousingWorld.capture(level,failed.getBlockPos(),actor,Direction.NORTH)==null,"inert placement recovery cannot be copied");
        var retired=owners.get(1);level.setBlock(retired.getBlockPos(),Blocks.AIR.defaultBlockState(),18);need(!retired.configureCopied(values,actor),"retired section cannot change replacement");
        System.out.println("[vandorlabs][copifier-segmented] write PASS cases="+count+" checks="+checks);
    }
    private static void read(ServerLevel level){level.getChunkAt(BASE);var first=(NativeSegmentedDoorEntity)level.getBlockEntity(BASE);need(first!=null&&!first.inert()&&first.getRedstoneChannels().equals(com.vandorlabs.redstone.ChannelList.of(41,43)),"selected channels survive restart");need(first.saveWithoutMetadata(level.registryAccess()).getString("CopiedSectionMarker").equals("retained"),"opaque data survives restart");System.out.println("[vandorlabs][copifier-segmented] read PASS checks="+checks);}
    @SubscribeEvent public static void started(ServerStartedEvent event)throws Exception{String phase=System.getProperty("vandorlabs.transmitterPhase","");if(!phase.equals("copifier-segmented-write")&&!phase.equals("copifier-segmented-read"))return;var level=event.getServer().overworld();if(phase.endsWith("write"))write(level);else read(level);event.getServer().saveEverything(true,true,true);event.getServer().halt(false);}
}
