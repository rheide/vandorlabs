package com.vandorlabs.rack;
/** Exact 36x4 top-bar texels from the 64px Power Port art. Shared item/rack/tower fill and clipping. */
public final class BatteryChargeBar {
 public static final int COLUMNS=36,BITS=6,MASK=63;
 public static int pixels(int energy,int capacity){return capacity<=0?0:(int)Math.min(COLUMNS,Math.max(0,(long)energy)*COLUMNS/capacity);}
 public static int pack(int packed,int bay,int energy,int capacity){int shift=bay*BITS;return packed&~(MASK<<shift)|pixels(energy,capacity)<<shift;}
 public static int unpack(int packed,int bay){return Math.min(COLUMNS,packed>>>(bay*BITS)&MASK);}
 /** Clip a baked rectangular bar by source U columns; every other vertex component is retained. */
 public static int[] clip(int[] source,int columns){int[] out=source.clone();int stride=source.length/4;float min=Float.POSITIVE_INFINITY,max=Float.NEGATIVE_INFINITY;for(int v=0;v<4;v++){float u=Float.intBitsToFloat(source[v*stride+4]);min=Math.min(min,u);max=Math.max(max,u);}float fraction=Math.max(0,Math.min(COLUMNS,columns))/(float)COLUMNS;
  for(int v=0;v<4;v++){int at=v*stride;if(Float.intBitsToFloat(source[at+4])<=min)continue;for(int low=0;low<4;low++){int base=low*stride;if(Float.intBitsToFloat(source[base+4])!=min||source[base+5]!=source[at+5])continue;for(int axis=0;axis<3;axis++){float a=Float.intBitsToFloat(source[base+axis]),b=Float.intBitsToFloat(source[at+axis]);out[at+axis]=Float.floatToRawIntBits(a+(b-a)*fraction);}out[at+4]=Float.floatToRawIntBits(min+(max-min)*fraction);break;}}
  return out;
 }
 private BatteryChargeBar(){}
}
