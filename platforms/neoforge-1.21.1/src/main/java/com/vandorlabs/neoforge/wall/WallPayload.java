package com.vandorlabs.neoforge.wall;

import com.vandorlabs.neoforge.ModernFinishCatalog;
import com.vandorlabs.persistence.WallAppearance;
import com.vandorlabs.render.TextureLayoutMode;
import com.vandorlabs.tiles.GlassTextures;
import com.vandorlabs.ui.WallDialog;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/** Fixed scalar wall settings; decoding never allocates from a peer-supplied length. */
@EventBusSubscriber(modid="vandorlabs",bus=EventBusSubscriber.Bus.MOD)
public record WallPayload(int menuId,BlockPos pos,WallDialog.State settings,WallDialog.Effect effect) implements CustomPacketPayload {
    public static final Type<WallPayload> TYPE=new Type<>(ResourceLocation.parse("vandorlabs:programmable_wall"));
    public static final StreamCodec<RegistryFriendlyByteBuf,WallPayload> CODEC=new StreamCodec<>(){
        @Override public WallPayload decode(RegistryFriendlyByteBuf b){int id=b.readVarInt();BlockPos pos=b.readBlockPos();WallDialog.Effect effect=switch(b.readUnsignedByte()){case 1->WallDialog.Effect.MAIN;case 2->WallDialog.Effect.SIDE;case 3->WallDialog.Effect.LAYOUT;case 4->WallDialog.Effect.APPEARANCE;default->WallDialog.Effect.NONE;};int main=b.readVarInt(),side=b.readVarInt(),layout=b.readUnsignedByte(),shade=b.readUnsignedByte(),tint=b.readUnsignedByte(),material=b.readVarInt(),shape=b.readUnsignedByte(),glassLayout=b.readByte();boolean framed=b.readBoolean(),stretch=b.readBoolean(),textureJoin=b.readBoolean(),join=b.readBoolean();return new WallPayload(id,pos,new WallDialog.State(main,side,layout,-1,new WallAppearance(shade,tint,material,framed,stretch,textureJoin,join,shape,glassLayout)),effect);}
        @Override public void encode(RegistryFriendlyByteBuf b,WallPayload p){var s=p.settings;var a=s.appearance;b.writeVarInt(p.menuId);b.writeBlockPos(p.pos);b.writeByte(p.effect.ordinal());b.writeVarInt(s.main);b.writeVarInt(s.side);b.writeByte(s.layout);b.writeByte(a.shade);b.writeByte(a.tint);b.writeVarInt(a.material);b.writeByte(a.shape);b.writeByte(a.layout);b.writeBoolean(a.framed);b.writeBoolean(a.stretch);b.writeBoolean(a.textureJoin);b.writeBoolean(a.join);}
    };
    @Override public Type<? extends CustomPacketPayload> type(){return TYPE;}
    @SubscribeEvent public static void register(RegisterPayloadHandlersEvent event){event.registrar("1").playToServer(TYPE,CODEC,(p,context)->context.enqueueWork(()->{if(context.player() instanceof ServerPlayer player)apply(player,p);}));}
    public static boolean apply(ServerPlayer player,WallPayload p){
        if(!(player.containerMenu instanceof WallMenu menu)||menu.containerId!=p.menuId||!menu.pos.equals(p.pos)||!menu.stillValid(player))return false;
        var s=p.settings;var current=menu.tile.wallSettings();var effect=p.effect;
        if(effect==null||effect==WallDialog.Effect.NONE||effect==WallDialog.Effect.CLOSE)return false;
        if(effect==WallDialog.Effect.MAIN&&!ModernFinishCatalog.validChoice(s.main))return false;
        if(effect==WallDialog.Effect.LAYOUT&&!TextureLayoutMode.valid(s.layout))return false;
        if(effect==WallDialog.Effect.SIDE&&(!menu.tile.profile().porthole()||s.side!=-1&&!ModernFinishCatalog.validChoice(s.side)&&!(s.side==-3&&current.side==-3)))return false;
        if(effect==WallDialog.Effect.APPEARANCE){var a=s.appearance;if(!menu.tile.profile().porthole()||a==null||!GlassTextures.valid(a.shade)||!GlassTextures.validTint(a.tint)||!WallAppearance.validShape(a.shape)||a.layout< -1||a.layout>2||!WallAppearance.validMaterial(a.material,ModernFinishCatalog::validChoice))return false;}
        return menu.tile.applyWall(WallDialog.applyEffect(current,s,effect));
    }
}
