package com.vandorlabs.client.segmented;

import com.vandorlabs.segmented.SegmentedDoorTile;
import com.vandorlabs.segmented.SegmentedDoorGeometry;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.texture.*;
import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.util.EnumFacing;
import org.lwjgl.opengl.GL11;

/** One shared atlas submission for every visible column, with allocation-free rigid panels. */
@net.minecraftforge.fml.common.Mod.EventBusSubscriber(modid="vandorlabs",value=net.minecraftforge.fml.relauncher.Side.CLIENT)
@net.minecraftforge.fml.relauncher.SideOnly(net.minecraftforge.fml.relauncher.Side.CLIENT)
public final class SegmentedDoorRenderer extends TileEntitySpecialRenderer<SegmentedDoorTile> {
    @net.minecraftforge.fml.common.eventhandler.SubscribeEvent public static void stitch(net.minecraftforge.client.event.TextureStitchEvent.Pre event){clearTemplates();event.getMap().registerSprite(new net.minecraft.util.ResourceLocation("vandorlabs:blocks/segmented/segmented_panel"));if(event.getMap()==Minecraft.getMinecraft().getTextureMapBlocks()){com.vandorlabs.client.SegmentedGateContexts.clear();gatePending=com.vandorlabs.client.SegmentedGateResources.prepare(Minecraft.getMinecraft().getResourceManager());gatePending.stitch(event.getMap());}}
    @net.minecraftforge.fml.common.eventhandler.SubscribeEvent public static void stitched(net.minecraftforge.client.event.TextureStitchEvent.Post event){if(event.getMap()==Minecraft.getMinecraft().getTextureMapBlocks()&&gatePending!=null){gateBake=gatePending.bake(event.getMap());gatePending=null;}}
    private static com.vandorlabs.client.SegmentedGateResources.Prepared gatePending;
    private static volatile com.vandorlabs.client.SegmentedGateMeshes gateBake;
    public static com.vandorlabs.client.SegmentedGateMeshes currentGateBake(){return gateBake;}
    private final com.vandorlabs.segmented.SegmentedGateContext.Scratch gateScratch=new com.vandorlabs.segmented.SegmentedGateContext.Scratch();
    private final double[] section=new double[6];
    private Capture capture=new Capture();
    private final net.minecraft.util.math.BlockPos.MutableBlockPos lightPos=new net.minecraft.util.math.BlockPos.MutableBlockPos();
    private static final int CACHE_BYTES=2*1024*1024;
    private static final java.util.Map<Long,Template> TEMPLATES=new java.util.LinkedHashMap<>(32,.75F,true);
    private static net.minecraft.client.renderer.vertex.VertexFormat templateFormat;
    private static int retained,columnBytes;
    private static long lightSamples,dynamicRevision;
    private static boolean hadDynamicLights,dynamicUnknown;
    private static final java.lang.reflect.Method DYNAMIC_COUNT=dynamicCount();
    private static java.lang.reflect.Method dynamicCount(){try{return Class.forName("net.optifine.DynamicLights").getMethod("getCount");}catch(ClassNotFoundException absent){return null;}catch(ReflectiveOperationException incompatible){dynamicUnknown=true;com.vandorlabs.VandorLabs.logger.warn("Segmented lighting cache uses per-frame fallback for unsupported dynamic lights",incompatible);return null;}}
    @net.minecraftforge.fml.common.eventhandler.SubscribeEvent public static void frame(net.minecraftforge.fml.common.gameevent.TickEvent.RenderTickEvent event){
        if(event.phase!=net.minecraftforge.fml.common.gameevent.TickEvent.Phase.START)return;boolean active=dynamicUnknown;
        if(DYNAMIC_COUNT!=null)try{active=((Number)DYNAMIC_COUNT.invoke(null)).intValue()>0;}catch(ReflectiveOperationException failure){active=true;}
        if(active||hadDynamicLights)dynamicRevision++;hadDynamicLights=active;
    }
    public static long lightSamples(){return lightSamples;}
    public static int retainedBytes(){return retained+columnBytes+com.vandorlabs.client.SegmentedDoorPanels.retainedBytes();}
    private static final int COLUMN_BYTES=16*1024*1024;
    private static final java.util.Map<SegmentedDoorTile,Column[]> COLUMNS=new java.util.WeakHashMap<>();
    private static final java.util.Map<Column,Boolean> COLUMN_LRU=new java.util.LinkedHashMap<>(128,.75F,true);
    private static final java.util.Map<net.minecraft.world.World,LightChanges> LIGHTS=new java.util.IdentityHashMap<>();
    private static final class LightChanges extends com.vandorlabs.world.WorldListener {
        long revision;
        @Override public void notifyLightSet(net.minecraft.util.math.BlockPos pos){revision++;}
        @Override public void notifyBlockUpdate(net.minecraft.world.World world,net.minecraft.util.math.BlockPos pos,net.minecraft.block.state.IBlockState before,net.minecraft.block.state.IBlockState after,int flags){revision++;}
    }
    private static final class Column {
        long key=-1,rawKey,revision=-1,origin,dynamic=-1;int phase;int vehicleRevision;java.lang.ref.WeakReference<Object> structure;final java.lang.ref.WeakReference<SegmentedDoorTile> tile;
        final int pass;Config config;Column(SegmentedDoorTile tile,int pass){this.tile=new java.lang.ref.WeakReference<>(tile);this.pass=pass;}double x=Double.NaN,y,z;int[] data;final int[] lights=new int[SegmentedDoorTile.MAX_HEIGHT];
        int bytes(){return data.length*4+528+(config==null?0:config.retainedBytes());}
    }
    private static LightChanges lightChanges(net.minecraft.world.World world){
        while(world instanceof com.vandorlabs.vehicle.VehicleWorld)world=((com.vandorlabs.vehicle.VehicleWorld)world).parent;
        LightChanges changes=LIGHTS.get(world);if(changes==null){changes=new LightChanges();LIGHTS.put(world,changes);world.addEventListener(changes);}return changes;
    }
    @net.minecraftforge.fml.common.eventhandler.SubscribeEvent public static void unload(net.minecraftforge.event.world.WorldEvent.Unload event){if(event.getWorld().isRemote)Minecraft.getMinecraft().addScheduledTask(()->{LightChanges observer=LIGHTS.remove(event.getWorld());if(observer!=null)event.getWorld().removeEventListener(observer);clearTemplates();});}

    /** Hash lookup plus exact descriptor equality prevents atlas cache aliasing. */
    private static final class Config {
        final com.vandorlabs.segmented.SegmentedDoorWindows.Layout[] windows;com.vandorlabs.segmented.SegmentedDoorSettings settings;final com.vandorlabs.segmented.SegmentedDoorSettings[] panels;final int[] artwork;int texture,height,column,columns,frame,steps,pass,face;long key;
        Config(){windows=new com.vandorlabs.segmented.SegmentedDoorWindows.Layout[SegmentedDoorTile.MAX_HEIGHT];panels=new com.vandorlabs.segmented.SegmentedDoorSettings[SegmentedDoorTile.MAX_HEIGHT];artwork=new int[SegmentedDoorTile.MAX_HEIGHT];}
        Config(Config probe){windows=java.util.Arrays.copyOf(probe.windows,probe.height);panels=java.util.Arrays.copyOf(probe.panels,probe.height);artwork=java.util.Arrays.copyOf(probe.artwork,probe.height);settings=probe.settings;texture=probe.texture;height=probe.height;column=probe.column;columns=probe.columns;frame=probe.frame;steps=probe.steps;pass=probe.pass;face=probe.face;key=probe.key;}
        Config load(SegmentedDoorTile t,int frame,int steps,int pass){settings=t.settings;for(int row=0;row<t.height;row++){SegmentedDoorTile member=t.panel(row);artwork[row]=member.effectiveTexture();windows[row]=com.vandorlabs.segmented.SegmentedDoorWindows.layout(member);panels[row]=member.settings;}texture=t.texture;height=t.height;column=t.column;columns=t.columns;this.frame=frame;this.steps=steps;this.pass=pass;face=t.facing().getIndex();long h=1469598103934665603L;h=mix(h,settings.hashCode());h=mix(h,texture);h=mix(h,height);h=mix(h,column);h=mix(h,columns);h=mix(h,frame);h=mix(h,steps);h=mix(h,pass);h=mix(h,face);for(int row=0;row<height;row++)h=mix(h,artwork[row]);for(int row=0;row<height;row++)h=mix(h,panels[row].hashCode());for(int row=0;row<height;row++)h=mix(h,windows[row].hashCode());key=h|Long.MIN_VALUE;return this;}
        private static long mix(long hash,int value){return (hash^value)*1099511628211L;}
        int retainedBytes(){return 256+height*192;}
        boolean matches(SegmentedDoorTile t,int frame,int steps,int pass){if(this.frame!=frame||this.steps!=steps||this.pass!=pass||texture!=t.texture||height!=t.height||column!=t.column||columns!=t.columns||face!=t.facing().getIndex()||!settings.equals(t.settings))return false;for(int row=0;row<height;row++){SegmentedDoorTile member=t.panel(row);if(artwork[row]!=member.effectiveTexture()||!panels[row].equals(member.settings)||!windows[row].equals(com.vandorlabs.segmented.SegmentedDoorWindows.layout(member)))return false;}return true;}
        public int hashCode(){return (int)(key^(key>>>32));}
        public boolean equals(Object o){if(this==o)return true;if(!(o instanceof Config))return false;Config c=(Config)o;if(key!=c.key||texture!=c.texture||height!=c.height||column!=c.column||columns!=c.columns||frame!=c.frame||steps!=c.steps||pass!=c.pass||face!=c.face||!settings.equals(c.settings))return false;for(int row=0;row<height;row++)if(artwork[row]!=c.artwork[row]||!panels[row].equals(c.panels[row])||!windows[row].equals(c.windows[row]))return false;return true;}
    }
    // This bounded scratch descriptor never enters a column or retained template.
    private final Config lookupConfig=new Config();
    private static final class Capture extends BufferBuilder {
        int row;byte[] rows=new byte[SegmentedDoorTile.MAX_HEIGHT*24];
        Capture(){super(SegmentedDoorTile.MAX_HEIGHT*24*16);}
        @Override public void endVertex(){if(getVertexCount()==rows.length)rows=java.util.Arrays.copyOf(rows,rows.length*2);rows[getVertexCount()]=(byte)row;super.endVertex();}
    }
    private static final class Template {
        final int[] data;final byte[] rows;final int stride,lightOffset;int bytes;Config config;long rawKey;int phase;
        Template(Capture b){data=b.getVertexState().getRawBuffer();rows=java.util.Arrays.copyOf(b.rows,b.getVertexCount());stride=b.getVertexFormat().getIntegerSize();lightOffset=b.getVertexFormat().getUvOffsetById(1)/4;bytes=data.length*4+rows.length+272;}
    }
    private static void clearTemplates(){com.vandorlabs.client.SegmentedDoorPanels.clear();TEMPLATES.clear();templateFormat=null;retained=0;COLUMNS.clear();COLUMN_LRU.clear();columnBytes=0;}
    private void cached(SegmentedDoorTile tile,double x,double y,double z,BufferBuilder buffer,double progress,int pass){
        if(templateFormat==null||!templateFormat.equals(buffer.getVertexFormat())){clearTemplates();capture=new Capture();templateFormat=new net.minecraft.client.renderer.vertex.VertexFormat(buffer.getVertexFormat());}
        boolean nativePanel=tile.canBake();
        int steps=(tile.settings.direction<2?tile.height:tile.columns)*tile.ticksPerSection*6,frame=(int)Math.round(progress*steps);progress=frame/(double)steps;
        long rawKey=((long)(tile.effectiveTexture()+1)<<48)|((long)steps<<24)|((long)frame<<8)|((tile.height-1)<<2)|(((int)(180-tile.facing().getHorizontalAngle())/90)&3);
        double aspect=nativePanel&&pass==0?com.vandorlabs.client.SegmentedDoorPanels.aspect(tile.effectiveTexture()):1;int phase=aspect>1?(int)(com.vandorlabs.segmented.SegmentedPanelMesh.tilePhase(tile.column,0,aspect)>>>32):0;
        long key=phase==0?rawKey:((rawKey*1099511628211L)^phase)&Long.MAX_VALUE;
        Column[] passes=COLUMNS.get(tile);if(passes==null){passes=new Column[2];COLUMNS.put(tile,passes);}Column column=passes[pass];if(column==null){column=new Column(tile,pass);passes[pass]=column;}
        boolean features=!nativePanel||!tile.uniformArtwork()||pass!=0;Config config=null;if(features){config=column.config;if(config==null||!config.matches(tile,frame,steps,pass)){config=lookupConfig.load(tile,frame,steps,pass);}key=config.key;}
        COLUMN_LRU.put(column,Boolean.TRUE);
        LightChanges changes=lightChanges(tile.getWorld());long origin=0;int vehicleRevision=0;Object structure=null;
        if(tile.getWorld() instanceof com.vandorlabs.vehicle.VehicleWorld){com.vandorlabs.vehicle.VehicleWorld vehicle=(com.vandorlabs.vehicle.VehicleWorld)tile.getWorld();origin=vehicle.origin.toLong();vehicleRevision=vehicle.tileRevision;structure=vehicle.structure;}
        boolean lighting=column.dynamic!=dynamicRevision||column.revision!=changes.revision||column.origin!=origin||column.vehicleRevision!=vehicleRevision||(column.structure==null?null:column.structure.get())!=structure;
        boolean geometry=column.key!=key||column.x!=x||column.y!=y||column.z!=z||!java.util.Objects.equals(column.config,config)||!features&&(column.rawKey!=rawKey||column.phase!=phase);
        Template mesh=null;
        if(geometry){
            int oldBytes=column.data==null?0:column.bytes();
            mesh=TEMPLATES.get(key);
            if(mesh==null||!java.util.Objects.equals(mesh.config,config)||!features&&(mesh.rawKey!=rawKey||mesh.phase!=phase)){if(mesh!=null){TEMPLATES.remove(key);retained-=mesh.bytes;}capture.begin(GL11.GL_QUADS,templateFormat);emit(tile,0,0,0,capture,progress,true,pass);capture.finishDrawing();mesh=new Template(capture);mesh.config=config==lookupConfig?new Config(lookupConfig):config;mesh.bytes+=mesh.config==null?0:mesh.config.retainedBytes();mesh.rawKey=rawKey;mesh.phase=phase;while(!TEMPLATES.isEmpty()&&retained+mesh.bytes>CACHE_BYTES){java.util.Iterator<Template> it=TEMPLATES.values().iterator();Template old=it.next();it.remove();retained-=old.bytes;}if(mesh.bytes<=CACHE_BYTES){TEMPLATES.put(key,mesh);retained+=mesh.bytes;}}
            if(column.data==null||column.data.length!=mesh.data.length){column.data=new int[mesh.data.length];lighting=true;}
            System.arraycopy(mesh.data,0,column.data,0,mesh.data.length);
            for(int v=0;v<mesh.rows.length;v++){int at=v*mesh.stride;column.data[at]=Float.floatToRawIntBits((float)(Float.intBitsToFloat(column.data[at])+x));column.data[at+1]=Float.floatToRawIntBits((float)(Float.intBitsToFloat(column.data[at+1])+y));column.data[at+2]=Float.floatToRawIntBits((float)(Float.intBitsToFloat(column.data[at+2])+z));}
            column.key=key;column.rawKey=rawKey;column.phase=phase;column.config=mesh.config;column.x=x;column.y=y;column.z=z;columnBytes+=column.bytes()-oldBytes;
        }
        if(lighting){for(int row=0;row<tile.height;row++){lightSamples++;int light=tile.getWorld().getCombinedLight(lightPos.setPos(tile.getPos().getX(),tile.getPos().getY()-row,tile.getPos().getZ()),0);column.lights[row]=light;}column.dynamic=dynamicRevision;column.revision=changes.revision;column.origin=origin;column.vehicleRevision=vehicleRevision;column.structure=structure==null?null:new java.lang.ref.WeakReference<>(structure);}
        if(geometry||lighting){if(mesh==null)mesh=TEMPLATES.get(key);if(mesh==null){column.key=-1;cached(tile,x,y,z,buffer,progress,pass);return;}for(int v=0;v<mesh.rows.length;v++)column.data[v*mesh.stride+mesh.lightOffset]=column.lights[mesh.rows[v]];}
        buffer.setTranslation(0,0,0);buffer.addVertexData(column.data);
        while(columnBytes>COLUMN_BYTES){java.util.Iterator<Column> it=COLUMN_LRU.keySet().iterator();Column old=it.next();it.remove();Column[] slots=COLUMNS.get(old.tile.get());if(slots!=null){slots[old.pass]=null;if(slots[0]==null&&slots[1]==null)COLUMNS.remove(old.tile.get());}columnBytes-=old.bytes();}
    }

    @Override public boolean isGlobalRenderer(SegmentedDoorTile tile){return tile.isRoot();}
    @Override public void renderTileEntityFast(SegmentedDoorTile tile,double x,double y,double z,float partial,int destroy,float alpha,BufferBuilder b){
        if(!tile.isRoot()||!tile.ready())return;if(tile.clientSuppressed){if(net.minecraftforge.client.MinecraftForgeClient.getRenderPass()==0)header(tile,x,y,z,b);return;}
        double progress=SegmentedDoorVisuals.pose(tile,partial);int pass=Math.max(0,net.minecraftforge.client.MinecraftForgeClient.getRenderPass());com.vandorlabs.client.SegmentedGateContexts.Prepared prepared=com.vandorlabs.client.SegmentedGateContexts.prepare(tile);
        if(prepared!=null&&prepared.complex){complex(tile,prepared,x,y,z,b,progress,partial,pass);return;}
        cached(tile,x,y,z,b,progress,pass);
    }
    private void complex(SegmentedDoorTile tile,com.vandorlabs.client.SegmentedGateContexts.Prepared prepared,double x,double y,double z,BufferBuilder buffer,double progress,float partial,int pass){
        if(pass==1&&(prepared.flags&2)==0)return;buffer.setTranslation(0,0,0);LightChanges changes=lightChanges(tile.getWorld());long origin=0;int vehicleRevision=0;Object structure=null;if(tile.getWorld() instanceof com.vandorlabs.vehicle.VehicleWorld){com.vandorlabs.vehicle.VehicleWorld vehicle=(com.vandorlabs.vehicle.VehicleWorld)tile.getWorld();origin=vehicle.origin.toLong();vehicleRevision=vehicle.tileRevision;structure=vehicle.structure;}
        if(prepared.lightRevision!=changes.revision||prepared.dynamic!=dynamicRevision||prepared.origin!=origin||prepared.vehicleRevision!=vehicleRevision||(prepared.structure==null?null:prepared.structure.get())!=structure){for(int row=0;row<prepared.height;row++){lightSamples++;prepared.lights[row]=tile.getWorld().getCombinedLight(lightPos.setPos(tile.getPos().getX(),tile.getPos().getY()-row,tile.getPos().getZ()),0);}prepared.lightRevision=changes.revision;prepared.dynamic=dynamicRevision;prepared.origin=origin;prepared.vehicleRevision=vehicleRevision;prepared.structure=structure==null?null:new java.lang.ref.WeakReference<>(structure);}
        if(pass==0)prepared.context.prepare(progress,prepared.leftPose(partial),prepared.rightPose(partial),prepared.rotation,gateScratch);
        for(int row=0;row<prepared.height;row++){SegmentedDoorGeometry.panel(row,prepared.height,prepared.column,prepared.width,progress,prepared.settings.direction,prepared.settings.straight,section);section[2]+=prepared.settings.depthOffset();int kind=prepared.kind(row);if(kind>=0){if(pass!=0)continue;com.vandorlabs.client.SegmentedGateMeshes bake=gateBake;if(bake==null)continue;int mask=prepared.context.preparedMask(row,gateScratch),below=row+1<prepared.height?prepared.context.preparedMask(row+1,gateScratch):0,above=row>0?prepared.context.preparedMask(row-1,gateScratch):0;bake.emit(kind,mask,below,above,buffer,x,y,z,section,prepared.rotation,prepared.lights[row]);}else prepared.emitPanel(row,pass,buffer,x,y,z,section,prepared.lights[row]);}
        if(pass==0)headerPrepared(prepared,x,y,z,buffer);
    }
    private void headerPrepared(com.vandorlabs.client.SegmentedGateContexts.Prepared prepared,double x,double y,double z,BufferBuilder buffer){int direction=prepared.settings.direction;if(direction==0&&prepared.settings.position==0||direction==2&&prepared.column!=0||direction==3&&prepared.column!=prepared.width-1)return;HeaderAdapter adapter=HEADER.get();adapter.edge=Minecraft.getMinecraft().getTextureMapBlocks().getAtlasSprite("vandorlabs:blocks/programmable_glass/metal_side");adapter.buffer=buffer;adapter.light=prepared.lights[0];adapter.normals=buffer.getVertexFormat().hasNormal();try{com.vandorlabs.segmented.SegmentedHeaderMesh.emit(direction,prepared.settings.position,prepared.height,prepared.column,prepared.width,prepared.rotation,x,y,z,adapter,adapter);}finally{adapter.buffer=null;adapter.edge=null;}}
    /** Uncached geometry remains the reference for moving sections and endpoint parity checks. */
    public void renderUncached(SegmentedDoorTile tile,double x,double y,double z,float partial,BufferBuilder buffer){if(tile.isRoot()&&tile.ready()){com.vandorlabs.client.SegmentedGateContexts.Prepared prepared=com.vandorlabs.client.SegmentedGateContexts.prepare(tile);int pass=Math.max(0,net.minecraftforge.client.MinecraftForgeClient.getRenderPass());if(prepared!=null&&prepared.complex)complex(tile,prepared,x,y,z,buffer,SegmentedDoorVisuals.pose(tile,partial),partial,pass);else emit(tile,x,y,z,buffer,SegmentedDoorVisuals.pose(tile,partial),false,pass);}}
    private void emit(SegmentedDoorTile tile,double x,double y,double z,BufferBuilder b,double progress,boolean capture,int pass){
        if(!tile.canBake()||pass!=0){int rotation=((int)(180-tile.facing().getHorizontalAngle())/90)&3;for(int row=0;row<tile.height;row++){if(capture)((Capture)b).row=row;SegmentedDoorGeometry.panel(row,tile.height,tile.column,tile.columns,progress,tile.settings.direction,tile.settings.straight,section);section[2]+=tile.settings.depthOffset();int light=capture?0:tile.getWorld().getCombinedLight(lightPos.setPos(tile.getPos().getX(),tile.getPos().getY()-row,tile.getPos().getZ()),0);com.vandorlabs.client.SegmentedDoorPanels.emit(tile,row,pass,b,x,y,z,section,rotation,light);}if(pass==0){if(capture)((Capture)b).row=0;header(tile,x,y,z,b);}return;}
        int rotation=((int)(180-tile.facing().getHorizontalAngle())/90)&3;b.setTranslation(0,0,0);
        for(int row=0;row<tile.height;row++){if(capture)((Capture)b).row=row;SegmentedDoorGeometry.panel(row,tile.height,tile.column,tile.columns,progress,tile.settings.direction,false,section);int light=capture?0:tile.getWorld().getCombinedLight(lightPos.setPos(tile.getPos().getX(),tile.getPos().getY()-row,tile.getPos().getZ()),0);com.vandorlabs.client.SegmentedDoorPanels.emitNative(tile,row,progress==0||progress==1,b,x,y,z,section,rotation,light);}
        if(capture)((Capture)b).row=0;header(tile,x,y,z,b);
    }
    /** Fixed border follows the leading edge, outside the panel's coplanar caps. */
    private static final class HeaderAdapter implements com.vandorlabs.segmented.SegmentedPanelMesh.Texture,com.vandorlabs.segmented.SegmentedPanelMesh.Sink {
        TextureAtlasSprite edge;BufferBuilder buffer;int light;boolean normals;
        public float u(double pixel){return edge.getInterpolatedU(pixel);}public float v(double pixel){return edge.getInterpolatedV(pixel);}
        public void vertex(double x,double y,double z,float u,float v,float nx,float ny,float nz){buffer.pos(x,y,z).color(255,255,255,255).tex(u,v).lightmap(light>>>16,light&65535);if(normals)buffer.normal(nx,ny,nz);buffer.endVertex();}
    }
    private static final ThreadLocal<HeaderAdapter> HEADER=ThreadLocal.withInitial(HeaderAdapter::new);
    private void header(SegmentedDoorTile tile,double x,double y,double z,BufferBuilder b){
        int direction=tile.settings.direction;if(direction==0&&tile.settings.position==0)return;if(direction==2&&tile.column!=0||direction==3&&tile.column!=tile.columns-1)return;
        HeaderAdapter adapter=HEADER.get();adapter.edge=Minecraft.getMinecraft().getTextureMapBlocks().getAtlasSprite("vandorlabs:blocks/programmable_glass/metal_side");adapter.buffer=b;adapter.light=tile.getWorld().getCombinedLight(tile.getPos(),0);adapter.normals=b.getVertexFormat().hasNormal();
        try{com.vandorlabs.segmented.SegmentedHeaderMesh.emit(direction,tile.settings.position,tile.height,tile.column,tile.columns,((int)(180-tile.facing().getHorizontalAngle())/90)&3,x,y,z,adapter,adapter);}finally{adapter.buffer=null;adapter.edge=null;}
    }
    @Override public void render(SegmentedDoorTile tile,double x,double y,double z,float partial,int destroy,float alpha){
        if(!tile.ready())return;int pass=net.minecraftforge.client.MinecraftForgeClient.getRenderPass();if(pass==1)com.vandorlabs.client.WaterloggedRenderer.render(tile,x,y,z);if(!tile.isRoot())return;
        try(com.vandorlabs.client.TurntableRenderState saved=new com.vandorlabs.client.TurntableRenderState()){
            bindTexture(TextureMap.LOCATION_BLOCKS_TEXTURE);GlStateManager.disableLighting();GlStateManager.disableCull();if(net.minecraftforge.client.MinecraftForgeClient.getRenderPass()==1){GlStateManager.enableBlend();GlStateManager.tryBlendFuncSeparate(GlStateManager.SourceFactor.SRC_ALPHA,GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA,GlStateManager.SourceFactor.ONE,GlStateManager.DestFactor.ZERO);GlStateManager.depthMask(false);}else{GlStateManager.disableBlend();GlStateManager.depthMask(true);}BufferBuilder b=Tessellator.getInstance().getBuffer();b.begin(GL11.GL_QUADS,DefaultVertexFormats.BLOCK);renderTileEntityFast(tile,x,y,z,partial,destroy,alpha,b);Tessellator.getInstance().draw();
        }
    }
}
