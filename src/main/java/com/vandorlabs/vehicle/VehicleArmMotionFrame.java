package com.vandorlabs.vehicle;

import io.netty.buffer.ByteBuf;
import java.util.*;
import net.minecraft.util.math.BlockPos;

/** Optional append-only STATE tail. Scalars only: no snapshot NBT, tile, world or inventory references. */
public final class VehicleArmMotionFrame {
    public static final int MAGIC=0x41524D31,VERSION=1,MAX_ARMS=64,HEADER_BYTES=15,ENTRY_BYTES=25;
    public static final VehicleArmMotionFrame NONE=new VehicleArmMotionFrame();
    public static final class Arm {
        public final BlockPos root;public final long revision;public final double progress;public final boolean target;
        public Arm(BlockPos root,long revision,double progress,boolean target){if(root==null||!root(root.toLong())||revision<0||!Double.isFinite(progress)||progress<0||progress>1)throw new IllegalArgumentException("Invalid carried arm phase");this.root=root.toImmutable();this.revision=revision;this.progress=progress;this.target=target;}
    }
    public final long layout;public final List<Arm> arms;
    private VehicleArmMotionFrame(){layout=0;arms=Collections.emptyList();}
    public VehicleArmMotionFrame(long layout,List<Arm> arms){
        if(layout==0||arms==null||arms.isEmpty()||arms.size()>MAX_ARMS)throw new IllegalArgumentException("Invalid carried arm layout/count");
        for(int i=0;i<arms.size();i++){if(arms.get(i)==null)throw new IllegalArgumentException("Missing carried arm phase");for(int j=0;j<i;j++)if(arms.get(i).root.equals(arms.get(j).root))throw new IllegalArgumentException("Duplicate carried arm root");}
        this.layout=layout;this.arms=Collections.unmodifiableList(new ArrayList<>(arms));
    }
    public int encodedBytes(){return arms.isEmpty()?0:HEADER_BYTES+ENTRY_BYTES*arms.size();}
    void write(ByteBuf out){if(arms.isEmpty())return;out.writeInt(MAGIC);out.writeByte(VERSION);out.writeLong(layout);out.writeShort(arms.size());for(Arm arm:arms){out.writeLong(arm.root.toLong());out.writeLong(arm.revision);out.writeDouble(arm.progress);out.writeByte(arm.target?1:0);}}
    /** Magic already consumed. Validate the entire bounded scalar payload before constructing any records. */
    static VehicleArmMotionFrame read(ByteBuf in){
        if(in.readableBytes()<HEADER_BYTES-4||in.readUnsignedByte()!=VERSION)throw new IllegalArgumentException("Unsupported arm phase tail");long layout=in.readLong();int count=in.readUnsignedShort();if(layout==0||count<1||count>MAX_ARMS||in.readableBytes()!=count*ENTRY_BYTES)throw new IllegalArgumentException("Invalid arm phase tail length");
        int start=in.readerIndex();for(int i=0;i<count;i++){int at=start+i*ENTRY_BYTES;long root=in.getLong(at);double progress=in.getDouble(at+16);if(!root(root)||in.getLong(at+8)<0||!Double.isFinite(progress)||progress<0||progress>1||in.getUnsignedByte(at+24)>1)throw new IllegalArgumentException("Invalid arm phase record");for(int j=0;j<i;j++)if(root==in.getLong(start+j*ENTRY_BYTES))throw new IllegalArgumentException("Duplicate arm phase root");}
        List<Arm> result=new ArrayList<>(count);for(int i=0;i<count;i++)result.add(new Arm(BlockPos.fromLong(in.readLong()),in.readLong(),in.readDouble(),in.readUnsignedByte()!=0));return new VehicleArmMotionFrame(layout,result);
    }
    private static boolean root(long packed){long x=packed>>38,y=(packed>>26)&4095L,z=packed<<38>>38;return x>=0&&x<VehicleStructure.MAX_AXIS&&y<VehicleStructure.MAX_AXIS&&z>=0&&z<VehicleStructure.MAX_AXIS;}
}
