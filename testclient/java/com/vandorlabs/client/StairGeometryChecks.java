package com.vandorlabs.client;

import com.vandorlabs.blocks.*;
import com.vandorlabs.render.StairGeometry;
import net.minecraft.block.*;
import net.minecraft.block.state.IBlockState;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.*;
import java.security.MessageDigest;
import java.util.List;
import java.util.Arrays;
import java.util.Locale;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

/** Raw current Forge scalar/box/face outputs, frozen before portable geometry extraction. */
public final class StairGeometryChecks {
    private static final String GOLDEN="cd4b14c2348647ba3595a5541fa2ec30506ef359f91a90ef3660979662025b31";
    private static final MessageDigest DIGEST=digest();
    private static MessageDigest digest(){try{return MessageDigest.getInstance("SHA-256");}catch(Exception failure){throw new AssertionError(failure);}}
    private static void put(double value){long bits=Double.doubleToRawLongBits(value);for(int shift=56;shift>=0;shift-=8)DIGEST.update((byte)(bits>>>shift));}
    private static void put(int value){for(int shift=24;shift>=0;shift-=8)DIGEST.update((byte)(value>>>shift));}
    private static volatile Object consumed;
    private static void allocations(IBlockState state){java.lang.management.ThreadMXBean base=java.lang.management.ManagementFactory.getThreadMXBean();if(!(base instanceof com.sun.management.ThreadMXBean))return;com.sun.management.ThreadMXBean bean=(com.sun.management.ThreadMXBean)base;if(!bean.isThreadAllocatedMemorySupported())return;bean.setThreadAllocatedMemoryEnabled(true);for(int i=0;i<5000;i++)consumed=ProgrammableStairGeometry.boxes(state,1|(2<<5));long thread=Thread.currentThread().getId(),before=bean.getThreadAllocatedBytes(thread);for(int i=0;i<2000;i++)consumed=ProgrammableStairGeometry.boxes(state,1|(2<<5));long bytes=bean.getThreadAllocatedBytes(thread)-before;System.out.println("Stair warmed allocation boxes256="+(bytes/2000)+" bytes/op");}
    /** The baseline is the adapter's original direct call, not a simplified geometry substitute. */
    private static final StairGeometry.Boxes<AxisAlignedBB> NATIVE_BOXES=new StairGeometry.Boxes<AxisAlignedBB>(){
        public AxisAlignedBB create(double x0,double y0,double z0,double x1,double y1,double z1){return new AxisAlignedBB(x0,y0,z0,x1,y1,z1);}
        public double minX(AxisAlignedBB b){return b.minX;}public double minY(AxisAlignedBB b){return b.minY;}public double minZ(AxisAlignedBB b){return b.minZ;}
        public double maxX(AxisAlignedBB b){return b.maxX;}public double maxY(AxisAlignedBB b){return b.maxY;}public double maxZ(AxisAlignedBB b){return b.maxZ;}
    };
    private static Object probe(IBlockState state,int operation,boolean cached){
        if(cached){
            if(operation==0)return ProgrammableStairGeometry.boxes(state,1|(2<<5));
            if(operation==1)return ProgrammableStairGeometry.solidFace(state,1|(2<<5),EnumFacing.EAST);
            return ProgrammableStairGeometry.unrolledFace(state,2,EnumFacing.EAST);
        }
        int settings=1|(2<<5),facing=state.getValue(BlockStairs.FACING).getIndex();
        boolean upper=state.getValue(BlockStairs.HALF)==BlockStairs.EnumHalf.TOP;
        if(operation==2)return EnumFacing.getFront(StairGeometry.unrolledFace(facing,upper,2,EnumFacing.EAST.getIndex(),ProgrammableStairGeometry.POINTS));
        StairGeometry.Shape shape=ProgrammableStairGeometry.shape(state.getValue(BlockStairs.SHAPE));
        if(operation==0)return StairGeometry.boxes(facing,upper,shape,settings,ProgrammableStairGeometry.POINTS,NATIVE_BOXES);
        return StairGeometry.solidFace(facing,upper,shape,settings,EnumFacing.EAST.getIndex(),ProgrammableStairGeometry.POINTS,NATIVE_BOXES);
    }
    private static void benchmark(IBlockState state){
        java.lang.management.ThreadMXBean base=java.lang.management.ManagementFactory.getThreadMXBean();
        if(!(base instanceof com.sun.management.ThreadMXBean))return;
        com.sun.management.ThreadMXBean meter=(com.sun.management.ThreadMXBean)base;
        if(!meter.isThreadAllocatedMemorySupported()||!meter.isCurrentThreadCpuTimeSupported())return;
        meter.setThreadAllocatedMemoryEnabled(true);meter.setThreadCpuTimeEnabled(true);
        long thread=Thread.currentThread().getId();
        for(int operation=0;operation<3;operation++){
            int calls=operation==0?200:operation==1?32:10000;
            long[][] cpu=new long[2][21],allocated=new long[2][21];
            for(int sample=-20;sample<21;sample++)for(int order=0;order<2;order++){
                int algorithm=(sample+order)&1;
                long bytes=meter.getThreadAllocatedBytes(thread),start=meter.getCurrentThreadCpuTime();
                for(int i=0;i<calls;i++)consumed=probe(state,operation,algorithm==1);
                long elapsed=meter.getCurrentThreadCpuTime()-start,used=meter.getThreadAllocatedBytes(thread)-bytes;
                if(sample>=0){cpu[algorithm][sample]=elapsed;allocated[algorithm][sample]=used;}
            }
            for(int algorithm=0;algorithm<2;algorithm++){
                Arrays.sort(cpu[algorithm]);Arrays.sort(allocated[algorithm]);
                System.out.printf(Locale.ROOT,"stair-cache operation=%s algorithm=%s calls=%d cpu-median-ns=%d allocated-median-bytes=%d%n",
                        operation==0?"boxes256":operation==1?"solid-face":"unrolled-face",algorithm==0?"uncached-adapter":"current",calls,cpu[algorithm][10],allocated[algorithm][10]);
            }
            if(allocated[1][10]>allocated[0][10]+1024)throw new AssertionError("stair cache increased warm allocation");
        }
    }
    private static void cacheContract(IBlockState state){
        final int settings=1|(2<<5);
        AtomicReference<Throwable> failure=new AtomicReference<>();
        AtomicReference<List<AxisAlignedBB>> first=new AtomicReference<>();
        CountDownLatch ready=new CountDownLatch(8),start=new CountDownLatch(1);
        Thread[] workers=new Thread[8];
        for(int t=0;t<workers.length;t++){
            final int worker=t;
            workers[t]=new Thread(()->{try{
                ready.countDown();start.await();
                for(int repeat=0;repeat<100;repeat++){
                    List<AxisAlignedBB> boxes=ProgrammableStairGeometry.boxes(state,settings);
                    first.compareAndSet(null,boxes);
                    if(first.get()!=boxes)throw new AssertionError("raced stair geometry was not canonical");
                    for(int side=0;side<6;side++)ProgrammableStairGeometry.solidFace(state,settings,EnumFacing.getFront((side+worker)%6));
                }
            }catch(Throwable thrown){failure.compareAndSet(null,thrown);}},"stair-geometry-"+t);
            workers[t].start();
        }
        try{ready.await();start.countDown();for(Thread worker:workers)worker.join();}
        catch(InterruptedException interrupted){Thread.currentThread().interrupt();throw new AssertionError(interrupted);}
        if(failure.get()!=null)throw new AssertionError("concurrent stair publication",failure.get());
        try{first.get().clear();throw new AssertionError("shared stair boxes are mutable");}
        catch(UnsupportedOperationException expected){}
        try{first.get().set(0,new AxisAlignedBB(0,0,0,1,1,1));throw new AssertionError("shared stair box slots are mutable");}
        catch(UnsupportedOperationException expected){}
        // The cache cannot capture an owner block or world: a different block
        // instance with the same native pose must receive the same geometry.
        IBlockState another=new BlockProgrammableStairs().getDefaultState();
        if(ProgrammableStairGeometry.boxes(another,settings)!=first.get())throw new AssertionError("stair geometry retained the owner state");
        System.out.println("Stair geometry cache PASS concurrent first publication, six lazy faces, immutable boxes, no owner identity");
    }
    public static void main(String[] ignored){net.minecraft.init.Bootstrap.register();BlockProgrammableStairs block=new BlockProgrammableStairs();int cases=0,boxes=0;
        cacheContract(block.getDefaultState());
        for(EnumFacing facing:EnumFacing.HORIZONTALS)for(BlockStairs.EnumHalf half:BlockStairs.EnumHalf.values())for(BlockStairs.EnumShape shape:BlockStairs.EnumShape.values())for(int tread:new int[]{1,2,4,8,16})for(int roll=0;roll<3;roll++){
            IBlockState state=block.getDefaultState().withProperty(BlockStairs.FACING,facing).withProperty(BlockStairs.HALF,half).withProperty(BlockStairs.SHAPE,shape);put(facing.getIndex());put(half.ordinal());put(shape.ordinal());put(tread);put(roll);
            for(AxisAlignedBB box:ProgrammableStairGeometry.boxes(state,tread|(roll<<5))){put(box.minX);put(box.minY);put(box.minZ);put(box.maxX);put(box.maxY);put(box.maxZ);boxes++;}for(EnumFacing side:EnumFacing.values()){put(ProgrammableStairGeometry.unrolledFace(state,roll,side).getIndex());put(ProgrammableStairGeometry.solidFace(state,tread|(roll<<5),side)?1:0);}cases++;
        }
        double[] values={-0D,0D,1e-7,.1,.5,.9,1D};for(EnumFacing facing:EnumFacing.HORIZONTALS)for(BlockStairs.EnumHalf half:BlockStairs.EnumHalf.values())for(int roll=0;roll<3;roll++)for(double x:values)for(double y:values)for(double z:values){IBlockState state=block.getDefaultState().withProperty(BlockStairs.FACING,facing).withProperty(BlockStairs.HALF,half);Vec3d p=ProgrammableStairGeometry.transform(new Vec3d(x,y,z),state,roll);put(p.x);put(p.y);put(p.z);}
        for(BlockStairs.EnumShape shape:BlockStairs.EnumShape.values())for(int count:new int[]{1,2,4,8,16})for(int x=0;x<count;x++)for(int z=0;z<count;z++)put(ProgrammableStairGeometry.height(x,z,count,shape));
        allocations(block.getDefaultState());
        StringBuilder hash=new StringBuilder();for(byte value:DIGEST.digest())hash.append(String.format("%02x",value&255));if(!GOLDEN.equals(hash.toString()))throw new AssertionError("Frozen stair geometry changed "+hash);System.out.println("Stair actual geometry PASS poses="+cases+" boxes="+boxes+" SHA256="+hash+" raw double bits, signed zero transforms, all6solid/unrolledfaces,5heightfields");
        benchmark(block.getDefaultState());
    }
}
