package com.vandorlabs.config;

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
