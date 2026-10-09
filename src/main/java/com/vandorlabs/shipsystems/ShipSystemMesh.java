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
    private static final Map<String, List<AxisAlignedBB>> BOXES = new HashMap<>();
    static {
        try (InputStream in = ShipSystemMesh.class.getResourceAsStream("/assets/vandorlabs/data/ship_system_meshes.json")) {
            for (JsonElement entry : new JsonParser().parse(new InputStreamReader(in, "UTF-8")).getAsJsonArray()) {
                JsonObject model = entry.getAsJsonObject();
                String id = model.get("id").getAsString();
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
                    face.group = "fixed";
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
        List<AxisAlignedBB> result = new ArrayList<>();
        for (AxisAlignedBB b : BOXES.get(id)) {
            Vec3d a = CanopyMesh.rotate(new Vec3d(b.minX, b.minY, b.minZ), facing);
            Vec3d c = CanopyMesh.rotate(new Vec3d(b.maxX, b.maxY, b.maxZ), facing);
            result.add(new AxisAlignedBB(a, c).offset(origin));
        }
        return result;
    }
    private ShipSystemMesh() {}
}
