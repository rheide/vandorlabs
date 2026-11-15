package com.vandorlabs.client;

import com.vandorlabs.blocks.BlockBattery;
import com.vandorlabs.tiles.TileEntityBattery;
import com.vandorlabs.ports.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.texture.*;
import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.util.EnumFacing;

/** Cached closed shell; face artwork and segmented charge markings remain live. */
@net.minecraftforge.fml.common.Mod.EventBusSubscriber(modid="vandorlabs",value=net.minecraftforge.fml.relauncher.Side.CLIENT)
public final class TEBattery extends TileEntitySpecialRenderer<TileEntityBattery> {
    private static final BatteryGeometryCache GEOMETRY=new BatteryGeometryCache();
    private static TextureAtlasSprite[] portSprites;
    @net.minecraftforge.fml.common.eventhandler.SubscribeEvent public static void stitch(net.minecraftforge.client.event.TextureStitchEvent.Post event){GEOMETRY.clear();portSprites=null;}
    @net.minecraftforge.fml.common.eventhandler.SubscribeEvent public static void unload(net.minecraftforge.event.world.WorldEvent.Unload event){if(event.getWorld().isRemote)GEOMETRY.clear();}
    static BatteryGeometryCache geometryCache(){return GEOMETRY;}
    private static TextureAtlasSprite sprite(String name){return Minecraft.getMinecraft().getTextureMapBlocks().getAtlasSprite("vandorlabs:blocks/"+name);}
    static double[] portUV(double[] p,EnumFacing cap){double u=cap==EnumFacing.NORTH?1-p[0]:cap==EnumFacing.EAST?1-p[2]:cap==EnumFacing.WEST?p[2]:p[0],v=cap==EnumFacing.UP?p[2]:cap==EnumFacing.DOWN?1-p[2]:1-p[1];return new double[]{u*16,v*16};}
    private static int side(float x,float y,float z){return EnumFacing.getFacingFromVector(x,y,z).getIndex();}
    private static final class GeometrySink implements BatteryGeometryCache.Sink {
        private final BufferBuilder buffer;private final TileEntityBattery tile;private final ResourcePortSettings settings;private final TextureAtlasSprite casing;
        private final TextureAtlasSprite[] ports;private TextureAtlasSprite current;private float shade,nx,ny,nz;private boolean crop;
        GeometrySink(BufferBuilder buffer,TileEntityBattery tile,TextureAtlasSprite casing,TextureAtlasSprite[] ports){this.buffer=buffer;this.tile=tile;settings=tile.settings();this.casing=casing;this.ports=ports;}
        public void beginQuad(int material,float nx,float ny,float nz){int face=side(nx,ny,nz);ResourcePortSettings.Face mode=settings.face(face);current=mode==ResourcePortSettings.Face.DISABLED?casing:ports[(mode==ResourcePortSettings.Face.INPUT?0:2)+((tile.appearanceMask()&(1<<face))!=0?1:0)];crop=mode!=ResourcePortSettings.Face.DISABLED&&material<2&&!tile.isBlockShape();shade=net.minecraftforge.client.model.pipeline.LightUtil.diffuseLight(nx,ny,nz);this.nx=nx;this.ny=ny;this.nz=nz;}
        public void vertex(double x,double y,double z,double u,double v){buffer.pos(x,y,z).tex(current.getInterpolatedU(BatteryChargeBars.tiledSideU(u,crop)),current.getInterpolatedV(v)).color(shade,shade,shade,1).normal(nx,ny,nz).endVertex();}
    }
    private static final class Indicators implements BatteryGeometryCache.Sink {
        private final BufferBuilder buffer;private final ResourcePortSettings settings;private final float charge;private final double[] corners=new double[12];private final ColorSink colorSink=new ColorSink();private int vertices;private boolean enabled;private float nx,ny,nz,red,green,blue;
        Indicators(BufferBuilder buffer,TileEntityBattery tile){this.buffer=buffer;settings=tile.settings();charge=tile.chargeFraction();}
        public void beginQuad(int material,float nx,float ny,float nz){enabled=material<2&&settings.face(side(nx,ny,nz))==ResourcePortSettings.Face.DISABLED;this.nx=nx;this.ny=ny;this.nz=nz;vertices=0;}
        public void vertex(double x,double y,double z,double u,double v){if(!enabled)return;int i=vertices++*3;corners[i]=x;corners[i+1]=y;corners[i+2]=z;if(vertices==4)BatteryChargeBars.emit(corners,nx,ny,nz,charge,colorSink);}
        private final class ColorSink implements BatteryChargeBars.Sink {
            public void beginQuad(int color,float x,float y,float z){float shade=net.minecraftforge.client.model.pipeline.LightUtil.diffuseLight(x,y,z);red=(color>>16&255)/255F*shade;green=(color>>8&255)/255F*shade;blue=(color&255)/255F*shade;}
            public void vertex(double x,double y,double z){buffer.pos(x,y,z).tex(0,0).color(red,green,blue,1).normal(nx,ny,nz).endVertex();}
        }
    }
    @Override public void render(TileEntityBattery tile,double x,double y,double z,float partial,int stage,float alpha){
        if(tile.getWorld()==null||tile.isInvalid()||!(tile.getWorld().getBlockState(tile.getPos()).getBlock() instanceof BlockBattery))return;
        EnumFacing face=tile.inputSide();GlStateManager.pushMatrix();
        try(TurntableRenderState state=new TurntableRenderState()){
            GlStateManager.translate(x,y,z);GlStateManager.disableLighting();GlStateManager.disableCull();GlStateManager.color(1,1,1,1);bindTexture(TextureMap.LOCATION_BLOCKS_TEXTURE);
            int light=tile.getWorld().getCombinedLight(tile.getPos(),0);OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit,light&65535,light>>16);
            TextureAtlasSprite casing=tile.hasCustomHousingTexture()?Minecraft.getMinecraft().getTextureMapBlocks().getAtlasSprite(com.vandorlabs.tiles.ScreenHousingTextures.texture(tile.getHousingTexture())):sprite("imported/power/battery_cell_bank");
            if(portSprites==null)portSprites=new TextureAtlasSprite[]{sprite("resource_ports/power_in_off"),sprite("resource_ports/power_in_on"),sprite("resource_ports/power_out_off"),sprite("resource_ports/power_out_on")};
            int axis=face.getAxis()==EnumFacing.Axis.X?BatteryGeometryCache.X:face.getAxis()==EnumFacing.Axis.Z?BatteryGeometryCache.Z:BatteryGeometryCache.Y;
            BatteryGeometryCache.Mesh mesh=GEOMETRY.mesh(tile.isBlockShape(),axis,face.getAxisDirection()==EnumFacing.AxisDirection.POSITIVE);
            Tessellator t=Tessellator.getInstance();BufferBuilder b=t.getBuffer();b.begin(7,DefaultVertexFormats.POSITION_TEX_COLOR_NORMAL);mesh.emit(new GeometrySink(b,tile,casing,portSprites));t.draw();
            GlStateManager.disableTexture2D();b.begin(7,DefaultVertexFormats.POSITION_TEX_COLOR_NORMAL);mesh.emit(new Indicators(b,tile));if(b.getVertexCount()>0)t.draw();else{b.finishDrawing();b.reset();}GlStateManager.enableTexture2D();
        }finally{GlStateManager.popMatrix();GlStateManager.resetColor();GlStateManager.color(1,1,1,1);}
    }
}
