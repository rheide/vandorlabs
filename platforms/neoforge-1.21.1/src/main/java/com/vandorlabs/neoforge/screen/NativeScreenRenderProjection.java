package com.vandorlabs.neoforge.screen;

import com.vandorlabs.screen.ScreenFamily;
import com.vandorlabs.screen.ScreenRenderLayout;

/** Independent artwork/frame rectangles. Published values contain no hosts, worlds or discovery caches. */
public record NativeScreenRenderProjection(Rectangle artwork,Rectangle frame) {
 public record Rectangle(int column,int row,int columns,int rows){
  public static final Rectangle SINGLE=new Rectangle(0,0,1,1);
  public Rectangle{if(columns<1||rows<1||(long)columns*rows>=1024||column<0||column>=columns||row<0||row>=rows)throw new IllegalArgumentException("Screen render rectangle");}
 }
 /** Frames and artwork use the same rendered axes, including ordinary floor/ceiling displays. */
 public static Rectangle surfaceRectangle(ScreenFamily family,int facing,boolean ceiling,Rectangle raw){
  if(facing<0||facing>5)throw new IllegalArgumentException("Screen render facing");java.util.Objects.requireNonNull(raw);int column=ScreenRenderLayout.mirrorColumns(family,facing)?raw.columns-1-raw.column:raw.column,row=ScreenRenderLayout.mirrorRows(family,facing,ceiling)?raw.rows-1-raw.row:raw.row;return column==raw.column&&row==raw.row?raw:new Rectangle(column,row,raw.columns,raw.rows);
 }
 public static Rectangle frameRectangle(int facing,boolean chip,Rectangle raw){
  return surfaceRectangle(ScreenFamily.SCREEN,facing,false,raw);
 }
 public static final NativeScreenRenderProjection SINGLE=new NativeScreenRenderProjection(Rectangle.SINGLE,Rectangle.SINGLE);
 public NativeScreenRenderProjection{java.util.Objects.requireNonNull(artwork);java.util.Objects.requireNonNull(frame);}
}
