package com.vandorlabs.neoforge.screen;

import com.vandorlabs.neoforge.ModernFinishCatalog;
import com.vandorlabs.screen.*;
import java.util.*;

/** World-free choices for the released role branches. Cache only this immutable projection, never a host. */
public final class NativeScreenRenderPlan {
 public enum Binding {ATLAS,DISPLAY,INPUT,OFF,ROWS,CHIP,NONE}
 public enum Light {KEEP,OWNER,FULL}
 public record Fragment(Binding binding,int material,int mode,int speed,double crop,int half,Light light,NativeScreenMediaCatalog.Input input){public Fragment{Objects.requireNonNull(binding);Objects.requireNonNull(light);if(half<0||half>2)throw new IllegalArgumentException("Screen half");}}
 public record Surface(ScreenRenderGeometry.Surface geometry,List<Fragment> fragments,boolean frameBefore){public Surface{Objects.requireNonNull(geometry);fragments=List.copyOf(fragments);}}
 public final boolean chip;public final NativeScreenEntity.Visual visual;public final ScreenFamily.State state;public final ScreenRenderGeometry geometry;public final List<Surface> surfaces;
 private NativeScreenRenderPlan(NativeScreenEntity.Visual visual,ScreenFamily.State state,ScreenRenderGeometry geometry,List<Surface> surfaces){this.visual=visual;this.state=state;this.chip=selectedChip(visual);this.geometry=geometry;this.surfaces=List.copyOf(surfaces);}
 public static boolean selectedChip(NativeScreenEntity.Visual visual){int slot=visual.family()==ScreenFamily.HALF_CONSOLE?1:0;return !visual.controls().get(slot).enabled()&&(slot==0?visual.settings().primarySurface():visual.settings().secondarySurface())==-4;}
 private static boolean standardMain(ScreenFamily family,int slot){return slot==0&&(family==ScreenFamily.SCREEN||family==ScreenFamily.CONSOLE||family==ScreenFamily.DIAGONAL);}
 private static double crop(int material){int flags=NativeScreenMediaCatalog.flags(material);return (flags&NativeScreenMediaCatalog.ENGINEERING_CROP)!=0?1D/16:(flags&NativeScreenMediaCatalog.UNPAIRED_INPUT_CROP)!=0?1D/32:0;}
 private static Binding display(int material,int mode){if(mode==0)return Binding.OFF;var entry=ModernFinishCatalog.entry(material);return mode==2&&(NativeScreenMediaCatalog.flags(material)&NativeScreenMediaCatalog.SCREENS)!=0&&entry!=null&&entry.playback()!=null&&entry.playback().animationTexture()!=null?Binding.DISPLAY:Binding.ATLAS;}
 private static Light mainLight(ScreenAppearance appearance){return appearance.effectiveMode==0?Light.OWNER:Light.FULL;}
 public static NativeScreenRenderPlan of(NativeScreenEntity.Visual visual,ScreenFamily.State state){
  Objects.requireNonNull(visual);Objects.requireNonNull(state);var family=visual.family();var settings=visual.settings();var scalar=settings.scalar();var appearance=visual.appearance();var catalog=NativeScreenCatalog.INSTANCE;var geometry=ScreenRenderGeometry.of(family,state,scalar.smallInput,scalar.wallPosition,settings.ceilingMounted(),settings.ceilingPosition(),selectedChip(visual));List<Surface> surfaces=new ArrayList<>(2);
  for(var surface:geometry.surfaces){int slot=surface.slot,raw=appearance.surface(slot),mode=appearance.playback(slot),speed=appearance.speed(slot);boolean main=standardMain(family,slot),early=main||family==ScreenFamily.HALF_DIAGONAL,rows=visual.controls().get(slot).enabled();List<Fragment> fragments=new ArrayList<>(2);boolean before=family==ScreenFamily.HALF_DIAGONAL&&(rows||raw==-4);
   if(early&&rows&&raw!=-2){fragments.add(new Fragment(Binding.NONE,-1,0,0,0,0,Light.KEEP,null));}
   else if(raw==-2){fragments.add(new Fragment(Binding.ROWS,-2,mode,speed,0,0,Light.FULL,null));}
   else if(raw==-4){fragments.add(new Fragment(Binding.CHIP,-4,mode,speed,0,0,Light.KEEP,null));}
   else if(raw < -1){fragments.add(new Fragment(Binding.NONE,raw,0,0,0,0,Light.KEEP,null));}
   else if(surface.split&&appearance.split){
    int fallback=raw>=0?raw:catalog.screenArtwork(scalar.selectedScreen);for(int half=0;half<2;half++){int material=half==0?visual.frames().splitTop:visual.frames().splitBottom;if(material<0)material=fallback;fragments.add(new Fragment(display(material,appearance.effectiveMode),material,appearance.effectiveMode,appearance.animationSpeed,crop(material),half+1,main?mainLight(appearance):Light.KEEP,null));}
   }else if(raw>=0){
    boolean screens=(NativeScreenMediaCatalog.flags(raw)&NativeScreenMediaCatalog.SCREENS)!=0;Binding binding;Light light;
    if(screens){binding=display(raw,mode);light=Light.KEEP;}
    else if(slot==family.activationSlot()&&settings.activation().data().mode>0&&mode==0){binding=Binding.OFF;light=Light.KEEP;}
    else {binding=Binding.ATLAS;light=Light.OWNER;}
    fragments.add(new Fragment(binding,raw,mode,speed,screens||binding==Binding.OFF?0:crop(raw),0,main?mainLight(appearance):light,null));
   }else if(surface.inputArtwork){
    String id=family==ScreenFamily.HALF_CONSOLE&&slot==1?scalar.secondaryInputPanel:scalar.inputPanel;var input=NativeScreenMediaCatalog.input(id);fragments.add(new Fragment(Binding.INPUT,catalog.inputArtwork(id),mode,speed,input.crop(),0,Light.KEEP,input));
   }else {
    int material=catalog.screenArtwork(scalar.selectedScreen);double inset="engineering_screen".equals(catalog.canonicalScreen(scalar.selectedScreen))?1D/16:0;fragments.add(new Fragment(Binding.DISPLAY,material,mode,speed,inset,0,mode==0?Light.OWNER:Light.FULL,null));
   }
   surfaces.add(new Surface(surface,fragments,before));
  }
  return new NativeScreenRenderPlan(visual,state,geometry,surfaces);
 }
}
