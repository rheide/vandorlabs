package com.vandorlabs.client;

import com.vandorlabs.network.*;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.model.*;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.*;
import net.minecraft.util.math.*;
import net.minecraft.world.World;
import java.util.*;

/** Read the actual/extended baked model, including programmable world-face overrides. */
public final class ArmorTextureSampler {
    private ArmorTextureSampler() { }
    public static void sample(EntityPlayer player,EnumHand hand,BlockPos pos,EnumFacing face,Vec3d hit) {
        String sprite;
        try {sprite=resolve(player.world,pos,face,hit);}
        catch(RuntimeException unsupportedModel) {sprite=null;}
        if(sprite!=null)PacketHandler.INSTANCE.sendToServer(new MessageSampleArmorTexture(pos,hand,player.getHeldItem(hand),sprite));
        else player.sendStatusMessage(new net.minecraft.util.text.TextComponentTranslation("item.vandorlabs.programmable_armor.no_sample"),true);
    }
    public static String resolve(World world,BlockPos pos,EnumFacing face,Vec3d hit) {
        if(face==null || hit==null || !world.isBlockLoaded(pos) || world.isAirBlock(pos))return null;
        IBlockState listed=world.getBlockState(pos);
        IBlockState actual=listed.getActualState(world,pos);
        IBakedModel model=Minecraft.getMinecraft().getBlockRendererDispatcher().getModelForState(actual);
        IBlockState state=actual.getBlock().getExtendedState(actual,world,pos);
        List<BakedQuad> quads=new ArrayList<>(model.getQuads(state,null,MathHelper.getPositionRandom(pos)));
        for(EnumFacing side:EnumFacing.values())quads.addAll(model.getQuads(state,side,MathHelper.getPositionRandom(pos)));
        Vec3d point=hit.subtract(pos.getX(),pos.getY(),pos.getZ());
        TextureAtlasSprite best=null;double distance=Double.POSITIVE_INFINITY;
        for(BakedQuad quad:quads) {
            if(!CustomBlockTextures.usable(quad.getSprite()))continue;
            int[] data=quad.getVertexData();int stride=data.length/4;
            Vec3d[] vertices=new Vec3d[4];
            for(int i=0;i<4;i++)vertices[i]=new Vec3d(Float.intBitsToFloat(data[i*stride]),
                    Float.intBitsToFloat(data[i*stride+1]),Float.intBitsToFloat(data[i*stride+2]));
            double candidate=Math.min(distance(point,vertices[0],vertices[1],vertices[2]),
                    distance(point,vertices[0],vertices[2],vertices[3]));
            if(candidate<distance) {distance=candidate;best=quad.getSprite();}
        }
        if(best!=null && distance<.08)return best.getIconName();
        // Special renderers may supply no baked faces; prefer a face quad before particle artwork.
        for(BakedQuad quad:quads)if(quad.getFace()==face && CustomBlockTextures.usable(quad.getSprite()))return quad.getSprite().getIconName();
        TextureAtlasSprite component=ComponentBlockTextures.sprite(actual,false);
        if(CustomBlockTextures.usable(component))return component.getIconName();
        return CustomBlockTextures.usable(model.getParticleTexture())?model.getParticleTexture().getIconName():null;
    }
    /** Plane distance only when the projected hit lies inside the triangle. */
    private static double distance(Vec3d point,Vec3d a,Vec3d b,Vec3d c) {
        Vec3d ab=b.subtract(a),ac=c.subtract(a),normal=ab.crossProduct(ac);
        double norm=normal.lengthSquared();if(norm<1e-12)return Double.POSITIVE_INFINITY;
        Vec3d ap=point.subtract(a);double plane=ap.dotProduct(normal);
        Vec3d projected=ap.subtract(normal.scale(plane/norm));
        double aa=ab.dotProduct(ab),bb=ab.dotProduct(ac),cc=ac.dotProduct(ac);
        double d=projected.dotProduct(ab),e=projected.dotProduct(ac),den=aa*cc-bb*bb;
        double u=(cc*d-bb*e)/den,v=(aa*e-bb*d)/den;
        return u>=-1e-5 && v>=-1e-5 && u+v<=1.00001?Math.abs(plane)/Math.sqrt(norm):Double.POSITIVE_INFINITY;
    }
}
