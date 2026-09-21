package com.vandorlabs.blocks;

import com.vandorlabs.VandorLabs;
import net.minecraft.block.BlockGlass;
import net.minecraft.block.SoundType;
import net.minecraft.block.material.Material;
import net.minecraft.util.BlockRenderLayer;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/** Frameless, full-cube cockpit glazing with partially transparent artwork. */
public class BlockCockpitGlass extends BlockGlass {

    public BlockCockpitGlass(String name) {
        super(Material.GLASS, false);
        setRegistryName(name);
        setUnlocalizedName(VandorLabs.MODID + "." + name);
        setCreativeTab(VandorLabs.VANDOR_LABS_TAB);
        setHardness(0.3F);
        setSoundType(SoundType.GLASS);
        setLightOpacity(0);
    }

    @Override
    @SideOnly(Side.CLIENT)
    public BlockRenderLayer getBlockLayer() {
        return BlockRenderLayer.TRANSLUCENT;
    }
}
