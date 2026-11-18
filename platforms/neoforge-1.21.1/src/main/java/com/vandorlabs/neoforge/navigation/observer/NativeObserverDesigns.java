package com.vandorlabs.neoforge.navigation.observer;
import java.util.*;
public final class NativeObserverDesigns {
 public record Design(String id,int width,int height,int depth,int maximum){public String block(){return id+"_core";}}
 public static final List<Design> ALL=List.of(new Design("standalone_dish",3,3,3,com.vandorlabs.navigation.NavigationRange.SURVEY_BASE),new Design("standalone_antenna",2,4,2,com.vandorlabs.navigation.NavigationRange.SENTINEL_BASE));
 public static Design find(String id){return ALL.stream().filter(d->d.id().equals(id)).findFirst().orElseThrow(()->new IllegalArgumentException("Unknown observer"));}
 public static Design scaled(String id,int width){var base=find(id);int checked=com.vandorlabs.navigation.ObserverScale.width(id,width);return new Design(id,checked,com.vandorlabs.navigation.ObserverScale.height(id,checked),checked,base.maximum());}
 private NativeObserverDesigns(){}
}