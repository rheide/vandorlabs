package com.vandorlabs.neoforge.copifier;

import com.google.gson.*;
import com.vandorlabs.neoforge.trapdoor.*;
import java.nio.file.*;
import java.util.*;
import java.util.zip.GZIPInputStream;
import net.minecraft.core.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.*;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.Half;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStartedEvent;

/** Canonical native leaves, actual Source callback results and independent restart. */
@EventBusSubscriber(modid="vandorlabs")
public final class NativeCopifierTrapdoorWorldChecks {
    private static final BlockPos BASE=new BlockPos(22504,90,22504);private static int checks;
    private static void need(boolean yes,String why){checks++;if(!yes)throw new AssertionError("Copied Trapdoor: "+why);}
    private static NativeTrapdoorEntity put(ServerLevel level,BlockPos pos,boolean diagonal,int face,int meta,CompoundTag input){
        level.getChunkAt(pos);level.setBlock(pos,Blocks.AIR.defaultBlockState(),18);var block=(NativeTrapdoorBlock)BuiltInRegistries.BLOCK.get(ResourceLocation.parse("vandorlabs:"+(diagonal?"programmable_diagonal_trapdoor":"programmable_trapdoor")));
        var state=block.defaultBlockState().setValue(NativeTrapdoorBlock.FACING,Direction.from3DDataValue(face)).setValue(NativeTrapdoorBlock.HALF,(meta&8)!=0?Half.TOP:Half.BOTTOM).setValue(NativeTrapdoorBlock.OPEN,(meta&4)!=0);level.setBlock(pos,state,18);var owner=(NativeTrapdoorEntity)level.getBlockEntity(pos);var data=input.copy();data.putString("CopiedTrapdoorMarker","retained");owner.loadWithComponents(data,level.registryAccess());need(!owner.inert(),"available owner "+owner.failureReason());return owner;
    }
    private static void write(ServerLevel level)throws Exception{
        var actor=new net.neoforged.neoforge.common.util.FakePlayer(level,new com.mojang.authlib.GameProfile(new UUID(0,1895),"CopiedTrapdoors"));actor.setGameMode(GameType.SURVIVAL);actor.setPos(BASE.getX()+.5,BASE.getY()+1,BASE.getZ()-2);
        byte[] raw;try(var in=new GZIPInputStream(Files.newInputStream(Path.of(System.getProperty("vandorlabs.copifierTrapdoorReference"))))){raw=in.readAllBytes();}int count=0;
        for(var element:JsonParser.parseString(new String(raw,java.nio.charset.StandardCharsets.UTF_8)).getAsJsonArray()){
            var row=element.getAsJsonObject();var owner=put(level,BASE,row.get("diagonal").getAsBoolean(),row.get("face").getAsInt(),row.get("meta").getAsInt(),TagParser.parseTag(row.get("input").getAsString()));var before=NativeCopifierHousingWorld.capture(level,BASE,actor,Direction.NORTH);need(TagParser.parseTag(row.get("before").getAsString()).equals(before),"Source before case="+count+" actual="+before);
            var values=TagParser.parseTag(row.get("values").getAsString());var original=values.copy();NativeCopifierUndo.begin(actor,level);boolean applied;try{applied=NativeCopifierHousingWorld.applySingle(level,BASE,actor,Direction.NORTH,values);}finally{NativeCopifierUndo.finish(actor);}
            need(applied==row.get("applied").getAsBoolean(),"Source applicability case="+count);var after=NativeCopifierHousingWorld.capture(level,BASE,actor,Direction.NORTH);need(TagParser.parseTag(row.get("after").getAsString()).equals(after),"Source after case="+count+" values="+values+" actual="+after);need(level.getBlockEntity(BASE)==owner&&values.equals(original),"same physical owner and clipboard case="+count);need(owner.saveWithoutMetadata(level.registryAccess()).getString("CopiedTrapdoorMarker").equals("retained"),"opaque data case="+count);
            need(owner.getBlockState().getValue(NativeTrapdoorBlock.FACING).get3DDataValue()==row.get("afterFace").getAsInt()&&(owner.getBlockState().getValue(NativeTrapdoorBlock.HALF)==Half.TOP)==row.get("afterTop").getAsBoolean(),"Source metadata case="+count);
            if(applied&&!before.equals(after)){need(NativeCopifierUndo.undo(actor),"common history undo case="+count);need(TagParser.parseTag(row.get("undo").getAsString()).equals(NativeCopifierHousingWorld.capture(level,BASE,actor,Direction.NORTH)),"Source callback undo case="+count);}count++;
        }
        need(count==768,"full Source corpus");var owner=put(level,BASE,false,Direction.NORTH.get3DDataValue(),0,new CompoundTag());var values=new CompoundTag();values.putInt("wall_texture",7);values.putInt("trapdoor_texture_layout",2);values.putBoolean("door_sliding",true);var before=owner.saveWithoutMetadata(level.registryAccess());need(!java.util.concurrent.CompletableFuture.supplyAsync(()->owner.configureCopied(values,actor)).join()&&owner.saveWithoutMetadata(level.registryAccess()).equals(before),"worker refuses before world reads");
        var denied=new net.neoforged.neoforge.common.util.FakePlayer(level,new com.mojang.authlib.GameProfile(new UUID(0,1896),"DeniedTrapdoor")){@Override public boolean mayUseItemAt(BlockPos pos,Direction side,net.minecraft.world.item.ItemStack item){return !pos.equals(BASE);}};denied.setPos(actor.position());denied.setGameMode(GameType.SURVIVAL);need(!NativeCopifierHousingWorld.applySingle(level,BASE,denied,Direction.NORTH,values)&&owner.saveWithoutMetadata(level.registryAccess()).equals(before),"protected owner refuses before mutation");
        var badPos=BASE.east(4);var failed=put(level,badPos,true,Direction.NORTH.get3DDataValue(),0,new CompoundTag());var recovery=failed.saveWithoutMetadata(level.registryAccess());recovery.putInt("NativeTrapdoorLinks",99);recovery.putByteArray("OpaqueRecoveryPayload",new byte[65536]);failed.loadWithComponents(recovery,level.registryAccess());need(failed.inert()&&!failed.configureCopied(values,actor)&&failed.saveWithoutMetadata(level.registryAccess()).equals(recovery)&&NativeCopifierHousingWorld.capture(level,badPos,actor,Direction.NORTH)==null,"future links retain complete inert original");
        var replacement=put(level,BASE,false,Direction.NORTH.get3DDataValue(),0,new CompoundTag());need(!owner.configureCopied(values,actor)&&level.getBlockEntity(BASE)==replacement,"retired owner refuses replacement");need(NativeCopifierHousingWorld.applySingle(level,BASE,actor,Direction.NORTH,values),"saved copied choices");level.setChunkForced(BASE.getX()>>4,BASE.getZ()>>4,true);System.out.println("[vandorlabs][copifier-trapdoor] write PASS cases="+count+" checks="+checks);
    }
    private static void groupMatches(ServerLevel level,ServerPlayer actor,List<NativeTrapdoorEntity> owners,JsonArray expected,String why)throws Exception{
        for(int i=0;i<owners.size();i++){var owner=owners.get(i);var cell=expected.get(i).getAsJsonObject();need(level.getBlockEntity(owner.getBlockPos())==owner,why+" exact owner="+i);need(TagParser.parseTag(cell.get("capture").getAsString()).equals(NativeCopifierHousingWorld.capture(level,owner.getBlockPos(),actor,Direction.NORTH)),why+" Source choices="+i);need(owner.getBlockState().getValue(NativeTrapdoorBlock.FACING).get3DDataValue()==cell.get("face").getAsInt()&&(owner.getBlockState().getValue(NativeTrapdoorBlock.HALF)==Half.TOP)==cell.get("top").getAsBoolean(),why+" Source metadata="+i);}
    }
    private static void groups(ServerLevel level)throws Exception{
        var actor=new net.neoforged.neoforge.common.util.FakePlayer(level,new com.mojang.authlib.GameProfile(new UUID(0,1897),"CopiedTrapdoorGroups"));actor.setGameMode(GameType.SURVIVAL);List<BlockPos> previous=new ArrayList<>();int count=0;byte[] raw;
        try(var stream=new GZIPInputStream(Files.newInputStream(Path.of(System.getProperty("vandorlabs.copifierTrapdoorGroupReference"))))){raw=stream.readAllBytes();}
        for(var element:JsonParser.parseString(new String(raw,java.nio.charset.StandardCharsets.UTF_8)).getAsJsonArray()){
            for(var pos:previous)level.setBlock(pos,Blocks.AIR.defaultBlockState(),18);previous.clear();var row=element.getAsJsonObject();var owners=new ArrayList<NativeTrapdoorEntity>();
            for(var data:row.getAsJsonArray("input")){var cell=data.getAsJsonObject();var tag=TagParser.parseTag(cell.get("data").getAsString());var pos=new BlockPos(tag.getInt("x"),tag.getInt("y"),tag.getInt("z"));owners.add(put(level,pos,row.get("diagonal").getAsBoolean(),cell.get("face").getAsInt(),cell.get("meta").getAsInt(),tag));previous.add(pos);}
            var selected=owners.get(row.get("selected").getAsInt());actor.setPos(selected.getBlockPos().getX()+.5,selected.getBlockPos().getY()+1,selected.getBlockPos().getZ()-2);var members=new java.util.HashSet<BlockPos>();for(var index:row.getAsJsonArray("members"))members.add(owners.get(index.getAsInt()).getBlockPos());need(members.equals(selected.group().stream().map(NativeTrapdoorEntity::getBlockPos).collect(java.util.stream.Collectors.toSet())),"actual Source formation members case="+count);
            groupMatches(level,actor,owners,row.getAsJsonArray("before"),"group before="+count);var before=NativeCopifierHousingWorld.capture(level,selected.getBlockPos(),actor,Direction.NORTH);var values=TagParser.parseTag(row.get("values").getAsString());NativeCopifierUndo.begin(actor,level);boolean applied;try{applied=NativeCopifierHousingWorld.applySingle(level,selected.getBlockPos(),actor,Direction.NORTH,values);}finally{NativeCopifierUndo.finish(actor);}need(applied==row.get("applied").getAsBoolean(),"group Source applicability="+count);groupMatches(level,actor,owners,row.getAsJsonArray("after"),"group after="+count);
            if(applied&&!before.equals(NativeCopifierHousingWorld.capture(level,selected.getBlockPos(),actor,Direction.NORTH))){need(NativeCopifierUndo.undo(actor),"group callback undo="+count);groupMatches(level,actor,owners,row.getAsJsonArray("undo"),"group undo="+count);}count++;
        }
        for(var pos:previous)level.setBlock(pos,Blocks.AIR.defaultBlockState(),18);need(count==288,"full Source formed-group corpus");System.out.println("[vandorlabs][copifier-trapdoor] groups PASS cases="+count+" checks="+checks);
    }
    private static void read(ServerLevel level){level.getChunkAt(BASE);var owner=(NativeTrapdoorEntity)level.getBlockEntity(BASE);need(owner!=null&&!owner.inert()&&owner.settings().choices().texture==7&&owner.settings().choices().layout()==2&&owner.sliding(),"copied choices survive restart");need(owner.saveWithoutMetadata(level.registryAccess()).getString("CopiedTrapdoorMarker").equals("retained"),"opaque data restart");var failed=(NativeTrapdoorEntity)level.getBlockEntity(BASE.east(4));need(failed.inert()&&failed.saveWithoutMetadata(level.registryAccess()).getInt("NativeTrapdoorLinks")==99&&failed.saveWithoutMetadata(level.registryAccess()).getByteArray("OpaqueRecoveryPayload").length==65536,"inert original restart");System.out.println("[vandorlabs][copifier-trapdoor] read PASS checks="+checks);}
    @SubscribeEvent public static void started(ServerStartedEvent event)throws Exception{String phase=System.getProperty("vandorlabs.transmitterPhase","");if(!phase.equals("copifier-trapdoor-write")&&!phase.equals("copifier-trapdoor-read")&&!phase.equals("copifier-trapdoor-groups"))return;var level=event.getServer().overworld();if(phase.endsWith("groups"))groups(level);else if(phase.endsWith("write"))write(level);else read(level);event.getServer().saveEverything(true,true,true);event.getServer().halt(false);}
}
