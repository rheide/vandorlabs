package com.vandorlabs.render;

/** Released artwork mapping, independent of group discovery and resource lookup. */
public final class TrapdoorArtworkCoordinates {
 private TrapdoorArtworkCoordinates(){}
 /** Bounds are owner-relative along the artwork plane; origins retain source world-coordinate rounding. */
 public static boolean write(double[][] closed,double[][] uv,boolean widthX,boolean tall,boolean diagonal,int position,
                             int originU,int originV,double relativeMinU,double relativeMinV,double relativeMaxU,double relativeMaxV,
                             int layout,boolean rawTile,boolean door,double aspect) {
  double minU=originU+relativeMinU,minV=originV+relativeMinV,maxU=originU+relativeMaxU,maxV=originV+relativeMaxV;
  double factor=diagonal?Math.sqrt(position==1?2:1.25):1;
  boolean fit=TextureLayoutMode.fit(layout,maxU-minU,(maxV-minV)*factor,aspect),tiled=!fit;
  for(int i=0;i<8;i++) {
   double u=originU+(widthX?closed[i][0]:closed[i][2]);
   double v=originV+(tall?closed[i][1]:widthX?closed[i][2]:closed[i][0]);
   double mappedU=u-minU,mappedV=tall?maxV-v:v-minV;
   if(layout==2||tiled&&!door){if(tiled){mappedU=TextureLayoutMode.tileU(mappedU,aspect);mappedV=TextureLayoutMode.tileV(mappedV*factor,aspect);}else{mappedU/=maxU-minU;mappedV/=maxV-minV;}}
   else if(rawTile){if(door){mappedV/=2;if(maxV-minV<1.5)mappedV+=.5;}}
   else{mappedU/=maxU-minU;mappedV/=maxV-minV;if(door&&maxV-minV<1.5)mappedV=.5+.5*mappedV;}
   uv[i][0]=mappedU;uv[i][1]=tall?mappedV:(i&2)==0?0:1;uv[i][2]=tall?(i&4)==0?0:1:mappedV;
  }
  return tiled;
 }
}
