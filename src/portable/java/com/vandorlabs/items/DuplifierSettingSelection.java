package com.vandorlabs.items;

import java.util.function.Consumer;
import java.util.function.Predicate;

/** Semantic-field selection only; adapters copy their capture before removing fields. */
public final class DuplifierSettingSelection {
    private static final DuplifierOptionCatalog.Option[] OPTIONS=DuplifierOptionCatalog.options();
    private static final DuplifierOptionMask MASK=new DuplifierOptionMask(OPTIONS.length);
    private static final String[][] DEPENDENCIES={
        {"door_sliding","trapdoor_hinge_side","trapdoor_rotation_flipped","trapdoor_slide_into_wall","trapdoor_slide_over_surface"},
        {"diagonal_geometry","diagonal_full_width"},
        {"slab_tile_sides","texture_layout"},
        {"light_face_texture","light_off_face_texture"},
        {"door_tile_texture","door_texture_layout"},
        {"trapdoor_tile_texture","trapdoor_texture_layout"},
        {"glass_material","glass_layout"},
        {"ramp_start","ramp_start_half"},
        {"ramp_end","ramp_end_half"},
        {"primary_texture","primary_kind"}
    };
    public static void prune(long[] mask,Predicate<String> contains,Consumer<String> remove){
        for(int i=0;i<OPTIONS.length;i++)if(!MASK.enabled(mask,i))remove.accept(OPTIONS[i].key);
        for(String[] group:DEPENDENCIES)if(!contains.test(group[0]))for(int i=1;i<group.length;i++)remove.accept(group[i]);
    }
    private DuplifierSettingSelection(){}
}
