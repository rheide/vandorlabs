package com.vandorlabs.neoforge.screen;

/** Independent artwork/frame rectangles. Published values contain no hosts, worlds or discovery caches. */
public record NativeScreenRenderProjection(Rectangle artwork,Rectangle frame) {
 public record Rectangle(int column,int row,int columns,int rows){
  public static final Rectangle SINGLE=new Rectangle(0,0,1,1);
  public Rectangle{if(columns<1||rows<1||(long)columns*rows>=1024||column<0||column>=columns||row<0||row>=rows)throw new IllegalArgumentException("Screen render rectangle");}
 }
 public static final NativeScreenRenderProjection SINGLE=new NativeScreenRenderProjection(Rectangle.SINGLE,Rectangle.SINGLE);
 public NativeScreenRenderProjection{java.util.Objects.requireNonNull(artwork);java.util.Objects.requireNonNull(frame);}
}
