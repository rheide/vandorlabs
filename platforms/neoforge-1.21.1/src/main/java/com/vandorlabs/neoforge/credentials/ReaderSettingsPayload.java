package com.vandorlabs.neoforge.credentials;

import com.vandorlabs.persistence.ControlMountSettings;
import com.vandorlabs.redstone.ChannelList;
import java.nio.charset.StandardCharsets;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/** Source MessageTokenSlot body, bounded before allocation and applied only to the current exact owner. */
public record ReaderSettingsPayload(int window,int behavior,boolean any,int allowed,String channels,ControlMountSettings mount) implements CustomPacketPayload {
    public ReaderSettingsPayload{if(behavior<0||behavior>3||!validMask(allowed)||channels==null||channels.length()>256||channels.getBytes(StandardCharsets.UTF_8).length>1024||ChannelList.parse(channels)==null||mount!=null&&!ControlMountSettings.valid(mount.height,mount.tilt,mount.direction))throw new IllegalArgumentException("Invalid reader edit");}
    private static boolean validMask(int mask){return mask==0||mask==127||mask>=2&&mask<=64&&Integer.bitCount(mask)==1;}
    public static final Type<ReaderSettingsPayload> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath("vandorlabs","reader_settings"));
    public static final StreamCodec<RegistryFriendlyByteBuf,ReaderSettingsPayload> CODEC=new StreamCodec<>(){
        public ReaderSettingsPayload decode(RegistryFriendlyByteBuf buffer){
            if(buffer.readableBytes()<8)throw new IllegalArgumentException("Truncated reader edit");int window=buffer.readInt(),behavior=buffer.readUnsignedByte();boolean any=buffer.readBoolean();int allowed=buffer.readUnsignedByte(),first=buffer.readUnsignedByte(),bytes=first&127;
            if((first&128)!=0){if(!buffer.isReadable())throw new IllegalArgumentException("Truncated reader text length");int second=buffer.readUnsignedByte();if((second&128)!=0)throw new IllegalArgumentException("Reader text length exceeds two bytes");bytes|=second<<7;}
            if(bytes>1024||bytes>buffer.readableBytes())throw new IllegalArgumentException("Reader channel text too long");String text=buffer.toString(buffer.readerIndex(),bytes,StandardCharsets.UTF_8);buffer.skipBytes(bytes);ControlMountSettings mount=null;
            if(buffer.isReadable()){if(buffer.readableBytes()!=3)throw new IllegalArgumentException("Invalid reader mount extension");int height=buffer.readUnsignedByte(),tilt=buffer.readUnsignedByte(),direction=buffer.readUnsignedByte();if(!ControlMountSettings.valid(height,tilt,direction))throw new IllegalArgumentException("Invalid reader mount");mount=ControlMountSettings.of(height,tilt,direction);}
            return new ReaderSettingsPayload(window,behavior,any,allowed,text,mount);
        }
        public void encode(RegistryFriendlyByteBuf buffer,ReaderSettingsPayload edit){buffer.writeInt(edit.window);buffer.writeByte(edit.behavior);buffer.writeBoolean(edit.any);buffer.writeByte(edit.allowed);byte[] text=edit.channels.getBytes(StandardCharsets.UTF_8);buffer.writeVarInt(text.length);buffer.writeBytes(text);if(edit.mount!=null){buffer.writeByte(edit.mount.height);buffer.writeByte(edit.mount.tilt);buffer.writeByte(edit.mount.direction);}}
    };
    @Override public Type<? extends CustomPacketPayload> type(){return TYPE;}
    public static boolean apply(ServerPlayer player,ReaderSettingsPayload edit){var menu=NativeCredentialReaderMenu.editing(player,edit.window);return menu!=null&&menu.tile.configure(player,edit.any,edit.allowed,edit.behavior,edit.mount==null?menu.tile.mountSettings():edit.mount);}
    public static void register(RegisterPayloadHandlersEvent event){event.registrar("1").playToServer(TYPE,CODEC,(edit,context)->context.enqueueWork(()->{if(context.player() instanceof ServerPlayer player)apply(player,edit);}));}
}
