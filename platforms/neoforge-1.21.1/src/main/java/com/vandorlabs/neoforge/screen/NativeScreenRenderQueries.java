package com.vandorlabs.neoforge.screen;

import com.vandorlabs.neoforge.client.NativeScreenClientPower;
import com.vandorlabs.screen.*;
import java.lang.ref.WeakReference;

/** Released top-level normal-media getter selection; nested SIGNAL/STATE are never promoted. */
public final class NativeScreenRenderQueries {
 private NativeScreenRenderQueries(){}
 private record Selection(WeakReference<NativeScreenSettings> settings,WeakReference<com.vandorlabs.persistence.FrameSettings> frames,boolean rows0,boolean rows1,int mask){boolean matches(NativeScreenEntity.Visual visual){return settings.get()==visual.settings()&&frames.get()==visual.frames()&&rows0==visual.controls().get(0).enabled()&&rows1==visual.controls().get(1).enabled();}}
 private static java.util.Map<NativeScreenEntity,Selection> selections=new java.util.WeakHashMap<>();
 /** One consolidated preparation covers all power-dependent branches reachable with these choices.
  * Every added potential getter has the same needsPhysical predicate as a reached mandatory getter. */
 public static NativeScreenEntity.Visual prepare(NativeScreenEntity tile){if(!net.minecraft.client.Minecraft.getInstance().isSameThread())throw new IllegalStateException("Screen render preparation requires the client thread");var visual=tile.visual();if(visual.inert())return visual;var old=selections.get(tile);if(old==null||!old.matches(visual)){old=new Selection(new WeakReference<>(visual.settings()),new WeakReference<>(visual.frames()),visual.controls().get(0).enabled(),visual.controls().get(1).enabled(),potentialMask(visual));if(selections.size()>=2048)selections=new java.util.WeakHashMap<>();selections.put(tile,old);}return NativeScreenClientPower.prepare(tile,old.mask);}
 public static void clear(){if(!net.minecraft.client.Minecraft.getInstance().isSameThread())throw new IllegalStateException("Screen render selection retirement requires the client thread");selections=new java.util.WeakHashMap<>();}
 /** Current released branch mask, independently compared with actual GL getter spies. */
 public static int mask(NativeScreenEntity.Visual visual){var family=visual.family();int mask=ScreenPowerQueries.SURFACE_0;if(family==ScreenFamily.CONSOLE||family==ScreenFamily.HALF_CONSOLE)mask|=ScreenPowerQueries.SURFACE_1;var a=visual.appearance();
  if(family==ScreenFamily.CONSOLE)mask|=input(visual,1,visual.settings().scalar().inputPanel);
  if(family==ScreenFamily.SCREEN||family==ScreenFamily.CONSOLE||family==ScreenFamily.DIAGONAL){if(visual.controls().get(0).enabled()||dynamic(a.primarySurface))return mask;mask|=ScreenPowerQueries.EFFECTIVE_MODE|ScreenPowerQueries.SPLIT;if(a.effectiveMode==2||a.split)mask|=ScreenPowerQueries.ANIMATION_SPEED;if(a.primarySurface>=0)mask|=custom(visual,0);return mask;}
  if(family==ScreenFamily.INPUT){if(dynamic(a.primarySurface))return mask;mask|=ScreenPowerQueries.SPLIT;if(a.split)return mask|ScreenPowerQueries.EFFECTIVE_MODE|ScreenPowerQueries.ANIMATION_SPEED;if(a.primarySurface>=0)return mask|custom(visual,0);return mask|ScreenPowerQueries.EFFECTIVE_MODE|(a.effectiveMode==2?ScreenPowerQueries.SPEED_0:0);}
  if(family==ScreenFamily.HALF_CONSOLE)return mask|input(visual,0,visual.settings().scalar().inputPanel)|input(visual,1,visual.settings().scalar().secondaryInputPanel);
  if(family==ScreenFamily.HALF_DIAGONAL&&visual.controls().get(0).enabled())return mask;
  return mask|input(visual,0,visual.settings().scalar().inputPanel);
 }
 private static boolean dynamic(int surface){return surface < -1;}
 private static int custom(NativeScreenEntity.Visual visual,int slot){var appearance=visual.appearance();int raw=appearance.surface(slot);if((NativeScreenMediaCatalog.flags(raw)&NativeScreenMediaCatalog.SCREENS)!=0)return slot==0?ScreenPowerQueries.MODE_0|ScreenPowerQueries.SPEED_0:ScreenPowerQueries.MODE_1|ScreenPowerQueries.SPEED_1;return slot==visual.family().activationSlot()&&visual.settings().activation().data().mode>0?(slot==0?ScreenPowerQueries.MODE_0:ScreenPowerQueries.MODE_1):0;}
 private static int input(NativeScreenEntity.Visual visual,int slot,String id){var a=visual.appearance();int raw=a.surface(slot);if(dynamic(raw))return 0;if(raw>=0)return custom(visual,slot);int mask=slot==0?ScreenPowerQueries.MODE_0:ScreenPowerQueries.MODE_1;if(a.playback(slot)==2&&NativeScreenMediaCatalog.input(id).declaredFrames()>1)mask|=slot==0?ScreenPowerQueries.SPEED_0:ScreenPowerQueries.SPEED_1;return mask;}
 public static int potentialMask(NativeScreenEntity.Visual visual){int mask=0;for(int physical:new int[]{0,5,10,15}){var appearance=NativeScreenAppearance.of(visual.family(),visual.settings(),visual.frames(),visual.controls(),physical,NativeScreenCatalog.INSTANCE);var candidate=new NativeScreenEntity.Visual(visual.family(),visual.settings(),visual.frames(),visual.glass(),visual.controls(),visual.effectivePower(),physical,visual.inert(),visual.geometryRevision(),appearance);mask|=mask(candidate);}return mask;}
 /** Only reached artwork/frame layout paths may discover neighbours. */
 public static boolean artworkLayout(NativeScreenEntity.Visual visual){var family=visual.family();if(!visual.frames().displayFit)return false;if(family==ScreenFamily.INPUT)return !dynamic(visual.appearance().primarySurface);return (family==ScreenFamily.SCREEN||family==ScreenFamily.CONSOLE||family==ScreenFamily.DIAGONAL)&&!visual.controls().get(0).enabled()&&!dynamic(visual.appearance().primarySurface);}
 public static NativeScreenRenderProjection projection(NativeScreenEntity tile,NativeScreenEntity.Visual visual){return NativeScreenRenderGroups.projection(tile,artworkLayout(visual),frameLayout(visual));}
 public static boolean frameLayout(NativeScreenEntity.Visual visual){var frames=visual.frames();return frames.front>=0&&!frames.bare&&!frames.tile;}
}
