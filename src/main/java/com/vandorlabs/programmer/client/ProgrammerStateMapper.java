package com.vandorlabs.programmer.client;

import net.minecraft.block.state.IBlockState;
import net.minecraft.client.renderer.block.model.ModelResourceLocation;
import net.minecraft.client.renderer.block.statemap.StateMapperBase;
import net.minecraftforge.fml.relauncher.*;

@SideOnly(Side.CLIENT)
public final class ProgrammerStateMapper extends StateMapperBase {
    @Override protected ModelResourceLocation getModelResourceLocation(IBlockState state){return new ModelResourceLocation("vandorlabs:data_chip_programmer","normal");}
}
