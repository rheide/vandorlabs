package com.vandorlabs.neoforge;

import com.vandorlabs.tiles.CustomMaterialIdentity;
import net.minecraft.SharedConstants;

/** No renderer or mod loader: real Minecraft DFU plus literal saved IDs and strict missing guards. */
public final class LegacyVanillaMaterialAliasChecks {
    private static void require(boolean value,String message){if(!value)throw new AssertionError(message);}
    private static void named(LegacyVanillaMaterialAliases aliases,int choice,String name){
        var state=aliases.state(choice);require(state!=null&&name.equals(state.getString("Name")),"Saved alias "+choice+" -> "+state+", expected "+name);
    }
    public static void main(String[] ignored){
        SharedConstants.tryDetectVersion();var aliases=new LegacyVanillaMaterialAliases();
        require(aliases.size()==3913,"manifest alias count");
        named(aliases,0x3100e0dd,"minecraft:stone");named(aliases,0x35db6411,"minecraft:dark_oak_planks");
        named(aliases,0x2998f6b8,"minecraft:red_wool");named(aliases,0x33011d3d,"minecraft:oak_door");
        named(aliases,CustomMaterialIdentity.identifier("minecraft:grass_path",0),"minecraft:dirt_path");
        named(aliases,CustomMaterialIdentity.identifier("minecraft:tallgrass",1),"minecraft:short_grass");
        named(aliases,CustomMaterialIdentity.identifier("minecraft:log",12),"minecraft:oak_wood");
        var log=aliases.state(0x378edb2f);require("x".equals(log.getCompound("Properties").getString("axis")),"log orientation preserved");
        for(int metadata=7;metadata<16;metadata++)named(aliases,CustomMaterialIdentity.identifier("minecraft:stone",metadata),"minecraft:stone");
        named(aliases,CustomMaterialIdentity.identifier("minecraft:planks",15),"minecraft:oak_planks");
        var copy=aliases.state(0x3100e0dd);copy.putString("Name","example:mutated");named(aliases,0x3100e0dd,"minecraft:stone");
        for(int unknown:new int[]{-1,253,254,256,Integer.MAX_VALUE})require(aliases.state(unknown,0)==null,"unknown block must not silently become AIR: "+unknown);
        require(aliases.state(33,6)==null&&aliases.state(59,8)==null,"actual legacy unsupported piston/wheat metadata must remain missing");
        require(aliases.state(CustomMaterialIdentity.identifier("minecraft:piston",6))==null,"unsupported requested-meta CRC remains unresolved");
        require(aliases.state(1,-1)==null&&aliases.state(1,16)==null,"metadata must not wrap");
        require(aliases.state(CustomMaterialIdentity.identifier("example:missing",0))==null,"missing mod retains unresolved identity");
        require(aliases.state(CustomMaterialIdentity.identifier("minecraft:oak_planks",0))==null,"new name must not reverse-select arbitrary legacy metadata");
        int states=0;for(int block=0;block<256;block++)for(int metadata=0;metadata<16;metadata++)if(aliases.state(block,metadata)!=null)states++;
        require(states==aliases.size(),"all supported state aliases resolve");
        System.out.println("PASS: native legacy material aliases "+states+" actual DFU states; saved CRCs, late renames, properties, immutable cache and strict unknown/AIR guards");
    }
}
