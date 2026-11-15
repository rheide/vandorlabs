package com.vandorlabs.neoforge.surface;

import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Block;

/** Configured picks place ordinary claimed-free Surfaces, retaining only appearance. */
public final class NativeSurfaceItem extends BlockItem {
    public NativeSurfaceItem(Block block,Properties properties){super(block,properties);}
    @Override protected boolean placeBlock(BlockPlaceContext context,net.minecraft.world.level.block.state.BlockState state){
        var data=context.getItemInHand().get(DataComponents.CUSTOM_DATA);if(data!=null&&data.copyTag().contains("ProgrammableSurface")){var tag=data.copyTag().getCompound("ProgrammableSurface");if(!NativeSurfaceArtwork.valid(tag.getString("SurfaceOn"))||!NativeSurfaceArtwork.valid(tag.getString("SurfaceOff"))||tag.getInt("SurfaceLayout")<0||tag.getInt("SurfaceLayout")>2)return false;}
        return super.placeBlock(context,state);
    }
}
