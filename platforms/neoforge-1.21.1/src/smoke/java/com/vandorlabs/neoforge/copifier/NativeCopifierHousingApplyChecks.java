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
import net.minecraft.world.level.block.Rotation;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStartedEvent;

/** Real canonical setters must preserve the installed owner, orientation and unrelated snapshot fields. */
@EventBusSubscriber(modid="vandorlabs")
public final class NativeCopifierHousingApplyChecks {
    private static int checks;
    private static final BlockPos BASE=new BlockPos(14680,100,14640);
    private static void read(ServerLevel level){level.getChunkAt(BASE);need(level.getBlockEntity(BASE) instanceof ProgrammableBlockEntity,"saved copied owner");var tile=(ProgrammableBlockEntity)level.getBlockEntity(BASE);var actor=new net.neoforged.neoforge.common.util.FakePlayer(level,new com.mojang.authlib.GameProfile(new UUID(0,1872),"HousingCopyApply"));actor.setGameMode(GameType.SURVIVAL);actor.setPos(BASE.getX()+.5,BASE.getY()+1,BASE.getZ()-2);var projection=NativeCopifierHousingWorld.capture(level,BASE,actor,Direction.NORTH);need(projection!=null&&projection.getInt("wall_texture")==7,"saved copied artwork");var tx=projection.getCompound("Transmission");need(tx.getInt("Range")==16&&tx.getBoolean("Local")&&java.util.Arrays.equals(tx.getIntArray("Channels"),new int[]{23}),"copied metadata survives process restart");need(!tile.saveWithoutMetadata(level.registryAccess()).getCompound("ForgeData").contains("VandorTransmitters"),"legacy module stays retired");System.out.println("[vandorlabs][copifier-housing-apply] read PASS checks="+checks);}

    private static void need(boolean ok,String message){checks++;if(!ok)throw new AssertionError("Housing apply: "+message);}
    private static void run(ServerLevel level)throws Exception{
        var actor=new net.neoforged.neoforge.common.util.FakePlayer(level,new com.mojang.authlib.GameProfile(new UUID(0,1872),"HousingCopyApply"));actor.setGameMode(GameType.SURVIVAL);
        byte[] raw;try(var input=new GZIPInputStream(Files.newInputStream(Path.of(System.getProperty("vandorlabs.copifierHousingReference"))))){raw=input.readAllBytes();}
        int cases=0;var pos=BASE;level.getChunkAt(pos);actor.setPos(pos.getX()+.5,pos.getY()+1,pos.getZ()-2);
        for(var element:JsonParser.parseString(new String(raw,StandardCharsets.UTF_8)).getAsJsonArray()){
            var row=element.getAsJsonObject();if(row.get("meta").getAsInt()!=0)continue;
            var id=ResourceLocation.fromNamespaceAndPath("vandorlabs",row.get("block").getAsString());var block=BuiltInRegistries.BLOCK.get(id);level.removeBlock(pos,false);level.setBlock(pos,block.defaultBlockState().rotate(Rotation.CLOCKWISE_90),18);
            var tile=(ProgrammableBlockEntity)level.getBlockEntity(pos);var original=tile.saveWithoutMetadata(level.registryAccess());original.putString("UnrelatedCopyMarker","preserved");tile.loadWithComponents(original,level.registryAccess());var state=level.getBlockState(pos);
            var values=TagParser.parseTag(row.get("capture").getAsString());var supplied=values.copy();need(NativeCopifierHousingWorld.applySingle(level,pos,actor,Direction.NORTH,values),"accepted Source request "+id);
            need(level.getBlockEntity(pos)==tile&&level.getBlockState(pos)==state,"same owner and orientation "+id);need(tile.saveWithoutMetadata(level.registryAccess()).getString("UnrelatedCopyMarker").equals("preserved"),"unrelated NBT preserved");need(values.equals(supplied),"clipboard unchanged");
            var actual=NativeCopifierHousingWorld.capture(level,pos,actor,Direction.NORTH);var expected=TagParser.parseTag(row.get("recaptured").getAsString());need(expected.equals(actual),"Source result "+id+" profile="+row.get("profile")+" expected="+expected+" actual="+actual);
            var before=tile.saveWithoutMetadata(level.registryAccess());var invalid=values.copy();invalid.putInt("texture_layout",999);need(!NativeCopifierHousingWorld.applySingle(level,pos,actor,Direction.NORTH,invalid)&&before.equals(tile.saveWithoutMetadata(level.registryAccess())),"refusal before mutation");
            actor.setGameMode(GameType.SPECTATOR);need(!NativeCopifierHousingWorld.applySingle(level,pos,actor,Direction.NORTH,values)&&before.equals(tile.saveWithoutMetadata(level.registryAccess())),"spectator denial unchanged");actor.setGameMode(GameType.SURVIVAL);cases++;
        }
        // Replaying a proven old hardware module after a copy must not restore its old range.
        var cube=BuiltInRegistries.BLOCK.get(ResourceLocation.parse("vandorlabs:programmable_block"));level.removeBlock(pos,false);level.setBlock(pos,cube.defaultBlockState(),18);var tile=(ProgrammableBlockEntity)level.getBlockEntity(pos);var old=tile.saveWithoutMetadata(level.registryAccess());var forge=new CompoundTag();var modules=new CompoundTag();var module=new CompoundTag();module.putString("id","vandorlabs:redstone_transmitter");module.putByte("Count",(byte)1);module.putShort("Damage",(short)0);var data=new CompoundTag();data.putIntArray("RedstoneChannels",new int[]{13});module.put("tag",data);modules.put("main",module);forge.put("VandorTransmitters",modules);old.put("ForgeData",forge);tile.loadWithComponents(old,level.registryAccess());
        var copied=new CompoundTag();copied.putInt("wall_texture",7);var tx=new CompoundTag();tx.putIntArray("Channels",new int[]{23});tx.putInt("Range",16);tx.putBoolean("Local",true);copied.put("Transmission",tx);
        need(NativeCopifierHousingWorld.capture(level,pos,actor,Direction.NORTH).getCompound("Transmission").getInt("Range")==64,"actual old module projection");need(NativeCopifierHousingWorld.applySingle(level,pos,actor,Direction.NORTH,copied),"old module target accepted");
        var after=NativeCopifierHousingWorld.capture(level,pos,actor,Direction.NORTH).getCompound("Transmission");need(after.getInt("Range")==16&&after.getBoolean("Local")&&java.util.Arrays.equals(after.getIntArray("Channels"),new int[]{23}),"old module does not undo new copy");need(!tile.saveWithoutMetadata(level.registryAccess()).getCompound("ForgeData").contains("VandorTransmitters"),"proven module migration commits once");
        System.out.println("[vandorlabs][copifier-housing-apply] PASS cases="+cases+" checks="+checks+" canonical setters/Source results/no replacement/no inventory reload; held item/connected/undo pending");
    }
    @SubscribeEvent public static void started(ServerStartedEvent event){if(!System.getProperty("vandorlabs.transmitterPhase","").startsWith("copifier-housing-apply"))return;try{if(System.getProperty("vandorlabs.transmitterPhase").endsWith("read"))read(event.getServer().overworld());else run(event.getServer().overworld());event.getServer().saveEverything(true,true,true);}catch(Throwable failure){failure.printStackTrace();System.out.println("[vandorlabs][copifier-housing-apply] FAIL "+failure);}finally{event.getServer().halt(false);}}
}
