package com.vandorlabs.client;

import com.vandorlabs.tiles.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.texture.*;
import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.util.*;
import net.minecraftforge.fluids.FluidStack;

/** Six-sided casing with fluid-colored gauges on the two flat faces. */
public final class TEHexTank extends TileEntitySpecialRenderer<TileEntityHexTank> {
    private static final double[][] OUTLINE={{.25,.0625},{.75,.0625},{1,.5},{.75,.9375},{.25,.9375},{0,.5}};
    private static TextureAtlasSprite sprite(String name){return Minecraft.getMinecraft().getTextureMapBlocks().getAtlasSprite(name);}
    private static void quad(BufferBuilder b,TextureAtlasSprite s,int color,double[][] v){
        float r=(color>>16&255)/255F,g=(color>>8&255)/255F,blue=(color&255)/255F;
        double[][] uv={{0,0},{0,16},{16,16},{16,0}};
        for(int i=0;i<4;i++)b.pos(v[i][0],v[i][1],v[i][2]).tex(s.getInterpolatedU(uv[i][0]),s.getInterpolatedV(uv[i][1])).color(r,g,blue,1).normal(0,1,0).endVertex();
    }
    private static void window(BufferBuilder b,TextureAtlasSprite s,int color,double minY,double maxY,double z){quad(b,s,color,new double[][]{{.34,maxY,z},{.34,minY,z},{.66,minY,z},{.66,maxY,z}});}
    @Override public void render(TileEntityHexTank tile,double x,double y,double z,float partial,int stage,float alpha){
        float oldU=OpenGlHelper.lastBrightnessX,oldV=OpenGlHelper.lastBrightnessY;
        GlStateManager.resetColor();GlStateManager.color(1,1,1,1);
        GlStateManager.pushMatrix();GlStateManager.translate(x,y,z);GlStateManager.disableLighting();GlStateManager.disableCull();
        bindTexture(TextureMap.LOCATION_BLOCKS_TEXTURE);int light=tile.getWorld().getCombinedLight(tile.getPos(),0);OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit,light&65535,light>>16);
        EnumFacing facing=tile.getWorld().getBlockState(tile.getPos()).getValue(com.vandorlabs.blocks.BlockAnimatedScreenSelector.FACING);
        GlStateManager.translate(.5,0,.5);GlStateManager.rotate(facing.getHorizontalIndex()>=0?180-facing.getHorizontalIndex()*90:0,0,1,0);GlStateManager.translate(-.5,0,-.5);
        TextureAtlasSprite casing=sprite(ScreenHousingTextures.texture(tile.getHousingTexture())),port=sprite("vandorlabs:blocks/matter/port"),dark=sprite("minecraft:blocks/obsidian");
        Tessellator t=Tessellator.getInstance();BufferBuilder b=t.getBuffer();b.begin(7,DefaultVertexFormats.POSITION_TEX_COLOR_NORMAL);
        for(int i=0;i<6;i++){double[] a=OUTLINE[i],c=OUTLINE[(i+1)%6];quad(b,casing,0xffffff,new double[][]{{a[0],1,a[1]},{a[0],0,a[1]},{c[0],0,c[1]},{c[0],1,c[1]}});}
        for(double height:new double[]{0,1})for(int i=0;i<6;i++){double[] a=OUTLINE[i],c=OUTLINE[(i+1)%6];quad(b,port,0xffffff,new double[][]{{.5,height,.5},{a[0],height,a[1]},{c[0],height,c[1]},{.5,height,.5}});}
        window(b,dark,0xffffff,.18,.82,.0615);window(b,dark,0xffffff,.18,.82,.9385);
        FluidStack fluid=tile.tank.getFluid();
        if(fluid!=null && fluid.amount>0){TextureAtlasSprite contents=sprite(fluid.getFluid().getStill(fluid).toString());int color=fluid.getFluid().getColor(fluid);double top=.2+.6*Math.min(1,fluid.amount/(double)TileEntityHexTank.CAPACITY);
            window(b,contents,color,.2,top,.0605);window(b,contents,color,.2,top,.9395);}
        t.draw();GlStateManager.enableCull();GlStateManager.enableLighting();GlStateManager.resetColor();GlStateManager.color(1,1,1,1);OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit,oldU,oldV);GlStateManager.popMatrix();
    }
}
