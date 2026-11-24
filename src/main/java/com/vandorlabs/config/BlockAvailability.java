package com.vandorlabs.config;

import com.vandorlabs.physics.FlightEnvelope;
import java.io.File;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.block.Block;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.common.config.Configuration;

/** Startup-only acquisition settings; registry identities and saved worlds stay intact. */
public final class BlockAvailability {
    private static final Map<String,Boolean> ENABLED=new HashMap<>();
    private static int vehicleMaxBlocks=4096;
    private static int vehicleMaxArticulationPoints=7;
    public enum CruiseCoordinates { ANCHORED, PHYSICAL }
    private static CruiseCoordinates cruiseCoordinates=CruiseCoordinates.ANCHORED;
    private static int cruiseHandoffAltitude=FlightEnvelope.CRUISE_ALTITUDE;
    public static int cruiseHandoffAltitude(){return cruiseHandoffAltitude;}
    public static CruiseCoordinates cruiseCoordinates(){return cruiseCoordinates;}
    public static int vehicleMaxArticulationPoints(){return vehicleMaxArticulationPoints;}
    public static int vehicleMaxBlocks(){return vehicleMaxBlocks;}
    private BlockAvailability() {}
    public static void initialize(File directory) {
        load(new File(directory,"vandorlabs-blocks.cfg"));

    }
    public static void load(File file) {
        Configuration config=new Configuration(file);
        config.load();
        vehicleMaxBlocks=config.getInt("maxBlocks","vehicles",4096,1,
                com.vandorlabs.vehicle.VehicleStructure.MAX_BLOCKS,
                "Maximum connected blocks allowed when assembling a vehicle. Existing vehicles remain restorable. The 64-block axis and 4 MiB data limits still apply. Restart required.");
        vehicleMaxArticulationPoints=config.getInt("maxArticulationPoints","vehicles",7,0,
                com.vandorlabs.vehicle.VehicleArticulation.MAX_JOINTS,
                "Maximum independent hinge points allowed in new vehicle assemblies and attachments. Default 7 permits eight articulated segments; 0 allows rigid vehicles only. Rigid/parallel couplers and closed loops do not count. Lowering this does not prevent saved vehicles loading or parking. Other geometry/data limits still apply. Restart required.");
        cruiseHandoffAltitude=config.getInt("cruiseHandoffAltitude","vehicles",FlightEnvelope.CRUISE_ALTITUDE,
                FlightEnvelope.WORLD_CEILING,FlightEnvelope.MAX_HANDOFF_ALTITUDE,
                "Altitude where Overworld flight finishes blending and stops normal player chunk tracking. Lower values shorten the band. 256 switches directly to seed terrain. Restart required.");
        String selected=config.getString("cruiseCoordinates","vehicles","anchored",
                "High Flight coordinates: anchored (default) or physical (geographic F3 X/Z). Other mods can load terrain at physical coordinates. Restart required.",
                new String[]{"anchored","physical"});
        cruiseCoordinates="physical".equalsIgnoreCase(selected)?CruiseCoordinates.PHYSICAL:CruiseCoordinates.ANCHORED;
        if("resident".equalsIgnoreCase(selected))config.get("vehicles","cruiseCoordinates","anchored").set("anchored");
        if(config.hasKey("vehicles","reanchorResidentCruise"))config.getCategory("vehicles").remove("reanchorResidentCruise");
        ENABLED.clear();
        config.setCategoryComment("blocks","Block categories available in creative menus and crafting. Restart required. Existing world blocks and inventory items are preserved. Diagonal variants belong to diagonals, regardless of material/function.");
        for(String category:BlockCategories.IDS)
            ENABLED.put(category,config.getBoolean(category,"blocks",true,"Enable acquisition of the "+category+" category."));
        if(config.hasChanged())config.save();
    }
    public static boolean enabled(Block block) {return block==null||block.getRegistryName()==null||enabled(block.getRegistryName());}
    public static boolean enabled(ResourceLocation id) {
        if(!"vandorlabs".equals(id.getResourceDomain()))return true;
        return !Boolean.FALSE.equals(ENABLED.get(BlockCategories.of(id.getResourcePath())));
    }
    public static boolean recipeEnabled(ResourceLocation id) {
        return !Block.REGISTRY.containsKey(id)||enabled(id);
    }
}
