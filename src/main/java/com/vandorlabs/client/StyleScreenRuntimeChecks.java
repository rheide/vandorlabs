package com.vandorlabs.client;

import com.google.gson.JsonObject;
import com.vandorlabs.blocks.ModBlocks;
import com.vandorlabs.tiles.ScreenHousingTextures;
import com.vandorlabs.tiles.TileEntityAnimatedScreenSelector;
import java.awt.image.BufferedImage;
import java.io.File;
import java.lang.reflect.*;
import java.util.*;
import javax.imageio.ImageIO;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.texture.*;
import net.minecraft.client.resources.IResourceManager;
import net.minecraft.client.shader.Framebuffer;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.opengl.GL11;

/** Exact runtime decoder, atlas and rendered-phase coverage for the display style expansions. */
final class StyleScreenRuntimeChecks {
    static void run(Minecraft mc,File output,String style)throws Exception{
        String prefix=style.toLowerCase(java.util.Locale.ROOT)+"27_";
        Method decode=TEAnimatedScreenSelector.class.getDeclaredMethod("decodeCompactAnimation",IResourceManager.class,String.class);decode.setAccessible(true);
        for(boolean half:new boolean[]{false,true}){
            List<BufferedImage> strips=new ArrayList<>();int height=half?64:128;
            for(int choice=0;choice<ScreenHousingTextures.IDS.length;choice++){
                JsonObject entry=ScreenHousingTextures.entry(choice);if(entry==null||!entry.get("id").getAsString().startsWith("screen_"+prefix))continue;
                String source=entry.get("source").getAsString();if(source.startsWith("console_inputs/")!=half)continue;
                String root=source.replaceFirst("_static$",""),id=root.replaceFirst("^console_inputs/","");
                require(ScreenHousingTextures.visible(choice)&&ScreenHousingTextures.menuCategory(choice).equals("Displays/"+style),"visible "+style+" folder "+id);
                if(half)require(Arrays.asList(TileEntityAnimatedScreenSelector.INPUT_PANELS).contains(id)&&TileEntityAnimatedScreenSelector.getInputFrameCount(id)==6,"native half sequence "+id);
                else require(ModBlocks.DISPLAY_SCREEN_IDS.contains(id),"native full sequence "+id);
                Object decoded=decode.invoke(null,mc.getResourceManager(),"textures/blocks/"+root);Field image=decoded.getClass().getDeclaredField("image"),count=decoded.getClass().getDeclaredField("frameCount");image.setAccessible(true);count.setAccessible(true);
                BufferedImage strip=(BufferedImage)image.get(decoded);require(count.getInt(decoded)==6&&strip.getWidth()==128&&strip.getHeight()==height*6,"native decoder dimensions "+id);
                Set<Integer> phases=new HashSet<>();for(int phase=0;phase<6;phase++){
                    int[] pixels=strip.getRGB(0,phase*height,128,height,null,0,128);for(int pixel:pixels)require(pixel>>>24==255,"opaque authored alpha "+id);phases.add(Arrays.hashCode(pixels));
                }
                require(phases.size()==6,"all six phases animate "+id);
                TextureAtlasSprite sprite=mc.getTextureMapBlocks().getAtlasSprite(ScreenHousingTextures.fullTexture(choice));require(sprite!=mc.getTextureMapBlocks().getMissingSprite(),"static picker sprite "+id);
                if(half)require(mc.getTextureMapBlocks().getAtlasSprite("vandorlabs:blocks/"+root+"_off")!=mc.getTextureMapBlocks().getMissingSprite(),"OFF sprite "+id);
                strips.add(strip);
            }
            require(strips.size()==21,"21 new layouts per height, got "+strips.size());BufferedImage rendered=render(mc,strips,height,style);
            for(int row=0;row<strips.size();row++)for(int phase=1;phase<6;phase++){
                boolean changed=false;for(int y=0;y<height;y++)for(int x=0;x<128;x++)if(rendered.getRGB(x,row*height+y)!=rendered.getRGB(phase*128+x,row*height+y))changed=true;
                require(changed,"actual rendered phase differs height="+height+" row="+row+" phase="+phase);
            }
            ImageIO.write(rendered,"png",new File(output,"shot_"+style.toLowerCase(java.util.Locale.ROOT)+"_screens_"+(half?"half":"full")+"_all_six_frames.png"));
        }
        System.out.println("[vandorlabs][reprolab] "+style.toLowerCase(java.util.Locale.ROOT)+"-screen-animations PASS layouts=42 frames=252 native decoder opaque-alpha static-OFF-atlas actual-rendered-phases retained-original-"+style);
    }
    static BufferedImage render(Minecraft mc,List<BufferedImage> strips,int height,String style){
        int frameWidth=strips.get(0).getWidth(),width=frameWidth*6,totalHeight=height*strips.size();Framebuffer frame=new Framebuffer(width,totalHeight,true);List<ResourceLocation> temporary=new ArrayList<>();GlStateManager.matrixMode(GL11.GL_PROJECTION);GlStateManager.pushMatrix();GlStateManager.loadIdentity();GlStateManager.ortho(0,width,totalHeight,0,-10,10);GlStateManager.matrixMode(GL11.GL_MODELVIEW);GlStateManager.pushMatrix();
        try(TurntableRenderState saved=new TurntableRenderState()){
            frame.bindFramebuffer(true);GlStateManager.loadIdentity();GlStateManager.clearColor(.125F,.188F,.25F,1);GlStateManager.clear(16640);GlStateManager.disableDepth();GlStateManager.disableCull();GlStateManager.disableLighting();GlStateManager.disableFog();GlStateManager.setActiveTexture(OpenGlHelper.defaultTexUnit);GlStateManager.enableTexture2D();GlStateManager.disableBlend();GlStateManager.color(1,1,1,1);mc.entityRenderer.disableLightmap();Tessellator.getInstance().getBuffer().setTranslation(0,0,0);
            for(int row=0;row<strips.size();row++)for(int phase=0;phase<6;phase++){
                BufferedImage image=strips.get(row).getSubimage(0,phase*height,frameWidth,height);ResourceLocation texture=mc.getTextureManager().getDynamicTextureLocation(style.toLowerCase(java.util.Locale.ROOT)+"_screen_phase",new DynamicTexture(image));temporary.add(texture);mc.getTextureManager().bindTexture(texture);Gui.drawModalRectWithCustomSizedTexture(phase*frameWidth,row*height,0,0,frameWidth,height,frameWidth,height);
            }
            GL11.glFinish();return net.minecraft.util.ScreenShotHelper.createScreenshot(width,totalHeight,frame);
        }finally{for(ResourceLocation texture:temporary)mc.getTextureManager().deleteTexture(texture);GlStateManager.popMatrix();GlStateManager.matrixMode(GL11.GL_PROJECTION);GlStateManager.popMatrix();GlStateManager.matrixMode(GL11.GL_MODELVIEW);frame.deleteFramebuffer();mc.getFramebuffer().bindFramebuffer(true);}
    }
    private static void require(boolean value,String message){if(!value)throw new IllegalStateException("Style screens: "+message);}
}
