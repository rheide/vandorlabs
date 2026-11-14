package com.vandorlabs.dynmap;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.util.math.Vec3d;
import org.dynmap.renderer.CustomRenderer;
import org.dynmap.renderer.MapDataContext;
import org.dynmap.renderer.RenderPatch;
import org.dynmap.renderer.RenderPatchFactory;

/** Read the actual rack models once; share all 96 settled facing/occupancy meshes. */
public final class ModuleRackRenderer extends CustomRenderer {
    private static final String[] FIELDS = {"ModuleMask"};
    private static final List<String> TEXTURES = Collections.unmodifiableList(Arrays.asList(
            "housing", "module", "trim", "dark", "indicators", "contacts"));
    private static final class Face {
        final int texture;
        final List<MapSurface.Vertex> vertices;
        Face(int texture, List<MapSurface.Vertex> vertices) {
            this.texture = texture;
            this.vertices = Collections.unmodifiableList(vertices);
        }
    }
    private static final List<Face> SHELL = read("shell");
    private static final List<List<Face>> SLOTS = slots("slot_seated_");
    private static final List<List<Face>> EMPTY_SLOTS = slots("slot_empty_");
    private static final List<List<Face>> INDICATORS = indicators();
    private final RenderPatch[][][] cache = new RenderPatch[6][16][];
    private RenderPatchFactory factory;

    @Override public String[] getTileEntityFieldsNeeded() { return FIELDS; }
    @Override protected int getMaximumTextureCount() { return TEXTURES.size(); }

    @Override public synchronized RenderPatch[] getRenderPatchList(MapDataContext context) {
        if (factory != context.getPatchFactory()) {
            factory = context.getPatchFactory();
            for (RenderPatch[][] facing : cache) Arrays.fill(facing, null);
        }
        int orientation = context.getBlockType().isStateMatch("facing", "up") ? 4
                : context.getBlockType().isStateMatch("facing", "down") ? 5
                : context.getBlockType().isStateMatch("facing", "west") ? 1
                : context.getBlockType().isStateMatch("facing", "north") ? 2
                : context.getBlockType().isStateMatch("facing", "east") ? 3 : 0;
        Object value = context.getBlockTileEntityField("ModuleMask");
        int mask = value instanceof Number ? ((Number) value).intValue() & 15 : 0;
        RenderPatch[] prepared = cache[orientation][mask];
        if (prepared != null) return prepared;
        List<RenderPatch> patches = new ArrayList<>();
        emit(context, patches, SHELL, orientation);
        for (int slot = 0; slot < 4; slot++) {
            boolean occupied = (mask & (1 << slot)) != 0;
            emit(context, patches, INDICATORS.get(slot * 2 + (occupied ? 1 : 0)), orientation);
            emit(context, patches, (occupied ? SLOTS : EMPTY_SLOTS).get(slot), orientation);
        }
        prepared = patches.toArray(new RenderPatch[0]);
        cache[orientation][mask] = prepared;
        return prepared;
    }

    private static void emit(MapDataContext context, List<RenderPatch> out,
            List<Face> faces, int orientation) {
        for (Face face : faces) {
            List<MapSurface.Vertex> rotated = new ArrayList<>(4);
            for (MapSurface.Vertex vertex : face.vertices) {
                double x = vertex.p.x, y = vertex.p.y, z = vertex.p.z;
                if (orientation == 4) {
                    y = vertex.p.z;
                    z = 1 - vertex.p.y;
                } else if (orientation == 5) {
                    y = 1 - vertex.p.z;
                    z = vertex.p.y;
                } else {
                    for (int turn = 0; turn < orientation; turn++) {
                        double oldX = x;
                        x = 1 - z;
                        z = oldX;
                    }
                }
                rotated.add(new MapSurface.Vertex(new Vec3d(x, y, z), vertex.u, vertex.v));
            }
            MapSurface.emit(context, out, rotated, face.texture);
        }
    }

    private static List<List<Face>> slots(String prefix) {
        List<List<Face>> result = new ArrayList<>(4);
        for (int slot = 0; slot < 4; slot++) result.add(read(prefix + slot));
        return Collections.unmodifiableList(result);
    }

    private static List<List<Face>> indicators() {
        List<List<Face>> result = new ArrayList<>(8);
        for (int slot = 0; slot < 4; slot++) {
            result.add(read("indicator_" + slot + "_off"));
            result.add(read("indicator_" + slot + "_on"));
        }
        return Collections.unmodifiableList(result);
    }

    private static double[] numbers(JsonArray source) {
        double[] result = new double[source.size()];
        for (int i = 0; i < result.length; i++) result[i] = source.get(i).getAsDouble();
        return result;
    }

    private static final class ModelData {
        final JsonArray elements;
        final Map<String, String> textures;
        ModelData(JsonArray elements, Map<String, String> textures) {
            this.elements = elements;
            this.textures = textures;
        }
    }

    /** JAR staging shares geometry through parent templates. Resolve those
     * templates with child texture overrides just as Minecraft does.
     */
    private static ModelData model(String resource, int depth) throws IOException {
        if (depth >= 16) throw new IOException("Rack model parent chain is too deep: " + resource);
        int separator = resource.indexOf(':');
        String namespace = separator < 0 ? "minecraft" : resource.substring(0, separator);
        String name = separator < 0 ? resource : resource.substring(separator + 1);
        String path = "/assets/" + namespace + "/models/" + name + ".json";
        try (InputStream input = ModuleRackRenderer.class.getResourceAsStream(path)) {
            if (input == null) throw new IOException("Missing rack model: " + path);
            JsonObject source = new JsonParser().parse(new InputStreamReader(input,
                    StandardCharsets.UTF_8)).getAsJsonObject();
            ModelData parent = source.has("parent") ? model(source.get("parent").getAsString(), depth + 1) : null;
            Map<String, String> textures = new LinkedHashMap<>();
            if (parent != null) textures.putAll(parent.textures);
            if (source.has("textures"))
                for (Map.Entry<String, JsonElement> texture : source.getAsJsonObject("textures").entrySet())
                    textures.put(texture.getKey(), texture.getValue().getAsString());
            JsonArray elements = source.has("elements") ? source.getAsJsonArray("elements")
                    : parent == null ? null : parent.elements;
            if (elements == null) throw new IOException("Rack model has no geometry: " + resource);
            return new ModelData(elements, textures);
        }
    }

    /** Vanilla BlockPart defaults when a cuboid face has no explicit UVs. */
    private static double[] defaultUvs(String side, double[] lo, double[] hi) throws IOException {
        switch (side) {
            case "down": return new double[]{lo[0], 16-hi[2], hi[0], 16-lo[2]};
            case "up": return new double[]{lo[0], lo[2], hi[0], hi[2]};
            case "north": return new double[]{16-hi[0], 16-hi[1], 16-lo[0], 16-lo[1]};
            case "south": return new double[]{lo[0], 16-hi[1], hi[0], 16-lo[1]};
            case "west": return new double[]{lo[2], 16-hi[1], hi[2], 16-lo[1]};
            case "east": return new double[]{16-hi[2], 16-hi[1], 16-lo[2], 16-lo[1]};
            default: throw new IOException("Unknown rack face: " + side);
        }
    }

    private static List<Face> read(String name) {
        try {
            ModelData model = model("vandorlabs:block/module_rack/" + name, 0);
            List<Face> result = new ArrayList<>();
            for (JsonElement value : model.elements) {
                JsonObject element = value.getAsJsonObject();
                if (element.has("rotation")) throw new IOException("Rack model needs rotated-element support");
                double[] lo = numbers(element.getAsJsonArray("from"));
                double[] hi = numbers(element.getAsJsonArray("to"));
                for (Map.Entry<String, JsonElement> entry : element.getAsJsonObject("faces").entrySet()) {
                    JsonObject face = entry.getValue().getAsJsonObject();
                    String texture = face.get("texture").getAsString();
                    int aliases = 0;
                    while (texture.startsWith("#") && aliases++ < 16) {
                        String resolved = model.textures.get(texture.substring(1));
                        if (resolved == null) throw new IOException("Missing rack texture alias: " + texture);
                        texture = resolved;
                    }
                    int index = TEXTURES.indexOf(texture.substring(texture.lastIndexOf('/') + 1));
                    if (index < 0) throw new IOException("Unknown rack texture: " + texture);
                    double[][] points;
                    switch (entry.getKey()) {
                        case "down": points = new double[][]{{lo[0],lo[1],hi[2]},{lo[0],lo[1],lo[2]},
                                {hi[0],lo[1],lo[2]},{hi[0],lo[1],hi[2]}}; break;
                        case "up": points = new double[][]{{lo[0],hi[1],lo[2]},{lo[0],hi[1],hi[2]},
                                {hi[0],hi[1],hi[2]},{hi[0],hi[1],lo[2]}}; break;
                        case "north": points = new double[][]{{hi[0],hi[1],lo[2]},{hi[0],lo[1],lo[2]},
                                {lo[0],lo[1],lo[2]},{lo[0],hi[1],lo[2]}}; break;
                        case "south": points = new double[][]{{lo[0],hi[1],hi[2]},{lo[0],lo[1],hi[2]},
                                {hi[0],lo[1],hi[2]},{hi[0],hi[1],hi[2]}}; break;
                        case "west": points = new double[][]{{lo[0],hi[1],lo[2]},{lo[0],lo[1],lo[2]},
                                {lo[0],lo[1],hi[2]},{lo[0],hi[1],hi[2]}}; break;
                        case "east": points = new double[][]{{hi[0],hi[1],hi[2]},{hi[0],lo[1],hi[2]},
                                {hi[0],lo[1],lo[2]},{hi[0],hi[1],lo[2]}}; break;
                        default: throw new IOException("Unknown rack face: " + entry.getKey());
                    }
                    double[] uv = face.has("uv") ? numbers(face.getAsJsonArray("uv"))
                            : defaultUvs(entry.getKey(), lo, hi);
                    double[][] corners = {{uv[0],uv[1]},{uv[0],uv[3]},{uv[2],uv[3]},{uv[2],uv[1]}};
                    int rotation = face.has("rotation") ? face.get("rotation").getAsInt() / 90 : 0;
                    List<MapSurface.Vertex> vertices = new ArrayList<>(4);
                    for (int i = 0; i < 4; i++) {
                        double[] p = points[i], t = corners[Math.floorMod(i + rotation, 4)];
                        vertices.add(new MapSurface.Vertex(new Vec3d(p[0]/16, p[1]/16, p[2]/16), t[0]/16, t[1]/16));
                    }
                    result.add(new Face(index, vertices));
                }
            }
            return Collections.unmodifiableList(result);
        } catch (IOException | RuntimeException failure) {
            throw new IllegalStateException("Cannot prepare Module Rack map geometry", failure);
        }
    }
}
