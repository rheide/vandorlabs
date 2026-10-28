package com.vandorlabs.client;

import com.google.gson.*;
import com.vandorlabs.tiles.*;
import java.awt.image.BufferedImage;
import java.io.*;
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

/** Actual packaged company animations, menu groups and layered framebuffer compositions. */
final class CompanyScreenLayerRuntimeChecks {
    static void run(Minecraft mc,File output)throws Exception{
        Method decode=TEAnimatedScreenSelector.class.getDeclaredMethod("decodeCompactAnimation",IResourceManager.class,String.class);decode.setAccessible(true);
        Map<String,BufferedImage> strips=new HashMap<>();int borders=0,displays=0;
        for(int choice=0;choice<ScreenHousingTextures.IDS.length;choice++){
            JsonObject entry=ScreenHousingTextures.entry(choice);if(entry==null)continue;String id=entry.get("id").getAsString();
            if(id.startsWith("frame_company_")){borders++;require(ScreenHousingTextures.menuCategory(choice).startsWith("Frame/Company/"),"frame company subgroup");}
            if(!id.startsWith("screen_company_"))continue;displays++;
            String source=entry.get("source").getAsString(),root=source.substring(0,source.length()-7);boolean half=source.startsWith("console_inputs/");
            Object decoded=decode.invoke(null,mc.getResourceManager(),"textures/blocks/"+root);Field imageField=decoded.getClass().getDeclaredField("image"),countField=decoded.getClass().getDeclaredField("frameCount");imageField.setAccessible(true);countField.setAccessible(true);
            BufferedImage strip=(BufferedImage)imageField.get(decoded);int height=half?64:128;require(countField.getInt(decoded)==6&&strip.getWidth()==128&&strip.getHeight()==height*6,"six native-sized decoded frames "+id);
            try(InputStream resource=mc.getResourceManager().getResource(new ResourceLocation("vandorlabs","textures/blocks/"+root+"_animated.png.mcmeta")).getInputStream()){
                JsonObject animation=new JsonParser().parse(new InputStreamReader(resource,"UTF-8")).getAsJsonObject().getAsJsonObject("animation");require(animation.get("frametime").getAsInt()==4&&animation.getAsJsonArray("frames").size()==6,"four-tick animation timing "+id);
            }
            boolean transparent=false;int changed=0;
            for(int y=0;y<height;y++)for(int x=0;x<128;x++){int pixel=strip.getRGB(x,y),alpha=pixel>>>24;transparent|=alpha<255;for(int frame=1;frame<6;frame++){int next=strip.getRGB(x,y+frame*height);require(next>>>24==alpha,"straight alpha retained in every decoded frame "+id);if(next!=pixel)changed++;}}
            require(transparent&&changed>0,"transparent animated layers "+id);strips.put(root,strip);
        }
        require(borders==12&&displays==72,"all twelve borders and seventy-two display sequences packaged");
        pickerGroups(HousingTextureList.forFrames(0,0,260,-1,8),"Frame/Company/",2,6);
        for(boolean input:new boolean[]{false,true}){ScreenTextureList picker=new ScreenTextureList(0,0,260,8,"",new String[0],new String[0],input);Field list=ScreenTextureList.class.getDeclaredField("list");list.setAccessible(true);pickerGroups((HousingTextureList)list.get(picker),"Screens/Company/",6,6);}
        BufferedImage composition=render(mc,strips);ImageIO.write(composition,"png",new File(output,"shot_company_layers_full_half_frames0_3.png"));
        require(difference(composition,0,128,0,128)>40&&difference(composition,256,384,0,128)>40,"full display frames visibly animate under company borders");
        require(difference(composition,0,128,224,64)>10&&difference(composition,256,384,224,64)>10,"half display frames visibly animate under correctly cropped borders");
        System.out.println("[vandorlabs][reprolab] company-screen-layers PASS borders=12 displays=72 frames=432 decoded-alpha-four-tick-timing six-company-picker-subgroups full-half-frame0-3-layered-framebuffer");
    }
    @SuppressWarnings("unchecked") private static void pickerGroups(HousingTextureList picker,String prefix,int each,int expected)throws Exception{Field options=HousingTextureList.class.getDeclaredField("options");options.setAccessible(true);Map<String,Integer> counts=new TreeMap<>();for(HousingTextureList.Option option:((Map<Integer,HousingTextureList.Option>)options.get(picker)).values())if(option.category.startsWith(prefix))counts.put(option.category,counts.getOrDefault(option.category,0)+1);require(counts.size()==expected,"six company subgroups "+counts);for(int count:counts.values())require(count==each,"company subgroup choice count "+counts);}
    private static BufferedImage render(Minecraft mc,Map<String,BufferedImage> strips){
        Framebuffer frame=new Framebuffer(512,384,true);List<ResourceLocation> temporary=new ArrayList<>();GlStateManager.matrixMode(GL11.GL_PROJECTION);GlStateManager.pushMatrix();GlStateManager.loadIdentity();GlStateManager.ortho(0,512,384,0,-10,10);GlStateManager.matrixMode(GL11.GL_MODELVIEW);GlStateManager.pushMatrix();
        try(TurntableRenderState saved=new TurntableRenderState()){
            frame.bindFramebuffer(true);GlStateManager.loadIdentity();GlStateManager.clearColor(.125F,.188F,.25F,1);GlStateManager.clear(16640);GlStateManager.disableDepth();GlStateManager.disableCull();GlStateManager.disableLighting();GlStateManager.disableFog();GlStateManager.setActiveTexture(OpenGlHelper.defaultTexUnit);GlStateManager.enableTexture2D();GlStateManager.enableBlend();GlStateManager.tryBlendFuncSeparate(GL11.GL_SRC_ALPHA,GL11.GL_ONE_MINUS_SRC_ALPHA,GL11.GL_ONE,GL11.GL_ZERO);GlStateManager.color(1,1,1,1);mc.entityRenderer.disableLightmap();
            String[] companies={"pinnacle","vandor"};
            for(int company=0;company<2;company++)for(boolean half:new boolean[]{false,true})for(int sample=0;sample<2;sample++){
                int h=half?64:128,x=company*256+sample*128,y=half?224:0;String root=(half?"console_inputs/":"")+"company_"+companies[company]+(half?"_half_radar":"_full_navigation");BufferedImage strip=strips.get(root),image=strip.getSubimage(0,sample*3*h,128,h);
                ResourceLocation dynamic=mc.getTextureManager().getDynamicTextureLocation("company_layer_check",new DynamicTexture(image));temporary.add(dynamic);mc.getTextureManager().bindTexture(dynamic);Gui.drawModalRectWithCustomSizedTexture(x,y,0,0,128,h,128,h);
                int choice=ScreenHousingTextures.screenIndex("frames/company/"+companies[company]+(half?"_half":"_full"));TextureAtlasSprite sprite=mc.getTextureMapBlocks().getAtlasSprite(ScreenHousingTextures.fullTexture(choice));require(choice>0&&sprite!=mc.getTextureMapBlocks().getMissingSprite(),"matching company border stitched");mc.getTextureManager().bindTexture(TextureMap.LOCATION_BLOCKS_TEXTURE);float maxV=sprite.getMinV()+(sprite.getMaxV()-sprite.getMinV())/(half?2:1);BufferBuilder b=Tessellator.getInstance().getBuffer();b.begin(GL11.GL_QUADS,net.minecraft.client.renderer.vertex.DefaultVertexFormats.POSITION_TEX);b.pos(x,y+h,0).tex(sprite.getMinU(),maxV).endVertex();b.pos(x+128,y+h,0).tex(sprite.getMaxU(),maxV).endVertex();b.pos(x+128,y,0).tex(sprite.getMaxU(),sprite.getMinV()).endVertex();b.pos(x,y,0).tex(sprite.getMinU(),sprite.getMinV()).endVertex();Tessellator.getInstance().draw();
            }
            GL11.glFinish();return net.minecraft.util.ScreenShotHelper.createScreenshot(512,384,frame);
        }finally{for(ResourceLocation texture:temporary)mc.getTextureManager().deleteTexture(texture);GlStateManager.popMatrix();GlStateManager.matrixMode(GL11.GL_PROJECTION);GlStateManager.popMatrix();GlStateManager.matrixMode(GL11.GL_MODELVIEW);frame.deleteFramebuffer();mc.getFramebuffer().bindFramebuffer(true);}
    }
    private static int difference(BufferedImage image,int left,int right,int top,int height){int changed=0;for(int y=top;y<top+height;y++)for(int x=0;x<128;x++)if(image.getRGB(left+x,y)!=image.getRGB(right+x,y))changed++;return changed;}
    private static void require(boolean value,String message){if(!value)throw new IllegalStateException("Company screen layers: "+message);}
}
