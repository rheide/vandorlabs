package com.vandorlabs.client;

import com.vandorlabs.vehicle.VehicleStructure;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import java.util.*;

/** Client-only material sampling; never modifies the chip or instantiates vehicle tiles/worlds. */
final class VehiclePreviewSampler {
    // 3,375 cells versus the saved thumbnail's 1,728: nearly twice the detail, not eight times.
    static final int SIZE=15;
    static final class Sample {
        final int[] colors;final float centerX,centerY,centerZ;
        Sample(int[] colors){this.colors=colors;int minX=SIZE,minY=SIZE,minZ=SIZE,maxX=-1,maxY=-1,maxZ=-1;for(int y=0;y<SIZE;y++)for(int z=0;z<SIZE;z++)for(int x=0;x<SIZE;x++)if(occupied(x,y,z)){minX=Math.min(minX,x);minY=Math.min(minY,y);minZ=Math.min(minZ,z);maxX=Math.max(maxX,x);maxY=Math.max(maxY,y);maxZ=Math.max(maxZ,z);}centerX=maxX<0?SIZE/2F:(minX+maxX+1)/2F;centerY=maxY<0?SIZE/2F:(minY+maxY+1)/2F;centerZ=maxZ<0?SIZE/2F:(minZ+maxZ+1)/2F;}
        boolean occupied(int x,int y,int z){return colors[(y*SIZE+z)*SIZE+x]!=0;}
        int color(int x,int y,int z){return colors[(y*SIZE+z)*SIZE+x]&0xffffff;}
    }
    private static final LinkedHashMap<IBlockState,Integer> COLORS=new LinkedHashMap<IBlockState,Integer>(64,.75F,true);
    static void clear(){COLORS.clear();}
    static Sample read(NBTTagCompound tag){
        if(!tag.hasKey("VehicleDesign",7))return null;
        try{
            byte[] raw=com.vandorlabs.copier.core.BlueprintCompression.decode(tag.getInteger("VehicleDesignCodec"),tag.getByteArray("VehicleDesign"));
            VehicleStructure structure=VehicleStructure.decode(raw);
            IBlockState[] materials=new IBlockState[SIZE*SIZE*SIZE];double[] weights=new double[materials.length];
            double scale=SIZE/Math.max(structure.bounds.maxX,Math.max(structure.bounds.maxY,structure.bounds.maxZ));
            for(VehicleStructure.Cell cell:structure.cells){double x0=cell.pos.getX()*scale,y0=cell.pos.getY()*scale,z0=cell.pos.getZ()*scale,x1=x0+scale,y1=y0+scale,z1=z0+scale;
                for(int y=Math.max(0,(int)Math.floor(y0));y<Math.min(SIZE,(int)Math.ceil(y1));y++)for(int z=Math.max(0,(int)Math.floor(z0));z<Math.min(SIZE,(int)Math.ceil(z1));z++)for(int x=Math.max(0,(int)Math.floor(x0));x<Math.min(SIZE,(int)Math.ceil(x1));x++){
                    int at=(y*SIZE+z)*SIZE+x;double overlap=(Math.min(x+1,x1)-Math.max(x,x0))*(Math.min(y+1,y1)-Math.max(y,y0))*(Math.min(z+1,z1)-Math.max(z,z0));
                    if(overlap>weights[at]){weights[at]=overlap;materials[at]=cell.state;}
                }
            }
            int[] colors=new int[materials.length];for(int at=0;at<colors.length;at++)if(materials[at]!=null)colors[at]=0xff000000|primary(materials[at]);return new Sample(colors);
        }catch(java.io.IOException|RuntimeException invalid){return null;} // Legacy/unsupported payloads retain their saved occupancy thumbnail.
    }
    private static int primary(IBlockState state){Integer cached=COLORS.get(state);if(cached!=null)return cached;int color=0xaaaaaa;
        try{
            int[] count=new int[4096],red=new int[4096],green=new int[4096],blue=new int[4096];
            Minecraft mc=Minecraft.getMinecraft();net.minecraft.client.renderer.block.model.IBakedModel model=mc.getBlockRendererDispatcher().getModelForState(state);
            Set<String> sampled=new HashSet<String>();int faces=0;
            for(int side=-1;side<6;side++)for(BakedQuad quad:model.getQuads(state,side<0?null:EnumFacing.VALUES[side],0)){
                if(faces++>=64)break;TextureAtlasSprite sprite=quad.getSprite();int tint=quad.hasTintIndex()?mc.getBlockColors().colorMultiplier(state,null,null,quad.getTintIndex()):0xffffff;if(tint<0)tint=0xffffff;
                if(!sampled.add(sprite.getIconName()+":"+tint))continue;sample(sprite,tint,count,red,green,blue);
            }
            if(sampled.isEmpty())sample(model.getParticleTexture(),0xffffff,count,red,green,blue);
            int best=0;for(int bin=1;bin<count.length;bin++)if(count[bin]>count[best])best=bin;
            if(count[best]>0)color=(red[best]/count[best])<<16|(green[best]/count[best])<<8|blue[best]/count[best];else color=state.getMapColor(null,BlockPos.ORIGIN).colorValue;
        }catch(RuntimeException unsupported){try{color=state.getMapColor(null,BlockPos.ORIGIN).colorValue;}catch(RuntimeException absent){color=0xaaaaaa;}}
        COLORS.put(state,color&0xffffff);if(COLORS.size()>4096)COLORS.remove(COLORS.keySet().iterator().next());return color&0xffffff;
    }
    private static void sample(TextureAtlasSprite sprite,int tint,int[] count,int[] red,int[] green,int[] blue){if(sprite.getFrameCount()==0)return;int[][] levels=sprite.getFrameTextureData(0);if(levels==null||levels.length==0||levels[0]==null)return;int[] pixels=levels[0];int step=Math.max(1,(pixels.length+511)/512);
        for(int at=0;at<pixels.length;at+=step){int pixel=pixels[at];if((pixel>>>24)<128)continue;int r=(pixel>>16&255)*(tint>>16&255)/255,g=(pixel>>8&255)*(tint>>8&255)/255,b=(pixel&255)*(tint&255)/255,bin=(r>>4)<<8|(g>>4)<<4|(b>>4);count[bin]++;red[bin]+=r;green[bin]+=g;blue[bin]+=b;}
    }
    private VehiclePreviewSampler(){}
}
