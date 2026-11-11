package com.vandorlabs.client;

import com.vandorlabs.blocks.PanelDepth;
import com.vandorlabs.tiles.GlassTextures;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraftforge.client.event.TextureStitchEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;

/** Finite native-glass geometry, shared by every tile and both main/shadow draws. */
@Mod.EventBusSubscriber(modid="vandorlabs",value=Side.CLIENT)
public final class GlassNativePanes {
    private static final int PLACEMENTS=6;
    private static volatile Palette current;

    static final class Pane {
        // Immutable local position, atlas UV and normal; lighting remains a draw input.
        private final float[] vertices;
        Pane(TextureAtlasSprite sprite,boolean rotated,int depth) {
            vertices=new float[8*8];int at=0;
            double offset=PanelDepth.offset(depth)/16D;
            for(int face=0;face<2;face++)for(int i=0;i<4;i++){
                boolean near=face==0;
                int index=near?(4-i)&3:i;
                double x=index==1||index==2?1:0,y=index<2?0:1,z=(near?7D/16:9D/16)+offset;
                vertices[at++]=(float)(rotated?1-z:x);
                vertices[at++]=(float)y;
                vertices[at++]=(float)(rotated?x:z);
                vertices[at++]=sprite.getInterpolatedU(x*16);
                vertices[at++]=sprite.getInterpolatedV((1-y)*16);
                vertices[at++]=rotated?(near?1:-1):0;
                vertices[at++]=0;
                vertices[at++]=rotated?0:(near?-1:1);
            }
        }
        void draw(BufferBuilder out,double x,double y,double z,int sky,int block) {
            // Keep the ordinary writer: OptiFine appends its shader attributes at
            // endVertex. Reusing packed records would overwrite that live context.
            for(int at=0;at<vertices.length;at+=8)
                out.pos(x+vertices[at],y+vertices[at+1],z+vertices[at+2]).color(1F,1F,1F,1F)
                    .tex(vertices[at+3],vertices[at+4]).lightmap(sky,block)
                    .normal(vertices[at+5],vertices[at+6],vertices[at+7]).endVertex();
        }
    }

    static final class Palette {
        private final TextureMap atlas;
        private final Pane[] panes=new Pane[GlassTextures.IDS.length*GlassTextures.TINT_COUNT*PLACEMENTS];
        Palette(TextureMap atlas) {
            this.atlas=atlas;
            for(int shade=0;shade<GlassTextures.IDS.length;shade++)for(int tint=0;tint<GlassTextures.TINT_COUNT;tint++){
                TextureAtlasSprite sprite=atlas.getAtlasSprite(GlassTextures.texture(shade,1,tint));
                int first=(shade*GlassTextures.TINT_COUNT+tint)*PLACEMENTS;
                for(int rotated=0;rotated<2;rotated++)for(int depth=0;depth<3;depth++)
                    panes[first+rotated*3+depth]=new Pane(sprite,rotated!=0,depth);
            }
        }
        Pane get(int shade,int size,int tint,boolean rotated,int depth) {
            // Native glass uses the same atlas sprite for every saved legacy size.
            int placement=(rotated?3:0)+(depth==1?1:depth==2?2:0);
            return panes[(GlassTextures.clamp(shade)*GlassTextures.TINT_COUNT+GlassTextures.clampTint(tint))*PLACEMENTS+placement];
        }
    }

    static Palette palette(TextureMap atlas) {
        Palette found=current;
        if(found==null||found.atlas!=atlas)current=found=new Palette(atlas);
        return found;
    }
    static Pane get(int shade,int size,int tint,boolean rotated,int depth) {
        return palette(Minecraft.getMinecraft().getTextureMapBlocks()).get(shade,size,tint,rotated,depth);
    }
    static void clear(){current=null;}

    @SubscribeEvent(priority=EventPriority.HIGHEST) public static void before(TextureStitchEvent.Pre event) {
        if(event.getMap()==Minecraft.getMinecraft().getTextureMapBlocks())clear();
    }
    @SubscribeEvent(priority=EventPriority.LOWEST) public static void after(TextureStitchEvent.Post event) {
        if(event.getMap()==Minecraft.getMinecraft().getTextureMapBlocks())current=new Palette(event.getMap());
    }
    private GlassNativePanes(){}
}
