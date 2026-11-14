package com.vandorlabs.client;

import com.vandorlabs.blocks.BlockGlassWall;
import com.vandorlabs.blocks.BlockProgrammableGlass;
import com.vandorlabs.blocks.PanelJoins;
import com.vandorlabs.tiles.TileEntityProgrammableGlass;
import com.vandorlabs.vehicle.VehicleWorld;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.chunk.*;
import net.minecraft.client.renderer.vertex.*;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.relauncher.ReflectionHelper;
import java.lang.reflect.*;
import java.util.*;

/** Publish native frame meshes once; retain the tile fallback until their section upload completes. */
@net.minecraftforge.fml.common.Mod.EventBusSubscriber(modid="vandorlabs",value=net.minecraftforge.fml.relauncher.Side.CLIENT)
public final class GlassFrameChunks {
    private static final boolean LEGACY_BENCHMARK=Boolean.getBoolean("vandorlabs.benchmarkLegacyGlazing");
    private static final Set<TileEntityProgrammableGlass> QUEUED=Collections.newSetFromMap(new IdentityHashMap<>());
    private static final Set<BlockPos> DIRTY=new HashSet<>();
    private static final Map<BlockPos,Entry> ENTRIES=new HashMap<>();
    private static final Map<BlockPos,Set<Entry>> MEMBERS=new HashMap<>();
    private static final Map<BlockPos,Section> PENDING=new LinkedHashMap<>();
    private static final com.google.common.cache.Cache<Key,GlassFrameModel.Mesh> MESHES=com.google.common.cache.CacheBuilder.newBuilder().maximumWeight(8*1024*1024)
            .weigher((Key key,GlassFrameModel.Mesh mesh)->(int)Math.min(Integer.MAX_VALUE,Math.max(1,mesh.bytes()+128L+key.source.cacheRetainedBytes()))).build();
    private static final Field FRUSTUM=ReflectionHelper.findField(RenderGlobal.class,"viewFrustum","field_175008_n"),TASK=ReflectionHelper.findField(RenderChunk.class,"compileTask","field_178599_i");
    private static final Method LOOKUP=lookup();
    private static World world;
    private static WorldRenderListener listener;
    private static VertexFormat format;
    private static ViewFrustum frustum;
    private static Method lookup(){for(Method method:ViewFrustum.class.getDeclaredMethods())if(method.getReturnType()==RenderChunk.class&&Arrays.equals(method.getParameterTypes(),new Class[]{BlockPos.class})){method.setAccessible(true);return method;}throw new IllegalStateException("Missing render chunk lookup");}
    private static BlockPos section(BlockPos pos){return new BlockPos(pos.getX()&~15,pos.getY()&~15,pos.getZ()&~15);}
    private static final class Key {
        final StaticSurfaceMesh source;final int depth,facing,kind;
        Key(TileEntityProgrammableGlass tile,IBlockState state){
            com.vandorlabs.blocks.BlockProgrammableWall.FlatCorner corner=PanelJoins.flatCorner(state,tile.getWorld(),tile.getPos());
            source=corner==null?GlassFrameMesh.get(tile):TEProgrammableGlass.meshes(tile,state,corner).frame;
            depth=state.getValue(BlockGlassWall.DEPTH);facing=(corner==null?PanelJoins.facing(state):PanelJoins.flatFacing(state,tile.getWorld(),tile.getPos())).getIndex();
            kind=corner==null?0:PanelJoins.flatBar(state,tile.getWorld(),tile.getPos())==null?1:2;
        }
        @Override public int hashCode(){return ((System.identityHashCode(source)*3+depth)*6+facing)*3+kind;}
        @Override public boolean equals(Object other){if(!(other instanceof Key))return false;Key key=(Key)other;return key.source==source&&key.depth==depth&&key.facing==facing&&key.kind==kind;}
    }
    private static final class Entry {
        final TileEntityProgrammableGlass tile;final BlockPos pos,section;
        GlassFrameModel.Mesh mesh;RenderChunk chunk;boolean ready,retiring,retained,wasNative,withdrawn=true;
        Entry(TileEntityProgrammableGlass tile){this.tile=tile;pos=tile.getPos().toImmutable();section=section(pos);}
    }
    private static final class Section {
        final BlockPos pos;RenderChunk chunk;CompiledChunk before,beforeTaskResult;
        ChunkCompileTaskGenerator beforeTask;boolean marked;
        Section(BlockPos pos){this.pos=pos;}
        Section(BlockPos pos,RenderChunk chunk,ChunkCompileTaskGenerator task){
            this.pos=pos;this.chunk=chunk;before=chunk.getCompiledChunk();beforeTask=task;beforeTaskResult=task==null?null:task.getCompiledChunk();
        }
        boolean uploaded(ViewFrustum view,RenderGlobal renderer)throws ReflectiveOperationException{
            if(view==null)return false;RenderChunk current=(RenderChunk)LOOKUP.invoke(view,pos);
            if(current==null||!current.getPosition().equals(pos))return false;
            if(chunk!=current){
                if(!current.getLockCompileTask().tryLock())return false;
                try{
                    ChunkCompileTaskGenerator old=(ChunkCompileTaskGenerator)TASK.get(current);
                    if(old!=null&&!old.getLock().tryLock())return false;
                    try{chunk=current;before=current.getCompiledChunk();beforeTask=old;beforeTaskResult=old==null?null:old.getCompiledChunk();marked=false;}
                    finally{if(old!=null)old.getLock().unlock();}
                }finally{current.getLockCompileTask().unlock();}
            }
            if(!marked){
                // One mark per pending section, after every changed mesh has been published.
                // An interior point marks this section without touching adjacent sections.
                int x=pos.getX()+8,y=pos.getY()+8,z=pos.getZ()+8;
                renderer.markBlockRangeForRenderUpdate(x,y,z,x,y,z);marked=true;
                return false;
            }
            // Vanilla schedules visible sections; polling must not stall the client thread.
            if(current.needsUpdate()||!current.getLockCompileTask().tryLock())return false;
            try{
                ChunkCompileTaskGenerator task=(ChunkCompileTaskGenerator)TASK.get(current);
                if(task==null||!task.getLock().tryLock())return false;
                try{
                    CompiledChunk installed=current.getCompiledChunk();
                    // A pre-publication DONE task can install after its lock is
                    // released. Exclude that result as well as the old installed
                    // chunk; a later resort may acknowledge the distinct rebuild.
                    return !current.needsUpdate()&&!task.isFinished()&&task!=beforeTask&&installed!=CompiledChunk.DUMMY
                            &&installed!=before&&installed!=beforeTaskResult&&task.getCompiledChunk()==installed
                            &&(task.getType()==ChunkCompileTaskGenerator.Type.RESORT_TRANSPARENCY||task.getStatus()==ChunkCompileTaskGenerator.Status.DONE);
                }finally{task.getLock().unlock();}
            }finally{current.getLockCompileTask().unlock();}
        }
    }
    /** Safe from onLoad and chunk workers: geometry and neighbour reads happen in flush only. */
    public static void changed(TileEntityProgrammableGlass tile){
        World view=tile.getWorld(),physical=view;
        // Captured views are remote even on the integrated server. Follow only
        // parent references before touching any client cache or publication queue.
        while(physical instanceof VehicleWorld)physical=((VehicleWorld)physical).parent;
        if(physical==null||!physical.isRemote)return;
        GlassSurfaceGroups.changed(tile);
        if(view instanceof VehicleWorld)return;
        synchronized(QUEUED){QUEUED.add(tile);}
        GlassFrameRetained.changed(tile);
    }
    static boolean selected(TileEntityProgrammableGlass tile){return tile.isFramed()&&(tile.frameAppearance.front>=0||tile.frameAppearance.interior>=0||tile.frameAppearance.exteriorChoice()>=0||tile.frameAppearance.tile);}
    static boolean supported(TileEntityProgrammableGlass tile,IBlockState state){
        return !LEGACY_BENCHMARK&&!(tile.getWorld() instanceof VehicleWorld)&&state.getBlock() instanceof BlockProgrammableGlass&&tile.isFramed()
                &&(selected(tile)||PanelJoins.flatCorner(state,tile.getWorld(),tile.getPos())!=null);
    }
    /** Mixed-light translucent frames use a retained frame group, independently
     * of the pane, instead of restoring an individual frame renderer. */
    static boolean nativeLighting(GlassFrameModel.Mesh mesh,int light){
        return mesh!=null&&(light==0||mesh.translucent.isEmpty());
    }
    static boolean awaiting(TileEntityProgrammableGlass tile){
        Object value=tile.frameChunkPublication;
        return value instanceof Entry&&((Entry)value).mesh!=null&&!((Entry)value).ready;
    }
    /** Never let a retained group overlap a previously published native frame. */
    static boolean retainedSafe(TileEntityProgrammableGlass tile){
        Object value=tile.frameChunkPublication;if(!(value instanceof Entry))return false;Entry entry=(Entry)value;
        return entry.tile==tile&&entry.pos.equals(tile.getPos())&&entry.retained&&entry.mesh==null
                &&tile.frameChunkMesh==null&&(!entry.wasNative||entry.withdrawn);
    }
    /** A ready mesh stays valid across ordinary relighting and transparent resort tasks. */
    static boolean rendered(TileEntityProgrammableGlass tile){
        Object value=tile.frameChunkPublication;if(!(value instanceof Entry))return false;Entry entry=(Entry)value;
        if(entry.tile!=tile||!entry.pos.equals(tile.getPos()))return false;
        if(!entry.retiring&&(!entry.ready||entry.mesh==null||tile.frameChunkMesh!=entry.mesh))return false;
        if(entry.chunk!=null&&entry.chunk.getPosition().equals(entry.section))return true;
        entry.ready=entry.retiring=false;changed(tile);GlassPaneTerrain.refreshAdmissions(tile.getPos());return false;
    }
    public static void reload(){
        MESHES.invalidateAll();GlassFrameMesh.clear();GlassFrameModel.clear();
        for(Entry entry:ENTRIES.values()){changed(entry.tile);GlassPaneTerrain.refreshAdmissions(entry.tile.getPos());}
        GlassFrameRetained.reload();
        PENDING.clear();
    }
    @SubscribeEvent public static void unloaded(net.minecraftforge.event.world.WorldEvent.Unload event){if(event.getWorld()==world)reset(null);}
    private static void reset(World next){
        if(world!=null&&listener!=null)world.removeEventListener(listener);
        for(Entry entry:ENTRIES.values()){entry.tile.frameChunkMesh=null;entry.tile.frameChunkPublication=null;}
        ENTRIES.clear();MEMBERS.clear();DIRTY.clear();PENDING.clear();MESHES.invalidateAll();world=next;frustum=null;
        if(next!=null){listener=new WorldRenderListener(){@Override public void notifyBlockUpdate(World w,BlockPos pos,IBlockState old,IBlockState state,int flags){
            // Newly loaded owners enter through QUEUED; unrelated worlds need no frame work.
            if(!MEMBERS.isEmpty())DIRTY.add(pos.toImmutable());
        }};next.addEventListener(listener);}
    }
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event){if(event.phase==TickEvent.Phase.END)flush();}
    @SubscribeEvent public static void renderTick(TickEvent.RenderTickEvent event){
        if(event.phase!=TickEvent.Phase.START||world==null||Minecraft.getMinecraft().world!=world)return;
        if(format!=null&&!format.equals(DefaultVertexFormats.BLOCK)){format=new VertexFormat(DefaultVertexFormats.BLOCK);reload();}
        try{viewChanged(Minecraft.getMinecraft());}catch(ReflectiveOperationException failure){throw new IllegalStateException("Glass frame view publication failed",failure);}
    }
    private static void viewChanged(Minecraft mc)throws ReflectiveOperationException{
        ViewFrustum next=(ViewFrustum)FRUSTUM.get(mc.renderGlobal);
        if(next==frustum)return;frustum=next;
        for(Entry entry:ENTRIES.values())if(entry.mesh!=null||!entry.withdrawn){entry.ready=entry.retiring=false;PENDING.put(entry.section,new Section(entry.section));GlassPaneTerrain.refreshAdmissions(entry.tile.getPos());GlassFrameRetained.invalidateSection(entry.pos);}
    }
    private static void remove(Entry entry){
        ENTRIES.remove(entry.pos,entry);if(entry.tile.frameChunkPublication==entry){entry.tile.frameChunkMesh=null;entry.tile.frameChunkPublication=null;}
        GlassPaneTerrain.refreshAdmissions(entry.pos);GlassFrameRetained.invalidateSection(entry.pos);
        Set<Entry> members=MEMBERS.get(entry.section);if(members!=null){members.remove(entry);if(members.isEmpty()){MEMBERS.remove(entry.section);PENDING.remove(entry.section);}}
    }
    static void flush(){
        Minecraft mc=Minecraft.getMinecraft();if(mc.world!=world)reset(mc.world);
        if(world==null){synchronized(QUEUED){QUEUED.clear();}return;}
        if(format==null||!format.equals(DefaultVertexFormats.BLOCK)){format=new VertexFormat(DefaultVertexFormats.BLOCK);reload();}
        try{viewChanged(mc);}catch(ReflectiveOperationException failure){throw new IllegalStateException("Glass frame view publication failed",failure);}
        Set<TileEntityProgrammableGlass> queued=Collections.emptySet();
        synchronized(QUEUED){if(!QUEUED.isEmpty()){queued=Collections.newSetFromMap(new IdentityHashMap<>());queued.addAll(QUEUED);QUEUED.clear();}}
        for(TileEntityProgrammableGlass tile:queued)if(tile.getWorld()==world){
            // Recheck after queued loads have settled, before building contours.
            // Only this owner and adjacent corner dependencies need to expire.
            GlassSurfaceGroups.changed(tile);
            BlockPos pos=tile.getPos().toImmutable();DIRTY.add(pos);
            if(valid(tile)){
                Object previous=tile.frameChunkPublication;if(previous instanceof Entry&&((Entry)previous).tile==tile&&!((Entry)previous).pos.equals(pos))remove((Entry)previous);
                Entry entry=ENTRIES.get(pos);if(entry==null||entry.tile!=tile){
                    if(entry!=null)remove(entry);
                    entry=new Entry(tile);ENTRIES.put(pos,entry);MEMBERS.computeIfAbsent(entry.section,p->Collections.newSetFromMap(new IdentityHashMap<>())).add(entry);tile.frameChunkPublication=entry;
                }
            }else{Entry entry=ENTRIES.get(pos);if(entry!=null&&entry.tile==tile)remove(entry);}
        }
        Set<Entry> dirty=DIRTY.isEmpty()?Collections.emptySet():Collections.newSetFromMap(new IdentityHashMap<>());
        if(!DIRTY.isEmpty())GlassFrameDirtyRegions.collect(ENTRIES,MEMBERS,DIRTY,dirty);
        DIRTY.clear();
        for(Entry entry:dirty){
            TileEntityProgrammableGlass tile=entry.tile;
            if(!valid(tile)||!entry.pos.equals(tile.getPos())){remove(entry);continue;}
            IBlockState state=world.getBlockState(tile.getPos());GlassFrameModel.Mesh mesh=null;int light=0;boolean retained=false;
            if(supported(tile,state)){
                Key key=new Key(tile,state);mesh=MESHES.getIfPresent(key);light=key.kind==0?tile.frameAppearance.light():0;
                if(mesh==null){mesh=GlassFrameModel.build(tile,state);MESHES.put(key,mesh);}
                if(!nativeLighting(mesh,light)){retained=true;mesh=null;}
            }
            if(entry.mesh==mesh&&tile.frameChunkLight==light&&entry.retained==retained
                    &&(entry.ready||PENDING.containsKey(entry.section)||mesh==null&&(!entry.wasNative||entry.withdrawn)))continue;
            try{if(!publish(entry,mesh,light,retained,mc))DIRTY.add(entry.pos);}
            catch(ReflectiveOperationException failure){throw new IllegalStateException("Glass frame publication fence failed",failure);}
        }
        // Retire old groups before any replacement native build can be queued.
        // Cold non-native frames may already establish their retained anchors.
        GlassFrameRetained.flush();
        settleUploads();
    }
    /** Observe receipts before retained groups and admission lists are updated.
     * This is cheap at frame start: no owner scan or new mesh publication. */
    static void settleUploads(){
        Minecraft mc=Minecraft.getMinecraft();if(mc==null||world==null||mc.world!=world)return;
        try{
            viewChanged(mc);
            Iterator<Section> pending=PENDING.values().iterator();while(pending.hasNext()){
                Section section=pending.next();if(!section.uploaded(frustum,mc.renderGlobal))continue;
                Set<Entry> members=MEMBERS.get(section.pos);if(members!=null)for(Entry entry:members){entry.chunk=section.chunk;entry.ready=entry.mesh!=null;entry.retiring=false;entry.withdrawn=entry.mesh==null;GlassPaneTerrain.refreshAdmissions(entry.tile.getPos());GlassFrameRetained.invalidateSection(entry.pos);}
                pending.remove();
            }
        }catch(ReflectiveOperationException failure){throw new IllegalStateException("Glass frame terrain publication failed",failure);}
    }
    /** Snapshot changes and withdrawal receipts share the real compile-task fence. */
    private static boolean publish(Entry entry,GlassFrameModel.Mesh mesh,int light,boolean retained,Minecraft mc)throws ReflectiveOperationException{
        ViewFrustum view=(ViewFrustum)FRUSTUM.get(mc.renderGlobal);
        RenderChunk chunk=view==null?null:(RenderChunk)LOOKUP.invoke(view,entry.section);
        if(chunk!=null&&!chunk.getPosition().equals(entry.section))chunk=null;
        if(chunk==null){publish(entry,mesh,light,retained);if(mesh!=null||entry.wasNative)PENDING.put(entry.section,new Section(entry.section));return true;}
        if(!chunk.getLockCompileTask().tryLock())return false;
        try{
            ChunkCompileTaskGenerator task=(ChunkCompileTaskGenerator)TASK.get(chunk);
            if(task!=null&&!task.getLock().tryLock())return false;
            try{
                if(!GlassPaneTerrain.finishBeforePublish(task))return false;
                publish(entry,mesh,light,retained);
                if(mesh!=null||entry.wasNative)PENDING.put(entry.section,new Section(entry.section,chunk,task));
                return true;
            }finally{if(task!=null)task.getLock().unlock();}
        }finally{chunk.getLockCompileTask().unlock();}
    }
    private static void publish(Entry entry,GlassFrameModel.Mesh mesh,int light,boolean retained){
        entry.retiring|=entry.ready;entry.ready=false;entry.retained=retained;
        entry.mesh=mesh;entry.tile.frameChunkLight=light;entry.tile.frameChunkMesh=mesh;
        if(mesh!=null){entry.wasNative=true;entry.withdrawn=false;}
        GlassPaneTerrain.refreshAdmissions(entry.pos);GlassFrameRetained.changed(entry.tile);
    }
    static String diagnostics(){
        Map<String,Integer> reasons=new TreeMap<>();int nativeOwners=0;
        for(Entry entry:ENTRIES.values()){
            String reason;
            if(entry.retained)reason=retainedSafe(entry.tile)?GlassFrameRetained.handles(entry.tile)?"retained":"retained-pending":"native-withdrawal";
            else if(entry.ready||entry.retiring){reason="native";nativeOwners++;}
            else if(entry.mesh!=null)reason="native-pending";
            else reason=entry.tile.isFramed()?"stock-or-unsupported":"unframed";
            reasons.put(reason,reasons.getOrDefault(reason,0)+1);
        }
        return "native-frame-owners="+nativeOwners+" native-frame-pending-sections="+PENDING.size()+" frame-owner-reasons="+reasons;
    }
    private static boolean valid(TileEntityProgrammableGlass tile){return !tile.isInvalid()&&world.isBlockLoaded(tile.getPos())&&world.getTileEntity(tile.getPos())==tile&&world.getBlockState(tile.getPos()).getBlock() instanceof BlockProgrammableGlass;}
    private GlassFrameChunks(){}
}
