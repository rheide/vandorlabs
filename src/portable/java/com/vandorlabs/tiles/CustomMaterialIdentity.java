package com.vandorlabs.tiles;

import java.nio.charset.StandardCharsets;
import java.util.zip.CRC32;

/** Stable saved identity for a legacy registry-name/metadata material sample. */
public final class CustomMaterialIdentity {
    private CustomMaterialIdentity(){}
    /** Caller owns metadata normalization; persisted identities must never be silently rehashed. */
    public static int identifier(String registryName,int metadata){
        CRC32 crc=new CRC32();crc.update((registryName+"@"+metadata).getBytes(StandardCharsets.UTF_8));
        return MaterialChoicePolicy.CUSTOM_BASE|((int)crc.getValue()&0x1fffffff);
    }
}
