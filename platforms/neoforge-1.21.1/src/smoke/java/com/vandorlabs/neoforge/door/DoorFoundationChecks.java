package com.vandorlabs.neoforge.door;

import com.google.gson.*;
import com.vandorlabs.redstone.ChannelList;
import java.nio.file.*;
import net.minecraft.core.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStartedEvent;

/** Registered dedicated storage/codec proof; no functional door blocks or client menu are installed. */
@EventBusSubscriber(modid="vandorlabs")
public final class DoorFoundationChecks {
    private static Block block;private static BlockEntityType<NativeProgrammableDoorEntity> type;private static long checks;
    private static boolean enabled(){return System.getProperty("vandorlabs.transmitterPhase","").equals("door-foundation");}
    private static void need(boolean value,String why){checks++;if(!value)throw new AssertionError("Door foundation: "+why);}
    @EventBusSubscriber(modid="vandorlabs",bus=EventBusSubscriber.Bus.MOD)
    public static final class Registration {
        @SubscribeEvent public static void register(net.neoforged.neoforge.registries.RegisterEvent event){if(!enabled())return;event.register(Registries.BLOCK,helper->{block=new Block(BlockBehaviour.Properties.of());helper.register(ResourceLocation.fromNamespaceAndPath("vandorlabs","door_storage_fixture"),block);});event.register(Registries.BLOCK_ENTITY_TYPE,helper->{type=BlockEntityType.Builder.of((pos,state)->new NativeProgrammableDoorEntity(type,pos,state,false,false),block).build(null);helper.register(ResourceLocation.fromNamespaceAndPath("vandorlabs","door_storage_fixture"),type);});}
    }
    private static final class Counting extends NativeProgrammableDoorEntity {
        int dirty,models;boolean observe;
        Counting(boolean large){super(type,BlockPos.ZERO,block.defaultBlockState(),large,false);observe=true;}
        private void complete(){if(observe)need(getModelData().get(SETTINGS)==settings(),"complete immutable model projection before callbacks");}
        @Override public void setChanged(){dirty++;complete();super.setChanged();}
        @Override public void requestModelDataUpdate(){models++;complete();super.requestModelDataUpdate();}
    }
    private static void reject(byte[] bytes,RegistryAccess registries){var b=new net.minecraft.network.RegistryFriendlyByteBuf(io.netty.buffer.Unpooled.wrappedBuffer(bytes),registries);try{boolean rejected=false;try{DoorSettingsPayload.CODEC.decode(b);}catch(IllegalArgumentException failure){rejected=true;}need(rejected,"malformed payload rejected");}finally{b.release();}}
    @SubscribeEvent public static void start(ServerStartedEvent event)throws Exception{if(!enabled())return;var registries=event.getServer().registryAccess();var rows=JsonParser.parseString(Files.readString(Paths.get(System.getProperty("vandorlabs.doorSettingsReference")))).getAsJsonArray();need(rows.size()==512,"complete actual Forge profile corpus");
        for(var value:rows){var row=value.getAsJsonObject();var source=TagParser.parseTag(row.get("input").getAsString());var before=source.copy();var settings=NativeDoorSettings.read(source);need(source.equals(before),"profile read does not mutate source");var actual=new CompoundTag();settings.write(actual);actual.putInt("SpaceDoorChannel",0);actual.putIntArray("RedstoneChannels",new int[0]);var expected=TagParser.parseTag(row.get("choices").getAsString());need(actual.equals(expected),"exact actual Forge user choices input="+source+" expected="+expected+" actual="+actual);
            var opaque=new CompoundTag();opaque.putString("owner","foreign:reader");source.put("ForgeCaps",opaque.copy());source.put("UnknownDoor",opaque.copy());var reader=source.getCompound("DoorKeyReader").copy();reader.put("InsertedToken",opaque.copy());source.put("DoorKeyReader",reader);var host=new Counting(row.get("large").getAsBoolean());host.loadWithComponents(source,registries);var saved=host.saveWithoutMetadata(registries);need(saved.getCompound("ForgeCaps").equals(opaque)&&saved.getCompound("UnknownDoor").equals(opaque)&&saved.getCompound("DoorKeyReader").getCompound("InsertedToken").equals(opaque),"opaque saved owner/capability/reader token retained exactly");var publicTag=host.getUpdateTag(registries);need(!publicTag.contains("ForgeCaps")&&!publicTag.contains("UnknownDoor")&&!publicTag.contains("DoorKeyReader")&&!publicTag.contains("DoorRearKeyReader"),"credentials and unknowns excluded from public projection");var item=host.itemSettings();need(!item.contains("DoorSecurityToggled")&&!item.contains("DoorKeyReader")&&!item.contains("ContainedFluids")&&!item.contains("RedstoneSignal"),"item choices exclude runtime state");need(host.saveWithoutMetadata(registries).getCompound("DoorKeyReader").equals(reader),"projection calls never replace retained reader contents");
        }
        byte[] emptyPacket=null;for(int size:new int[]{0,1,64}){int[] values=new int[size];for(int i=0;i<size;i++)values[i]=i+1;var channels=ChannelList.of(values);var edit=new NativeDoorEdit(2,2,true,0,0,false,true,2,true,2,4,126,-1,2);var packet=new DoorSettingsPayload(new BlockPos(-17,32,47),12,edit,channels);var b=new net.minecraft.network.RegistryFriendlyByteBuf(io.netty.buffer.Unpooled.buffer(),registries);byte[] bytes;try{DoorSettingsPayload.CODEC.encode(b,packet);need(b.readableBytes()==30+4*size,"bounded explicit wire bytes");bytes=new byte[b.readableBytes()];b.getBytes(0,bytes);if(size==0)emptyPacket=bytes;need(DoorSettingsPayload.CODEC.decode(b).equals(packet),"exact full packet roundtrip");}finally{b.release();}for(int offset:new int[]{14,17,18,20}){var malformed=bytes.clone();malformed[offset]=2;reject(malformed,registries);}var malformed=bytes.clone();malformed[29]=65;reject(malformed,registries);reject(java.util.Arrays.copyOf(bytes,bytes.length+1),registries);}
        int[][] bounds={{12,30},{13,3},{15,4},{16,3},{19,3},{21,3},{22,5},{23,128},{24,3}};for(int[] bound:bounds){byte[] malformed=emptyPacket.clone();malformed[bound[0]]=(byte)bound[1];reject(malformed,registries);}
        byte[] negative=emptyPacket.clone();java.util.Arrays.fill(negative,8,12,(byte)255);reject(negative,registries);
        for(int length:new int[]{0,12,29,287,4096})reject(new byte[length],registries);
        var edit=new NativeDoorEdit(23,1,true,3,0,true,true,1,true,2,0,127,-1,0);need(edit.accepts(true)&&!edit.accepts(false),"large-only design and X motion refused by ordinary host");var host=new Counting(false);host.loadWithComponents(new CompoundTag(),registries);var prior=host.settings();need(!host.accepts(edit)&&host.settings()==prior,"invalid ordinary edit keeps exact snapshot");var valid=new NativeDoorEdit(2,1,true,0,1,false,true,0,false,0,4,126,-1,1);need(host.stage(valid,ChannelList.of(7))&&host.dirty==0,"staged choices publish no callbacks");host.publish();need(host.dirty==1&&host.settings().panelMode()==1&&host.settings().securityAllowed()==2,"one publication security enables panel and canonicalizes company");need(!host.stage(valid,ChannelList.of(7)),"no-op stage quiescent");
        var bank=new CompoundTag();bank.putInt("RedstoneChannel",11);bank.putIntArray("RedstoneChannels",new int[]{-1});host.loadWithComponents(bank,registries);need(host.getRedstoneChannels().equals(ChannelList.of(11)),"malformed saved bank keeps legacy scalar fallback");bank.putIntArray("RedstoneChannels",new int[]{9,7,9,0});host.loadWithComponents(bank,registries);need(host.getRedstoneChannels().equals(ChannelList.of(7,9)),"saved bank uses canonical sorted membership");
        var credentialHost=new Counting(false);var credentialSource=new CompoundTag();var rawReader=new CompoundTag();rawReader.putLong("TokenPulseUntil",123);rawReader.putBoolean("TokenToggled",true);var physicalToken=new CompoundTag();physicalToken.putString("id","foreign:card");rawReader.put("InsertedToken",physicalToken);credentialSource.put("DoorKeyReader",rawReader);credentialHost.loadWithComponents(credentialSource,registries);var appearanceOnly=new NativeDoorEdit(2,1,false,0,1,false,true,0,true,1,0,127,-1,0);need(credentialHost.stage(appearanceOnly,ChannelList.EMPTY),"appearance edit staged with opaque reader");var retainedReader=credentialHost.saveWithoutMetadata(registries).getCompound("DoorKeyReader");need(retainedReader.getLong("TokenPulseUntil")==123&&retainedReader.getBoolean("TokenToggled")&&retainedReader.getCompound("InsertedToken").equals(physicalToken),"appearance-only edit does not reset or consume default-typed reader runtime state");
        need(NativeDoorSettings.ORDINARY_ID.equals("programmable_door")&&NativeDoorSettings.LARGE_ID.equals("large_programmable_door"),"canonical previous-release block/entity IDs");
        System.out.println("[vandorlabs][door-foundation] dedicated PASS profiles="+rows.size()+" checks="+checks+" exact Forge choices, opaque save/public projection, bounded codec; linked gameplay/menu/player path deferred");event.getServer().halt(false);
    }
}
