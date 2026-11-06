package com.vandorlabs.neoforge.screen;
import net.minecraft.nbt.*;
/** Selected chip geometry stays deferred; reader contents are validated atomically by the owned storage decoder. */
public final class NativeScreenOwnership {
 private NativeScreenOwnership(){}
 public static boolean installable(CompoundTag root){if(root.contains(NativeScreenItemSettings.PLACEMENT_RECOVERY)||root.contains(NativeScreenItemSettings.RECOVERY))return false;for(String key:new String[]{"PrimarySurfaceTexture","SecondarySurfaceTexture"})if(root.contains(key,Tag.TAG_INT)&&(root.getInt(key)==-4||root.getInt(key)==-3))return false;return !root.contains("ScreenChipReader")||root.contains("ScreenChipReader",Tag.TAG_COMPOUND);}
}
