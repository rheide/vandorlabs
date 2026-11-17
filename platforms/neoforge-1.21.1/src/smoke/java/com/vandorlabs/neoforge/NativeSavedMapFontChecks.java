package com.vandorlabs.neoforge;
import net.minecraft.client.Minecraft;
import java.util.Arrays;
/** Real loaded minecraft:alt glyph output and fitting, separate from codec flag checks. */
public final class NativeSavedMapFontChecks {
 public static void run(){
  var font=Minecraft.getInstance().font;var canvas=new NativeOverlayCanvas(font);String text="Hidden ancient words abcdefghijklmnopqrstuvwxyz";
  var plain=canvas.build(t->t.fitted(text,8,23,304,0xe2edf5));var alt=canvas.build(t->t.enchantingFitted(text,8,23,304,0xe2edf5));
  boolean different=plain.layers().size()!=alt.layers().size();if(!different)for(int i=0;i<plain.layers().size();i++)different|=!plain.layers().get(i).type().equals(alt.layers().get(i).type())||!Arrays.equals(plain.layers().get(i).words(),alt.layers().get(i).words());need(different,"loaded minecraft:alt emits different glyphs");int vertices=0;for(var layer:alt.layers())for(int i=0;i<layer.words().length;i+=7){float x=Float.intBitsToFloat(layer.words()[i]),y=Float.intBitsToFloat(layer.words()[i+1]);need(Float.isFinite(x)&&Float.isFinite(y)&&x>=7&&x<=314&&y>=22&&y<=33,"alt glyph bounds fit journal body");vertices++;}need(vertices>0,"enchanting font emits visible glyph vertices");
  var repeat=canvas.build(t->t.enchantingFitted(text,8,23,304,0xe2edf5));need(repeat.layers().size()==alt.layers().size(),"alt layers deterministic");for(int i=0;i<alt.layers().size();i++)need(Arrays.equals(repeat.layers().get(i).words(),alt.layers().get(i).words()),"alt glyphs deterministic");System.out.println("[vandorlabs][saved-map-font] PASS actual minecraft:alt glyphs/fittedBody vertices="+vertices);
 }
 private static void need(boolean value,String message){if(!value)throw new AssertionError(message);}
 private NativeSavedMapFontChecks(){}
}
