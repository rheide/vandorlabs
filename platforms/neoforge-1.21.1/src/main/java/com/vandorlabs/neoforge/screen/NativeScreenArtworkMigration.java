package com.vandorlabs.neoforge.screen;
import com.vandorlabs.screen.ScreenArtworkMigration;
import com.vandorlabs.neoforge.ModernHousingTags;
import net.minecraft.nbt.CompoundTag;
/** A private migrated draft; the original saved envelope remains owned by its Saved wrapper. */
public final class NativeScreenArtworkMigration {
 private NativeScreenArtworkMigration(){}
 public static CompoundTag normalize(CompoundTag original,ScreenArtworkMigration.Catalog catalog){CompoundTag draft=original.copy();ScreenArtworkMigration.migrate(new ModernHousingTags(draft),catalog);return draft;}
}
