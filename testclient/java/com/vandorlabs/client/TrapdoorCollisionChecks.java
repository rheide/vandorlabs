package com.vandorlabs.client;

import com.vandorlabs.render.DiagonalTrapdoorGeometry;
import com.vandorlabs.render.DiagonalTrapdoorCollision;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/** Exact collision lists and ordering, including strict touching-edge rejection. */
final class TrapdoorCollisionChecks {
    static void run() {
        Random random=new Random(314159);int cases=0;
        for(int mode=0;mode<3;mode++)for(int turns=0;turns<4;turns++)
            for(boolean inverted:new boolean[]{false,true})for(boolean reverse:new boolean[]{false,true})
            for(int movement=0;movement<3;movement++)for(double pose:new double[]{0,.37,1})
            for(double travel:new double[]{15/16D,127/16D}) {
                double[][] v=DiagonalTrapdoorGeometry.corners(mode,inverted,turns,movement!=0,reverse,pose,
                        reverse?15/16D:1/16D,travel,inverted?-1:1,movement==2);
                int axis=mode==2?4:2,across=pose>0 && movement==0?16:1;
                BlockPos pos=new BlockPos(random.nextBoolean()?8:29999900,100,-400);
                AxisAlignedBB full=new AxisAlignedBB(-16,-16,-16,16,16,16).offset(pos);
                List<AxisAlignedBB> all=reference(v,axis,across,pos,full);
                compare(v,axis,across,pos,full);cases++;
                for(int i=0;i<4;i++) {
                    double x=random.nextDouble()*4-2,y=random.nextDouble()*4-2,z=random.nextDouble()*4-2;
                    compare(v,axis,across,pos,new AxisAlignedBB(x,y,z,x+.6,y+1.8,z+.6).offset(pos));cases++;
                }
                AxisAlignedBB edge=all.get(random.nextInt(all.size()));
                compare(v,axis,across,pos,new AxisAlignedBB(edge.maxX,edge.minY,edge.minZ,edge.maxX+1,edge.maxY,edge.maxZ));cases++;
                compare(v,axis,across,pos,new AxisAlignedBB(Math.nextDown(edge.maxX),edge.minY,edge.minZ,edge.maxX+1,edge.maxY,edge.maxZ));cases++;
            }
        System.out.println("PASS: "+cases+" exact diagonal collision lists across poses, shapes, motion, grouped travel and edge contacts");
        cacheSafety();
    }
    private static void cacheSafety() {
        try {
            java.lang.reflect.Field field=DiagonalTrapdoorCollision.class.getDeclaredField("MESHES");field.setAccessible(true);
            com.google.common.cache.Cache<?,?> cache=(com.google.common.cache.Cache<?,?>)field.get(null);
            cache.cleanUp();if(cache.size()>128)throw new AssertionError("unbounded collision meshes");
            BlockPos pos=BlockPos.ORIGIN;AxisAlignedBB box=new AxisAlignedBB(-20,-20,-20,20,20,20);
            double[][] original=DiagonalTrapdoorGeometry.corners(0,false,0,false,false,1);
            double[][] edited=DiagonalTrapdoorGeometry.corners(0,false,0,false,false,1);
            cache.invalidateAll();compare(edited,2,16,pos,box);
            for(double[] point:edited)point[0]+=7;
            compare(original,2,16,pos,box);compare(edited,2,16,pos,box);
            java.util.concurrent.ExecutorService workers=java.util.concurrent.Executors.newFixedThreadPool(4);
            try {
                List<java.util.concurrent.Future<?>> tasks=new ArrayList<>();
                for(int worker=0;worker<4;worker++) {
                    final int direction=worker;
                    tasks.add(workers.submit(()->{
                        for(int i=0;i<128;i++) {
                            double[][] v=DiagonalTrapdoorGeometry.corners(i%3,(i&1)==0,direction,false,false,(i%17)/16D);
                            compare(v,i%3==2?4:2,16,pos,box);
                        }
                    }));
                }
                for(java.util.concurrent.Future<?> task:tasks)task.get();
            } finally {workers.shutdownNow();}
            cache.cleanUp();if(cache.size()>128)throw new AssertionError("concurrent collision meshes exceeded cap");
        } catch(ReflectiveOperationException | java.util.concurrent.ExecutionException | InterruptedException failure) {
            throw new AssertionError(failure);
        }
        System.out.println("PASS: collision caches copy input corners, support concurrent queries and remain bounded");
    }
    private static List<AxisAlignedBB> reference(double[][] v,int axis,int across,BlockPos pos,AxisAlignedBB box) {
        List<AxisAlignedBB> result=new ArrayList<>();ReferenceDiagonalTrapdoorCollision.add(v,axis,across,pos,box,result);return result;
    }
    private static void compare(double[][] v,int axis,int across,BlockPos pos,AxisAlignedBB box) {
        List<AxisAlignedBB> expected=reference(v,axis,across,pos,box),actual=new ArrayList<>();
        DiagonalTrapdoorCollision.add(v,axis,across,pos,box,actual);
        if(!expected.equals(actual))throw new AssertionError("collision bounds/order differ: "+expected+" vs "+actual);
    }
}
