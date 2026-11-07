package com.vandorlabs.neoforge.glass;

import com.google.gson.*;
import com.vandorlabs.render.FlatGlassPolicy;
import java.nio.file.*;

/** Actual Forge multipart selections and authored atlas identities, not a second condition parser. */
public final class GlassAssetChecks {
 private static int checks;
 private static void need(boolean yes,String text){checks++;if(!yes)throw new AssertionError("Authored flat glass "+text);}
 private static String normalized(String source){String[] parts=source.split(":",2);return parts[0]+":"+(parts[1].startsWith("block/")||parts[1].startsWith("item/")?"":"block/")+parts[1];}
 public static void main(String[] args)throws Exception {int profiles=0,artwork=0,quads=0;try(var reader=Files.newBufferedReader(Path.of(args[0]))){for(String line;(line=reader.readLine())!=null;){var row=JsonParser.parseString(line).getAsJsonObject();var pose=new FlatGlassPolicy.Pose(row.get("rotated").getAsBoolean(),row.get("depth").getAsInt(),row.get("size").getAsInt());var selected=NativeFlatGlassAssets.frame(pose,row.get("mask").getAsInt());var expected=row.getAsJsonArray("parts");need(selected.size()==expected.size(),"actual predicate count");for(int i=0;i<selected.size();i++){var old=expected.get(i).getAsJsonObject();var part=selected.get(i);need(NativeFlatGlassAssets.model(part.model()).source().equals(normalized(old.get("source").getAsString()))&&part.y()==old.get("y").getAsInt()&&!old.get("uvlock").getAsBoolean(),"actual predicate part/model rotation");}profiles++;}}
  try(var reader=Files.newBufferedReader(Path.of(args[1]))){for(String line;(line=reader.readLine())!=null;){var row=JsonParser.parseString(line).getAsJsonObject();String source=normalized(row.get("source").getAsString());need(NativeFlatGlassAssets.models().stream().anyMatch(id->NativeFlatGlassAssets.model(id).source().equals(source)),"actual authored model "+source);for(var quad:row.getAsJsonArray("quads")){String texture=quad.getAsJsonObject().get("sprite").getAsString();need(NativeFlatGlassAssets.texture(texture)!=null,"actual full-content texture identity "+texture);quads++;}artwork++;}}
  need(profiles==4608&&artwork==400&&quads==8480&&NativeFlatGlassAssets.items().size()==304,"released coverage");System.out.println("[vandorlabs][flat-glass-assets] PASS selections="+profiles+" authoredModels="+artwork+" sourceQuads="+quads+" itemVariants=304 checks="+checks);
 }
}
