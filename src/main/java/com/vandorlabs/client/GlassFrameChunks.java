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
        final TileEntityProgrammableGlass tile;final BlockPos section;
        GlassFrameModel.Mesh mesh;RenderChunk chunk;boolean ready;
        Entry(TileEntityProgrammableGlass tile){this.tile=tile;section=section(tile.getPos());}
    }
    private static final class Section {
        final BlockPos pos;RenderChunk chunk;CompiledChunk before;
        Section(BlockPos pos){this.pos=pos;}
        boolean uploaded(ViewFrustum view,RenderGlobal renderer)throws ReflectiveOperationException{
            if(view==null)return false;RenderChunk current=(RenderChunk)LOOKUP.invoke(view,pos);
            if(current==null||!current.getPosition().equals(pos))return false;
            if(chunk!=current){
                chunk=current;before=current.getCompiledChunk();
                // One mark per pending section, after every changed mesh has been published.
                // An interior point marks this section without touching adjacent sections.
                int x=pos.getX()+8,y=pos.getY()+8,z=pos.getZ()+8;
                renderer.markBlockRangeForRenderUpdate(x,y,z,x,y,z);
                return false;
            }
            // Vanilla schedules visible sections; polling must not stall the client thread.
            if(current.needsUpdate()||!current.getLockCompileTask().tryLock())return false;
            try{
                ChunkCompileTaskGenerator task=(ChunkCompileTaskGenerator)TASK.get(current);
                if(task==null||!task.getLock().tryLock())return false;
                try{
                    return !current.needsUpdate()&&task.getStatus()==ChunkCompileTaskGenerator.Status.DONE&&current.getCompiledChunk()!=before&&task.getCompiledChunk()==current.getCompiledChunk();
                }finally{task.getLock().unlock();}
            }finally{current.getLockCompileTask().unlock();}
        }
    }
    /** Safe from onLoad and chunk workers: geometry and neighbour reads happen in flush only. */
    public static void changed(TileEntityProgrammableGlass tile){
        if(tile.getWorld()==null||!tile.getWorld().isRemote||tile.getWorld() instanceof VehicleWorld)return;
        synchronized(QUEUED){QUEUED.add(tile);}
    }
    static boolean selected(TileEntityProgrammableGlass tile){return tile.isFramed()&&(tile.frameAppearance.front>=0||tile.frameAppearance.interior>=0||tile.frameAppearance.exteriorChoice()>=0||tile.frameAppearance.tile);}
    static boolean supported(TileEntityProgrammableGlass tile,IBlockState state){
        return !LEGACY_BENCHMARK&&!(tile.getWorld() instanceof VehicleWorld)&&state.getBlock() instanceof BlockProgrammableGlass&&tile.isFramed()
                &&(selected(tile)||PanelJoins.flatCorner(state,tile.getWorld(),tile.getPos())!=null);
    }
    /** A ready mesh stays valid across ordinary relighting and transparent resort tasks. */
    static boolean rendered(TileEntityProgrammableGlass tile){
        Object value=tile.frameChunkPublication;if(!(value instanceof Entry))return false;Entry entry=(Entry)value;
        if(!entry.ready||entry.mesh==null||tile.frameChunkMesh!=entry.mesh)return false;
        if(entry.chunk!=null&&entry.chunk.getPosition().equals(entry.section))return true;
        entry.ready=false;changed(tile);return false;
    }
    public static void reload(){
        MESHES.invalidateAll();GlassFrameMesh.clear();GlassFrameModel.clear();
        for(Entry entry:ENTRIES.values()){entry.ready=false;entry.mesh=null;entry.tile.frameChunkMesh=null;changed(entry.tile);}
        PENDING.clear();frustum=null;
    }
    @SubscribeEvent public static void unloaded(net.minecraftforge.event.world.WorldEvent.Unload event){if(event.getWorld()==world)reset(null);}
    private static void reset(World next){
        if(world!=null&&listener!=null)world.removeEventListener(listener);
        for(Entry entry:ENTRIES.values()){entry.tile.frameChunkMesh=null;entry.tile.frameChunkPublication=null;}
        ENTRIES.clear();MEMBERS.clear();DIRTY.clear();PENDING.clear();MESHES.invalidateAll();world=next;frustum=null;
        if(next!=null){listener=new WorldRenderListener(){@Override public void notifyBlockUpdate(World w,BlockPos pos,IBlockState old,IBlockState state,int flags){DIRTY.add(pos.toImmutable());}};next.addEventListener(listener);}
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
        for(Entry entry:ENTRIES.values())if(entry.mesh!=null){entry.ready=false;PENDING.put(entry.section,new Section(entry.section));}
    }
    private static void remove(Entry entry){
        ENTRIES.remove(entry.tile.getPos(),entry);entry.tile.frameChunkMesh=null;entry.tile.frameChunkPublication=null;
        Set<Entry> members=MEMBERS.get(entry.section);if(members!=null){members.remove(entry);if(members.isEmpty()){MEMBERS.remove(entry.section);PENDING.remove(entry.section);}}
    }
    static void flush(){
        Minecraft mc=Minecraft.getMinecraft();if(mc.world!=world)reset(mc.world);
        if(world==null){synchronized(QUEUED){QUEUED.clear();}return;}
        if(format==null||!format.equals(DefaultVertexFormats.BLOCK)){format=new VertexFormat(DefaultVertexFormats.BLOCK);reload();}
        Set<TileEntityProgrammableGlass> queued=Collections.newSetFromMap(new IdentityHashMap<>());
        synchronized(QUEUED){queued.addAll(QUEUED);QUEUED.clear();}
        // Tile loads and packets may arrive without a neighbour block-state notification.
        // Invalidate once for the batch, before reading any frame contours.
        if(!queued.isEmpty())GlassSurfaceGroups.invalidate(world);
        for(TileEntityProgrammableGlass tile:queued)if(tile.getWorld()==world){
            BlockPos pos=tile.getPos().toImmutable();DIRTY.add(pos);
            if(valid(tile)){
                Entry entry=ENTRIES.get(pos);if(entry==null||entry.tile!=tile){
                    if(entry!=null)remove(entry);
                    entry=new Entry(tile);ENTRIES.put(pos,entry);MEMBERS.computeIfAbsent(entry.section,p->Collections.newSetFromMap(new IdentityHashMap<>())).add(entry);tile.frameChunkPublication=entry;
                }
            }else{Entry entry=ENTRIES.get(pos);if(entry!=null&&entry.tile==tile)remove(entry);}
        }
        Set<Entry> dirty=Collections.newSetFromMap(new IdentityHashMap<>());
        for(BlockPos pos:DIRTY)for(int x=-1;x<=1;x++)for(int y=-1;y<=1;y++)for(int z=-1;z<=1;z++){Entry entry=ENTRIES.get(pos.add(x,y,z));if(entry!=null)dirty.add(entry);}
        DIRTY.clear();
        for(Entry entry:dirty){
            TileEntityProgrammableGlass tile=entry.tile;
            if(!valid(tile)){remove(entry);continue;}
            IBlockState state=world.getBlockState(tile.getPos());GlassFrameModel.Mesh mesh=null;int light=0;
            if(supported(tile,state)){
                Key key=new Key(tile,state);mesh=MESHES.getIfPresent(key);light=key.kind==0?tile.frameAppearance.light():0;
                if(mesh==null){mesh=GlassFrameModel.build(tile,state);MESHES.put(key,mesh);}
            }
            if(entry.mesh==mesh&&tile.frameChunkLight==light&&(entry.ready||PENDING.containsKey(entry.section)))continue;
            if(entry.mesh==mesh&&mesh==null)continue;
            entry.mesh=mesh;entry.ready=false;tile.frameChunkLight=light;tile.frameChunkMesh=mesh;
            PENDING.put(entry.section,new Section(entry.section));
        }
        try{
            viewChanged(mc);
            Iterator<Section> pending=PENDING.values().iterator();while(pending.hasNext()){
                Section section=pending.next();if(!section.uploaded(frustum,mc.renderGlobal))continue;
                Set<Entry> members=MEMBERS.get(section.pos);if(members!=null)for(Entry entry:members){entry.chunk=section.chunk;entry.ready=entry.mesh!=null;}
                pending.remove();
            }
        }catch(ReflectiveOperationException failure){throw new IllegalStateException("Glass frame terrain publication failed",failure);}
    }
    private static boolean valid(TileEntityProgrammableGlass tile){return !tile.isInvalid()&&world.isBlockLoaded(tile.getPos())&&world.getTileEntity(tile.getPos())==tile&&world.getBlockState(tile.getPos()).getBlock() instanceof BlockProgrammableGlass;}
    private GlassFrameChunks(){}
}
