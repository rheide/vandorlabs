package com.vandorlabs.blocks;

import com.vandorlabs.tiles.FaceTextures;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraftforge.common.property.IExtendedBlockState;
import net.minecraftforge.common.property.IUnlistedProperty;
import java.util.Objects;
import java.util.concurrent.*;

/** Immutable shared-value snapshots, including admission overflow and concurrent publication. */
public final class HousingPreparedStateChecks {
    private static void need(boolean yes,String message){if(!yes)throw new AssertionError(message);}
    public static IExtendedBlockState sample(IBlockState source,int finish,int side,FaceTextures faces,int layout,int visible){
        return HousingBlockState.sample((IExtendedBlockState)source,finish,side,faces,layout,visible,0);
    }
    public static void same(IExtendedBlockState expected,IExtendedBlockState actual){
        need(expected.getClean()==actual.getClean(),"canonical clean state changed");
        need(expected.getProperties().equals(actual.getProperties()),"listed properties changed");
        need(expected.getUnlistedNames().equals(actual.getUnlistedNames()),"unlisted schema changed");
        for(IUnlistedProperty<?> property:expected.getUnlistedNames())need(Objects.equals(expected.getValue(property),actual.getValue(property)),"snapshot value changed: "+property.getName());
        need(expected.getUnlistedProperties().equals(actual.getUnlistedProperties()),"materialized Forge values changed");
    }
    public static void run()throws Exception {
        int compared=0;
        for(Block block:new Block[]{new BlockProgrammableBlock(),new BlockProgrammableSlab()}){
            HousingBlockState container=(HousingBlockState)block.getBlockState();
            for(IBlockState source:container.getValidStates())for(int mask=0;mask<64;mask++){
                IExtendedBlockState actual=sample(source,3,-1,FaceTextures.DEFAULT,1,mask);
                same(ReferencePreparedHousingState.sample((IExtendedBlockState)source,3,-1,FaceTextures.DEFAULT,1,mask,0),actual);
                need(sample(source,3,-1,FaceTextures.DEFAULT,1,mask)==actual,"common snapshot missed equal values");compared++;
            }
            IBlockState source=block.getDefaultState();
            IExtendedBlockState pinned=sample(source,0,-1,FaceTextures.DEFAULT,1,63);
            for(int finish=0;finish<4096;finish++){
                IExtendedBlockState actual=sample(source,finish,-1,FaceTextures.DEFAULT,1,63);
                same(ReferencePreparedHousingState.sample((IExtendedBlockState)source,finish,-1,FaceTextures.DEFAULT,1,63,0),actual);
            }
            int retained=container.cachedCommonSnapshots();
            need(retained==HousingBlockState.MAX_COMMON_SNAPSHOTS,"snapshot admission did not reach a stable cap");
            need(container.cachedCommonBytes()<=HousingBlockState.MAX_COMMON_SNAPSHOTS*HousingBlockState.COMMON_SNAPSHOT_BYTES,"snapshot byte budget exceeded");
            need(sample(source,0,-1,FaceTextures.DEFAULT,1,63)==pinned,"overflow displaced a hot shared snapshot");
            IExtendedBlockState overflow=sample(source,9000,-1,FaceTextures.DEFAULT,1,63);
            need(sample(source,9000,-1,FaceTextures.DEFAULT,1,63)!=overflow,"overflow unexpectedly added retained state");
            same(ReferencePreparedHousingState.sample((IExtendedBlockState)source,9000,-1,FaceTextures.DEFAULT,1,63,0),overflow);
            // Saved disabled face choices must remain distinct, even when visually uniform.
            FaceTextures disabled=new FaceTextures(false,new int[]{-1,2,-1,-1,-1,-1});
            same(ReferencePreparedHousingState.sample((IExtendedBlockState)source,3,-1,disabled,1,63,0),sample(source,3,-1,disabled,1,63));
            need(container.cachedCommonSnapshots()==retained,"overflow/fallback altered bounded admission");
        }
        Block block=new BlockProgrammableBlock();IBlockState source=block.getDefaultState();
        ExecutorService workers=Executors.newFixedThreadPool(8);
        try{
            java.util.List<Future<IExtendedBlockState>> jobs=new java.util.ArrayList<>();
            CountDownLatch start=new CountDownLatch(1);
            for(int i=0;i<8;i++)jobs.add(workers.submit(()->{start.await();IExtendedBlockState result=null;for(int n=0;n<512;n++){result=sample(source,4,-1,FaceTextures.DEFAULT,1,63);result.getUnlistedProperties();}return result;}));
            start.countDown();IExtendedBlockState first=jobs.get(0).get();
            for(Future<IExtendedBlockState> job:jobs)need(job.get()==first,"racing workers published distinct admitted snapshots");
            need(((HousingBlockState)block.getBlockState()).cachedCommonSnapshots()==1,"concurrent admissions overcounted entries");
        }finally{workers.shutdownNow();}
        System.out.println("housing-prepared snapshots PASS canonical source/schema parity, all native metadata/masks, disabled choices, bounded overflow and concurrent publication; comparisons="+compared);
    }
}
