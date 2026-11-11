package com.vandorlabs.client;

import com.vandorlabs.client.segmented.*;
import com.vandorlabs.segmented.*;
import java.lang.reflect.*;
import java.lang.management.ManagementFactory;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.locks.ReentrantLock;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.chunk.*;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.relauncher.CoreModManager;
import sun.misc.Unsafe;

/** Real publication CPU path, isolated from GL; blocked workers must never block the tick. */
public final class SegmentedPublicationCpuChecks {
    private static final BlockPos POS=new BlockPos(0,32,0);
    private static final TickEvent.ClientTickEvent TICK=new TickEvent.ClientTickEvent(TickEvent.Phase.END);
    private static Unsafe unsafe;
    private static Minecraft mc;
    private static Constructor<?> sectionConstructor;
    private static Method uploaded;
    private static int checks;
    private static void need(boolean value,String why){checks++;if(!value)throw new AssertionError(why);}
    private static Field field(Class<?> type,String name)throws Exception{Field f=type.getDeclaredField(name);f.setAccessible(true);return f;}
    private static void set(Object owner,Class<?> type,String name,Object value)throws Exception{field(type,name).set(owner,value);}
    private static Object get(Object owner,Class<?> type,String name)throws Exception{return field(type,name).get(owner);}
    private static final class Marks extends RenderGlobal {
        int count;
        Marks(){super(null);}
        @Override public void markBlockRangeForRenderUpdate(int x,int y,int z,int maxX,int maxY,int maxZ){
            need((x&15)==8&&(y&15)==8&&(z&15)==8&&x==maxX&&y==maxY&&z==maxZ,"section interior mark");count++;
            super.markBlockRangeForRenderUpdate(x,y,z,maxX,maxY,maxZ);
        }
    }
    private static final class World extends WorldClient {
        Map<BlockPos,TileEntity> tiles;Map<BlockPos,IBlockState> states;int reads;long time;
        World(){super(null,null,0,net.minecraft.world.EnumDifficulty.NORMAL,null);}
        @Override public TileEntity getTileEntity(BlockPos pos){reads++;return tiles.get(pos);}
        @Override public IBlockState getBlockState(BlockPos pos){reads++;return states.getOrDefault(pos,net.minecraft.init.Blocks.AIR.getDefaultState());}
        @Override public boolean isBlockLoaded(BlockPos pos){return true;}
        @Override public boolean isBlockLoaded(BlockPos pos,boolean empty){return true;}
        @Override public long getTotalWorldTime(){return time;}
    }
    private static final class Fixture {
        final Marks renderer;final ViewFrustum view;final World world;
        Fixture()throws Exception{
            view=(ViewFrustum)unsafe.allocateInstance(ViewFrustum.class);set(view,ViewFrustum.class,"countChunksX",3);set(view,ViewFrustum.class,"countChunksY",3);set(view,ViewFrustum.class,"countChunksZ",3);view.renderChunks=new RenderChunk[27];
            for(int z=0;z<3;z++)for(int y=0;y<3;y++)for(int x=0;x<3;x++){
                RenderChunk next=(RenderChunk)unsafe.allocateInstance(RenderChunk.class);set(next,RenderChunk.class,"position",new BlockPos.MutableBlockPos((x==2?-1:x)*16,y*16,(z==2?-1:z)*16));
                set(next,RenderChunk.class,"lockCompileTask",new ReentrantLock());set(next,RenderChunk.class,"lockCompiledChunk",new ReentrantLock());next.compiledChunk=new CompiledChunk();view.renderChunks[(z*3+y)*3+x]=next;
            }
            renderer=(Marks)unsafe.allocateInstance(Marks.class);set(renderer,RenderGlobal.class,"viewFrustum",view);mc.renderGlobal=renderer;
            world=(World)unsafe.allocateInstance(World.class);set(world,net.minecraft.world.World.class,"isRemote",true);world.tiles=new HashMap<>();world.states=new HashMap<>();mc.world=world;
        }
        RenderChunk chunk(){return view.renderChunks[6];}
        void complete(RenderChunk chunk,CompiledChunk compiled)throws Exception{
            ChunkCompileTaskGenerator task=new ChunkCompileTaskGenerator(chunk,ChunkCompileTaskGenerator.Type.REBUILD_CHUNK,0);task.setCompiledChunk(compiled);task.setStatus(ChunkCompileTaskGenerator.Status.DONE);set(chunk,RenderChunk.class,"compileTask",task);chunk.compiledChunk=compiled;chunk.clearNeedsUpdate();
        }
        void completeDirty()throws Exception{for(RenderChunk chunk:view.renderChunks)if(chunk.needsUpdate())complete(chunk,new CompiledChunk());}
        ChunkCompileTaskGenerator task()throws Exception{return (ChunkCompileTaskGenerator)get(chunk(),RenderChunk.class,"compileTask");}
    }
    private static boolean poll(Object section,Fixture fixture)throws Exception{
        mc.renderGlobal=fixture.renderer;Set<RenderChunk> marks=new HashSet<>();boolean result=(Boolean)uploaded.invoke(section,mc,marks);
        for(RenderChunk chunk:marks){BlockPos pos=chunk.getPosition();int x=pos.getX()+8,y=pos.getY()+8,z=pos.getZ()+8;fixture.renderer.markBlockRangeForRenderUpdate(x,y,z,x,y,z);}return result;
    }
    private static void lockDefers(Object section,Fixture fixture,ReentrantLock lock,String label)throws Exception{
        ExecutorService worker=Executors.newSingleThreadExecutor();Future<Boolean> result=null;CountDownLatch began=new CountDownLatch(1);lock.lock();
        try{
            result=worker.submit(()->{began.countDown();return poll(section,fixture);});need(began.await(2,TimeUnit.SECONDS),label+" worker start");
            try{need(!result.get(2,TimeUnit.SECONDS),label+" must defer");}catch(TimeoutException blocked){throw new AssertionError(label+" blocks client caller",blocked);}
        }finally{lock.unlock();if(result!=null)result.cancel(true);worker.shutdownNow();need(worker.awaitTermination(2,TimeUnit.SECONDS),label+" worker stopped");}
        need(poll(section,fixture),label+" release recovers");
    }
    private static void acknowledgements()throws Exception{
        Fixture fixture=new Fixture();Object section=sectionConstructor.newInstance(POS);
        need(!poll(section,fixture),"initial old upload rejected");need(fixture.renderer.count==1,"one initial mark");int dirty=0;for(RenderChunk chunk:fixture.view.renderChunks)if(chunk.needsUpdate())dirty++;need(dirty==1,"adjacent section untouched");
        for(int i=0;i<100;i++)need(!poll(section,fixture),"unuploaded section rejected");need(fixture.renderer.count==1,"pending polls do not rebuild repeatedly");
        fixture.complete(fixture.chunk(),new CompiledChunk());need(poll(section,fixture),"completed generation accepted");
        lockDefers(section,fixture,fixture.chunk().getLockCompileTask(),"compile lock");lockDefers(section,fixture,fixture.task().getLock(),"task lock");
        Object edited=sectionConstructor.newInstance(POS);CompiledChunk previous=fixture.chunk().getCompiledChunk();need(!poll(edited,fixture),"edit rejects old generation");fixture.complete(fixture.chunk(),previous);need(!poll(edited,fixture),"old compiled identity rejected");
        fixture.complete(fixture.chunk(),new CompiledChunk());ChunkCompileTaskGenerator task=fixture.task();CompiledChunk current=fixture.chunk().getCompiledChunk();task.setCompiledChunk(new CompiledChunk());need(!poll(edited,fixture),"unmatched upload rejected");task.setCompiledChunk(current);
        for(ChunkCompileTaskGenerator.Status status:ChunkCompileTaskGenerator.Status.values()){task.setStatus(status);need(poll(edited,fixture)==(status==ChunkCompileTaskGenerator.Status.DONE),"exact task completion "+status);}
        task.setStatus(ChunkCompileTaskGenerator.Status.DONE);fixture.chunk().setNeedsUpdate(false);need(!poll(edited,fixture),"dirty new generation rejected");fixture.chunk().clearNeedsUpdate();need(poll(edited,fixture),"clean complete recovers");
        Fixture next=new Fixture();need(!poll(edited,next),"new frustum does not reuse old upload");next.complete(next.chunk(),new CompiledChunk());need(poll(edited,next),"new frustum upload accepted");
        ((BlockPos.MutableBlockPos)get(next.chunk(),RenderChunk.class,"position")).setPos(POS.east(48));need(!poll(edited,next),"recycled section position rejected");
        System.out.println("segmented-publication-ack PASS initial/edit/replacement generations, coalesced interior marks, exact upload match, recycled positions and nonblocking compile/task locks");
    }
    @SuppressWarnings("unchecked") private static Map<SegmentedDoorTile,?> active(Class<?> type)throws Exception{return (Map<SegmentedDoorTile,?>)get(null,type,"ACTIVE");}
    private static void clear(Class<?> type)throws Exception{active(type).clear();((Set<?>)get(null,type,"QUEUED")).clear();}
    private static SegmentedDoorTile[] column(World world,int height,boolean nativePanel)throws Exception{
        SegmentedDoorTile[] tiles=new SegmentedDoorTile[height];IBlockState state=new SegmentedDoorBlock().getDefaultState();
        for(int i=0;i<height;i++){
            SegmentedDoorTile tile=new SegmentedDoorTile();tile.setWorld(world);tile.setPos(POS.down(i));tile.height=height;tile.topOffset=i;field(SegmentedDoorTile.class,"ready").setBoolean(tile,true);
            tile.settings=nativePanel?SegmentedDoorSettings.DEFAULT:new SegmentedDoorSettings(0,false,0,0,false,0,false,SegmentedDoorSettings.DEFAULT.glass);world.tiles.put(tile.getPos(),tile);world.states.put(tile.getPos(),state);tiles[i]=tile;
        }return tiles;
    }
    private static void retirement()throws Exception{
        Fixture fixture=new Fixture();clear(SegmentedDoorChunks.class);clear(ReferenceSegmentedDoorChunks.class);SegmentedDoorTile[] rows=column(fixture.world,32,false);SegmentedDoorTile root=rows[0];
        ReferenceSegmentedDoorChunks.changed(root);ReferenceSegmentedDoorChunks.tick(TICK);SegmentedDoorChunks.changed(root);SegmentedDoorChunks.tick(TICK);
        need(active(ReferenceSegmentedDoorChunks.class).containsKey(root),"baseline closed custom column remains active");need(!active(SegmentedDoorChunks.class).containsKey(root),"closed nonbake column retired");
        com.sun.management.ThreadMXBean meter=(com.sun.management.ThreadMXBean)ManagementFactory.getThreadMXBean();meter.setThreadAllocatedMemoryEnabled(true);long thread=Thread.currentThread().getId();long[] bytes=new long[2];int[] reads=new int[2];
        for(int mode=0;mode<2;mode++){
            fixture.world.reads=0;long start=meter.getThreadAllocatedBytes(thread);for(int tick=0;tick<256;tick++)if(mode==0)ReferenceSegmentedDoorChunks.tick(TICK);else SegmentedDoorChunks.tick(TICK);bytes[mode]=meter.getThreadAllocatedBytes(thread)-start;reads[mode]=fixture.world.reads;
        }
        need(reads[0]>=256*32&&reads[1]==0,"idle retired columns still rescan");need(bytes[1]<bytes[0],"idle retired columns allocate");
        System.out.println("segmented-publication-idle 256 ticks, closed 32-row nonnative column: worldReads="+Arrays.toString(reads)+" allocatedBytes="+Arrays.toString(bytes));
        clear(ReferenceSegmentedDoorChunks.class);clear(SegmentedDoorChunks.class);fixture=new Fixture();rows=column(fixture.world,3,true);root=rows[0];root.from=.5;root.duration=100;root.targetOpen=false;SegmentedDoorVisuals.authored=true;
        com.vandorlabs.CommonProxy saved=com.vandorlabs.VandorLabs.proxy;com.vandorlabs.VandorLabs.proxy=new com.vandorlabs.CommonProxy(){@Override public void segmentedAppearanceChanged(SegmentedDoorTile tile){SegmentedGateContexts.changed(tile);SegmentedDoorChunks.changed(tile);}};
        try{
            root.updateContainingBlockInfo();SegmentedDoorChunks.tick(TICK);need(active(SegmentedDoorChunks.class).containsKey(root)&&!root.clientAwaitingChunk,"native closing column must stay active");
            fixture.world.time=100;SegmentedDoorChunks.tick(TICK);need(root.clientAwaitingChunk&&!root.clientSuppressed,"closed endpoint waits for upload");for(SegmentedDoorTile row:rows)need(row.clientBaked,"member flag published before upload");
            fixture.completeDirty();SegmentedDoorChunks.tick(TICK);need(root.clientSuppressed&&!root.clientAwaitingChunk&&active(SegmentedDoorChunks.class).isEmpty(),"native column retires only after complete upload");
            root.settings=new SegmentedDoorSettings(0,false,0,0,false,0,false,SegmentedDoorSettings.DEFAULT.glass);root.updateContainingBlockInfo();SegmentedDoorChunks.tick(TICK);need(root.clientAwaitingChunk&&!root.clientSuppressed,"appearance change requeues native-to-dynamic transition");
            fixture.completeDirty();SegmentedDoorChunks.tick(TICK);need(!root.clientAwaitingChunk&&!root.clientSuppressed&&active(SegmentedDoorChunks.class).isEmpty(),"dynamic fallback resumes and stops polling");
            for(SegmentedDoorTile row:rows)need(!row.clientBaked,"old native panels removed");
            SegmentedDoorTile missing=rows[2];fixture.world.tiles.remove(missing.getPos());root.updateContainingBlockInfo();SegmentedDoorChunks.tick(TICK);need(active(SegmentedDoorChunks.class).isEmpty(),"incomplete idle owner retires");
            fixture.world.reads=0;for(int tick=0;tick<256;tick++)SegmentedDoorChunks.tick(TICK);need(fixture.world.reads==0,"incomplete idle owner keeps rescanning");
            fixture.world.tiles.put(missing.getPos(),missing);missing.updateContainingBlockInfo();SegmentedDoorChunks.tick(TICK);need(fixture.world.reads>0&&active(SegmentedDoorChunks.class).isEmpty(),"member callback rechecks completed custom column");
        }finally{SegmentedDoorVisuals.authored=false;com.vandorlabs.VandorLabs.proxy=saved;clear(SegmentedDoorChunks.class);}
    }
    public static void main(String[] args)throws Exception{
        net.minecraft.init.Bootstrap.register();Field environment=field(CoreModManager.class,"deobfuscatedEnvironment");boolean old=environment.getBoolean(null);
        try{
            environment.setBoolean(null,true);unsafe=(Unsafe)field(Unsafe.class,"theUnsafe").get(null);mc=(Minecraft)unsafe.allocateInstance(Minecraft.class);
            for(Field f:Minecraft.class.getDeclaredFields()){if(Modifier.isStatic(f.getModifiers())&&f.getType()==Minecraft.class){f.setAccessible(true);f.set(null,mc);}if(f.getType()==Thread.class){f.setAccessible(true);f.set(mc,Thread.currentThread());}}
            Class<?> section=Class.forName("com.vandorlabs.client.segmented.SegmentedDoorChunks$Section");sectionConstructor=section.getDeclaredConstructor(BlockPos.class);sectionConstructor.setAccessible(true);uploaded=section.getDeclaredMethod("uploaded",Minecraft.class,Set.class);uploaded.setAccessible(true);
            acknowledgements();retirement();System.out.println("segmented-publication-cpu PASS checks="+checks+" idle/custom/incomplete retirement, member and appearance requeue, native closing and exact upload handoff");
        }finally{environment.setBoolean(null,old);}
    }
    private SegmentedPublicationCpuChecks(){}
}
