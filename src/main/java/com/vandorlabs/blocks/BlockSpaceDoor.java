package com.vandorlabs.blocks;

/** Independent Space assets with shared door interaction and connection rules. */
public class BlockSpaceDoor extends BlockConnectingDetailedDoor {
    public static final float PIN_X=1F, PIN_Z=11.24F;
    private final boolean framed;
    @Override public net.minecraft.util.math.AxisAlignedBB getBoundingBox(
            net.minecraft.block.state.IBlockState state,net.minecraft.world.IBlockAccess world,
            net.minecraft.util.math.BlockPos pos) {
        net.minecraft.block.state.IBlockState actual=getActualState(state,world,pos);
        return spaceBounds(actual,world,pos);
    }
    /** Accept the placed block's resolved state, even when this is a model prototype. */
    public net.minecraft.util.math.AxisAlignedBB spaceBounds(net.minecraft.block.state.IBlockState actual,
            net.minecraft.world.IBlockAccess world,net.minecraft.util.math.BlockPos pos) {
        if (isSlidingModel() || !actual.getValue(OPEN)) return super.getBoundingBox(actual,world,pos);
        double x0=framed?1:0;
        double x1=framed && !actual.getValue(PAIRED)?15:16;
        // Exact 90-degree bounds of the single rectangular slab. Moving the
        // original hinge to the slab face also moves the true rotation axis.
        double a=(PIN_X+12.24-PIN_Z)/16, b=(PIN_X+14.24-PIN_Z)/16;
        double c=(PIN_Z-x1+PIN_X)/16, d=(PIN_Z-x0+PIN_X)/16;
        if (actual.getValue(HINGE)==net.minecraft.block.BlockDoor.EnumHingePosition.LEFT) {
            double oldA=a; a=1-b; b=1-oldA;
        }
        switch (actual.getValue(FACING)) {
            case NORTH: return new net.minecraft.util.math.AxisAlignedBB(1-b,0,1-d,1-a,1,1-c);
            case EAST: return new net.minecraft.util.math.AxisAlignedBB(c,0,1-b,d,1,1-a);
            case WEST: return new net.minecraft.util.math.AxisAlignedBB(1-d,0,a,1-c,1,b);
            default: return new net.minecraft.util.math.AxisAlignedBB(a,0,c,b,1,d);
        }
    }
    public boolean hasGlass() {
        return getRegistryName().getResourcePath().startsWith("space_observation_");
    }
    public BlockSpaceDoor(String name, boolean sliding, boolean framed,
            BlockDetailedDoor paired) {
        super(name, sliding ? DoorMotion.SLIDING : DoorMotion.HINGED,
                sliding, false, false, PIN_X, 16-PIN_X,
                PIN_Z, 90, -90, -15, 15, paired);
        this.framed=framed;
    }

    @Override
    protected boolean canPairWith(BlockVandorDoor other) {
        return other == this;
    }

}
