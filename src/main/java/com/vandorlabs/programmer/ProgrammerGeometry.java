package com.vandorlabs.programmer;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.*;

/** Shared cached authored planes: client selection and server socket clicks use identical rays. */
public final class ProgrammerGeometry {
    private static final class Face {final Vec3d[] points;final int slot;final String group;Face(Vec3d[] points,String group){this.points=points;this.group=group;slot=group.contains("__input_pad__")||group.contains("__input_socket_")?1:group.contains("__output_pad__")||group.contains("__output_socket_")?2:group.contains("__master_pad__")||group.contains("__master_socket_")?0:-1;}}
    private static final List<Face> FACES=load();
    private static List<Face> load(){
        List<Face> result=new ArrayList<>();
        for(String part:new String[]{"static","screen"})try(InputStream stream=ProgrammerGeometry.class.getResourceAsStream("/assets/vandorlabs/models/block/data_chip_programmer/ready/parts/"+part+".obj")){
            if(stream==null)throw new IOException("Missing programmer geometry");List<Vec3d> vertices=new ArrayList<>();String group="";
            try(BufferedReader reader=new BufferedReader(new InputStreamReader(stream,StandardCharsets.UTF_8))){String line;while((line=reader.readLine())!=null){String[] bits=line.trim().split("\\s+");if(bits[0].equals("v"))vertices.add(new Vec3d(Double.parseDouble(bits[1]),Double.parseDouble(bits[2]),Double.parseDouble(bits[3])));else if(bits[0].equals("g"))group=bits[1];else if(bits[0].equals("f")){Vec3d[] points=new Vec3d[bits.length-1];for(int i=1;i<bits.length;i++)points[i-1]=vertices.get(Integer.parseInt(bits[i].split("/")[0])-1);result.add(new Face(points,group));}}}
        }catch(IOException|RuntimeException failure){throw new IllegalStateException("Cannot load programmer selection geometry",failure);}
        return Collections.unmodifiableList(result);
    }
    public static ProgrammerTransform.Affine transform(EnumFacing facing){return ProgrammerTransform.of(ProgrammerTransform.Facing.valueOf(facing.name()),2,1);}
    private static Vec3d local(Vec3d point,BlockPos origin,ProgrammerTransform.Affine a){double x=point.x-origin.getX()-a.offsetX,z=point.z-origin.getZ()-a.offsetZ;return new Vec3d(a.xx*x+a.zx*z,point.y-origin.getY(),a.xz*x+a.zz*z);}
    private static Vec3d triangle(Vec3d from,Vec3d ray,Vec3d a,Vec3d b,Vec3d c){Vec3d edge=b.subtract(a),other=c.subtract(a),p=ray.crossProduct(other);double determinant=edge.dotProduct(p);if(Math.abs(determinant)<1e-10)return null;double inv=1/determinant;Vec3d t=from.subtract(a);double u=t.dotProduct(p)*inv;if(u< -1e-7||u>1+1e-7)return null;Vec3d q=t.crossProduct(edge);double v=ray.dotProduct(q)*inv;if(v< -1e-7||u+v>1+1e-7)return null;double distance=other.dotProduct(q)*inv;return distance>=0&&distance<=1?from.add(ray.scale(distance)):null;}
    public static RayTraceResult trace(BlockPos cell,BlockPos origin,EnumFacing facing,Vec3d start,Vec3d end){
        ProgrammerTransform.Affine a=transform(facing);Vec3d from=local(start,origin,a),to=local(end,origin,a),ray=to.subtract(from);RayTraceResult closest=null;double distance=Double.POSITIVE_INFINITY;
        for(Face face:FACES)for(int i=1;i<face.points.length-1;i++){
            Vec3d hit=triangle(from,ray,face.points[0],face.points[i],face.points[i+1]);if(hit==null)continue;
            Vec3d world=new Vec3d(origin.getX()+a.x(hit.x,hit.z),origin.getY()+hit.y,origin.getZ()+a.z(hit.x,hit.z));if(world.x<cell.getX()-1e-7||world.x>cell.getX()+1+1e-7||world.y<cell.getY()-1e-7||world.y>cell.getY()+1+1e-7||world.z<cell.getZ()-1e-7||world.z>cell.getZ()+1+1e-7)continue;
            double length=start.squareDistanceTo(world);if(length>=distance)continue;Vec3d normal=face.points[i].subtract(face.points[0]).crossProduct(face.points[i+1].subtract(face.points[0]));EnumFacing side=EnumFacing.getFacingFromVector((float)(a.xx*normal.x+a.xz*normal.z),(float)normal.y,(float)(a.zx*normal.x+a.zz*normal.z));closest=new RayTraceResult(world,side,cell);closest.subHit=face.slot;closest.hitInfo=face.group;distance=length;
        }
        return closest;
    }
    private ProgrammerGeometry(){}
}
