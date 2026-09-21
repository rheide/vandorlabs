package com.vandorlabs.blocks;

import com.vandorlabs.VandorLabs;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.world.World;

import java.util.Random;

/**
 * One of four registered meshes that together form a click-positioned triangular
 * thruster. Only the bottom-left block has an ItemBlock; placement of that item
 * selects the appropriate sibling mesh in the facing's local X/Y plane.
 */
public class BlockTrianglePropulsionLight extends BlockPropulsionLight {
    private final String baseId;

    public BlockTrianglePropulsionLight(String name, String baseId) {
        super(name, false, 1.0F);
        this.baseId = baseId;
    }

    @Override public IBlockState getStateForPlacement(World world, BlockPos pos,
            EnumFacing facing, float hitX, float hitY, float hitZ, int meta,
            EntityLivingBase placer, EnumHand hand) {
        Block selected = Block.REGISTRY.getObject(new ResourceLocation(VandorLabs.MODID,
                variantIdForHit(baseId, facing, hitX, hitY, hitZ)));
        if (!(selected instanceof BlockTrianglePropulsionLight)) selected = this;
        return selected.getDefaultState().withProperty(FACING, facing)
                .withProperty(POWERED, true);
    }

    /** Resolve hit coordinates into the model's local +X-right/+Y-up frame. */
    public static String variantIdForHit(String baseId, EnumFacing facing,
            float hitX, float hitY, float hitZ) {
        float localX;
        float localY;
        switch (facing) {
            case SOUTH:
                localX = 1.0F - hitX;
                localY = hitY;
                break;
            case EAST:
                localX = hitZ;
                localY = hitY;
                break;
            case WEST:
                localX = 1.0F - hitZ;
                localY = hitY;
                break;
            case UP:
                localX = hitX;
                localY = hitZ;
                break;
            case DOWN:
                localX = hitX;
                localY = 1.0F - hitZ;
                break;
            case NORTH:
            default:
                localX = hitX;
                localY = hitY;
                break;
        }

        boolean right = localX >= 0.5F;
        boolean top = localY >= 0.5F;
        if (top) return baseId + (right ? "_top_right" : "_top_left");
        return right ? baseId + "_bottom_right" : baseId;
    }

    private Block baseBlock() {
        Block block = Block.REGISTRY.getObject(new ResourceLocation(VandorLabs.MODID, baseId));
        return block instanceof BlockTrianglePropulsionLight ? block : this;
    }

    @Override public Item getItemDropped(IBlockState state, Random random, int fortune) {
        return Item.getItemFromBlock(baseBlock());
    }

    @Override public ItemStack getPickBlock(IBlockState state, RayTraceResult target,
            World world, BlockPos pos, EntityPlayer player) {
        return new ItemStack(baseBlock());
    }
}
