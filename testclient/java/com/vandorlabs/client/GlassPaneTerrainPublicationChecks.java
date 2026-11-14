package com.vandorlabs.client;

import java.lang.reflect.Field;
import java.util.*;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.chunk.ChunkCompileTaskGenerator;
import net.minecraft.client.renderer.chunk.CompiledChunk;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.client.renderer.vertex.VertexFormat;
import net.minecraft.client.renderer.vertex.VertexFormatElement;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.BlockRenderLayer;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.fml.relauncher.CoreModManager;

/** Installed-buffer lifetime and exact compiled tile-list ownership without a graphics context. */
public final class GlassPaneTerrainPublicationChecks {
    private static int checks;
    private static final BlockPos ORIGIN=new BlockPos(0,64,0),A=new BlockPos(1,64,1),B=new BlockPos(2,64,1);
    private static final BufferBuilder HOLDER=new BufferBuilder(256);
    private static void require(boolean condition,String message){checks++;if(!condition)throw new AssertionError(message);}
    private static final class Sprite extends TextureAtlasSprite {
        Sprite(String name,int x){super("vandorlabs:checks/terrain-publication-"+name);setIconWidth(16);setIconHeight(16);initSprite(512,512,x,96,false);}
    }
    private static final class Chunk extends CompiledChunk {
        Chunk(BufferBuilder.State state){setState(state);if(state!=null)setLayerUsed(BlockRenderLayer.TRANSLUCENT);}
    }
    private static Map<BlockPos,GlassPaneTerrainMesh> panes(Object... values){
        Map<BlockPos,GlassPaneTerrainMesh> result=new LinkedHashMap<>();
        for(int i=0;i<values.length;i+=2)result.put((BlockPos)values[i],(GlassPaneTerrainMesh)values[i+1]);return result;
    }
    private static CompiledChunk chunk(Map<BlockPos,GlassPaneTerrainMesh> panes){
        if(panes.isEmpty())return new Chunk(null);
        VertexFormat format=panes.values().iterator().next().format;int size=0;
        for(GlassPaneTerrainMesh mesh:panes.values())for(BakedQuad quad:mesh.quads)size+=quad.getVertexData().length;
        int[] data=new int[size];int at=0,stride=format.getIntegerSize();
        for(Map.Entry<BlockPos,GlassPaneTerrainMesh> pane:panes.entrySet())for(BakedQuad quad:pane.getValue().quads){
            int[] part=quad.getVertexData().clone();
            for(int vertex=0;vertex<4;vertex++){
                int p=vertex*stride;
                part[p]=Float.floatToRawIntBits(Float.intBitsToFloat(part[p])+pane.getKey().getX()-ORIGIN.getX());
                part[p+1]=Float.floatToRawIntBits(Float.intBitsToFloat(part[p+1])+pane.getKey().getY()-ORIGIN.getY());
                part[p+2]=Float.floatToRawIntBits(Float.intBitsToFloat(part[p+2])+pane.getKey().getZ()-ORIGIN.getZ());
            }
            System.arraycopy(part,0,data,at,part.length);at+=part.length;
        }
        return new Chunk(HOLDER.new State(data,format));
    }
    private static void publication(){
        GlassPaneTerrainMesh gold=GlassPaneTerrainMesh.build(false,0,new Sprite("gold",32),true,DefaultVertexFormats.BLOCK);
        GlassPaneTerrainMesh blue=GlassPaneTerrainMesh.build(false,0,new Sprite("blue",64),true,DefaultVertexFormats.BLOCK);
        GlassPaneTerrainMesh offset=GlassPaneTerrainMesh.build(false,1,gold.sprite,true,DefaultVertexFormats.BLOCK);
        GlassPaneTerrain.Publication publication=new GlassPaneTerrain.Publication();
        publication.request(panes(A,gold,B,gold));
        CompiledChunk old=chunk(Collections.emptyMap());
        require(!publication.observe(old,ORIGIN)&&!publication.visible(A)&&publication.pending(),"unbuilt pane suppressed fallback");
        CompiledChunk noPane=chunk(Collections.emptyMap());
        ChunkCompileTaskGenerator wrongWorker=new ChunkCompileTaskGenerator(null,ChunkCompileTaskGenerator.Type.REBUILD_CHUNK,0);
        wrongWorker.setCompiledChunk(noPane);wrongWorker.setStatus(ChunkCompileTaskGenerator.Status.DONE);
        require(!publication.observe(wrongWorker.getCompiledChunk(),ORIGIN)&&!publication.visible(A),"DONE ticket replaced actual content proof");

        CompiledChunk partial=chunk(panes(A,gold));
        require(publication.observe(partial,ORIGIN)&&publication.visible(A)&&!publication.visible(B),"partial upload was all or nothing");
        require(publication.pending()&&publication.rebuildNeeded()&&publication.owners()==1&&publication.quads()==2,"partial ownership counts incorrect");
        CompiledChunk complete=chunk(panes(A,gold,B,gold));
        require(publication.observe(complete,ORIGIN)&&publication.visible(B)&&!publication.pending(),"complete native content not promoted");
        require(!publication.materialPending()&&!publication.rebuildNeeded()&&publication.owners()==2&&publication.quads()==4,"complete material proof incorrect");
        require(!publication.observe(complete,ORIGIN)&&publication.visible(A),"unchanged upload republished ownership");
        complete.setState(chunk(panes(B,gold,A,gold)).getState());
        require(!publication.observe(complete,ORIGIN)&&publication.owners()==2,"transparency resort retired the same geometry");

        publication.request(panes(A,blue,B,gold));publication.observe(complete,ORIGIN);
        require(publication.visible(A)&&!publication.pending()&&publication.materialPending(),"old tint lost geometry ownership");
        require(publication.rebuildNeeded(),"known previous material never requested a repair");
        CompiledChunk recolored=chunk(panes(A,blue,B,gold));publication.observe(recolored,ORIGIN);
        require(!publication.materialPending()&&!publication.rebuildNeeded()&&publication.visible(A),"new material never settled");

        publication.request(panes(A,offset,B,gold));publication.observe(recolored,ORIGIN);
        require(publication.visible(A)&&publication.pending(),"depth edit drew fallback over old native pane");
        CompiledChunk moved=chunk(panes(A,offset,B,gold));publication.observe(moved,ORIGIN);
        require(!publication.pending()&&!publication.materialPending(),"new depth did not settle");

        publication.request(panes(A,offset));publication.observe(moved,ORIGIN);
        require(publication.visible(B)&&publication.pending(),"unsupported replacement overlapped old native geometry");
        CompiledChunk removedB=chunk(panes(A,offset));publication.observe(removedB,ORIGIN);
        require(!publication.visible(B)&&publication.visible(A)&&!publication.pending(),"unsupported replacement remained suppressed after removal");
        publication.request(Collections.emptyMap());publication.observe(removedB,ORIGIN);
        require(publication.visible(A)&&publication.pending(),"last-owner removal retired ownership before upload");
        publication.observe(chunk(Collections.emptyMap()),ORIGIN);
        require(!publication.visible(A)&&publication.owners()==0&&!publication.pending(),"last-owner removal suppressed replacement forever");
        publication.observe(moved,ORIGIN);
        require(publication.visible(A)&&publication.visible(B)&&publication.pending()&&publication.rebuildNeeded(),"late stale geometry was not owned and repaired");
        publication.observe(chunk(Collections.emptyMap()),ORIGIN);
        require(publication.owners()==0&&!publication.pending(),"replacement empty upload did not retire old content");

        publication.request(panes(A,gold));
        CompiledChunk localUv=chunk(panes(A,gold));int[] raw=localUv.getState().getRawBuffer().clone();
        int stride=gold.format.getIntegerSize(),uv=gold.format.getUvOffsetById(0)/4;
        for(int at=0;at<raw.length;at+=stride){raw[at+uv]=Float.floatToRawIntBits(.25F);raw[at+uv+1]=Float.floatToRawIntBits(.75F);}
        publication.observe(new Chunk(HOLDER.new State(raw,gold.format)),ORIGIN);
        require(publication.visible(A)&&publication.materialPending()&&!publication.pending(),"unknown UV lost physical ownership");
        require(!publication.rebuildNeeded(),"unknown material coordinates caused continuous rebuild requests");
        publication.observe(CompiledChunk.DUMMY,ORIGIN);
        require(!publication.visible(A)&&publication.pending(),"recycled empty section retained ownership");
        publication.reset();
        require(publication.owners()==0&&!publication.pending()&&!publication.materialPending(),"world reset retained publication");
    }
    private static void formatGuard(){
        GlassPaneTerrainMesh nativeMesh=GlassPaneTerrainMesh.build(false,0,new Sprite("format",128),true,DefaultVertexFormats.BLOCK);
        GlassPaneTerrain.Snapshot current=new GlassPaneTerrain.Snapshot(nativeMesh);
        require(GlassPaneTerrain.mesh(current)==nativeMesh&&GlassPaneTerrain.hasMesh(current),"matching format rejected");
        VertexFormat other=new VertexFormat(DefaultVertexFormats.BLOCK).addElement(
                new VertexFormatElement(0,VertexFormatElement.EnumType.FLOAT,VertexFormatElement.EnumUsage.PADDING,1));
        GlassPaneTerrainMesh old=GlassPaneTerrainMesh.build(false,0,nativeMesh.sprite,true,other);
        GlassPaneTerrain.Snapshot stale=new GlassPaneTerrain.Snapshot(old);
        require(GlassPaneTerrain.mesh(stale)==null&&!GlassPaneTerrain.hasMesh(stale),"stale stride reached the chunk buffer");
        require(GlassPaneTerrain.peekMesh(stale)==null,"damage preview consumed stale stride");
        require(GlassPaneTerrain.mesh(old)==old,"isolated raw fixture depended on active format");
        require(!GlassPaneTerrain.hasMesh(new GlassPaneTerrain.Snapshot(null)),"retired snapshot retained geometry");
    }
    private static void unavailableRecords(){
        GlassPaneTerrainMesh gold=GlassPaneTerrainMesh.build(false,0,new Sprite("unavailable",160),true,DefaultVertexFormats.BLOCK);
        GlassPaneTerrain.Publication publication=new GlassPaneTerrain.Publication();
        CompiledChunk unknown=chunk(panes(A,gold));unknown.setState(null);
        require(!publication.observe(unknown,ORIGIN)&&publication.absent(A),"never-published panes required unrelated native records");
        publication.request(panes(A,gold));publication.observe(chunk(panes(A,gold)),ORIGIN);
        require(publication.visible(A),"confirmed pane fixture missing");
        require(!publication.observe(unknown,ORIGIN)&&publication.unverified()&&publication.visible(A),"unknown buffer discarded known native ownership");
        require(!publication.rebuildNeeded(),"uninspectable records caused native rebuild spin");
        require(!publication.absent(A),"unknown buffer authorized duplicate retained fallback");
        publication.withdrawn(unknown);
        require(publication.absent(A)&&!publication.visible(A)&&!publication.pending()&&!publication.known(A),"confirmed native withdrawal retained stale ownership");

        GlassPaneTerrain.Withdrawal withdrawal=new GlassPaneTerrain.Withdrawal();
        CompiledChunk before=chunk(panes(A,gold)),after=chunk(Collections.emptyMap());
        ChunkCompileTaskGenerator task=new ChunkCompileTaskGenerator(null,ChunkCompileTaskGenerator.Type.REBUILD_CHUNK,0);
        task.setCompiledChunk(after);task.setStatus(ChunkCompileTaskGenerator.Status.DONE);
        require(!withdrawal.observe(after,task,false),"unrequested rebuild authorized fallback");
        withdrawal.request(before);
        require(!withdrawal.observe(before,task,false),"old upload authorized fallback");
        require(!withdrawal.observe(CompiledChunk.DUMMY,task,false),"dummy upload authorized fallback");
        require(!withdrawal.observe(after,task,true),"still-dirty section authorized fallback");
        task.setStatus(ChunkCompileTaskGenerator.Status.UPLOADING);
        require(!withdrawal.observe(after,task,false),"partly uploaded withdrawal authorized fallback");
        task.setStatus(ChunkCompileTaskGenerator.Status.DONE);task.setCompiledChunk(new CompiledChunk());
        require(!withdrawal.observe(after,task,false),"wrong completed upload authorized fallback");
        ChunkCompileTaskGenerator resort=new ChunkCompileTaskGenerator(null,ChunkCompileTaskGenerator.Type.RESORT_TRANSPARENCY,0);
        resort.setCompiledChunk(before);resort.setStatus(ChunkCompileTaskGenerator.Status.DONE);
        require(!withdrawal.observe(before,resort,false),"resorting old geometry was mistaken for native removal");
        task.setCompiledChunk(after);
        withdrawal.request(before,task);
        require(!withdrawal.observe(after,task,false),"pre-snapshot task was mistaken for the withdrawal rebuild");
        resort.setCompiledChunk(after);
        require(!withdrawal.observe(after,resort,false),"resort of a late pre-snapshot result authorized fallback");
        CompiledChunk replacement=chunk(Collections.emptyMap());
        task=new ChunkCompileTaskGenerator(null,ChunkCompileTaskGenerator.Type.REBUILD_CHUNK,0);task.setCompiledChunk(replacement);task.setStatus(ChunkCompileTaskGenerator.Status.DONE);task.finish();
        require(!withdrawal.observe(replacement,task,false),"canceled DONE task authorized fallback");
        task=new ChunkCompileTaskGenerator(null,ChunkCompileTaskGenerator.Type.REBUILD_CHUNK,0);task.setCompiledChunk(replacement);task.setStatus(ChunkCompileTaskGenerator.Status.DONE);
        require(withdrawal.observe(replacement,task,false)&&withdrawal.complete(),"actual replacement rebuild never released fallback");
        require(!withdrawal.observe(replacement,task,false),"settled withdrawal was republished");
        withdrawal.reset();require(!withdrawal.complete()&&!withdrawal.requested(),"world reset retained completed withdrawal");
        withdrawal.request(before);resort.setCompiledChunk(replacement);resort.setStatus(ChunkCompileTaskGenerator.Status.PENDING);
        require(withdrawal.observe(replacement,resort,false)&&withdrawal.complete(),"camera resort hid an already installed replacement rebuild");
    }

    private static Field field(Class<?> type,String name)throws Exception{Field value=type.getDeclaredField(name);value.setAccessible(true);return value;}
    @SuppressWarnings("unchecked") private static void fencedWithdrawal()throws Exception{
        // Exercise the real manager's request/observe methods and native task
        // locks. Only RenderChunk's constructor (which allocates GL resources)
        // is bypassed; publication and fallback eligibility are production code.
        sun.misc.Unsafe unsafe=(sun.misc.Unsafe)field(sun.misc.Unsafe.class,"theUnsafe").get(null);
        net.minecraft.client.renderer.chunk.RenderChunk chunk=(net.minecraft.client.renderer.chunk.RenderChunk)unsafe.allocateInstance(net.minecraft.client.renderer.chunk.RenderChunk.class);
        Class<?> renderType=net.minecraft.client.renderer.chunk.RenderChunk.class;
        field(renderType,"position").set(chunk,new BlockPos.MutableBlockPos(ORIGIN.getX(),ORIGIN.getY(),ORIGIN.getZ()));
        field(renderType,"lockCompileTask").set(chunk,new java.util.concurrent.locks.ReentrantLock());
        field(renderType,"lockCompiledChunk").set(chunk,new java.util.concurrent.locks.ReentrantLock());
        NonRenderingChecks.MemoryWorld world=new NonRenderingChecks.MemoryWorld(true);
        com.vandorlabs.tiles.TileEntityProgrammableGlass tile=new com.vandorlabs.tiles.TileEntityProgrammableGlass();tile.setWorld(world);tile.setPos(A);world.tiles.put(A,tile);
        com.vandorlabs.blocks.BlockProgrammableGlass block=new com.vandorlabs.blocks.BlockProgrammableGlass("native_withdrawal_checks");world.states.put(A,block.getDefaultState());
        GlassPaneTerrainMesh gold=GlassPaneTerrainMesh.build(false,0,new Sprite("fenced",192),true,DefaultVertexFormats.BLOCK);
        CompiledChunk before=chunk(panes(A,gold));chunk.compiledChunk=before;
        Class<?> sectionType=Class.forName("com.vandorlabs.client.GlassPaneTerrain$Section");
        java.lang.reflect.Constructor<?> sectionConstructor=sectionType.getDeclaredConstructor(BlockPos.class);sectionConstructor.setAccessible(true);
        Object section=sectionConstructor.newInstance(ORIGIN);field(sectionType,"chunk").set(section,chunk);
        field(sectionType,"dirty").setBoolean(section,false);field(sectionType,"requestedFrom").set(section,before);
        Class<?> entryType=Class.forName("com.vandorlabs.client.GlassPaneTerrain$Entry");
        java.lang.reflect.Constructor<?> entryConstructor=entryType.getDeclaredConstructor(com.vandorlabs.tiles.TileEntityProgrammableGlass.class,sectionType);entryConstructor.setAccessible(true);
        Object entry=entryConstructor.newInstance(tile,section);field(entryType,"wasNative").setBoolean(entry,true);field(entryType,"mesh").set(entry,gold);
        ((Set<Object>)field(sectionType,"entries").get(section)).add(entry);
        tile.paneTerrainPublication=entry;GlassPaneTerrain.Snapshot original=new GlassPaneTerrain.Snapshot(gold);tile.paneTerrainMesh=original;
        GlassPaneTerrain.Publication publication=(GlassPaneTerrain.Publication)field(sectionType,"publication").get(section);
        publication.request(panes(A,gold));publication.observe(before,ORIGIN);
        java.lang.reflect.Method request=GlassPaneTerrain.class.getDeclaredMethod("request",sectionType,renderType);request.setAccessible(true);
        java.lang.reflect.Method observe=GlassPaneTerrain.class.getDeclaredMethod("observe",sectionType);observe.setAccessible(true);
        Field activeWorld=field(GlassPaneTerrain.class,"world");Object previousWorld=activeWorld.get(null);activeWorld.set(null,world);
        Map<BlockPos,Object> sections=(Map<BlockPos,Object>)field(GlassPaneTerrain.class,"SECTIONS").get(null);
        Object previousSection=sections.put(ORIGIN,section);
        try{
            CompiledChunk unavailable=chunk(panes(A,gold));unavailable.setState(null);chunk.compiledChunk=unavailable;
            ChunkCompileTaskGenerator task=new ChunkCompileTaskGenerator(chunk,ChunkCompileTaskGenerator.Type.REBUILD_CHUNK,0);
            task.setCompiledChunk(unavailable);task.setStatus(ChunkCompileTaskGenerator.Status.UPLOADING);field(renderType,"compileTask").set(chunk,task);
            observe.invoke(null,section);
            require(field(sectionType,"nativeDisabled").getBoolean(section)&&field(entryType,"mesh").get(entry)==null,
                    "unavailable installed records stayed in native limbo behind an active successor task");
            request.invoke(null,section,chunk);
            require(tile.paneTerrainMesh==original,"withdrawal changed snapshots during a partial native upload");
            require(!GlassPaneTerrain.fallbackSafe(tile),"retained fallback overlapped an uploading native pane");
            CompiledChunk late=chunk(panes(A,gold));late.setState(null);
            task.setStatus(ChunkCompileTaskGenerator.Status.DONE);task.setCompiledChunk(late);request.invoke(null,section,chunk);
            require(tile.paneTerrainMesh!=original&&!GlassPaneTerrain.hasMesh(tile.paneTerrainMesh),"withdrawal failed to remove future native emissions");
            require(chunk.needsUpdate()&&!GlassPaneTerrain.fallbackSafe(tile),"snapshot removal skipped native upload fence");
            // Vanilla marks a task DONE before it installs its CompiledChunk.
            // Even that narrow pre-snapshot task race must not acknowledge this
            // withdrawal merely because its late result has a new identity.
            chunk.compiledChunk=late;chunk.clearNeedsUpdate();
            observe.invoke(null,section);
            require(!((GlassPaneTerrain.Withdrawal)field(sectionType,"withdrawal").get(section)).complete()
                    &&!GlassPaneTerrain.fallbackSafe(tile),"late pre-snapshot completion released retained fallback");
            ChunkCompileTaskGenerator lateResort=new ChunkCompileTaskGenerator(chunk,ChunkCompileTaskGenerator.Type.RESORT_TRANSPARENCY,0);
            lateResort.setCompiledChunk(late);lateResort.setStatus(ChunkCompileTaskGenerator.Status.DONE);field(renderType,"compileTask").set(chunk,lateResort);
            require(!GlassPaneTerrain.fallbackSafe(tile),"resorting late pre-snapshot geometry released retained fallback");
            CompiledChunk after=chunk(panes(B,gold));after.setState(null); // Unrelated translucent terrain with no CPU records.
            task=new ChunkCompileTaskGenerator(chunk,ChunkCompileTaskGenerator.Type.REBUILD_CHUNK,0);task.setCompiledChunk(after);task.setStatus(ChunkCompileTaskGenerator.Status.UPLOADING);
            field(renderType,"compileTask").set(chunk,task);chunk.compiledChunk=after;chunk.clearNeedsUpdate();observe.invoke(null,section);
            require(!GlassPaneTerrain.fallbackSafe(tile),"retained fallback entered a partial removal upload");
            task.setStatus(ChunkCompileTaskGenerator.Status.DONE);
            ChunkCompileTaskGenerator successor=new ChunkCompileTaskGenerator(chunk,ChunkCompileTaskGenerator.Type.RESORT_TRANSPARENCY,0);successor.setCompiledChunk(after);
            field(renderType,"compileTask").set(chunk,successor);observe.invoke(null,section);
            require(GlassPaneTerrain.fallbackSafe(tile)&&!GlassPaneTerrain.rendered(tile),"fenced removal never handed off to retained rendering");
            require(publication.owners()==0&&!publication.pending(),"native withdrawal remained in ownership limbo");
            long requests=field(GlassPaneTerrain.class,"requests").getLong(null);
            for(int i=0;i<100;i++)require(GlassPaneTerrain.fallbackSafe(tile),"settled fallback lost its native-free proof");
            require(field(GlassPaneTerrain.class,"requests").getLong(null)==requests,"settled fallback continually rebuilt native terrain");
        }finally{
            if(previousSection==null)sections.remove(ORIGIN);else sections.put(ORIGIN,previousSection);
            activeWorld.set(null,previousWorld);tile.paneTerrainPublication=null;tile.paneTerrainMesh=null;
        }
    }
    private static void uploadFence(){
        ChunkCompileTaskGenerator task=new ChunkCompileTaskGenerator(null,ChunkCompileTaskGenerator.Type.REBUILD_CHUNK,0);
        final boolean[] canceled={false};task.addFinishRunnable(()->canceled[0]=true);
        task.setStatus(ChunkCompileTaskGenerator.Status.UPLOADING);task.getLock().lock();
        try{require(!GlassPaneTerrain.finishBeforePublish(task),"partly uploaded section was canceled");}
        finally{task.getLock().unlock();}
        require(!canceled[0]&&!task.isFinished()&&task.getStatus()==ChunkCompileTaskGenerator.Status.UPLOADING,"deferred publication changed native upload task");
        task.setStatus(ChunkCompileTaskGenerator.Status.DONE);task.getLock().lock();
        try{require(GlassPaneTerrain.finishBeforePublish(task),"completed upload blocked replacement");}
        finally{task.getLock().unlock();}
        require(!canceled[0]&&!task.isFinished(),"DONE callback was canceled after upload");
        require(GlassPaneTerrain.finishBeforePublish(null),"empty task blocked first publication");
    }
    private static void allOwnerAdmissions(){
        TileEntity anchor=new TileEntity(){},member=new TileEntity(){},frame=new TileEntity(){},foreign=new TileEntity(){};
        Set<TileEntity> installed=Collections.newSetFromMap(new IdentityHashMap<>());installed.addAll(Arrays.asList(anchor,member,frame,foreign));
        Set<TileEntity> ready=Collections.newSetFromMap(new IdentityHashMap<>());
        CompiledChunk chunk=new CompiledChunk();chunk.getTileEntities().addAll(Arrays.asList(anchor,member,frame,foreign));
        GlassPaneAdmissions admissions=new GlassPaneAdmissions();admissions.prune(chunk,ready::contains,installed::contains);
        require(chunk.getTileEntities().size()==4,"pending terrain hid fallback tiles");
        ready.add(anchor);ready.add(member);admissions.prune(chunk,ready::contains,installed::contains);
        require(admissions.removedCount()==2&&!chunk.getTileEntities().contains(anchor),"terrain retained an anchor");
        require(chunk.getTileEntities().contains(frame)&&chunk.getTileEntities().contains(foreign),"terrain removed necessary or foreign fallback");
        ready.add(frame);admissions.prune(chunk,ready::contains,installed::contains);
        require(admissions.removedCount()==3&&chunk.getTileEntities().equals(Collections.singletonList(foreign)),"settled frame owner still reached TESR dispatch");
        CompiledChunk replacement=new CompiledChunk();replacement.getTileEntities().addAll(Arrays.asList(anchor,member,frame,foreign));
        admissions.prune(replacement,ready::contains,installed::contains);
        require(chunk.getTileEntities().size()==4&&replacement.getTileEntities().size()==1,"new chunk did not own exact list removals");
        ready.clear();admissions.prune(replacement,ready::contains,installed::contains);
        require(replacement.getTileEntities().size()==4&&admissions.removedCount()==0,"unsupported transition did not restore fallback");
        ready.add(anchor);ready.add(member);admissions.prune(replacement,ready::contains,installed::contains);
        installed.remove(member);admissions.restore(installed::contains);
        require(replacement.getTileEntities().contains(anchor)&&!replacement.getTileEntities().contains(member),"teardown restored a removed tile identity");
        admissions.prune(CompiledChunk.DUMMY,ready::contains,installed::contains);
        require(admissions.removedCount()==0,"dummy chunk retained removals");
    }
    public static void main(String[] args)throws Exception{
        net.minecraft.init.Bootstrap.register();
        Field environment=CoreModManager.class.getDeclaredField("deobfuscatedEnvironment");environment.setAccessible(true);
        boolean before=environment.getBoolean(null);
        try{environment.setBoolean(null,true);publication();formatGuard();uploadFence();unavailableRecords();fencedWithdrawal();allOwnerAdmissions();}
        finally{environment.setBoolean(null,before);}
        System.out.println("GlassPaneTerrainPublication PASS checks="+checks+" installed records, partial/stale results, material/removal lifetime, fenced native withdrawal, format guard and all-owner admissions");
    }
    private GlassPaneTerrainPublicationChecks(){}
}
