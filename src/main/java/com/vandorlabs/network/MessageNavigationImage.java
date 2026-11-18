package com.vandorlabs.network;

import com.vandorlabs.navigation.*;
import io.netty.buffer.ByteBuf;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.fml.common.network.simpleimpl.*;
import java.util.*;

/** Bounded raster/acknowledgement. Palette coding avoids repeating four bytes per biome sample. */
public final class MessageNavigationImage implements IMessage {
    public static final int HEADER=53;
    public BlockPos pos;public int slot,serial,x,z,zoom,aspect,chipX,chipZ,width=96,height=96,canvasWidth=384,canvasHeight=384,revision;
    public NavigationOverlay overlay=NavigationOverlay.EMPTY;
    public boolean noSignal,hasChip,marker,write,unlinked;public int[] pixels;
    public MessageNavigationImage(){}
    public MessageNavigationImage(BlockPos pos,int slot,int serial,int x,int z,int zoom,int aspect,boolean noSignal,ItemStack chip,boolean table,int dimension,int[] pixels){this.pos=pos;this.slot=slot;this.serial=serial;this.x=x;this.z=z;this.zoom=zoom;this.aspect=aspect;this.noSignal=noSignal;this.pixels=pixels;hasChip=table&&!chip.isEmpty();write=table;marker=NavigationChip.inDimension(chip,dimension);if(marker){chipX=NavigationChip.value(chip,"X");chipZ=NavigationChip.value(chip,"Z");}canvasHeight=Math.max(96,Math.min(65536,(int)Math.round(384*16.0/aspect)));}
    public MessageNavigationImage dimensions(NavigationCanvas canvas,int id){return dimensions(canvas,canvas.gridWidth(zoom),canvas.gridHeight(zoom),id);}
    public MessageNavigationImage dimensions(NavigationCanvas canvas,int columns,int rows,int id){canvasWidth=canvas.width;canvasHeight=canvas.height;width=columns;height=rows;revision=id;return this;}
    public MessageNavigationImage linkState(boolean unlinked){this.unlinked=unlinked;return this;}
    @Override public void toBytes(ByteBuf b){
        b.writeLong(pos.toLong());b.writeByte(slot);b.writeInt(serial);b.writeInt(x);b.writeInt(z);b.writeByte(zoom);b.writeByte(aspect);b.writeBoolean(noSignal);b.writeBoolean(hasChip);b.writeBoolean(marker);b.writeBoolean(write);b.writeBoolean(unlinked);b.writeInt(chipX);b.writeInt(chipZ);b.writeShort(width);b.writeShort(height);b.writeInt(canvasWidth);b.writeInt(canvasHeight);b.writeInt(revision);
        if(pixels==null){b.writeByte(0);NavigationOverlayCodec.write(b,overlay);return;}if(pixels.length!=width*height)throw new IllegalArgumentException("Invalid navigation raster length");
        // Primitive palette lookup avoids two Integer allocations per sample on large views.
        int[] keys=new int[512],colors=new int[256];short[] slots=new short[512];byte[] indices=new byte[pixels.length];int size=0;boolean raw=false;
        for(int i=0;i<pixels.length;i++){int color=pixels[i],mixed=color*0x9e3779b9,at=(mixed^(mixed>>>16))&511;while(slots[at]!=0&&keys[at]!=color)at=(at+1)&511;if(slots[at]==0){if(size==256){raw=true;break;}keys[at]=color;colors[size]=color;slots[at]=(short)(++size);}indices[i]=(byte)(slots[at]-1);}
        if(raw){b.writeByte(2);for(int color:pixels)b.writeInt(color);}else{b.writeByte(1);b.writeShort(size);for(int i=0;i<size;i++)b.writeInt(colors[i]);b.writeBytes(indices);}
        NavigationOverlayCodec.write(b,overlay);

    }
    @Override public void fromBytes(ByteBuf b){
        if(b.readableBytes()<HEADER)throw new IllegalArgumentException("Truncated navigation raster");
        pos=BlockPos.fromLong(b.readLong());slot=b.readUnsignedByte();serial=b.readInt();x=b.readInt();z=b.readInt();zoom=b.readUnsignedByte();aspect=b.readUnsignedByte();noSignal=b.readBoolean();hasChip=b.readBoolean();marker=b.readBoolean();write=b.readBoolean();unlinked=b.readBoolean();chipX=b.readInt();chipZ=b.readInt();width=b.readUnsignedShort();height=b.readUnsignedShort();canvasWidth=b.readInt();canvasHeight=b.readInt();revision=b.readInt();int mode=b.readUnsignedByte();
        if(slot>1||zoom>=NavigationMap.SPANS.length||aspect<8||aspect>64||Math.abs((long)x)>NavigationMap.LIMIT||Math.abs((long)z)>NavigationMap.LIMIT||width<1||height<1||width>NavigationCanvas.MAX_GRID||height>NavigationCanvas.MAX_GRID||canvasWidth<96||canvasWidth>65536||canvasHeight<96||canvasHeight>65536||revision<0)throw new IllegalArgumentException("Invalid navigation bounds");
        int count=width*height;
        if(mode==0){pixels=null;overlay=NavigationOverlayCodec.read(b);return;}
        if(mode==2){if(b.readableBytes()<count*4)throw new IllegalArgumentException("Invalid navigation raw size");pixels=new int[count];for(int i=0;i<count;i++)pixels[i]=b.readInt();overlay=NavigationOverlayCodec.read(b);return;}
        if(mode!=1||b.readableBytes()<2)throw new IllegalArgumentException("Invalid navigation encoding");int size=b.readUnsignedShort();if(size<1||size>256||b.readableBytes()<size*4+count)throw new IllegalArgumentException("Invalid navigation palette size");int[] palette=new int[size];for(int i=0;i<size;i++)palette[i]=b.readInt();pixels=new int[count];for(int i=0;i<count;i++){int at=b.readUnsignedByte();if(at>=size)throw new IllegalArgumentException("Invalid navigation palette index");pixels[i]=palette[at];}overlay=NavigationOverlayCodec.read(b);
    }
    public static final class Handler implements IMessageHandler<MessageNavigationImage,IMessage>{public IMessage onMessage(MessageNavigationImage m,MessageContext c){com.vandorlabs.VandorLabs.proxy.navigationImage(m);return null;}}
}
