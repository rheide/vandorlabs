package com.vandorlabs.client;

import com.vandorlabs.blocks.BlockGlassWall;
import com.vandorlabs.blocks.BlockProgrammableGlass;
import com.vandorlabs.tiles.GlassMaterials;
import com.vandorlabs.tiles.TileEntityProgrammableGlass;
import com.vandorlabs.vehicle.VehicleWorld;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.*;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderGlobal;
import net.minecraft.client.renderer.ViewFrustum;
import net.minecraft.client.renderer.chunk.ChunkCompileTaskGenerator;
import net.minecraft.client.renderer.chunk.CompiledChunk;
import net.minecraft.client.renderer.chunk.RenderChunk;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.client.renderer.vertex.VertexFormat;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.relauncher.ReflectionHelper;
import net.minecraftforge.fml.relauncher.Side;

/** Publish authored stationary panes into Minecraft's ordinary translucent chunk buffers. */
@Mod.EventBusSubscriber(modid="vandorlabs",value=Side.CLIENT)
public final class GlassPaneTerrain {
    private static final int BUILD_OWNERS=1024,MAX_QUEUED=65536;
    private static final Set<TileEntityProgrammableGlass> QUEUED=identities();
    private static boolean rescan;
    private static final Map<BlockPos,Entry> ENTRIES=new HashMap<>();
    private static final Map<BlockPos,Section> SECTIONS=new LinkedHashMap<>();
    private static final Set<Entry> DIRTY=identities();
    private static final BlockPos.MutableBlockPos LOOKUP_POS=new BlockPos.MutableBlockPos();
    private static final com.google.common.cache.Cache<Key,GlassPaneTerrainMesh> MESHES=
            com.google.common.cache.CacheBuilder.newBuilder().maximumWeight(8*1024*1024)
                    .weigher((Key key,GlassPaneTerrainMesh mesh)->mesh.bytes()+96).build();
    private static final Field FRUSTUM=ReflectionHelper.findField(RenderGlobal.class,"viewFrustum","field_175008_n");
    private static final Field TASK=ReflectionHelper.findField(RenderChunk.class,"compileTask","field_178599_i");
    private static final Method CHUNK_LOOKUP=lookup();
    private static World world;
    private static WorldRenderListener listener;
    private static ViewFrustum frustum;
    private static VertexFormat format;
    private static volatile boolean formatRefresh;
    private static boolean terrainActive;
    private static long builds,reuses,requests,publications,admissionSweeps,withdrawals;
    private static String fallback="not-initialized";

    private static <T> Set<T> identities(){return Collections.newSetFromMap(new IdentityHashMap<T,Boolean>());}
    private static BlockPos section(BlockPos pos){return new BlockPos(pos.getX()&~15,pos.getY()&~15,pos.getZ()&~15);}
    private static Method lookup(){
        for(Method method:ViewFrustum.class.getDeclaredMethods())if(method.getReturnType()==RenderChunk.class
                &&Arrays.equals(method.getParameterTypes(),new Class[]{BlockPos.class})){method.setAccessible(true);return method;}
        throw new IllegalStateException("Missing render chunk lookup");
    }
    private static final class Key {
        final TextureAtlasSprite sprite;
        final boolean rotated,single;
        final int depth;
        Key(IBlockState state,GlassPaneTextures.Selection material){
            sprite=material.sprite;single=material.singleSurface;
            rotated=state.getValue(BlockGlassWall.ROTATED);depth=state.getValue(BlockGlassWall.DEPTH);
        }
        @Override public int hashCode(){return ((System.identityHashCode(sprite)*3+depth)*2+(rotated?1:0))*2+(single?1:0);}
        @Override public boolean equals(Object value){if(!(value instanceof Key))return false;Key key=(Key)value;
            return sprite==key.sprite&&rotated==key.rotated&&single==key.single&&depth==key.depth;}
    }
    private static final class Entry {
        final TileEntityProgrammableGlass tile;
        final BlockPos pos;
        final Section section;
        GlassPaneTerrainMesh mesh;
        boolean wasNative;
        String reason="pending";
        Entry(TileEntityProgrammableGlass tile,Section section){this.tile=tile;pos=tile.getPos().toImmutable();this.section=section;}
    }

    /** Immutable input distinguishes production publications from isolated model fixtures. */
    static final class Snapshot {
        final GlassPaneTerrainMesh mesh;
        Snapshot(GlassPaneTerrainMesh mesh){this.mesh=mesh;}
    }

    /**
     * Ownership follows installed translucent records, never an inferred worker
     * identity. Temporary record indexes are built once per installed chunk or
     * requested revision; stable owners are just set lookups.
     */
    static final class Publication {
        private static final int HISTORY=8;
        private final Map<BlockPos,ArrayDeque<GlassPaneTerrainMesh>> history=new HashMap<>();
        private Map<BlockPos,GlassPaneTerrainMesh> wanted=Collections.emptyMap(),visibleMeshes=Collections.emptyMap();
        private Set<BlockPos> visible=Collections.emptySet();
        private CompiledChunk checked;
        private boolean pending,materialPending,rebuildNeeded,verified;
        private int visibleQuads;
        private String proof="not-checked";

        private void remember(BlockPos pos,GlassPaneTerrainMesh mesh){
            if(mesh==null)return;
            ArrayDeque<GlassPaneTerrainMesh> values=history.computeIfAbsent(pos,p->new ArrayDeque<>());
            values.remove(mesh);values.addLast(mesh);
            while(values.size()>HISTORY)values.removeFirst();
        }
        void request(Map<BlockPos,GlassPaneTerrainMesh> next){
            for(Map.Entry<BlockPos,GlassPaneTerrainMesh> entry:wanted.entrySet())remember(entry.getKey(),entry.getValue());
            for(Map.Entry<BlockPos,GlassPaneTerrainMesh> entry:next.entrySet())remember(entry.getKey(),entry.getValue());
            wanted=Collections.unmodifiableMap(new HashMap<>(next));checked=null;pending=true;rebuildNeeded=false;
        }
        boolean observe(CompiledChunk current,BlockPos origin){
            if(current==checked)return false;
            checked=current;
            if(history.isEmpty()&&wanted.isEmpty()&&visible.isEmpty()){
                verified=true;pending=materialPending=rebuildNeeded=false;proof="no-native-publication";return false;
            }
            if(current==CompiledChunk.DUMMY){
                boolean changed=!visible.isEmpty();visible=Collections.emptySet();visibleMeshes=Collections.emptyMap();
                visibleQuads=0;pending=!wanted.isEmpty();materialPending=rebuildNeeded=false;verified=true;proof="no-upload";return changed;
            }
            Map<BlockPos,List<GlassPaneTerrainMesh>> candidates=new HashMap<>();
            for(Map.Entry<BlockPos,ArrayDeque<GlassPaneTerrainMesh>> entry:history.entrySet())candidates.put(entry.getKey(),new ArrayList<>(entry.getValue()));
            for(Map.Entry<BlockPos,GlassPaneTerrainMesh> entry:visibleMeshes.entrySet()){
                List<GlassPaneTerrainMesh> values=candidates.computeIfAbsent(entry.getKey(),p->new ArrayList<>());
                if(!values.contains(entry.getValue()))values.add(entry.getValue());
            }
            GlassPaneTerrainProof.Result result=GlassPaneTerrainProof.inspect(current,origin,candidates,wanted);
            if(!result.verified){verified=false;pending=true;rebuildNeeded=false;proof="unavailable-native-records";return false;}
            verified=true;
            Set<BlockPos> previous=visible;
            Map<BlockPos,GlassPaneTerrainMesh> meshes=new HashMap<>(result.matched);
            for(BlockPos pos:result.physicalPresent)if(!meshes.containsKey(pos)){
                GlassPaneTerrainMesh mesh=visibleMeshes.get(pos);
                if(mesh==null)mesh=wanted.get(pos);
                if(mesh==null){List<GlassPaneTerrainMesh> known=candidates.get(pos);if(known!=null&&!known.isEmpty())mesh=known.get(known.size()-1);}
                if(mesh!=null)meshes.put(pos,mesh);
            }
            visible=Collections.unmodifiableSet(new HashSet<>(result.physicalPresent));
            visibleMeshes=Collections.unmodifiableMap(meshes);visibleQuads=0;
            for(GlassPaneTerrainMesh mesh:meshes.values())visibleQuads+=mesh.quads.size();
            pending=!result.wantedPhysicalPresent.equals(wanted.keySet())||!visible.equals(wanted.keySet());
            materialPending=!result.wantedPresent.equals(wanted.keySet());
            rebuildNeeded=pending;
            for(Map.Entry<BlockPos,GlassPaneTerrainMesh> entry:wanted.entrySet()){
                GlassPaneTerrainMesh actual=result.matched.get(entry.getKey());
                if(actual!=null&&actual!=entry.getValue()&&!result.wantedPresent.contains(entry.getKey()))rebuildNeeded=true;
            }
            proof=materialPending?"native-geometry-confirmed":"native-records-confirmed";
            return !previous.equals(visible);
        }
        void reset(){
            history.clear();wanted=visibleMeshes=Collections.emptyMap();visible=Collections.emptySet();checked=null;
            pending=materialPending=rebuildNeeded=verified=false;visibleQuads=0;proof="not-checked";
        }
        /** A fenced rebuild with no published pane snapshots cannot emit our native panes. */
        void withdrawn(CompiledChunk current){
            reset();checked=current;verified=true;proof="native-withdrawal-confirmed";
        }
        boolean known(BlockPos pos){return history.containsKey(pos)||wanted.containsKey(pos)||visible.contains(pos);}
        boolean absent(BlockPos pos){return verified&&!visible.contains(pos);}
        boolean unverified(){return !verified&&"unavailable-native-records".equals(proof);}
        boolean visible(BlockPos pos){return visible.contains(pos);}
        boolean pending(){return pending;}
        boolean materialPending(){return materialPending;}
        boolean rebuildNeeded(){return rebuildNeeded;}
        int owners(){return visible.size();}
        int quads(){return visibleQuads;}
        String proof(){return proof;}
    }

    /**
     * Records unavailable in a rendering extension never authorize ownership.
     * Withdraw snapshots, then require the actual replacement rebuild to finish
     * before a retained renderer is allowed to draw this section instead.
     */
    static final class Withdrawal {
        private CompiledChunk before,beforeTaskResult;
        private ChunkCompileTaskGenerator beforeTask;
        private boolean requested,complete;
        void request(CompiledChunk before){request(before,null);}
        void request(CompiledChunk before,ChunkCompileTaskGenerator beforeTask){
            this.before=before;this.beforeTask=beforeTask;beforeTaskResult=beforeTask==null?null:beforeTask.getCompiledChunk();requested=true;complete=false;
        }
        boolean observe(CompiledChunk current,ChunkCompileTaskGenerator task,boolean needsUpdate){
            if(!requested||complete||needsUpdate||current==null||current==CompiledChunk.DUMMY||current==before||current==beforeTaskResult
                    ||task==null||task==beforeTask||task.isFinished()||task.getCompiledChunk()!=current)return false;
            if(task.getType()==ChunkCompileTaskGenerator.Type.REBUILD_CHUNK&&task.getStatus()!=ChunkCompileTaskGenerator.Status.DONE)return false;
            // A camera resort reuses the exact installed CompiledChunk; it cannot
            // create the new identity above. Therefore a successor resort is a
            // receipt for the post-fence rebuild too. Excluding both old objects
            // prevents a pre-snapshot DONE-before-install race from qualifying.
            complete=true;return true;
        }
        boolean requested(){return requested;}
        boolean complete(){return complete;}
        void reset(){before=beforeTaskResult=null;beforeTask=null;requested=complete=false;}
    }

    private static final class Section {
        final BlockPos origin;
        final Set<Entry> entries=identities();
        final Set<TileEntityProgrammableGlass> frameWaiting=identities();
        final GlassPaneAdmissions admissions=new GlassPaneAdmissions();
        final Publication publication=new Publication();
        final Withdrawal withdrawal=new Withdrawal();
        volatile RenderChunk chunk;
        boolean dirty=true,admissionsDirty=true,nativeDisabled;
        CompiledChunk admitted,requestedFrom,retried;
        Section(BlockPos origin){this.origin=origin;}
    }

    static GlassPaneTerrainMesh mesh(Object value){return peekMesh(value);}
    public static boolean hasMesh(Object value){return peekMesh(value)!=null;}
    static GlassPaneTerrainMesh peekMesh(Object value){
        if(value instanceof GlassPaneTerrainMesh)return (GlassPaneTerrainMesh)value;
        GlassPaneTerrainMesh mesh=value instanceof Snapshot?((Snapshot)value).mesh:null;
        if(mesh!=null&&!mesh.format.equals(DefaultVertexFormats.BLOCK)){
            // Native writers can copy raw records. Never pass an old stride to
            // the new buffer; workers request only a client-thread format check.
            formatRefresh=true;return null;
        }
        return mesh;
    }

    /** Safe from loading callbacks or chunk workers: no neighbour reads or graphics calls. */
    public static void changed(TileEntityProgrammableGlass tile){
        World physical=tile.getWorld();while(physical instanceof VehicleWorld)physical=((VehicleWorld)physical).parent;
        if(physical==null||!physical.isRemote||tile.getWorld() instanceof VehicleWorld)return;
        synchronized(QUEUED){if(QUEUED.size()<MAX_QUEUED)QUEUED.add(tile);else rescan=true;}
    }
    static String unsupportedReason(TileEntityProgrammableGlass tile,IBlockState state){
        if(!GlassPanePolicy.terrainEnabled())return "terrain-disabled";
        if(WorldSceneBenchmark.omitGlazing())return "benchmark-no-glass";
        if(!GlassPaneTerrainProof.supports(DefaultVertexFormats.BLOCK)||!GlassPaneTerrainMesh.supports(DefaultVertexFormats.BLOCK))return "unsupported-vertex-layout";
        if(tile.getWorld() instanceof VehicleWorld)return "moving";
        if(!(state.getBlock() instanceof BlockProgrammableGlass))return "not-programmable-glass";
        if(tile.getMaterial()>=0)return "custom-material";
        if(tile.fluids.any())return "fluid";
        if(GlassMaterials.opaque(tile.getMaterial(),tile.getTint()))return "opaque";
        if(GlassSurfaceGroups.flatCorner(tile,state)!=null)return "corner";
        // Pane lighting is independent of the selected frame. Emissive borders
        // and joined cells with no frame geometry must not reject the pane.
        return "none";
    }
    static boolean supported(TileEntityProgrammableGlass tile,IBlockState state){return "none".equals(unsupportedReason(tile,state));}

    /** Retained fallback may draw only after every possible native owner is gone. */
    public static boolean fallbackSafe(TileEntityProgrammableGlass tile){
        if(tile.getWorld()!=world||world==null)return tile.paneTerrainMesh==null;
        Entry entry=tile.paneTerrainPublication instanceof Entry?(Entry)tile.paneTerrainPublication:null;
        Section section=entry!=null&&entry.tile==tile&&entry.pos.equals(tile.getPos())?entry.section:SECTIONS.get(section(tile.getPos()));
        if(section==null)return tile.paneTerrainMesh==null;
        observe(section);
        if(entry!=null&&entry.tile==tile&&entry.mesh!=null)return false;
        if(section.publication.visible(tile.getPos()))return false;
        if(section.nativeDisabled&&section.withdrawal.complete())return true;
        if(section.nativeDisabled&&section.publication.known(tile.getPos()))return false;
        if(entry!=null&&entry.wasNative)return section.publication.absent(tile.getPos());
        return !section.publication.known(tile.getPos());
    }
    public static String fallbackReason(TileEntityProgrammableGlass tile){
        Entry entry=tile.paneTerrainPublication instanceof Entry?(Entry)tile.paneTerrainPublication:null;
        if(entry==null||entry.tile!=tile)return "not-observed";
        if(entry.section.nativeDisabled)return entry.section.withdrawal.complete()?"native-records-unavailable":"withdrawing-native";
        if(entry.section.publication.visible(entry.pos))return "native";
        return entry.mesh==null?entry.reason:entry.section.publication.unverified()?"unverified-native":"native-pending";
    }
    /** Called when a retained group's anchor or member list changes. */
    static void refreshAdmissions(BlockPos pos){Section section=SECTIONS.get(section(pos));if(section!=null)section.admissionsDirty=true;}
    public static boolean claims(TileEntityProgrammableGlass tile){
        Object raw=tile.paneTerrainPublication;
        return raw instanceof Entry&&((Entry)raw).tile==tile&&((Entry)raw).pos.equals(tile.getPos())
                &&tile.getWorld()==world&&!tile.isInvalid()&&(((Entry)raw).mesh!=null||rendered(tile));
    }
    /** Check here too: uploads may finish after the frame-start admission sweep. */
    public static boolean rendered(TileEntityProgrammableGlass tile){
        if(tile.getWorld()!=world||tile.isInvalid()||world==null)return false;
        Entry entry=tile.paneTerrainPublication instanceof Entry?(Entry)tile.paneTerrainPublication:null;
        Section section=entry!=null&&entry.tile==tile&&entry.pos.equals(tile.getPos())?entry.section:SECTIONS.get(section(tile.getPos()));
        if(section==null||section.chunk==null||!section.chunk.getPosition().equals(section.origin))return false;
        observe(section);
        return section.publication.visible(tile.getPos());
    }
    /** -1 leaves existing frame/fluid work available; panes have no native TESR draw. */
    public static int renderPass(TileEntityProgrammableGlass tile,int pass){
        if(!rendered(tile))return -1;
        if(ProgrammableGlassPasses.frameFallbackInPass(tile,pass))return -1;
        if(pass==1)return tile.fluids.any()?-1:0;
        return 0;
    }
    private static boolean installed(TileEntity tile){return tile!=null&&world!=null&&tile.getWorld()==world
            &&!tile.isInvalid()&&world.isBlockLoaded(tile.getPos())&&world.getTileEntity(tile.getPos())==tile;}
    private static boolean suppress(TileEntity raw){
        if(!(raw instanceof TileEntityProgrammableGlass))return false;
        TileEntityProgrammableGlass tile=(TileEntityProgrammableGlass)raw;
        return GlassPaneRetained.suppress(tile)||rendered(tile)&&!tile.fluids.any()
                &&!ProgrammableGlassPasses.frameFallbackInPass(tile,0)&&!ProgrammableGlassPasses.frameFallbackInPass(tile,1);
    }

    private static void dirty(BlockPos pos){
        // Native chunk lighting needs no rebuild of identical local pane meshes.
        for(int y=-1;y<=1;y++)for(int z=-1;z<=1;z++)for(int x=-1;x<=1;x++){
            Entry entry=ENTRIES.get(LOOKUP_POS.setPos(pos.getX()+x,pos.getY()+y,pos.getZ()+z));
            if(entry!=null){DIRTY.add(entry);entry.section.admissionsDirty=true;}
        }
    }
    private static void remove(Entry entry){
        ENTRIES.remove(entry.pos,entry);DIRTY.remove(entry);entry.section.entries.remove(entry);
        entry.section.frameWaiting.remove(entry.tile);entry.section.dirty=true;entry.section.admissionsDirty=true;
        if(entry.tile.paneTerrainPublication==entry){entry.tile.paneTerrainPublication=null;entry.tile.paneTerrainMesh=null;}
        GlassPaneRetained.invalidateSection(entry.pos);
    }
    private static void reset(World next){
        if(world!=null&&listener!=null)world.removeEventListener(listener);
        for(Section section:SECTIONS.values())section.admissions.restore(GlassPaneTerrain::installed);
        for(Entry entry:ENTRIES.values())if(entry.tile.paneTerrainPublication==entry){entry.tile.paneTerrainPublication=null;entry.tile.paneTerrainMesh=null;}
        ENTRIES.clear();SECTIONS.clear();DIRTY.clear();MESHES.invalidateAll();world=next;frustum=null;format=null;
        synchronized(QUEUED){QUEUED.removeIf(tile->tile.getWorld()!=next);rescan=false;}
        if(next==null)return;
        listener=new WorldRenderListener(){
            @Override public void notifyBlockUpdate(World changed,BlockPos pos,IBlockState before,IBlockState after,int flags){if(changed==world)dirty(pos);}
        };
        next.addEventListener(listener);
        synchronized(QUEUED){rescan=true;}
    }
    public static void reload(){
        MESHES.invalidateAll();format=null;
        // Old visible terrain still owns its pane until a replacement upload.
        DIRTY.addAll(ENTRIES.values());
        for(Section section:SECTIONS.values()){
            section.admissionsDirty=true;section.nativeDisabled=false;section.withdrawal.reset();
            GlassPaneRetained.invalidateSection(section.origin);
        }
    }
    private static void observe(Section section){
        RenderChunk chunk=section.chunk;if(chunk==null||!chunk.getPosition().equals(section.origin))return;
        CompiledChunk current=chunk.getCompiledChunk();
        if(section.publication.observe(current,section.origin)){
            publications++;section.admissionsDirty=true;GlassPaneRetained.invalidateSection(section.origin);
        }
        if(current!=section.admitted)section.admissionsDirty=true;
        if(section.nativeDisabled){
            if(section.withdrawal.requested()&&!section.withdrawal.complete()&&chunk.getLockCompileTask().tryLock())try{
                ChunkCompileTaskGenerator task=(ChunkCompileTaskGenerator)TASK.get(chunk);
                if(task!=null&&task.getLock().tryLock())try{
                    if(section.withdrawal.observe(current,task,chunk.needsUpdate())){
                        section.publication.withdrawn(current);section.admissionsDirty=true;withdrawals++;
                        GlassPaneRetained.invalidateSection(section.origin);
                    }
                }finally{task.getLock().unlock();}
            }catch(IllegalAccessException failure){throw new IllegalStateException("Cannot inspect native pane withdrawal",failure);}
            finally{chunk.getLockCompileTask().unlock();}
            return;
        }
        // An extension may discard the CPU copy of a completed native buffer.
        // Do not spin rebuilding that same uninspectable path or claim guessed
        // ownership. Clear our snapshots and hand off after a fenced rebuild.
        if(section.publication.unverified()&&current!=section.requestedFrom&&current!=CompiledChunk.DUMMY&&!section.dirty){
            // Do not wait to catch a DONE task: camera resorts can replace that
            // receipt before a tick observes it. This only queues withdrawal;
            // request() still fences every running/uploading task before it
            // changes snapshots, and Withdrawal still proves the replacement.
            section.nativeDisabled=true;section.dirty=true;section.admissionsDirty=true;
            for(Entry entry:section.entries){entry.mesh=null;entry.reason="native-records-unavailable";DIRTY.remove(entry);}
            GlassPaneRetained.invalidateSection(section.origin);
            return;
        }
        if(section.publication.rebuildNeeded()&&current!=section.requestedFrom&&current!=section.retried
                &&current!=CompiledChunk.DUMMY&&!section.dirty&&!chunk.needsUpdate()){
            if(!chunk.getLockCompileTask().tryLock())return;
            try{
                ChunkCompileTaskGenerator task=(ChunkCompileTaskGenerator)TASK.get(chunk);
                if(task==null){section.dirty=true;section.retried=current;}
                else{if(!task.getLock().tryLock())return;try{
                    if(task.getStatus()==ChunkCompileTaskGenerator.Status.DONE){section.dirty=true;section.retried=current;}
                }finally{task.getLock().unlock();}}
            }catch(IllegalAccessException failure){throw new IllegalStateException("Cannot inspect native pane retry",failure);}
            finally{chunk.getLockCompileTask().unlock();}
        }
    }
    /** Caller holds the task lock. Let separately queued layer uploads finish. */
    static boolean finishBeforePublish(ChunkCompileTaskGenerator task){
        if(task==null)return true;
        if(task.getStatus()==ChunkCompileTaskGenerator.Status.UPLOADING)return false;
        if(task.getStatus()!=ChunkCompileTaskGenerator.Status.DONE)task.finish();
        return true;
    }
    private static void request(Section section,RenderChunk chunk)throws IllegalAccessException{
        section.chunk=chunk;
        boolean managed=section.publication.owners()>0||section.publication.pending();
        for(Entry entry:section.entries)if(entry.wasNative||entry.mesh!=null){managed=true;break;}
        if(!managed){section.dirty=false;return;}
        Map<BlockPos,GlassPaneTerrainMesh> panes=new HashMap<>();
        for(Entry entry:section.entries)if(entry.mesh!=null)panes.put(entry.pos,entry.mesh);
        // Chunk workers must not stall the render thread. Dirty publication
        // stays queued when either short task lock is currently occupied.
        if(!chunk.getLockCompileTask().tryLock())return;
        try{
            ChunkCompileTaskGenerator old=(ChunkCompileTaskGenerator)TASK.get(chunk);
            if(old!=null&&!old.getLock().tryLock())return;
            try{
                if(!finishBeforePublish(old))return;
                // No snapshot changes while an older task is UPLOADING. Individual
                // layer VBOs can upload before its final CompiledChunk is installed.
                for(Entry entry:section.entries){
                    if(entry.mesh!=null)entry.wasNative=true;
                    entry.tile.paneTerrainMesh=entry.wasNative?new Snapshot(entry.mesh):null;
                }
                section.requestedFrom=chunk.getCompiledChunk();
                section.publication.request(panes);
                if(section.nativeDisabled)section.withdrawal.request(section.requestedFrom,old);
                chunk.setNeedsUpdate(false);
            }finally{
                if(old!=null)old.getLock().unlock();
            }
        }finally{chunk.getLockCompileTask().unlock();}
        section.dirty=false;section.admissionsDirty=true;requests++;GlassPaneRetained.invalidateSection(section.origin);
    }

    private static void updateView(Minecraft mc)throws ReflectiveOperationException{
        ViewFrustum view=(ViewFrustum)FRUSTUM.get(mc.renderGlobal);
        if(view!=frustum){
            for(Section section:SECTIONS.values()){
                section.admissions.restore(GlassPaneTerrain::installed);section.publication.reset();
                section.chunk=null;section.admitted=null;section.requestedFrom=null;section.retried=null;
                section.withdrawal.reset();
                section.dirty=true;section.admissionsDirty=true;
            }
            frustum=view;
        }
        if(view==null)return;
        Iterator<Section> sections=SECTIONS.values().iterator();
        while(sections.hasNext()){
            Section section=sections.next();RenderChunk chunk=(RenderChunk)CHUNK_LOOKUP.invoke(view,section.origin);
            if(chunk==null||!chunk.getPosition().equals(section.origin)){
                if(section.chunk!=null){section.admissions.restore(GlassPaneTerrain::installed);section.publication.reset();section.withdrawal.reset();section.chunk=null;section.admitted=null;section.dirty=true;GlassPaneRetained.invalidateSection(section.origin);}
                if(section.entries.isEmpty())sections.remove();
                continue;
            }
            if(section.chunk!=chunk){
                section.admissions.restore(GlassPaneTerrain::installed);section.publication.reset();
                section.chunk=chunk;section.admitted=null;section.requestedFrom=null;section.retried=null;
                section.withdrawal.reset();GlassPaneRetained.invalidateSection(section.origin);
                section.dirty=true;section.admissionsDirty=true;
            }
            if(section.dirty)request(section,chunk);
            observe(section);
            Iterator<TileEntityProgrammableGlass> frames=section.frameWaiting.iterator();
            while(frames.hasNext()){
                TileEntityProgrammableGlass tile=frames.next();
                if(!installed(tile)||ProgrammableGlassPasses.emptyOpaquePass(tile,null)){frames.remove();section.admissionsDirty=true;}
            }
            if(section.admissionsDirty){
                CompiledChunk current=chunk.getCompiledChunk();
                section.admissions.prune(current,GlassPaneTerrain::suppress,GlassPaneTerrain::installed);
                section.admitted=current;section.admissionsDirty=false;admissionSweeps++;
                section.frameWaiting.clear();
                for(Entry entry:section.entries)if((section.publication.visible(entry.pos)||GlassPaneRetained.handles(entry.tile))
                        &&!entry.tile.fluids.any()&&GlassFrameChunks.awaiting(entry.tile)
                        &&!ProgrammableGlassPasses.emptyOpaquePass(entry.tile,null))section.frameWaiting.add(entry.tile);
            }
            if(section.entries.isEmpty()&&(chunk.getCompiledChunk()==CompiledChunk.DUMMY
                    ||!section.publication.pending()&&section.publication.owners()==0)){
                section.admissions.restore(GlassPaneTerrain::installed);sections.remove();
            }
        }
    }
    @SubscribeEvent public static void unloaded(net.minecraftforge.event.world.WorldEvent.Unload event){if(event.getWorld()==world)reset(null);}
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event){if(event.phase==TickEvent.Phase.END)flush();}
    @SubscribeEvent public static void renderTick(TickEvent.RenderTickEvent event){if(event.phase==TickEvent.Phase.START)flush();}

    static void flush(){
        Minecraft mc=Minecraft.getMinecraft();if(mc.world!=world)reset(mc.world);
        if(world==null){synchronized(QUEUED){QUEUED.clear();}GlassPaneRetained.flush();return;}
        boolean active=GlassPanePolicy.terrainEnabled()&&!WorldSceneBenchmark.omitGlazing();
        if(active!=terrainActive){terrainActive=active;DIRTY.addAll(ENTRIES.values());}
        // Even explicit retained-group mode needs ownership/admission tracking.
        // A mode change withdraws snapshots through the same upload fence rather
        // than forgetting already uploaded native panes and drawing over them.
        if(formatRefresh||format==null||!format.equals(DefaultVertexFormats.BLOCK)){
            formatRefresh=false;
            // Old worker snapshots may ask while the bounded queue is already
            // progressing. Do not clear its cache or restart all pending owners.
            if(format==null||!format.equals(DefaultVertexFormats.BLOCK)){
                reload();format=new VertexFormat(DefaultVertexFormats.BLOCK);
            }
        }
        Set<TileEntityProgrammableGlass> queued=Collections.emptySet();
        synchronized(QUEUED){
            if(!QUEUED.isEmpty()||rescan){
                queued=identities();queued.addAll(QUEUED);QUEUED.clear();
                if(rescan){for(TileEntity tile:world.loadedTileEntityList)if(tile instanceof TileEntityProgrammableGlass)queued.add((TileEntityProgrammableGlass)tile);rescan=false;}
            }
        }
        for(TileEntityProgrammableGlass tile:queued){
            Object publication=tile.paneTerrainPublication;
            if(publication instanceof Entry){Entry old=(Entry)publication;if(!installed(tile)||!old.pos.equals(tile.getPos()))remove(old);}
            if(!installed(tile))continue;
            BlockPos pos=tile.getPos().toImmutable();Entry entry=ENTRIES.get(pos);
            if(entry!=null&&entry.tile!=tile){remove(entry);entry=null;}
            if(entry==null){Section section=SECTIONS.computeIfAbsent(section(pos),Section::new);
                entry=new Entry(tile,section);ENTRIES.put(pos,entry);section.entries.add(entry);tile.paneTerrainPublication=entry;}
            dirty(pos);
        }
        fallback=!active?(WorldSceneBenchmark.omitGlazing()?"benchmark-no-glass":"terrain-disabled")
                :GlassPaneTerrainProof.supports(DefaultVertexFormats.BLOCK)&&GlassPaneTerrainMesh.supports(DefaultVertexFormats.BLOCK)?"none":"unsupported-vertex-layout";
        int processed=0;
        while(!DIRTY.isEmpty()&&processed++<BUILD_OWNERS){
            Iterator<Entry> iterator=DIRTY.iterator();Entry entry=iterator.next();iterator.remove();
            if(!installed(entry.tile)||!entry.pos.equals(entry.tile.getPos())){remove(entry);continue;}
            IBlockState state=world.getBlockState(entry.pos);GlassPaneTerrainMesh mesh=null;
            entry.reason=entry.section.nativeDisabled?"native-records-unavailable":unsupportedReason(entry.tile,state);
            if("none".equals(entry.reason)){
                GlassPaneTextures.Selection material=GlassPaneTextures.select(entry.tile.getShade(),entry.tile.getTint());
                if(material==null){entry.reason=fallback="atlas-not-ready";synchronized(QUEUED){QUEUED.add(entry.tile);}}
                else if(!material.terrainAlphaSafe)entry.reason="native-alpha-cutoff";
                else{
                    Key key=new Key(state,material);mesh=MESHES.getIfPresent(key);
                    if(mesh==null){mesh=GlassPaneTerrainMesh.build(key.rotated,key.depth,key.sprite,key.single,format);MESHES.put(key,mesh);builds++;}
                    else reuses++;
                    entry.reason="none";
                }
            }
            if(entry.mesh!=mesh){entry.mesh=mesh;entry.section.dirty=true;entry.section.admissionsDirty=true;GlassPaneRetained.invalidateSection(entry.pos);}
        }
        // Retire incompatible groups before publishing native snapshots, and
        // establish replacement anchors before the compiled lists are pruned.
        GlassPaneRetained.flush();
        try{updateView(mc);}catch(ReflectiveOperationException failure){throw new IllegalStateException("Native glass pane publication failed",failure);}
    }

    public static String diagnostics(){
        int ready=0,quads=0,pruned=0,pending=0,materials=0,frames=0,alphaFallback=0,unsupported=0,unverified=0,withdrawing=0,withdrawn=0,emissive=0;long bytes=0;
        Map<String,Integer> reasons=new TreeMap<>();
        for(Section section:SECTIONS.values()){
            ready+=section.publication.owners();quads+=section.publication.quads();pruned+=section.admissions.removedCount();
            if(section.dirty||section.publication.pending())pending++;frames+=section.frameWaiting.size();
            if(section.publication.materialPending())materials++;
            if("unavailable-native-records".equals(section.publication.proof()))unverified++;
            if(section.nativeDisabled){if(section.withdrawal.complete())withdrawn++;else withdrawing++;}
        }
        for(GlassPaneTerrainMesh mesh:MESHES.asMap().values())bytes+=mesh.bytes();
        for(Entry entry:ENTRIES.values()){
            if("native-alpha-cutoff".equals(entry.reason))alphaFallback++;
            else if(!"none".equals(entry.reason))unsupported++;
            String reason=fallbackReason(entry.tile);reasons.put(reason,reasons.getOrDefault(reason,0)+1);
            if(sectionVisible(entry)&&entry.tile.isFramed()&&entry.tile.frameAppearance.light()>0)emissive++;
        }
        return "terrain-pane-owners="+ready+" quads="+quads+" pruned-tiles="+pruned+" sections="+SECTIONS.size()
                +" pending-sections="+pending+" frame-fallbacks="+frames+" mesh-bytes="+bytes+" builds="+builds
                +" reused="+reuses+" rebuild-requests="+requests+" publications="+publications+" admission-sweeps="+admissionSweeps
                +" alpha-fallback-owners="+alphaFallback+" unsupported-owners="+unsupported
                +" material-pending-sections="+materials+" unverified-sections="+unverified
                +" withdrawing-sections="+withdrawing+" retained-safe-sections="+withdrawn+" completed-withdrawals="+withdrawals
                +" emissive-frame-pane-owners="+emissive+" owner-reasons="+reasons+" fallback="+fallback;
    }
    private static boolean sectionVisible(Entry entry){return entry.section.publication.visible(entry.pos);}
    private GlassPaneTerrain(){}
}
