package com.vandorlabs.client;

import com.vandorlabs.tiles.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.texture.*;
import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.util.*;
import net.minecraftforge.fluids.FluidStack;

/** Axial connecting ports, recessed windows and a gravity-aligned fluid gauge. */
@net.minecraftforge.fml.common.Mod.EventBusSubscriber(modid="vandorlabs",value=net.minecraftforge.fml.relauncher.Side.CLIENT)
public final class TEHexTank extends TileEntitySpecialRenderer<TileEntityHexTank> {
    @net.minecraftforge.fml.common.eventhandler.SubscribeEvent public static void stitch(net.minecraftforge.client.event.TextureStitchEvent.Pre event){for(String name:new String[]{"port","tank_empty"})event.getMap().registerSprite(new ResourceLocation("vandorlabs:blocks/matter/"+name));}
    private static final double[][] OUTLINE={{.25,.0625},{.75,.0625},{1,.5},{.75,.9375},{.25,.9375},{0,.5}};
    private static TextureAtlasSprite sprite(String name){return Minecraft.getMinecraft().getTextureMapBlocks().getAtlasSprite(name);}
    private static double[] orient(double[] p,EnumFacing f){switch(f){case DOWN:return new double[]{p[0],1-p[1],1-p[2]};case NORTH:return new double[]{p[0],p[2],1-p[1]};case SOUTH:return new double[]{p[0],1-p[2],p[1]};case EAST:return new double[]{p[1],1-p[0],p[2]};case WEST:return new double[]{1-p[1],p[0],p[2]};default:return p;}}
    private static void quad(BufferBuilder b,TextureAtlasSprite s,int color,double[][] vertices,EnumFacing facing,boolean cap,double fill){
        double[][] v=new double[4][];for(int i=0;i<4;i++)v[i]=orient(vertices[i],facing);
        if(fill>=0){double min=Double.POSITIVE_INFINITY;for(double[] p:v)min=Math.min(min,p[1]);if(min>=fill)return;for(double[] p:v)p[1]=Math.min(p[1],fill);}
        float r=(color>>16&255)/255F,g=(color>>8&255)/255F,blue=(color&255)/255F;
        double[][] uv={{0,0},{0,16},{16,16},{16,0}};
        for(int i=0;i<4;i++)b.pos(v[i][0],v[i][1],v[i][2]).tex(s.getInterpolatedU(cap?vertices[i][0]*16:uv[i][0]),s.getInterpolatedV(cap?vertices[i][2]*16:uv[i][1])).color(r,g,blue,1).normal(0,1,0).endVertex();
    }
    private static void panel(BufferBuilder b,TextureAtlasSprite s,int color,double left,double right,double bottom,double top,double z,EnumFacing facing,double fill){quad(b,s,color,new double[][]{{left,top,z},{left,bottom,z},{right,bottom,z},{right,top,z}},facing,false,fill);}
    @Override public void render(TileEntityHexTank tile,double x,double y,double z,float partial,int stage,float alpha){
        GlStateManager.pushMatrix();
        try(TurntableRenderState state=new TurntableRenderState()){
            GlStateManager.translate(x,y,z);GlStateManager.disableLighting();GlStateManager.disableCull();GlStateManager.color(1,1,1,1);bindTexture(TextureMap.LOCATION_BLOCKS_TEXTURE);
            int light=tile.getWorld().getCombinedLight(tile.getPos(),0);OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit,light&65535,light>>16);
            EnumFacing facing=tile.getWorld().getBlockState(tile.getPos()).getValue(com.vandorlabs.blocks.BlockAnimatedScreenSelector.FACING);
            TextureAtlasSprite casing=sprite(ScreenHousingTextures.texture(tile.getHousingTexture())),port=sprite("vandorlabs:blocks/matter/port"),empty=sprite("vandorlabs:blocks/matter/tank_empty");
            Tessellator t=Tessellator.getInstance();BufferBuilder b=t.getBuffer();b.begin(7,DefaultVertexFormats.POSITION_TEX_COLOR_NORMAL);
            for(int i=0;i<6;i++){
                double[] a=OUTLINE[i],c=OUTLINE[(i+1)%6];
                if(i==0||i==3){double depth=a[1];panel(b,casing,0xffffff,.25,.75,0,.18,depth,facing,-1);panel(b,casing,0xffffff,.25,.75,.82,1,depth,facing,-1);panel(b,casing,0xffffff,.25,.34,.18,.82,depth,facing,-1);panel(b,casing,0xffffff,.66,.75,.18,.82,depth,facing,-1);}
                else quad(b,casing,0xffffff,new double[][]{{a[0],1,a[1]},{a[0],0,a[1]},{c[0],0,c[1]},{c[0],1,c[1]}},facing,false,-1);
            }
            for(double height:new double[]{0,1})for(int i=0;i<6;i++){double[] a=OUTLINE[i],c=OUTLINE[(i+1)%6];quad(b,port,0xffffff,new double[][]{{.5,height,.5},{a[0],height,a[1]},{c[0],height,c[1]},{.5,height,.5}},facing,true,-1);}
            for(double depth:new double[]{.075,.925})panel(b,empty,0xffffff,.34,.66,.18,.82,depth,facing,-1);
            FluidStack fluid=tile.tank.getFluid();
            if(fluid!=null&&fluid.amount>0){TextureAtlasSprite contents=sprite(fluid.getFluid().getStill(fluid).toString());double fill=Math.min(1,fluid.amount/(double)TileEntityHexTank.CAPACITY);for(double depth:new double[]{.0615,.9385})panel(b,contents,fluid.getFluid().getColor(fluid),.34,.66,.18,.82,depth,facing,fill);}
            t.draw();
        }finally{GlStateManager.popMatrix();GlStateManager.resetColor();GlStateManager.color(1,1,1,1);}
    }
}
