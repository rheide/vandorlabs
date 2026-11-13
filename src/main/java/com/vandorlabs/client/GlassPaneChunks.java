package com.vandorlabs.client;

import com.vandorlabs.blocks.BlockGlassWall;
import com.vandorlabs.blocks.BlockProgrammableGlass;
import com.vandorlabs.blocks.PanelDepth;
import com.vandorlabs.render.JoinedPaneMesher;
import com.vandorlabs.render.GlassPaneOrdering;
import com.vandorlabs.tiles.GlassMaterials;
import com.vandorlabs.tiles.TileEntityProgrammableGlass;
import com.vandorlabs.vehicle.VehicleWorld;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderGlobal;
import net.minecraft.client.renderer.ViewFrustum;
import net.minecraft.client.renderer.chunk.RenderChunk;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.vertex.VertexFormat;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.relauncher.ReflectionHelper;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.*;

/**
 * Static native panes own immutable planar batches inside world sections. One
 * real member admits each batch to the existing main/shadow render pipelines.
 * Publication happens only after the replacement GPU records are ready.
 */
@net.minecraftforge.fml.common.Mod.EventBusSubscriber(modid="vandorlabs",value=net.minecraftforge.fml.relauncher.Side.CLIENT)
public final class GlassPaneChunks {
    private static final int BUILD_SECTIONS=8;
    private static final Set<TileEntityProgrammableGlass> QUEUED=Collections.newSetFromMap(new IdentityHashMap<>());
    private static final Map<BlockPos,Entry> ENTRIES=new HashMap<>();
    private static final Map<BlockPos,Section> SECTIONS=new LinkedHashMap<>();
    private static final Set<BlockPos> DIRTY=new LinkedHashSet<>();
    private static final BlockPos.MutableBlockPos DIRTY_LOOKUP=new BlockPos.MutableBlockPos();
    private static final Field FRUSTUM=ReflectionHelper.findField(RenderGlobal.class,"viewFrustum","field_175008_n");
    private static final Method LOOKUP=lookup();
    private static World world;
    private static WorldRenderListener listener;
    private static ViewFrustum frustum;
    private static VertexFormat format;
    private static Object shaderIdentity;
    private static long generation;
    private static int tick;
    private static String fallback="not-initialized";
    private static long builds,reused;

    /** Complete draw source. Only ready and gpu are changed after construction. */
    public static final class Plan {
        public final BlockPos origin;
        public final TileEntityProgrammableGlass anchor;
        public final List<TileEntityProgrammableGlass> members;
        public final List<JoinedPaneMesher.Rect> rectangles;
        public final IBlockState state;
        public final boolean rotated,singleSurface,smooth;
        public final int depth,plane,shade,tint,light;
        public final TextureAtlasSprite sprite;
        public final AxisAlignedBB bounds;
        public final long generation;
        public volatile boolean ready;
        /** Render-thread-owned storage; the GPU backend retains its immutable build format. */
        public Object gpu;

        Plan(BlockPos origin,Key key,List<Entry> entries,GlassPaneTextures.Selection texture){
            this.origin=origin;rotated=key.rotated;depth=key.depth;plane=key.plane;
            shade=key.shade;tint=key.tint;light=key.light;state=key.state;
            sprite=texture.sprite;singleSurface=texture.singleSurface;smooth=texture.smooth;
            generation=++GlassPaneChunks.generation;
            entries.sort((a,b)->a.pos.compareTo(b.pos));
            anchor=entries.get(0).tile;
            List<TileEntityProgrammableGlass> owners=new ArrayList<>(entries.size());
            List<JoinedPaneMesher.Cell> cells=new ArrayList<>(entries.size());
            int minU=16,minV=16,maxU=0,maxV=0;
            for(Entry entry:entries){
                owners.add(entry.tile);
                int u=(rotated?entry.pos.getZ()-origin.getZ():entry.pos.getX()-origin.getX()),v=entry.pos.getY()-origin.getY();
                cells.add(new JoinedPaneMesher.Cell(u,v,entry.tile.isJoin()));
                minU=Math.min(minU,u);minV=Math.min(minV,v);maxU=Math.max(maxU,u+1);maxV=Math.max(maxV,v+1);
            }
            members=Collections.unmodifiableList(owners);
            rectangles=JoinedPaneMesher.merge(cells,texture.mergeable);
            bounds=paneBounds(origin,rotated,depth,plane,minU,minV,maxU,maxV);
        }

        private boolean same(Plan other){
            if(other==null||anchor!=other.anchor||rotated!=other.rotated||depth!=other.depth||plane!=other.plane
                    ||shade!=other.shade||tint!=other.tint||light!=other.light||sprite!=other.sprite
                    ||singleSurface!=other.singleSurface||smooth!=other.smooth
                    ||state.getBlock()!=other.state.getBlock()||state.getBlock().getMetaFromState(state)!=other.state.getBlock().getMetaFromState(other.state)
                    ||members.size()!=other.members.size()||rectangles.size()!=other.rectangles.size())return false;
            for(int i=0;i<members.size();i++)if(members.get(i)!=other.members.get(i))return false;
            for(int i=0;i<rectangles.size();i++){
                JoinedPaneMesher.Rect a=rectangles.get(i),b=other.rectangles.get(i);
                if(a.u!=b.u||a.v!=b.v||a.width!=b.width||a.height!=b.height)return false;
            }
            return true;
        }
    }

    private static final class Key {
        final IBlockState state;final Block block;final boolean rotated;
        final int depth,plane,shade,tint,light,metadata;
        Key(TileEntityProgrammableGlass tile,IBlockState state,BlockPos origin){
            this.state=state;block=state.getBlock();metadata=block.getMetaFromState(state);
            rotated=state.getValue(BlockGlassWall.ROTATED);depth=state.getValue(BlockGlassWall.DEPTH);
            plane=rotated?tile.getPos().getX()-origin.getX():tile.getPos().getZ()-origin.getZ();
            shade=tile.getShade();tint=tile.getTint();light=world.getCombinedLight(tile.getPos(),0);
        }
        @Override public int hashCode(){return Objects.hash(block,rotated,depth,plane,shade,tint,light,metadata);}
        @Override public boolean equals(Object value){
            if(!(value instanceof Key))return false;Key key=(Key)value;
            return block==key.block&&rotated==key.rotated&&depth==key.depth&&plane==key.plane
                    &&shade==key.shade&&tint==key.tint&&light==key.light&&metadata==key.metadata;
        }
    }

    private static final class Entry {
        final TileEntityProgrammableGlass tile;final BlockPos pos;final Section section;
        volatile Plan plan;
        Entry(TileEntityProgrammableGlass tile,Section section){this.tile=tile;pos=tile.getPos().toImmutable();this.section=section;}
    }
    private static final class Section {
        final BlockPos origin;
        final Set<Entry> entries=Collections.newSetFromMap(new IdentityHashMap<>());
        final GlassPaneAdmissions admissions=new GlassPaneAdmissions();
        Map<Key,Plan> plans=new HashMap<>();
        int retry,orderingFallback;
        Section(BlockPos origin){this.origin=origin;}
        void invalidate(){for(Plan plan:plans.values())plan.ready=false;DIRTY.add(origin);}
    }
    private static final class LegacyPlane {
        final boolean rotated;final double plane;final AxisAlignedBB bounds;
        LegacyPlane(Entry entry,IBlockState state){
            rotated=state.getValue(BlockGlassWall.ROTATED);
            plane=(rotated?entry.pos.getX():entry.pos.getZ())+.5+(rotated?-1:1)*PanelDepth.offset(state.getValue(BlockGlassWall.DEPTH))/16D;
            bounds=new AxisAlignedBB(entry.pos);
        }
    }

    private static Method lookup(){
        for(Method method:ViewFrustum.class.getDeclaredMethods())if(method.getReturnType()==RenderChunk.class
                &&Arrays.equals(method.getParameterTypes(),new Class[]{BlockPos.class})){method.setAccessible(true);return method;}
        throw new IllegalStateException("Missing render chunk lookup");
    }
    private static BlockPos section(BlockPos pos){return new BlockPos(pos.getX()&~15,pos.getY()&~15,pos.getZ()&~15);}
    /** Full batch bounds are also used when the anchor itself is outside the view. */
    static AxisAlignedBB paneBounds(BlockPos origin,boolean rotated,int depth,int plane,int minU,int minV,int maxU,int maxV){
        double middle=plane+.5+(rotated?-1:1)*PanelDepth.offset(depth)/16D;
        return rotated
                ?new AxisAlignedBB(origin.getX()+middle-.0625,origin.getY()+minV,origin.getZ()+minU,origin.getX()+middle+.0625,origin.getY()+maxV,origin.getZ()+maxU)
                :new AxisAlignedBB(origin.getX()+minU,origin.getY()+minV,origin.getZ()+middle-.0625,origin.getX()+maxU,origin.getY()+maxV,origin.getZ()+middle+.0625);
    }

    /** Safe during tile loading: no neighbour discovery or GPU work. */
    public static void changed(TileEntityProgrammableGlass tile){
        World physical=tile.getWorld();while(physical instanceof VehicleWorld)physical=((VehicleWorld)physical).parent;
        if(physical==null||!physical.isRemote||tile.getWorld() instanceof VehicleWorld)return;
        Object publication=tile.paneChunkPublication;
        if(publication instanceof Entry){Plan plan=((Entry)publication).plan;if(plan!=null)plan.ready=false;}
        synchronized(QUEUED){QUEUED.add(tile);}
    }

    public static Plan plan(TileEntityProgrammableGlass tile){
        Object publication=tile.paneChunkPublication;if(!(publication instanceof Entry))return null;
        Entry entry=(Entry)publication;Plan plan=entry.plan;
        return entry.tile==tile&&tile.getWorld()==world&&!tile.isInvalid()&&entry.pos.equals(tile.getPos())
                &&plan!=null&&plan.ready?plan:null;
    }
    public static boolean owns(TileEntityProgrammableGlass tile){return plan(tile)!=null;}
    public static boolean anchor(TileEntityProgrammableGlass tile){Plan plan=plan(tile);return plan!=null&&plan.anchor==tile;}
    public static AxisAlignedBB bounds(TileEntityProgrammableGlass tile){Plan plan=plan(tile);return plan!=null&&plan.anchor==tile?plan.bounds:null;}

    /** A draw failure can occur inside RenderGlobal's tile-list iterator. */
    static void failed(Plan plan){
        plan.ready=false;
        Section section=SECTIONS.get(plan.origin);
        if(section!=null){DIRTY.add(section.origin);section.retry=0;}
        fallback=GlassPaneBuffers.unavailableReason();
        // Do not restore admissions here: RenderGlobal may be iterating them.
        // The next flush restores every surviving owner before normal rendering.
    }

    /** -1 preserves legacy behavior, 0 suppresses this pass, 1 admits an anchor/fallback. */
    public static int renderPass(TileEntityProgrammableGlass tile,int pass){
        Plan plan=plan(tile);if(plan==null)return -1;
        if(pass!=0&&pass!=1)return 0;
        boolean shadow=VehicleRenderPass.shadow();
        if(pass==0){
            // Main-view pass zero registers anchor bounds with GlazingRenderOrder.
            if(!shadow&&plan.anchor==tile)return 1;
            return ProgrammableGlassPasses.emptyOpaquePass(tile,null)?0:-1;
        }
        if(shadow&&GlassPanePolicy.framesOnlyShadows())return 0;
        return plan.anchor==tile?1:0;
    }

    private static boolean installed(TileEntity raw){
        return raw!=null&&raw.getWorld()==world&&!raw.isInvalid()&&world!=null&&world.isBlockLoaded(raw.getPos())&&world.getTileEntity(raw.getPos())==raw;
    }
    private static boolean suppress(TileEntity raw){
        if(!(raw instanceof TileEntityProgrammableGlass))return false;
        TileEntityProgrammableGlass tile=(TileEntityProgrammableGlass)raw;Plan plan=plan(tile);
        // Anchors remain visible to both engine passes. Selected-frame fallbacks
        // remain too, until their native frame upload has actually completed.
        return plan!=null&&plan.anchor!=tile&&ProgrammableGlassPasses.emptyOpaquePass(tile,null);
    }

    private static void dirty(BlockPos pos){
        // Joins and neighbour-brightness sample adjacent cells. Merely sharing a
        // section with an animated screen or an unrelated light is not a dirty
        // dependency. This lookup has a fixed 27-cell bound and no allocations.
        for(int dy=-1;dy<=1;dy++)for(int dz=-1;dz<=1;dz++)for(int dx=-1;dx<=1;dx++){
            Entry entry=ENTRIES.get(DIRTY_LOOKUP.setPos(pos.getX()+dx,pos.getY()+dy,pos.getZ()+dz));
            if(entry!=null)entry.section.invalidate();
        }
    }

    private static void remove(Entry entry){
        ENTRIES.remove(entry.pos,entry);entry.section.entries.remove(entry);entry.section.invalidate();
        if(entry.tile.paneChunkPublication==entry)entry.tile.paneChunkPublication=null;
        entry.plan=null;
    }
    private static void reset(World next){
        if(world!=null&&listener!=null)world.removeEventListener(listener);
        for(Section section:SECTIONS.values()){
            section.admissions.restore(GlassPaneChunks::installed);
            for(Plan plan:section.plans.values()){plan.ready=false;GlassPaneBuffers.dispose(plan);}
        }
        for(Entry entry:ENTRIES.values())if(entry.tile.paneChunkPublication==entry)entry.tile.paneChunkPublication=null;
        ENTRIES.clear();SECTIONS.clear();DIRTY.clear();world=next;frustum=null;format=null;shaderIdentity=null;
        GlassPaneBuffers.resetFailures();
        synchronized(QUEUED){QUEUED.removeIf(tile->tile.getWorld()!=next);}
        if(next==null)return;
        listener=new WorldRenderListener(){
            @Override public void notifyBlockUpdate(World changed,BlockPos pos,IBlockState before,IBlockState after,int flags){if(changed==world)dirty(pos);}
            @Override public void notifyLightSet(BlockPos pos){dirty(pos);}
        };
        next.addEventListener(listener);
        // Registration is normally onLoad-driven. This single world-change pass
        // also admits owners loaded before the client subscriber was initialized.
        synchronized(QUEUED){for(TileEntity raw:next.loadedTileEntityList)if(raw instanceof TileEntityProgrammableGlass)QUEUED.add((TileEntityProgrammableGlass)raw);}
    }

    public static void reload(){
        for(Section section:SECTIONS.values()){
            section.admissions.restore(GlassPaneChunks::installed);
            for(Plan plan:section.plans.values()){plan.ready=false;GlassPaneBuffers.dispose(plan);}
            section.plans.clear();section.invalidate();
            for(Entry entry:section.entries)entry.plan=null;
        }
        format=null;shaderIdentity=null;
        GlassPaneBuffers.resetFailures();
    }

    private static void rebuild(Section section){
        section.admissions.restore(GlassPaneChunks::installed);
        Map<Key,List<Entry>> groups=new LinkedHashMap<>();
        List<LegacyPlane> legacy=new ArrayList<>();
        for(Entry entry:new ArrayList<>(section.entries)){
            entry.plan=null;
            if(!installed(entry.tile)||!entry.pos.equals(entry.tile.getPos())){remove(entry);continue;}
            TileEntityProgrammableGlass tile=entry.tile;IBlockState state=world.getBlockState(entry.pos);
            if(!(state.getBlock() instanceof BlockProgrammableGlass)||GlassSurfaceGroups.flatCorner(tile,state)!=null)continue;
            if(tile.getMaterial()>=0||tile.fluids.any()||GlassMaterials.opaque(tile.getMaterial(),tile.getTint())){
                legacy.add(new LegacyPlane(entry,state));continue;
            }
            groups.computeIfAbsent(new Key(tile,state,section.origin),key->new ArrayList<>()).add(entry);
        }
        Map<Key,Plan> candidates=new LinkedHashMap<>();boolean retry=false;
        for(Map.Entry<Key,List<Entry>> group:groups.entrySet()){
            Key key=group.getKey();GlassPaneTextures.Selection texture=GlassPaneTextures.select(key.shade,key.tint);
            if(texture==null){retry=true;fallback="atlas-not-ready";for(Entry entry:group.getValue())legacy.add(new LegacyPlane(entry,key.state));continue;}
            candidates.put(key,new Plan(section.origin,key,group.getValue(),texture));
        }
        Set<Plan> split=Collections.newSetFromMap(new IdentityHashMap<>());
        List<Plan> surfaces=new ArrayList<>(candidates.values());
        for(int i=0;i<surfaces.size();i++)for(int j=i+1;j<surfaces.size();j++){
            Plan a=surfaces.get(i),b=surfaces.get(j);
            if(requiresSplit(a,b)){
                if(a.members.size()>1)split.add(a);
                if(b.members.size()>1)split.add(b);
            }
        }
        // A custom/opaque/fluid pane may retain its original renderer while a
        // native group spans both sides of it. Preserve that ordering too.
        for(Plan candidate:surfaces)if(candidate.members.size()>1)for(LegacyPlane other:legacy){
            AxisAlignedBB a=candidate.bounds,b=other.bounds;
            if(GlassPaneOrdering.requiresSplit(candidate.rotated?0:2,candidate.rotated?(a.minX+a.maxX)*.5:(a.minZ+a.maxZ)*.5,
                    candidate.rotated?a.minZ:a.minX,candidate.rotated?a.maxZ:a.maxX,a.minY,a.maxY,
                    other.rotated?0:2,other.plane,other.rotated?b.minZ:b.minX,other.rotated?b.maxZ:b.maxX,b.minY,b.maxY))split.add(candidate);
        }
        section.orderingFallback=0;
        Map<Key,Plan> next=new HashMap<>();
        for(Map.Entry<Key,Plan> group:candidates.entrySet()){
            Key key=group.getKey();Plan candidate=group.getValue(),previous=section.plans.get(key);
            if(split.contains(candidate)){section.orderingFallback+=candidate.members.size();continue;}
            Plan published;
            if(candidate.same(previous)&&previous.gpu!=null){published=previous;reused++;}
            else{
                if(!GlassPaneBuffers.prepare(candidate)){retry=true;fallback=GlassPaneBuffers.unavailableReason();continue;}
                published=candidate;builds++;
            }
            published.ready=true;next.put(key,published);
            for(Entry entry:groups.get(key))entry.plan=published;
        }
        for(Plan previous:section.plans.values())if(!next.containsValue(previous)){previous.ready=false;GlassPaneBuffers.dispose(previous);}
        section.plans=next;section.retry=retry?tick+20:0;
    }

    private static boolean requiresSplit(Plan a,Plan b){
        AxisAlignedBB x=a.bounds,y=b.bounds;
        return GlassPaneOrdering.requiresSplit(a.rotated?0:2,a.rotated?(x.minX+x.maxX)*.5:(x.minZ+x.maxZ)*.5,
                a.rotated?x.minZ:x.minX,a.rotated?x.maxZ:x.maxX,x.minY,x.maxY,
                b.rotated?0:2,b.rotated?(y.minX+y.maxX)*.5:(y.minZ+y.maxZ)*.5,
                b.rotated?y.minZ:y.minX,b.rotated?y.maxZ:y.maxX,y.minY,y.maxY);
    }

    @SubscribeEvent public static void unloaded(net.minecraftforge.event.world.WorldEvent.Unload event){if(event.getWorld()==world)reset(null);}
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event){if(event.phase==TickEvent.Phase.END){tick++;flush();}}
    @SubscribeEvent public static void renderTick(TickEvent.RenderTickEvent event){if(event.phase==TickEvent.Phase.START)flush();}

    static void flush(){
        Minecraft mc=Minecraft.getMinecraft();if(mc.world!=world)reset(mc.world);
        if(world==null){synchronized(QUEUED){QUEUED.clear();}return;}
        if(!GlassPanePolicy.staticEnabled()){
            if(!SECTIONS.isEmpty())reset(world);
            synchronized(QUEUED){QUEUED.clear();}
            fallback="disabled";return;
        }
        Set<TileEntityProgrammableGlass> queued=Collections.emptySet();
        synchronized(QUEUED){if(!QUEUED.isEmpty()){queued=Collections.newSetFromMap(new IdentityHashMap<>());queued.addAll(QUEUED);QUEUED.clear();}}
        for(TileEntityProgrammableGlass tile:queued){
            Object publication=tile.paneChunkPublication;
            if(publication instanceof Entry){Entry old=(Entry)publication;if(!installed(tile)||!old.pos.equals(tile.getPos()))remove(old);}
            if(!installed(tile))continue;
            BlockPos pos=tile.getPos().toImmutable();Entry found=ENTRIES.get(pos);
            if(found!=null&&found.tile!=tile){remove(found);found=null;}
            if(found==null){
                BlockPos origin=section(pos);Section section=SECTIONS.computeIfAbsent(origin,Section::new);
                found=new Entry(tile,section);ENTRIES.put(pos,found);section.entries.add(found);tile.paneChunkPublication=found;
            }
            dirty(pos);
        }
        VertexFormat current=BlockSurfaceFormat.get();
        Object currentShader=GlassPaneBuffers.shaderIdentity();
        if(format==null||!format.equals(current)||shaderIdentity!=currentShader){
            reload();format=new VertexFormat(current);shaderIdentity=currentShader;
        }
        if(!GlassPaneBuffers.supported()){
            fallback=GlassPaneBuffers.unavailableReason();
            for(Section section:SECTIONS.values()){section.invalidate();section.admissions.restore(GlassPaneChunks::installed);}
            return;
        }
        fallback="none";
        for(Section section:SECTIONS.values())if(section.retry!=0&&tick>=section.retry){section.retry=0;section.invalidate();}
        int built=0;
        while(!DIRTY.isEmpty()&&built++<BUILD_SECTIONS){
            Iterator<BlockPos> pending=DIRTY.iterator();BlockPos pos=pending.next();pending.remove();
            Section section=SECTIONS.get(pos);if(section==null)continue;
            rebuild(section);
            if(section.entries.isEmpty()){section.admissions.restore(GlassPaneChunks::installed);SECTIONS.remove(pos);DIRTY.remove(pos);}
        }
        try{
            ViewFrustum view=(ViewFrustum)FRUSTUM.get(mc.renderGlobal);
            if(view!=frustum){for(Section section:SECTIONS.values())section.admissions.restore(GlassPaneChunks::installed);frustum=view;}
            if(view!=null)for(Section section:SECTIONS.values()){
                RenderChunk chunk=(RenderChunk)LOOKUP.invoke(view,section.origin);
                if(chunk==null||!chunk.getPosition().equals(section.origin)){section.admissions.restore(GlassPaneChunks::installed);continue;}
                section.admissions.prune(chunk.getCompiledChunk(),GlassPaneChunks::suppress,GlassPaneChunks::installed);
            }
        }catch(ReflectiveOperationException failure){throw new IllegalStateException("Glass pane section admission failed",failure);}
    }

    public static String diagnostics(){
        int groups=0,owners=0,rectangles=0,pruned=0,orderingFallback=0;
        for(Section section:SECTIONS.values()){
            pruned+=section.admissions.removedCount();
            orderingFallback+=section.orderingFallback;
            for(Plan plan:section.plans.values())if(plan.ready){groups++;owners+=plan.members.size();rectangles+=plan.rectangles.size();}
        }
        return "static-pane-groups="+groups+" owners="+owners+" rectangles="+rectangles+" pruned-tiles="+pruned
                +" ordering-fallback-owners="+orderingFallback+" gpu-bytes="+GlassPaneBuffers.retainedBytes()+" builds="+builds+" reused="+reused+" pending-sections="+DIRTY.size()+" fallback="+fallback;
    }

    private GlassPaneChunks(){}
}
