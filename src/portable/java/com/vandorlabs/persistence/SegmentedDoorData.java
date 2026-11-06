package com.vandorlabs.persistence;
/** World-free Segmented Door scalar contract; glass Original (-2) is never valid here. */
public class SegmentedDoorData {
 public static final int UP=0,DOWN=1,LEFT=2,RIGHT=3,PACKET_BYTES=15;
 public final int direction,layout,activation,shape,position;
 public final boolean straight,window,join;
 public final DoorGlassData glass;
 public SegmentedDoorData(int direction,boolean straight,int layout,int activation,boolean window,int shape,boolean join,DoorGlassData glass,int position){
  if(position<0||position>2||direction<0||direction>3||layout<0||layout>2||activation< -1||activation>2||shape<0||shape>3||glass==null||glass.material==DoorGlassData.ORIGINAL)throw new IllegalArgumentException("Invalid Segmented Door settings");
  this.direction=direction;this.straight=straight;this.layout=layout;this.activation=activation;this.window=window;this.shape=shape;this.join=join;this.glass=glass;this.position=position;
 }
 public double depthOffset(){return position==1?-13D/32:position==2?13D/32:0;}
 public boolean nativePanel(){return !window&&layout==1;}
 public boolean same(SegmentedDoorData s){return s!=null&&direction==s.direction&&straight==s.straight&&layout==s.layout&&activation==s.activation&&window==s.window&&shape==s.shape&&join==s.join&&position==s.position&&glass.material==s.glass.material&&glass.shade==s.glass.shade&&glass.tint==s.glass.tint&&glass.layout==s.glass.layout;}
}
