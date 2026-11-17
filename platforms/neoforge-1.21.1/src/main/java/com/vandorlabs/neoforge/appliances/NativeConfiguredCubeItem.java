package com.vandorlabs.neoforge.appliances;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Block;
/** The existing programmable cube item, including its normal placement and component behavior. */
public final class NativeConfiguredCubeItem extends BlockItem {
 public NativeConfiguredCubeItem(Block block,Properties properties){super(block,properties);}
 @Override public Component getName(ItemStack stack){var name=NativeAirConditionerPresets.name(stack);return name==null?super.getName(stack):name;}
}
