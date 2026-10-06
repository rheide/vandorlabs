package com.vandorlabs.client;

import net.minecraft.block.state.IBlockState;
import net.minecraft.client.renderer.block.model.*;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.Vec3d;
import java.util.*;

/** Compare actual baked geometry with the marked row on the panel texture. */
final class WallSliderModelChecks {
    static void check(IBakedModel model,IBlockState state,EnumFacing facing,int level){
        List<BakedQuad> quads=new ArrayList<>(model.getQuads(state,null,0));
        for(EnumFacing side:EnumFacing.values())quads.addAll(model.getQuads(state,side,0));
        BakedQuad panel=null,cap=null;
        for(BakedQuad quad:quads){
            if(quad.getFace()!=facing)continue;
            String sprite=quad.getSprite().getIconName();
            if(sprite.contains("/slider_"))panel=quad;
            if(sprite.endsWith(level==0?"/amber":"/cyan"))cap=quad;
        }
        require(panel!=null && cap!=null,"missing outward panel/grip face "+state);
        int axis=facing.getAxis()==EnumFacing.Axis.X?0:facing.getAxis()==EnumFacing.Axis.Y?1:2;
        double plane=facing.getAxisDirection()==EnumFacing.AxisDirection.POSITIVE?1.0/16:15.0/16;
        for(int vertex=0;vertex<4;vertex++)require(Math.abs(coordinate(position(panel,vertex),axis)-plane)<.0001,"slider panel mounted away from support "+state);
        Vec3d center=Vec3d.ZERO;
        for(int vertex=0;vertex<4;vertex++)center=center.add(position(cap,vertex).scale(.25));
        if(facing==EnumFacing.UP && (level==0 || level==3)){
            EnumFacing look=EnumFacing.getHorizontal(state.getValue(com.vandorlabs.blocks.BlockVandorSwitch.ROTATION));
            double distance=(center.x-.5)*look.getFrontOffsetX()+(center.z-.5)*look.getFrontOffsetZ();
            require(level==0?distance<-.15:distance>.15,"floor slider Off end must face the placing player "+state);
        }
        Vec3d origin=position(panel,0),u=position(panel,1).subtract(origin),v=position(panel,3).subtract(origin),point=center.subtract(origin);
        double a=point.dotProduct(u)/u.lengthSquared(),b=point.dotProduct(v)/v.lengthSquared();
        float[] uv0=uv(panel,0),uv1=uv(panel,1),uv3=uv(panel,3);
        float mappedU=(float)(uv0[0]+a*(uv1[0]-uv0[0])+b*(uv3[0]-uv0[0]));
        float mappedV=(float)(uv0[1]+a*(uv1[1]-uv0[1])+b*(uv3[1]-uv0[1]));
        TextureAtlasSprite sprite=panel.getSprite();
        double texelU=sprite.getUnInterpolatedU(mappedU),texelV=sprite.getUnInterpolatedV(mappedV);
        require(texelU>6 && texelU<9 && Math.abs(texelV-(12.25-level*3))<.03,"slider grip misses marked level "+state+": texel "+texelU+", "+texelV);
    }
    private static Vec3d position(BakedQuad quad,int vertex){int[] data=quad.getVertexData();int at=vertex*(data.length/4);return new Vec3d(Float.intBitsToFloat(data[at]),Float.intBitsToFloat(data[at+1]),Float.intBitsToFloat(data[at+2]));}
    private static float[] uv(BakedQuad quad,int vertex){int[] data=quad.getVertexData();int at=vertex*(data.length/4)+quad.getFormat().getUvOffsetById(0)/4;return new float[]{Float.intBitsToFloat(data[at]),Float.intBitsToFloat(data[at+1])};}
    private static double coordinate(Vec3d point,int axis){return axis==0?point.x:axis==1?point.y:point.z;}
    private static void require(boolean value,String message){if(!value)throw new IllegalStateException(message);}
    private WallSliderModelChecks(){}
}
