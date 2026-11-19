package com.vandorlabs.neoforge.transfer;

import com.vandorlabs.neoforge.VandorLabsBootstrap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;

/** The old solid matter cube had no listed state properties or tile. */
public final class NativeMatterMigration {
    public static final String OLD="vandorlabs:programmable_matter_block", CURRENT="vandorlabs:programmable_block";
    public static final ResourceLocation OLD_ID=ResourceLocation.parse(OLD), CURRENT_ID=ResourceLocation.parse(CURRENT);

    public static String itemId(String id){return OLD.equals(id)?CURRENT:id;}

    public static CompoundTag item(CompoundTag saved){
        if(!OLD.equals(saved.getString("id")))return saved;
        var migrated=saved.copy();
        migrated.putString("id",CURRENT);
        return migrated;
    }

    public static boolean legacyPalette(CompoundTag saved){return OLD.equals(saved.getString("Name"));}

    public static CompoundTag palette(CompoundTag saved){
        if(!legacyPalette(saved))return saved;
        if(saved.contains("Properties")&&(!saved.contains("Properties",Tag.TAG_COMPOUND)||!saved.getCompound("Properties").isEmpty()))
            throw new IllegalArgumentException("Unknown retired Matter Block properties");
        return NbtUtils.writeBlockState(VandorLabsBootstrap.PROGRAMMABLE_BLOCK.get().defaultBlockState());
    }

    public static CompoundTag tile(CompoundTag state,CompoundTag raw,BlockPos position,HolderLookup.Provider registries){
        if(!legacyPalette(state))return chestItems(raw);
        if(raw!=null)throw new IllegalArgumentException("Unexpected retired Matter Block tile");
        return NativeConstructorFrame.defaults(position,registries).tile();
    }

    private static CompoundTag chestItems(CompoundTag raw){
        if(raw==null||!java.util.Set.of("minecraft:chest","minecraft:trapped_chest","minecraft:barrel").contains(raw.getString("id")))
            return raw;
        var items=raw.getList("Items",Tag.TAG_COMPOUND);
        for(int slot=0;slot<items.size();slot++){
            if(!OLD.equals(items.getCompound(slot).getString("id")))continue;
            var migrated=raw.copy();
            var contents=migrated.getList("Items",Tag.TAG_COMPOUND);
            for(int i=slot;i<contents.size();i++)contents.set(i,item(contents.getCompound(i)));
            return migrated;
        }
        return raw;
    }

    private NativeMatterMigration(){}
}
