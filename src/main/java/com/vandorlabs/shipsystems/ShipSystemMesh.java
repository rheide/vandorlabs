package com.vandorlabs.shipsystems;

import com.google.gson.*;
import com.vandorlabs.canopy.CanopyMesh;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.*;
import java.io.*;
import java.util.*;

/** Immutable authored faces and solid cuboids, shared by items and world rendering. */
public final class ShipSystemMesh {
    public static final Map<String, List<CanopyMesh.Face>> MODELS = new LinkedHashMap<>();
    public static final Map<String, int[]> DIMENSIONS = new LinkedHashMap<>();
    public static final Map<String, String> BLOCK_MODELS = new LinkedHashMap<>();
    public static final Set<String> MATERIALS = new LinkedHashSet<>();
    private static final Map<String, List<AxisAlignedBB>> BOXES = new HashMap<>();
    private static final Map<String,Geometry> ORIENTED = new java.util.concurrent.ConcurrentHashMap<>();
    private static final class Geometry {
        final List<AxisAlignedBB> boxes;
        final AxisAlignedBB bounds;
        final Map<BlockPos,List<AxisAlignedBB>> cells;
        Geometry(List<AxisAlignedBB> boxes,AxisAlignedBB bounds,Map<BlockPos,List<AxisAlignedBB>> cells) { this.boxes=boxes;this.bounds=bounds;this.cells=cells; }
    }
    private static void load(String resource) {
        try (InputStream in = ShipSystemMesh.class.getResourceAsStream("/assets/vandorlabs/data/" + resource)) {
            for (JsonElement entry : new JsonParser().parse(new InputStreamReader(in, "UTF-8")).getAsJsonArray()) {
                JsonObject model = entry.getAsJsonObject();
                String id = model.get("id").getAsString();
                if (model.has("block_id")) BLOCK_MODELS.put(model.get("block_id").getAsString(), id);
                else if (!model.has("active")) BLOCK_MODELS.put(id, id);
                JsonArray size = model.getAsJsonArray("occupancy_xyz");
                DIMENSIONS.put(id, new int[]{size.get(0).getAsInt(), size.get(2).getAsInt(), size.get(1).getAsInt()});
                List<CanopyMesh.Face> faces = new ArrayList<>();
                for (JsonElement element : model.getAsJsonArray("faces")) {
                    JsonObject f = element.getAsJsonObject();
                    CanopyMesh.Face face = new CanopyMesh.Face();
                    JsonArray vertices = f.getAsJsonArray("v"), uv = f.getAsJsonArray("uv");
                    face.vertices = new Vec3d[vertices.size()];
                    face.uv = new double[vertices.size()][2];
                    for (int i = 0; i < vertices.size(); i++) {
                        JsonArray v = vertices.get(i).getAsJsonArray(), u = uv.get(i).getAsJsonArray();
                        face.vertices[i] = new Vec3d(v.get(0).getAsDouble(), v.get(1).getAsDouble(), v.get(2).getAsDouble());
                        face.uv[i] = new double[]{u.get(0).getAsDouble(), u.get(1).getAsDouble()};
                    }
                    face.material = f.get("material").getAsString();
                    face.baseMaterial = f.has("base_material") ? f.get("base_material").getAsString() : face.material.substring(face.material.lastIndexOf('/') + 1);
                    MATERIALS.add(face.material);
                    face.group = f.has("part") ? f.get("part").getAsString() : "fixed";
                    face.doubleSided = f.get("double").getAsBoolean();
                    faces.add(face);
                }
                MODELS.put(id, Collections.unmodifiableList(faces));
                List<AxisAlignedBB> boxes = new ArrayList<>();
                for (JsonElement element : model.getAsJsonArray("collision")) {
                    JsonArray b = element.getAsJsonArray();
                    boxes.add(new AxisAlignedBB(b.get(0).getAsDouble(), b.get(1).getAsDouble(), b.get(2).getAsDouble(), b.get(3).getAsDouble(), b.get(4).getAsDouble(), b.get(5).getAsDouble()));
                }
                BOXES.put(id, Collections.unmodifiableList(boxes));
            }
        } catch (IOException e) { throw new ExceptionInInitializerError(e); }
    }
    public static List<AxisAlignedBB> boxes(String id, EnumFacing facing, BlockPos origin) {
        return boxes(id, facing, EnumFacing.UP, origin);
    }
    private static Geometry geometry(String id,EnumFacing facing,EnumFacing mount) {
        return ORIENTED.computeIfAbsent(id+":"+facing+":"+mount,key->{
            List<AxisAlignedBB> boxes=new ArrayList<>();MountFrame frame=new MountFrame(facing,mount);
            AxisAlignedBB bounds=null;Map<BlockPos,List<AxisAlignedBB>> cells=new HashMap<>();
            for(AxisAlignedBB authored:BOXES.get(id)) {
                Vec3d a=frame.point(new Vec3d(authored.minX,authored.minY,authored.minZ)),b=frame.point(new Vec3d(authored.maxX,authored.maxY,authored.maxZ));
                AxisAlignedBB box=new AxisAlignedBB(a,b);boxes.add(box);bounds=bounds==null?box:bounds.union(box);
                for(int x=MathHelper.floor(box.minX);x<MathHelper.ceil(box.maxX);x++)
                    for(int y=MathHelper.floor(box.minY);y<MathHelper.ceil(box.maxY);y++)
                        for(int z=MathHelper.floor(box.minZ);z<MathHelper.ceil(box.maxZ);z++) {
                            BlockPos cell=new BlockPos(x,y,z);AxisAlignedBB region=new AxisAlignedBB(cell);
                            if(box.intersects(region))cells.computeIfAbsent(cell,ignored->new ArrayList<>()).add(box.intersect(region));
                        }
            }
            Map<BlockPos,List<AxisAlignedBB>> immutable=new HashMap<>();for(Map.Entry<BlockPos,List<AxisAlignedBB>> cell:cells.entrySet())immutable.put(cell.getKey(),Collections.unmodifiableList(cell.getValue()));
            return new Geometry(Collections.unmodifiableList(boxes),bounds==null?new AxisAlignedBB(BlockPos.ORIGIN):bounds,Collections.unmodifiableMap(immutable));
        });
    }
    public static List<AxisAlignedBB> boxes(String id,EnumFacing facing,EnumFacing mount,BlockPos origin) {
        List<AxisAlignedBB> result=new ArrayList<>();for(AxisAlignedBB box:geometry(id,facing,mount).boxes)result.add(box.offset(origin));return result;
    }
    public static List<AxisAlignedBB> cellBoxes(String id,EnumFacing facing,EnumFacing mount,BlockPos offset) {
        return geometry(id,facing,mount).cells.getOrDefault(offset,Collections.emptyList());
    }
    public static AxisAlignedBB bounds(String id,EnumFacing facing,EnumFacing mount,BlockPos origin) {
        return geometry(id,facing,mount).bounds.offset(origin);
    }
    public static int authoredBoxCount(String id) { return BOXES.get(id).size(); }
    private ShipSystemMesh() {}
}
