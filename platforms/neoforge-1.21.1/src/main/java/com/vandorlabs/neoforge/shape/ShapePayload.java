package com.vandorlabs.neoforge.shape;

import com.vandorlabs.neoforge.ModernFinishCatalog;
import com.vandorlabs.persistence.StairState;
import com.vandorlabs.render.TextureLayoutMode;
import com.vandorlabs.ui.SlabStairDialog;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/** Fixed six-face payload: no peer-controlled allocation or world lookup outside the server task. */
@EventBusSubscriber(modid="vandorlabs",bus=EventBusSubscriber.Bus.MOD)
public record ShapePayload(int menuId,BlockPos pos,SlabStairDialog.State settings) implements CustomPacketPayload {
    public static final Type<ShapePayload> TYPE=new Type<>(ResourceLocation.parse("vandorlabs:programmable_shape"));
    public static final StreamCodec<RegistryFriendlyByteBuf,ShapePayload> CODEC=new StreamCodec<>(){
        public ShapePayload decode(RegistryFriendlyByteBuf b){int id=b.readVarInt();BlockPos pos=b.readBlockPos();int main=b.readVarInt(),side=b.readVarInt(),layout=b.readUnsignedByte();boolean enabled=b.readBoolean();int[] faces=new int[6];for(int i=0;i<6;i++)faces[i]=b.readVarInt();return new ShapePayload(id,pos,new SlabStairDialog.State(main,side,layout,enabled,faces,-1,new StairState(b.readUnsignedByte(),b.readUnsignedByte())));}
        public void encode(RegistryFriendlyByteBuf b,ShapePayload p){var s=p.settings;b.writeVarInt(p.menuId);b.writeBlockPos(p.pos);b.writeVarInt(s.main);b.writeVarInt(s.side);b.writeByte(s.layout);b.writeBoolean(s.facesEnabled);for(int face:s.faces())b.writeVarInt(face);b.writeByte(s.stairs.tread);b.writeByte(s.stairs.roll);}
    };
    @Override public Type<? extends CustomPacketPayload> type(){return TYPE;}
    @SubscribeEvent public static void register(RegisterPayloadHandlersEvent event){event.registrar("1").playToServer(TYPE,CODEC,(p,context)->context.enqueueWork(()->{if(context.player() instanceof ServerPlayer player)apply(player,p);}));}
    public static boolean apply(ServerPlayer player,ShapePayload p){
        if(!(player.containerMenu instanceof ShapeMenu menu)||menu.containerId!=p.menuId||!menu.pos.equals(p.pos)||!menu.stillValid(player))return false;
        var s=p.settings;if(!ModernFinishCatalog.validChoice(s.main)||!TextureLayoutMode.valid(s.layout)||!StairState.valid(s.stairs.tread,s.stairs.roll))return false;
        if(s.side!=-1&&!ModernFinishCatalog.validChoice(s.side)&&!(s.side==-3&&menu.tile.sideTexture()==-3))return false;
        for(int face:s.faces())if(face!=-1&&!ModernFinishCatalog.validChoice(face))return false;
        return menu.tile.applyShape(s);
    }
}
