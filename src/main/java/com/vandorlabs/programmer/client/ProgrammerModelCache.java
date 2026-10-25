package com.vandorlabs.programmer.client;

import com.vandorlabs.programmer.*;

import com.google.common.collect.ImmutableMap;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.IBakedModel;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.client.resources.IResource;
import net.minecraft.util.EnumFacing;
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

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Client-only manifest-driven cache. Register during client preInit. */
@SideOnly(Side.CLIENT)
public final class ProgrammerModelCache {
    public static final ProgrammerModelCache INSTANCE = new ProgrammerModelCache();
    public static final String NAMESPACE = "vandorlabs";
    private final Map<String, IModel> unbaked = new LinkedHashMap<>();
    private final Map<String, Mesh> meshes = new LinkedHashMap<>();
    private Definition definition;
    private boolean registered;
    private boolean ready;
    private IModel itemModel;
    public net.minecraft.client.renderer.vertex.VertexFormat format;

    private ProgrammerModelCache() { }

    public void register() {
        if (registered) return;
        registered = true;
        OBJLoader.INSTANCE.addDomain(NAMESPACE);
        MinecraftForge.EVENT_BUS.register(this);
    }

    public boolean isReady() { return ready; }

    public Definition definition() {
        if (definition == null) throw new IllegalStateException("Programmer model has not loaded");
        return definition;
    }

    public Mesh mesh(ProgrammerStatus activity, String part) {
        Mesh result = meshes.get(key(activity.resourceName, part));
        if (result == null) throw new IllegalArgumentException("Missing Programmer mesh: "
                + key(activity.resourceName, part));
        return result;
    }

    private static String key(String activity, String part) { return activity + "/" + part; }

    @SubscribeEvent
    public void beforeStitch(TextureStitchEvent.Pre event) {
        if (event.getMap() != Minecraft.getMinecraft().getTextureMapBlocks()) return;
        ready = false;
        definition = null; unbaked.clear(); meshes.clear();
        try {
            JsonObject manifest;
            ResourceLocation location = new ResourceLocation(NAMESPACE, "data_chip_programmer/data_chip_programmer.json");
            try (IResource resource = Minecraft.getMinecraft().getResourceManager().getResource(location);
                 InputStreamReader reader = new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8)) {
                manifest = new JsonParser().parse(reader).getAsJsonObject();
            }
            definition = new Definition(manifest);
            itemModel=ModelLoaderRegistry.getModel(new ResourceLocation("vandorlabs:block/data_chip_programmer/ready/complete.obj")).process(ImmutableMap.of("flip-v","true"));
            for(ResourceLocation texture:itemModel.getTextures())event.getMap().registerSprite(texture);
            JsonObject states = manifest.getAsJsonObject("states");
            for (ProgrammerStatus state : ProgrammerStatus.values()) {
                JsonObject parts = states.getAsJsonObject(state.resourceName).getAsJsonObject("parts");
                if (parts.entrySet().size() != definition.parts.size())
                    throw new IllegalArgumentException("Inconsistent programmer state parts: " + state);
                for (Part required : definition.parts)
                    if (!parts.has(required.name)) throw new IllegalArgumentException("Missing part: " + required.name);
                for (Map.Entry<String, JsonElement> entry : parts.entrySet()) {
                    String path = entry.getValue().getAsJsonObject().get("obj_resource").getAsString();
                    // Direct loader locations include block/, unlike blockstate locations.
                    IModel model = ModelLoaderRegistry.getModel(new ResourceLocation(path))
                            .process(ImmutableMap.of("flip-v", "true"));
                    unbaked.put(key(state.resourceName, entry.getKey()), model);
                    for (ResourceLocation texture : model.getTextures()) event.getMap().registerSprite(texture);
                }
            }
        } catch (Exception exception) {
            throw new IllegalStateException("Could not load Data Chip Programmer resources", exception);
        }
    }

    @SubscribeEvent
    public void afterBake(ModelBakeEvent event) {
        if (unbaked.isEmpty()) return;
        meshes.clear();
        format = new net.minecraft.client.renderer.vertex.VertexFormat(DefaultVertexFormats.BLOCK);
        for (Map.Entry<String, IModel> entry : unbaked.entrySet()) {
            IModel model = entry.getValue();
            IBakedModel baked = model.bake(model.getDefaultState(), format,
                    texture -> Minecraft.getMinecraft().getTextureMapBlocks().getAtlasSprite(texture.toString()));
            meshes.put(entry.getKey(), new Mesh(baked, format.getIntegerSize()));
        }
        event.getModelRegistry().putObject(new net.minecraft.client.renderer.block.model.ModelResourceLocation("vandorlabs:data_chip_programmer","inventory"),new ProgrammerItemModel(itemModel.bake(itemModel.getDefaultState(),DefaultVertexFormats.ITEM,texture->Minecraft.getMinecraft().getTextureMapBlocks().getAtlasSprite(texture.toString()))));
        ready = true;
    }

    /** Verify both the OBJ dependencies and what was actually baked into the casing meshes. */
    public void assertTexturesResolved(){
        net.minecraft.client.renderer.texture.TextureMap atlas=Minecraft.getMinecraft().getTextureMapBlocks();
        for(Map.Entry<String,IModel> entry:unbaked.entrySet())for(ResourceLocation texture:entry.getValue().getTextures()){
            net.minecraft.client.renderer.texture.TextureAtlasSprite sprite=atlas.getAtlasSprite(texture.toString());
            if(sprite==atlas.getMissingSprite())throw new IllegalStateException("Programmer texture missing from stitched runtime atlas: "+entry.getKey()+" -> "+texture);
        }
        for(Map.Entry<String,Mesh> entry:meshes.entrySet())for(net.minecraft.client.renderer.texture.TextureAtlasSprite sprite:entry.getValue().sprites)
            if(sprite==atlas.getMissingSprite()||sprite.getIconName().equals(atlas.getMissingSprite().getIconName()))throw new IllegalStateException("Programmer baked missing sprite: "+entry.getKey());
    }

    /** Fixed mesh group and its optional example-card visibility bit. */
    public static final class Part {
        public final String name;
        public final int exampleCardBit;
        Part(String name) {
            this.name = name;
            int bit = 0;
            if (!"static".equals(name) && !"screen".equals(name)) {
                for (ProgrammerCardSlot slot : ProgrammerCardSlot.values())
                    if (slot.partName.equals(name)) bit = slot.mask;
                if (bit == 0) throw new IllegalArgumentException("Unknown programmer part: " + name);
            }
            exampleCardBit = bit;
        }
    }

    public static final class Definition {
        public final double width, height, depth;
        public final boolean screenFullbrightWhenActive;
        public final List<Part> parts;
        Definition(JsonObject manifest) {
            JsonArray size = manifest.getAsJsonArray("size_blocks");
            width = size.get(0).getAsDouble();
            height = size.get(1).getAsDouble();
            depth = size.get(2).getAsDouble();
            if (!(width > 0) || !(height > 0) || !(depth > 0))
                throw new IllegalArgumentException("Invalid programmer extent");
            screenFullbrightWhenActive = !manifest.has("screen_fullbright_when_active")
                    || manifest.get("screen_fullbright_when_active").getAsBoolean();
            JsonObject partMap = manifest.getAsJsonObject("states").getAsJsonObject("ready").getAsJsonObject("parts");
            List<Part> names = new ArrayList<>();
            for (Map.Entry<String, JsonElement> entry : partMap.entrySet()) names.add(new Part(entry.getKey()));
            if (names.size() != 5 || !partMap.has("static") || !partMap.has("screen"))
                throw new IllegalArgumentException("Expected casing, screen and three optional sample cards");
            for (ProgrammerCardSlot slot : ProgrammerCardSlot.values())
                if (!partMap.has(slot.partName)) throw new IllegalArgumentException("Missing card part: " + slot.partName);
            parts = Collections.unmodifiableList(names);
        }
    }

    /** Private immutable copies of native BLOCK-format quads with stitched UVs. */
    public static final class Mesh {
        private final List<int[]> quads;
        private final java.util.Set<net.minecraft.client.renderer.texture.TextureAtlasSprite> sprites=new java.util.HashSet<>();
        Mesh(IBakedModel model, int stride) {
            List<int[]> data = new ArrayList<>();
            add(data, model.getQuads(null, null, 0L),stride,sprites);
            for (EnumFacing side : EnumFacing.values()) add(data, model.getQuads(null, side, 0L),stride,sprites);
            quads = Collections.unmodifiableList(data);
        }
        private static void add(List<int[]> destination, List<BakedQuad> source,int stride,java.util.Set<net.minecraft.client.renderer.texture.TextureAtlasSprite> sprites) {
            for (BakedQuad quad : source) {
                sprites.add(quad.getSprite());
                int[] values = quad.getVertexData();
                if (values.length != stride*4) throw new IllegalArgumentException("Expected a BLOCK-format quad");
                destination.add(values.clone());
            }
        }
        List<int[]> rawQuads() { return quads; }
        public int quadCount() { return quads.size(); }
    }
}
