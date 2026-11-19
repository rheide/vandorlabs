package com.vandorlabs.neoforge.builders;
import java.util.List;
public final class NativeBuilderDesigns {
 public record Design(String id,int width,int height,int depth){public NativeDriveDesign drive(){return NativeDriveDesign.of(id);}public String block(){var drive=drive();return drive==null?id+"_core":drive.block;}}
 public static final List<Design> ALL;
 static {
  var designs=new java.util.ArrayList<>(List.of(new Design("synthesis_tower",3,5,3),new Design("assembly_press",4,3,3),new Design("enclosed_gantry",5,4,3)));
  for(var drive:NativeDriveDesign.SUPPORTED)designs.add(new Design(drive.id,drive.width,drive.height,drive.depth));
  ALL=List.copyOf(designs);
 }
 public static Design at(int index){return ALL.get(index);}public static Design find(String id){return ALL.stream().filter(d->d.id().equals(id)||d.block().equals(id)).findFirst().orElseThrow(()->new IllegalArgumentException("Unknown builder"));}
 private NativeBuilderDesigns(){}
}
