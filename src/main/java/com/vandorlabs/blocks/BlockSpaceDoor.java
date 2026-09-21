package com.vandorlabs.blocks;

/** Independent Space assets with shared door interaction and connection rules. */
public class BlockSpaceDoor extends BlockConnectingDetailedDoor {
    @Override public net.minecraft.util.math.AxisAlignedBB getBoundingBox(
            net.minecraft.block.state.IBlockState state,net.minecraft.world.IBlockAccess world,
            net.minecraft.util.math.BlockPos pos) {
        net.minecraft.block.state.IBlockState actual=getActualState(state,world,pos);
        net.minecraft.util.math.AxisAlignedBB box=super.getBoundingBox(actual,world,pos);
        if (!isSlidingModel() && actual.getValue(OPEN)) {
            // Thick leaf occupies five model pixels along its hinge-side edge.
            if (box.maxX==.25) return new net.minecraft.util.math.AxisAlignedBB(0,0,0,5.0/16,1,1);
            if (box.minX==.75) return new net.minecraft.util.math.AxisAlignedBB(11.0/16,0,0,1,1,1);
            if (box.maxZ==.25) return new net.minecraft.util.math.AxisAlignedBB(0,0,0,1,1,5.0/16);
            if (box.minZ==.75) return new net.minecraft.util.math.AxisAlignedBB(0,0,11.0/16,1,1,1);
        }
        return box;
    }
    public boolean hasGlass() {
        return getRegistryName().getResourcePath().startsWith("space_observation_");
    }
    public BlockSpaceDoor(String name, boolean sliding, boolean framed,
            BlockDetailedDoor paired) {
        super(name, sliding ? DoorMotion.SLIDING : DoorMotion.HINGED,
                sliding, false, false, 2.5F, 13.5F,
                13.5F, 90, -90, -15, 15, paired);
    }

    @Override
    protected boolean canPairWith(BlockVandorDoor other) {
        return other == this;
    }

}
