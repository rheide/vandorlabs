package com.vandorlabs.neoforge.screen;

import com.google.gson.*;
import com.vandorlabs.neoforge.ModernFinishCatalog;
import com.vandorlabs.animation.CompactAnimationDecoder;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.zip.GZIPInputStream;
import javax.imageio.ImageIO;

/** Actual source predicate/binding table plus independent Java image decoding. */
public final class ScreenMediaChecks {
 private static int checks;private static void need(boolean value,String message){checks++;if(!value)throw new AssertionError("Screen media: "+message);}
 private static Path source(Path root,String name){for(String directory:List.of("texture-packs/additional","texture-packs/default","src/main/resources")){var path=root.resolve(directory+"/assets/vandorlabs/"+name);if(Files.isRegularFile(path))return path;}return null;}
 private static java.awt.image.BufferedImage generated(Path cube,Path media,String texture)throws Exception{String name=texture.substring("vandorlabs:".length());Path path=media.resolve(name);if(!Files.isRegularFile(path))path=cube.resolve(name);return ImageIO.read(path.toFile());}
 private static void pixels(java.awt.image.BufferedImage a,java.awt.image.BufferedImage b){need(a.getWidth()==b.getWidth()&&a.getHeight()==b.getHeight(),"pixel dimensions");need(Arrays.equals(a.getRGB(0,0,a.getWidth(),a.getHeight(),null,0,a.getWidth()),b.getRGB(0,0,b.getWidth(),b.getHeight(),null,0,b.getWidth())),"exact Source RGBA");}
 public static void main(String[] args)throws Exception{Path root=Path.of(args[0]),cube=Path.of(args[1]),media=Path.of(args[2]);JsonObject reference;try(var stream=new GZIPInputStream(Files.newInputStream(root.resolve("testclient/fixtures/screen-media-reference.json.gz")));var reader=new InputStreamReader(stream,java.nio.charset.StandardCharsets.UTF_8)){reference=JsonParser.parseReader(reader).getAsJsonObject();}int materials=0,inputs=0,animations=0;Set<String> seen=new HashSet<>();
  for(var value:reference.getAsJsonArray("materials")){var row=value.getAsJsonObject();int choice=row.get("choice").getAsInt();need(NativeScreenMediaCatalog.flags(choice)==row.get("flags").getAsInt(),"actual Source material flag "+choice);need((ModernFinishCatalog.option(choice).playback()!=null)==((row.get("flags").getAsInt()&1)!=0),"custom-material playback keeps exact Source eligibility "+choice);materials++;}
  for(var value:reference.getAsJsonArray("inputs")){var row=value.getAsJsonObject();var input=NativeScreenMediaCatalog.input(row.get("id").getAsString());need(input.id().equals(row.get("id").getAsString())&&input.canonical().equals(row.get("canonical").getAsString()),"Source input aliases");need(input.declaredFrames()==row.get("frames").getAsInt()&&input.declaredFrames()==NativeScreenCatalog.INSTANCE.inputFrames(input.id()),"Source animation eligibility");need(input.crop()==row.get("crop").getAsDouble()&&input.sourceStationary().equals(row.get("stationary").getAsString())&&input.sourceOff().equals(row.get("off").getAsString()),"Source input crop/bindings");inputs++;if(!seen.add(input.canonical()))continue;
   var original=ImageIO.read(source(root,input.sourceStationary().substring("vandorlabs:".length())).toFile());need(input.aspect()==(double)original.getWidth()/original.getHeight(),"source input aspect");pixels(original,generated(cube,media,input.staticTexture()));pixels(ImageIO.read(source(root,input.sourceOff().substring("vandorlabs:".length())).toFile()),generated(cube,media,input.offTexture()));
   if(input.frames()>1){animations++;String name=input.sourceStationary().substring("vandorlabs:".length()).replaceFirst("_static\\.png$","");var layered=source(root,name+"_animated.png");if(layered!=null)pixels(ImageIO.read(layered.toFile()),generated(cube,media,input.animationTexture()));else try(var packed=Files.newInputStream(source(root,name+"_animated.anim"))){var decoded=CompactAnimationDecoder.decode(original,packed);need(decoded.frameCount==input.frames(),"Source compact input frame count");pixels(decoded.strip,generated(cube,media,input.animationTexture()));}}
  }
  need(materials==893&&inputs==190&&seen.size()==139,"source media coverage");System.out.println("Native Screen media PASS materials="+materials+" inputs="+inputs+" canonical="+seen.size()+" animations="+animations+" checks="+checks+" actual predicates/bindings/straight-alpha PNG and compact Java-decoder pixels");
 }
}
