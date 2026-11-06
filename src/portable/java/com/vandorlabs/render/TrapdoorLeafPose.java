package com.vandorlabs.render;

/** Loaded membership decisions resolved once by the host; no world/tile references. */
public final class TrapdoorLeafPose {
 public final boolean diagonal,inverted,sliding,cover,surface,intoWall,reverse,closedReverse;
 public final int position,turns,lift;
 public final double hinge,travel,overhang;
 public TrapdoorLeafPose(boolean diagonal,int position,int turns,boolean inverted,boolean sliding,boolean cover,boolean surface,boolean intoWall,boolean reverse,boolean closedReverse,int lift,double hinge,double travel,double overhang){
  if(position<0||position>2||turns<0||turns>3||(lift!=1&&lift!=-1)||!Double.isFinite(hinge)||!Double.isFinite(travel)||!Double.isFinite(overhang)||Math.abs(hinge)>8||travel<15D/16||travel>8||overhang<0||overhang>1D/16)throw new IllegalArgumentException("Trapdoor resolved geometry");
  this.diagonal=diagonal;this.position=position;this.turns=turns;this.inverted=inverted;this.sliding=sliding;this.cover=cover;this.surface=surface;this.intoWall=intoWall;this.reverse=reverse;this.closedReverse=closedReverse;this.lift=lift;this.hinge=hinge;this.travel=travel;this.overhang=overhang;
 }
 /** Renderer passes false; authoritative block queries retain the source closed ungrouped path. */
 public void write(double pose,boolean authoritative,double[][] out){
  if(diagonal)DiagonalTrapdoorGeometry.writeCorners(position,inverted,turns,sliding,authoritative&&pose==0?closedReverse:reverse,pose,hinge,travel,authoritative&&pose==0?1:lift,intoWall,out);
  else if(cover)TrapdoorGeometry.writeCoverCorners(position,sliding,turns,pose,overhang,out);
  else if(sliding&&surface)TrapdoorGeometry.writeSurfaceCorners(position,turns,pose,hinge,travel,out);
  else TrapdoorGeometry.writeCorners(position,sliding,turns,pose,hinge,travel,out);
 }
 @Override public boolean equals(Object object){if(!(object instanceof TrapdoorLeafPose))return false;TrapdoorLeafPose p=(TrapdoorLeafPose)object;return diagonal==p.diagonal&&position==p.position&&turns==p.turns&&inverted==p.inverted&&sliding==p.sliding&&cover==p.cover&&surface==p.surface&&intoWall==p.intoWall&&reverse==p.reverse&&closedReverse==p.closedReverse&&lift==p.lift&&Double.doubleToLongBits(hinge)==Double.doubleToLongBits(p.hinge)&&Double.doubleToLongBits(travel)==Double.doubleToLongBits(p.travel)&&Double.doubleToLongBits(overhang)==Double.doubleToLongBits(p.overhang);}
 @Override public int hashCode(){int flags=(diagonal?1:0)|(inverted?2:0)|(sliding?4:0)|(cover?8:0)|(surface?16:0)|(intoWall?32:0)|(reverse?64:0)|(closedReverse?128:0);int h=((flags*31+position)*31+turns)*31+lift;h=31*h+Double.hashCode(hinge);h=31*h+Double.hashCode(travel);return 31*h+Double.hashCode(overhang);}
}
