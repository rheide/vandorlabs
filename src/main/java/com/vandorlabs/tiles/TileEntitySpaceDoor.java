package com.vandorlabs.tiles;

import com.vandorlabs.blocks.BlockConnectingDetailedDoor;
import com.vandorlabs.blocks.BlockSpaceDoor;
import com.vandorlabs.blocks.BlockVandorDoor;
import net.minecraft.block.Block;
import net.minecraft.block.BlockDoor;
import net.minecraft.block.state.IBlockState;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;

/** Appearance belongs to the placed door, not a separate registered block. */
public class TileEntitySpaceDoor extends TileEntitySlidingDoor {
    public static final String[] DESIGNS={"observation","airlock","standard","security","reactor","viewport","laboratory","cargo","ventilation",
            "cargo_lift","blast_shield","glazed_hangar","quarantine_seal","reactor_barrier","modular_shutter"};
    public static final String[] DETAILS={"low","medium","high"};
    private int design=2, detail=1;
    private int slideDirection;
    private boolean framed=true, sliding;
    private boolean middle;
    private boolean migrateLegacyMotion;
    public boolean isMiddle() { return middle; }
    public static final double MIDDLE_OFFSET = -5.24/16.0;
    public int getDesign() { return design; }
    public int getDetail() { return detail; }
    public boolean isFramed() { return framed; }
    public boolean isSliding() { migrateLegacyMotion(); return sliding; }
    public int getSlideDirection() { return slideDirection; }
    public static boolean validSlideDirection(int value) { return value>=0 && value<=2; }
    public double verticalTravel() {
        return com.vandorlabs.persistence.SpaceDoorData.verticalTravel(framed,slideDirection);
    }
    /** Model-space depth offset: rotating art is edge-native, sliding art is centre-native. */
    public double positionOffset() {
        return com.vandorlabs.persistence.SpaceDoorData.positionOffset(isSliding(),middle);
    }
    public static boolean valid(int design,int detail) { return design>=0 && design<DESIGNS.length && detail>=0 && detail<DETAILS.length; }

    public BlockSpaceDoor model(boolean sliding) {
        return (BlockSpaceDoor)Block.REGISTRY.getObject(new ResourceLocation("vandorlabs",
                "space_standard"+(sliding?"_sliding_":"_rotating_")+(framed?"framed":"bare")));
    }
    public static int metadata(int design,int detail,boolean framed,boolean paired,boolean right,int part) {
        return 1+((design*3+detail)*2+(framed?1:0))*12+(paired?6:0)+part*2+(right?1:0);
    }
    public static int metadata(int design,int detail,boolean framed,boolean paired,boolean right,int part,boolean sliding) {
        return metadata(design,detail,framed,paired,right,part)+(sliding?1080:0);
    }
    public int metadata(boolean paired,boolean right,int part) {
        return metadata(design,detail,framed,paired,right,part,isSliding());
    }
    public BlockPos mate() {
        if (world==null) return null;
        IBlockState state=world.getBlockState(pos);
        if (!(state.getBlock() instanceof BlockConnectingDetailedDoor)) return null;
        state=state.getBlock().getActualState(state,world,pos);
        if (!state.getValue(BlockConnectingDetailedDoor.PAIRED)) return null;
        return pos.offset(state.getValue(BlockVandorDoor.HINGE)==BlockDoor.EnumHingePosition.LEFT
                ?state.getValue(BlockVandorDoor.FACING).rotateY():state.getValue(BlockVandorDoor.FACING).rotateYCCW());
    }
    public void configure(int design,int detail,boolean framed) {
        configure(design,detail,framed,slideDirection);
    }
    public void configure(int design,int detail,boolean framed,int direction) {
        configure(design,detail,framed,direction,middle);
    }
    public void configure(int design,int detail,boolean framed,int direction,boolean middle) {
        configure(design,detail,framed,direction,middle,sliding);
    }
    public void configure(int design,int detail,boolean framed,int direction,boolean middle,boolean sliding) {
        if (!valid(design,detail) || !validSlideDirection(direction)) return;
        this.design=design; this.detail=detail; this.framed=framed;
        this.slideDirection=direction;
        this.middle=middle; this.sliding=sliding; this.migrateLegacyMotion=false;
        markDirty();
        if (world!=null) {
            IBlockState state=world.getBlockState(pos);
            world.notifyBlockUpdate(pos,state,state,2);
        }
    }
    @Override public NBTTagCompound writeToNBT(NBTTagCompound tag) {
        super.writeToNBT(tag);
        new com.vandorlabs.persistence.SpaceDoorData(design,detail,framed,slideDirection,middle,sliding)
                .write(new com.vandorlabs.persistence.NbtPrimitiveData(tag));
        return tag;
    }
    @Override public void readFromNBT(NBTTagCompound tag) {
        super.readFromNBT(tag);
        com.vandorlabs.persistence.SpaceDoorData data=com.vandorlabs.persistence.SpaceDoorData.read(
                new com.vandorlabs.persistence.NbtPrimitiveData(tag));
        design=data.design; detail=data.detail; framed=data.framed; slideDirection=data.direction;
        middle=data.middle; sliding=data.sliding;
        migrateLegacyMotion=!tag.hasKey("SpaceDoorSchema");
    }
    private void migrateLegacyMotion() {
        if (!migrateLegacyMotion || world==null) return;
        net.minecraft.block.Block block=world.getBlockState(pos).getBlock();
        if (block instanceof com.vandorlabs.blocks.BlockDetailedDoor) {
            sliding=((com.vandorlabs.blocks.BlockDetailedDoor)block).isSlidingModel();
            // The old sliding block's native placement was the centre track.
            if (sliding) middle=true;
        }
        migrateLegacyMotion=false;
        if (!world.isRemote) markDirty();
    }
    @Override public void onLoad() { super.onLoad(); migrateLegacyMotion(); }
    public static boolean hasGlassDesign(int design) { return design==0 || design==5 || design==6 || design==11; }
    public boolean hasGlass() { return hasGlassDesign(design); }
    @Override public boolean shouldRenderInPass(int pass) { return pass==0 || pass==1 && hasGlass(); }
    @Override public net.minecraft.util.math.AxisAlignedBB getRenderBoundingBox() {
        return slideDirection==0?super.getRenderBoundingBox():
                new net.minecraft.util.math.AxisAlignedBB(pos.add(-1,-2,-1),pos.add(2,4,2));
    }
}
