package com.vandorlabs.neoforge.door;

import com.vandorlabs.redstone.ChannelList;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Bounded 30+4N-byte Door editor transport (N<=64), applied through the current authenticated menu. */
public record DoorSettingsPayload(BlockPos pos,int window,NativeDoorEdit edit,ChannelList channels) implements CustomPacketPayload {
    public static final Type<DoorSettingsPayload> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath("vandorlabs","door_settings"));
    private static boolean flag(int value){if(value>1)throw new IllegalArgumentException("Invalid door boolean");return value!=0;}
    public static final StreamCodec<RegistryFriendlyByteBuf,DoorSettingsPayload> CODEC=new StreamCodec<>(){
        public DoorSettingsPayload decode(RegistryFriendlyByteBuf b){
            if(b.readableBytes()<30||b.readableBytes()>30+4*ChannelList.MAX_CHANNELS)throw new IllegalArgumentException("Invalid bounded door payload length");
            BlockPos pos=b.readBlockPos();int window=b.readInt(),design=b.readUnsignedByte(),detail=b.readUnsignedByte();boolean framed=flag(b.readUnsignedByte());int direction=b.readUnsignedByte(),depth=b.readUnsignedByte();boolean sliding=flag(b.readUnsignedByte()),hinges=flag(b.readUnsignedByte());int trigger=b.readUnsignedByte();boolean panel=flag(b.readUnsignedByte());int mode=b.readUnsignedByte(),security=b.readUnsignedByte(),allowed=b.readUnsignedByte(),layout=b.readUnsignedByte(),face=b.readInt(),count=b.readUnsignedByte();
            var edit=new NativeDoorEdit(design,detail,framed,direction,depth,sliding,hinges,trigger,panel,mode,security,allowed,face,layout);
            if(window<0||!edit.valid()||count>ChannelList.MAX_CHANNELS||b.readableBytes()!=count*4)throw new IllegalArgumentException("Invalid door settings/channel count");
            for(int i=0;i<count;i++)if(b.getInt(b.readerIndex()+i*4)<0)throw new IllegalArgumentException("Negative door channel");
            int[] values=new int[count];for(int i=0;i<count;i++)values[i]=b.readInt();return new DoorSettingsPayload(pos,window,edit,ChannelList.of(values));
        }
        public void encode(RegistryFriendlyByteBuf b,DoorSettingsPayload p){if(p.pos==null||p.window<0||p.edit==null||!p.edit.valid()||p.channels==null)throw new IllegalArgumentException("Invalid door settings");var e=p.edit;b.writeBlockPos(p.pos);b.writeInt(p.window);b.writeByte(e.design());b.writeByte(e.detail());b.writeBoolean(e.framed());b.writeByte(e.direction());b.writeByte(e.depth());b.writeBoolean(e.sliding());b.writeBoolean(e.hinges());b.writeByte(e.trigger());b.writeBoolean(e.panel());b.writeByte(e.panelMode());b.writeByte(e.securityMode());b.writeByte(e.allowed());b.writeByte(e.layout());b.writeInt(e.faceTexture());b.writeByte(p.channels.size());for(int i=0;i<p.channels.size();i++)b.writeInt(p.channels.get(i));}
    };
    @Override public Type<? extends CustomPacketPayload> type(){return TYPE;}
}
