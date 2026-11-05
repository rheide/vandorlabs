package com.vandorlabs.neoforge;

import com.vandorlabs.neoforge.shape.*;
import com.vandorlabs.persistence.PanelGlassSettings;
import io.netty.buffer.Unpooled;
import java.util.Arrays;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;

final class PortholePayloadChecks {
    private static int checks;
    private static void require(boolean value,String message){checks++;if(!value)throw new AssertionError("Porthole codec/session "+message);}
    private static byte[] encode(ServerLevel level,PortholeGlassPayload value){var buffer=new RegistryFriendlyByteBuf(Unpooled.buffer(),level.registryAccess());try{PortholeGlassPayload.CODEC.encode(buffer,value);byte[] bytes=new byte[buffer.readableBytes()];buffer.readBytes(bytes);return bytes;}finally{buffer.release();}}
    private static PortholeGlassPayload decode(ServerLevel level,byte[] bytes){var buffer=new RegistryFriendlyByteBuf(Unpooled.wrappedBuffer(bytes),level.registryAccess());try{return PortholeGlassPayload.CODEC.decode(buffer);}finally{buffer.release();}}
    private static void rejects(ServerLevel level,byte[] bytes){boolean rejected=false;try{decode(level,bytes);}catch(IllegalArgumentException expected){rejected=true;}require(rejected,"Malformed rejected before host mutation");}
    static void run(ServerLevel level){
        var pos=new BlockPos(2603,100,2600);level.getChunkAt(pos);var state=VandorLabsBootstrap.PROGRAMMABLE_BLOCK.get().defaultBlockState();level.setBlock(pos,state,3);var host=new NativeWallEntity(VandorLabsBootstrap.PROGRAMMABLE_ENTITY.get(),pos,state,true);level.setBlockEntity(host);var player=net.neoforged.neoforge.common.util.FakePlayerFactory.getMinecraft(level);player.setPos(pos.getX()+.5,pos.getY(),pos.getZ()+2.5);player.setGameMode(GameType.SURVIVAL);player.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(VandorLabsBootstrap.CONFIGURIZER.get()));player.containerMenu=new ProgrammableMenu(63,player.getInventory(),pos);
        for(int mode=0;mode<3;mode++)for(int speed=0;speed<3;speed++){var packet=new PortholeGlassPayload(pos,63,new PanelGlassSettings(18,129,2,true,3,false,false),new PanelPlayback(mode,speed),false,3);var bytes=encode(level,packet);require(bytes.length==26,"Fixed26 bytes");var decoded=decode(level,bytes);require(decoded.glass().shade==18&&decoded.glass().material==129&&decoded.glass().layout==2&&decoded.playback().equals(packet.playback()),"Exact scalar roundtrip");require(PortholeGlassPayload.apply(player,decoded)&&host.panelPlayback().equals(packet.playback())&&host.panelGlassSettings().tint==3,"Actual authorized porthole session");
            if(mode==0&&speed==0){for(int size=0;size<26;size++)rejects(level,Arrays.copyOf(bytes,size));rejects(level,Arrays.copyOf(bytes,27));for(int offset:new int[]{12,13,18,19,20,21,22,23,24,25}){var invalid=bytes.clone();invalid[offset]=(byte)(offset==12?19:offset==13||offset==25?4:offset==18||offset==22||offset==23?3:2);rejects(level,invalid);}for(int offset:new int[]{8,14}){var invalid=bytes.clone();java.nio.ByteBuffer.wrap(invalid).putInt(offset,-2);rejects(level,invalid);}}
        }
        var valid=new PortholeGlassPayload(pos,63,host.panelGlassSettings(),host.panelPlayback(),false,3);var snapshot=host.wallAppearance();require(!PortholeGlassPayload.apply(player,new PortholeGlassPayload(pos,64,valid.glass(),valid.playback(),false,3))&&host.wallAppearance()==snapshot,"Stale session");player.setItemInHand(InteractionHand.MAIN_HAND,ItemStack.EMPTY);require(!PortholeGlassPayload.apply(player,valid),"Held tool auth");player.setGameMode(GameType.SPECTATOR);require(!PortholeGlassPayload.apply(player,valid),"Spectator auth");player.setGameMode(GameType.CREATIVE);require(PortholeGlassPayload.apply(player,valid),"Creative auth");player.setPos(pos.getX()+20,pos.getY(),pos.getZ());require(!PortholeGlassPayload.apply(player,valid),"Reach auth");player.setPos(pos.getX()+.5,pos.getY(),pos.getZ()+2.5);level.setBlockEntity(new NativeWallEntity(VandorLabsBootstrap.PROGRAMMABLE_ENTITY.get(),pos,state,true));require(!PortholeGlassPayload.apply(player,valid),"Replacement identity");player.containerMenu=new ProgrammableMenu(63,player.getInventory(),pos);level.setBlockEntity(new NativeWallEntity(VandorLabsBootstrap.PROGRAMMABLE_ENTITY.get(),pos,state,false));player.containerMenu=new ProgrammableMenu(63,player.getInventory(),pos);require(!PortholeGlassPayload.apply(player,valid),"Plain wall cannot receive insert transaction");
        System.out.println("[vandorlabs][porthole-payload] PASS checks="+checks+" fixed26byte bounds nine playback pairs actual injected menu auth; registration/UI/render separate");
    }
}
