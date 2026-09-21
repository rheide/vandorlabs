package com.vandorlabs.blocks;

import com.vandorlabs.VandorLabs;
import net.minecraft.block.Block;
import net.minecraft.block.SoundType;
import net.minecraft.block.material.Material;

public class BlockVandor extends Block {

    public BlockVandor(String name) {
        this(name, 0.0F);
    }

    public BlockVandor(String name, float lightLevel) {
        super(Material.IRON);
        setRegistryName(name);
        setUnlocalizedName(VandorLabs.MODID + "." + name);
        setCreativeTab(VandorLabs.VANDOR_LABS_TAB);
        setHardness(5.0F);
        setResistance(10.0F);
        setSoundType(SoundType.METAL);
        setHarvestLevel("pickaxe", 1);
        setLightLevel(lightLevel);
    }
}
