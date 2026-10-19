package com.vandorlabs.client.constructors;

import com.google.common.collect.ImmutableMap;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.IBakedModel;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.client.renderer.vertex.VertexFormat;
import net.minecraft.client.renderer.vertex.VertexFormatElement;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.event.ModelBakeEvent;
import net.minecraftforge.client.event.TextureStitchEvent;
import net.minecraftforge.client.model.IModel;
import net.minecraftforge.client.model.ModelLoaderRegistry;
import net.minecraftforge.client.model.obj.OBJLoader;
import net.minecraftforge.client.model.pipeline.LightUtil;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/**
 * Integration example for Forge 1.12.2, Java 8. It does not register blocks or TEs.
 * Invoke preInit() once from your client proxy during FMLPreInitializationEvent.
 * Register extra part paths before the first TextureStitchEvent.Pre.
 * This cache is rebuilt on every resource reload, including F3+T.
 */
@SideOnly(Side.CLIENT)
public final class UniversalConstructorModelCache {
    private static final String DOMAIN = "vandorlabs";
    private static final UniversalConstructorModelCache EVENTS = new UniversalConstructorModelCache();
    private static final Map<String, ResourceLocation> LOCATIONS = new LinkedHashMap<>();
    private static final Map<String, IModel> UNBAKED = new LinkedHashMap<>();
    private static Map<String, Mesh> meshes = Collections.emptyMap();
    private static boolean installed;

    private UniversalConstructorModelCache() { }

    public static void preInit() {
        if (installed) return;
        installed = true;
        OBJLoader.INSTANCE.addDomain(DOMAIN);
        for (String id : new String[] {"assembly_press", "synthesis_tower", "enclosed_gantry"}) {
            register(id, "complete.obj");
        }
        MinecraftForge.EVENT_BUS.register(EVENTS);
    }

    /** Example: register("assembly_press", "parts/press.obj"). */
    public static void register(String machineId, String relativeObjPath) {
        LOCATIONS.put(key(machineId, relativeObjPath),
            new ResourceLocation(DOMAIN, "block/universal_constructor/" + machineId + "/" + relativeObjPath));
    }

    public static Mesh get(String machineId, String relativeObjPath) {
        Mesh mesh = meshes.get(key(machineId, relativeObjPath));
        if (mesh == null) {
            throw new IllegalStateException("Model not registered/baked: " + key(machineId, relativeObjPath));
        }
        return mesh;
    }

    public static boolean isReady() {
        return !meshes.isEmpty();
    }

    private static String key(String machineId, String path) {
        return machineId + "/" + path;
    }

    @SubscribeEvent
    public void beforeTextureStitch(TextureStitchEvent.Pre event) {
        if (event.getMap() != Minecraft.getMinecraft().getTextureMapBlocks()) return;
        UNBAKED.clear();
        meshes = Collections.emptyMap();
        for (Map.Entry<String, ResourceLocation> entry : LOCATIONS.entrySet()) {
            try {
                // Java IModel.process values are strings; blockstate JSON uses booleans.
                IModel model = ModelLoaderRegistry.getModel(entry.getValue())
                    .process(ImmutableMap.of("flip-v", "true", "ambient", "false"));
                UNBAKED.put(entry.getKey(), model);
                for (ResourceLocation texture : model.getTextures()) event.getMap().registerSprite(texture);
            } catch (Exception e) {
                throw new IllegalStateException("Cannot load constructor asset " + entry.getValue(), e);
            }
        }
    }

    @SubscribeEvent
    public void afterModelBake(ModelBakeEvent event) {
        Map<String, Mesh> next = new LinkedHashMap<>();
        for (Map.Entry<String, IModel> entry : UNBAKED.entrySet()) {
            IModel source = entry.getValue();
            IBakedModel baked = source.bake(source.getDefaultState(), DefaultVertexFormats.ITEM,
                location -> Minecraft.getMinecraft().getTextureMapBlocks().getAtlasSprite(location.toString()));
            next.put(entry.getKey(), Mesh.fromObj(baked));
        }
        meshes = Collections.unmodifiableMap(next);
    }

    /**
     * Immutable CPU-side vertex cache with atlas UVs. Only translations and the
     * machine's four horizontal facings are applied during frame rendering.
     * No per-frame OBJ parsing, rebaking, texture binding, or OpenGL state changes.
     */
    public static final class Mesh {
        private final Face[] faces;

        private Mesh(Face[] faces) { this.faces = faces; }

        private static Mesh fromObj(IBakedModel model) {
            List<Face> result = new ArrayList<>();
            // Forge 1.12.x's OBJ model returns all faces for side == null.
            for (BakedQuad quad : model.getQuads(null, null, 0L)) {
                VertexFormat format = quad.getFormat();
                int position = find(format, VertexFormatElement.EnumUsage.POSITION, 0);
                int color = find(format, VertexFormatElement.EnumUsage.COLOR, 0);
                int uv = find(format, VertexFormatElement.EnumUsage.UV, 0);
                float[] data = new float[4 * 9]; // per vertex: x y z r g b a u v
                float[] temp = new float[4];
                for (int vertex = 0; vertex < 4; vertex++) {
                    int offset = vertex * 9;
                    LightUtil.unpack(quad.getVertexData(), temp, format, vertex, position);
                    System.arraycopy(temp, 0, data, offset, 3);
                    LightUtil.unpack(quad.getVertexData(), temp, format, vertex, color);
                    System.arraycopy(temp, 0, data, offset + 3, 4);
                    LightUtil.unpack(quad.getVertexData(), temp, format, vertex, uv);
                    System.arraycopy(temp, 0, data, offset + 7, 2);
                }
                result.add(new Face(data, quad.getFace(), quad.shouldApplyDiffuseLighting()));
            }
            return new Mesh(result.toArray(new Face[0]));
        }

        private static int find(VertexFormat format, VertexFormatElement.EnumUsage usage, int index) {
            for (int i = 0; i < format.getElementCount(); i++) {
                VertexFormatElement element = format.getElement(i);
                if (element.getUsage() == usage && element.getIndex() == index) return i;
            }
            throw new IllegalArgumentException("OBJ vertex format lacks " + usage + "/" + index);
        }

        public int getQuadCount() { return faces.length; }

        /**
         * originX/Y/Z are camera-relative coordinates of the minimum corner of
         * the whole, rotated footprint. width/depth are UNROTATED block dimensions.
         * Offsets are in unrotated model-local axes and are applied before facing.
         * packedLight is World.getCombinedLight(...), supplied by the host renderer.
         */
        public void emit(BufferBuilder buffer, double originX, double originY, double originZ,
                         EnumFacing facing, double width, double depth,
                         double offsetX, double offsetY, double offsetZ, int packedLight) {
            if (facing.getAxis().isVertical()) throw new IllegalArgumentException("Horizontal facing required");
            int blockLight = packedLight & 0xFFFF;
            int skyLight = (packedLight >>> 16) & 0xFFFF;
            for (Face face : faces) {
                EnumFacing litSide = rotate(face.side, facing);
                float shade = face.diffuse ? LightUtil.diffuseLight(litSide) : 1.0F;
                for (int v = 0; v < 4; v++) {
                    int i = v * 9;
                    double px = face.data[i] + offsetX;
                    double py = face.data[i + 1] + offsetY;
                    double pz = face.data[i + 2] + offsetZ;
                    double rx, rz;
                    switch (facing) {
                        case EAST: rx = depth - pz; rz = px; break;
                        case SOUTH: rx = width - px; rz = depth - pz; break;
                        case WEST: rx = pz; rz = width - px; break;
                        default: rx = px; rz = pz; break; // NORTH
                    }
                    buffer.pos(originX + rx, originY + py, originZ + rz)
                        .color(face.data[i + 3] * shade, face.data[i + 4] * shade,
                               face.data[i + 5] * shade, face.data[i + 6])
                        .tex(face.data[i + 7], face.data[i + 8])
                        .lightmap(blockLight, skyLight).endVertex();
                }
            }
        }

        private static EnumFacing rotate(EnumFacing side, EnumFacing machineFacing) {
            if (side.getAxis().isVertical()) return side;
            switch (machineFacing) {
                case EAST: return side.rotateY();
                case SOUTH: return side.getOpposite();
                case WEST: return side.rotateYCCW();
                default: return side;
            }
        }
    }

    private static final class Face {
        final float[] data;
        final EnumFacing side;
        final boolean diffuse;
        Face(float[] data, EnumFacing side, boolean diffuse) {
            this.data = data; this.side = side; this.diffuse = diffuse;
        }
    }
}
