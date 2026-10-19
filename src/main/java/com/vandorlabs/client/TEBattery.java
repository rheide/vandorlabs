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
public final class TEBattery extends TileEntitySpecialRenderer<TileEntityBattery> {
    private static TextureAtlasSprite sprite(String name){return Minecraft.getMinecraft().getTextureMapBlocks().getAtlasSprite("vandorlabs:blocks/"+name);}
    private static void quad(BufferBuilder b,TextureAtlasSprite sprite,double[][] points,float[] normal,int color,EnumFacing facing){
        float shade=net.minecraftforge.client.model.pipeline.LightUtil.diffuseLight(normal[0],normal[1],normal[2]);
        float r=(color>>16&255)/255F*shade,g=(color>>8&255)/255F*shade,blue=(color&255)/255F*shade;
        double[][] uv={{0,0},{0,16},{16,16},{16,0}};
        for(int i=0;i<4;i++){double[] p=AxialShellGeometry.orient(points[i],facing);b.pos(p[0],p[1],p[2]).tex(sprite.getInterpolatedU(uv[i][0]),sprite.getInterpolatedV(uv[i][1])).color(r,g,blue,1).normal(normal[0],normal[1],normal[2]).endVertex();}
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
    @Override public void render(TileEntityBattery tile,double x,double y,double z,float partial,int stage,float alpha){
        if(tile.getWorld()==null||tile.isInvalid()||!(tile.getWorld().getBlockState(tile.getPos()).getBlock() instanceof BlockBattery))return;
        EnumFacing face=tile.inputSide();double[][] outline=tile.isBlockShape()?AxialShellGeometry.BLOCK:AxialShellGeometry.HEX;
        GlStateManager.pushMatrix();
        try(TurntableRenderState state=new TurntableRenderState()){
            GlStateManager.translate(x,y,z);GlStateManager.disableLighting();GlStateManager.disableCull();GlStateManager.color(1,1,1,1);bindTexture(TextureMap.LOCATION_BLOCKS_TEXTURE);
            int light=tile.getWorld().getCombinedLight(tile.getPos(),0);OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit,light&65535,light>>16);
            TextureAtlasSprite casing=sprite("matter/casing"),cells=sprite("imported/power/battery_cell_bank");
            Tessellator t=Tessellator.getInstance();BufferBuilder b=t.getBuffer();b.begin(7,DefaultVertexFormats.POSITION_TEX_COLOR_NORMAL);
            for(int i=0;i<outline.length;i++){
                double[] a=outline[i],c=outline[(i+1)%outline.length];float[] normal=AxialShellGeometry.normal(outline,i,face);
                quad(b,Math.abs(normal[1])>.99?casing:cells,new double[][]{{a[0],1,a[1]},{a[0],0,a[1]},{c[0],0,c[1]},{c[0],1,c[1]}},normal,0xffffff,face);
            }
            for(int end=0;end<2;end++){
                double[] n=AxialShellGeometry.orient(new double[]{0,end==0?-1:1,0},face);float[] normal={(float)n[0],(float)n[1],(float)n[2]};
                boolean input=face.getAxisDirection()==(end==0?EnumFacing.AxisDirection.NEGATIVE:EnumFacing.AxisDirection.POSITIVE);
                TextureAtlasSprite port=sprite(input?"matter/energy_in":"matter/energy_out");
                // Explicit profile UVs keep each port centered over the complete cap.
                float shade=net.minecraftforge.client.model.pipeline.LightUtil.diffuseLight(normal[0],normal[1],normal[2]);
                for(int i=0;i<outline.length;i++)for(double[] p:new double[][]{{.5,end,.5},{outline[i][0],end,outline[i][1]},{outline[(i+1)%outline.length][0],end,outline[(i+1)%outline.length][1]},{.5,end,.5}}){double[] q=AxialShellGeometry.orient(p,face);b.pos(q[0],q[1],q[2]).tex(port.getInterpolatedU(p[0]*16),port.getInterpolatedV(p[2]*16)).color(shade,shade,shade,1).normal(normal[0],normal[1],normal[2]).endVertex();}
            }
            t.draw();
            // Draw solid colors without introducing another atlas texture.
            GlStateManager.disableTexture2D();b.begin(7,DefaultVertexFormats.POSITION_TEX_COLOR_NORMAL);
            double low=tile.isBlockShape()||face.getAxis()==EnumFacing.Axis.Y?0:AxialShellGeometry.HEX[0][1];
            indicator(b,casing,low,tile.chargeFraction());indicator(b,casing,1-low,tile.chargeFraction());t.draw();GlStateManager.enableTexture2D();
        }finally{GlStateManager.popMatrix();GlStateManager.resetColor();GlStateManager.color(1,1,1,1);}
    }
}
