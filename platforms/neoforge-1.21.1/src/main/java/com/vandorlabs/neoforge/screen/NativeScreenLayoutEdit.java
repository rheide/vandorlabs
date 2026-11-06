package com.vandorlabs.neoforge.screen;
/** The dedicated Screen sides command changes artwork layout, preserving the housing layout. */
public record NativeScreenLayoutEdit(int layout) {
 public NativeScreenLayoutEdit{if(!com.vandorlabs.render.TextureLayoutMode.valid(layout))throw new IllegalArgumentException("Screen surface layout");}
 public NativeScreenSettings apply(NativeScreenSettings old){if(old.surfaceLayout()==layout)return old;return new NativeScreenSettings(old.scalar(),old.housing(),old.activation(),old.channels(),old.channelLevel(),old.inverted(),old.primarySurface(),old.secondarySurface(),old.ceilingMounted(),old.ceilingPosition(),layout,layout==1);}
}
