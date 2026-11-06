package com.vandorlabs.render;

/** Exact released reader face bases, logo winding, indicators and insertion offset. */
public final class ReaderVisualMesh {
 public interface Sink {void vertex(double x,double y,double z,float u,float v,int rgb);}
 public static final class Basis {
  public final double ox,oy,oz,ux,uy,uz,vx,vy,vz,nx,ny,nz;
  private Basis(int face,int rotation){
   nx=face==4?-1:face==5?1:0;ny=face==0?-1:face==1?1:0;nz=face==2?-1:face==3?1:0;
   ox=.5-nx*.5;oy=.5-ny*.5;oz=.5-nz*.5;
   double x=face==4||face==5?0:face==3?-1:1,y=0,z=face==5?1:face==4?-1:0;
   double a=0,b=ny==0?1:0,c=face==1?1:face==0?-1:0;
   if(ny!=0)for(int i=0;i<(rotation&3);i++){double oldX=x,oldY=y,oldZ=z;x=a;y=b;z=c;a=-oldX;b=-oldY;c=-oldZ;}
   ux=x==0?0:x;uy=y==0?0:y;uz=z==0?0:z;vx=a==0?0:a;vy=b==0?0:b;vz=c==0?0:c;
  }
  public double x(double x,double y,double depth){return ox+ux*x+vx*y+nx*depth;}
  public double y(double x,double y,double depth){return oy+uy*x+vy*y+ny*depth;}
  public double z(double x,double y,double depth){return oz+uz*x+vz*y+nz*depth;}
  private void vertex(Sink sink,double x,double y,double depth,float u,float v,int color){sink.vertex(x(x,y,depth),y(x,y,depth),z(x,y,depth),u,v,color);}
 }
 private static final Basis[][] BASES=new Basis[6][4];
 static{for(int f=0;f<6;f++)for(int r=0;r<4;r++)BASES[f][r]=new Basis(f,r);}
 private ReaderVisualMesh(){}
 public static Basis basis(int face,int rotation){if(face<0||face>5)throw new IllegalArgumentException("Reader face");return BASES[face][rotation&3];}
 public static double insertionOffset(double age,boolean returning){double slide=age<8?age/8:returning&&age>12?(20-age)/8:1;slide=slide*slide*(3-2*slide);return .60*(1-slide);}
 public static void logo(Basis b,float u0,float u1,float v0,float v1,Sink sink){double h=2.2/16;b.vertex(sink,-h,-h,.025,u1,v1,0xffffff);b.vertex(sink,h,-h,.025,u0,v1,0xffffff);b.vertex(sink,h,h,.025,u0,v0,0xffffff);b.vertex(sink,-h,h,.025,u1,v0,0xffffff);}
 private static void panel(Basis b,Sink sink,double left,double right,double bottom,double top,double depth,int rgb){b.vertex(sink,left,bottom,depth,0,0,rgb);b.vertex(sink,right,bottom,depth,0,0,rgb);b.vertex(sink,right,top,depth,0,0,rgb);b.vertex(sink,left,top,depth,0,0,rgb);}
 public static void overlays(Basis b,boolean chip,boolean rejected,boolean indicator,Sink sink){
  double h=(chip?3:4.4)/16;
  if(rejected)for(int pixel=-2;pixel<=2;pixel++){panel(b,sink,(pixel-.35)/16,(pixel+.35)/16,(pixel-.35)/16,(pixel+.35)/16,.026,0xffb340);if(pixel!=0)panel(b,sink,(pixel-.35)/16,(pixel+.35)/16,(-pixel-.35)/16,(-pixel+.35)/16,.026,0xffb340);}
  panel(b,sink,.9/16,2.1/16,-h+.22/16,-h+.47/16,.098,indicator?0x5cffff:0x1c6876);panel(b,sink,-2.1/16,-.9/16,-h+.22/16,-h+.47/16,.098,0xc68a37);
 }
}
