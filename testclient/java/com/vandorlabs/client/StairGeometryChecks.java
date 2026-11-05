package com.vandorlabs.client;

import com.vandorlabs.blocks.*;
import net.minecraft.block.*;
import net.minecraft.block.state.IBlockState;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.*;
import java.security.MessageDigest;

/** Raw current Forge scalar/box/face outputs, frozen before portable geometry extraction. */
public final class StairGeometryChecks {
    private static final String GOLDEN="cd4b14c2348647ba3595a5541fa2ec30506ef359f91a90ef3660979662025b31";
    private static final MessageDigest DIGEST=digest();
    private static MessageDigest digest(){try{return MessageDigest.getInstance("SHA-256");}catch(Exception failure){throw new AssertionError(failure);}}
    private static void put(double value){long bits=Double.doubleToRawLongBits(value);for(int shift=56;shift>=0;shift-=8)DIGEST.update((byte)(bits>>>shift));}
    private static void put(int value){for(int shift=24;shift>=0;shift-=8)DIGEST.update((byte)(value>>>shift));}
    private static volatile Object consumed;
    private static void allocations(IBlockState state){java.lang.management.ThreadMXBean base=java.lang.management.ManagementFactory.getThreadMXBean();if(!(base instanceof com.sun.management.ThreadMXBean))return;com.sun.management.ThreadMXBean bean=(com.sun.management.ThreadMXBean)base;if(!bean.isThreadAllocatedMemorySupported())return;bean.setThreadAllocatedMemoryEnabled(true);for(int i=0;i<5000;i++)consumed=ProgrammableStairGeometry.boxes(state,1|(2<<5));long thread=Thread.currentThread().getId(),before=bean.getThreadAllocatedBytes(thread);for(int i=0;i<2000;i++)consumed=ProgrammableStairGeometry.boxes(state,1|(2<<5));long bytes=bean.getThreadAllocatedBytes(thread)-before;System.out.println("Stair warmed allocation boxes256="+(bytes/2000)+" bytes/op");}
    public static void main(String[] ignored){net.minecraft.init.Bootstrap.register();BlockProgrammableStairs block=new BlockProgrammableStairs();int cases=0,boxes=0;
        for(EnumFacing facing:EnumFacing.HORIZONTALS)for(BlockStairs.EnumHalf half:BlockStairs.EnumHalf.values())for(BlockStairs.EnumShape shape:BlockStairs.EnumShape.values())for(int tread:new int[]{1,2,4,8,16})for(int roll=0;roll<3;roll++){
            IBlockState state=block.getDefaultState().withProperty(BlockStairs.FACING,facing).withProperty(BlockStairs.HALF,half).withProperty(BlockStairs.SHAPE,shape);put(facing.getIndex());put(half.ordinal());put(shape.ordinal());put(tread);put(roll);
            for(AxisAlignedBB box:ProgrammableStairGeometry.boxes(state,tread|(roll<<5))){put(box.minX);put(box.minY);put(box.minZ);put(box.maxX);put(box.maxY);put(box.maxZ);boxes++;}for(EnumFacing side:EnumFacing.values()){put(ProgrammableStairGeometry.unrolledFace(state,roll,side).getIndex());put(ProgrammableStairGeometry.solidFace(state,tread|(roll<<5),side)?1:0);}cases++;
        }
        double[] values={-0D,0D,1e-7,.1,.5,.9,1D};for(EnumFacing facing:EnumFacing.HORIZONTALS)for(BlockStairs.EnumHalf half:BlockStairs.EnumHalf.values())for(int roll=0;roll<3;roll++)for(double x:values)for(double y:values)for(double z:values){IBlockState state=block.getDefaultState().withProperty(BlockStairs.FACING,facing).withProperty(BlockStairs.HALF,half);Vec3d p=ProgrammableStairGeometry.transform(new Vec3d(x,y,z),state,roll);put(p.x);put(p.y);put(p.z);}
        for(BlockStairs.EnumShape shape:BlockStairs.EnumShape.values())for(int count:new int[]{1,2,4,8,16})for(int x=0;x<count;x++)for(int z=0;z<count;z++)put(ProgrammableStairGeometry.height(x,z,count,shape));
        allocations(block.getDefaultState());
        StringBuilder hash=new StringBuilder();for(byte value:DIGEST.digest())hash.append(String.format("%02x",value&255));if(!GOLDEN.equals(hash.toString()))throw new AssertionError("Frozen stair geometry changed "+hash);System.out.println("Stair actual geometry PASS poses="+cases+" boxes="+boxes+" SHA256="+hash+" raw double bits, signed zero transforms, all6solid/unrolledfaces,5heightfields");
    }
}
