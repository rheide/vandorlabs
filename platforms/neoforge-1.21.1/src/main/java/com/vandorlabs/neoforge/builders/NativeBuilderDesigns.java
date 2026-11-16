package com.vandorlabs.neoforge.builders;
import java.util.List;
public final class NativeBuilderDesigns {
 public record Design(String id,int width,int height,int depth){public String block(){return id+"_core";}}
 public static final List<Design> ALL=List.of(new Design("synthesis_tower",3,5,3),new Design("assembly_press",4,3,3),new Design("enclosed_gantry",5,4,3));
 public static Design at(int index){return ALL.get(index);}public static Design find(String id){return ALL.stream().filter(d->d.id().equals(id)||d.block().equals(id)).findFirst().orElseThrow(()->new IllegalArgumentException("Unknown builder"));}
 private NativeBuilderDesigns(){}
}
