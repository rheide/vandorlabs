package com.vandorlabs.neoforge;

import com.google.gson.*;
import com.vandorlabs.neoforge.credentials.*;
import com.vandorlabs.persistence.ControlMountSettings;
import com.vandorlabs.redstone.ChannelList;
import io.netty.buffer.Unpooled;
import java.nio.file.*;
import net.minecraft.core.*;
import net.minecraft.nbt.CompoundTag;
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
            if(root.has("transmissions"))transmissionWire(root,level);
            if(root.has("loads"))savedChannels(root,level,block);
        }
        malformed(level);
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
    private static void transmissionWire(JsonObject root,ServerLevel level){
        need(root.get("transmissionGolden").getAsString().equals("38c0a1bc782188537156caefadd086eb486299ff4dc24a1c9e6f36415505484d"),"Source transmitter wire golden");long sourceBytes=0,nativeBytes=0;int cases=0;
        for(var entry:root.getAsJsonArray("transmissions")){var row=entry.getAsJsonObject();var edit=new ReaderTransmissionPayload(1947,ChannelList.parse(row.get("channels").getAsString()),row.get("range").getAsInt(),row.get("local").getAsBoolean());var buffer=new RegistryFriendlyByteBuf(Unpooled.buffer(),level.registryAccess());try{ReaderTransmissionPayload.CODEC.encode(buffer,edit);need(buffer.readableBytes()==8+4*edit.channels().size(),"bounded native transmission body");sourceBytes+=row.get("bytes").getAsInt();nativeBytes+=buffer.readableBytes();var decoded=ReaderTransmissionPayload.CODEC.decode(buffer);need(decoded.equals(edit)&&!buffer.isReadable(),"transmission round trip");}finally{buffer.release();}cases++;}
        need(nativeBytes<sourceBytes,"measured Source/native transmission byte reduction");System.out.println("Native reader transmission wire PASS cases="+cases+" SourceBytes="+sourceBytes+" nativeBytes="+nativeBytes+" messages="+cases+"/"+cases+" bodies exclude transport headers");
    }
    private static void savedChannels(JsonObject root,ServerLevel level,NativeCredentialReaderBlock block){int index=0;for(var entry:root.getAsJsonArray("loads")){var row=entry.getAsJsonObject();CompoundTag raw;try{raw=net.minecraft.nbt.TagParser.parseTag(row.get("input").getAsString());}catch(com.mojang.brigadier.exceptions.CommandSyntaxException malformed){throw new AssertionError(malformed);}var owner=(NativeCredentialReaderEntity)block.newBlockEntity(BlockPos.ZERO,block.defaultBlockState());owner.loadWithComponents(raw,level.registryAccess());boolean safe=row.get("ownershipSafe").getAsBoolean();need(owner.inert()!=safe,"unknown development ownership inert "+index);if(safe){need(owner.getRedstoneChannels().toString().equals(row.get("channels").getAsString()),"Source saved scalar/list fallback "+index+" native="+owner.getRedstoneChannels()+" source="+row.get("channels"));need(owner.transmittedChannels().toString().equals(row.get("transmission").getAsString())&&owner.transmissionRange()==row.get("range").getAsInt()&&owner.localRedstone()==row.get("local").getAsBoolean(),"Source development transmitter normalization "+index);}else need(owner.saveWithoutMetadata(level.registryAccess()).equals(raw),"unsupported development ownership retains original "+index);index++;}System.out.println("Native reader saved channel/module policy PASS SourceCases="+index);}
    private static void malformed(ServerLevel level){
        var full=new RegistryFriendlyByteBuf(Unpooled.buffer(),level.registryAccess());ReaderSettingsPayload.CODEC.encode(full,new ReaderSettingsPayload(37,0,false,127,"",ControlMountSettings.of(3,3,3)));byte[] original=new byte[full.readableBytes()];full.readBytes(original);full.release();
        for(int length=0;length<original.length;length++){byte[] input=java.util.Arrays.copyOf(original,length);need(decodes(input,level)==(length==8),"truncation rejects except the exact Source optional-extension boundary "+length);}
        for(int tail:new int[]{1,2,4,64})need(!decodes(java.util.Arrays.copyOf(original,original.length+tail),level),"trailing bytes refused");
        for(int at=8;at<11;at++){byte[] input=original.clone();input[at]=4;need(!decodes(input,level),"out-of-range mount byte refused");}
        for(int size:new int[]{257,1024,1025}){var buffer=new RegistryFriendlyByteBuf(Unpooled.buffer(),level.registryAccess());buffer.writeInt(37);buffer.writeByte(0);buffer.writeBoolean(false);buffer.writeByte(127);buffer.writeVarInt(size);buffer.writeBytes(new byte[size]);byte[] input=new byte[buffer.readableBytes()];buffer.readBytes(input);buffer.release();need(!decodes(input,level),"channel text bounded before installation "+size);}
    }
    private static boolean decodes(byte[] input,ServerLevel level){var buffer=bytes(input,level.registryAccess());try{ReaderSettingsPayload.CODEC.decode(buffer);return true;}catch(RuntimeException rejected){return false;}finally{buffer.release();}}
}
