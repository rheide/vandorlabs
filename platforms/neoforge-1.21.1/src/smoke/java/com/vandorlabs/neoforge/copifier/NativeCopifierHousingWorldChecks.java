package com.vandorlabs.neoforge.copifier;

import com.google.gson.*;
import com.vandorlabs.neoforge.ProgrammableBlockEntity;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.zip.GZIPInputStream;
import net.minecraft.core.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.*;
import net.minecraft.world.level.GameType;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStartedEvent;

/** Actual canonical factories and installed authorization, independent of future item/menu routes. */
@EventBusSubscriber(modid="vandorlabs")
public final class NativeCopifierHousingWorldChecks {
    private static int checks;
    private static void need(boolean ok,String message){checks++;if(!ok)throw new AssertionError("World housing capture: "+message);}
    private static void run(ServerLevel level)throws Exception{
        final Direction[] deniedFace={null};
        var actor=new net.neoforged.neoforge.common.util.FakePlayer(level,new com.mojang.authlib.GameProfile(new UUID(0,1871),"HousingCopyCapture")){
            @Override public boolean mayUseItemAt(BlockPos pos,Direction face,net.minecraft.world.item.ItemStack held){return face!=deniedFace[0]&&super.mayUseItemAt(pos,face,held);}
        };actor.setGameMode(GameType.CREATIVE);
        byte[] raw;try(var input=new GZIPInputStream(Files.newInputStream(Path.of(System.getProperty("vandorlabs.copifierHousingReference"))))){raw=input.readAllBytes();}
        int cases=0;var pos=new BlockPos(14640,100,14640);level.getChunkAt(pos);
        for(var element:JsonParser.parseString(new String(raw,StandardCharsets.UTF_8)).getAsJsonArray()){
            var row=element.getAsJsonObject();if(row.get("meta").getAsInt()!=0)continue;
            var id=ResourceLocation.fromNamespaceAndPath("vandorlabs",row.get("block").getAsString());need(BuiltInRegistries.BLOCK.containsKey(id),"canonical id "+id);
            var block=BuiltInRegistries.BLOCK.get(id);level.removeBlock(pos,false);level.setBlock(pos,block.defaultBlockState(),18);need(level.getBlockEntity(pos) instanceof ProgrammableBlockEntity,"real factory "+id);
            var tile=(ProgrammableBlockEntity)level.getBlockEntity(pos);var original=TagParser.parseTag(row.get("input").getAsString());tile.loadWithComponents(original,level.registryAccess());
            actor.setPos(pos.getX()+.5,pos.getY()+1,pos.getZ()-2);var before=tile.saveWithoutMetadata(level.registryAccess());
            var capture=NativeCopifierHousingWorld.capture(level,pos,actor,Direction.NORTH);var expected=TagParser.parseTag(row.get("capture").getAsString());
            need(expected.equals(capture),"Source capture "+id+" profile="+row.get("profile")+" expected="+expected+" actual="+capture);
            need(before.equals(tile.saveWithoutMetadata(level.registryAccess())),"capture preserves installed snapshot");
            actor.setGameMode(GameType.SURVIVAL);need(expected.equals(NativeCopifierHousingWorld.capture(level,pos,actor,Direction.NORTH)),"Survival capture uses ordinary edit permission");actor.setGameMode(GameType.CREATIVE);
            for(var face:Direction.values()){deniedFace[0]=face;need(NativeCopifierHousingWorld.capture(level,pos,actor,face)==null,"actual clicked-face refusal "+face);need(NativeCopifierHousingWorld.capture(level,pos,actor,face.getOpposite())!=null,"different allowed face "+face);}deniedFace[0]=null;
            actor.setGameMode(GameType.SPECTATOR);need(NativeCopifierHousingWorld.capture(level,pos,actor,Direction.NORTH)==null,"spectator refused");actor.setGameMode(GameType.CREATIVE);
            actor.setPos(pos.getX()+100,pos.getY(),pos.getZ());need(NativeCopifierHousingWorld.capture(level,pos,actor,Direction.NORTH)==null,"distant refused");actor.setPos(pos.getX()+.5,pos.getY()+1,pos.getZ()-2);
            var workerFailure=new java.util.concurrent.atomic.AtomicReference<Throwable>();var worker=new Thread(()->{try{if(NativeCopifierHousingWorld.capture(level,pos,actor,Direction.NORTH)!=null)throw new AssertionError("worker accepted");}catch(Throwable failure){workerFailure.set(failure);}});worker.start();worker.join();need(workerFailure.get()==null,"worker refused before world reads: "+workerFailure.get());cases++;
        }
        var unloaded=new BlockPos(1100000,100,1100000);need(!level.hasChunkAt(unloaded),"cold fixture");need(NativeCopifierHousingWorld.capture(level,unloaded,actor,Direction.NORTH)==null&&!level.hasChunkAt(unloaded),"no cold chunk loading");
        System.out.println("[vandorlabs][copifier-housing-world] PASS cases="+cases+" checks="+checks+" canonical installed capture/no mutation/authorization; no held-tool route claim");
    }
    @SubscribeEvent public static void started(ServerStartedEvent event){if(!System.getProperty("vandorlabs.transmitterPhase","").equals("copifier-housing-world"))return;try{run(event.getServer().overworld());event.getServer().saveEverything(true,true,true);}catch(Throwable failure){failure.printStackTrace();System.out.println("[vandorlabs][copifier-housing-world] FAIL "+failure);}finally{event.getServer().halt(false);}}
}
