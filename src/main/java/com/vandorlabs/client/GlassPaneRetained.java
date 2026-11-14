package com.vandorlabs.client;

import com.vandorlabs.blocks.BlockGlassWall;
import com.vandorlabs.blocks.BlockProgrammableGlass;
import com.vandorlabs.blocks.PanelDepth;
import com.vandorlabs.tiles.GlassMaterials;
import com.vandorlabs.tiles.TileEntityProgrammableGlass;
import com.vandorlabs.vehicle.VehicleWorld;
import java.lang.reflect.Method;
import java.util.*;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
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
 * Retained fallback for stationary flat panes. A group is one coplanar material
 * inside a render section, never the bounding rectangle of its members: missing
 * cells stay empty and each occupied cell keeps its authored UVs and lightmap.
 * The real anchor participates in normal tile/shader passes; other pane-only
 * owners can leave the compiled tile list. Frames have independent ownership.
 */
public final class GlassPaneRetained {
    private static final int LIMIT=16*1024*1024,MAX_GROUP=2*1024*1024,MAX_SECTIONS_PER_FLUSH=8,MAX_QUEUED=32768;
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
    private static boolean rescan=true,warned,drawFailed;
    private static int retainedBytes;
    static long builds,uploads,mainDraws,shadowDraws,generation;
    private static String unavailable="world-unloaded";

    static final class Section {
        final long key;
        final Set<TileEntityProgrammableGlass> tiles=Collections.newSetFromMap(new IdentityHashMap<>());
        final List<Group> groups=new ArrayList<>();
        boolean current;
        Section(long key){this.key=key;}
        void retire(){
            current=false;
            for(Group group:groups){for(TileEntityProgrammableGlass tile:group.members)OWNERS.remove(tile,group);group.delete();}
            groups.clear();
            GlassPaneTerrain.refreshAdmissions(BlockPos.fromLong(key));
        }
    }

    /** A shader material boundary, coplanar placement and culling mode. */
    static final class Key {
        final IBlockState state;
        final TextureAtlasSprite sprite;
        final int shader,opacity,axis;
        final long plane;
        final boolean single,palette,opaque;
        Key(IBlockState state,TextureAtlasSprite sprite,int shader,float opacity,boolean single,boolean palette,BlockPos pos){
            this(state,sprite,shader,opacity,single,palette,false,pos);
        }
        Key(IBlockState state,TextureAtlasSprite sprite,int shader,float opacity,boolean single,boolean palette,boolean opaque,BlockPos pos){
            this.state=state;this.sprite=sprite;this.shader=shader;this.opacity=Float.floatToRawIntBits(opacity);this.single=single;this.palette=palette;this.opaque=opaque;
            boolean rotated=state.getValue(BlockGlassWall.ROTATED);axis=rotated?0:2;
            double offset=PanelDepth.offset(state.getValue(BlockGlassWall.DEPTH))/16D;
            plane=Double.doubleToLongBits((rotated?pos.getX():pos.getZ())+.5+(rotated?-offset:offset));
        }
        public int hashCode(){return Objects.hash(System.identityHashCode(state),System.identityHashCode(sprite),shader,opacity,axis,plane,single,palette,opaque);}
        public boolean equals(Object other){
            if(!(other instanceof Key))return false;Key k=(Key)other;
            return state==k.state&&sprite==k.sprite&&shader==k.shader&&opacity==k.opacity&&axis==k.axis&&plane==k.plane&&single==k.single&&palette==k.palette&&opaque==k.opaque;
        }
    }

    static final class Group {
        final List<TileEntityProgrammableGlass> members;
        final TileEntityProgrammableGlass anchor;
        final Key key;
        final AxisAlignedBB bounds;
        final VertexFormat format;
        final int[] records;
        final int vertices,bytes;
        final Map<TileEntityProgrammableGlass,BlockPos> positions=new IdentityHashMap<>();
        private final Section section;
        private VertexBuffer buffer;
        private boolean accounted;
        Group(Section section,Key key,List<TileEntityProgrammableGlass> members,VertexFormat format,int[] records){
            this.section=section;this.key=key;this.format=new VertexFormat(format);this.records=records;
            List<TileEntityProgrammableGlass> sorted=new ArrayList<>(members);sorted.sort(Comparator.comparing(TileEntity::getPos));
            this.members=Collections.unmodifiableList(sorted);anchor=sorted.get(0);
            AxisAlignedBB box=null;
            for(TileEntityProgrammableGlass tile:sorted){BlockPos pos=tile.getPos().toImmutable();positions.put(tile,pos);AxisAlignedBB next=new AxisAlignedBB(pos);box=box==null?next:box.union(next);}
            bounds=box;vertices=records.length/format.getIntegerSize();bytes=records.length*8+256+members.size()*80;
        }
        int[] recordCopy(){return records.clone();}
        void upload(){
            JOINED.begin(GL11.GL_QUADS,format);
            try{JOINED.setVertexState(JOINED.new State(records,new VertexFormat(format)));}finally{JOINED.finishDrawing();}
            buffer=new VertexBuffer(format);buffer.bufferData(JOINED.getByteBuffer());retainedBytes+=bytes;accounted=true;uploads++;
        }
        boolean current(){return buffer!=null&&section.current&&format.equals(BlockSurfaceFormat.get());}
        void delete(){if(buffer!=null){buffer.deleteGlBuffers();buffer=null;}if(accounted){retainedBytes-=bytes;accounted=false;}}
        void draw(double x,double y,double z){
            boolean shadow=VehicleRenderPass.shadow();
            if(GlassPanePolicy.omitPane(key.opaque,shadow))return;
            if(format.getNextOffset()>32)try{SHADER_TILE.invoke(null,anchor);}catch(ReflectiveOperationException failure){throw new IllegalStateException("Cannot select retained glass shader material",failure);}
            GlassPaneGpuTimers.Token timing=GlassPaneGpuTimers.begin(shadow?"retained-shadow":"retained-main");
            GlStateManager.pushMatrix();
            try{
                GlStateManager.translate(x,y,z);
                Minecraft.getMinecraft().getTextureManager().bindTexture(TextureMap.LOCATION_BLOCKS_TEXTURE);
                boolean cull=key.palette&&!key.opaque;
                GlStateManager.disableLighting();if(cull)GlStateManager.enableCull();else GlStateManager.disableCull();
                if(key.opaque)GlStateManager.disableBlend();else GlStateManager.enableBlend();
                GlStateManager.tryBlendFuncSeparate(770,771,1,0);
                GlStateManager.alphaFunc(GL11.GL_GREATER,.003F);GlStateManager.depthMask(key.opaque);GlStateManager.color(1,1,1,1);
                BlockMeshVbo.draw(buffer,format,key.opaque?BlockRenderLayer.CUTOUT:BlockRenderLayer.TRANSLUCENT,cull);
                if(shadow)shadowDraws++;else mainDraws++;
            }finally{
                GlStateManager.enableCull();GlStateManager.depthMask(true);GlStateManager.alphaFunc(GL11.GL_GREATER,.1F);
                GlStateManager.disableBlend();GlStateManager.enableLighting();GlStateManager.color(1,1,1,1);
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
    private static boolean installed(TileEntity tile){return world!=null&&tile.getWorld()==world&&!tile.isInvalid()
            &&world.isBlockLoaded(tile.getPos())&&world.getTileEntity(tile.getPos())==tile;}

    public static void changed(TileEntityProgrammableGlass tile){
        if(!GlassPanePolicy.retainedEnabled()||tile.getWorld()==null||!tile.getWorld().isRemote||tile.getWorld() instanceof VehicleWorld)return;
        synchronized(QUEUED){if(QUEUED.size()<MAX_QUEUED)QUEUED.add(tile);else rescan=true;}
        invalidateSection(tile.getPos());
    }
    /** Native publications call this before admissions are swept again. */
    public static void invalidateSection(BlockPos pos){
        if(!GlassPanePolicy.retainedEnabled())return;
        long key=sectionKey(pos);
        synchronized(QUEUED){if(INVALIDATED.size()<MAX_QUEUED)INVALIDATED.add(key);else rescan=true;}
        // Publication observation can run inside RenderGlobal's tile iterator.
        // Retire groups and restore admissions together at the next flush, never
        // mutate an active tile list or drop an anchor halfway through a pass.
    }
    private static void neighbours(BlockPos pos){
        for(int y=-1;y<=1;y++)for(int z=-1;z<=1;z++)for(int x=-1;x<=1;x++)invalidateSection(pos.add(x,y,z));
    }
    private static void range(int x1,int y1,int z1,int x2,int y2,int z2){
        if(!GlassPanePolicy.retainedEnabled())return;
        int ax=Math.min(x1,x2)&~15,bx=Math.max(x1,x2)&~15,ay=Math.min(y1,y2)&~15,by=Math.max(y1,y2)&~15,az=Math.min(z1,z2)&~15,bz=Math.max(z1,z2)&~15;
        long count=((long)bx-ax+16)/16*((long)by-ay+16)/16;
        long depth=((long)bz-az+16)/16;
        synchronized(QUEUED){
            if(count>MAX_QUEUED||depth>MAX_QUEUED||count*depth>MAX_QUEUED){rescan=true;return;}
            for(long y=ay;y<=by;y+=16)for(long z=az;z<=bz;z+=16)for(long x=ax;x<=bx;x+=16){
                if(INVALIDATED.size()>=MAX_QUEUED){rescan=true;return;}
                INVALIDATED.add(new BlockPos((int)x,(int)y,(int)z).toLong());
            }
        }
    }
    public static void reload(){
        format=null;drawFailed=false;
        for(Section section:SECTIONS.values()){section.current=false;DIRTY.add(section.key);GlassPaneTerrain.refreshAdmissions(BlockPos.fromLong(section.key));}
    }
    private static void reset(World next){
        if(world!=null&&listener!=null)world.removeEventListener(listener);
        for(Section section:SECTIONS.values())section.retire();
        SECTIONS.clear();OWNERS.clear();DIRTY.clear();format=null;world=next;drawFailed=false;generation++;
        synchronized(QUEUED){QUEUED.removeIf(tile->tile.getWorld()!=next);INVALIDATED.clear();rescan=true;}
        if(next==null)return;
        listener=new WorldRenderListener(){
            @Override public void notifyBlockUpdate(World changed,BlockPos pos,IBlockState before,IBlockState after,int flags){
                if(changed==world)neighbours(pos);
            }
            @Override public void notifyLightSet(BlockPos pos){neighbours(pos);}
            @Override public void markBlockRangeForRenderUpdate(int x1,int y1,int z1,int x2,int y2,int z2){
                range(x1,y1,z1,x2,y2,z2);
            }
        };next.addEventListener(listener);
    }

    private static Key selection(TileEntityProgrammableGlass tile){
        if(!installed(tile)||tile.getWorld() instanceof VehicleWorld||tile.fluids.any()
                ||!GlazingDisplay.atlasStatic(tile)||!GlassPaneTerrain.fallbackSafe(tile))return null;
        IBlockState state=world.getBlockState(tile.getPos());
        if(!(state.getBlock() instanceof BlockProgrammableGlass)||GlassSurfaceGroups.flatCorner(tile,state)!=null)return null;
        int shader=format.getNextOffset()>32?SHADER_MATERIAL.key(tile):GlassShaderMaterial.UNAVAILABLE;
        boolean opaque=GlassMaterials.opaque(tile.getMaterial(),tile.getTint());
        if(tile.getMaterial()<0){
            if(opaque){
                TextureAtlasSprite sprite=Minecraft.getMinecraft().getTextureMapBlocks().getAtlasSprite(GlassMaterials.texture(-1,tile.getShade(),tile.getSize(),tile.getTint()));
                return new Key(state,sprite,shader,1,false,true,true,tile.getPos());
            }
            GlassPaneTextures.Selection material=GlassPaneTextures.select(tile.getShade(),tile.getTint());
            return material==null?null:new Key(state,material.sprite,shader,1,material.singleSurface,true,tile.getPos());
        }
        TextureAtlasSprite sprite=Minecraft.getMinecraft().getTextureMapBlocks().getAtlasSprite(GlassMaterials.texture(tile.getMaterial(),tile.getShade(),tile.getSize(),tile.getTint()));
        return new Key(state,sprite,shader,GlassMaterials.opacity(tile.getMaterial(),tile.getTint()),false,false,opaque,tile.getPos());
    }

    /** Real ordinary vertex writers generate per-component optional shader data once. */
    static int[] capture(Key key,List<TileEntityProgrammableGlass> members,VertexFormat format,BlockPos origin){
        List<int[]> components=new ArrayList<>();int size=0;
        for(TileEntityProgrammableGlass tile:members){
            COMPONENT.begin(GL11.GL_QUADS,format);boolean context=false;
            try{
                context=VehicleShaderContext.push(tile,COMPONENT);
                int light=tile.getWorld().getCombinedLight(tile.getPos(),0);
                light=(light&0xffff0000)|GlassInsertLighting.block(tile,light&65535);
                double x=tile.getPos().getX()-origin.getX(),y=tile.getPos().getY()-origin.getY(),z=tile.getPos().getZ()-origin.getZ();
                if(key.palette&&key.opaque)GlassNativePanes.get(tile.getShade(),tile.getSize(),tile.getTint(),key.state.getValue(BlockGlassWall.ROTATED),key.state.getValue(BlockGlassWall.DEPTH)).draw(COMPONENT,x,y,z,light>>>16,light&65535);
                else if(key.palette)emitPalette(COMPONENT,key,x,y,z,light);
                else{
                    boolean rotated=key.state.getValue(BlockGlassWall.ROTATED);double offset=PanelDepth.offset(key.state.getValue(BlockGlassWall.DEPTH))/16D;
                    StaticSurfaceMesh mesh=GlassSurfaceMapping.map(TEProgrammableGlass.materialPlane(),key.sprite,GlassSurfaceGroups.layout(tile),true,0,0,tile.getTextureLayout());
                    mesh.drawPlacedColored(COMPONENT,x-(rotated?offset:0),y,z+(rotated?0:offset),rotated?3:0,light,Float.intBitsToFloat(key.opacity));
                }
            }finally{try{if(context)VehicleShaderContext.pop(COMPONENT);}finally{COMPONENT.finishDrawing();COMPONENT.setTranslation(0,0,0);}}
            int[] record=COMPONENT.getVertexState().getRawBuffer();size=Math.addExact(size,record.length);
            if(size>MAX_GROUP/4)throw new IllegalStateException("Retained glass group exceeds 2 MiB");components.add(record);
        }
        int[] result=new int[size];int at=0;
        for(int[] component:components){System.arraycopy(component,0,result,at,component.length);at+=component.length;}
        return result;
    }
    static void emitPalette(BufferBuilder out,Key key,double x,double y,double z,int light){
        boolean rotated=key.state.getValue(BlockGlassWall.ROTATED);double offset=PanelDepth.offset(key.state.getValue(BlockGlassWall.DEPTH))/16D;
        int sheets=key.single?1:2;
        for(int sheet=0;sheet<sheets;sheet++)for(int face=0;face<2;face++)for(int v=0;v<4;v++){
            boolean near=face==0;int index=near?(4-v)&3:v;
            double u=index==1||index==2?1:0,h=index<2?0:1,plane=(key.single?.5:sheet==0?7D/16:9D/16)+offset;
            out.pos(x+(rotated?1-plane:u),y+h,z+(rotated?u:plane)).color(255,255,255,255)
                    .tex(key.sprite.getInterpolatedU(u*16),key.sprite.getInterpolatedV((1-h)*16)).lightmap(light>>>16,light&65535)
                    .normal(rotated?(near?1:-1):0,0,rotated?0:(near?-1:1)).endVertex();
        }
    }
    private static void rebuild(Section section){
        section.retire();section.tiles.removeIf(tile->!installed(tile)||sectionKey(tile.getPos())!=section.key);
        Map<Key,List<TileEntityProgrammableGlass>> grouped=new LinkedHashMap<>();
        for(TileEntityProgrammableGlass tile:section.tiles){Key key=selection(tile);if(key!=null)grouped.computeIfAbsent(key,k->new ArrayList<>()).add(tile);}
        for(Map.Entry<Key,List<TileEntityProgrammableGlass>> entry:grouped.entrySet()){
            List<TileEntityProgrammableGlass> members=entry.getValue();members.sort(Comparator.comparing(TileEntity::getPos));
            Group group=null;
            try{
                group=new Group(section,entry.getKey(),members,format,capture(entry.getKey(),members,format,members.get(0).getPos()));
                if(retainedBytes+group.bytes>LIMIT){unavailable="retained-byte-limit";continue;}
                group.upload();section.groups.add(group);for(TileEntityProgrammableGlass tile:members)OWNERS.put(tile,group);builds++;
            }catch(RuntimeException|LinkageError failure){
                if(group!=null)group.delete();unavailable="retained-build-failed";
                if(!warned){warned=true;if(com.vandorlabs.VandorLabs.logger!=null)com.vandorlabs.VandorLabs.logger.warn("Retained glass unavailable; keeping individual pane rendering",failure);}
            }
        }
        section.current=true;generation++;GlassPaneTerrain.refreshAdmissions(BlockPos.fromLong(section.key));
    }
    public static void flush(){
        Minecraft mc=Minecraft.getMinecraft();if(mc==null)return;
        if(mc.world!=world)reset(mc.world);
        if(world==null){unavailable="world-unloaded";return;}
        if(!GlassPanePolicy.retainedEnabled()||WorldSceneBenchmark.omitGlazing()||!OpenGlHelper.useVbo()){
            if(!SECTIONS.isEmpty())reset(world);
            if(!GlassPanePolicy.retainedEnabled()&&listener!=null){world.removeEventListener(listener);listener=null;}
            unavailable="retained-disabled";return;
        }
        VertexFormat current=BlockSurfaceFormat.get();
        if(format==null||!format.equals(current)){reload();format=new VertexFormat(current);}
        if(drawFailed||!BlockMeshVbo.supportsRetained(current)||(current.getNextOffset()>32&&(SHADER_TILE==null||!VehicleShaderContext.available()))){
            for(Section section:SECTIONS.values()){if(!section.groups.isEmpty())section.retire();DIRTY.add(section.key);}
            unavailable=drawFailed?"retained-draw-failed":"unsupported-shader-hooks";return;
        }
        Set<TileEntityProgrammableGlass> queued=Collections.newSetFromMap(new IdentityHashMap<>());
        synchronized(QUEUED){
            queued.addAll(QUEUED);QUEUED.clear();DIRTY.addAll(INVALIDATED);INVALIDATED.clear();
            if(rescan){for(Section section:SECTIONS.values())DIRTY.add(section.key);for(TileEntity tile:world.loadedTileEntityList)if(tile instanceof TileEntityProgrammableGlass)queued.add((TileEntityProgrammableGlass)tile);rescan=false;}
        }
        for(TileEntityProgrammableGlass tile:queued)if(installed(tile)){
            long key=sectionKey(tile.getPos());Section section=SECTIONS.computeIfAbsent(key,Section::new);section.tiles.add(tile);DIRTY.add(key);section.current=false;
        }
        // All stale groups leave ownership before the native manager publishes
        // any replacement snapshots, including sections beyond this build budget.
        for(long key:DIRTY){Section section=SECTIONS.get(key);if(section!=null&&!section.groups.isEmpty())section.retire();}
        unavailable="none";int rebuilt=0;
        Iterator<Long> dirty=DIRTY.iterator();
        while(dirty.hasNext()&&rebuilt++<MAX_SECTIONS_PER_FLUSH){
            long key=dirty.next();dirty.remove();Section section=SECTIONS.get(key);if(section==null)continue;
            rebuild(section);if(section.tiles.isEmpty())SECTIONS.remove(key);
        }
    }
    private static Group current(TileEntityProgrammableGlass tile){
        Group group=OWNERS.get(tile);
        return group!=null&&world==tile.getWorld()&&!tile.isInvalid()&&group.positions.get(tile).equals(tile.getPos())
                &&group.positions.get(group.anchor).equals(group.anchor.getPos())&&!group.anchor.isInvalid()&&group.current()?group:null;
    }
    public static boolean handles(TileEntityProgrammableGlass tile){return current(tile)!=null;}
    public static boolean anchor(TileEntityProgrammableGlass tile){Group group=current(tile);return group!=null&&group.anchor==tile;}
    public static AxisAlignedBB bounds(TileEntityProgrammableGlass tile){Group group=current(tile);return group!=null&&group.anchor==tile?group.bounds:null;}
    static double plane(TileEntityProgrammableGlass tile){Group group=current(tile);return group==null?Double.NaN:Double.longBitsToDouble(group.key.plane);}
    /** Never remove an anchor or an owner whose exceptional frame still needs a TESR. */
    public static boolean suppress(TileEntityProgrammableGlass tile){Group group=current(tile);return group!=null&&group.anchor!=tile&&!tile.fluids.any()&&!ProgrammableGlassPasses.frameFallbackInPass(tile,0);}
    public static int renderPass(TileEntityProgrammableGlass tile,int pass){
        Group group=current(tile);if(group==null)return -1;
        if(pass==0)return group.anchor==tile?1:ProgrammableGlassPasses.frameFallbackInPass(tile,0)?-1:0;
        if(pass==1)return group.anchor==tile&&!group.key.opaque&&!GlassPanePolicy.omitPane(false,VehicleRenderPass.shadow())?1:0;
        return 0;
    }
    public static boolean draw(TileEntityProgrammableGlass tile,double x,double y,double z){
        Group group=current(tile);if(group==null)return false;
        int pass=MinecraftForgeClient.getRenderPass();
        if(group.anchor==tile&&(pass<0||pass==(group.key.opaque?0:1))){
            try{group.draw(x,y,z);}catch(RuntimeException|LinkageError failure){
                // Do not mutate RenderGlobal's active tile iterator here. Retire
                // and restore every member at the next safe flush boundary.
                drawFailed=true;invalidateSection(group.anchor.getPos());
                if(!warned){warned=true;if(com.vandorlabs.VandorLabs.logger!=null)com.vandorlabs.VandorLabs.logger.warn("Retained glass draw unavailable; restoring individual panes",failure);}
                return false;
            }
        }
        return true;
    }
    public static String diagnostics(){
        int groups=0;for(Section section:SECTIONS.values())if(section.current)groups+=section.groups.size();
        return "retained-owners="+OWNERS.size()+" retained-groups="+groups+" retained-bytes="+retainedBytes+" retained-pending="+DIRTY.size()
                +" retained-builds="+builds+" retained-uploads="+uploads+" retained-main-draws="+mainDraws+" retained-shadow-draws="+shadowDraws+" retained-fallback="+unavailable;
    }
    private GlassPaneRetained(){}
}
