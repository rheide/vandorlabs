package com.vandorlabs.neoforge;
import com.google.gson.*;
import java.nio.file.*;
import javax.imageio.ImageIO;
public final class NativeTrapdoorBorderChecks {
 public static void main(String[] args)throws Exception{
  int profiles=0,bordered=0;try(var lines=Files.lines(Path.of(args[0]))){for(String line:(Iterable<String>)lines::iterator){var row=JsonParser.parseString(line).getAsJsonObject();int choice=row.get("choice").getAsInt();var entry=ModernFinishCatalog.option(choice);need(entry.frame()!=null,"Frame metadata "+choice);need(entry.frame().border()==row.get("border").getAsBoolean(),"border flag "+choice);need(entry.frame().aspect()==row.get("aspect").getAsDouble(),"aspect "+choice);NativeTrapdoorFrameBorders.Borders result;
   if(entry.frame().border()){var id=net.minecraft.resources.ResourceLocation.parse(entry.texture());try(var stream=NativeTrapdoorBorderChecks.class.getResourceAsStream("/assets/"+id.getNamespace()+"/textures/"+id.getPath()+".png")){need(stream!=null,"actual generated frame resource");result=NativeTrapdoorFrameBorders.read(ImageIO.read(stream),entry.frame().aspect());}bordered++;}
   else result=new NativeTrapdoorFrameBorders.Borders(1D/16,1D/16,1D/16,1D/16);
   double[] actual=result.fractions();for(int i=0;i<4;i++)need(Double.doubleToLongBits(actual[i])==Double.doubleToLongBits(row.getAsJsonArray("borders").get(i).getAsDouble()),"released border fraction choice="+choice+" edge="+i);profiles++;
  }}need(profiles==70&&bordered==62,"all Frame paths");System.out.println("[vandorlabs][trapdoor-borders] CPU PASS actual source70choices/62bordered/8solid exact pixels/aspects/resource ratios");
 }
 private static void need(boolean value,String message){if(!value)throw new AssertionError(message);}
}
