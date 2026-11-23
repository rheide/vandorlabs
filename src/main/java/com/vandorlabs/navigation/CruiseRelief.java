package com.vandorlabs.navigation;

import java.util.Random;
import net.minecraft.init.Biomes;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.gen.ChunkGeneratorSettings;
import net.minecraft.world.gen.NoiseGeneratorOctaves;
import net.minecraft.world.gen.NoiseGeneratorPerlin;
import net.minecraft.world.gen.layer.GenLayer;

/** Sample the Overworld density field directly, without constructing a Chunk or running population. */
final class CruiseRelief {
    private static final int GRID=com.vandorlabs.network.MessageCruiseTerrain.HEIGHT_GRID, VERTICAL=33;
    private final NoiseGeneratorOctaves lower,upper,blend,depth;
    final NoiseGeneratorPerlin surface;
    private final ChunkGeneratorSettings settings=new ChunkGeneratorSettings.Factory().build();
    private final double[] lowerNoise=new double[GRID*GRID*VERTICAL];
    private final double[] upperNoise=new double[lowerNoise.length],blendNoise=new double[lowerNoise.length];
    private final double[] depthNoise=new double[GRID*GRID],height=new double[GRID*GRID];
    private final float[] weights=new float[25];

    CruiseRelief(long seed){
        Random random=new Random(seed);
        lower=new NoiseGeneratorOctaves(random,16);
        upper=new NoiseGeneratorOctaves(random,16);
        blend=new NoiseGeneratorOctaves(random,8);
        surface=new NoiseGeneratorPerlin(random,4);
        new NoiseGeneratorOctaves(random,10); // skip vanilla's scale noise in the seed sequence
        depth=new NoiseGeneratorOctaves(random,16);
        for(int dz=-2;dz<=2;dz++)for(int dx=-2;dx<=2;dx++)
            weights[(dz+2)*5+dx+2]=(float)(10/Math.sqrt(dx*dx+dz*dz+.2));
    }

    byte[] shade(CruiseAtlas.Tile tile,GenLayer biomes,CruiseScratch scratch,boolean amplified,int[] pixels){
        // One height per four texture pixels, including an identical edge row in adjacent tiles.
        int stride=tile.step, originX=tile.x/(4*stride),originZ=tile.z/(4*stride);
        double horizontal=settings.coordinateScale*stride,vertical=settings.heightScale;
        lower.generateNoiseOctaves(lowerNoise,originX,0,originZ,GRID,VERTICAL,GRID,horizontal,vertical,horizontal);
        upper.generateNoiseOctaves(upperNoise,originX,0,originZ,GRID,VERTICAL,GRID,horizontal,vertical,horizontal);
        blend.generateNoiseOctaves(blendNoise,originX,0,originZ,GRID,VERTICAL,GRID,
                horizontal/settings.mainNoiseScaleX,vertical/settings.mainNoiseScaleY,horizontal/settings.mainNoiseScaleZ);
        depth.generateNoiseOctaves(depthNoise,originX,originZ,GRID,GRID,
                settings.depthNoiseScaleX*stride,settings.depthNoiseScaleZ*stride,settings.depthNoiseScaleExponent);
        int rowWidth=(GRID-1)*stride+5;
        for(int z=0;z<GRID;z++){
            scratch.reset();
            int[] ids=biomes.getInts(tile.x/4-2,tile.z/4+z*stride-2,rowWidth,5);
            for(int x=0;x<GRID;x++){
                int center=x*stride+2+rowWidth*2, column=x*GRID+z;
                float base=Biome.getBiome(ids[center],Biomes.PLAINS).getBaseHeight();
                double weight=0,elevation=0,variation=0;
                for(int dz=-2;dz<=2;dz++)for(int dx=-2;dx<=2;dx++){
                    Biome biome=Biome.getBiome(ids[center+dx+dz*rowWidth],Biomes.PLAINS);
                    double d=biome.getBaseHeight(),v=biome.getHeightVariation();
                    if(amplified&&d>0){d=1+2*d;v=1+4*v;}
                    double w=weights[(dz+2)*5+dx+2]/(d+2);
                    if(biome.getBaseHeight()>base)w*=.5;
                    weight+=w;elevation+=w*d;variation+=w*v;
                }
                double d=depthNoise[column]/8000;
                if(d<0)d*=-.3;
                d=d*3-2;
                d=d<0?Math.max(-1,d*.5)/2.8:Math.min(1,d)/8;
                double centerY=settings.baseSize+( (elevation/weight*4-1)/8+d*.2)*settings.baseSize*.5;
                height[z*GRID+x]=surfaceHeight(column,centerY,variation/weight*.9+.1);
            }
        }
        int size=CruiseAtlas.SIZE;
        for(int z=0;z<size;z++)for(int x=0;x<size;x++){
            int i=(z/4)*GRID+x/4;
            double fx=(x%4+.5)/4,fz=(z%4+.5)/4;
            double nw=height[i],ne=height[i+1],sw=height[i+GRID],se=height[i+GRID+1];
            double h=(nw+(ne-nw)*fx)*(1-fz)+(sw+(se-sw)*fx)*fz;
            int p=z*size+x;
            if(h<settings.seaLevel-1){
                double shallow=Math.max(0,1-(settings.seaLevel-h)/23);
                pixels[p]=0xff000000|((int)(28+22*shallow)<<16)|((int)(78+43*shallow)<<8)|(int)(129+27*shallow);
            }else{
                double slope=((ne-nw)*(1-fz)+(se-sw)*fz+(sw-nw)*(1-fx)+(se-ne)*fx)/(4*tile.step);
                int color=pixels[p];
                if(color==0xff28659b)color=h<settings.seaLevel+2?0xffc7b77c:0xff637e4f;
                double light=Math.max(.64,Math.min(1.32,1+.6*slope));
                pixels[p]=0xff000000|(Math.min(255,(int)((color>>16&255)*light))<<16)
                        |(Math.min(255,(int)((color>>8&255)*light))<<8)|Math.min(255,(int)((color&255)*light));
            }
        }
        byte[] elevations=new byte[height.length];
        for(int i=0;i<height.length;i++)elevations[i]=(byte)Math.round(Math.max(settings.seaLevel-1,Math.min(255,height[i])));
        return elevations;
    }

    private double surfaceHeight(int column,double center,double variation){
        double above=-10;
        for(int y=31;y>=0;y--){
            int i=column*VERTICAL+y;
            double mix=Math.max(0,Math.min(1,(blendNoise[i]/10+1)*.5));
            double density=(1-mix)*lowerNoise[i]/settings.lowerLimitScale+mix*upperNoise[i]/settings.upperLimitScale;
            double sink=(y-center)*settings.stretchY*.5/variation;
            density-=sink<0?4*sink:sink;
            if(y>29)density+=(-10-density)*(y-29)/3;
            if(density>=0)return (y+density/(density-above))*8;
            above=density;
        }
        return 0;
    }
}
