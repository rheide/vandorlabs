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
            "cargo_lift","blast_shield","glazed_hangar","quarantine_seal","reactor_barrier","modular_shutter","white_glass","dark_glass","plain_cargo","stepped_freight","observation_leaf","reinforced_leaf","warehouse_shutter","slotted_bay","cross_braced_bay","split_view_bay","offset_cargo","twin_observation","armored_biparting","service_freight"};
    public static final int FIRST_DOUBLE_DESIGN=21, DEFAULT_LARGE_DESIGN=23;
    public static final String[] DETAILS={"low","medium"};
    private static final ResourceLocation[] MOTION_MODELS = new ResourceLocation[4];
    private static final java.util.concurrent.atomic.AtomicReferenceArray<BlockSpaceDoor>
            RESOLVED_MODELS = new java.util.concurrent.atomic.AtomicReferenceArray<>(4);
    static {
        for (int i = 0; i < MOTION_MODELS.length; i++)
            MOTION_MODELS[i] = new ResourceLocation("vandorlabs",
                    modelId(2, (i & 2) != 0, (i & 1) != 0));
    }
    private int design=2, detail=1,faceTexture=-1;
    private boolean tileTexture;
    public boolean isTileTexture(){return tileTexture;}
    public void setTileTexture(boolean value){if(tileTexture==value)return;tileTexture=value;markDirty();if(world!=null)world.notifyBlockUpdate(pos,world.getBlockState(pos),world.getBlockState(pos),2);}
    public int getFaceTexture(){return faceTexture;}
    public void setFaceTexture(int choice){faceTexture=choice<0?-1:ScreenHousingTextures.clamp(choice);markDirty();if(world!=null){IBlockState state=world.getBlockState(pos);world.notifyBlockUpdate(pos,state,state,2);}}

    private int slideDirection;
    private boolean framed=true, sliding;
    private boolean middle, hinges=true, panel=true;
    /** 0 centre, 1 near edge, 2 far edge. */
    private int placementDepth = 1;
    private int trigger=com.vandorlabs.persistence.SpaceDoorData.TRIGGER_DISABLED;
    public int getTrigger() { return trigger; }
    public boolean hasHinges() { return hinges; }
    public boolean hasPanel() { return panel; }
    private boolean migrateLegacyMotion;
    public boolean isMiddle() { migrateLegacyMotion(); return middle; }
    public static final double MIDDLE_OFFSET = -5.24/16.0;
    public int getDesign() { return design; }
    public int getDetail() { return detail; }
    public boolean isFramed() { return framed; }
    public boolean isSliding() { migrateLegacyMotion(); return sliding; }
    public int getSlideDirection() { return slideDirection; }
    public static boolean validSlideDirection(int value) { return value>=0 && value<=3; }
    public boolean isXSplit(){return this instanceof TileEntityLargeProgrammableDoor && isSliding() && slideDirection==3;}
    public double verticalTravel() {
        return com.vandorlabs.persistence.SpaceDoorData.verticalTravel(framed,slideDirection);
    }
    /** Model-space depth offset for centre and flush near/far edge placement. */
    public double positionOffset() {
        return com.vandorlabs.persistence.SpaceDoorData.positionOffset(isSliding(),framed,hinges,placementDepth);
    }
    public int getPlacementDepth() { return placementDepth; }
    public void setPlacementDepth(int depth) {
        if (depth < 0 || depth > 2) return;
        placementDepth = depth;
        middle = depth == 0;
        markDirty();
        if (world != null && pos != null) {
            IBlockState state = world.getBlockState(pos);
            world.notifyBlockUpdate(pos, state, state, 3);
        }
    }
    public boolean acceptsDesign(int design) { return design<FIRST_DOUBLE_DESIGN || this instanceof TileEntityLargeProgrammableDoor; }
    public static boolean valid(int design,int detail) { return design>=0 && design<DESIGNS.length && detail>=0 && detail<3; }

    public BlockSpaceDoor model(boolean sliding) {
        int key = (sliding ? 2 : 0) | (framed ? 1 : 0);
        BlockSpaceDoor resolved = RESOLVED_MODELS.get(key);
        if (resolved != null) return resolved;
        resolved = (BlockSpaceDoor)Block.REGISTRY.getObject(MOTION_MODELS[key]);
        if (resolved != null) RESOLVED_MODELS.compareAndSet(key, null, resolved);
        return resolved;
    }
    public static String modelId(int design, boolean sliding, boolean framed) {
        String family = DESIGNS[design];
        String prefix = "space_" + ("reactor".equals(family) ? "reactor_service" : family)
                + (sliding ? "_sliding" : "_rotating");
        return prefix + (design < 5 ? "_door_" : "_") + (framed ? "framed" : "bare");
    }
    public static int metadata(int design,int detail,boolean framed,boolean paired,boolean right,int part) {
        return (design<15?1+design*72:4321+(design-15)*72)+((detail)*2+(framed?1:0))*12+(paired?6:0)+part*2+(right?1:0);
    }
    public static int metadata(int design,int detail,boolean framed,boolean paired,boolean right,int part,boolean sliding) {
        return metadata(design,detail,framed,paired,right,part)+(sliding?1080:0);
    }
    public int metadata(boolean paired,boolean right,int part) {
        return metadata(design,detail,framed,paired,right,part,isSliding())+(!isSliding() && !hinges?2160:0);
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
        configure(design,detail,framed,direction,middle,sliding,hinges);
    }
    public void configure(int design,int detail,boolean framed,int direction,boolean middle,boolean sliding,boolean hinges) {
        configure(design,detail,framed,direction,middle,sliding,hinges,trigger);
    }
    public void configure(int design,int detail,boolean framed,int direction,boolean middle,boolean sliding,boolean hinges,int trigger) {
        configure(design,detail,framed,direction,middle,sliding,hinges,trigger,panel);
    }
    public void configure(int design,int detail,boolean framed,int direction,boolean middle,boolean sliding,boolean hinges,int trigger,boolean panel) {
        if (!valid(design,detail) || !acceptsDesign(design) || direction==3 && !(this instanceof TileEntityLargeProgrammableDoor) || !validSlideDirection(direction)
                || !com.vandorlabs.persistence.SpaceDoorData.validTrigger(trigger)) return;
        this.design=design; this.detail=Math.min(detail,1); this.framed=framed;
        this.slideDirection=direction;
        if (this.middle != middle) placementDepth = middle ? 0 : 1;
        this.middle=middle; this.sliding=sliding; this.migrateLegacyMotion=false;
        this.hinges=hinges;
        this.trigger=trigger;
        this.panel=panel;
        markDirty();
        if (world!=null) {
            IBlockState state=world.getBlockState(pos);
            if (!world.isRemote && state.getBlock() instanceof BlockSpaceDoor)
                ((BlockSpaceDoor)state.getBlock()).updateRedstoneState(world,pos,state);
            world.notifyBlockUpdate(pos,state,state,2);
        }
    }
    @Override public NBTTagCompound writeToNBT(NBTTagCompound tag) {
        migrateLegacyMotion();
        super.writeToNBT(tag);
        new com.vandorlabs.persistence.SpaceDoorData(design,detail,framed,slideDirection,middle,sliding,hinges,trigger,panel)
                .write(new com.vandorlabs.persistence.NbtPrimitiveData(tag));
        tag.setInteger("DoorFaceTexture",faceTexture);tag.setBoolean("DoorTileTexture",tileTexture);
        tag.setInteger("SpaceDoorPlacementDepth", placementDepth);
        return tag;
    }
    /** Copy user choices only, not tile coordinates, power or animation state. */
    public NBTTagCompound itemSettings() {
        migrateLegacyMotion();
        NBTTagCompound tag=new NBTTagCompound();
        new com.vandorlabs.persistence.SpaceDoorData(design,detail,framed,slideDirection,middle,sliding,hinges,trigger,panel)
                .write(new com.vandorlabs.persistence.NbtPrimitiveData(tag));
        tag.setInteger("DoorFaceTexture",faceTexture);tag.setBoolean("DoorTileTexture",tileTexture);
        tag.setInteger("SpaceDoorChannel",getRedstoneChannel());
        com.vandorlabs.redstone.ChannelData.write(tag,getRedstoneChannels());
        return tag;
    }
    public void applyItemSettings(NBTTagCompound tag) {
        setTileTexture(tag.getBoolean("DoorTileTexture"));
        setFaceTexture(tag.hasKey("DoorFaceTexture",3)?tag.getInteger("DoorFaceTexture"):-1);
        com.vandorlabs.persistence.SpaceDoorData data=com.vandorlabs.persistence.SpaceDoorData.read(
                new com.vandorlabs.persistence.NbtPrimitiveData(tag));
        configure(data.design,data.detail,data.framed,data.direction,data.middle,data.sliding,data.hinges,data.trigger,data.panel);
        setRedstoneChannels(com.vandorlabs.redstone.ChannelData.read(tag,tag.getInteger("SpaceDoorChannel")));
    }
    @Override public void readFromNBT(NBTTagCompound tag) {
        super.readFromNBT(tag);
        tileTexture=tag.getBoolean("DoorTileTexture");
        faceTexture=tag.hasKey("DoorFaceTexture",3) && tag.getInteger("DoorFaceTexture")>=0?ScreenHousingTextures.clamp(tag.getInteger("DoorFaceTexture")):-1;
        com.vandorlabs.persistence.SpaceDoorData data=com.vandorlabs.persistence.SpaceDoorData.read(
                new com.vandorlabs.persistence.NbtPrimitiveData(tag));
        design=data.design; detail=data.detail; framed=data.framed; slideDirection=data.direction;
        middle=data.middle; sliding=data.sliding;
        placementDepth = tag.hasKey("SpaceDoorPlacementDepth", 3)
                ? Math.max(0, Math.min(2, tag.getInteger("SpaceDoorPlacementDepth")))
                : (middle ? 0 : 1);
        hinges=data.hinges;
        trigger=data.trigger;
        panel=data.panel;
        migrateLegacyMotion=!tag.hasKey("SpaceDoorSchema");
    }
    private void migrateLegacyMotion() {
        if (!migrateLegacyMotion || world==null) return;
        net.minecraft.block.Block block=world.getBlockState(pos).getBlock();
        if (block instanceof com.vandorlabs.blocks.BlockDetailedDoor) {
            sliding=((com.vandorlabs.blocks.BlockDetailedDoor)block).isSlidingModel();
            // The old sliding block's native placement was the centre track.
            if (sliding) { middle=true; placementDepth=0; }
        }
        migrateLegacyMotion=false;
        if (!world.isRemote) markDirty();
    }
    @Override public void onLoad() { super.onLoad(); migrateLegacyMotion(); }
    public static boolean hasGlassDesign(int design) { return design==0 || design==5 || design==6 || design==11 || design==15 || design==16; }
    public boolean hasGlass() { return faceTexture<0 && hasGlassDesign(design); }
    @Override
    @net.minecraftforge.fml.relauncher.SideOnly(net.minecraftforge.fml.relauncher.Side.CLIENT)
    public boolean hasFastRenderer() {
        return com.vandorlabs.client.OpaqueDoorBatch.available(this);
    }
    @Override public boolean shouldRenderInPass(int pass) {
        return isLowerDoor() && (pass == 0 || pass == 1 && hasGlass());
    }
    @Override public net.minecraft.util.math.AxisAlignedBB getRenderBoundingBox() {
        return slideDirection==0?super.getRenderBoundingBox():
                new net.minecraft.util.math.AxisAlignedBB(pos.add(-1,-2,-1),pos.add(2,4,2));
    }
}
