package com.vandorlabs.neoforge.client;

import com.google.gson.*;
import com.vandorlabs.animation.CompactAnimationDecoder;
import com.vandorlabs.neoforge.ModernFinishCatalog;
import java.awt.image.BufferedImage;
import java.nio.file.*;
import java.util.*;
import javax.imageio.ImageIO;

/** Independent legacy Java decoder versus generated raw strips, plus actual provider timing. */
public final class MaterialPlaybackChecks {
    private static int checks;
    private static void require(boolean value,String message){checks++;if(!value)throw new AssertionError(message);}
    private static Path source(Path root,String name){for(String dir:List.of("texture-packs/additional","texture-packs/default","src/main/resources")){Path path=root.resolve(dir+"/assets/vandorlabs/textures/blocks/"+name);if(Files.isRegularFile(path))return path;}throw new AssertionError("Missing source "+name);}
    private static void pixels(BufferedImage a,BufferedImage b){require(a.getWidth()==b.getWidth()&&a.getHeight()==b.getHeight(),"Raw image dimensions");require(Arrays.equals(a.getRGB(0,0,a.getWidth(),a.getHeight(),null,0,a.getWidth()),b.getRGB(0,0,b.getWidth(),b.getHeight(),null,0,b.getWidth())),"Exact legacy decoded pixels");}
    private static Path output(Path assets,String texture){return assets.resolve(texture.substring(texture.indexOf(':')+1));}
    public static void main(String[] args)throws Exception{
        Path root=Path.of(args[0]),assets=Path.of(args[1]);Map<String,String> sources=new HashMap<>();for(var row:JsonParser.parseString(Files.readString(root.resolve("generated-resources/assets/vandorlabs/data/unified_textures.json"))).getAsJsonArray()){var entry=row.getAsJsonObject();sources.put(entry.get("id").getAsString(),entry.get("source").getAsString());}
        var catalog=JsonParser.parseString(Files.readString(assets.resolve("data/housing_finishes.json"))).getAsJsonArray();Set<String> unique=new HashSet<>();int descriptors=0,animated=0;
        for(var row:catalog){var entry=row.getAsJsonObject();if(!entry.has("playback"))continue;descriptors++;var playback=new Gson().fromJson(entry.get("playback"),ModernFinishCatalog.Playback.class);var material=new NativeMaterialPlayback.Material(playback);require(material.texture(0).toString().equals(playback.offTexture())&&material.texture(1).toString().equals(playback.staticTexture()),"Off/static direct textures");String key=playback.staticTexture().substring("vandorlabs:textures/playback/".length()).replaceFirst("_static\\.png$","");if(!unique.add(key))continue;String source=sources.get(key);require(source!=null,"Canonical source metadata");BufferedImage base=ImageIO.read(source(root,source+".png").toFile());pixels(base,ImageIO.read(output(assets,playback.staticTexture()).toFile()));require(material.aspect()==(double)base.getWidth()/base.getHeight(),"Raw source aspect before atlas padding/fallback");
            if(playback.frameCount()>1){animated++;String stem=source.replaceFirst("_static$","");try(var encoded=Files.newInputStream(source(root,stem+"_animated.anim"))){var decoded=CompactAnimationDecoder.decode(base,encoded);require(decoded.frameCount==playback.frameCount(),"Actual legacy frame count");pixels(decoded.strip,ImageIO.read(output(assets,playback.animationTexture()).toFile()));}}
            for(int speed=0;speed<3;speed++)for(int mode=0;mode<3;mode++)for(long tick:new long[]{0,4,5,9,10,19,20,59,60,119,120,Long.MAX_VALUE}){int frame=material.frame(mode,speed,tick),expected=mode==2?(int)(tick/new int[]{20,10,5}[speed]%playback.frameCount()):0;require(frame==expected,"Mode/speed frame boundary");require(material.top(mode,frame)==(mode==2?(double)frame/playback.frameCount():0)&&material.bottom(mode,frame)==(mode==2?(double)(frame+1)/playback.frameCount():1),"Per-frame strip coordinates; wholeUV Off/static");}
        }
        require(descriptors==389&&animated>0,"Current source eligibility, not menu filter");long unit=50000000L;require(NativeMaterialPlayback.tick(0,false)==0,"Clock initial");require(NativeMaterialPlayback.tick(10*unit,false)==10,"Clock progresses");require(NativeMaterialPlayback.tick(100*unit,true)==10&&NativeMaterialPlayback.tick(200*unit,true)==10,"Pause freezes");require(NativeMaterialPlayback.tick(300*unit,false)==10&&NativeMaterialPlayback.tick(310*unit,false)==20,"Resume no jump");require(NativeMaterialPlayback.tick(309*unit,false)==20,"Backward monotonic sample cannot rewind");
        System.out.println("Material playback PASS descriptors="+descriptors+" unique="+unique.size()+" animated="+animated+" checks="+checks+" exact Java-decoder pixels pause/frame boundaries; renderer activation separate");
    }
}
