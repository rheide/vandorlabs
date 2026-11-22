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
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.client.renderer.vertex.VertexFormat;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.BlockRenderLayer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.relauncher.ReflectionHelper;
import net.minecraftforge.fml.relauncher.Side;

/**
 * Stationary Programmable Glass panes are ordinary chunk geometry. The client thread
 * prepares one immutable mesh per tile and stores it on the tile; the block model
 * emits it whenever Minecraft rebuilds the section, exactly like any other block.
 * Opaque panes use the solid layer, all others the translucent layer. Pane-only
 * owners leave the compiled tile list; animated, moving, corner and fluid panes keep
 * their tile renderer.
 */
@Mod.EventBusSubscriber(modid="vandorlabs",value=Side.CLIENT)
public final class GlassPaneTerrain {
    private static final int BUILD_OWNERS=1024,MAX_QUEUED=65536;
    private static final GlassSurfaceGroups.Layout UNIT_LAYOUT=new GlassSurfaceGroups.Layout(0,0,1,1);
    private static final Set<TileEntityProgrammableGlass> QUEUED=identities();
    private static boolean rescan;
    private static final Map<BlockPos,Entry> ENTRIES=new HashMap<>();
    private static final Map<BlockPos,Section> SECTIONS=new LinkedHashMap<>();
    private static final Set<Entry> DIRTY=identities();
    /** Joined artwork layouts can change when a distant member changes; recheck owners on topology edits. */
    private static final Set<Entry> ARTWORK_OWNERS=identities();
    private static final Set<BlockPos> REBUILD=new HashSet<>();
    private static final BlockPos.MutableBlockPos LOOKUP_POS=new BlockPos.MutableBlockPos();
    private static final com.google.common.cache.Cache<Key,GlassPaneTerrainMesh> MESHES=
            com.google.common.cache.CacheBuilder.newBuilder().maximumWeight(8*1024*1024)
                    .weigher((Key key,GlassPaneTerrainMesh mesh)->mesh.bytes()+96).build();
    private static final com.google.common.cache.Cache<ArtworkKey,GlassPaneTerrainMesh> ARTWORK=
            com.google.common.cache.CacheBuilder.newBuilder().maximumWeight(8*1024*1024)
                    .weigher((ArtworkKey key,GlassPaneTerrainMesh mesh)->mesh.bytes()+128).build();
    private static final Field FRUSTUM=ReflectionHelper.findField(RenderGlobal.class,"viewFrustum","field_175008_n");
    private static final Method CHUNK_LOOKUP=lookup();
    private static World world;
    private static WorldRenderListener listener;
    private static ViewFrustum frustum;
    private static VertexFormat format;
    private static volatile boolean formatRefresh;
    private static long artworkMutation=Long.MIN_VALUE;
    private static long builds,reuses,rebuildRequests,admissionSweeps;

    private static <T> Set<T> identities(){return Collections.newSetFromMap(new IdentityHashMap<T,Boolean>());}
    private static BlockPos section(BlockPos pos){return new BlockPos(pos.getX()&~15,pos.getY()&~15,pos.getZ()&~15);}
    private static Method lookup(){
        for(Method method:ViewFrustum.class.getDeclaredMethods())if(method.getReturnType()==RenderChunk.class
                &&Arrays.equals(method.getParameterTypes(),new Class[]{BlockPos.class})){method.setAccessible(true);return method;}
        throw new IllegalStateException("Missing render chunk lookup");
    }
    /** Stock translucent palette panes: one mesh per sprite and placement. */
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
    /** Artwork and opaque panes: the mapped two-sheet plane, cached by its full mapping. */
    private static final class ArtworkKey {
        final TextureAtlasSprite sprite;
        final boolean rotated,opaque;
        final int depth,mode,opacity;
        final String layout;
        ArtworkKey(IBlockState state,TextureAtlasSprite sprite,GlassSurfaceGroups.Layout layout,int mode,float opacity,boolean opaque){
            this.sprite=sprite;rotated=state.getValue(BlockGlassWall.ROTATED);depth=state.getValue(BlockGlassWall.DEPTH);
            this.layout=layout.key();this.mode=mode;this.opacity=Float.floatToIntBits(opacity);this.opaque=opaque;
        }
        @Override public int hashCode(){return Objects.hash(System.identityHashCode(sprite),rotated,opaque,depth,mode,opacity,layout);}
        @Override public boolean equals(Object value){if(!(value instanceof ArtworkKey))return false;ArtworkKey key=(ArtworkKey)value;
            return sprite==key.sprite&&rotated==key.rotated&&opaque==key.opaque&&depth==key.depth&&mode==key.mode&&opacity==key.opacity&&layout.equals(key.layout);}
    }
    private static final class Entry {
        final TileEntityProgrammableGlass tile;
        final BlockPos pos;
        final Section section;
        GlassPaneTerrainMesh mesh;
        String reason="pending";
        Entry(TileEntityProgrammableGlass tile,Section section){this.tile=tile;pos=tile.getPos().toImmutable();this.section=section;}
    }
    private static final class Section {
        final BlockPos origin;
        final Set<Entry> entries=identities();
        final GlassPaneAdmissions admissions=new GlassPaneAdmissions();
        RenderChunk chunk;
        CompiledChunk admitted;
        boolean admissionsDirty=true;
        Section(BlockPos origin){this.origin=origin;}
    }

    static GlassPaneTerrainMesh mesh(Object value){return peekMesh(value);}
    public static boolean hasMesh(Object value){return peekMesh(value)!=null;}
    /** A mesh written for an older vertex format is never emitted; the client thread rebuilds it. */
    static GlassPaneTerrainMesh peekMesh(Object value){
        if(!(value instanceof GlassPaneTerrainMesh))return null;
        GlassPaneTerrainMesh mesh=(GlassPaneTerrainMesh)value;
        if(!mesh.format.equals(DefaultVertexFormats.BLOCK)){formatRefresh=true;return null;}
        return mesh;
    }
    /** The chunk layer that owns a pane mesh, or null when the state carries none. */
    public static BlockRenderLayer layer(Object value){
        GlassPaneTerrainMesh mesh=peekMesh(value);
        return mesh==null?null:mesh.opaque?BlockRenderLayer.SOLID:BlockRenderLayer.TRANSLUCENT;
    }
    /** Live cell light for the pane layer; a lit frame backlights opaque replacement artwork. */
    public static int light(IBlockAccess access,BlockPos pos){
        int light=access.getCombinedLight(pos,0);
        TileEntity tile=access.getTileEntity(pos);
        return tile==null?light:(light&0xffff0000)|GlassInsertLighting.block(tile,light&65535);
    }

    /** Safe from loading callbacks or chunk workers: no neighbour reads or graphics calls. */
    public static void changed(TileEntityProgrammableGlass tile){
        World physical=tile.getWorld();while(physical instanceof VehicleWorld)physical=((VehicleWorld)physical).parent;
        if(physical==null||!physical.isRemote||tile.getWorld() instanceof VehicleWorld)return;
        synchronized(QUEUED){if(QUEUED.size()<MAX_QUEUED)QUEUED.add(tile);else rescan=true;}
    }
    static String unsupportedReason(TileEntityProgrammableGlass tile,IBlockState state){
        if(WorldSceneBenchmark.omitGlazing())return "benchmark-no-glass";
        if(!GlassPaneTerrainMesh.supports(DefaultVertexFormats.BLOCK))return "unsupported-vertex-layout";
        if(tile.getWorld() instanceof VehicleWorld)return "moving";
        if(!(state.getBlock() instanceof BlockProgrammableGlass)||state.getBlock() instanceof com.vandorlabs.blocks.BlockProgrammableCornerGlass)return "not-programmable-glass";
        // Static artwork and static-mode Screens share the atlas; animated displays keep their renderer.
        if(tile.getMaterial()>=0&&!GlazingDisplay.atlasStatic(tile))return "animated-display";
        if(tile.fluids.any())return "fluid";
        if(GlassSurfaceGroups.flatCorner(tile,state)!=null)return "corner";
        // Pane lighting is independent of the selected frame. Emissive borders
        // and joined cells with no frame geometry must not reject the pane.
        return "none";
    }
    static boolean supported(TileEntityProgrammableGlass tile,IBlockState state){return "none".equals(unsupportedReason(tile,state));}

    public static String fallbackReason(TileEntityProgrammableGlass tile){
        Entry entry=entry(tile);
        if(entry==null)return "not-observed";
        return rendered(tile)?"native":entry.reason;
    }
    /** Called when frame ownership changes, so the section's tile list is swept again. */
    static void refreshAdmissions(BlockPos pos){Section section=SECTIONS.get(section(pos));if(section!=null)section.admissionsDirty=true;}
    private static Entry entry(TileEntityProgrammableGlass tile){
        Object raw=tile.paneTerrainPublication;
        return raw instanceof Entry&&((Entry)raw).tile==tile&&((Entry)raw).pos.equals(tile.getPos())?(Entry)raw:null;
    }
    /** The block model emits this tile's pane; its tile renderer draws no pane. */
    public static boolean rendered(TileEntityProgrammableGlass tile){
        if(world==null||tile.getWorld()!=world||tile.isInvalid())return false;
        Entry entry=entry(tile);
        return entry!=null&&entry.mesh!=null&&tile.paneTerrainMesh==entry.mesh&&entry.mesh.format.equals(DefaultVertexFormats.BLOCK);
    }
    /** -1 leaves existing frame/fluid work available; panes have no native TESR draw. */
    public static int renderPass(TileEntityProgrammableGlass tile,int pass){
        if(!rendered(tile))return -1;
        if(ProgrammableGlassPasses.frameFallbackInPass(tile,pass))return -1;
        if(pass==1)return tile.fluids.any()?-1:0;
        return 0;
    }
    /** Caller holds the task lock. Let separately queued layer uploads finish (used by frame publication). */
    static boolean finishBeforePublish(ChunkCompileTaskGenerator task){
        if(task==null)return true;
        if(task.getStatus()==ChunkCompileTaskGenerator.Status.UPLOADING)return false;
        if(task.getStatus()!=ChunkCompileTaskGenerator.Status.DONE)task.finish();
        return true;
    }
    private static boolean installed(TileEntity tile){return tile!=null&&world!=null&&tile.getWorld()==world
            &&!tile.isInvalid()&&world.isBlockLoaded(tile.getPos())&&world.getTileEntity(tile.getPos())==tile;}
    private static boolean suppress(TileEntity raw){
        if(!(raw instanceof TileEntityProgrammableGlass))return false;
        TileEntityProgrammableGlass tile=(TileEntityProgrammableGlass)raw;
        return rendered(tile)&&!tile.fluids.any()
                &&!ProgrammableGlassPasses.frameFallbackInPass(tile,0)&&!ProgrammableGlassPasses.frameFallbackInPass(tile,1);
    }
    private static void dirty(BlockPos pos){
        for(int y=-1;y<=1;y++)for(int z=-1;z<=1;z++)for(int x=-1;x<=1;x++){
            Entry entry=ENTRIES.get(LOOKUP_POS.setPos(pos.getX()+x,pos.getY()+y,pos.getZ()+z));
            if(entry!=null){DIRTY.add(entry);entry.section.admissionsDirty=true;}
        }
    }
    private static void remove(Entry entry){
        ENTRIES.remove(entry.pos,entry);DIRTY.remove(entry);ARTWORK_OWNERS.remove(entry);entry.section.entries.remove(entry);
        entry.section.admissionsDirty=true;
        if(entry.mesh!=null)REBUILD.add(entry.section.origin);
        if(entry.tile.paneTerrainPublication==entry){entry.tile.paneTerrainPublication=null;entry.tile.paneTerrainMesh=null;}
    }
    private static void reset(World next){
        if(world!=null&&listener!=null)world.removeEventListener(listener);
        for(Section section:SECTIONS.values())section.admissions.restore(GlassPaneTerrain::installed);
        for(Entry entry:ENTRIES.values())if(entry.tile.paneTerrainPublication==entry){entry.tile.paneTerrainPublication=null;entry.tile.paneTerrainMesh=null;}
        ENTRIES.clear();SECTIONS.clear();DIRTY.clear();ARTWORK_OWNERS.clear();REBUILD.clear();
        MESHES.invalidateAll();ARTWORK.invalidateAll();world=next;frustum=null;format=null;
        synchronized(QUEUED){QUEUED.removeIf(tile->tile.getWorld()!=next);rescan=false;}
        if(next==null)return;
        listener=new WorldRenderListener(){
            @Override public void notifyBlockUpdate(World changed,BlockPos pos,IBlockState before,IBlockState after,int flags){if(changed==world)dirty(pos);}
        };
        next.addEventListener(listener);
        synchronized(QUEUED){rescan=true;}
    }
    /** Atlas or vertex-format change: rebuild every mesh and re-sweep every section. */
    public static void reload(){
        MESHES.invalidateAll();ARTWORK.invalidateAll();format=null;
        DIRTY.addAll(ENTRIES.values());
        for(Section section:SECTIONS.values())section.admissionsDirty=true;
    }
    private static GlassPaneTerrainMesh build(Entry entry,IBlockState state){
        TileEntityProgrammableGlass tile=entry.tile;
        boolean opaque=GlassMaterials.opaque(tile.getMaterial(),tile.getTint());
        if(tile.getMaterial()<0&&!opaque){
            ARTWORK_OWNERS.remove(entry);
            GlassPaneTextures.Selection material=GlassPaneTextures.select(tile.getShade(),tile.getTint());
            if(material==null){entry.reason="atlas-not-ready";synchronized(QUEUED){QUEUED.add(tile);}return null;}
            // Prepared variants floor faint pixels, so this holds unless a resource pack bypasses preparation.
            if(!material.terrainAlphaSafe){entry.reason="native-alpha-cutoff";return null;}
            Key key=new Key(state,material);GlassPaneTerrainMesh mesh=MESHES.getIfPresent(key);
            if(mesh==null){mesh=GlassPaneTerrainMesh.build(key.rotated,key.depth,key.sprite,key.single,format);MESHES.put(key,mesh);builds++;}
            else reuses++;
            entry.reason="none";return mesh;
        }
        // Selected artwork (any opacity) and opaque stock panes use the mapped two-sheet plane.
        // Native culling leaves one visible sheet; faint artwork pixels below the alpha test are dropped.
        TextureMap atlas=Minecraft.getMinecraft().getTextureMapBlocks();
        TextureAtlasSprite sprite=atlas.getAtlasSprite(GlassMaterials.texture(tile.getMaterial(),tile.getShade(),tile.getSize(),tile.getTint()));
        if(sprite==null||sprite==atlas.getMissingSprite()){entry.reason="missing-artwork";return null;}
        boolean artwork=tile.getMaterial()>=0;
        if(artwork)ARTWORK_OWNERS.add(entry);else ARTWORK_OWNERS.remove(entry);
        float opacity=opaque?1F:com.vandorlabs.render.GlassRenderPolicy.singleLayerOpacity(GlassMaterials.opacity(tile.getMaterial(),tile.getTint()));
        GlassSurfaceGroups.Layout layout=artwork?GlassSurfaceGroups.layout(tile):UNIT_LAYOUT;
        int mode=artwork?tile.getTextureLayout():0;
        ArtworkKey key=new ArtworkKey(state,sprite,layout,mode,opacity,opaque);
        GlassPaneTerrainMesh mesh=ARTWORK.getIfPresent(key);
        if(mesh==null){
            StaticSurfaceMesh mapped=GlassSurfaceMapping.map(TEProgrammableGlass.materialPlane(),sprite,layout,true,0,0,mode);
            mesh=GlassPaneTerrainMesh.mapped(key.rotated,key.depth,sprite,mapped,opacity,opaque,format);ARTWORK.put(key,mesh);builds++;
        }else reuses++;
        entry.reason="none";return mesh;
    }

    @SubscribeEvent public static void unloaded(net.minecraftforge.event.world.WorldEvent.Unload event){if(event.getWorld()==world)reset(null);}
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event){if(event.phase==TickEvent.Phase.END)flush();}
    @SubscribeEvent public static void renderTick(TickEvent.RenderTickEvent event){if(event.phase==TickEvent.Phase.START)flush();}

    static void flush(){
        Minecraft mc=Minecraft.getMinecraft();if(mc.world!=world)reset(mc.world);
        if(world==null){synchronized(QUEUED){QUEUED.clear();}GlassFrameRetained.flush();PortholeRetained.flush();return;}
        if(formatRefresh||format==null||!format.equals(DefaultVertexFormats.BLOCK)){
            formatRefresh=false;
            if(format==null||!format.equals(DefaultVertexFormats.BLOCK)){reload();format=new VertexFormat(DefaultVertexFormats.BLOCK);}
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
        long mutation=GlassSurfaceGroups.mutation();
        if(mutation!=artworkMutation){artworkMutation=mutation;DIRTY.addAll(ARTWORK_OWNERS);}
        int processed=0;
        while(!DIRTY.isEmpty()&&processed++<BUILD_OWNERS){
            Iterator<Entry> iterator=DIRTY.iterator();Entry entry=iterator.next();iterator.remove();
            if(!installed(entry.tile)||!entry.pos.equals(entry.tile.getPos())){remove(entry);continue;}
            IBlockState state=world.getBlockState(entry.pos);GlassPaneTerrainMesh mesh=null;
            entry.reason=unsupportedReason(entry.tile,state);
            if("none".equals(entry.reason))mesh=build(entry,state);else ARTWORK_OWNERS.remove(entry);
            if(entry.mesh!=mesh||entry.tile.paneTerrainMesh!=mesh){
                entry.mesh=mesh;entry.tile.paneTerrainMesh=mesh;
                entry.section.admissionsDirty=true;REBUILD.add(entry.section.origin);
            }
        }
        // One interior mark per changed section; RenderGlobal schedules it like any block change.
        for(BlockPos origin:REBUILD){int x=origin.getX()+8,y=origin.getY()+8,z=origin.getZ()+8;mc.renderGlobal.markBlockRangeForRenderUpdate(x,y,z,x,y,z);rebuildRequests++;}
        REBUILD.clear();
        GlassFrameChunks.settleUploads();
        GlassFrameRetained.flush();
        PortholeRetained.flush();
        try{updateView(mc);}catch(ReflectiveOperationException failure){throw new IllegalStateException("Native glass pane admission failed",failure);}
    }
    /** Keep each section's installed tile list free of pane-only owners. */
    private static void updateView(Minecraft mc)throws ReflectiveOperationException{
        ViewFrustum view=(ViewFrustum)FRUSTUM.get(mc.renderGlobal);
        if(view!=frustum){
            for(Section section:SECTIONS.values()){section.admissions.restore(GlassPaneTerrain::installed);section.chunk=null;section.admitted=null;section.admissionsDirty=true;}
            frustum=view;
        }
        if(view==null)return;
        Iterator<Section> sections=SECTIONS.values().iterator();
        while(sections.hasNext()){
            Section section=sections.next();RenderChunk chunk=(RenderChunk)CHUNK_LOOKUP.invoke(view,section.origin);
            if(chunk==null||!chunk.getPosition().equals(section.origin)){
                if(section.chunk!=null){section.admissions.restore(GlassPaneTerrain::installed);section.chunk=null;section.admitted=null;}
                if(section.entries.isEmpty())sections.remove();
                continue;
            }
            if(section.chunk!=chunk){section.admissions.restore(GlassPaneTerrain::installed);section.chunk=chunk;section.admitted=null;section.admissionsDirty=true;}
            CompiledChunk current=chunk.getCompiledChunk();
            if(current!=section.admitted)section.admissionsDirty=true;
            if(section.admissionsDirty){
                section.admissions.prune(current,GlassPaneTerrain::suppress,GlassPaneTerrain::installed);
                section.admitted=current;section.admissionsDirty=false;admissionSweeps++;
            }
            if(section.entries.isEmpty()){section.admissions.restore(GlassPaneTerrain::installed);sections.remove();}
        }
    }

    public static String diagnostics(){
        int owners=0,quads=0,pruned=0,opaque=0;long bytes=0;
        Map<String,Integer> reasons=new TreeMap<>();
        for(Section section:SECTIONS.values())pruned+=section.admissions.removedCount();
        for(GlassPaneTerrainMesh mesh:MESHES.asMap().values())bytes+=mesh.bytes();
        for(GlassPaneTerrainMesh mesh:ARTWORK.asMap().values())bytes+=mesh.bytes();
        for(Entry entry:ENTRIES.values()){
            if(rendered(entry.tile)){owners++;quads+=entry.mesh.quads.size();if(entry.mesh.opaque)opaque++;}
            String reason=fallbackReason(entry.tile);reasons.put(reason,reasons.getOrDefault(reason,0)+1);
        }
        return "terrain-pane-owners="+owners+" opaque-owners="+opaque+" quads="+quads+" pruned-tiles="+pruned+" sections="+SECTIONS.size()
                +" pending-owners="+DIRTY.size()+" mesh-bytes="+bytes+" builds="+builds+" reused="+reuses
                +" rebuild-requests="+rebuildRequests+" admission-sweeps="+admissionSweeps+" owner-reasons="+reasons;
    }
    private GlassPaneTerrain(){}
}
