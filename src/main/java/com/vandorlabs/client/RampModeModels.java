package com.vandorlabs.client;

import com.vandorlabs.blocks.*;
import net.minecraft.item.Item;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.event.ModelRegistryEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;

/** Picked/configured controller items keep the same mode front as their placed counterpart. */
@Mod.EventBusSubscriber(modid="vandorlabs",value=Side.CLIENT)
public final class RampModeModels {
    @SubscribeEvent public static void register(ModelRegistryEvent event){
        Item.getItemFromBlock(ModBlocks.PROGRAMMABLE_RAMP).addPropertyOverride(new ResourceLocation("vandorlabs","ramp_mode"),
                (stack,world,entity)->BlockRampController.modeIndex(stack.getSubCompound("RampSettings")));
    }
    private RampModeModels(){}
}
