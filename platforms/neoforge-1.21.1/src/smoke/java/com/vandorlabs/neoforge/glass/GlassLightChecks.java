package com.vandorlabs.neoforge.glass;

import com.google.gson.*;
import java.nio.file.*;
import net.minecraft.nbt.TagParser;

/** Exact Source light helper and placed-block intrinsic light callbacks, including mismatched legacy bare flags. */
public final class GlassLightChecks {
 public static void main(String[] args)throws Exception {int profiles=0,oldRuleMismatches=0;try(var reader=Files.newBufferedReader(Path.of(args[0]))){for(String line;(line=reader.readLine())!=null;){var row=JsonParser.parseString(line).getAsJsonObject();var settings=NativeGlassSettings.read(TagParser.parseTag(row.get("tag").getAsString()));if(settings.frameLight()!=row.get("frameLight").getAsInt()||settings.intrinsicFrameLight()!=row.get("intrinsic").getAsInt()||settings.paneBlockLight(112)!=row.get("paneBlock").getAsInt())throw new AssertionError("Glass light "+row);boolean oldGlow=false;var f=settings.frames();for(int choice:new int[]{f.front,f.interior,f.exteriorChoice()})if(choice>=0){var frame=com.vandorlabs.neoforge.ModernFinishCatalog.option(choice).frame();oldGlow|=frame!=null&&frame.light()>0;}if(oldGlow!=(settings.frameLight()>0))oldRuleMismatches++;profiles++;}}if(oldRuleMismatches==0)throw new AssertionError("Missing FrameBare light negative");if(profiles!=3408)throw new AssertionError("Source light coverage "+profiles);System.out.println("[vandorlabs][flat-glass-light] PASS profiles="+profiles+" frame/intrinsic/opaque-insert old-rule-mismatches="+oldRuleMismatches);}
}
