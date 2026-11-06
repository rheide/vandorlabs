package com.vandorlabs.neoforge.screen;
/** Released independent artwork/frame membership; no authority, world or cache access. */
public final class NativeScreenRenderMatching {
 private NativeScreenRenderMatching(){}
 public static boolean sameShape(NativeScreenEntity.Visual a,NativeScreenEntity.Visual b){var x=a.settings();var y=b.settings();var sx=x.scalar();var sy=y.scalar();return x.ceilingMounted()==y.ceilingMounted()&&x.ceilingPosition()==y.ceilingPosition()&&sx.wallPosition==sy.wallPosition&&sx.smallInput==sy.smallInput;}
 public static boolean matches(NativeScreenEntity.Visual a,NativeScreenEntity.Visual b,boolean frame){if(!sameShape(a,b))return false;var af=a.frames();var bf=b.frames();if(frame)return af.bare==bf.bare&&af.front==bf.front&&af.tile==bf.tile;var aa=a.appearance();var ba=b.appearance();return af.displayFit==bf.displayFit&&aa.split==ba.split&&(!aa.split||af.splitTop==bf.splitTop&&af.splitBottom==bf.splitBottom)&&aa.primaryArtwork==ba.primaryArtwork&&aa.effectiveMode==ba.effectiveMode&&aa.animationSpeed==ba.animationSpeed;}
}
