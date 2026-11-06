package com.vandorlabs.blocks;

/** Independent Space assets with shared door interaction and connection rules. */
public class BlockSpaceDoor extends BlockConnectingDetailedDoor {
    public static final float PIN_X=com.vandorlabs.render.SpaceDoorBounds.PIN_X, PIN_Z=com.vandorlabs.render.SpaceDoorBounds.PIN_Z;
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
        return com.vandorlabs.render.SpaceDoorBounds.rotatingOpen(SPACE_BOXES,actual.getValue(FACING).getIndex(),actual.getValue(HINGE)==net.minecraft.block.BlockDoor.EnumHingePosition.LEFT,framed,actual.getValue(PAIRED));
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
