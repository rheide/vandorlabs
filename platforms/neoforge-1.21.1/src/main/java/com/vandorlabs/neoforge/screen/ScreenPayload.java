package com.vandorlabs.neoforge.screen;

import com.vandorlabs.neoforge.ModernFinishCatalog;
import com.vandorlabs.redstone.ChannelList;
import com.vandorlabs.ui.ScreenSelectorDialog;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record ScreenPayload(int menuId,BlockPos pos,String artwork,int mode,int speed,int trigger,int sides,boolean framed,String channels,int housing,int surface) implements CustomPacketPayload {
    public static final Type<ScreenPayload> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath("vandorlabs","screen_settings"));
    public static final StreamCodec<RegistryFriendlyByteBuf,ScreenPayload> CODEC=new StreamCodec<>(){
        public ScreenPayload decode(RegistryFriendlyByteBuf b){return new ScreenPayload(b.readVarInt(),b.readBlockPos(),b.readUtf(128),b.readUnsignedByte(),b.readUnsignedByte(),b.readUnsignedByte(),b.readUnsignedByte(),b.readBoolean(),b.readUtf(ChannelList.MAX_TEXT_LENGTH),b.readVarInt(),b.readVarInt());}
        public void encode(RegistryFriendlyByteBuf b,ScreenPayload p){b.writeVarInt(p.menuId);b.writeBlockPos(p.pos);b.writeUtf(p.artwork,128);b.writeByte(p.mode);b.writeByte(p.speed);b.writeByte(p.trigger);b.writeByte(p.sides);b.writeBoolean(p.framed);b.writeUtf(p.channels,ChannelList.MAX_TEXT_LENGTH);b.writeVarInt(p.housing);b.writeVarInt(p.surface);}
    };
    @Override public Type<? extends CustomPacketPayload> type(){return TYPE;}
    public static void register(RegisterPayloadHandlersEvent event){event.registrar("1").playToServer(TYPE,CODEC,ScreenPayload::handle);}
    private static void handle(ScreenPayload p,IPayloadContext context){context.enqueueWork(()->{if(context.player() instanceof ServerPlayer player)apply(player,p);});}
    public static boolean apply(ServerPlayer player,ScreenPayload p){
        if(!player.isAlive()||!(player.containerMenu instanceof ScreenMenu menu)||menu.containerId!=p.menuId||!menu.pos.equals(p.pos)||!menu.stillValid(player))return false;
        ChannelList channels=ChannelList.parse(p.channels);
        if(channels==null||p.mode<0||p.mode>2||p.speed<0||p.speed>2||p.trigger<0||p.trigger>2||p.sides<0||p.sides>2||!ModernFinishCatalog.validChoice(p.housing)||p.surface<com.vandorlabs.redstone.RedstoneScreenPolicy.REDSTONE_SURFACE||p.surface>=0&&!ModernFinishCatalog.validChoice(p.surface)||!ScreenArtwork.valid(p.artwork))return false;
        if(player.serverLevel().getBlockEntity(p.pos)!=menu.tile)return false;
        menu.tile.configure(p.artwork,new ScreenSelectorDialog.State(false,0,p.mode,p.speed,p.trigger,p.sides,p.framed,true),channels,p.housing,p.surface);
        if(p.surface==com.vandorlabs.redstone.RedstoneScreenPolicy.REDSTONE_SURFACE)player.openMenu(new net.minecraft.world.SimpleMenuProvider((id,inventory,p1)->new ScreenMenu(id,inventory,p.pos,true),net.minecraft.network.chat.Component.literal("Redstone controls")),buffer->{buffer.writeBlockPos(p.pos);buffer.writeBoolean(true);});
        return true;
    }
}
