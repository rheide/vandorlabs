package com.vandorlabs.client;

import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;

/** Tile a one-block image on the two leaf faces without sampling outside its atlas sprite. */
final class DoorFaceTextureMesh {
    static void draw(BufferBuilder buffer,TextureAtlasSprite sprite,double[] b,int light) {
        draw(buffer,sprite,b,light,1);
    }
    static void draw(BufferBuilder buffer,TextureAtlasSprite sprite,double[] b,int light,double aspect) {
        draw(buffer,sprite,b,light,aspect,1);
    }
    static void draw(BufferBuilder buffer,TextureAtlasSprite sprite,double[] b,int light,double aspect,double scale) {
        double width=Math.max(1,aspect)/scale,height=Math.max(1,1/aspect)/scale;
        for(double x=b[0];x<b[3]-1e-9;x+=width)for(double y=b[1];y<b[4]-1e-9;y+=height) {
            double x1=Math.min(x+width,b[3]),y1=Math.min(y+height,b[4]),u=(x1-x)/width,spanV=(y1-y)/height;
            for(int side=0;side<2;side++) {
                double z=b[side==0?2:5];
                double[][] p=side==0?new double[][]{{x,y,z,0,1},{x,y1,z,0,1-spanV},{x1,y1,z,u,1-spanV},{x1,y,z,u,1}}
                    :new double[][]{{x,y,z,0,1},{x1,y,z,u,1},{x1,y1,z,u,1-spanV},{x,y1,z,0,1-spanV}};
                for(double[] v:p)buffer.pos(v[0],v[1],v[2]).color(255,255,255,255).tex(sprite.getInterpolatedU(v[3]*16),sprite.getInterpolatedV(v[4]*16)).lightmap(light>>>16,light&65535).normal(0,0,side==0?-1:1).endVertex();
            }
        }
    }
}
