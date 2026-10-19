package com.vandorlabs.client;

import com.vandorlabs.tiles.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.texture.*;
import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.util.*;
import net.minecraftforge.fluids.FluidStack;

/** Axial hollow shell with level fluid and windows on its diagonal sides. */
@net.minecraftforge.fml.common.Mod.EventBusSubscriber(modid="vandorlabs",value=net.minecraftforge.fml.relauncher.Side.CLIENT)
public final class TEHexTank extends TileEntitySpecialRenderer<TileEntityHexTank> {
    @net.minecraftforge.fml.common.eventhandler.SubscribeEvent public static void stitch(net.minecraftforge.client.event.TextureStitchEvent.Pre event){for(String name:new String[]{"port","tank_empty"})event.getMap().registerSprite(new ResourceLocation("vandorlabs:blocks/matter/"+name));}
    private static final double[][] OUTLINE=AxialShellGeometry.HEX;
    private static final double[][] BLOCK=AxialShellGeometry.BLOCK;
    private static final double INNER=.90, LIQUID=.895, FLOOR=.04, CEILING=.96;
    private static TextureAtlasSprite sprite(String name){return Minecraft.getMinecraft().getTextureMapBlocks().getAtlasSprite(name);}
    /** Horizontal hexagons retain flat top/bottom faces, like hexagonal thrusters. */
    static double[] orient(double[] p,EnumFacing f){return AxialShellGeometry.orient(p,f);}
    static double fluidLevel(int amount){return FLOOR+(CEILING-FLOOR)*Math.max(0,Math.min(1,amount/(double)TileEntityHexTank.CAPACITY));}
    private static double[] point(double[][] outline,int edge,double u,double y,double scale){double[] a=outline[edge],c=outline[(edge+1)%outline.length];return new double[]{.5+(a[0]+(c[0]-a[0])*u-.5)*scale,y,.5+(a[1]+(c[1]-a[1])*u-.5)*scale};}
    private static void quad(BufferBuilder b,TextureAtlasSprite s,int color,double[][] vertices,EnumFacing facing,boolean cap){
        float r=(color>>16&255)/255F,g=(color>>8&255)/255F,blue=(color&255)/255F;
        double[][] uv={{0,0},{0,16},{16,16},{16,0}};
        for(int i=0;i<4;i++){double[] p=orient(vertices[i],facing);b.pos(p[0],p[1],p[2]).tex(s.getInterpolatedU(cap?vertices[i][0]*16:uv[i][0]),s.getInterpolatedV(cap?vertices[i][2]*16:uv[i][1])).color(r,g,blue,1).normal(0,1,0).endVertex();}
    }
    private static void side(double[][] outline,BufferBuilder b,TextureAtlasSprite s,int color,int edge,double left,double right,double bottom,double top,double scale,EnumFacing facing){quad(b,s,color,new double[][]{point(outline,edge,left,top,scale),point(outline,edge,left,bottom,scale),point(outline,edge,right,bottom,scale),point(outline,edge,right,top,scale)},facing,false);}
    private static void cap(double[][] outline,BufferBuilder b,TextureAtlasSprite s,int color,double y,double scale,EnumFacing facing){double cx=0,cz=0;for(double[] p:outline){cx+=p[0];cz+=p[1];}cx=.5+(cx/outline.length-.5)*scale;cz=.5+(cz/outline.length-.5)*scale;for(int i=0;i<outline.length;i++)quad(b,s,color,new double[][]{{cx,y,cz},point(outline,i,0,y,scale),point(outline,i,1,y,scale),{cx,y,cz}},facing,true);}
    static double[] window(boolean block,int edge,EnumFacing facing){
        if(facing.getAxis()!=EnumFacing.Axis.Y && (block?edge==0||edge==2:edge==0||edge==3))return null;
        double[][] outline=block?BLOCK:OUTLINE;double[] a=outline[edge],b=outline[(edge+1)%outline.length];
        double half=.125/Math.hypot(b[0]-a[0],b[1]-a[1]);return new double[]{.5-half,.5+half,.375,.625};
    }
    static double[] casingUV(double[][] outline,int edge,double u,double y,boolean tiled,double scale){
        double[] a=outline[edge],b=outline[(edge+1)%outline.length];return new double[]{u*(tiled?Math.hypot(b[0]-a[0],b[1]-a[1])*scale:1)*16,(1-y)*16};
    }
    static float[] casingNormal(double[][] outline,int edge,EnumFacing facing){
        return AxialShellGeometry.normal(outline,edge,facing);
    }
    private static void casingSide(double[][] outline,BufferBuilder b,TextureAtlasSprite s,int color,int edge,double left,double right,double bottom,double top,double scale,EnumFacing facing,boolean tiled){
        double[][] vertices={point(outline,edge,left,top,scale),point(outline,edge,left,bottom,scale),point(outline,edge,right,bottom,scale),point(outline,edge,right,top,scale)};
        double[][] uv={casingUV(outline,edge,left,top,tiled,scale),casingUV(outline,edge,left,bottom,tiled,scale),casingUV(outline,edge,right,bottom,tiled,scale),casingUV(outline,edge,right,top,tiled,scale)};
        float[] normal=casingNormal(outline,edge,facing);float shade=net.minecraftforge.client.model.pipeline.LightUtil.diffuseLight(normal[0],normal[1],normal[2]);
        float r=(color>>16&255)/255F*shade,g=(color>>8&255)/255F*shade,blue=(color&255)/255F*shade;
        for(int i=0;i<4;i++){double[] p=orient(vertices[i],facing);b.pos(p[0],p[1],p[2]).tex(s.getInterpolatedU(uv[i][0]),s.getInterpolatedV(uv[i][1])).color(r,g,blue,1).normal(normal[0],normal[1],normal[2]).endVertex();}
    }
    private static void wall(double[][] outline,BufferBuilder b,TextureAtlasSprite s,int color,int edge,double scale,double low,double high,EnumFacing facing,boolean tiled){
        double[] w=window(outline.length==4,edge,facing);if(w==null){casingSide(outline,b,s,color,edge,0,1,low,high,scale,facing,tiled);return;}
        casingSide(outline,b,s,color,edge,0,1,low,w[2],scale,facing,tiled);casingSide(outline,b,s,color,edge,0,1,w[3],high,scale,facing,tiled);
        casingSide(outline,b,s,color,edge,0,w[0],w[2],w[3],scale,facing,tiled);casingSide(outline,b,s,color,edge,w[1],1,w[2],w[3],scale,facing,tiled);
    }
    static double area(double[][] polygon){double sum=0;for(int i=0;i<polygon.length;i++){double[] a=polygon[i],b=polygon[(i+1)%polygon.length];sum+=a[0]*b[1]-b[0]*a[1];}return Math.abs(sum)*.5;}
    private static double[][] below(double[][] polygon,double level){java.util.List<double[]> out=new java.util.ArrayList<>();for(int i=0;i<polygon.length;i++){double[] a=polygon[i],b=polygon[(i+1)%polygon.length];boolean inside=a[1]<=level,next=b[1]<=level;if(inside)out.add(a.clone());if(inside!=next){double t=(level-a[1])/(b[1]-a[1]);out.add(new double[]{a[0]+(b[0]-a[0])*t,level});}}return out.toArray(new double[0][]);}
    static double[][] fluidOutline(boolean block,int amount){double[][] polygon=block?BLOCK:OUTLINE;double fraction=Math.max(0,Math.min(1,amount/(double)TileEntityHexTank.CAPACITY)),low=0,high=1,total=area(polygon);if(fraction==1)return polygon;for(int i=0;i<40;i++){double mid=(low+high)/2;if(area(below(polygon,mid))<total*fraction)low=mid;else high=mid;}return below(polygon,(low+high)/2);}
    @Override public void render(TileEntityHexTank tile,double x,double y,double z,float partial,int stage,float alpha){
        if(tile.getWorld()==null||tile.isInvalid()||!tile.getWorld().getBlockState(tile.getPos()).getProperties().containsKey(com.vandorlabs.blocks.BlockAnimatedScreenSelector.FACING))return;
        GlStateManager.pushMatrix();
        try(TurntableRenderState state=new TurntableRenderState()){
            GlStateManager.translate(x,y,z);GlStateManager.disableLighting();GlStateManager.disableCull();GlStateManager.color(1,1,1,1);bindTexture(TextureMap.LOCATION_BLOCKS_TEXTURE);
            int light=tile.getWorld().getCombinedLight(tile.getPos(),0);OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit,light&65535,light>>16);
            EnumFacing facing=tile.getWorld().getBlockState(tile.getPos()).getValue(com.vandorlabs.blocks.BlockAnimatedScreenSelector.FACING);
            TextureAtlasSprite casing=sprite(ScreenHousingTextures.texture(tile.getHousingTexture())),port=sprite("vandorlabs:blocks/matter/port"),liner=sprite("vandorlabs:blocks/matter/tank_empty");
            double[][] outline=tile.isBlockShape()?BLOCK:OUTLINE;
            Tessellator t=Tessellator.getInstance();BufferBuilder b=t.getBuffer();b.begin(7,DefaultVertexFormats.POSITION_TEX_COLOR_NORMAL);
            for(int i=0;i<outline.length;i++){
                wall(outline,b,casing,0xffffff,i,1,0,1,facing,tile.getMainTextureLayout()==1);wall(outline,b,liner,0xffffff,i,INNER,FLOOR,CEILING,facing,false);
                double[] w=window(tile.isBlockShape(),i,facing);if(w==null)continue;
                // Thickness around the four edges of every window opening.
                for(double u:new double[]{w[0],w[1]})quad(b,casing,0xb5c2cc,new double[][]{point(outline,i,u,w[2],1),point(outline,i,u,w[3],1),point(outline,i,u,w[3],INNER),point(outline,i,u,w[2],INNER)},facing,false);
                for(double h:new double[]{w[2],w[3]})quad(b,casing,0xb5c2cc,new double[][]{point(outline,i,w[0],h,1),point(outline,i,w[1],h,1),point(outline,i,w[1],h,INNER),point(outline,i,w[0],h,INNER)},facing,false);
            }
            cap(outline,b,port,0xffffff,0,1,facing);cap(outline,b,port,0xffffff,1,1,facing);
            cap(outline,b,liner,0xffffff,FLOOR,INNER,facing);cap(outline,b,liner,0xffffff,CEILING,INNER,facing);t.draw();
            FluidStack fluid=tile.tank.getFluid();
            GlStateManager.enableBlend();GlStateManager.tryBlendFuncSeparate(770,771,1,0);
            if(fluid!=null&&fluid.amount>0){
                TextureAtlasSprite contents=sprite(fluid.getFluid().getStill(fluid).toString());double top=fluidLevel(fluid.amount);int color=fluid.getFluid().getColor(fluid);
                b.begin(7,DefaultVertexFormats.POSITION_TEX_COLOR_NORMAL);
                double[][] liquid=facing.getAxis()==EnumFacing.Axis.Y?outline:fluidOutline(tile.isBlockShape(),fluid.amount);double high=facing.getAxis()==EnumFacing.Axis.Y?top:CEILING;
                for(int i=0;i<liquid.length;i++)side(liquid,b,contents,color,i,0,1,FLOOR+.0005,high,LIQUID,facing);
                cap(liquid,b,contents,color,high,LIQUID,facing);cap(liquid,b,contents,color,FLOOR+.0005,LIQUID,facing);t.draw();
            }
            // Actual glass panes, with transparent centers rather than a painted level image.
            b.begin(7,DefaultVertexFormats.POSITION_TEX_COLOR_NORMAL);TextureAtlasSprite glass=sprite("minecraft:blocks/glass");
            for(int i=0;i<outline.length;i++){double[] w=window(tile.isBlockShape(),i,facing);if(w!=null)side(outline,b,glass,0xffffff,i,w[0],w[1],w[2],w[3],1.0005,facing);}t.draw();
        }finally{GlStateManager.popMatrix();GlStateManager.resetColor();GlStateManager.color(1,1,1,1);}
    }
}
