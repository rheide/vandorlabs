package com.vandorlabs.physics;

/** Directional world-space body prior only; no contacts, gravity, terrain or joint-angle admission. */
public final class CouplerBodyPrior {
 public enum Status {APPLIED,UNCHANGED,DEGENERATE,INVALID}
 public static final double MIN_LEVER=1e-9,ROTATION_TOLERANCE=1e-7;
 /**
  * Left-compose the shortest bounded SO(3) correction into a proper row-major WORLD rotation.
  * All positions are WORLD scalars. Supplied arrays have nine entries; aliasing is supported.
  * INVALID leaves output untouched. DEGENERATE/UNCHANGED copy the original rotation exactly.
  */
 public static Status write(double[] priorR,double cx,double cy,double cz,double px,double py,double pz,double desiredX,double desiredY,double desiredZ,double maxStepRadians,double[] outR){
  if(priorR==null||priorR.length!=9||outR==null||outR.length!=9||!finite(cx)||!finite(cy)||!finite(cz)||!finite(px)||!finite(py)||!finite(pz)||!finite(desiredX)||!finite(desiredY)||!finite(desiredZ)||!finite(maxStepRadians)||maxStepRadians<0||maxStepRadians>Math.PI||!proper(priorR))return Status.INVALID;
  double x=cx-px,y=cy-py,z=cz-pz,u=desiredX-px,v=desiredY-py,w=desiredZ-pz;
  if(!finite(x)||!finite(y)||!finite(z)||!finite(u)||!finite(v)||!finite(w))return Status.INVALID;
  double a=Math.hypot(Math.hypot(x,y),z),b=Math.hypot(Math.hypot(u,v),w);
  if(!finite(a)||!finite(b))return Status.INVALID;
  if(a<=MIN_LEVER||b<=MIN_LEVER){System.arraycopy(priorR,0,outR,0,9);return Status.DEGENERATE;}
  x/=a;y/=a;z/=a;u/=b;v/=b;w/=b;
  double ax=y*w-z*v,ay=z*u-x*w,az=x*v-y*u,sine=Math.hypot(Math.hypot(ax,ay),az),cosine=Math.max(-1,Math.min(1,x*u+y*v+z*w));
  if(sine<=1e-12){
   if(cosine>=0||maxStepRadians==0){System.arraycopy(priorR,0,outR,0,9);return Status.UNCHANGED;}
   // Exact antiparallel tie: least-aligned basis (X, then Y, then Z) cross current lever.
   double xx=Math.abs(x),yy=Math.abs(y),zz=Math.abs(z);
   if(xx<=yy&&xx<=zz){ax=0;ay=-z;az=y;}
   else if(yy<=zz){ax=z;ay=0;az=-x;}
   else{ax=-y;ay=x;az=0;}
   a=Math.hypot(Math.hypot(ax,ay),az);
  }else a=sine;
  double angle=Math.min(maxStepRadians,Math.atan2(sine,cosine));
  if(angle<=1e-12){System.arraycopy(priorR,0,outR,0,9);return Status.UNCHANGED;}
  ax/=a;ay/=a;az/=a;double c=Math.cos(angle),s=Math.sin(angle),t=1-c;
  double q00=c+ax*ax*t,q01=ax*ay*t-az*s,q02=ax*az*t+ay*s,q10=ay*ax*t+az*s,q11=c+ay*ay*t,q12=ay*az*t-ax*s,q20=az*ax*t-ay*s,q21=az*ay*t+ax*s,q22=c+az*az*t;
  // Read all nine before writing: caller-owned output may be the prior itself.
  double r00=priorR[0],r01=priorR[1],r02=priorR[2],r10=priorR[3],r11=priorR[4],r12=priorR[5],r20=priorR[6],r21=priorR[7],r22=priorR[8];
  outR[0]=q00*r00+q01*r10+q02*r20;outR[1]=q00*r01+q01*r11+q02*r21;outR[2]=q00*r02+q01*r12+q02*r22;
  outR[3]=q10*r00+q11*r10+q12*r20;outR[4]=q10*r01+q11*r11+q12*r21;outR[5]=q10*r02+q11*r12+q12*r22;
  outR[6]=q20*r00+q21*r10+q22*r20;outR[7]=q20*r01+q21*r11+q22*r21;outR[8]=q20*r02+q21*r12+q22*r22;return Status.APPLIED;
 }
 private static boolean proper(double[] r){for(double x:r)if(!finite(x))return false;for(int a=0;a<3;a++)for(int b=a;b<3;b++){double dot=r[a]*r[b]+r[3+a]*r[3+b]+r[6+a]*r[6+b];if(!finite(dot)||Math.abs(dot-(a==b?1:0))>ROTATION_TOLERANCE)return false;}double det=r[0]*(r[4]*r[8]-r[5]*r[7])-r[1]*(r[3]*r[8]-r[5]*r[6])+r[2]*(r[3]*r[7]-r[4]*r[6]);return finite(det)&&Math.abs(det-1)<=ROTATION_TOLERANCE;}
 private static boolean finite(double x){return Double.isFinite(x);}
 private CouplerBodyPrior(){}
}
