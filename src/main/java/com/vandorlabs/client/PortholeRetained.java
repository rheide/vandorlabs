package com.vandorlabs.client;

import com.vandorlabs.blocks.BlockProgrammableWall;
import com.vandorlabs.tiles.GlassMaterials;
import com.vandorlabs.tiles.TileEntityAnimatedScreenSelector;
import com.vandorlabs.vehicle.VehicleWorld;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.*;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.chunk.CompiledChunk;
import net.minecraft.client.renderer.chunk.RenderChunk;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.client.renderer.vertex.VertexBuffer;
import net.minecraft.client.renderer.vertex.VertexFormat;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.BlockRenderLayer;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.client.MinecraftForgeClient;
import net.minecraftforge.fml.relauncher.ReflectionHelper;
import org.lwjgl.opengl.GL11;

/** Retain the real porthole housing and aperture, independently of each other. */
public final class PortholeRetained {
    private static final int LIMIT=16*1024*1024,MAX_GROUP=2*1024*1024,MAX_SECTIONS_PER_FLUSH=8,MAX_QUEUED=32768;
    private static final Map<Long,Section> SECTIONS=new LinkedHashMap<>();
    private static final Map<TileEntityAnimatedScreenSelector,Owner> OWNERS=new IdentityHashMap<>();
    private static final Set<TileEntityAnimatedScreenSelector> QUEUED=identities();
    private static final Set<Long> INVALIDATED=new LinkedHashSet<>(),DIRTY=new LinkedHashSet<>();
    private static PortholeGroupCache.Dependencies changedRegion=new PortholeGroupCache.Dependencies();
    private static final BufferBuilder COMPONENT=new BufferBuilder(16384),JOINED=new BufferBuilder(65536);
    private static final Field FRUSTUM=ReflectionHelper.findField(RenderGlobal.class,"viewFrustum","field_175008_n");
    private static final Method CHUNK_LOOKUP=chunkLookup(),SHADER_TILE=shaderTile();
    private static final GlassShaderMaterial SHADER_MATERIAL=GlassShaderMaterial.detect();
    private static World world;
    private static WorldRenderListener listener;
    private static ViewFrustum frustum;
    private static VertexFormat format;
    private static boolean rescan=true,reloadPending,warned,drawFailed;
    private static int retainedBytes,trackedTiles;
    static long builds,uploads,housingDraws,paneDraws,shadowDraws,admissionSweeps,generation,legacyHousingDraws,legacyPaneDraws;
    private static String unavailable="world-unloaded";

    private static <T> Set<T> identities(){return Collections.newSetFromMap(new IdentityHashMap<T,Boolean>());}
    static long sectionKey(BlockPos pos){return new BlockPos(pos.getX()&~15,pos.getY()&~15,pos.getZ()&~15).toLong();}
    private static Method chunkLookup(){
        for(Method method:ViewFrustum.class.getDeclaredMethods())if(method.getReturnType()==RenderChunk.class
                &&Arrays.equals(method.getParameterTypes(),new Class[]{BlockPos.class})){method.setAccessible(true);return method;}
        throw new IllegalStateException("Missing render chunk lookup");
    }
    private static Method shaderTile(){
        try{return Class.forName("net.optifine.shaders.Shaders").getMethod("nextBlockEntity",TileEntity.class);}
        catch(ReflectiveOperationException|LinkageError unsupported){return null;}
    }
    private static boolean porthole(IBlockState state){return state.getBlock() instanceof BlockProgrammableWall&&((BlockProgrammableWall)state.getBlock()).isPortholeShape();}
    private static boolean installed(TileEntity tile){return world!=null&&tile.getWorld()==world&&!tile.isInvalid()
            &&world.isBlockLoaded(tile.getPos())&&world.getTileEntity(tile.getPos())==tile;}

    static final class Section {
        final long key;
        final BlockPos origin;
        final Set<TileEntityAnimatedScreenSelector> tiles=identities();
        final List<Group> groups=new ArrayList<>();
        final GlassPaneAdmissions admissions=new GlassPaneAdmissions();
        PortholeGroupCache.Dependencies dependencies=new PortholeGroupCache.Dependencies();
        RenderChunk chunk;
        CompiledChunk admitted;
        boolean current,admittedReady,admissionsDirty=true;
        String fallback="none";
        Section(long key){this.key=key;origin=BlockPos.fromLong(key);}
        void retire(){
            current=false;admittedReady=false;
            for(TileEntityAnimatedScreenSelector tile:tiles){Owner owner=OWNERS.get(tile);if(owner!=null&&owner.section==this)OWNERS.remove(tile);}
            for(Group group:groups)group.delete();groups.clear();admissionsDirty=true;
        }
    }
    static final class Owner {
        final Section section;
        final TileEntityAnimatedScreenSelector tile;
        final BlockPos pos;
        final IBlockState state;
        final long revision;
        final boolean staticPane,opaque,emptyHousing,emptyPane;
        Group housing,pane;
        Owner(Section section,TileEntityAnimatedScreenSelector tile,IBlockState state,PortholeRetainedGeometry.Surfaces meshes){
            this.section=section;this.tile=tile;this.state=state;pos=tile.getPos().toImmutable();revision=tile.getSettingsRevision();
            emptyHousing=meshes.housing.vertexCount()==0;emptyPane=meshes.pane.vertexCount()==0;
            staticPane=emptyPane||GlazingDisplay.atlasStatic(tile);opaque=GlassMaterials.opaque(tile.getGlassMaterial(),tile.getGlassTint());
        }
        boolean ready(){return section.current&&(emptyHousing||housing!=null&&housing.current())
                &&(!staticPane||emptyPane||pane!=null&&pane.current());}
        boolean anchor(){return housing!=null&&housing.anchor==tile||pane!=null&&pane.anchor==tile;}
    }
    /** Housing shares section/state; panes additionally keep their real plane or joined shape. */
    static final class Key {
        final IBlockState state;
        final int shader,kind;
        final long[] planes;
        final Object shape;
        final boolean single;
        Key(TileEntityAnimatedScreenSelector tile,IBlockState state,int kind,boolean single,PortholeRetainedGeometry.Surfaces meshes){
            this.state=state;this.kind=kind;this.single=single;
            shader=format.getNextOffset()>32?SHADER_MATERIAL.key(tile):GlassShaderMaterial.UNAVAILABLE;
            planes=kind==0?null:meshes.pane.portholePlanes(meshes.rotation,tile.getPos());
            // The existing translucent sorter has exact order for vertical
            // axis-aligned planes. Other shapes retain their original per-owner
            // sort granularity while still reusing both uploaded buffers.
            boolean sortable=planes!=null&&planes[1]==0&&(planes[0]==0||planes[2]==0);
            shape=kind!=0&&!sortable?tile:null;
        }
        public int hashCode(){return Objects.hash(System.identityHashCode(state),shader,kind,Arrays.hashCode(planes),System.identityHashCode(shape),single);}
        public boolean equals(Object other){if(!(other instanceof Key))return false;Key k=(Key)other;
            return state==k.state&&shader==k.shader&&kind==k.kind&&Arrays.equals(planes,k.planes)&&shape==k.shape&&single==k.single;}
    }
    static final class Piece {
        final Owner owner;final StaticSurfaceMesh mesh;final int rotation;
        Piece(Owner owner,StaticSurfaceMesh mesh,int rotation){this.owner=owner;this.mesh=mesh;this.rotation=rotation;}
    }
    static final class Group {
        final Section section;
        final Key key;
        final List<Owner> members;
        final TileEntityAnimatedScreenSelector anchor;
        final BlockPos origin;
        final AxisAlignedBB bounds;
        final VertexFormat format;
        final int[] records;
        final int vertices,bytes;
        VertexBuffer buffer;
        boolean accounted;
        Group(Section section,Key key,List<Piece> pieces,VertexFormat format){
            this.section=section;this.key=key;this.format=new VertexFormat(format);
            pieces.sort(Comparator.comparing(piece->piece.owner.pos));members=new ArrayList<>();
            for(Piece piece:pieces)members.add(piece.owner);
            anchor=members.get(0).tile;origin=members.get(0).pos;
            records=capture(key,pieces,format,origin);vertices=records.length/format.getIntegerSize();
            bytes=records.length*8+256+members.size()*64;
            double x0=Double.POSITIVE_INFINITY,y0=x0,z0=x0,x1=Double.NEGATIVE_INFINITY,y1=x1,z1=x1;
            for(int i=0;i<records.length;i+=format.getIntegerSize()){
                double x=Float.intBitsToFloat(records[i])+origin.getX(),y=Float.intBitsToFloat(records[i+1])+origin.getY(),z=Float.intBitsToFloat(records[i+2])+origin.getZ();
                x0=Math.min(x0,x);y0=Math.min(y0,y);z0=Math.min(z0,z);x1=Math.max(x1,x);y1=Math.max(y1,y);z1=Math.max(z1,z);
            }
            bounds=new AxisAlignedBB(x0,y0,z0,x1,y1,z1).grow(.001);
        }
        int[] recordCopy(){return records.clone();}
        void upload(){
            JOINED.begin(GL11.GL_QUADS,format);
            try{JOINED.setVertexState(JOINED.new State(records,new VertexFormat(format)));}finally{JOINED.finishDrawing();}
            buffer=new VertexBuffer(format);buffer.bufferData(JOINED.getByteBuffer());retainedBytes+=bytes;accounted=true;uploads++;
        }
        boolean current(){return buffer!=null&&section.current&&format.equals(BlockSurfaceFormat.get())&&!anchor.isInvalid()&&origin.equals(anchor.getPos());}
        void delete(){if(buffer!=null){buffer.deleteGlBuffers();buffer=null;}if(accounted){retainedBytes-=bytes;accounted=false;}}
        void draw(double x,double y,double z){
            boolean shadow=VehicleRenderPass.shadow(),pane=key.kind!=0,transparent=key.kind==2;
            if(pane&&GlassPanePolicy.omitPane(!transparent,shadow))return;
            if(format.getNextOffset()>32)try{SHADER_TILE.invoke(null,anchor);}
            catch(ReflectiveOperationException failure){throw new IllegalStateException("Cannot select retained porthole shader material",failure);}
            GlassPaneGpuTimers.Token timing=GlassPaneGpuTimers.begin("porthole-"+(pane?"pane":"housing")+(shadow?"-shadow":"-main"));
            GlStateManager.pushMatrix();
            try{
                GlStateManager.translate(x,y,z);Minecraft.getMinecraft().getTextureManager().bindTexture(TextureMap.LOCATION_BLOCKS_TEXTURE);
                GlStateManager.disableLighting();GlStateManager.enableTexture2D();
                if(key.single)GlStateManager.enableCull();else GlStateManager.disableCull();
                if(transparent)GlStateManager.enableBlend();else GlStateManager.disableBlend();
                GlStateManager.tryBlendFuncSeparate(770,771,1,0);GlStateManager.alphaFunc(GL11.GL_GREATER,pane?.003F:.1F);
                GlStateManager.depthMask(!transparent);GlStateManager.color(1,1,1,1);
                BlockMeshVbo.draw(buffer,format,transparent?BlockRenderLayer.TRANSLUCENT:BlockRenderLayer.CUTOUT,key.single);
                if(shadow)shadowDraws++;else if(pane)paneDraws++;else housingDraws++;
            }finally{
                GlStateManager.enableCull();GlStateManager.depthMask(true);GlStateManager.alphaFunc(GL11.GL_GREATER,.1F);
                GlStateManager.disableBlend();GlStateManager.enableLighting();GlStateManager.color(1,1,1,1);
                GlStateManager.popMatrix();GlassPaneGpuTimers.end(timing);
            }
        }
    }

    public static void changed(TileEntityAnimatedScreenSelector tile){
        if(!GlassPanePolicy.retainedEnabled()||tile.getWorld()==null||!tile.getWorld().isRemote||tile.getWorld() instanceof VehicleWorld)return;
        synchronized(QUEUED){if(QUEUED.size()<MAX_QUEUED)QUEUED.add(tile);else rescan=true;}
        neighbours(tile.getPos());
    }
    public static void invalidateSection(BlockPos pos){
        synchronized(QUEUED){if(INVALIDATED.size()<MAX_QUEUED)INVALIDATED.add(sectionKey(pos));else rescan=true;}
    }
    /** Topology eviction may affect an entire joined aperture far beyond the edited cell. */
    static void invalidatePositions(World changed,BlockPos[] positions){
        if(changed!=world)return;
        synchronized(QUEUED){for(BlockPos pos:positions){if(INVALIDATED.size()>=MAX_QUEUED){rescan=true;break;}INVALIDATED.add(sectionKey(pos));}}
    }
    private static void neighbours(BlockPos pos){
        synchronized(QUEUED){changedRegion.include(pos);}
        for(int y=-1;y<=1;y++)for(int z=-1;z<=1;z++)for(int x=-1;x<=1;x++)invalidateSection(pos.add(x,y,z));
    }
    private static void range(int x0,int y0,int z0,int x1,int y1,int z1){
        long ax=Math.min(x0,x1)&~15,bx=Math.max(x0,x1)&~15,ay=Math.min(y0,y1)&~15,by=Math.max(y0,y1)&~15,az=Math.min(z0,z1)&~15,bz=Math.max(z0,z1)&~15;
        long count=((bx-ax+16)/16)*((by-ay+16)/16),depth=(bz-az+16)/16;
        synchronized(QUEUED){
            changedRegion.include(new BlockPos(Math.min(x0,x1),Math.min(y0,y1),Math.min(z0,z1)));
            changedRegion.include(new BlockPos(Math.max(x0,x1),Math.max(y0,y1),Math.max(z0,z1)));
            if(count>MAX_QUEUED||depth>MAX_QUEUED||count*depth>MAX_QUEUED){rescan=true;return;}
            for(long y=ay;y<=by;y+=16)for(long z=az;z<=bz;z+=16)for(long x=ax;x<=bx;x+=16){
                if(INVALIDATED.size()>=MAX_QUEUED){rescan=true;return;}INVALIDATED.add(new BlockPos((int)x,(int)y,(int)z).toLong());
            }
        }
    }
    public static void reload(){synchronized(QUEUED){reloadPending=true;}drawFailed=false;}
    private static void reset(World next){
        if(world!=null&&listener!=null)world.removeEventListener(listener);
        for(Section section:SECTIONS.values()){section.admissions.restore(PortholeRetained::installed);section.retire();}
        SECTIONS.clear();OWNERS.clear();DIRTY.clear();format=null;frustum=null;listener=null;trackedTiles=0;world=next;drawFailed=false;generation++;
        synchronized(QUEUED){QUEUED.removeIf(tile->tile.getWorld()!=next);INVALIDATED.clear();changedRegion=new PortholeGroupCache.Dependencies();rescan=true;reloadPending=false;}
        if(next!=null&&GlassPanePolicy.retainedEnabled())listen(next);
    }
    private static void listen(World next){
        if(listener!=null)return;
        listener=new WorldRenderListener(){
            @Override public void notifyBlockUpdate(World changed,BlockPos pos,IBlockState before,IBlockState after,int flags){if(changed==world)neighbours(pos);}
            @Override public void notifyLightSet(BlockPos pos){invalidateSection(pos);}
            @Override public void markBlockRangeForRenderUpdate(int x0,int y0,int z0,int x1,int y1,int z1){range(x0,y0,z0,x1,y1,z1);}
        };next.addEventListener(listener);
    }
    /** Use ordinary per-owner writers before concatenating, preserving optional shader records. */
    static int[] capture(Key key,List<Piece> pieces,VertexFormat format,BlockPos origin){
        List<int[]> components=new ArrayList<>();int size=0;
        for(Piece piece:pieces){
            Owner owner=piece.owner;TileEntityAnimatedScreenSelector tile=owner.tile;
            COMPONENT.begin(GL11.GL_QUADS,format);boolean context=false;
            try{
                context=VehicleShaderContext.push(tile,COMPONENT);
                int light=tile.getWorld().getCombinedLight(owner.pos,0);
                float opacity=key.kind==0?1:GlassMaterials.opacity(tile.getGlassMaterial(),tile.getGlassTint());
                piece.mesh.drawPlacedColored(COMPONENT,owner.pos.getX()-origin.getX(),owner.pos.getY()-origin.getY(),owner.pos.getZ()-origin.getZ(),piece.rotation,light,opacity);
            }finally{try{if(context)VehicleShaderContext.pop(COMPONENT);}finally{COMPONENT.finishDrawing();COMPONENT.setTranslation(0,0,0);}}
            int[] records=COMPONENT.getVertexState().getRawBuffer();size=Math.addExact(size,records.length);
            if(size>MAX_GROUP/8)throw new IllegalStateException("Retained porthole group exceeds 2 MiB including CPU and GPU records");components.add(records);
        }
        int[] result=new int[size];int at=0;for(int[] component:components){System.arraycopy(component,0,result,at,component.length);at+=component.length;}return result;
    }
    private static void rebuild(Section section){
        section.retire();int before=section.tiles.size();section.tiles.removeIf(tile->!installed(tile)||sectionKey(tile.getPos())!=section.key||!porthole(world.getBlockState(tile.getPos())));trackedTiles-=before-section.tiles.size();
        section.dependencies=new PortholeGroupCache.Dependencies();section.fallback="none";
        Map<Key,List<Piece>> groups=new LinkedHashMap<>();List<Owner> owners=new ArrayList<>();
        for(TileEntityAnimatedScreenSelector tile:section.tiles){
            try{
                IBlockState state=world.getBlockState(tile.getPos());PortholeRetainedGeometry.Surfaces meshes=PortholeRetainedGeometry.capture(tile,state);
                PortholeGroupCache.Dependencies dependencies=PortholeGroupCache.dependencies(world,tile.getPos());
                if(dependencies!=null)section.dependencies.include(dependencies);else section.dependencies.include(tile.getPos());
                Owner owner=new Owner(section,tile,state,meshes);owners.add(owner);
                if(!owner.emptyHousing)groups.computeIfAbsent(new Key(tile,state,0,false,meshes),k->new ArrayList<>()).add(new Piece(owner,meshes.housing,meshes.rotation));
                if(owner.staticPane&&!owner.emptyPane)groups.computeIfAbsent(new Key(tile,state,owner.opaque?1:2,meshes.single,meshes),k->new ArrayList<>()).add(new Piece(owner,meshes.pane,meshes.rotation));
            }catch(RuntimeException|LinkageError failure){section.fallback="retained-build-failed";failed(section.fallback,failure);}
        }
        for(Map.Entry<Key,List<Piece>> entry:groups.entrySet()){
            List<Piece> pieces=entry.getValue();pieces.sort(Comparator.comparing(piece->piece.owner.pos));
            List<Piece> batch=new ArrayList<>();long bytes=256;
            for(Piece piece:pieces){
                long next=piece.mesh.vertexCount()*(long)format.getNextOffset()*2+64;
                if(!batch.isEmpty()&&bytes+next>MAX_GROUP){upload(section,entry.getKey(),batch);batch=new ArrayList<>();bytes=256;}
                if(next+256>MAX_GROUP){section.fallback="individual-mesh-byte-limit";continue;}
                batch.add(piece);bytes+=next;
            }
            if(!batch.isEmpty())upload(section,entry.getKey(),batch);
        }
        section.current=true;for(Owner owner:owners)if(owner.ready())OWNERS.put(owner.tile,owner);
        // A missing pane group must not leave a shared housing drawing alongside
        // its individual fallback. Publish only complete owner sets.
        for(Group group:new ArrayList<>(section.groups)){
            boolean complete=true;for(Owner owner:group.members)complete&=OWNERS.get(owner.tile)==owner;
            if(!complete){group.delete();section.groups.remove(group);for(Owner owner:group.members)OWNERS.remove(owner.tile);}
        }
        boolean changed;
        do{
            changed=false;
            for(Group group:new ArrayList<>(section.groups))for(Owner owner:group.members)if(!owner.ready()||!OWNERS.containsKey(owner.tile)){
                group.delete();section.groups.remove(group);for(Owner member:group.members)OWNERS.remove(member.tile);changed=true;break;
            }
        }while(changed);
        section.admissionsDirty=true;generation++;
    }
    private static void upload(Section section,Key key,List<Piece> pieces){
        Group group=null;
        try{
            group=new Group(section,key,pieces,format);
            if(retainedBytes+group.bytes>LIMIT){section.fallback="retained-byte-limit";return;}
            group.upload();section.groups.add(group);builds++;
            for(Owner owner:group.members)if(group.key.kind==0)owner.housing=group;else owner.pane=group;
        }catch(RuntimeException|LinkageError failure){if(group!=null)group.delete();section.fallback="retained-build-failed";failed(section.fallback,failure);}
    }
    private static void failed(String reason,Throwable failure){
        unavailable=reason;if(!warned){warned=true;if(com.vandorlabs.VandorLabs.logger!=null)
            com.vandorlabs.VandorLabs.logger.warn("Retained porthole unavailable; keeping individual rendering",failure);}
    }
    private static void updateView(Minecraft mc)throws ReflectiveOperationException{
        ViewFrustum view=(ViewFrustum)FRUSTUM.get(mc.renderGlobal);
        if(view!=frustum){for(Section section:SECTIONS.values()){section.admissions.restore(PortholeRetained::installed);section.chunk=null;section.admitted=null;section.admittedReady=false;section.admissionsDirty=true;}frustum=view;}
        for(Section section:SECTIONS.values()){
            RenderChunk chunk=view==null?null:(RenderChunk)CHUNK_LOOKUP.invoke(view,section.origin);
            if(chunk!=null&&!chunk.getPosition().equals(section.origin))chunk=null;
            if(chunk!=section.chunk){section.admissions.restore(PortholeRetained::installed);section.chunk=chunk;section.admitted=null;section.admittedReady=false;section.admissionsDirty=true;}
            CompiledChunk current=chunk==null?null:chunk.getCompiledChunk();
            if(current!=section.admitted)section.admissionsDirty=true;
            if(section.admissionsDirty){
                // A rebuilt group's new anchor may be one of our previous
                // suppressed members. Restore it before testing publication.
                section.admissions.restore(PortholeRetained::installed);
                section.admittedReady=section.current&&current!=null&&current!=CompiledChunk.DUMMY;
                if(section.admittedReady)for(Group group:section.groups)if(!current.getTileEntities().contains(group.anchor)){section.admittedReady=false;break;}
                section.admissions.prune(current,PortholeRetained::suppress,PortholeRetained::installed);section.admitted=current;section.admissionsDirty=false;admissionSweeps++;
            }
        }
    }
    public static void flush(){
        Minecraft mc=Minecraft.getMinecraft();if(mc==null)return;
        if(mc.world!=world)reset(mc.world);
        if(world==null){unavailable="world-unloaded";return;}
        if(!GlassPanePolicy.retainedEnabled()||WorldSceneBenchmark.omitGlazing()||!OpenGlHelper.useVbo()){
            if(!SECTIONS.isEmpty())reset(world);
            if(listener!=null){world.removeEventListener(listener);listener=null;}
            unavailable="retained-disabled";return;
        }
        listen(world);
        VertexFormat current=BlockSurfaceFormat.get();
        Set<TileEntityAnimatedScreenSelector> queued=identities();
        synchronized(QUEUED){
            if(reloadPending||format==null||!format.equals(current)){for(Section section:SECTIONS.values())DIRTY.add(section.key);format=new VertexFormat(current);reloadPending=false;}
            queued.addAll(QUEUED);QUEUED.clear();DIRTY.addAll(INVALIDATED);INVALIDATED.clear();
            if(changedRegion.minX!=Integer.MAX_VALUE){
                for(Section section:SECTIONS.values())if(section.dependencies.intersects(changedRegion.minX,changedRegion.minY,changedRegion.minZ,
                        changedRegion.maxX,changedRegion.maxY,changedRegion.maxZ))DIRTY.add(section.key);
                changedRegion=new PortholeGroupCache.Dependencies();
            }
            if(rescan){for(Section section:SECTIONS.values())DIRTY.add(section.key);for(TileEntity tile:world.loadedTileEntityList)
                if(tile instanceof TileEntityAnimatedScreenSelector)queued.add((TileEntityAnimatedScreenSelector)tile);rescan=false;}
        }
        unavailable="none";
        for(TileEntityAnimatedScreenSelector tile:queued)if(installed(tile)&&porthole(world.getBlockState(tile.getPos()))){
            long key=sectionKey(tile.getPos());Section section=SECTIONS.get(key);
            if(section!=null&&section.tiles.contains(tile)){DIRTY.add(key);continue;}
            if(trackedTiles>=MAX_QUEUED){unavailable="retained-owner-limit";continue;}
            if(section==null){section=new Section(key);SECTIONS.put(key,section);}section.tiles.add(tile);trackedTiles++;DIRTY.add(key);
        }
        for(long key:DIRTY){Section section=SECTIONS.get(key);if(section!=null)section.retire();}
        if(drawFailed||!BlockMeshVbo.supportsRetained(current)||(current.getNextOffset()>32&&(SHADER_TILE==null||!VehicleShaderContext.available()))){
            for(Section section:SECTIONS.values()){DIRTY.add(section.key);section.retire();}unavailable=drawFailed?"retained-draw-failed":"unsupported-shader-hooks";
        }else{
            int rebuilt=0;Iterator<Long> dirty=DIRTY.iterator();
            while(dirty.hasNext()&&rebuilt++<MAX_SECTIONS_PER_FLUSH){long key=dirty.next();dirty.remove();Section section=SECTIONS.get(key);if(section!=null)rebuild(section);}
        }
        try{updateView(mc);}catch(ReflectiveOperationException|RuntimeException failure){
            for(Section section:SECTIONS.values()){section.admittedReady=false;section.admissions.restore(PortholeRetained::installed);}failed("admission-unavailable",failure);
        }
        Iterator<Section> sections=SECTIONS.values().iterator();while(sections.hasNext()){
            Section section=sections.next();if(section.tiles.isEmpty()){section.admissions.restore(PortholeRetained::installed);section.retire();sections.remove();}
        }
    }
    private static Owner current(TileEntityAnimatedScreenSelector tile){
        Owner owner=OWNERS.get(tile);
        if(owner==null||world!=tile.getWorld()||tile.isInvalid()||!owner.pos.equals(tile.getPos())||!owner.section.admittedReady||!owner.ready())return null;
        // Queue a missed appearance notification, but keep this frame's owner
        // until flush can restore every suppressed member atomically.
        if(owner.revision!=tile.getSettingsRevision())changed(tile);
        return owner;
    }
    public static boolean handles(TileEntityAnimatedScreenSelector tile){return current(tile)!=null;}
    public static boolean handlesHousing(TileEntityAnimatedScreenSelector tile){return current(tile)!=null;}
    public static boolean anchor(TileEntityAnimatedScreenSelector tile){Owner owner=current(tile);return owner!=null&&owner.anchor();}
    public static AxisAlignedBB bounds(TileEntityAnimatedScreenSelector tile){
        Owner owner=current(tile);if(owner==null)return null;AxisAlignedBB box=null;
        if(owner.housing!=null&&owner.housing.anchor==tile)box=owner.housing.bounds;
        if(owner.pane!=null&&owner.pane.anchor==tile)box=box==null?owner.pane.bounds:box.union(owner.pane.bounds);return box;
    }
    static AxisAlignedBB paneBounds(TileEntityAnimatedScreenSelector tile){Owner owner=current(tile);return owner!=null&&owner.pane!=null&&owner.pane.anchor==tile?owner.pane.bounds:null;}
    static int paneAxis(TileEntityAnimatedScreenSelector tile){
        Owner owner=current(tile);if(owner==null||owner.pane==null||owner.pane.anchor!=tile)return -1;
        long[] planes=owner.pane.key.planes;
        return planes!=null&&planes[1]==0&&(planes[0]==0||planes[2]==0)?planes[0]==0?2:0:-1;
    }
    static double paneCoordinate(TileEntityAnimatedScreenSelector tile){
        Owner owner=current(tile);if(owner==null||owner.pane==null||owner.pane.anchor!=tile)return Double.NaN;
        long[] planes=owner.pane.key.planes;return planes==null?Double.NaN:(planes[3]+planes[4])/200000D;
    }
    private static boolean suppress(TileEntity tile){
        if(!(tile instanceof TileEntityAnimatedScreenSelector))return false;Owner owner=current((TileEntityAnimatedScreenSelector)tile);
        return owner!=null&&owner.staticPane&&!owner.anchor()&&!owner.tile.fluids.any();
    }
    public static int renderPass(TileEntityAnimatedScreenSelector tile,int pass){
        Owner owner=current(tile);if(owner==null)return -1;
        if(pass!=0&&pass!=1)return 0;
        if(pass==1&&tile.fluids.any())return -1;
        boolean omitPane=GlassPanePolicy.omitPane(owner.opaque,VehicleRenderPass.shadow());
        if(!owner.staticPane&&!omitPane&&pass==(owner.opaque?0:1))return -1;
        if(pass==0&&(owner.anchor()||!owner.staticPane&&!omitPane||tile.fluids.any()))return 1;
        if(owner.pane!=null&&owner.pane.anchor==tile&&pass==(owner.opaque?0:1)
                &&!GlassPanePolicy.omitPane(owner.opaque,VehicleRenderPass.shadow()))return 1;
        return 0;
    }
    /** True means the entire porthole pass is handled; fluid rendering stays with the caller. */
    public static boolean draw(TileEntityAnimatedScreenSelector tile,double x,double y,double z){
        Owner owner=current(tile);if(owner==null)return false;int pass=MinecraftForgeClient.getRenderPass();
        try{
            if(owner.housing!=null&&owner.housing.anchor==tile&&(pass<0||pass==0))owner.housing.draw(x,y,z);
            if(owner.pane!=null&&owner.pane.anchor==tile&&(pass<0||pass==(owner.opaque?0:1)))owner.pane.draw(x,y,z);
        }catch(RuntimeException|LinkageError failure){drawFailed=true;invalidateSection(owner.pos);failed("retained-draw-failed",failure);return false;}
        return owner.staticPane||GlassPanePolicy.omitPane(owner.opaque,VehicleRenderPass.shadow())||pass>=0&&pass!=(owner.opaque?0:1);
    }
    public static String diagnostics(){
        int groups=0,vertices=0,pruned=0,dynamic=0,single=0,active=0,waiting=0;Map<String,Integer> reasons=new TreeMap<>();
        for(Section section:SECTIONS.values()){
            if(!"none".equals(section.fallback))reasons.merge(section.fallback,1,Integer::sum);
            if(section.current&&!section.admittedReady)waiting++;
            pruned+=section.admissions.removedCount();if(section.current)for(Group group:section.groups){groups++;vertices+=group.vertices;if(group.key.single)single++;}
        }
        for(Owner owner:OWNERS.values()){if(!owner.staticPane)dynamic++;if(owner.section.admittedReady&&owner.ready())active++;}
        return "porthole-owners="+active+" porthole-captured-owners="+OWNERS.size()+" porthole-tracked="+trackedTiles
                +" porthole-groups="+groups+" porthole-vertices="+vertices+" porthole-bytes="+retainedBytes+" porthole-anchor-wait="+waiting
                +" porthole-pruned="+pruned+" porthole-pending="+DIRTY.size()+" porthole-dynamic-panes="+dynamic+" porthole-single-groups="+single
                +" porthole-builds="+builds+" porthole-uploads="+uploads+" porthole-housing-draws="+housingDraws+" porthole-pane-draws="+paneDraws
                +" porthole-shadow-draws="+shadowDraws+" porthole-legacy-housing-draws="+legacyHousingDraws+" porthole-legacy-pane-draws="+legacyPaneDraws
                +" porthole-fallback="+unavailable+" porthole-section-fallbacks="+reasons;
    }
    static void legacyHousing(){legacyHousingDraws++;}
    static void legacyPane(){legacyPaneDraws++;}
    private PortholeRetained(){}
}
