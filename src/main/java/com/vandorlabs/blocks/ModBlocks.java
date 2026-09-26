package com.vandorlabs.blocks;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.vandorlabs.VandorLabs;
import com.vandorlabs.render.DoorLeaf;
import net.minecraft.block.Block;
import net.minecraft.block.properties.PropertyDirection;
import net.minecraft.client.renderer.block.model.ModelResourceLocation;
import net.minecraft.item.Item;
import net.minecraft.item.ItemBlock;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.event.ModelRegistryEvent;
import net.minecraftforge.client.event.ModelBakeEvent;
import net.minecraftforge.client.event.TextureStitchEvent;
import net.minecraftforge.client.model.ModelLoader;
import net.minecraft.client.renderer.block.statemap.StateMap;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Mod.EventBusSubscriber(modid = VandorLabs.MODID)
public class ModBlocks {

    public static Block ANIMATED_SCREEN_SELECTOR;
    public static Block PROGRAMMABLE_CONSOLE;
    public static Block PROGRAMMABLE_WALL;
    public static Block PROGRAMMABLE_BLOCK;
    public static Block PROGRAMMABLE_TRIGGER_BLOCK;
    public static Block PROGRAMMABLE_LIGHT;
    public static Block PROGRAMMABLE_SLAB;
    public static Block PROGRAMMABLE_CHAIR;
    public static Block PROGRAMMABLE_PORTHOLE_WALL;
    public static Block PROGRAMMABLE_PORTHOLE_BLOCK;
    public static Block PROGRAMMABLE_DIAGONAL_WALL;
    public static Block PROGRAMMABLE_DIAGONAL_SCREEN;
    public static Block PROGRAMMABLE_INPUT;
    public static Block PROGRAMMABLE_HALF_CONSOLE;
    public static Block PROGRAMMABLE_FULL_INPUT;
    public static Block CONTROLLED_RAMP;
    public static Block PROGRAMMABLE_RAMP;
    public static final List<Block> BLOCKS = new ArrayList<>();
    /** Ids of every "display" (animated screen) block, sorted. Populated
     * from the generated catalog; drives the selector GUI list and packet
     * validation. */
    public static final List<String> DISPLAY_SCREEN_IDS = new ArrayList<>();
    /** Old static screen ids retained only for seamless world migration. */
    private static final java.util.Set<String> RETIRED_SCREEN_IDS = new java.util.HashSet<>();
    /** Intentionally removed legacy doors; old saves may discard these entries. */
    public static final java.util.Set<String> REMOVED_DOOR_IDS = java.util.Collections.unmodifiableSet(
            new java.util.HashSet<>(java.util.Arrays.asList(
                    "door_airlock_glass", "door_security", "sliding_airlock_glass", "sliding_security_door",
                    "detail_engineering_rotating_single", "detail_engineering_rotating_double",
                    "detail_engineering_sliding_single", "detail_engineering_sliding_double",
                    "detail_observation_rotating_single", "detail_observation_rotating_double",
                    "detail_observation_sliding_single", "detail_observation_sliding_double",
                    "detail_split_rotating_single", "detail_split_rotating_double",
                    "detail_split_sliding_single", "detail_split_sliding_double")));
    /** Subset of display ids using the framed off-border. */
    public static final java.util.Set<String> DISPLAY_FRAMED_IDS = new java.util.HashSet<>();
    /** One entry per screen family: ids carrying a bare_/framed_ segment are
     * paired under a shared key ("analysis_bare_station" +
     * "analysis_framed_station" -> "analysis_station"); lone ids stand alone.
     * Drives the selector GUI's single list + Bare/Framed checkbox. */
    public static final List<ScreenOption> SCREEN_OPTIONS = new ArrayList<>();

    public static class ScreenOption {
        public final String key;
        public final String bareId;
        public final String framedId;

        public ScreenOption(String key, String bareId, String framedId) {
            this.key = key;
            this.bareId = bareId;
            this.framedId = framedId;
        }

        public boolean hasPair() {
            // Lone ids occupy both slots with the same id, so a real pair
            // means two distinct variants.
            return bareId != null && framedId != null && !bareId.equals(framedId);
        }
    }
    private static final java.util.Set<Block> NO_ITEM = new java.util.HashSet<>();

    static {
        // Block list loaded from the checked-in generated resource catalog.
        Map<String, Block> byId = new HashMap<>();
        JsonArray catalog = loadCatalog();
        for (JsonElement el : catalog) {
            JsonObject e = el.getAsJsonObject();
            String id = e.get("id").getAsString();
            boolean display = e.has("type")
                    && "display".equals(e.get("type").getAsString());
            if (display) {
                DISPLAY_SCREEN_IDS.add(id);
                if (e.has("framed") && e.get("framed").getAsBoolean()) {
                    DISPLAY_FRAMED_IDS.add(id);
                }
            }
            if (e.has("retired") && e.get("retired").getAsBoolean()) {
                RETIRED_SCREEN_IDS.add(id);
                continue;
            }
            // Display sequences are data owned by the Programmable blocks,
            // not independently registered blocks/items.
            if (e.has("programmable_only")
                    && e.get("programmable_only").getAsBoolean()) {
                continue;
            }
            String cls = e.get("class").getAsString();
            Block block = create(cls, e, byId);
            if (e.has("hidden") && e.get("hidden").getAsBoolean()) block.setCreativeTab(null);
            byId.put(id, block);
            // Paired detailed-door assets retain their authored geometry and
            // motion parameters without registering a redundant block/item.
            if (e.has("internal_model")
                    && e.get("internal_model").getAsBoolean()) {
                continue;
            }
            if (e.has("item") && !e.get("item").getAsBoolean()) {
                addNoItem(block);
            } else {
                add(block);
            }
        }
        java.util.Collections.sort(DISPLAY_SCREEN_IDS);
        Map<String, String> bareByKey = new HashMap<>();
        Map<String, String> framedByKey = new HashMap<>();
        for (String id : DISPLAY_SCREEN_IDS) {
            if (id.contains("_bare_") || id.startsWith("bare_")) {
                bareByKey.put(stripVariant(id), id);
            } else if (id.contains("_framed_") || id.startsWith("framed_")) {
                framedByKey.put(stripVariant(id), id);
            } else {
                bareByKey.put(id, id);
                framedByKey.put(id, id);
            }
        }
        java.util.Set<String> keys = new java.util.TreeSet<>();
        keys.addAll(bareByKey.keySet());
        keys.addAll(framedByKey.keySet());
        for (String key : keys) {
            String bare = bareByKey.get(key);
            String framed = framedByKey.get(key);
            // Lone ids occupy both slots so callers never null-check.
            if (bare == null) {
                bare = framed;
            }
            if (framed == null) {
                framed = bare;
            }
            SCREEN_OPTIONS.add(new ScreenOption(key, bare, framed));
        }
        // Hand-maintained custom blocks with bespoke assets in
        // src/main/resources.
        add(new BlockIndustrialLever());
        add(new BlockCompactLever());
        add(new BlockIndustrialTable());
        ANIMATED_SCREEN_SELECTOR = new BlockAnimatedScreenSelector();
        add(ANIMATED_SCREEN_SELECTOR);
        PROGRAMMABLE_CONSOLE = new BlockProgrammableConsole();
        add(PROGRAMMABLE_CONSOLE);
        PROGRAMMABLE_WALL = add(new BlockProgrammableWall("programmable_wall",
                BlockProgrammableWall.Shape.PLAIN));
        PROGRAMMABLE_BLOCK = add(new BlockProgrammableBlock());
        PROGRAMMABLE_TRIGGER_BLOCK = add(new BlockProgrammableTrigger());
        PROGRAMMABLE_LIGHT = add(new BlockProgrammableLight());
        PROGRAMMABLE_CHAIR = add(new BlockBridgeChair());
        PROGRAMMABLE_SLAB = add(new BlockProgrammableSlab());
        PROGRAMMABLE_PORTHOLE_WALL = add(new BlockProgrammableWall("programmable_porthole_wall",
                BlockProgrammableWall.Shape.PORTHOLE));
        PROGRAMMABLE_PORTHOLE_BLOCK = add(new BlockProgrammablePortholeBlock());
        PROGRAMMABLE_DIAGONAL_WALL = add(new BlockProgrammableWall("programmable_diagonal_wall",
                BlockProgrammableWall.Shape.DIAGONAL));
        PROGRAMMABLE_DIAGONAL_SCREEN = new BlockProgrammableDiagonalScreen();
        add(PROGRAMMABLE_DIAGONAL_SCREEN);
        PROGRAMMABLE_INPUT = new BlockProgrammableInput();
        add(PROGRAMMABLE_INPUT);
        PROGRAMMABLE_HALF_CONSOLE = new BlockProgrammableHalfConsole();
        add(PROGRAMMABLE_HALF_CONSOLE);
        PROGRAMMABLE_FULL_INPUT = new BlockProgrammableFullInput();
        add(PROGRAMMABLE_FULL_INPUT);
        PROGRAMMABLE_RAMP = add(new BlockRampController());
        CONTROLLED_RAMP = new BlockControlledRamp();
        addNoItem(CONTROLLED_RAMP);
    }

    private static String stripVariant(String id) {
        if (id.contains("_bare_")) {
            return id.replace("_bare_", "_");
        }
        if (id.contains("_framed_")) {
            return id.replace("_framed_", "_");
        }
        if (id.startsWith("bare_")) {
            return id.substring("bare_".length());
        }
        if (id.startsWith("framed_")) {
            return id.substring("framed_".length());
        }
        return id;
    }

    private static JsonArray loadCatalog() {
        String path = "/assets/vandorlabs/data/blocks.json";
        InputStream in = ModBlocks.class.getResourceAsStream(path);
        if (in == null) {
            throw new IllegalStateException(
                    "vandorlabs: missing checked-in generated resource catalog " + path);
        }
        try (InputStreamReader reader = new InputStreamReader(in, "UTF-8")) {
            return new JsonParser().parse(reader).getAsJsonArray();
        } catch (Exception e) {
            throw new IllegalStateException("vandorlabs: cannot parse " + path, e);
        }
    }

    private static Block create(String cls, JsonObject e, Map<String, Block> byId) {
        String id = e.get("id").getAsString();
        switch (cls) {
            case "BlockConfigurableSpaceDoor":
                return new BlockConfigurableSpaceDoor(id,e.get("sliding").getAsBoolean(),
                        (BlockDetailedDoor)byId.get(e.get("paired_model").getAsString()));
            case "BlockProgrammableGlass":
                return new BlockProgrammableGlass(id);
            case "BlockSpaceDoor":
                return new BlockSpaceDoor(id, e.get("sliding").getAsBoolean(),
                        e.get("framed").getAsBoolean(),
                        (BlockDetailedDoor) byId.get(e.get("paired_model").getAsString()));
            case "BlockVandor":
                return new BlockVandor(id);
            case "BlockCockpitGlass":
                return new BlockCockpitGlass(id);
            case "BlockGlassWall":
                return new BlockGlassWall(id);
            case "BlockVandorDirectional":
                return new BlockVandorDirectional(id);
            case "BlockVandorConsole":
                return new BlockVandorConsole(id);
            case "BlockDisplaySequenced":
                return new BlockDisplaySequenced(id);
            case "BlockVandorSwitch":
                return new BlockVandorSwitch(id,
                        e.has("momentary") && e.get("momentary").getAsBoolean());
            case "BlockVandorDoor":
                return new BlockVandorDoor(id,
                        BlockVandorDoor.DoorMotion.fromId(e.get("motion").getAsString()),
                        e.has("cutout") && e.get("cutout").getAsBoolean());
            case "BlockDetailedDoor":
                return new BlockDetailedDoor(id,
                        BlockVandorDoor.DoorMotion.fromId(e.get("motion").getAsString()),
                        e.get("sliding").getAsBoolean(),
                        "double".equals(e.get("door_layout").getAsString()),
                        e.get("split_inside_one_block").getAsBoolean(),
                        e.get("left_pivot").getAsFloat(),
                        e.get("right_pivot").getAsFloat(),
                        e.get("pivot_z").getAsFloat(),
                        e.get("left_angle").getAsFloat(),
                        e.get("right_angle").getAsFloat(),
                        e.get("left_slide").getAsFloat(),
                        e.get("right_slide").getAsFloat());
            case "BlockConnectingDetailedDoor": {
                Block paired = byId.get(e.get("paired_model").getAsString());
                if (!(paired instanceof BlockDetailedDoor)) {
                    throw new IllegalStateException("vandorlabs: paired detailed door missing for "
                            + id + ": " + e.get("paired_model").getAsString());
                }
                return new BlockConnectingDetailedDoor(id,
                        BlockVandorDoor.DoorMotion.fromId(e.get("motion").getAsString()),
                        e.get("sliding").getAsBoolean(),
                        "double".equals(e.get("door_layout").getAsString()),
                        e.get("split_inside_one_block").getAsBoolean(),
                        e.get("left_pivot").getAsFloat(),
                        e.get("right_pivot").getAsFloat(),
                        e.get("pivot_z").getAsFloat(),
                        e.get("left_angle").getAsFloat(),
                        e.get("right_angle").getAsFloat(),
                        e.get("left_slide").getAsFloat(),
                        e.get("right_slide").getAsFloat(),
                        (BlockDetailedDoor) paired);
            }
            case "BlockLamp":
                return new BlockLamp(id);
            case "BlockPropulsionLight":
                return new BlockPropulsionLight(id,
                        e.has("points_up") && e.get("points_up").getAsBoolean(),
                        e.has("depth") ? e.get("depth").getAsFloat() : 1.0F);
            case "BlockConnectedPropulsionLight":
                return new BlockConnectedPropulsionLight(id);
            case "BlockTrianglePropulsionLight":
                return new BlockTrianglePropulsionLight(id,
                        e.get("triangle_base").getAsString());
            case "BlockLampOff": {
                Block on = byId.get(e.get("pair").getAsString());
                if (on == null) {
                    throw new IllegalStateException("vandorlabs: lamp pair missing for " + id);
                }
                return new BlockLampOff(id, on);
            }
            default:
                throw new IllegalStateException("vandorlabs: unknown block class " + cls + " for " + id);
        }
    }

    private static Block add(Block block) {
        BLOCKS.add(block);
        return block;
    }

    private static Block addNoItem(Block block) {
        BLOCKS.add(block);
        NO_ITEM.add(block);
        return block;
    }

    @SubscribeEvent
    public static void onBlockRegister(RegistryEvent.Register<Block> event) {
        event.getRegistry().registerAll(BLOCKS.toArray(new Block[0]));
    }

    /** Allow worlds from pre-Programmable builds to load after the 73
     * redundant sequenced blocks/items were intentionally retired. */
    @SubscribeEvent
    public static void onMissingDisplayBlocks(RegistryEvent.MissingMappings<Block> event) {
        for (RegistryEvent.MissingMappings.Mapping<Block> mapping : event.getMappings()) {
            if (!VandorLabs.MODID.equals(mapping.key.getResourceDomain())) continue;
            String replacement = replacementBlockId(mapping.key.getResourcePath());
            if (replacement != null) {
                Block block = Block.REGISTRY.getObject(
                        new ResourceLocation(VandorLabs.MODID, replacement));
                if (block != null) mapping.remap(block);
            } else if (isRetiredScreenId(mapping.key.getResourcePath())
                    || REMOVED_DOOR_IDS.contains(mapping.key.getResourcePath())) mapping.ignore();
        }
    }

    @SubscribeEvent
    public static void onMissingDisplayItems(RegistryEvent.MissingMappings<Item> event) {
        for (RegistryEvent.MissingMappings.Mapping<Item> mapping : event.getMappings()) {
            if (!VandorLabs.MODID.equals(mapping.key.getResourceDomain())) continue;
            String replacement = replacementBlockId(mapping.key.getResourcePath());
            if (replacement != null) {
                Item item = Item.REGISTRY.getObject(
                        new ResourceLocation(VandorLabs.MODID, replacement));
                if (item != null) mapping.remap(item);
            } else if (isRetiredScreenId(mapping.key.getResourcePath())
                    || REMOVED_DOOR_IDS.contains(mapping.key.getResourcePath())) mapping.ignore();
        }
    }

    private static boolean isRetiredScreenId(String id) {
        return DISPLAY_SCREEN_IDS.contains(id) || RETIRED_SCREEN_IDS.contains(id);
    }

    private static String replacementBlockId(String id) {
        if ("wall_vent".equals(id) || "bolted_wall_plate".equals(id)
                || "vent_grille".equals(id) || "burgundy_carpet".equals(id)
                || "bluegray_carpet".equals(id)) return null;
        for (String finish : com.vandorlabs.tiles.ScreenHousingTextures.IDS)
            if (finish.equals(id)) return "programmable_block";
        if ("plasma_thruster".equals(id)) return "plasma_vent";
        if ("impulse_engine".equals(id)) return "impulse_engine";
        return null;
    }

    @SubscribeEvent
    public static void onItemRegister(RegistryEvent.Register<Item> event) {
        for (Block block : BLOCKS) {
            if (!NO_ITEM.contains(block)) {
                ItemBlock item = block == PROGRAMMABLE_INPUT
                        ? new ItemProgrammableInput((BlockProgrammableInput) block)
                        : new ItemBlock(block);
                event.getRegistry().register(item.setRegistryName(block.getRegistryName()));
            }
        }
    }

    @SubscribeEvent
    @SideOnly(Side.CLIENT)
    public static void onTextureStitch(TextureStitchEvent.Pre event) {
        // Door control panels still use this sprite after the standalone
        // Control Buttons block and its model were retired.
        event.getMap().registerSprite(new ResourceLocation(
                VandorLabs.MODID, "blocks/control_buttons"));
        for (int i = 0; i < com.vandorlabs.tiles.ScreenHousingTextures.IDS.length; i++)
            event.getMap().registerSprite(new ResourceLocation(
                    com.vandorlabs.tiles.ScreenHousingTextures.texture(i)));
        for (int i = 0; i < com.vandorlabs.tiles.ProgrammableLightTextures.IDS.length; i++) {
            event.getMap().registerSprite(new ResourceLocation(
                    com.vandorlabs.tiles.ProgrammableLightTextures.texture(i, true)));
            event.getMap().registerSprite(new ResourceLocation(
                    com.vandorlabs.tiles.ProgrammableLightTextures.texture(i, false)));
        }
    }

    @SubscribeEvent
    @SideOnly(Side.CLIENT)
    public static void onModelRegister(ModelRegistryEvent event) {
        for (Block block : BLOCKS) {
            if (!NO_ITEM.contains(block)) {
                Item item = Item.getItemFromBlock(block);
                if (block instanceof BlockConfigurableSpaceDoor) {
                    registerSpaceDoorModels((BlockConfigurableSpaceDoor)block,item);
                    ModelLoader.setCustomMeshDefinition(item, stack -> doorItemModel(stack));
                    continue;
                }
                if (block == PROGRAMMABLE_BLOCK || block == PROGRAMMABLE_TRIGGER_BLOCK
                        || block == PROGRAMMABLE_SLAB
                        || block == PROGRAMMABLE_WALL || block == PROGRAMMABLE_DIAGONAL_WALL
                        || block == PROGRAMMABLE_PORTHOLE_WALL
                        || block == PROGRAMMABLE_PORTHOLE_BLOCK) {
                    if (block == PROGRAMMABLE_SLAB) registerSlabItemModels(item);
                    else registerHousingItemModels(block, item);
                    continue;
                }
                if (block == PROGRAMMABLE_LIGHT) {
                    registerLightItemModels(item);
                    continue;
                }
                if (block == PROGRAMMABLE_CHAIR) {
                    registerChairItemModels(item);
                    continue;
                }
                if (block instanceof BlockConnectedPropulsionLight
                        && !((BlockConnectedPropulsionLight) block).familyId().isEmpty()) {
                    String family = ((BlockConnectedPropulsionLight) block).familyId();
                    ResourceLocation[] shapes = {
                            new ResourceLocation(VandorLabs.MODID, family),
                            new ResourceLocation(VandorLabs.MODID, family + "_hexagonal"),
                            new ResourceLocation(VandorLabs.MODID, family + "_wedge")};
                    ModelLoader.registerItemVariants(item, shapes);
                    ModelLoader.setCustomMeshDefinition(item, stack -> {
                        net.minecraft.nbt.NBTTagCompound tag = stack.getSubCompound("BlockEntityTag");
                        int shape = tag == null ? 0 : tag.getInteger("PropulsionShape");
                        return new ModelResourceLocation(shapes[
                                Math.max(0, Math.min(2, shape))], "inventory");
                    });
                    continue;
                }
                ModelLoader.setCustomModelResourceLocation(
                        item,
                        0,
                        new ModelResourceLocation(block.getRegistryName(), "inventory"));
                if (block instanceof BlockDetailedDoor) {
                    ModelLoader.setCustomStateMapper(block,
                            new StateMap.Builder().ignore(BlockVandorDoor.POWERED).build());
                    String id = block.getRegistryName().getResourcePath();
                    ModelLoader.setCustomModelResourceLocation(item, DoorLeaf.LEFT.legacyMetadata,
                            new ModelResourceLocation(VandorLabs.MODID + ":detailed_doors/"
                                    + id + "_"+DoorLeaf.LEFT.modelSuffix, "inventory"));
                    ModelLoader.setCustomModelResourceLocation(item, DoorLeaf.RIGHT.legacyMetadata,
                            new ModelResourceLocation(VandorLabs.MODID + ":detailed_doors/"
                                    + id + "_"+DoorLeaf.RIGHT.modelSuffix, "inventory"));
                    if (block instanceof BlockConnectingDetailedDoor) {
                        String paired = ((BlockConnectingDetailedDoor) block)
                                .getPairedModelId();
                        ModelLoader.setCustomModelResourceLocation(item, 3,
                                new ModelResourceLocation(VandorLabs.MODID
                                        + ":detailed_doors/" + paired
                                        + "_" + DoorLeaf.LEFT.modelSuffix,
                                        "inventory"));
                        ModelLoader.setCustomModelResourceLocation(item, 4,
                                new ModelResourceLocation(VandorLabs.MODID
                                        + ":detailed_doors/" + paired
                                        + "_" + DoorLeaf.RIGHT.modelSuffix,
                                        "inventory"));
                        if (block instanceof BlockSpaceDoor && ((BlockSpaceDoor) block).hasGlass()) {
                            for (int meta = 5; meta <= 8; meta++) {
                                String base = meta >= 7 ? paired : id;
                                String hand = meta % 2 == 1 ? "left" : "right";
                                ModelLoader.setCustomModelResourceLocation(item, meta,
                                        new ModelResourceLocation(VandorLabs.MODID
                                                + ":detailed_doors/" + base + "_" + hand
                                                + "_glass", "inventory"));
                            }
                        }
                    }
                }
            }
        }
    }

    @SideOnly(Side.CLIENT)
    private static void registerHousingItemModels(Block block, Item item) {
        String id = block.getRegistryName().getResourcePath();
        ResourceLocation[] variants = new ResourceLocation[
                com.vandorlabs.tiles.ScreenHousingTextures.IDS.length];
        for (int i = 0; i < variants.length; i++)
            variants[i] = new ResourceLocation(VandorLabs.MODID,
                    "configured/" + id + "_"
                            + com.vandorlabs.tiles.ScreenHousingTextures.IDS[i]);
        ModelLoader.registerItemVariants(item, variants);
        ModelLoader.setCustomMeshDefinition(item, stack -> {
            net.minecraft.nbt.NBTTagCompound tag = stack.getSubCompound("BlockEntityTag");
            int choice = tag == null ? 0 : com.vandorlabs.tiles.ScreenHousingTextures.clamp(
                    tag.getInteger(com.vandorlabs.persistence.SaveSchema.Screen.HOUSING_TEXTURE));
            return new ModelResourceLocation(variants[choice], "inventory");
        });
    }

    @SideOnly(Side.CLIENT)
    private static void registerSlabItemModels(Item item) {
        ResourceLocation[] variants = new ResourceLocation[
                com.vandorlabs.tiles.ScreenHousingTextures.IDS.length * 2];
        for (int i = 0; i < com.vandorlabs.tiles.ScreenHousingTextures.IDS.length; i++) {
            String id = com.vandorlabs.tiles.ScreenHousingTextures.IDS[i];
            variants[i * 2] = new ResourceLocation(VandorLabs.MODID,
                    "configured/programmable_slab_" + id + "_fit");
            variants[i * 2 + 1] = new ResourceLocation(VandorLabs.MODID,
                    "configured/programmable_slab_" + id + "_tile");
        }
        ModelLoader.registerItemVariants(item, variants);
        ModelLoader.setCustomMeshDefinition(item, stack -> {
            net.minecraft.nbt.NBTTagCompound tag = stack.getSubCompound("BlockEntityTag");
            int choice = tag == null ? 0 : com.vandorlabs.tiles.ScreenHousingTextures.clamp(
                    tag.getInteger(com.vandorlabs.persistence.SaveSchema.Screen.HOUSING_TEXTURE));
            boolean tileSides = tag != null && tag.getBoolean("SlabTileSides");
            return new ModelResourceLocation(variants[choice * 2 + (tileSides ? 1 : 0)],
                    "inventory");
        });
    }

    @SideOnly(Side.CLIENT)
    private static void registerLightItemModels(Item item) {
        ResourceLocation[] variants = new ResourceLocation[
                com.vandorlabs.tiles.ProgrammableLightTextures.IDS.length * 2];
        for (int i = 0; i < com.vandorlabs.tiles.ProgrammableLightTextures.IDS.length; i++) {
            String id = com.vandorlabs.tiles.ProgrammableLightTextures.IDS[i];
            variants[i * 2] = new ResourceLocation(VandorLabs.MODID,
                    "configured/programmable_light_" + id + "_off");
            variants[i * 2 + 1] = new ResourceLocation(VandorLabs.MODID,
                    "configured/programmable_light_" + id + "_on");
        }
        ModelLoader.registerItemVariants(item, variants);
        ModelLoader.setCustomMeshDefinition(item, stack -> {
            net.minecraft.nbt.NBTTagCompound tag = stack.getSubCompound("BlockEntityTag");
            int choice = tag == null ? 0
                    : com.vandorlabs.tiles.ProgrammableLightTextures.clamp(
                            tag.getInteger("LightTexture"));
            boolean lit = tag == null || ((!tag.hasKey("LightOn")
                    || tag.getBoolean("LightOn"))
                    && (!tag.hasKey("LightLevel", 3) || tag.getInteger("LightLevel") > 0));
            return new ModelResourceLocation(variants[choice * 2 + (lit ? 1 : 0)],
                    "inventory");
        });
    }

    @SideOnly(Side.CLIENT)
    private static ModelResourceLocation doorItemModel(net.minecraft.item.ItemStack stack) {
        net.minecraft.nbt.NBTTagCompound tag = stack.getSubCompound("SpaceDoorSettings");
        com.vandorlabs.persistence.SpaceDoorData data =
                com.vandorlabs.persistence.SpaceDoorData.read(
                        new com.vandorlabs.persistence.NbtPrimitiveData(
                                tag == null ? new net.minecraft.nbt.NBTTagCompound() : tag));
        String model = com.vandorlabs.tiles.TileEntitySpaceDoor.modelId(
                data.design, data.sliding, data.framed) + "_left_leaf"
                + (!data.sliding && !data.hinges ? "_no_hinges" : "");
        return new ModelResourceLocation(VandorLabs.MODID + ":detailed_doors/"
                + com.vandorlabs.tiles.TileEntitySpaceDoor.DETAILS[data.detail]
                + "/" + model, "inventory");
    }

    @SubscribeEvent
    @SideOnly(Side.CLIENT)
    public static void onModelBake(ModelBakeEvent event) {
        for (ModelResourceLocation location : new java.util.ArrayList<>(
                event.getModelRegistry().getKeys())) {
            if (!VandorLabs.MODID.equals(location.getResourceDomain())) continue;
            String path = location.getResourcePath();
            if (!(path.startsWith("rocket_thruster") || path.startsWith("ion_drive")
                    || path.startsWith("plasma_vent") || path.startsWith("impulse_engine")))
                continue;
            net.minecraft.client.renderer.block.model.IBakedModel model =
                    event.getModelRegistry().getObject(location);
            if (model != null) event.getModelRegistry().putObject(location,
                    new com.vandorlabs.client.PropulsionSideModel(model));
        }
        for (int design = 0; design < com.vandorlabs.tiles.TileEntitySpaceDoor.DESIGNS.length;
                design++) for (String detail : com.vandorlabs.tiles.TileEntitySpaceDoor.DETAILS)
            for (boolean framed : new boolean[]{false, true})
                for (boolean sliding : new boolean[]{false, true})
                    for (boolean hinges : new boolean[]{false, true}) {
                        if (sliding && !hinges) continue;
                        String model = com.vandorlabs.tiles.TileEntitySpaceDoor.modelId(
                                design, sliding, framed) + "_left_leaf"
                                + (!sliding && !hinges ? "_no_hinges" : "");
                        ModelResourceLocation location = new ModelResourceLocation(
                                VandorLabs.MODID + ":detailed_doors/" + detail + "/" + model,
                                "inventory");
                        net.minecraft.client.renderer.block.model.IBakedModel baked =
                                event.getModelRegistry().getObject(location);
                        if (baked != null) event.getModelRegistry().putObject(location,
                                new com.vandorlabs.client.ScaledDoorItemModel(baked));
                    }
    }

    @SideOnly(Side.CLIENT)
    private static void registerChairItemModels(Item item) {
        ResourceLocation[] variants = new ResourceLocation[
                BlockBridgeChair.Style.values().length * BlockBridgeChair.Height.values().length];
        for (int style = 0; style < BlockBridgeChair.Style.values().length; style++)
            for (int height = 0; height < BlockBridgeChair.Height.values().length; height++) {
                String name = "configured/programmable_chair_"
                        + BlockBridgeChair.Style.byIndex(style).id;
                if (height != 1)
                    name += "_" + BlockBridgeChair.Height.byIndex(height).getName();
                variants[style * 3 + height] = new ResourceLocation(VandorLabs.MODID, name);
            }
        ModelLoader.registerItemVariants(item, variants);
        ModelLoader.setCustomMeshDefinition(item, stack -> {
            net.minecraft.nbt.NBTTagCompound tag = stack.getSubCompound("BlockEntityTag");
            int style = tag == null ? 0 : tag.getInteger("ChairStyle");
            int height = tag == null || !tag.hasKey("ChairHeight", 3)
                    ? 1 : tag.getInteger("ChairHeight");
            return new ModelResourceLocation(variants[BlockBridgeChair.Style.byIndex(style)
                    .ordinal() * 3 + BlockBridgeChair.Height.byIndex(height).ordinal()], "inventory");
        });
    }

    @SideOnly(Side.CLIENT)
    private static void registerSpaceDoorModels(BlockConfigurableSpaceDoor block,Item item) {
        ModelLoader.setCustomStateMapper(block,new StateMap.Builder().ignore(BlockVandorDoor.POWERED).build());
        String[] details=com.vandorlabs.tiles.TileEntitySpaceDoor.DETAILS;
        for (boolean sliding:new boolean[]{false,true}) {
        for (int d=0;d<com.vandorlabs.tiles.TileEntitySpaceDoor.DESIGNS.length;d++) for (int l=0;l<details.length;l++)
            for (boolean framed:new boolean[]{false,true}) for (boolean paired:new boolean[]{false,true})
                for (boolean right:new boolean[]{false,true}) for (int part=0;part<3;part++) {
                    if (part==2 && !com.vandorlabs.tiles.TileEntitySpaceDoor.hasGlassDesign(d)) continue;
                    String name=com.vandorlabs.tiles.TileEntitySpaceDoor.modelId(d,sliding,framed)
                            +(paired?"_paired":"")+(right?"_right_":"_left_")
                            +(part==0?"fixed":part==1?"leaf":"glass");
                    int meta=com.vandorlabs.tiles.TileEntitySpaceDoor.metadata(d,l,framed,paired,right,part,sliding);
                    ModelLoader.setCustomModelResourceLocation(item,meta,new ModelResourceLocation(
                            VandorLabs.MODID+":detailed_doors/"+details[l]+"/"+name,"inventory"));
                    if (!sliding) ModelLoader.setCustomModelResourceLocation(item,meta+2160,new ModelResourceLocation(
                            VandorLabs.MODID+":detailed_doors/"+details[l]+"/"+name+(part==2?"":"_no_hinges"),"inventory"));
                }
        }
    }
}
