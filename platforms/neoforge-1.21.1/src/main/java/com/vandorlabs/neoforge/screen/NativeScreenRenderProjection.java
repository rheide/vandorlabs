package com.vandorlabs.neoforge.screen;

/** Independent artwork/frame rectangles. Published values contain no hosts, worlds or discovery caches. */
public record NativeScreenRenderProjection(Rectangle artwork,Rectangle frame) {
 public record Rectangle(int column,int row,int columns,int rows){
  public static final Rectangle SINGLE=new Rectangle(0,0,1,1);
  public Rectangle{if(columns<1||rows<1||(long)columns*rows>=1024||column<0||column>=columns||row<0||row>=rows)throw new IllegalArgumentException("Screen render rectangle");}
 }
 /** Regular CHIP screens mirror the normal frame partition on vertical faces in Source. */
 public static Rectangle frameRectangle(int facing,boolean chip,Rectangle raw){
  if(facing<0||facing>5)throw new IllegalArgumentException("Screen frame facing");java.util.Objects.requireNonNull(raw);int column=chip&&facing==1?raw.columns-1-raw.column:raw.column,row=chip&&facing==0?raw.rows-1-raw.row:raw.row;return column==raw.column&&row==raw.row?raw:new Rectangle(column,row,raw.columns,raw.rows);
 }
 public static final NativeScreenRenderProjection SINGLE=new NativeScreenRenderProjection(Rectangle.SINGLE,Rectangle.SINGLE);
 public NativeScreenRenderProjection{java.util.Objects.requireNonNull(artwork);java.util.Objects.requireNonNull(frame);}
}
