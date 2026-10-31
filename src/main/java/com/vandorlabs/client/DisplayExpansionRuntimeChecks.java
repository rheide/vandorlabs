package com.vandorlabs.client;

import com.google.gson.JsonObject;
import com.vandorlabs.tiles.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.lang.reflect.*;
import java.util.Collections;
import javax.imageio.ImageIO;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.IResourceManager;

/** Actual decoder/atlas/GL coverage for all standard and explicitly sized Large additions. */
final class DisplayExpansionRuntimeChecks {
    static boolean expansion(JsonObject entry){return entry!=null&&(entry.get("id").getAsString().startsWith("screen_expansion27_")||entry.get("id").getAsString().startsWith("screen_large27_")||entry.get("id").getAsString().startsWith("screen_door27_"));}
    static void run(Minecraft mc,File output)throws Exception{
        Method decode=TEAnimatedScreenSelector.class.getDeclaredMethod("decodeCompactAnimation",IResourceManager.class,String.class);decode.setAccessible(true);int layouts=0,large=0;
        for(int choice=0;choice<ScreenHousingTextures.IDS.length;choice++){
            JsonObject entry=ScreenHousingTextures.entry(choice);if(!expansion(entry))continue;layouts++;
            int width=entry.has("pixelWidth")?entry.get("pixelWidth").getAsInt():128;String source=entry.get("source").getAsString(),root=source.replaceFirst("_static$","");boolean half=source.startsWith("console_inputs/");int height=half?width/2:width;
            if(width>128)large++;
            require(ScreenHousingTextures.visible(choice),"new choice visible "+source);TextureAtlasSprite sprite=mc.getTextureMapBlocks().getAtlasSprite(ScreenHousingTextures.fullTexture(choice));
            require(sprite!=mc.getTextureMapBlocks().getMissingSprite()&&UnifiedTextureSprites.contentWidth(sprite)==width&&UnifiedTextureSprites.contentHeight(sprite)==height,"native static atlas resolution "+source);
            if(half){TextureAtlasSprite off=mc.getTextureMapBlocks().getAtlasSprite("vandorlabs:blocks/"+root+"_off");require(off!=mc.getTextureMapBlocks().getMissingSprite()&&UnifiedTextureSprites.contentWidth(off)==width&&UnifiedTextureSprites.contentHeight(off)==height,"native OFF resolution "+source);}
            Object decoded=decode.invoke(null,mc.getResourceManager(),"textures/blocks/"+root);Field image=decoded.getClass().getDeclaredField("image"),count=decoded.getClass().getDeclaredField("frameCount");image.setAccessible(true);count.setAccessible(true);BufferedImage strip=(BufferedImage)image.get(decoded);
            require(count.getInt(decoded)==6&&strip.getWidth()==width&&strip.getHeight()==height*6,"native six-frame decode "+source);
            BufferedImage rendered=StyleScreenRuntimeChecks.render(mc,Collections.singletonList(strip),height,"display_expansion");
            for(int phase=0;phase<6;phase++)for(int y=0;y<height;y++)for(int x=0;x<width;x++)if(rendered.getRGB(phase*width+x,y)!=strip.getRGB(x,phase*height+y))require(false,"actual GL pixel parity "+source+" phase="+phase);
            ImageIO.write(rendered,"png",new File(output,"shot_expansion_"+root.replace('/','_')+"_all_six_frames.png"));
        }
        String retired="engineering_screen",retained=ScreenHousingTextures.canonicalScreen(retired);
        require(!retired.equals(retained)&&ScreenTextureLocations.stationary(retired).equals(ScreenTextureLocations.stationary(retained)),"Engineering static fallback replacement");
        Method animation=TEAnimatedScreenSelector.class.getDeclaredMethod("animData",String.class);animation.setAccessible(true);require(animation.invoke(null,retired)==animation.invoke(null,retained),"Engineering animation replacement shares cache");
        Method crop=TEAnimatedScreenSelector.class.getDeclaredMethod("engineeringChoice",int.class);crop.setAccessible(true);require(!(Boolean)crop.invoke(null,ScreenHousingTextures.screenIndex("engineering_screen_static")),"retired Engineering choice crops retained pixels");
        require(layouts==120&&large==12,"120 additions including twelve Large choices");System.out.println("[vandorlabs][reprolab] display-expansion-animations PASS layouts=120 frames=720 Large=12 native-128-256-512 decoder static-OFF-atlas exact-GL-pixels");
    }
    private static void require(boolean value,String message){if(!value)throw new IllegalStateException("Display expansion: "+message);}
}
