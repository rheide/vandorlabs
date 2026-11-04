package com.vandorlabs.segmented;

import com.vandorlabs.tiles.DoorGlassAppearance;
import net.minecraft.nbt.NBTTagCompound;
import io.netty.buffer.ByteBuf;

/** Optional settings preserve the old panel path and absent-field save format. */
public final class SegmentedDoorSettings {
    public static final int UP=0,DOWN=1,LEFT=2,RIGHT=3;
    public static final int PACKET_BYTES=15;
    public static final SegmentedDoorSettings DEFAULT=new SegmentedDoorSettings(0,false,1,0,false,0,false,new DoorGlassAppearance(-1,0,1,1));
    public static final SegmentedDoorSettings LEGACY=DEFAULT.activation(-1);
    public final int direction,layout,activation,shape,position;
    public final boolean straight,window,join;
    public final DoorGlassAppearance glass;
    public SegmentedDoorSettings(int direction,boolean straight,int layout,int activation,boolean window,int shape,boolean join,DoorGlassAppearance glass){
        this(direction,straight,layout,activation,window,shape,join,glass,0);
    }
    public SegmentedDoorSettings(int direction,boolean straight,int layout,int activation,boolean window,int shape,boolean join,DoorGlassAppearance glass,int position){
        if(position<0||position>2)throw new IllegalArgumentException("Invalid Segmented Door position");
        if(direction<0||direction>3||layout<0||layout>2||activation< -1||activation>2||shape<0||shape>3||glass==null||glass.material==DoorGlassAppearance.ORIGINAL)throw new IllegalArgumentException("Invalid Segmented Door settings");
        this.position=position;this.direction=direction;this.straight=straight;this.layout=layout;this.activation=activation;this.window=window;this.shape=shape;this.join=join;this.glass=glass;
    }
    public SegmentedDoorSettings activation(int value){return new SegmentedDoorSettings(direction,straight,layout,value,window,shape,join,glass,position);}
    public SegmentedDoorSettings shared(SegmentedDoorSettings shared){if(direction==shared.direction&&straight==shared.straight&&layout==shared.layout&&activation==shared.activation&&position==shared.position)return this;return new SegmentedDoorSettings(shared.direction,shared.straight,shared.layout,shared.activation,window,shape,join,glass,shared.position);}
    public double depthOffset(){return position==1?-13D/32:position==2?13D/32:0;}
    public boolean nativePanel(){return !window&&layout==1;}
    public void write(NBTTagCompound root){
        if(equals(LEGACY)){root.removeTag("SegmentedSettings");for(String field:new String[]{"SegmentedPosition","SegmentedSlide","SegmentedStraight","SegmentedLayout","SegmentedWindow","SegmentedShape","SegmentedJoin","SegmentedGlassMaterial","SegmentedGlassShade","SegmentedGlassTint","SegmentedGlassLayout"})root.removeTag(field);return;}
        if(position!=0)root.setInteger("SegmentedPosition",position);else root.removeTag("SegmentedPosition");root.setInteger("SegmentedSlide",direction);root.setBoolean("SegmentedStraight",straight);root.setInteger("SegmentedLayout",layout);root.setBoolean("SegmentedWindow",window);root.setInteger("SegmentedShape",shape);root.setBoolean("SegmentedJoin",join);root.setInteger("SegmentedGlassMaterial",glass.material);root.setInteger("SegmentedGlassShade",glass.shade);root.setInteger("SegmentedGlassTint",glass.tint);root.setInteger("SegmentedGlassLayout",glass.layout);
        NBTTagCompound n=new NBTTagCompound();if(position!=0)n.setInteger("Position",position);n.setInteger("Direction",direction);n.setBoolean("Straight",straight);n.setInteger("Layout",layout);n.setInteger("Activation",activation);n.setBoolean("Window",window);n.setInteger("Shape",shape);n.setBoolean("Join",join);glass.write(n);root.setTag("SegmentedSettings",n);
    }
    public static SegmentedDoorSettings read(NBTTagCompound root){
        if(!root.hasKey("SegmentedSettings",10))return LEGACY;NBTTagCompound n=root.getCompoundTag("SegmentedSettings");DoorGlassAppearance g=DoorGlassAppearance.read(n);if(g.material==DoorGlassAppearance.ORIGINAL)g=DEFAULT.glass;
        try{return new SegmentedDoorSettings(n.getInteger("Direction"),n.getBoolean("Straight"),n.getInteger("Layout"),n.getInteger("Activation"),n.getBoolean("Window"),n.getInteger("Shape"),n.getBoolean("Join"),g,n.getInteger("Position"));}catch(IllegalArgumentException malformed){return LEGACY;}
    }
    public void encode(ByteBuf b){b.writeByte(direction);b.writeBoolean(straight);b.writeByte(layout);b.writeByte(activation+1);b.writeBoolean(window);b.writeByte(shape);b.writeBoolean(join);b.writeInt(glass.material);b.writeByte(glass.shade);b.writeByte(glass.tint);b.writeByte(glass.layout);b.writeByte(position);}
    public static SegmentedDoorSettings decode(ByteBuf b){if(b.readableBytes()!=PACKET_BYTES&&b.readableBytes()!=PACKET_BYTES-1)throw new IllegalArgumentException("Invalid Segmented Door settings length");int d=b.readUnsignedByte();boolean s=bool(b);int l=b.readUnsignedByte(),a=b.readUnsignedByte()-1;boolean w=bool(b);int shape=b.readUnsignedByte();boolean j=bool(b);DoorGlassAppearance g=new DoorGlassAppearance(b.readInt(),b.readUnsignedByte(),b.readUnsignedByte(),b.readUnsignedByte());return new SegmentedDoorSettings(d,s,l,a,w,shape,j,g,b.isReadable()?b.readUnsignedByte():0);}
    private static boolean bool(ByteBuf b){int v=b.readUnsignedByte();if(v>1)throw new IllegalArgumentException("Invalid Segmented Door flag");return v==1;}
    @Override public int hashCode(){int h=direction;h=h*31+(straight?1:0);h=h*31+layout;h=h*31+activation;h=h*31+(window?1:0);h=h*31+shape;h=h*31+(join?1:0);return (h*31+glass.hashCode())*31+position;}
    @Override public boolean equals(Object other){if(!(other instanceof SegmentedDoorSettings))return false;SegmentedDoorSettings s=(SegmentedDoorSettings)other;return position==s.position&&direction==s.direction&&straight==s.straight&&layout==s.layout&&activation==s.activation&&window==s.window&&shape==s.shape&&join==s.join&&glass.equals(s.glass);}
}
