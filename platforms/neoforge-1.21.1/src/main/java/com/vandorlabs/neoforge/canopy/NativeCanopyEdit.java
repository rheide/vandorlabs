package com.vandorlabs.neoforge.canopy;

import com.vandorlabs.neoforge.ModernFinishCatalog;
import com.vandorlabs.persistence.CanopySettingsData;
import com.vandorlabs.redstone.ChannelList;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;

/** Bounded native settings-only request. No arbitrary NBT or physical ownership fields. */
public record NativeCanopyEdit(BlockPos pos,int window,int request,NativeCanopySettings settings) {
    public static final int BASE_BYTES=55,MAX_BYTES=BASE_BYTES+4*ChannelList.MAX_CHANNELS;
    public NativeCanopyEdit {
        java.util.Objects.requireNonNull(pos);java.util.Objects.requireNonNull(settings);pos=pos.immutable();
        if(window<0||request<0||!valid(settings.data()))throw new IllegalArgumentException("Invalid Canopy edit");
    }
    public static boolean valid(CanopySettingsData d){return d.valid()&&d.mount>=0&&d.mount<=3&&d.motion>=0&&d.motion<=1
        &&d.hinge>=0&&d.hinge<=3&&d.angle>=0&&d.angle<=3&&d.textureLayout()>=0&&d.textureLayout()<=2
        &&(d.material==-1||ModernFinishCatalog.validChoice(d.material))&&d.channels.size()<=ChannelList.MAX_CHANNELS;}
    public void encode(RegistryFriendlyByteBuf b){
        b.writeBlockPos(pos);b.writeInt(window);b.writeInt(request);var d=settings.data();
        b.writeInt(d.depth);b.writeInt(d.scale);b.writeInt(d.shade);b.writeInt(d.tint);b.writeInt(d.mount);
        b.writeInt(d.motion);b.writeInt(d.hinge);b.writeInt(d.angle);b.writeInt(d.material);
        b.writeByte((d.openable?1:0)|(d.redstone?2:0)|(d.slideForward?4:0)|(d.tileMaterial?8:0)|(d.activateOnPower?16:0));
        b.writeByte(d.textureLayout());b.writeByte(d.channels.size());for(int i=0;i<d.channels.size();i++)b.writeInt(d.channels.get(i));
    }
    public static NativeCanopyEdit decode(RegistryFriendlyByteBuf b){
        if(b.readableBytes()<BASE_BYTES||b.readableBytes()>MAX_BYTES)throw new IllegalArgumentException("Canopy edit length");
        var pos=b.readBlockPos();int window=b.readInt(),request=b.readInt();
        int depth=b.readInt(),scale=b.readInt(),shade=b.readInt(),tint=b.readInt(),mount=b.readInt(),motion=b.readInt(),hinge=b.readInt(),angle=b.readInt(),material=b.readInt();
        int flags=b.readUnsignedByte(),layout=b.readUnsignedByte(),count=b.readUnsignedByte();
        if(flags>31||layout>2||count>ChannelList.MAX_CHANNELS||b.readableBytes()!=4*count)throw new IllegalArgumentException("Canopy edit header");
        // Validate the whole bounded bank before allocating; native wire is canonical strictly ascending positive IDs.
        int previous=0;for(int i=0;i<count;i++){int value=b.getInt(b.readerIndex()+4*i);if(value<=previous)throw new IllegalArgumentException("Canopy channels");previous=value;}
        int[] channels=new int[count];for(int i=0;i<count;i++)channels[i]=b.readInt();
        return new NativeCanopyEdit(pos,window,request,new NativeCanopySettings(new CanopySettingsData(depth,scale,shade,tint,mount,motion,hinge,angle,material,
            (flags&1)!=0,(flags&2)!=0,(flags&4)!=0,(flags&8)!=0,(flags&16)!=0,layout,ChannelList.of(channels))));
    }
}
