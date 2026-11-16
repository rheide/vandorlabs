package com.vandorlabs.client.constructors;

import java.util.*;
import javax.vecmath.Matrix4f;
import javax.vecmath.Vector4f;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.model.*;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.vertex.*;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumFacing;
import net.minecraftforge.client.model.pipeline.LightUtil;

/** Immutable baked item triangles and the exact FIXED-perspective projected model bounds. */
final class BuilderItemGeometry {
    static final class Bounds {
        double minX=Double.POSITIVE_INFINITY,minY=minX,minZ=minX,maxX=Double.NEGATIVE_INFINITY,maxY=maxX,maxZ=maxX;
        void add(double x,double y,double z){minX=Math.min(minX,x);minY=Math.min(minY,y);minZ=Math.min(minZ,z);maxX=Math.max(maxX,x);maxY=Math.max(maxY,y);maxZ=Math.max(maxZ,z);}
        boolean valid(){return Double.isFinite(minX)&&maxX>=minX;}
        double center(int axis){return axis==0?(minX+maxX)/2:axis==1?(minY+maxY)/2:(minZ+maxZ)/2;}
        double diagonal(){return valid()?Math.sqrt((maxX-minX)*(maxX-minX)+(maxY-minY)*(maxY-minY)+(maxZ-minZ)*(maxZ-minZ)):1;}
    }
    static final class Triangle {
        final double[] p,uv;final TextureAtlasSprite sprite;final int tint,surface;
        Triangle(double[] p,double[] uv,TextureAtlasSprite sprite,int tint,int surface){this.p=p;this.uv=uv;this.sprite=sprite;this.tint=tint;this.surface=surface;}
        int pixel(double a,double b,double c){if(sprite.getFrameCount()==0)return 0;int[][] levels=sprite.getFrameTextureData(0);if(levels==null||levels.length==0||levels[0]==null)return 0;double u=a*uv[0]+b*uv[2]+c*uv[4],v=a*uv[1]+b*uv[3]+c*uv[5];int x=Math.max(0,Math.min(sprite.getIconWidth()-1,(int)((u-sprite.getMinU())/(sprite.getMaxU()-sprite.getMinU())*sprite.getIconWidth()))),y=Math.max(0,Math.min(sprite.getIconHeight()-1,(int)((v-sprite.getMinV())/(sprite.getMaxV()-sprite.getMinV())*sprite.getIconHeight())));int color=levels[0][y*sprite.getIconWidth()+x];return color&0xff000000|((color>>16&255)*(tint>>16&255)/255)<<16|((color>>8&255)*(tint>>8&255)/255)<<8|(color&255)*(tint&255)/255;}
    }
    final List<Triangle> triangles;final Bounds raw,fixed;final ItemStack stack;
    private BuilderItemGeometry(ItemStack stack){this.stack=stack.copy();Minecraft mc=Minecraft.getMinecraft();IBakedModel model=mc.getRenderItem().getItemModelWithOverrides(stack,null,null);triangles=extract(model,stack);raw=bounds(triangles,null,false);org.apache.commons.lang3.tuple.Pair<? extends IBakedModel,Matrix4f> perspective=model.handlePerspective(ItemCameraTransforms.TransformType.FIXED);fixed=bounds(perspective.getLeft()==model?triangles:extract(perspective.getLeft(),stack),perspective.getRight(),true);}
    private static final LinkedList<BuilderItemGeometry> CACHE=new LinkedList<>();
    private static long retained;
    private long bytes(){return 128L+triangles.size()*256L;}
    static BuilderItemGeometry get(ItemStack stack){for(Iterator<BuilderItemGeometry> it=CACHE.iterator();it.hasNext();){BuilderItemGeometry value=it.next();if(ItemStack.areItemsEqual(value.stack,stack)&&ItemStack.areItemStackTagsEqual(value.stack,stack)){it.remove();CACHE.addFirst(value);return value;}}BuilderItemGeometry value=new BuilderItemGeometry(stack);CACHE.addFirst(value);retained+=value.bytes();while(CACHE.size()>1&&(CACHE.size()>32||retained>4L*1024*1024))retained-=CACHE.removeLast().bytes();return value;}
    static void clear(){CACHE.clear();retained=0;}
    private static Bounds bounds(List<Triangle> triangles,Matrix4f transform,boolean centered){Bounds bounds=new Bounds();for(Triangle triangle:triangles)for(int v=0;v<3;v++){Vector4f point=new Vector4f((float)triangle.p[v*3]-(centered?.5F:0),(float)triangle.p[v*3+1]-(centered?.5F:0),(float)triangle.p[v*3+2]-(centered?.5F:0),1);if(transform!=null)transform.transform(point);bounds.add(point.x,point.y,point.z);}return bounds;}
    private static List<Triangle> extract(IBakedModel model,ItemStack stack){List<Triangle> result=new ArrayList<>();for(int side=-1;side<6;side++)for(BakedQuad quad:model.getQuads(null,side<0?null:EnumFacing.VALUES[side],0)){
        if(result.size()>=2048)return Collections.unmodifiableList(result);VertexFormat format=quad.getFormat();int position=-1,uv=-1,color=-1;for(int element=0;element<format.getElementCount();element++){VertexFormatElement field=format.getElement(element);if(field.getUsage()==VertexFormatElement.EnumUsage.POSITION)position=element;if(field.getUsage()==VertexFormatElement.EnumUsage.UV&&field.getIndex()==0)uv=element;if(field.getUsage()==VertexFormatElement.EnumUsage.COLOR)color=element;}if(position<0||uv<0)continue;
        double[] p=new double[12],t=new double[8];float[] values=new float[4];int tint=quad.hasTintIndex()?Minecraft.getMinecraft().getItemColors().colorMultiplier(stack,quad.getTintIndex()):0xffffff;if(tint<0)tint=0xffffff;
        if(color>=0){LightUtil.unpack(quad.getVertexData(),values,format,0,color);tint=((int)((tint>>16&255)*values[0]))<<16|((int)((tint>>8&255)*values[1]))<<8|(int)((tint&255)*values[2]);}
        for(int v=0;v<4;v++){LightUtil.unpack(quad.getVertexData(),values,format,v,position);for(int a=0;a<3;a++)p[v*3+a]=values[a];LightUtil.unpack(quad.getVertexData(),values,format,v,uv);t[v*2]=values[0];t[v*2+1]=values[1];}
        int surface=result.size()/2;for(int[] indices:new int[][]{{0,1,2},{0,2,3}}){double[] tp=new double[9],tu=new double[6];for(int v=0;v<3;v++){System.arraycopy(p,indices[v]*3,tp,v*3,3);System.arraycopy(t,indices[v]*2,tu,v*2,2);}result.add(new Triangle(tp,tu,quad.getSprite(),tint,surface));}
    }return Collections.unmodifiableList(result);}
}
