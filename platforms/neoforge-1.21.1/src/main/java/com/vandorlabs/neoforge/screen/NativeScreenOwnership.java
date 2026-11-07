package com.vandorlabs.neoforge.screen;
import net.minecraft.nbt.*;
/** Installation boundaries for Screen-derived hosts, with explicit ownership support. */
public final class NativeScreenOwnership {
 private NativeScreenOwnership(){}
 private static final CompoundTag EMPTY=emptyReader();
 private static CompoundTag emptyReader(){CompoundTag tag=new CompoundTag();tag.putBoolean("AnyToken",true);tag.putInt("AllowedTokens",127);tag.putBoolean("ChipSlot",true);tag.putInt("TokenLogo",0);tag.putInt("TokenBehavior",0);for(String key:new String[]{"TokenPulseUntil","TokenInsertStarted","TokenRejectedUntil","TokenInsertionSequence"})tag.putLong(key,0);tag.putBoolean("TokenToggleApplied",false);tag.putBoolean("TokenToggled",false);return tag;}
 private static boolean surfaces(CompoundTag root,boolean chip){if(root.contains(NativeScreenItemSettings.PLACEMENT_RECOVERY)||root.contains(NativeScreenItemSettings.RECOVERY))return false;for(String key:new String[]{"PrimarySurfaceTexture","SecondarySurfaceTexture"})if(root.contains(key,Tag.TAG_INT)&&(root.getInt(key)==-3||!chip&&root.getInt(key)==-4))return false;return true;}
 /** Hosts without an owned-reader codec may install only the proven empty Source envelope. */
 public static boolean installable(CompoundTag root){if(!surfaces(root,false)||root.contains("ScreenChipReader")&&!root.contains("ScreenChipReader",Tag.TAG_COMPOUND))return false;if(!root.contains("ScreenChipReader"))return true;CompoundTag reader=root.getCompound("ScreenChipReader");for(String key:reader.getAllKeys())if(!EMPTY.contains(key)||!EMPTY.get(key).equals(reader.get(key)))return false;return true;}
 /** The Screen host must validate and stage the complete owned-reader payload before installing it. */
 public static boolean screenStorageInstallable(CompoundTag root){return surfaces(root,true)&&(!root.contains("ScreenChipReader")||root.contains("ScreenChipReader",Tag.TAG_COMPOUND));}
}
