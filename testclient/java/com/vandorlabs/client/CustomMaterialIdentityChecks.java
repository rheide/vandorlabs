package com.vandorlabs.client;

import com.vandorlabs.tiles.CustomBlockMaterials;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;

/** Actual legacy item normalization and literal persisted identities, independent of the shared helper. */
public final class CustomMaterialIdentityChecks {
    private static void require(boolean value,String message){if(!value)throw new AssertionError(message);}
    public static void main(String[] ignored){
        net.minecraft.init.Bootstrap.register();
        require(CustomBlockMaterials.identifier("minecraft:stone",0)==0x3100e0dd,"string delegate golden");
        require(CustomBlockMaterials.identifier(Blocks.PLANKS,5)==0x35db6411,"actual registry block golden");
        require(CustomBlockMaterials.choice(new ItemStack(Blocks.PLANKS,1,17))==0x32b6a008,"legacy caller retains metadata mask");
        require(CustomBlockMaterials.choice(new ItemStack(Blocks.WOOL,1,14))==0x2998f6b8,"actual item metadata golden");
        require(CustomBlockMaterials.choice(new ItemStack(Blocks.WOOL,1,31))==0x3e9fc62e,"legacy caller masks to15");
        require(CustomBlockMaterials.identifier("minecraft:wooden_door",0)==0x33011d3d,"saved door identity golden");
        require(CustomBlockMaterials.choice(ItemStack.EMPTY)==-1,"empty item remains unavailable");
        require(CustomBlockMaterials.choice(new ItemStack(Items.STICK))==-1,"non-block item remains unavailable");
        System.out.println("PASS: actual legacy block/item saved-ID goldens and unchanged caller-owned ItemBlock metadata normalization");
    }
}
