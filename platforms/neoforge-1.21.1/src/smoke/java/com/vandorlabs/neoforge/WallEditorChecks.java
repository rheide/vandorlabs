package com.vandorlabs.neoforge;

import com.vandorlabs.neoforge.shape.*;
import com.vandorlabs.persistence.*;
import com.vandorlabs.ui.*;
import io.netty.buffer.Unpooled;
import java.util.Arrays;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.*;
import net.minecraft.world.level.GameType;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStartedEvent;

/** Fixed codec and actual server session over an injected wall host; registration/widget checks are separate. */
@EventBusSubscriber(modid="vandorlabs")
public final class WallEditorChecks {
    private static int checks;
    private static void require(boolean value,String message){checks++;if(!value)throw new IllegalStateException("Native wall editor: "+message);}
    private static byte[] encode(ServerLevel level,WallAppearancePayload value){var buffer=new RegistryFriendlyByteBuf(Unpooled.buffer(),level.registryAccess());try{WallAppearancePayload.CODEC.encode(buffer,value);byte[] bytes=new byte[buffer.readableBytes()];buffer.readBytes(bytes);return bytes;}finally{buffer.release();}}
    private static WallAppearancePayload decode(ServerLevel level,byte[] bytes){var buffer=new RegistryFriendlyByteBuf(Unpooled.wrappedBuffer(bytes),level.registryAccess());try{return WallAppearancePayload.CODEC.decode(buffer);}finally{buffer.release();}}
    private static void rejects(ServerLevel level,byte[] bytes){boolean failed=false;try{decode(level,bytes);}catch(IllegalArgumentException expected){failed=true;}require(failed,"malformed fixed payload rejected");}
    @SubscribeEvent public static void start(ServerStartedEvent event){
        if(!System.getProperty("vandorlabs.transmitterPhase","").equals("wall-editor"))return;var level=event.getServer().overworld();var pos=new BlockPos(2600,100,2600);level.getChunkAt(pos);var state=VandorLabsBootstrap.PROGRAMMABLE_BLOCK.get().defaultBlockState();level.setBlock(pos,state,3);var wall=new NativeWallEntity(VandorLabsBootstrap.PROGRAMMABLE_ENTITY.get(),pos,state,false);level.setBlockEntity(wall);
        var draft=NativeWallDialog.editor(wall.wallAppearance());require(NativeWallDialog.defaultScreen()==129,"actual Forge pane defaultScreen reference129");var original=draft.frames;draft=draft.action(197,1);require(draft.frames==original&&draft.frameDraft.splitTop==129&&draft.frameDraft.exterior==-1,"opening frame pane edits only private draft");draft=draft.action(195,1).choose(7);require(draft.frames==original&&draft.frames.exterior==-2,"main choice preserves uncommitted sentinel");draft=draft.action(197,1).choose(-1);require(draft.frames.splitTop==129&&draft.frames.exterior==-1&&draft.frames.interior==-1,"Original artwork frame choice publishes normalized draft");var packet=new WallAppearancePayload(pos,61,NativeWallDialog.appearance(draft),draft.walls,draft.frames);var bytes=encode(level,packet);require(bytes.length==79,"fixed79 bytes");var decoded=decode(level,bytes);require(decoded.frames().interior==-1&&decoded.frames().exterior==-1&&decoded.frames().splitTop==129&&decoded.base().housing().mainTexture==7,"full header and sparse frame sentinel roundtrip");
        for(int length=0;length<79;length++)rejects(level,Arrays.copyOf(bytes,length));rejects(level,Arrays.copyOf(bytes,80));
        for(int offset:new int[]{48,49,50,51,52,53,66,75,76,77,78}){byte[] invalid=bytes.clone();invalid[offset]=(byte)(offset==50?4:offset==51?12:offset==53?4:2);rejects(level,invalid);}
        var player=net.neoforged.neoforge.common.util.FakePlayerFactory.getMinecraft(level);player.setPos(pos.getX()+.5,pos.getY(),pos.getZ()+2.5);player.setGameMode(GameType.SURVIVAL);player.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(VandorLabsBootstrap.CONFIGURIZER.get()));player.containerMenu=new ProgrammableMenu(61,player.getInventory(),pos);require(WallAppearancePayload.apply(player,decoded)&&wall.settings().mainTexture==7&&wall.frameSettings().exterior==-1,"actual injected-host authorized menu transaction");var stable=wall.wallAppearance();require(!WallAppearancePayload.apply(player,new WallAppearancePayload(pos,62,decoded.base(),decoded.walls(),decoded.frames()))&&wall.wallAppearance()==stable,"stale session cannot alter any field");player.setItemInHand(InteractionHand.MAIN_HAND,ItemStack.EMPTY);require(!WallAppearancePayload.apply(player,decoded),"survival held tool authorization");player.setGameMode(GameType.SPECTATOR);require(!WallAppearancePayload.apply(player,decoded),"spectator rejected");player.setGameMode(GameType.CREATIVE);require(WallAppearancePayload.apply(player,decoded),"creative permitted");player.setPos(pos.getX()+20,pos.getY(),pos.getZ());require(!WallAppearancePayload.apply(player,decoded),"reach denied");player.setPos(pos.getX()+.5,pos.getY(),pos.getZ()+2.5);level.removeBlock(pos,false);level.setBlock(pos,state,3);require(!WallAppearancePayload.apply(player,decoded),"replacement exact identity denied");
        System.out.println("[vandorlabs][wall-editor] dedicated PASS checks="+checks+" injectedHost=true bounded79byte codec frame draft auth sentinels; registered wall GUI acceptance separate");event.getServer().halt(false);
    }
}
