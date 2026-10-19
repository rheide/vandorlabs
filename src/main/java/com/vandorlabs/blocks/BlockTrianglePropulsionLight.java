package com.vandorlabs.blocks;

import com.vandorlabs.VandorLabs;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.Rotation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/**
 * One of four registered meshes that together form a click-positioned triangular
 * thruster. The programmable family item selects the sibling mesh from the
 * placement click in the facing's local X/Y plane.
 */
public class BlockTrianglePropulsionLight extends BlockPropulsionLight {
    private final String baseId;

    public BlockTrianglePropulsionLight(String name, String baseId) {
        super(name, false, 1.0F);
        this.baseId = baseId;
    }

    /** Vertical nozzles retain their normal under yaw, but their corner mesh must turn. */
    @Override public IBlockState withRotation(IBlockState state,Rotation rotation){
        EnumFacing facing=state.getValue(FACING);
        if(facing.getAxis()!=EnumFacing.Axis.Y)return super.withRotation(state,rotation);
        String id=getRegistryName().getResourcePath();boolean right=id.endsWith("_right"),top=id.contains("_top_");
        for(int i=0;i<rotation.ordinal();i++){boolean before=right;right=facing==EnumFacing.UP?!top:top;top=facing==EnumFacing.UP?before:!before;}
        String target=baseId+(top?(right?"_top_right":"_top_left"):(right?"_bottom_right":""));
        Block sibling=Block.getBlockFromName("vandorlabs:"+target);
        return sibling.getDefaultState().withProperty(FACING,facing).withProperty(POWERED,state.getValue(POWERED));
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

}
