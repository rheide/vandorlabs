package com.vandorlabs.neoforge;

import com.google.gson.*;
import com.vandorlabs.neoforge.credentials.*;
import com.vandorlabs.persistence.ControlMountSettings;
import com.vandorlabs.redstone.ChannelList;
import io.netty.buffer.Unpooled;
import java.nio.file.*;
import net.minecraft.core.*;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.*;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.level.block.Blocks;

/** Codec closure plus exact current-menu identity/permission and separate main-slot settings. */
final class StandaloneReaderEditorChecks {
    private static int checks;
    private static void need(boolean value,String why){checks++;if(!value)throw new AssertionError("Reader editor: "+why);}
    private static RegistryFriendlyByteBuf bytes(byte[] bytes,RegistryAccess registries){return new RegistryFriendlyByteBuf(Unpooled.wrappedBuffer(bytes),registries);}
    static void run(ServerLevel level,NativeCredentialReaderBlock block,MenuType<NativeCredentialReaderMenu> type)throws Exception {
        String reference=System.getProperty("vandorlabs.standaloneReaderEditorReference");int cases=0;
        if(reference!=null){JsonObject root;try(var input=Files.newBufferedReader(Path.of(reference))){root=JsonParser.parseReader(input).getAsJsonObject();}need(root.get("golden").getAsString().equals("0c02d57ddba0456757e1c22aff3816f4367b2511abb0ad8a86172fb5690cf19f"),"frozen Source editor");
            for(var entry:root.getAsJsonArray("cases")){var row=entry.getAsJsonObject();byte[] raw=java.util.HexFormat.of().parseHex(row.get("wire").getAsString());var buffer=bytes(raw,level.registryAccess());ReaderSettingsPayload decoded=null;try{decoded=ReaderSettingsPayload.CODEC.decode(buffer);}catch(IllegalArgumentException rejected){}finally{buffer.release();}need((decoded!=null)==row.get("valid").getAsBoolean(),"Source handler scalar preflight "+cases);if(decoded!=null){need(decoded.window()==1947&&decoded.behavior()==row.get("mode").getAsInt()&&decoded.any()==row.get("any").getAsBoolean()&&decoded.allowed()==row.get("mask").getAsInt()&&decoded.channels().equals(row.get("channels").getAsString()),"decoded Source fields");int mount=row.get("mount").getAsInt();need(mount<0?decoded.mount()==null:decoded.mount().height==mount%4&&decoded.mount().tilt==mount/4%4&&decoded.mount().direction==mount/16,"optional Source mount");var encoded=new RegistryFriendlyByteBuf(Unpooled.buffer(),level.registryAccess());ReaderSettingsPayload.CODEC.encode(encoded,decoded);byte[] round=new byte[encoded.readableBytes()];encoded.readBytes(round);encoded.release();need(java.util.Arrays.equals(raw,round),"Source/native exact body round trip");}cases++;}
        }
        var player=net.neoforged.neoforge.common.util.FakePlayerFactory.getMinecraft(level);player.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);player.getInventory().clearContent();BlockPos pos=new BlockPos(5170,90,5150);level.getChunkAt(pos);level.setBlock(pos.south(),Blocks.STONE.defaultBlockState(),3);level.setBlock(pos,block.defaultBlockState(),3);var owner=(NativeCredentialReaderEntity)level.getBlockEntity(pos);player.setPos(pos.getX()+.5,pos.getY(),pos.getZ()-1);var originalMenu=player.containerMenu;var menu=new NativeCredentialReaderMenu(type,37,owner);player.containerMenu=menu;
        try{
            need(menu.slots.isEmpty()&&menu.stillValid(player),"survival current reader has no inventory transfer slots");
            var edit=new ReaderSettingsPayload(37,2,false,4,"999",ControlMountSettings.of(3,2,1));need(ReaderSettingsPayload.apply(player,edit)&&owner.behavior()==2&&owner.visual().allowed()==4&&owner.mountSettings().height==3,"survival Source dialog can edit without Configurizer");need(owner.getRedstoneChannels().isEmpty(),"legacy channel text is validation only");
            need(!ReaderSettingsPayload.apply(player,new ReaderSettingsPayload(38,0,true,127,"",null)),"wrong window refused");
            need(ReaderTransmissionPayload.apply(player,new ReaderTransmissionPayload(37,ChannelList.of(41,43),32,false))&&owner.getRedstoneChannels().equals(ChannelList.of(41,43))&&!owner.localRedstone()&&owner.transmissionRange()==32,"independent Source main-slot channel/range/local edit");
            need(ReaderSettingsPayload.apply(player,new ReaderSettingsPayload(37,1,true,127,"999",null))&&owner.getRedstoneChannels().equals(ChannelList.of(41,43))&&owner.mountSettings().height==3,"policy edit retains transmitter bank and absent mount");
            var saved=owner.saveWithoutMetadata(level.registryAccess());need(saved.getCompound("ForgeData").getCompound("VandorTransmission").getCompound("main").getInt("Range")==32,"main-slot settings use original private envelope");
            var dto=owner.getUpdateTag(level.registryAccess());var client=new NativeCredentialReaderEntity(owner.getType(),pos,owner.getBlockState(),false);client.loadWithComponents(dto,level.registryAccess());need(client.behavior()==1&&client.transmittedChannels().equals(ChannelList.of(41,43))&&client.transmissionRange()==32&&!client.localRedstone(),"public editor scalar projection round trip without private envelope");
            player.setPos(pos.getX()+20,pos.getY(),pos.getZ());need(!ReaderSettingsPayload.apply(player,edit),"reach rechecked after opening");player.setPos(pos.getX()+.5,pos.getY(),pos.getZ()-1);
            player.setGameMode(net.minecraft.world.level.GameType.SPECTATOR);need(!menu.stillValid(player),"spectator refused");player.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
            level.removeBlock(pos,false);level.setBlock(pos,block.defaultBlockState(),3);need(!ReaderSettingsPayload.apply(player,edit),"same-position replacement cannot inherit session");
        }finally{player.containerMenu=originalMenu;player.getInventory().clearContent();level.removeBlock(pos,false);}
        System.out.println("Native standalone reader editor PASS SourceCases="+cases+" checks="+checks);
    }
}
