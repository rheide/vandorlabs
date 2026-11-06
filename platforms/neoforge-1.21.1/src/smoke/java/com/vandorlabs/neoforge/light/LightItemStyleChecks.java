package com.vandorlabs.neoforge.light;
import com.google.gson.*;
import java.nio.file.*;
import net.minecraft.nbt.TagParser;
/** Actual released mesh-definition outputs, independent of the native item wrapper. */
public final class LightItemStyleChecks {
 public static void main(String[] args)throws Exception {
  var rows=JsonParser.parseString(Files.readString(Paths.get(args[0]))).getAsJsonArray();int count=0;
  for(var element:rows){var row=element.getAsJsonObject();String text=row.get("tag").getAsString();var style=NativeLightItemStyle.read(text.equals("absent")?null:TagParser.parseTag(text));if(style.modelKey()!=row.get("choice").getAsInt())throw new AssertionError("Released item selection "+row+" native="+style);count++;}
  if(count!=1566)throw new AssertionError("Missing actual item style profiles "+count);
  System.out.println("Native Light item style PASS actual Source selectors="+count+" typed defaults/manual-on/configured-level; signals do not change inventory artwork");
 }
}
