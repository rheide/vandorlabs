package com.vandorlabs.neoforge.screen;
import com.vandorlabs.screen.ScreenFamily;
/** Default/static/Redstone/Chip scalar selection; installed-host availability belongs to the authority. Vehicle selection remains refused. */
public record NativeScreenSurfaceEdit(int slot,int choice) {
 public NativeScreenSurfaceEdit{if(slot<0||slot>1||(choice < -2&&choice!=-4))throw new IllegalArgumentException("Screen surface choice");}
 public record Result(NativeScreenSettings settings,boolean rowsEnabled){}
 public Result apply(ScreenFamily family,NativeScreenSettings old,NativeScreenSettings.Catalog catalog){if(!family.supportsSlot(slot)||choice>=0&&!catalog.validMaterial(choice))return null;int raw=slot==0?old.primarySurface():old.secondarySurface(),next=choice==-2?(raw==-3?-1:raw):choice<0?choice:catalog.material(choice);NativeScreenSettings settings=raw==next?old:new NativeScreenSettings(old.scalar(),old.housing(),old.activation(),old.channels(),old.channelLevel(),old.inverted(),slot==0?next:old.primarySurface(),slot==1?next:old.secondarySurface(),old.ceilingMounted(),old.ceilingPosition(),old.surfaceLayout(),old.surfaceTileSides());return new Result(settings,choice==-2);}
}
