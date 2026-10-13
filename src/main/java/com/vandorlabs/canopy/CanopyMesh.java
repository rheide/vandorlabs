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
    }

    public static Vec3d pose(Vec3d v, String kind, int width, double progress, boolean moving) {
        return pose(v,kind,width,progress,moving,false);
    }

    public static Vec3d pose(Vec3d v, String kind, int width, double progress, boolean moving, boolean slideForward) {
        // Three constituents share the two-wide shell, retaining the authored outer rail thickness.
        if(width==3 && (kind.contains("slide") || kind.contains("hinge"))) {
            double x=v.x<=.125?v.x:v.x>=1.875?v.x+1:.125+(v.x-.125)*2.75/1.75;
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

    private static void subdivide(
            List<AxisAlignedBB> boxes, Vec3d a, Vec3d b, Vec3d c, int depth, BlockPos pos) {
        boolean flat =
                Math.max(a.x, Math.max(b.x, c.x)) - Math.min(a.x, Math.min(b.x, c.x)) < .0001
                        || Math.max(a.y, Math.max(b.y, c.y)) - Math.min(a.y, Math.min(b.y, c.y))
                                < .0001
                        || Math.max(a.z, Math.max(b.z, c.z)) - Math.min(a.z, Math.min(b.z, c.z))
                                < .0001;
        if (depth == 0 || flat) {
            boxes.add(
                    new AxisAlignedBB(
                                    Math.min(a.x, Math.min(b.x, c.x)),
                                    Math.min(a.y, Math.min(b.y, c.y)),
                                    Math.min(a.z, Math.min(b.z, c.z)),
                                    Math.max(a.x, Math.max(b.x, c.x)),
                                    Math.max(a.y, Math.max(b.y, c.y)),
                                    Math.max(a.z, Math.max(b.z, c.z)))
                            .grow(.002)
                            .offset(pos));
            return;
        }
        Vec3d ab = a.add(b).scale(.5), bc = b.add(c).scale(.5), ca = c.add(a).scale(.5);
        subdivide(boxes, a, ab, ca, depth - 1, pos);
        subdivide(boxes, ab, b, bc, depth - 1, pos);
        subdivide(boxes, ca, bc, c, depth - 1, pos);
        subdivide(boxes, ab, bc, ca, depth - 1, pos);
    }

    static {DIMENSIONS.put("large_front_canopy_glass",new int[]{5,1,5});MODELS.put("large_front_canopy_glass",LargeCanopyGeometry.faces(5,new LargeCanopySettings()));}
    static {CanopyVariants.init();}
    private CanopyMesh() {}
}
