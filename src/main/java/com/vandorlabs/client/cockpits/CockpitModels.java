package com.vandorlabs.client.cockpits;

import com.google.gson.Gson;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.model.IBakedModel;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.client.resources.IResource;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.event.ModelBakeEvent;
import net.minecraftforge.client.event.TextureStitchEvent;
import net.minecraftforge.client.model.IModel;
import net.minecraftforge.client.model.ModelLoaderRegistry;
import net.minecraftforge.client.model.obj.OBJLoader;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/**
 * Reference integration for Forge 1.12.2. Call install() from CLIENT preInit.
 * This owns a TESR model cache; it does not register blocks, items or a mod.
 */
@SideOnly(Side.CLIENT)
public final class CockpitModels {
    public static final String DOMAIN = "vandorlabs";
    private static final String[] IDS = {
        "cockpit_a", "cockpit_b", "chair_siderests", "yoke_screen"
    };
    private static final CockpitModels INSTANCE = new CockpitModels();
    private static final Gson GSON = new Gson();
    private static final Map<String, Rig> RIGS = new LinkedHashMap<>();
    private static final Map<ResourceLocation, IModel> RAW = new LinkedHashMap<>();
    private static final Map<ResourceLocation, IBakedModel> BAKED = new LinkedHashMap<>();
    private static final Map<String,IBakedModel> SKINS=new LinkedHashMap<String,IBakedModel>(16,.75F,true){
        @Override protected boolean removeEldestEntry(Map.Entry<String,IBakedModel> entry){return size()>64;}
    };
    private static boolean installed;

    private CockpitModels() {}

    public static void install() {
        if (installed) return;
        OBJLoader.INSTANCE.addDomain(DOMAIN);
        MinecraftForge.EVENT_BUS.register(INSTANCE);
        installed = true;
    }

    public static boolean ready() {
        return !BAKED.isEmpty();
    }

    public static Rig rig(String id) {
        Rig rig = RIGS.get(id);
        if (rig == null) throw new IllegalArgumentException("Unknown/unloaded cockpit: " + id);
        return rig;
    }

    public static IBakedModel baked(ResourceLocation id) {
        IBakedModel model = BAKED.get(id);
        if (model == null) throw new IllegalStateException("Cockpit model is not baked: " + id);
        return model;
    }
    public static IBakedModel baked(ResourceLocation id,int skin){
        if(skin<0)return baked(id);
        String key=id+"/"+skin;
        return SKINS.computeIfAbsent(key,k->{
            String texture=com.vandorlabs.tiles.ScreenHousingTextures.fullTexture(skin);
            IModel raw=RAW.get(id).retexture(com.google.common.collect.ImmutableMap.of("#sidewall",texture));
            return raw.bake(raw.getDefaultState(),DefaultVertexFormats.ITEM,location->Minecraft.getMinecraft().getTextureMapBlocks().getAtlasSprite(location.toString()));
        });
    }

    /** Reloads both metadata and models BEFORE atlas stitching, including F3+T. */
    @SubscribeEvent
    public void beforeStitch(TextureStitchEvent.Pre event) {
        Minecraft mc = Minecraft.getMinecraft();
        if (event.getMap() != mc.getTextureMapBlocks()) return;
        RIGS.clear();
        RAW.clear();
        BAKED.clear();
        for (String id : IDS) {
            ResourceLocation location = new ResourceLocation(DOMAIN, "cockpits/" + id + "/rig.json");
            try (IResource resource = mc.getResourceManager().getResource(location);
                 Reader reader = new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8)) {
                Rig rig = GSON.fromJson(reader, Rig.class);
                if (rig == null) throw new IllegalArgumentException("Empty rig");
                rig.prepare(id);
                RIGS.put(id, rig);
                for (ResourceLocation modelLocation : rig.allModels()) {
                    if (RAW.containsKey(modelLocation)) continue;
                    // Plain ResourceLocation: no models/ prefix and no #variant.
                    IModel model = ModelLoaderRegistry.getModel(modelLocation);
                    RAW.put(modelLocation, model);
                    for (ResourceLocation texture : model.getTextures()) {
                        event.getMap().registerSprite(texture);
                    }
                }
            } catch (Exception exception) {
                throw new IllegalStateException("Unable to load cockpit rig " + location, exception);
            }
        }
    }

    /** Bake AFTER the atlas exists; never bake a model during a render frame. */
    @SubscribeEvent
    public void afterBake(ModelBakeEvent event) {
        BAKED.clear();
        SKINS.clear();
        Minecraft mc = Minecraft.getMinecraft();
        for (Map.Entry<ResourceLocation, IModel> entry : RAW.entrySet()) {
            IModel raw = entry.getValue();
            BAKED.put(entry.getKey(), raw.bake(raw.getDefaultState(), DefaultVertexFormats.ITEM,
                texture -> mc.getTextureMapBlocks().getAtlasSprite(texture.toString())));
        }
    }

    /** Gson field names deliberately match rig.json. Values are read-only after load. */
    public static final class Rig {
        public int schema_version;
        public String id;
        public int units_per_block;
        public String static_model;
        public Map<String, String> poses = new LinkedHashMap<>();
        public double[] render_bounds_blocks;
        public List<Part> parts = new ArrayList<>();
        public transient ResourceLocation staticResource;
        public transient Map<String, ResourceLocation> poseResources;

        private void prepare(String expectedId) {
            if (schema_version != 1 || units_per_block != 16 || !expectedId.equals(id)) {
                throw new IllegalArgumentException("Unexpected rig schema or ID");
            }
            checkVector(render_bounds_blocks, 6, "render_bounds_blocks");
            for (int axis = 0; axis < 3; axis++) {
                if (render_bounds_blocks[axis] >= render_bounds_blocks[axis + 3]) {
                    throw new IllegalArgumentException("Empty render bounds");
                }
            }
            staticResource = new ResourceLocation(static_model);
            poseResources = new LinkedHashMap<>();
            for (Map.Entry<String, String> pose : poses.entrySet()) {
                poseResources.put(pose.getKey(), new ResourceLocation(pose.getValue()));
            }
            Set<String> names = new LinkedHashSet<>();
            for (Part part : parts) {
                if (part.id == null || !names.add(part.id)) {
                    throw new IllegalArgumentException("Missing/duplicate part ID");
                }
                part.prepare();
            }
        }

        private Set<ResourceLocation> allModels() {
            Set<ResourceLocation> locations = new LinkedHashSet<>();
            locations.add(staticResource);
            locations.addAll(poseResources.values());
            for (Part part : parts) locations.add(part.modelResource);
            return locations;
        }
    }

    public static final class Part {
        public String id;
        public String model;
        public String type;
        public double[] pivot;
        public double[] axis;
        public double[] values;
        public transient ResourceLocation modelResource;

        private void prepare() {
            modelResource = new ResourceLocation(model);
            checkVector(pivot, 3, "pivot");
            checkVector(axis, 3, "axis");
            checkVector(values, 4, "values");
            double lengthSquared = axis[0]*axis[0] + axis[1]*axis[1] + axis[2]*axis[2];
            if (Math.abs(lengthSquared - 1.0) > 0.00001) {
                throw new IllegalArgumentException("Part axis must have unit length");
            }
            if (!"rotation".equals(type) && !"translation".equals(type)) {
                throw new IllegalArgumentException("Unsupported part transform: " + type);
            }
        }
    }

    private static void checkVector(double[] vector, int length, String field) {
        if (vector == null || vector.length != length) {
            throw new IllegalArgumentException("Invalid " + field);
        }
        for (double value : vector) {
            if (!Double.isFinite(value)) throw new IllegalArgumentException("Non-finite " + field);
        }
    }
}
