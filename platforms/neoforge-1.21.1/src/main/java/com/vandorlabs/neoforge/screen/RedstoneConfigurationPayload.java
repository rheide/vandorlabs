package com.vandorlabs.neoforge.screen;

import com.vandorlabs.neoforge.ModernFinishCatalog;
import com.vandorlabs.redstone.ChannelList;
import com.vandorlabs.redstone.RedstoneScreenPolicy;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Bounded configuration submitted only by the exact installed screen's row-editor session. */
public record RedstoneConfigurationPayload(int menuId,BlockPos pos,String title,List<Row> rows,int housing) implements CustomPacketPayload {
    public record Row(String label,ChannelList channels,int kind,int min,int max){}
    public static final Type<RedstoneConfigurationPayload> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath("vandorlabs","redstone_screen"));
    public static final StreamCodec<RegistryFriendlyByteBuf,RedstoneConfigurationPayload> CODEC=new StreamCodec<>(){
        public RedstoneConfigurationPayload decode(RegistryFriendlyByteBuf buffer){
            int menu=buffer.readVarInt();BlockPos pos=buffer.readBlockPos();String title=buffer.readUtf(RedstoneScreenPolicy.MAX_TITLE);int count=buffer.readUnsignedByte();
            if(count>RedstoneScreenPolicy.MAX_ROWS)throw new IllegalArgumentException("Too many screen rows");
            List<Row> rows=new ArrayList<>(count);
            for(int i=0;i<count;i++){
                String label=buffer.readUtf(RedstoneScreenPolicy.MAX_LABEL);int members=buffer.readUnsignedByte();if(members>ChannelList.MAX_CHANNELS)throw new IllegalArgumentException("Too many row channels");
                int[] channels=new int[members];for(int c=0;c<members;c++)channels[c]=buffer.readVarInt();
                rows.add(new Row(label,ChannelList.of(channels),buffer.readUnsignedByte(),buffer.readUnsignedByte(),buffer.readUnsignedByte()));
            }
            return new RedstoneConfigurationPayload(menu,pos,title,List.copyOf(rows),buffer.readVarInt());
        }
        public void encode(RegistryFriendlyByteBuf buffer,RedstoneConfigurationPayload value){
            if(value.rows.size()>RedstoneScreenPolicy.MAX_ROWS)throw new IllegalArgumentException("Too many screen rows");
            buffer.writeVarInt(value.menuId);buffer.writeBlockPos(value.pos);buffer.writeUtf(value.title,RedstoneScreenPolicy.MAX_TITLE);buffer.writeByte(value.rows.size());
            for(Row row:value.rows){buffer.writeUtf(row.label,RedstoneScreenPolicy.MAX_LABEL);buffer.writeByte(row.channels.size());for(int channel:row.channels.toArray())buffer.writeVarInt(channel);buffer.writeByte(row.kind);buffer.writeByte(row.min);buffer.writeByte(row.max);}
            buffer.writeVarInt(value.housing);
        }
    };
    @Override public Type<? extends CustomPacketPayload> type(){return TYPE;}
    public static void register(RegisterPayloadHandlersEvent event){event.registrar("1").playToServer(TYPE,CODEC,RedstoneConfigurationPayload::handle);}
    private static void handle(RedstoneConfigurationPayload value,IPayloadContext context){context.enqueueWork(()->{if(context.player() instanceof ServerPlayer player)apply(player,value);});}
    public static boolean apply(ServerPlayer player,RedstoneConfigurationPayload value){
        if(!(player.containerMenu instanceof ScreenMenu menu)||!menu.redstoneEditor||menu.containerId!=value.menuId||!menu.pos.equals(value.pos)||!menu.stillValid(player)||!ModernFinishCatalog.validChoice(value.housing)||!RedstoneScreenPolicy.validTitle(value.title)||value.rows.size()>menu.tile.rowCapacity(menu.slot))return false;
        ListTag rows=new ListTag();
        for(Row row:value.rows){
            if(row.channels==null||row.kind<0||row.kind>2||!RedstoneScreenPolicy.validLabel(row.label)||!RedstoneScreenPolicy.validOptions(row.label,row.kind==1,row.kind==2,row.min,row.max))return false;
            CompoundTag tag=new CompoundTag();tag.putString("Label",row.label);tag.putIntArray("RedstoneChannels",row.channels.toArray());tag.putBoolean("Slider",row.kind==1);tag.putBoolean("Pulse",row.kind==2);tag.putInt("Min",row.min);tag.putInt("Max",row.max);rows.add(tag);
        }
        if(!menu.tile.redstone(menu.slot).applyRowConfiguration(value.title,rows))return false;
        menu.tile.setHousingTexture(value.housing);return true;
    }
}
