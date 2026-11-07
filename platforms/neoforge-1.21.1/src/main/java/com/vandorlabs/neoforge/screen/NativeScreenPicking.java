package com.vandorlabs.neoforge.screen;
import com.vandorlabs.render.LegacyBoxTrace;
import com.vandorlabs.render.ScreenHousingMesh;
import com.vandorlabs.screen.ScreenFamily;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;
/** Exact Source mesh picking for the full incline; half inclines retain eight slice picking. */
public final class NativeScreenPicking {
 private NativeScreenPicking(){}
 private static final Triangle[][] MESHES=new Triangle[8][];
 static {for(int face=2;face<6;face++)for(int upper=0;upper<2;upper++){java.util.List<Triangle> triangles=new java.util.ArrayList<>();for(var quad:ScreenHousingMesh.diagonal(upper!=0).quads){Vec3[] points=new Vec3[4];for(int i=0;i<4;i++){var v=quad.vertices[i];points[i]=world(v.x/16,v.y/16,v.z/16,face);}triangles.add(new Triangle(points[0],points[1],points[2],face,upper!=0));triangles.add(new Triangle(points[0],points[2],points[3],face,upper!=0));}MESHES[(face-2)*2+upper]=triangles.toArray(Triangle[]::new);}}
 public static LegacyBoxTrace.Hit trace(ScreenFamily family,ScreenFamily.State state,NativeScreenGeometry geometry,Vec3 start,Vec3 end){
  if(family==ScreenFamily.DIAGONAL){Vec3 delta=end.subtract(start);Triangle selected=null;double closest=Double.POSITIVE_INFINITY;for(var triangle:MESHES[(state.facing-2)*2+(state.inverted?1:0)]){double t=triangle.hit(start,delta);if(t<closest){closest=t;selected=triangle;}}LegacyBoxTrace.Hit hit=null;if(selected!=null){Vec3 point=start.add(delta.scale(closest));int face=Direction.getNearest((float)selected.normal.x,(float)selected.normal.y,(float)selected.normal.z).get3DDataValue();hit=new LegacyBoxTrace.Hit(point.x,point.y,point.z,face);}var box=geometry.readerPick();if(box!=null){var reader=LegacyBoxTrace.clip(box.minX,box.minY,box.minZ,box.maxX,box.maxY,box.maxZ,start.x,start.y,start.z,end.x,end.y,end.z);if(reader!=null&&(hit==null||reader.distance(start.x,start.y,start.z)<hit.distance(start.x,start.y,start.z)))return reader;}return hit;}

  LegacyBoxTrace.Hit closest=null;for(var box:geometry.collision()){var hit=LegacyBoxTrace.clip(box.minX,box.minY,box.minZ,box.maxX,box.maxY,box.maxZ,start.x,start.y,start.z,end.x,end.y,end.z);if(hit!=null&&(closest==null||hit.distance(start.x,start.y,start.z)<closest.distance(start.x,start.y,start.z)))closest=hit;}return closest;
 }
 private static Vec3 world(double x,double y,double z,int face){return switch(face){case 5->new Vec3(1-z,y,x);case 3->new Vec3(1-x,y,1-z);case 4->new Vec3(z,y,1-x);default->new Vec3(x,y,z);};}
 private static Vec3 local(Vec3 p,int face){double x=p.x-.5,y=p.y-.5,z=p.z-.5;return switch(face){case 5->new Vec3(z+.5,y+.5,-x+.5);case 3->new Vec3(-x+.5,y+.5,-z+.5);case 4->new Vec3(-z+.5,y+.5,x+.5);default->p;};}
 private static final class Triangle {
  final Vec3 a,e1,e2,normal;
  Triangle(Vec3 a,Vec3 b,Vec3 c,int facing,boolean upper){this.a=a;e1=b.subtract(a);e2=c.subtract(a);Vec3 n=e1.cross(e2).normalize(),center=a.add(b).add(c).scale(1D/3),probe=local(center.add(n.scale(.00001)),facing);double height=probe.z<1D/16?1D/16:probe.z>=15D/16?1:probe.z;boolean inside=probe.x>=0&&probe.x<=1&&probe.y>=0&&probe.y<=1&&probe.z>=0&&probe.z<=1&&(upper?probe.y>=1-height:probe.y<=height);normal=inside?n.scale(-1):n;}
  double hit(Vec3 start,Vec3 d){double px=d.y*e2.z-d.z*e2.y,py=d.z*e2.x-d.x*e2.z,pz=d.x*e2.y-d.y*e2.x,det=e1.x*px+e1.y*py+e1.z*pz;if(Math.abs(det)<1e-10)return Double.POSITIVE_INFINITY;double tx=start.x-a.x,ty=start.y-a.y,tz=start.z-a.z,u=(tx*px+ty*py+tz*pz)/det;if(u< -1e-8||u>1+1e-8)return Double.POSITIVE_INFINITY;double qx=ty*e1.z-tz*e1.y,qy=tz*e1.x-tx*e1.z,qz=tx*e1.y-ty*e1.x,v=(d.x*qx+d.y*qy+d.z*qz)/det;if(v< -1e-8||u+v>1+1e-8)return Double.POSITIVE_INFINITY;double t=(e2.x*qx+e2.y*qy+e2.z*qz)/det;return t>=0&&t<=1?t:Double.POSITIVE_INFINITY;}
 }
}
