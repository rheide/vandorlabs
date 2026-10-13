package com.vandorlabs.client;

/** Run surface coverage and atlas-boundary checks before starting the live renderer. */
public final class CanopyMaterialUvChecks {
    public static void main(String[] args){net.minecraft.init.Bootstrap.register();LargeCanopyRuntimeChecks.materialUvs();}
}
