package com.vandorlabs.neoforge.screen;
import com.vandorlabs.persistence.FrameSettings;
import com.vandorlabs.neoforge.ModernFinishCatalog;
/** Strict frame choices; Source device editors always publish current artwork version one. */
public record NativeScreenFrameEdit(FrameSettings frames) {
 public NativeScreenFrameEdit{var catalog=NativeScreenCatalog.INSTANCE;if(frames==null||!catalog.validFrame(frames.front)||!catalog.validFrameSurface(frames.interior)||!catalog.validFrameSurface(frames.exterior)||(frames.artworkVersion!=0&&frames.artworkVersion!=1)||(frames.splitTop!=-1&&!ModernFinishCatalog.validChoice(frames.splitTop))||(frames.splitBottom!=-1&&!ModernFinishCatalog.validChoice(frames.splitBottom)))throw new IllegalArgumentException("Screen frame edit");}
 public FrameSettings apply(FrameSettings old){var f=frames;var next=new FrameSettings(f.front,f.interior,f.exterior,1,f.splitTop,f.splitBottom,f.tile,f.displayFit,f.split,f.bare);return same(old,next)?old:next;}
 private static boolean same(FrameSettings a,FrameSettings b){return a.front==b.front&&a.interior==b.interior&&a.exterior==b.exterior&&a.artworkVersion==b.artworkVersion&&a.splitTop==b.splitTop&&a.splitBottom==b.splitBottom&&a.tile==b.tile&&a.displayFit==b.displayFit&&a.split==b.split&&a.bare==b.bare;}
}
