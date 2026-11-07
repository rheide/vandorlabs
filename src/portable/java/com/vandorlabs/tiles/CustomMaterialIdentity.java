package com.vandorlabs.tiles;

import java.nio.charset.StandardCharsets;
import java.util.zip.CRC32;

/** Stable saved identity for a legacy registry-name/metadata material sample. */
public final class CustomMaterialIdentity {
    private CustomMaterialIdentity(){}
    /** Caller owns metadata normalization; persisted identities must never be silently rehashed. */
    public static int identifier(String registryName,int metadata){
        return hash(registryName+"@"+metadata);
    }
    /** New flattened default-state samples use a version-independent domain; old saved IDs stay unchanged. */
    public static int flattenedBlockIdentifier(String registryName){return hash("flattened-block:"+registryName+"@0");}
    /** Explicit non-default native states have a separate, property-ordered domain. */
    public static int flattenedStateIdentifier(String registryName,java.util.Map<String,String> properties){
        java.util.Objects.requireNonNull(registryName,"registryName");
        StringBuilder preimage=new StringBuilder("flattened-state:").append(registryName).append('[');
        boolean separator=false;
        for(java.util.Map.Entry<String,String> entry:new java.util.TreeMap<String,String>(properties).entrySet()){
            if(!propertyToken(entry.getKey(),false)||!propertyToken(entry.getValue(),true))throw new IllegalArgumentException("Invalid state property");
            if(separator)preimage.append(',');separator=true;preimage.append(entry.getKey()).append('=').append(entry.getValue());
        }
        return hash(preimage.append(']').toString());
    }
    private static boolean propertyToken(String value,boolean signed){
        if(value==null||value.isEmpty())return false;
        for(int i=0;i<value.length();i++){char c=value.charAt(i);if(c!='_'&&!(signed&&c=='-')&&(c<'a'||c>'z')&&(c<'0'||c>'9'))return false;}
        return true;
    }
    private static int hash(String preimage){
        CRC32 crc=new CRC32();crc.update(preimage.getBytes(StandardCharsets.UTF_8));
        return MaterialChoicePolicy.CUSTOM_BASE|((int)crc.getValue()&0x1fffffff);
    }
}
