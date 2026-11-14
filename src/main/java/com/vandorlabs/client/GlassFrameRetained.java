package com.vandorlabs.client;

import com.vandorlabs.blocks.BlockGlassWall;
import com.vandorlabs.blocks.BlockProgrammableGlass;
import com.vandorlabs.blocks.PanelDepth;
import com.vandorlabs.tiles.TileEntityProgrammableGlass;
import com.vandorlabs.vehicle.VehicleWorld;
import java.lang.reflect.Method;
import java.util.*;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.client.renderer.vertex.VertexBuffer;
import net.minecraft.client.renderer.vertex.VertexFormat;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.BlockRenderLayer;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.client.MinecraftForgeClient;
import org.lwjgl.opengl.GL11;

/**
 * Retained stationary frame geometry, independently of the glass insert.
 * Genuine alpha-blended emissive artwork keeps its fullbright vertex light,
 * original two-sided faces and opaque-pass depth behaviour without a separate
 * buffer upload and draw for every frame tile.
 */
public final class GlassFrameRetained {
    private static final int LIMIT=32*1024*1024,MAX_GROUP=2*1024*1024,MAX_SECTIONS_PER_FLUSH=8,MAX_QUEUED=32768;
    private static final Map<Long,Section> SECTIONS=new LinkedHashMap<>();
    private static final Map<TileEntityProgrammableGlass,Group> OWNERS=new IdentityHashMap<>();
    private static final Set<TileEntityProgrammableGlass> QUEUED=Collections.newSetFromMap(new IdentityHashMap<>());
    private static final Set<Long> INVALIDATED=new LinkedHashSet<>(),DIRTY=new LinkedHashSet<>();
    private static final BufferBuilder COMPONENT=new BufferBuilder(16384),JOINED=new BufferBuilder(65536);
    private static final GlassShaderMaterial SHADER_MATERIAL=GlassShaderMaterial.detect();
    private static final Method SHADER_TILE=shaderTile();
    private static World world;
    private static WorldRenderListener listener;
    private static VertexFormat format;
    private static boolean rescan=true,drawFailed,warned;
    private static int retainedBytes;
    static long builds,uploads,mainDraws,shadowDraws,legacyMainDraws,legacyShadowDraws,generation;
    private static String unavailable="world-unloaded";

    static final class Section {
        final long key;
        final BlockPos origin;
        final Set<TileEntityProgrammableGlass> tiles=Collections.newSetFromMap(new IdentityHashMap<>());
        final List<Group> groups=new ArrayList<>();
        boolean current;
        Section(long key){this.key=key;origin=BlockPos.fromLong(key);}
        void retire(){
            current=false;
            for(Group group:groups){for(TileEntityProgrammableGlass tile:group.members)OWNERS.remove(tile,group);group.delete();}
            groups.clear();GlassPaneTerrain.refreshAdmissions(origin);
        }
    }
    /** Coplanar faces may share one atlas draw even when their frame finishes differ. */
    static final class Key {
        final IBlockState state;
        final int shader,axis;
        final long plane;
        Key(IBlockState state,int shader,BlockPos pos){
            this.state=state;this.shader=shader;
            boolean rotated=state.getValue(BlockGlassWall.ROTATED);axis=rotated?0:2;
            double offset=PanelDepth.offset(state.getValue(BlockGlassWall.DEPTH))/16D;
            plane=Double.doubleToLongBits((rotated?pos.getX():pos.getZ())+.5+(rotated?-offset:offset));
        }
        public int hashCode(){return Objects.hash(System.identityHashCode(state),shader,axis,plane);}
        public boolean equals(Object other){if(!(other instanceof Key))return false;Key key=(Key)other;return state==key.state&&shader==key.shader&&axis==key.axis&&plane==key.plane;}
    }
    static final class Group {
        final Section section;
        final List<TileEntityProgrammableGlass> members;
        final TileEntityProgrammableGlass anchor;
        final AxisAlignedBB bounds;
        final VertexFormat format;
        final int[] records;
        final int bytes;
        final Map<TileEntityProgrammableGlass,BlockPos> positions=new IdentityHashMap<>();
        private VertexBuffer buffer;
        private boolean accounted;
        Group(Section section,List<TileEntityProgrammableGlass> members,VertexFormat format,int[] records){
            this.section=section;this.format=new VertexFormat(format);this.records=records;
            this.members=Collections.unmodifiableList(new ArrayList<>(members));anchor=members.get(0);
            AxisAlignedBB box=null;
            for(TileEntityProgrammableGlass tile:members){BlockPos pos=tile.getPos().toImmutable();positions.put(tile,pos);AxisAlignedBB next=new AxisAlignedBB(pos);box=box==null?next:box.union(next);}
            bounds=box;bytes=recordBytes(records.length,members.size());
        }
        void upload(){
            JOINED.begin(GL11.GL_QUADS,format);
            try{JOINED.setVertexState(JOINED.new State(records,new VertexFormat(format)));}finally{JOINED.finishDrawing();}
            buffer=new VertexBuffer(format);buffer.bufferData(JOINED.getByteBuffer());retainedBytes+=bytes;accounted=true;uploads++;
        }
        boolean current(){return buffer!=null&&section.current&&format.equals(BlockSurfaceFormat.get());}
        void delete(){if(buffer!=null){buffer.deleteGlBuffers();buffer=null;}if(accounted){retainedBytes-=bytes;accounted=false;}}
        void draw(double x,double y,double z){
            boolean shadow=VehicleRenderPass.shadow();
            if(format.getNextOffset()>32)try{SHADER_TILE.invoke(null,anchor);}catch(ReflectiveOperationException failure){throw new IllegalStateException("Cannot select retained frame shader material",failure);}
            GlassPaneGpuTimers.Token timing=GlassPaneGpuTimers.begin(shadow?"retained-frame-shadow":"retained-frame-main");
            GlStateManager.pushMatrix();
            try{
                BlockPos pos=positions.get(anchor);
                GlStateManager.translate(x+section.origin.getX()-pos.getX(),y+section.origin.getY()-pos.getY(),z+section.origin.getZ()-pos.getZ());
                Minecraft.getMinecraft().getTextureManager().bindTexture(TextureMap.LOCATION_BLOCKS_TEXTURE);
                GlStateManager.disableLighting();GlStateManager.disableCull();GlStateManager.enableBlend();
                GlStateManager.tryBlendFuncSeparate(770,771,1,0);GlStateManager.depthMask(true);GlStateManager.color(1,1,1,1);
                // The legacy selected-frame renderer blends in pass zero with
                // depth writes and the caller's alpha threshold. Keep that
                // contract rather than turning glowing edges into cutouts.
                BlockMeshVbo.draw(buffer,format,BlockRenderLayer.TRANSLUCENT,false);
                if(shadow)shadowDraws++;else mainDraws++;
            }finally{
                GlStateManager.disableBlend();GlStateManager.enableCull();GlStateManager.enableLighting();GlStateManager.color(1,1,1,1);
                GlStateManager.popMatrix();GlassPaneGpuTimers.end(timing);
            }
        }
    }
    private static Method shaderTile(){
        try{return Class.forName("net.optifine.shaders.Shaders").getMethod("nextBlockEntity",TileEntity.class);}
        catch(ClassNotFoundException absent){return null;}
        catch(ReflectiveOperationException|LinkageError unsupported){return null;}
    }
    static long sectionKey(BlockPos pos){return new BlockPos(pos.getX()&~15,pos.getY()&~15,pos.getZ()&~15).toLong();}
    private static boolean installed(TileEntity tile){return world!=null&&tile.getWorld()==world&&!tile.isInvalid()&&world.isBlockLoaded(tile.getPos())&&world.getTileEntity(tile.getPos())==tile;}
    static boolean enabled(){return GlassPanePolicy.retainedEnabled()&&!Boolean.getBoolean("vandorlabs.benchmarkLegacyGlazing");}
    public static void changed(TileEntityProgrammableGlass tile){
        if(!enabled()||tile.getWorld()==null||!tile.getWorld().isRemote||tile.getWorld() instanceof VehicleWorld)return;
        synchronized(QUEUED){if(QUEUED.size()<MAX_QUEUED)QUEUED.add(tile);else rescan=true;}
        invalidateSection(tile.getPos());
    }
    public static void invalidateSection(BlockPos pos){
        if(!enabled())return;
        synchronized(QUEUED){if(INVALIDATED.size()<MAX_QUEUED)INVALIDATED.add(sectionKey(pos));else rescan=true;}
    }
    private static void neighbours(BlockPos pos){for(int y=-1;y<=1;y++)for(int z=-1;z<=1;z++)for(int x=-1;x<=1;x++)invalidateSection(pos.add(x,y,z));}
    private static void range(int x1,int y1,int z1,int x2,int y2,int z2){
        int ax=Math.min(x1,x2)&~15,bx=Math.max(x1,x2)&~15,ay=Math.min(y1,y2)&~15,by=Math.max(y1,y2)&~15,az=Math.min(z1,z2)&~15,bz=Math.max(z1,z2)&~15;
        long count=((long)bx-ax+16)/16*((long)by-ay+16)/16,depth=((long)bz-az+16)/16;
        synchronized(QUEUED){
            if(count>MAX_QUEUED||depth>MAX_QUEUED||count*depth>MAX_QUEUED){rescan=true;return;}
            for(long y=ay;y<=by;y+=16)for(long z=az;z<=bz;z+=16)for(long x=ax;x<=bx;x+=16){
                if(INVALIDATED.size()>=MAX_QUEUED){rescan=true;return;}INVALIDATED.add(new BlockPos((int)x,(int)y,(int)z).toLong());
            }
        }
    }
    public static void reload(){
        format=null;drawFailed=false;
        for(Section section:SECTIONS.values()){section.current=false;DIRTY.add(section.key);GlassPaneTerrain.refreshAdmissions(section.origin);}
    }
    private static void reset(World next){
        if(world!=null&&listener!=null)world.removeEventListener(listener);
        for(Section section:SECTIONS.values())section.retire();
        SECTIONS.clear();OWNERS.clear();DIRTY.clear();format=null;world=next;drawFailed=false;generation++;
        synchronized(QUEUED){QUEUED.removeIf(tile->tile.getWorld()!=next);INVALIDATED.clear();rescan=true;}
        if(next==null)return;
        listener=new WorldRenderListener(){
            @Override public void notifyBlockUpdate(World changed,BlockPos pos,IBlockState before,IBlockState after,int flags){if(changed==world)neighbours(pos);}
            @Override public void notifyLightSet(BlockPos pos){neighbours(pos);}
            @Override public void markBlockRangeForRenderUpdate(int x1,int y1,int z1,int x2,int y2,int z2){range(x1,y1,z1,x2,y2,z2);}
        };next.addEventListener(listener);
    }
    private static Key selection(TileEntityProgrammableGlass tile){
        if(!installed(tile)||tile.getWorld() instanceof VehicleWorld||!GlassFrameChunks.retainedSafe(tile))return null;
        IBlockState state=world.getBlockState(tile.getPos());
        if(!(state.getBlock() instanceof BlockProgrammableGlass)||!GlassFrameChunks.selected(tile)||GlassSurfaceGroups.flatCorner(tile,state)!=null)return null;
        return new Key(state,format.getNextOffset()>32?SHADER_MATERIAL.key(tile):GlassShaderMaterial.UNAVAILABLE,tile.getPos());
    }
    static int recordBytes(int ints,int members){return Math.addExact(Math.multiplyExact(ints,8),256+members*80);}
    /** Normal writers retain optional shader records and a distinct lightmap for every frame. */
    static int[] capture(TileEntityProgrammableGlass tile,IBlockState state,VertexFormat format,BlockPos origin){
        COMPONENT.begin(GL11.GL_QUADS,format);boolean context=false;
        try{
            context=VehicleShaderContext.push(tile,COMPONENT);
            int light=tile.getWorld().getCombinedLight(tile.getPos(),tile.frameAppearance.light());
            if(tile.frameAppearance.light()>0)light=(light&0xffff0000)|240;
            boolean rotated=state.getValue(BlockGlassWall.ROTATED);double depth=PanelDepth.offset(state.getValue(BlockGlassWall.DEPTH))/16D;
            GlassFrameMesh.get(tile).drawPlaced(COMPONENT,tile.getPos().getX()-origin.getX()-(rotated?depth:0),tile.getPos().getY()-origin.getY(),tile.getPos().getZ()-origin.getZ()+(rotated?0:depth),rotated?3:0,light);
        }finally{try{if(context)VehicleShaderContext.pop(COMPONENT);}finally{COMPONENT.finishDrawing();COMPONENT.setTranslation(0,0,0);}}
        return COMPONENT.getVertexState().getRawBuffer();
    }
    private static int[] join(List<int[]> parts,int size){int[] result=new int[size];int at=0;for(int[] part:parts){System.arraycopy(part,0,result,at,part.length);at+=part.length;}return result;}
    private static void install(Section section,List<TileEntityProgrammableGlass> members,List<int[]> parts,int size){
        if(members.isEmpty())return;
        Group group=new Group(section,members,format,join(parts,size));
        if(retainedBytes+group.bytes>LIMIT){unavailable="frame-byte-limit";return;}
        try{group.upload();section.groups.add(group);for(TileEntityProgrammableGlass tile:members)OWNERS.put(tile,group);builds++;}
        catch(RuntimeException|LinkageError failure){group.delete();throw failure;}
    }
    private static void rebuild(Section section){
        section.retire();section.tiles.removeIf(tile->!installed(tile)||sectionKey(tile.getPos())!=section.key);
        Map<Key,List<TileEntityProgrammableGlass>> groups=new LinkedHashMap<>();
        for(TileEntityProgrammableGlass tile:section.tiles){Key key=selection(tile);if(key!=null)groups.computeIfAbsent(key,k->new ArrayList<>()).add(tile);}
        try{
            for(Map.Entry<Key,List<TileEntityProgrammableGlass>> entry:groups.entrySet()){
                entry.getValue().sort(Comparator.comparing(TileEntity::getPos));
                List<TileEntityProgrammableGlass> members=new ArrayList<>();List<int[]> parts=new ArrayList<>();int size=0;
                for(TileEntityProgrammableGlass tile:entry.getValue()){
                    int[] records=capture(tile,entry.getKey().state,format,section.origin);
                    if(records.length==0)continue;
                    if(recordBytes(records.length,1)>MAX_GROUP){unavailable="frame-owner-byte-limit";continue;}
                    if(recordBytes(size+records.length,members.size()+1)>MAX_GROUP){install(section,members,parts,size);members.clear();parts.clear();size=0;}
                    members.add(tile);parts.add(records);size+=records.length;
                }
                install(section,members,parts,size);
            }
        }catch(RuntimeException|LinkageError failure){
            section.retire();unavailable="frame-build-failed";
            if(!warned){warned=true;if(com.vandorlabs.VandorLabs.logger!=null)com.vandorlabs.VandorLabs.logger.warn("Retained glass frame unavailable; keeping frame fallback",failure);}
        }
        section.current=true;generation++;GlassPaneTerrain.refreshAdmissions(section.origin);
    }
    public static void flush(){
        Minecraft mc=Minecraft.getMinecraft();if(mc==null)return;
        if(mc.world!=world)reset(mc.world);
        if(world==null){unavailable="world-unloaded";return;}
        if(!enabled()||WorldSceneBenchmark.omitGlazing()||!OpenGlHelper.useVbo()){
            if(!SECTIONS.isEmpty())reset(world);unavailable="frame-retained-disabled";return;
        }
        VertexFormat current=BlockSurfaceFormat.get();
        if(format==null||!format.equals(current)){reload();format=new VertexFormat(current);}
        if(drawFailed||!BlockMeshVbo.supportsRetained(current)||(current.getNextOffset()>32&&(SHADER_TILE==null||!VehicleShaderContext.available()))){
            for(Section section:SECTIONS.values()){if(!section.groups.isEmpty())section.retire();DIRTY.add(section.key);}
            unavailable=drawFailed?"frame-draw-failed":"unsupported-frame-shader-hooks";return;
        }
        Set<TileEntityProgrammableGlass> queued=Collections.newSetFromMap(new IdentityHashMap<>());
        synchronized(QUEUED){
            queued.addAll(QUEUED);QUEUED.clear();DIRTY.addAll(INVALIDATED);INVALIDATED.clear();
            if(rescan){
                // Queue overflow may have omitted a removed/moved owner which
                // is no longer in the world's loaded list. Retire its section
                // too, instead of only enrolling the surviving loaded tiles.
                DIRTY.addAll(SECTIONS.keySet());
                for(TileEntity tile:world.loadedTileEntityList)if(tile instanceof TileEntityProgrammableGlass)queued.add((TileEntityProgrammableGlass)tile);
                rescan=false;
            }
        }
        for(TileEntityProgrammableGlass tile:queued)if(installed(tile)){
            long key=sectionKey(tile.getPos());Section section=SECTIONS.computeIfAbsent(key,Section::new);section.tiles.add(tile);DIRTY.add(key);section.current=false;
            if(tile.frameChunkPublication==null)GlassFrameChunks.changed(tile);
        }
        for(long key:DIRTY){Section section=SECTIONS.get(key);if(section!=null&&!section.groups.isEmpty())section.retire();}
        unavailable="none";int count=0;
        Iterator<Long> dirty=DIRTY.iterator();while(dirty.hasNext()&&count++<MAX_SECTIONS_PER_FLUSH){
            long key=dirty.next();dirty.remove();Section section=SECTIONS.get(key);if(section==null)continue;
            rebuild(section);if(section.tiles.isEmpty())SECTIONS.remove(key);
        }
    }
    private static Group current(TileEntityProgrammableGlass tile){
        Group group=OWNERS.get(tile);
        return group!=null&&world==tile.getWorld()&&!tile.isInvalid()&&group.positions.get(tile).equals(tile.getPos())&&group.positions.get(group.anchor).equals(group.anchor.getPos())&&!group.anchor.isInvalid()&&group.current()?group:null;
    }
    public static boolean handles(TileEntityProgrammableGlass tile){return current(tile)!=null;}
    public static boolean anchor(TileEntityProgrammableGlass tile){Group group=current(tile);return group!=null&&group.anchor==tile;}
    public static AxisAlignedBB bounds(TileEntityProgrammableGlass tile){Group group=current(tile);return group!=null&&group.anchor==tile?group.bounds:null;}
    public static boolean draw(TileEntityProgrammableGlass tile,double x,double y,double z){
        Group group=current(tile);if(group==null)return false;
        if(group.anchor==tile&&MinecraftForgeClient.getRenderPass()!=1){
            try{group.draw(x,y,z);}catch(RuntimeException|LinkageError failure){
                drawFailed=true;invalidateSection(tile.getPos());
                if(!warned){warned=true;if(com.vandorlabs.VandorLabs.logger!=null)com.vandorlabs.VandorLabs.logger.warn("Retained frame draw unavailable; restoring frame fallback",failure);}
                return false;
            }
        }
        return true;
    }
    static void legacyDraw(){if(VehicleRenderPass.shadow())legacyShadowDraws++;else legacyMainDraws++;}
    public static String diagnostics(){
        int groups=0;for(Section section:SECTIONS.values())if(section.current)groups+=section.groups.size();
        return "retained-frame-owners="+OWNERS.size()+" retained-frame-groups="+groups+" retained-frame-bytes="+retainedBytes+" retained-frame-pending="+DIRTY.size()
                +" retained-frame-builds="+builds+" retained-frame-uploads="+uploads+" retained-frame-main-draws="+mainDraws+" retained-frame-shadow-draws="+shadowDraws
                +" legacy-frame-main-draws="+legacyMainDraws+" legacy-frame-shadow-draws="+legacyShadowDraws+" retained-frame-fallback="+unavailable+" "+GlassFrameChunks.diagnostics();
    }
    private GlassFrameRetained(){}
}
