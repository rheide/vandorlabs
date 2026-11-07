package com.vandorlabs.neoforge;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.vandorlabs.neoforge.client.NativeDoorSpriteSource;
import com.vandorlabs.neoforge.door.NativeDoorAssets;
import java.nio.ByteBuffer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.*;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;

/** Title-only real model bake and alpha mip upload, separate from placed-door/render acceptance. */
@EventBusSubscriber(modid="vandorlabs",value=Dist.CLIENT)
public final class NativeDoorResourceChecks {
    private static int ticks;private static boolean done;
    private static void need(boolean value,String message){if(!value)throw new AssertionError("Door resource: "+message);}
    private static boolean visible(NativeImage image){for(int y=0;y<image.getHeight();y++)for(int x=0;x<image.getWidth();x++)if((image.getPixelRGBA(x,y)>>>24)>0)return true;return false;}
    @SubscribeEvent public static void tick(ClientTickEvent.Post event){
        if(!"true".equals(System.getenv("VANDOR_DOOR_RESOURCE_CHECK"))||done)return;var mc=Minecraft.getInstance();if(mc.screen instanceof net.minecraft.client.gui.screens.AccessibilityOnboardingScreen screen){screen.onClose();return;}
        if(++ticks>2400)throw new AssertionError("Door resource title timeout screen="+mc.screen+" overlay="+mc.getOverlay());if(ticks<80||mc.getOverlay()!=null||!(mc.screen instanceof net.minecraft.client.gui.screens.TitleScreen))return;
        need(mc.level==null&&mc.getSingleplayerServer()==null,"no world/server");var getter=mc.getTextureAtlas(TextureAtlas.LOCATION_BLOCKS);int sprites=0,models=0,quads=0;
        for(var descriptor:NativeDoorAssets.textures()){
            var id=ResourceLocation.parse(descriptor.id());var contents=getter.apply(id).contents();need(contents.name().equals(id)&&contents instanceof NativeDoorSpriteSource.Contents,"actual registered sprite "+id);
            var door=(NativeDoorSpriteSource.Contents)contents;need(door.usedU()==descriptor.usedU()&&door.usedV()==descriptor.usedV()&&door.width()==descriptor.size()&&door.height()==descriptor.size(),"full content ratios "+id);sprites++;
        }
        var random=RandomSource.create(0);for(String id:NativeDoorAssets.models()){
            var model=mc.getModelManager().getModel(ModelResourceLocation.standalone(ResourceLocation.parse(id)));int count=model.getQuads(null,null,random).size();for(var side:Direction.values())count+=model.getQuads(null,side,random).size();need(count==NativeDoorAssets.faces(id),"actual inherited authored face count "+id+" expected="+NativeDoorAssets.faces(id)+" got="+count);models++;quads+=count;
        }
        var glassId=ResourceLocation.fromNamespaceAndPath("vandorlabs","door-artwork/blocks/space_doors/low/glass_tile");var sprite=getter.apply(glassId);var glass=sprite.contents();need(glass.byMipLevel.length>=3&&visible(glass.byMipLevel[2]),"faint reflection survives actual mip2");
        NativeImage copy=new NativeImage(glass.getOriginalImage().getWidth(),glass.getOriginalImage().getHeight(),false);copy.copyFrom(glass.getOriginalImage());NativeImage[] vanilla=null;
        try{vanilla=MipmapGenerator.generateMipLevels(new NativeImage[]{copy},2);need(!visible(vanilla[2]),"vanilla cutout negative control reproduces lost reflection");}finally{if(vanilla==null)copy.close();else for(var image:vanilla)image.close();}
        int previous=GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        try{
            var atlas=mc.getModelManager().getAtlas(TextureAtlas.LOCATION_BLOCKS);RenderSystem.bindTexture(atlas.getId());int full=GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D,0,GL11.GL_TEXTURE_WIDTH),fullHeight=GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D,0,GL11.GL_TEXTURE_HEIGHT),width=GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D,2,GL11.GL_TEXTURE_WIDTH),height=GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D,2,GL11.GL_TEXTURE_HEIGHT);need(width>0&&height>0,"actual GPU mip allocation");ByteBuffer pixels=BufferUtils.createByteBuffer(width*height*4);GL11.glGetTexImage(GL11.GL_TEXTURE_2D,2,GL11.GL_RGBA,GL11.GL_UNSIGNED_BYTE,pixels);int x0=Math.round(sprite.getU0()*full)>>2,y0=Math.round(sprite.getV0()*fullHeight)>>2;boolean visible=false;
            for(int y=0;y<(glass.height()>>2);y++)for(int x=0;x<(glass.width()>>2);x++){int alpha=pixels.get(((y0+y)*width+x0+x)*4+3)&255;need(alpha==(glass.byMipLevel[2].getPixelRGBA(x,y)>>>24),"exact GPU mip2 alpha sample");visible|=alpha>0;}need(visible,"faint reflection uploaded into actual GPU mip2");
        }finally{RenderSystem.bindTexture(previous);}
        need(models==1924&&sprites==80,"complete source inventory");System.out.println("[vandorlabs][door-resources] title PASS models="+models+" sprites="+sprites+" quads="+quads+" full content ratios; faint GPU mip2 positive/vanilla negative; no world/server or placed-door claim");done=true;mc.stop();
    }
}
