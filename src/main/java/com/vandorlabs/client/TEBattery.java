package com.vandorlabs.client;

import com.vandorlabs.blocks.BlockBattery;
import com.vandorlabs.tiles.TileEntityBattery;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.texture.*;
import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.util.EnumFacing;

/** Closed axial battery shell with live segmented charge indicators. */
@net.minecraftforge.fml.common.Mod.EventBusSubscriber(modid="vandorlabs",value=net.minecraftforge.fml.relauncher.Side.CLIENT)
public final class TEBattery extends TileEntitySpecialRenderer<TileEntityBattery> {
    private static final BatteryGeometryCache GEOMETRY=new BatteryGeometryCache();
    @net.minecraftforge.fml.common.eventhandler.SubscribeEvent public static void stitch(net.minecraftforge.client.event.TextureStitchEvent.Post event){GEOMETRY.clear();}
    @net.minecraftforge.fml.common.eventhandler.SubscribeEvent public static void unload(net.minecraftforge.event.world.WorldEvent.Unload event){if(event.getWorld().isRemote)GEOMETRY.clear();}
    static BatteryGeometryCache geometryCache(){return GEOMETRY;}
    private static TextureAtlasSprite sprite(String name){return Minecraft.getMinecraft().getTextureMapBlocks().getAtlasSprite("vandorlabs:blocks/"+name);}
    private static void quad(BufferBuilder b,TextureAtlasSprite sprite,double[][] points,float[] normal,int color,EnumFacing facing){
        float shade=net.minecraftforge.client.model.pipeline.LightUtil.diffuseLight(normal[0],normal[1],normal[2]);
        float r=(color>>16&255)/255F*shade,g=(color>>8&255)/255F*shade,blue=(color&255)/255F*shade;
        double[][] uv={{0,0},{0,16},{16,16},{16,0}};
        for(int i=0;i<4;i++){double[] p=AxialShellGeometry.orient(points[i],facing);b.pos(p[0],p[1],p[2]).tex(sprite.getInterpolatedU(uv[i][0]),sprite.getInterpolatedV(uv[i][1])).color(r,g,blue,1).normal(normal[0],normal[1],normal[2]).endVertex();}
    }
    static double[] portUV(double[] p,EnumFacing cap){
        double u=cap==EnumFacing.NORTH?1-p[0]:cap==EnumFacing.EAST?1-p[2]:cap==EnumFacing.WEST?p[2]:p[0];
        double v=cap==EnumFacing.UP?p[2]:cap==EnumFacing.DOWN?1-p[2]:1-p[1];
        return new double[]{u*16,v*16};
    }
    /** Indicator rectangles are in world space: always on the physical top and bottom. */
    private static void indicator(BufferBuilder b,TextureAtlasSprite white,double top,float charge){
        float[] normal={0,top>.5?1:-1,0};
        double surface=top+(top>.5?.001:-.001),detail=surface+(top>.5?.0005:-.0005);
        rectangle(b,white,.27,.73,.36,.64,surface,normal,0x18221f);
        for(int i=0;i<10;i++){
            double left=.29+i*.042,right=left+.033;
            rectangle(b,white,left,right,.39,.61,detail,normal,0x233f2b);
            double fill=Math.max(0,Math.min(1,charge*10-i));
            if(fill>0)rectangle(b,white,left,left+(right-left)*fill,.39,.61,detail+(top>.5?.0003:-.0003),normal,0x62ed82);
        }
    }
    private static void rectangle(BufferBuilder b,TextureAtlasSprite white,double left,double right,double front,double back,double y,float[] normal,int color){quad(b,white,new double[][]{{left,y,front},{left,y,back},{right,y,back},{right,y,front}},normal,color,EnumFacing.UP);}
    private static final class GeometrySink implements BatteryGeometryCache.Sink {
        private final BufferBuilder buffer;
        private final TextureAtlasSprite casing,cells,input,output;
        private TextureAtlasSprite current;
        private float shade,nx,ny,nz;
        GeometrySink(BufferBuilder buffer,TextureAtlasSprite casing,TextureAtlasSprite cells,TextureAtlasSprite input,TextureAtlasSprite output){this.buffer=buffer;this.casing=casing;this.cells=cells;this.input=input;this.output=output;}
        public void beginQuad(int material,float nx,float ny,float nz){current=material==BatteryGeometryCache.CASING?casing:material==BatteryGeometryCache.CELLS?cells:material==BatteryGeometryCache.INPUT?input:output;shade=net.minecraftforge.client.model.pipeline.LightUtil.diffuseLight(nx,ny,nz);this.nx=nx;this.ny=ny;this.nz=nz;}
        public void vertex(double x,double y,double z,double u,double v){buffer.pos(x,y,z).tex(current.getInterpolatedU(u),current.getInterpolatedV(v)).color(shade,shade,shade,1).normal(nx,ny,nz).endVertex();}
    }
    @Override public void render(TileEntityBattery tile,double x,double y,double z,float partial,int stage,float alpha){
        if(tile.getWorld()==null||tile.isInvalid()||!(tile.getWorld().getBlockState(tile.getPos()).getBlock() instanceof BlockBattery))return;
        EnumFacing face=tile.inputSide();
        GlStateManager.pushMatrix();
        try(TurntableRenderState state=new TurntableRenderState()){
            GlStateManager.translate(x,y,z);GlStateManager.disableLighting();GlStateManager.disableCull();GlStateManager.color(1,1,1,1);bindTexture(TextureMap.LOCATION_BLOCKS_TEXTURE);
            int light=tile.getWorld().getCombinedLight(tile.getPos(),0);OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit,light&65535,light>>16);
            TextureAtlasSprite casing=tile.hasCustomHousingTexture()?Minecraft.getMinecraft().getTextureMapBlocks().getAtlasSprite(com.vandorlabs.tiles.ScreenHousingTextures.texture(tile.getHousingTexture())):sprite("matter/casing"),cells=tile.hasCustomHousingTexture()?casing:sprite("imported/power/battery_cell_bank");
            Tessellator t=Tessellator.getInstance();BufferBuilder b=t.getBuffer();b.begin(7,DefaultVertexFormats.POSITION_TEX_COLOR_NORMAL);
            int axis=face.getAxis()==EnumFacing.Axis.X?BatteryGeometryCache.X:face.getAxis()==EnumFacing.Axis.Z?BatteryGeometryCache.Z:BatteryGeometryCache.Y;
            GEOMETRY.mesh(tile.isBlockShape(),axis,face.getAxisDirection()==EnumFacing.AxisDirection.POSITIVE).emit(new GeometrySink(b,casing,cells,sprite("matter/energy_in"),sprite("matter/energy_out")));
            t.draw();
            // Draw solid colors without introducing another atlas texture.
            GlStateManager.disableTexture2D();b.begin(7,DefaultVertexFormats.POSITION_TEX_COLOR_NORMAL);
            double low=tile.isBlockShape()||face.getAxis()==EnumFacing.Axis.Y?0:AxialShellGeometry.HEX[0][1];
            indicator(b,casing,low,tile.chargeFraction());indicator(b,casing,1-low,tile.chargeFraction());t.draw();GlStateManager.enableTexture2D();
        }finally{GlStateManager.popMatrix();GlStateManager.resetColor();GlStateManager.color(1,1,1,1);}
    }
}
