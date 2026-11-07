package com.vandorlabs.neoforge.storage;
/** Smoke-only selection; checking installation must not initialize inactive deferred definitions. */
public final class StorageCanonicalMode {
 public static boolean requested(){return "true".equals(System.getenv("VANDOR_STORAGE_CANONICAL_CHECK"));}
 public static boolean installed(){return net.minecraft.core.registries.BuiltInRegistries.BLOCK.get(net.minecraft.resources.ResourceLocation.parse("vandorlabs:programmable_storage")) instanceof NativeStorageBlock;}
 public static void require(){if(!installed())throw new IllegalStateException("Canonical Storage fixture requires the explicit activation candidate");}
 private StorageCanonicalMode(){}
}
