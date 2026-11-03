package com.vandorlabs.client.garage;

import com.vandorlabs.garage.GarageDoorTile;
import com.vandorlabs.garage.GarageDoorGeometry;

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
public final class GarageDoorRenderer extends TileEntitySpecialRenderer<GarageDoorTile> {
    @net.minecraftforge.fml.common.eventhandler.SubscribeEvent public static void stitch(net.minecraftforge.client.event.TextureStitchEvent.Pre event){clearTemplates();event.getMap().registerSprite(new net.minecraft.util.ResourceLocation("vandorlabs:blocks/garage/garage_panel"));}
    private static final double H=GarageDoorGeometry.HALF_THICKNESS;
    private static final double[][][] FACES={
        {{0,-.5,H},{0,-.5,-H},{1,-.5,-H},{1,-.5,H}},{{0,.5,-H},{0,.5,H},{1,.5,H},{1,.5,-H}},
        {{1,-.5,-H},{0,-.5,-H},{0,.5,-H},{1,.5,-H}},{{0,-.5,H},{1,-.5,H},{1,.5,H},{0,.5,H}},
        {{0,-.5,-H},{0,-.5,H},{0,.5,H},{0,.5,-H}},{{1,-.5,H},{1,-.5,-H},{1,.5,-H},{1,.5,H}}};
    private static final int[][] NORMALS={{0,-1,0},{0,1,0},{0,0,-1},{0,0,1},{-1,0,0},{1,0,0}};
    private final double[] section=new double[4];
    private final Capture capture=new Capture();
    private final net.minecraft.util.math.BlockPos.MutableBlockPos lightPos=new net.minecraft.util.math.BlockPos.MutableBlockPos();
    private static final int CACHE_BYTES=2*1024*1024;
    private static final java.util.Map<Long,Template> TEMPLATES=new java.util.LinkedHashMap<>(32,.75F,true);
    private static net.minecraft.client.renderer.vertex.VertexFormat templateFormat;
    private static int retained,columnBytes;
    private static long lightSamples,dynamicRevision;
    private static boolean hadDynamicLights,dynamicUnknown;
    private static final java.lang.reflect.Method DYNAMIC_COUNT=dynamicCount();
    private static java.lang.reflect.Method dynamicCount(){try{return Class.forName("net.optifine.DynamicLights").getMethod("getCount");}catch(ClassNotFoundException absent){return null;}catch(ReflectiveOperationException incompatible){dynamicUnknown=true;com.vandorlabs.VandorLabs.logger.warn("Garage lighting cache uses per-frame fallback for unsupported dynamic lights",incompatible);return null;}}
    @net.minecraftforge.fml.common.eventhandler.SubscribeEvent public static void frame(net.minecraftforge.fml.common.gameevent.TickEvent.RenderTickEvent event){
        if(event.phase!=net.minecraftforge.fml.common.gameevent.TickEvent.Phase.START)return;boolean active=dynamicUnknown;
        if(DYNAMIC_COUNT!=null)try{active=((Number)DYNAMIC_COUNT.invoke(null)).intValue()>0;}catch(ReflectiveOperationException failure){active=true;}
        if(active||hadDynamicLights)dynamicRevision++;hadDynamicLights=active;
    }
    public static long lightSamples(){return lightSamples;}
    public static int retainedBytes(){return retained+columnBytes;}
    private static final int COLUMN_BYTES=16*1024*1024;
    private static final java.util.Map<GarageDoorTile,Column> COLUMNS=new java.util.WeakHashMap<>();
    private static final java.util.Map<Column,Boolean> COLUMN_LRU=new java.util.LinkedHashMap<>(128,.75F,true);
    private static final java.util.Map<net.minecraft.world.World,LightChanges> LIGHTS=new java.util.IdentityHashMap<>();
    private static final class LightChanges extends com.vandorlabs.world.WorldListener {
        long revision;
        @Override public void notifyLightSet(net.minecraft.util.math.BlockPos pos){revision++;}
        @Override public void notifyBlockUpdate(net.minecraft.world.World world,net.minecraft.util.math.BlockPos pos,net.minecraft.block.state.IBlockState before,net.minecraft.block.state.IBlockState after,int flags){revision++;}
    }
    private static final class Column {
        long key=-1,revision=-1,origin,dynamic=-1;int vehicleRevision;java.lang.ref.WeakReference<Object> structure;final java.lang.ref.WeakReference<GarageDoorTile> tile;
        Column(GarageDoorTile tile){this.tile=new java.lang.ref.WeakReference<>(tile);}double x=Double.NaN,y,z;int[] data;final int[] lights=new int[GarageDoorTile.MAX_HEIGHT];
        int bytes(){return data.length*4+384;}
    }
    private static LightChanges lightChanges(net.minecraft.world.World world){
        while(world instanceof com.vandorlabs.vehicle.VehicleWorld)world=((com.vandorlabs.vehicle.VehicleWorld)world).parent;
        LightChanges changes=LIGHTS.get(world);if(changes==null){changes=new LightChanges();LIGHTS.put(world,changes);world.addEventListener(changes);}return changes;
    }
    @net.minecraftforge.fml.common.eventhandler.SubscribeEvent public static void unload(net.minecraftforge.event.world.WorldEvent.Unload event){if(event.getWorld().isRemote)Minecraft.getMinecraft().addScheduledTask(()->{LightChanges observer=LIGHTS.remove(event.getWorld());if(observer!=null)event.getWorld().removeEventListener(observer);clearTemplates();});}

    private static final class Capture extends BufferBuilder {
        int row;final byte[] rows=new byte[GarageDoorTile.MAX_HEIGHT*24];
        Capture(){super(GarageDoorTile.MAX_HEIGHT*24*16);}
        @Override public void endVertex(){rows[getVertexCount()]=(byte)row;super.endVertex();}
    }
    private static final class Template {
        final int[] data;final byte[] rows;final int stride,lightOffset,bytes;
        Template(Capture b){data=b.getVertexState().getRawBuffer();rows=java.util.Arrays.copyOf(b.rows,b.getVertexCount());stride=b.getVertexFormat().getIntegerSize();lightOffset=b.getVertexFormat().getUvOffsetById(1)/4;bytes=data.length*4+rows.length+128;}
    }
    private static void clearTemplates(){TEMPLATES.clear();templateFormat=null;retained=0;COLUMNS.clear();COLUMN_LRU.clear();columnBytes=0;}
    private void cached(GarageDoorTile tile,double x,double y,double z,BufferBuilder buffer,double progress){
        if(templateFormat==null||!templateFormat.equals(buffer.getVertexFormat())){clearTemplates();templateFormat=new net.minecraft.client.renderer.vertex.VertexFormat(buffer.getVertexFormat());}
        int steps=tile.height*tile.ticksPerSection*6,frame=(int)Math.round(progress*steps);progress=frame/(double)steps;
        long key=((long)(tile.texture+1)<<48)|((long)steps<<24)|((long)frame<<8)|((tile.height-1)<<2)|(((int)(180-tile.facing().getHorizontalAngle())/90)&3);
        Column column=COLUMNS.get(tile);if(column==null){column=new Column(tile);COLUMNS.put(tile,column);}
        COLUMN_LRU.put(column,Boolean.TRUE);
        LightChanges changes=lightChanges(tile.getWorld());long origin=0;int vehicleRevision=0;Object structure=null;
        if(tile.getWorld() instanceof com.vandorlabs.vehicle.VehicleWorld){com.vandorlabs.vehicle.VehicleWorld vehicle=(com.vandorlabs.vehicle.VehicleWorld)tile.getWorld();origin=vehicle.origin.toLong();vehicleRevision=vehicle.tileRevision;structure=vehicle.structure;}
        boolean lighting=column.dynamic!=dynamicRevision||column.revision!=changes.revision||column.origin!=origin||column.vehicleRevision!=vehicleRevision||(column.structure==null?null:column.structure.get())!=structure;
        boolean geometry=column.key!=key||column.x!=x||column.y!=y||column.z!=z;
        Template mesh=null;
        if(geometry){
            mesh=TEMPLATES.get(key);
            if(mesh==null){capture.begin(GL11.GL_QUADS,templateFormat);emit(tile,0,0,0,capture,progress,true);capture.finishDrawing();mesh=new Template(capture);while(!TEMPLATES.isEmpty()&&retained+mesh.bytes>CACHE_BYTES){java.util.Iterator<Template> it=TEMPLATES.values().iterator();Template old=it.next();it.remove();retained-=old.bytes;}TEMPLATES.put(key,mesh);retained+=mesh.bytes;}
            if(column.data==null||column.data.length!=mesh.data.length){if(column.data!=null)columnBytes-=column.bytes();column.data=new int[mesh.data.length];columnBytes+=column.bytes();lighting=true;}
            System.arraycopy(mesh.data,0,column.data,0,mesh.data.length);
            for(int v=0;v<mesh.rows.length;v++){int at=v*mesh.stride;column.data[at]=Float.floatToRawIntBits((float)(Float.intBitsToFloat(column.data[at])+x));column.data[at+1]=Float.floatToRawIntBits((float)(Float.intBitsToFloat(column.data[at+1])+y));column.data[at+2]=Float.floatToRawIntBits((float)(Float.intBitsToFloat(column.data[at+2])+z));}
            column.key=key;column.x=x;column.y=y;column.z=z;
        }
        if(lighting){for(int row=0;row<tile.height;row++){lightSamples++;int light=tile.getWorld().getCombinedLight(lightPos.setPos(tile.getPos().getX(),tile.getPos().getY()-row,tile.getPos().getZ()),0);column.lights[row]=light;}column.dynamic=dynamicRevision;column.revision=changes.revision;column.origin=origin;column.vehicleRevision=vehicleRevision;column.structure=structure==null?null:new java.lang.ref.WeakReference<>(structure);}
        if(geometry||lighting){if(mesh==null)mesh=TEMPLATES.get(key);if(mesh==null){column.key=-1;cached(tile,x,y,z,buffer,progress);return;}for(int v=0;v<mesh.rows.length;v++)column.data[v*mesh.stride+mesh.lightOffset]=column.lights[mesh.rows[v]];}
        buffer.setTranslation(0,0,0);buffer.addVertexData(column.data);
        while(columnBytes>COLUMN_BYTES){java.util.Iterator<Column> it=COLUMN_LRU.keySet().iterator();Column old=it.next();it.remove();COLUMNS.remove(old.tile.get());columnBytes-=old.bytes();}
    }

    @Override public boolean isGlobalRenderer(GarageDoorTile tile){return tile.isRoot();}
    @Override public void renderTileEntityFast(GarageDoorTile tile,double x,double y,double z,float partial,int destroy,float alpha,BufferBuilder b){
        if(!tile.isRoot()||!tile.ready()||tile.clientSuppressed)return;
        double progress=GarageDoorVisuals.pose(tile,partial);
        cached(tile,x,y,z,b,progress);
    }
    /** Uncached geometry remains the reference for moving sections and endpoint parity checks. */
    public void renderUncached(GarageDoorTile tile,double x,double y,double z,float partial,BufferBuilder buffer){if(tile.isRoot()&&tile.ready())emit(tile,x,y,z,buffer,GarageDoorVisuals.pose(tile,partial),false);}
    private void emit(GarageDoorTile tile,double x,double y,double z,BufferBuilder b,double progress,boolean capture){
        TextureMap atlas=Minecraft.getMinecraft().getTextureMapBlocks();TextureAtlasSprite front=atlas.getAtlasSprite(tile.texture<0?"vandorlabs:blocks/garage/garage_panel":com.vandorlabs.tiles.ScreenHousingTextures.fullTexture(tile.texture)),edge=atlas.getAtlasSprite("vandorlabs:blocks/programmable_glass/metal_side");
        int rotation=((int)(180-tile.facing().getHorizontalAngle())/90)&3;boolean normals=b.getVertexFormat().hasNormal();b.setTranslation(0,0,0);
        for(int row=0;row<tile.height;row++){
            GarageDoorGeometry.section(row,tile.height,progress,section);if(capture)((Capture)b).row=row;int light=capture?0:tile.getWorld().getCombinedLight(lightPos.setPos(tile.getPos().getX(),tile.getPos().getY()-row,tile.getPos().getZ()),0);double sin=section[2],cos=section[3];
            for(int face=0;face<6;face++){
                if((progress==0||progress==1)&&(face==0&&row<tile.height-1||face==1&&row>0))continue;
                TextureAtlasSprite sprite=face==2||face==3?front:edge;double nx=NORMALS[face][0],ny=cos*NORMALS[face][1]-sin*NORMALS[face][2],nz=sin*NORMALS[face][1]+cos*NORMALS[face][2];
                for(int v=0;v<4;v++){
                    double[] p=FACES[face][v];double px=p[0],py=section[0]+cos*p[1]-sin*p[2],pz=section[1]+sin*p[1]+cos*p[2],rx=px,rz=pz,rnx=nx,rnz=nz;
                    switch(rotation){case 1:rx=pz;rz=1-px;rnx=nz;rnz=-nx;break;case 2:rx=1-px;rz=1-pz;rnx=-nx;rnz=-nz;break;case 3:rx=1-pz;rz=px;rnx=-nz;rnz=nx;break;default:break;}
                    b.pos(x+rx,y+py,z+rz).color(255,255,255,255).tex(sprite.getInterpolatedU(v==1||v==2?16:0),sprite.getInterpolatedV(v<2?16:0)).lightmap(light>>>16,light&65535);
                    if(normals)b.normal((float)rnx,(float)ny,(float)rnz);b.endVertex();
                }
            }
        }
    }
    @Override public void render(GarageDoorTile tile,double x,double y,double z,float partial,int destroy,float alpha){
        if(!tile.isRoot()||!tile.ready()||tile.clientSuppressed)return;
        try(com.vandorlabs.client.TurntableRenderState saved=new com.vandorlabs.client.TurntableRenderState()){
            bindTexture(TextureMap.LOCATION_BLOCKS_TEXTURE);GlStateManager.disableLighting();GlStateManager.disableCull();GlStateManager.disableBlend();GlStateManager.depthMask(true);BufferBuilder b=Tessellator.getInstance().getBuffer();b.begin(GL11.GL_QUADS,DefaultVertexFormats.BLOCK);renderTileEntityFast(tile,x,y,z,partial,destroy,alpha,b);Tessellator.getInstance().draw();
        }
    }
}
