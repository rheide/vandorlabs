package com.vandorlabs.neoforge.screen;
import net.minecraft.nbt.*;
/** Temporary installation boundary until owned chip/journal services and selected geometry are available. */
public final class NativeScreenOwnership {
 private NativeScreenOwnership(){}
 private static final CompoundTag EMPTY=emptyReader();
 private static CompoundTag emptyReader(){CompoundTag tag=new CompoundTag();tag.putBoolean("AnyToken",true);tag.putInt("AllowedTokens",127);tag.putBoolean("ChipSlot",true);tag.putInt("TokenLogo",0);tag.putInt("TokenBehavior",0);for(String key:new String[]{"TokenPulseUntil","TokenInsertStarted","TokenRejectedUntil","TokenInsertionSequence"})tag.putLong(key,0);tag.putBoolean("TokenToggleApplied",false);tag.putBoolean("TokenToggled",false);return tag;}
 public static boolean installable(CompoundTag root){for(String key:new String[]{"PrimarySurfaceTexture","SecondarySurfaceTexture"})if(root.contains(key,Tag.TAG_INT)&&(root.getInt(key)==-4||root.getInt(key)==-3))return false;if(!root.contains("ScreenChipReader"))return true;if(!root.contains("ScreenChipReader",Tag.TAG_COMPOUND))return false;CompoundTag reader=root.getCompound("ScreenChipReader");for(String key:reader.getAllKeys())if(!EMPTY.contains(key)||!EMPTY.get(key).equals(reader.get(key)))return false;return true;}
}
