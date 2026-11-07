package com.vandorlabs.neoforge.storage;

import com.vandorlabs.neoforge.ModernFinishCatalog;
import com.vandorlabs.neoforge.ModernHousingTags;
import com.vandorlabs.persistence.HousingSettings;
import net.minecraft.nbt.*;

/** Housing-only Storage projection. Inventory and inherited owned modules require separate installation. */
public record NativeStorageSettings(HousingSettings housing) {
 public NativeStorageSettings {java.util.Objects.requireNonNull(housing);}
 public static final int SLOTS=27,STACK_LIMIT=64;
 private static final int DEFAULT=defaultChoice();
 private static int defaultChoice(){for(int i=0;i<ModernFinishCatalog.ENTRIES.size();i++)if(ModernFinishCatalog.ENTRIES.get(i).id().equals("storage_cabinet"))return i;throw new IllegalStateException("Missing Storage Cabinet material");}
 public static NativeStorageSettings fresh(){return new NativeStorageSettings(HousingSettings.read(new ModernHousingTags(new CompoundTag()),DEFAULT,false,ModernFinishCatalog::storedChoice,ModernFinishCatalog::validChoice));}
 public static NativeStorageSettings read(CompoundTag input){return read(input,fresh());}
 public static NativeStorageSettings read(CompoundTag input,NativeStorageSettings previous){var copy=input.copy();if(!copy.contains(HousingSettings.MAIN,Tag.TAG_INT))copy.putInt(HousingSettings.MAIN,DEFAULT);return new NativeStorageSettings(HousingSettings.read(new ModernHousingTags(copy),previous.housing.mainTexture,previous.housing.tileSides,ModernFinishCatalog::storedChoice,ModernFinishCatalog::validChoice));}
 /** Vanilla ItemBlock merges into a freshly saved, versioned factory before decoding. */
 public static NativeStorageSettings item(CompoundTag supplied){var merged=new CompoundTag();fresh().write(merged);if(supplied!=null)merged.merge(supplied.copy());return read(merged);}
 public void write(CompoundTag target){housing.write(new ModernHousingTags(target));}
 public static final class Saved {
  private final CompoundTag original;public final NativeStorageSettings settings;
  public Saved(CompoundTag tag){original=tag.copy();settings=read(original);}
  public CompoundTag unchanged(){return original.copy();}
  /** An appearance edit must preserve the inventory and every opaque sibling field. */
  public CompoundTag edited(NativeStorageSettings next){var changed=original.copy();next.write(changed);return changed;}
 }
}
