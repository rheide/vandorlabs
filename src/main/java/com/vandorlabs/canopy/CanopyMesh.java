package com.vandorlabs.canopy;

import com.google.gson.*;

import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.*;

import java.io.*;
import java.util.*;

/** Authored kit faces shared by drawing, picking and thin surface collision. */
public final class CanopyMesh {
    public static final Map<String, List<Face>> MODELS = new HashMap<>();
    public static final Map<String, int[]> DIMENSIONS = new HashMap<>();
    public static final Set<String> MATERIALS = new LinkedHashSet<>();

    public static final class Face {
        public Vec3d[] vertices;
        public double[][] uv;
        public String material, baseMaterial, group;
        public boolean doubleSided;
        public boolean glazing,tiled;
    }

    static {
        try (InputStream in =
                CanopyMesh.class.getResourceAsStream(
                        "/assets/vandorlabs/data/canopy_meshes.json")) {
            for (JsonElement model :
                    new JsonParser().parse(new InputStreamReader(in, "UTF-8")).getAsJsonArray()) {
                JsonObject m = model.getAsJsonObject();
                List<Face> faces = new ArrayList<>();
                for (JsonElement element : m.getAsJsonArray("faces")) {
                    JsonObject f = element.getAsJsonObject();
                    Face face = new Face();
                    JsonArray v = f.getAsJsonArray("v"), uv = f.getAsJsonArray("uv");
                    face.vertices = new Vec3d[v.size()];
                    face.uv = new double[v.size()][2];
                    for (int i = 0; i < v.size(); i++) {
                        JsonArray a = v.get(i).getAsJsonArray(), b = uv.get(i).getAsJsonArray();
                        face.vertices[i] =
                                new Vec3d(
                                        a.get(0).getAsDouble(),
                                        a.get(1).getAsDouble(),
                                        a.get(2).getAsDouble());
                        face.uv[i] = new double[] {b.get(0).getAsDouble(), b.get(1).getAsDouble()};
                    }
                    face.material = f.get("material").getAsString();
                    face.baseMaterial = f.has("base_material") ? f.get("base_material").getAsString() : face.material;
                    MATERIALS.add(face.material);
                    face.group = f.get("group").getAsString();
                    face.doubleSided = f.get("double").getAsBoolean();
                    faces.add(face);
                }
                String id = m.get("id").getAsString();
                JsonArray footprint = m.getAsJsonArray("footprint");
                double top = 0;
                for (Face face : faces) for (Vec3d v : face.vertices) top = Math.max(top, v.y);
                DIMENSIONS.put(
                        id,
                        new int[] {
                            Math.max(1, (int) Math.ceil(footprint.get(0).getAsDouble())),
                            Math.max(1, (int) Math.ceil(footprint.get(1).getAsDouble())),
                            Math.max(1, (int) Math.ceil(top))
                        });
                MODELS.put(id, Collections.unmodifiableList(faces));
            }
        } catch (IOException e) {
            throw new ExceptionInInitializerError(e);
        }
        DIMENSIONS.put("long_canopy",new int[]{1,3,1});
        MODELS.put("long_canopy",OpeningCanopyGeometry.faces(1,3,1,false));
    }

    public static Vec3d pose(Vec3d v, String kind, int width, double progress, boolean moving) {
        return pose(v,kind,width,progress,moving,false);
    }

    public static Vec3d pose(Vec3d v, String kind, int width, double progress, boolean moving, boolean slideForward) {
        // Widen the authored two-wide mounting rails while preserving their outer thickness.
        if(width>=3 && (kind.contains("slide") || kind.contains("hinge"))) {
            double x=v.x<=.125?v.x:v.x>=1.875?v.x+width-2:.125+(v.x-.125)*(width-.25)/1.75;
            v=new Vec3d(x,v.y,v.z);
        }
        if (!moving) return v;
        double p = progress * progress * (3 - 2 * progress);
        if (kind.contains("slide")) return v.addVector(0, Math.min(1,p*8)/16D, (slideForward?-2:2) * p);
        double a = Math.toRadians(105 * p), y = v.y - 1, z = v.z - 2;
        return new Vec3d(
                v.x, 1 + y * Math.cos(a) - z * Math.sin(a), 2 + y * Math.sin(a) + z * Math.cos(a));
    }

    /** Local +Z is rear, local +X is right; rotate around the placement cell center. */
    public static Vec3d rotate(Vec3d v, EnumFacing facing) {
        double x = v.x - .5, z = v.z - .5;
        switch (facing) {
            case SOUTH:
                return new Vec3d(.5 - x, v.y, .5 - z);
            case EAST:
                return new Vec3d(.5 - z, v.y, .5 + x);
            case WEST:
                return new Vec3d(.5 + z, v.y, .5 - x);
            default:
                return v;
        }
    }

    public static List<AxisAlignedBB> collision(
            String model,
            String kind,
            int width,
            double progress,
            EnumFacing facing,
            BlockPos pos) {
        return collision(MODELS.get(model),kind,width,progress,facing,pos);
    }
    public static List<AxisAlignedBB> collision(List<Face> faces,String kind,int width,double progress,EnumFacing facing,BlockPos pos) {
        List<AxisAlignedBB> boxes = new ArrayList<>();
        for (Face f : faces)
            for (int i = 1; i < f.vertices.length - 1; i++) {
                Vec3d
                        a =
                                rotate(
                                        pose(
                                                f.vertices[0],
                                                kind,
                                                width,
                                                progress,
                                                f.group.equals("shell")),
                                        facing),
                        b =
                                rotate(
                                        pose(
                                                f.vertices[i],
                                                kind,
                                                width,
                                                progress,
                                                f.group.equals("shell")),
                                        facing),
                        c =
                                rotate(
                                        pose(
                                                f.vertices[i + 1],
                                                kind,
                                                width,
                                                progress,
                                                f.group.equals("shell")),
                                        facing);
                subdivide(boxes, a, b, c, 3, pos);
            }
        return boxes;
    }

    /** Glazing and two-pixel edge rails provide hollow collision without bevel micro-boxes. */
    public static List<AxisAlignedBB> windowCollision(List<Face> faces,EnumFacing facing,BlockPos pos) {
        List<AxisAlignedBB> boxes=new ArrayList<>();
        Set<AxisAlignedBB> rails=new HashSet<>();
        for(Face face:faces)if(face.glazing || face.baseMaterial.equals("glass")) {
            Vec3d[] vertices=new Vec3d[face.vertices.length];
            for(int i=0;i<vertices.length;i++)vertices[i]=rotate(face.vertices[i],facing);
            for(int i=1;i<vertices.length-1;i++)subdivide(boxes,vertices[0],vertices[i],vertices[i+1],3,pos);
            for(int i=0;i<vertices.length;i++) {
                Vec3d a=vertices[i],b=vertices[(i+1)%vertices.length];
                int steps=Math.max(1,(int)Math.ceil(a.distanceTo(b)*8));
                for(int step=0;step<steps;step++) {
                    Vec3d from=a.add(b.subtract(a).scale(step/(double)steps));
                    Vec3d to=a.add(b.subtract(a).scale((step+1D)/steps));
                    rails.add(new AxisAlignedBB(from,to).grow(1D/16+.002).offset(pos));
                }
            }
        }
        boxes.addAll(rails);return boxes;
    }

    /** Inclusive segment broadphase; parallel and zero-length rays retain exact picking semantics. */
    public static boolean intersectsRay(AxisAlignedBB bounds,Vec3d start,Vec3d end){
        double low=0,high=1;
        for(int axis=0;axis<3;axis++){
            double from=axis==0?start.x:axis==1?start.y:start.z,to=axis==0?end.x:axis==1?end.y:end.z;
            double min=axis==0?bounds.minX:axis==1?bounds.minY:bounds.minZ,max=axis==0?bounds.maxX:axis==1?bounds.maxY:bounds.maxZ,delta=to-from;
            if(delta==0){if(from<min||from>max)return false;continue;}
            double a=(min-from)/delta,b=(max-from)/delta;if(a>b){double swap=a;a=b;b=swap;}low=Math.max(low,a);high=Math.min(high,b);if(low>high)return false;
        }
        return true;
    }

    /** Exact face picking avoids treating subdivided collision boxes as selectable pixels. */
    public static RayTraceResult trace(String model,String kind,int width,double progress,EnumFacing facing,BlockPos pos,Vec3d start,Vec3d end) {
        return trace(MODELS.get(model),kind,width,progress,facing,pos,start,end);
    }
    public static RayTraceResult trace(List<Face> faces,String kind,int width,double progress,EnumFacing facing,BlockPos pos,Vec3d start,Vec3d end) {
        Vec3d direction=end.subtract(start);double nearest=Double.POSITIVE_INFINITY;RayTraceResult result=null;
        for(Face face:faces) {
            Vec3d[] vertices=new Vec3d[face.vertices.length];
            for(int i=0;i<vertices.length;i++)vertices[i]=rotate(pose(face.vertices[i],kind,width,progress,face.group.equals("shell")),facing).add(new Vec3d(pos));
            for(int i=1;i<vertices.length-1;i++) {
                Vec3d a=vertices[0],edge1=vertices[i].subtract(a),edge2=vertices[i+1].subtract(a),cross=direction.crossProduct(edge2);
                double determinant=edge1.dotProduct(cross);if(Math.abs(determinant)<1e-10)continue;
                double inverse=1/determinant;Vec3d relative=start.subtract(a);
                double u=relative.dotProduct(cross)*inverse;if(u< -1e-8 || u>1+1e-8)continue;
                Vec3d q=relative.crossProduct(edge1);double v=direction.dotProduct(q)*inverse;
                if(v< -1e-8 || u+v>1+1e-8)continue;
                double distance=edge2.dotProduct(q)*inverse;
                if(distance<0 || distance>1 || distance>=nearest)continue;
                nearest=distance;Vec3d normal=edge1.crossProduct(edge2);
                if(normal.dotProduct(direction)>0)normal=normal.scale(-1);
                result=new RayTraceResult(start.add(direction.scale(distance)),EnumFacing.getFacingFromVector((float)normal.x,(float)normal.y,(float)normal.z),pos);
            }
        }
        return result;
    }

    private static void subdivide(List<AxisAlignedBB> boxes,Vec3d a,Vec3d b,Vec3d c,int depth,BlockPos pos){
        subdivide(boxes,a.x,a.y,a.z,b.x,b.y,b.z,c.x,c.y,c.z,depth,pos);
    }
    /** Keep the same dyadic triangles and bounds without temporary vectors or intermediate boxes. */
    private static void subdivide(List<AxisAlignedBB> boxes,double ax,double ay,double az,double bx,double by,double bz,double cx,double cy,double cz,int depth,BlockPos pos){
        double minX=Math.min(ax,Math.min(bx,cx)),minY=Math.min(ay,Math.min(by,cy)),minZ=Math.min(az,Math.min(bz,cz));
        double maxX=Math.max(ax,Math.max(bx,cx)),maxY=Math.max(ay,Math.max(by,cy)),maxZ=Math.max(az,Math.max(bz,cz));
        if(depth==0 || maxX-minX<.0001 || maxY-minY<.0001 || maxZ-minZ<.0001){
            // Match constructor.grow(.002).offset(pos) in precisely that arithmetic order.
            boxes.add(new AxisAlignedBB(minX-.002+pos.getX(),minY-.002+pos.getY(),minZ-.002+pos.getZ(),maxX+.002+pos.getX(),maxY+.002+pos.getY(),maxZ+.002+pos.getZ()));return;
        }
        double abx=(ax+bx)*.5,aby=(ay+by)*.5,abz=(az+bz)*.5,bcx=(bx+cx)*.5,bcy=(by+cy)*.5,bcz=(bz+cz)*.5,cax=(cx+ax)*.5,cay=(cy+ay)*.5,caz=(cz+az)*.5;
        subdivide(boxes,ax,ay,az,abx,aby,abz,cax,cay,caz,depth-1,pos);
        subdivide(boxes,abx,aby,abz,bx,by,bz,bcx,bcy,bcz,depth-1,pos);
        subdivide(boxes,cax,cay,caz,bcx,bcy,bcz,cx,cy,cz,depth-1,pos);
        subdivide(boxes,abx,aby,abz,bcx,bcy,bcz,cax,cay,caz,depth-1,pos);
    }

    static {DIMENSIONS.put("large_front_canopy_glass",new int[]{5,1,5});MODELS.put("large_front_canopy_glass",LargeCanopyGeometry.faces(5,new LargeCanopySettings()));}
    static {CanopyVariants.init();}
    private CanopyMesh() {}
}
