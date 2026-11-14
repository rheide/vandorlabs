package com.vandorlabs.client;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.locks.ReentrantLock;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderGlobal;
import net.minecraft.client.renderer.ViewFrustum;
import net.minecraft.client.renderer.chunk.ChunkCompileTaskGenerator;
import net.minecraft.client.renderer.chunk.CompiledChunk;
import net.minecraft.client.renderer.chunk.RenderChunk;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.fml.relauncher.CoreModManager;
import sun.misc.Unsafe;

/** Exercise the real frame upload acknowledgement without a world or OpenGL context. */
public final class GlassFramePublicationChecks {
    private static final BlockPos POS=new BlockPos(0,16,0);
    private static Unsafe unsafe;
    private static Constructor<?> sectionConstructor;
    private static Method uploaded;

    private static final class Marks extends RenderGlobal {
        int count;
        Marks(){super((Minecraft)null);}
        @Override public void markBlockRangeForRenderUpdate(int x1,int y1,int z1,int x2,int y2,int z2){
            count++;
            require(x1==POS.getX()+8&&y1==POS.getY()+8&&z1==POS.getZ()+8&&x2==x1&&y2==y1&&z2==z1,
                    "publication must mark an interior point of its section");
            super.markBlockRangeForRenderUpdate(x1,y1,z1,x2,y2,z2);
        }
    }

    private static final class Fixture {
        final ViewFrustum view;
        final Marks renderer;
        final RenderChunk chunk;
        Fixture()throws Exception{
            // Constructors allocate GPU resources; only the actual CPU fields used by this path are needed.
            view=(ViewFrustum)unsafe.allocateInstance(ViewFrustum.class);
            set(view,ViewFrustum.class,"countChunksX",3);
            set(view,ViewFrustum.class,"countChunksY",3);
            set(view,ViewFrustum.class,"countChunksZ",3);
            view.renderChunks=new RenderChunk[27];
            for(int z=0;z<3;z++)for(int y=0;y<3;y++)for(int x=0;x<3;x++){
                RenderChunk next=(RenderChunk)unsafe.allocateInstance(RenderChunk.class);
                set(next,RenderChunk.class,"position",new BlockPos.MutableBlockPos((x==2?-1:x)*16,y*16,(z==2?-1:z)*16));
                set(next,RenderChunk.class,"lockCompileTask",new ReentrantLock());
                set(next,RenderChunk.class,"lockCompiledChunk",new ReentrantLock());
                next.compiledChunk=new CompiledChunk();
                view.renderChunks[(z*3+y)*3+x]=next;
            }
            chunk=view.renderChunks[3];
            renderer=(Marks)unsafe.allocateInstance(Marks.class);
            set(renderer,RenderGlobal.class,"viewFrustum",view);
        }
        void complete(CompiledChunk compiled)throws Exception{
            task(ChunkCompileTaskGenerator.Type.REBUILD_CHUNK,compiled,ChunkCompileTaskGenerator.Status.DONE,true);
        }
        ChunkCompileTaskGenerator task(ChunkCompileTaskGenerator.Type type,CompiledChunk compiled,ChunkCompileTaskGenerator.Status status,boolean install)throws Exception{
            ChunkCompileTaskGenerator task=new ChunkCompileTaskGenerator(chunk,type,0);
            task.setCompiledChunk(compiled);
            task.setStatus(status);
            set(chunk,RenderChunk.class,"compileTask",task);
            if(install)chunk.compiledChunk=compiled;
            chunk.clearNeedsUpdate();
            return task;
        }
        ChunkCompileTaskGenerator task()throws Exception{return (ChunkCompileTaskGenerator)get(chunk,RenderChunk.class,"compileTask");}
    }

    private static void require(boolean value,String message){if(!value)throw new AssertionError(message);}
    private static Field field(Class<?> type,String name)throws Exception{Field field=type.getDeclaredField(name);field.setAccessible(true);return field;}
    private static void set(Object owner,Class<?> type,String name,Object value)throws Exception{field(type,name).set(owner,value);}
    private static Object get(Object owner,Class<?> type,String name)throws Exception{return field(type,name).get(owner);}
    private static Object section()throws Exception{return sectionConstructor.newInstance(POS);}
    private static boolean poll(Object section,Fixture fixture)throws Exception{return (Boolean)uploaded.invoke(section,fixture.view,fixture.renderer);}

    private static void lockDefers(Object section,Fixture fixture,ReentrantLock lock,String name)throws Exception{
        ExecutorService worker=Executors.newSingleThreadExecutor();
        CountDownLatch entered=new CountDownLatch(1);
        Future<Boolean> query=null;
        lock.lock();
        try{
            query=worker.submit(()->{entered.countDown();return poll(section,fixture);});
            require(entered.await(2,TimeUnit.SECONDS),name+" probe did not start");
            try{
                require(!query.get(2,TimeUnit.SECONDS),name+" contention must defer acknowledgement");
            }catch(TimeoutException failure){
                throw new AssertionError(name+" contention blocked the caller instead of deferring",failure);
            }
        }finally{
            lock.unlock();
            if(query!=null)query.cancel(true);
            worker.shutdownNow();
            require(worker.awaitTermination(2,TimeUnit.SECONDS),name+" probe did not stop");
        }
        require(poll(section,fixture),name+" release did not restore acknowledgement");
    }

    private static void run()throws Exception{
        Fixture fixture=new Fixture();
        Object initial=section();
        require(!poll(initial,fixture),"initial publication accepted an old compiled section");
        require(fixture.renderer.count==1,"initial publication did not mark its section");
        int dirty=0;for(RenderChunk chunk:fixture.view.renderChunks)if(chunk.needsUpdate())dirty++;
        require(dirty==1&&fixture.chunk.needsUpdate(),"section mark dirtied adjacent sections");
        for(int i=0;i<100;i++)require(!poll(initial,fixture),"pending section accepted before upload");
        require(fixture.renderer.count==1,"pending polls repeatedly marked the section");
        fixture.complete(new CompiledChunk());
        require(poll(initial,fixture),"new matching completed upload was not accepted");
        lockDefers(initial,fixture,fixture.chunk.getLockCompileTask(),"compile lock");
        lockDefers(initial,fixture,fixture.task().getLock(),"task lock");
        require(fixture.renderer.count==1,"acknowledgement locks caused a rebuild mark");

        Object edited=section();CompiledChunk previous=fixture.chunk.getCompiledChunk();
        require(!poll(edited,fixture),"edit accepted the previous upload");
        require(fixture.renderer.count==2,"edit must mark exactly once");
        fixture.complete(previous);
        require(!poll(edited,fixture),"DONE task accepted the previous compiled generation");
        CompiledChunk next=new CompiledChunk();fixture.complete(next);
        ChunkCompileTaskGenerator task=fixture.task();
        task.setCompiledChunk(new CompiledChunk());
        require(!poll(edited,fixture),"task/current upload identity mismatch was accepted");
        task.setCompiledChunk(next);
        for(ChunkCompileTaskGenerator.Status status:new ChunkCompileTaskGenerator.Status[]{
                ChunkCompileTaskGenerator.Status.PENDING,ChunkCompileTaskGenerator.Status.COMPILING,ChunkCompileTaskGenerator.Status.UPLOADING}){
            task.setStatus(status);require(!poll(edited,fixture),status+" task was accepted before completion");
        }
        task.setStatus(ChunkCompileTaskGenerator.Status.DONE);
        require(poll(edited,fixture),"completed edit upload was not accepted");
        fixture.chunk.setNeedsUpdate(false);
        require(!poll(edited,fixture),"dirty section was accepted");
        fixture.chunk.clearNeedsUpdate();
        require(poll(edited,fixture),"clean completed section did not recover");
        require(fixture.renderer.count==2,"readiness polling dirtied an unchanged section");

        Fixture replacement=new Fixture();
        require(!poll(edited,replacement),"frustum replacement reused the former upload");
        require(replacement.renderer.count==1,"replacement section was not marked once");
        replacement.complete(new CompiledChunk());
        require(poll(edited,replacement),"replacement upload was rejected");
        BlockPos.MutableBlockPos position=(BlockPos.MutableBlockPos)get(replacement.chunk,RenderChunk.class,"position");
        position.setPos(POS.add(48,0,0));
        require(!poll(edited,replacement),"recycled chunk at another position was accepted");
        require(replacement.renderer.count==1,"out-of-position chunk was marked");
        latePublication();
        System.out.println("glass-frame-publication PASS initial/edit/new-frustum generations, late DONE installation and old resort rejection, successor resort acknowledgement, canceled task rejection, interior-only coalesced marks, recycled position and nonblocking compile/task locks");
    }

    /** A DONE rebuild can still install its result after its publication lock is released. */
    private static void latePublication()throws Exception{
        Fixture fixture=new Fixture();CompiledChunk oldResult=new CompiledChunk();
        ChunkCompileTaskGenerator before=fixture.task(ChunkCompileTaskGenerator.Type.REBUILD_CHUNK,oldResult,ChunkCompileTaskGenerator.Status.DONE,false);
        Object pending=section();require(!poll(pending,fixture),"withdrawal fence did not mark its section");
        fixture.chunk.compiledChunk=oldResult;fixture.chunk.clearNeedsUpdate();
        require(!poll(pending,fixture),"pre-publication DONE task installed after the fence and falsely withdrew native frame");
        require(fixture.task()==before,"receipt probe replaced the in-flight task");
        fixture.task(ChunkCompileTaskGenerator.Type.RESORT_TRANSPARENCY,oldResult,ChunkCompileTaskGenerator.Status.DONE,true);
        require(!poll(pending,fixture),"resort of a pre-publication task result falsely withdrew native frame");

        CompiledChunk replacement=new CompiledChunk();fixture.complete(replacement);
        fixture.task(ChunkCompileTaskGenerator.Type.RESORT_TRANSPARENCY,replacement,ChunkCompileTaskGenerator.Status.PENDING,true);
        require(poll(pending,fixture),"successor resort lost acknowledgement of the already installed replacement rebuild");
        require(fixture.renderer.count==1,"resorting repeatedly scheduled the same native-frame withdrawal");

        ChunkCompileTaskGenerator canceled=fixture.task(ChunkCompileTaskGenerator.Type.REBUILD_CHUNK,new CompiledChunk(),ChunkCompileTaskGenerator.Status.COMPILING,true);
        canceled.finish();fixture.chunk.clearNeedsUpdate();
        require(!poll(pending,fixture),"canceled task was mistaken for a completed frame replacement");
        fixture.complete(new CompiledChunk());require(poll(pending,fixture),"valid replacement could not recover from a canceled task");
    }

    public static void main(String[] args)throws Exception{
        Field environment=field(CoreModManager.class,"deobfuscatedEnvironment");
        boolean wasDeobfuscated=environment.getBoolean(null);
        try{
            environment.setBoolean(null,true);
            unsafe=(Unsafe)field(Unsafe.class,"theUnsafe").get(null);
            Class<?> section=Class.forName("com.vandorlabs.client.GlassFrameChunks$Section");
            sectionConstructor=section.getDeclaredConstructor(BlockPos.class);sectionConstructor.setAccessible(true);
            uploaded=section.getDeclaredMethod("uploaded",ViewFrustum.class,RenderGlobal.class);uploaded.setAccessible(true);
            run();
        }finally{environment.setBoolean(null,wasDeobfuscated);}
    }
    private GlassFramePublicationChecks(){}
}
