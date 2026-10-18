package com.vandorlabs.client;

import com.vandorlabs.tiles.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.texture.*;
import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.util.*;
import net.minecraftforge.fluids.FluidStack;

/** Hollow upright hexagonal shell; central side windows expose the actual fluid volume. */
@net.minecraftforge.fml.common.Mod.EventBusSubscriber(modid="vandorlabs",value=net.minecraftforge.fml.relauncher.Side.CLIENT)
public final class TEHexTank extends TileEntitySpecialRenderer<TileEntityHexTank> {
    @net.minecraftforge.fml.common.eventhandler.SubscribeEvent public static void stitch(net.minecraftforge.client.event.TextureStitchEvent.Pre event){for(String name:new String[]{"port","tank_empty"})event.getMap().registerSprite(new ResourceLocation("vandorlabs:blocks/matter/"+name));}
    private static final double[][] OUTLINE={{.25,.0625},{.75,.0625},{1,.5},{.75,.9375},{.25,.9375},{0,.5}};
    private static final double[][] BLOCK={{0,0},{1,0},{1,1},{0,1}};
    private static final double INNER=.90, LIQUID=.895, FLOOR=.04, CEILING=.96;
    private static TextureAtlasSprite sprite(String name){return Minecraft.getMinecraft().getTextureMapBlocks().getAtlasSprite(name);}
    /** A horizontal connection turns the body in plan, keeping both hexagonal caps level. */
    static double[] orient(double[] p,EnumFacing f){return f.getAxis()==EnumFacing.Axis.X?new double[]{1-p[2],p[1],p[0]}:p;}
    static double fluidLevel(int amount){return FLOOR+(CEILING-FLOOR)*Math.max(0,Math.min(1,amount/(double)TileEntityHexTank.CAPACITY));}
    private static double[] point(double[][] outline,int edge,double u,double y,double scale){double[] a=outline[edge],c=outline[(edge+1)%outline.length];return new double[]{.5+(a[0]+(c[0]-a[0])*u-.5)*scale,y,.5+(a[1]+(c[1]-a[1])*u-.5)*scale};}
    private static void quad(BufferBuilder b,TextureAtlasSprite s,int color,double[][] vertices,EnumFacing facing,boolean cap){
        float r=(color>>16&255)/255F,g=(color>>8&255)/255F,blue=(color&255)/255F;
        double[][] uv={{0,0},{0,16},{16,16},{16,0}};
        for(int i=0;i<4;i++){double[] p=orient(vertices[i],facing);b.pos(p[0],p[1],p[2]).tex(s.getInterpolatedU(cap?vertices[i][0]*16:uv[i][0]),s.getInterpolatedV(cap?vertices[i][2]*16:uv[i][1])).color(r,g,blue,1).normal(0,1,0).endVertex();}
    }
    private static void side(double[][] outline,BufferBuilder b,TextureAtlasSprite s,int color,int edge,double left,double right,double bottom,double top,double scale,EnumFacing facing){quad(b,s,color,new double[][]{point(outline,edge,left,top,scale),point(outline,edge,left,bottom,scale),point(outline,edge,right,bottom,scale),point(outline,edge,right,top,scale)},facing,false);}
    private static void cap(double[][] outline,BufferBuilder b,TextureAtlasSprite s,int color,double y,double scale,EnumFacing facing){for(int i=0;i<outline.length;i++)quad(b,s,color,new double[][]{{.5,y,.5},point(outline,i,0,y,scale),point(outline,i,1,y,scale),{.5,y,.5}},facing,true);}
    private static void wall(double[][] outline,BufferBuilder b,TextureAtlasSprite s,int color,int edge,double scale,double low,double high,EnumFacing facing){
        side(outline,b,s,color,edge,0,1,low,.4,scale,facing);side(outline,b,s,color,edge,0,1,.6,high,scale,facing);
        side(outline,b,s,color,edge,0,.3,.4,.6,scale,facing);side(outline,b,s,color,edge,.7,1,.4,.6,scale,facing);
    }
    @Override public void render(TileEntityHexTank tile,double x,double y,double z,float partial,int stage,float alpha){
        GlStateManager.pushMatrix();
        try(TurntableRenderState state=new TurntableRenderState()){
            GlStateManager.translate(x,y,z);GlStateManager.disableLighting();GlStateManager.disableCull();GlStateManager.color(1,1,1,1);bindTexture(TextureMap.LOCATION_BLOCKS_TEXTURE);
            int light=tile.getWorld().getCombinedLight(tile.getPos(),0);OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit,light&65535,light>>16);
            EnumFacing facing=tile.getWorld().getBlockState(tile.getPos()).getValue(com.vandorlabs.blocks.BlockAnimatedScreenSelector.FACING);
            TextureAtlasSprite casing=sprite(ScreenHousingTextures.texture(tile.getHousingTexture())),port=sprite("vandorlabs:blocks/matter/port"),liner=sprite("vandorlabs:blocks/matter/tank_empty");
            double[][] outline=tile.isBlockShape()?BLOCK:OUTLINE;
            Tessellator t=Tessellator.getInstance();BufferBuilder b=t.getBuffer();b.begin(7,DefaultVertexFormats.POSITION_TEX_COLOR_NORMAL);
            for(int i=0;i<outline.length;i++){
                wall(outline,b,casing,0xffffff,i,1,0,1,facing);wall(outline,b,liner,0xffffff,i,INNER,FLOOR,CEILING,facing);
                // Thickness around the four edges of every window opening.
                for(double u:new double[]{.3,.7})quad(b,casing,0xb5c2cc,new double[][]{point(outline,i,u,.4,1),point(outline,i,u,.6,1),point(outline,i,u,.6,INNER),point(outline,i,u,.4,INNER)},facing,false);
                for(double h:new double[]{.4,.6})quad(b,casing,0xb5c2cc,new double[][]{point(outline,i,.3,h,1),point(outline,i,.7,h,1),point(outline,i,.7,h,INNER),point(outline,i,.3,h,INNER)},facing,false);
                side(outline,b,casing,0x697782,i,0,.3,.485,.515,1.001,facing);side(outline,b,casing,0x697782,i,.7,1,.485,.515,1.001,facing);
            }
            cap(outline,b,port,0xffffff,0,1,facing);cap(outline,b,port,0xffffff,1,1,facing);
            cap(outline,b,liner,0xffffff,FLOOR,INNER,facing);cap(outline,b,liner,0xffffff,CEILING,INNER,facing);t.draw();
            FluidStack fluid=tile.tank.getFluid();
            GlStateManager.enableBlend();GlStateManager.tryBlendFuncSeparate(770,771,1,0);
            if(fluid!=null&&fluid.amount>0){
                TextureAtlasSprite contents=sprite(fluid.getFluid().getStill(fluid).toString());double top=fluidLevel(fluid.amount);int color=fluid.getFluid().getColor(fluid);
                b.begin(7,DefaultVertexFormats.POSITION_TEX_COLOR_NORMAL);
                for(int i=0;i<outline.length;i++)side(outline,b,contents,color,i,0,1,FLOOR+.0005,top,LIQUID,facing);
                cap(outline,b,contents,color,top,LIQUID,facing);cap(outline,b,contents,color,FLOOR+.0005,LIQUID,facing);t.draw();
            }
            // Actual glass panes, with transparent centers rather than a painted level image.
            b.begin(7,DefaultVertexFormats.POSITION_TEX_COLOR_NORMAL);TextureAtlasSprite glass=sprite("minecraft:blocks/glass");
            for(int i=0;i<outline.length;i++)side(outline,b,glass,0xffffff,i,.3,.7,.4,.6,1.0005,facing);t.draw();
        }finally{GlStateManager.popMatrix();GlStateManager.resetColor();GlStateManager.color(1,1,1,1);}
    }
}
