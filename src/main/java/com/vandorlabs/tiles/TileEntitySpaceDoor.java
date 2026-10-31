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
    public final FrameAppearance frameAppearance=new FrameAppearance();
    private DoorGlassAppearance glassAppearance=DoorGlassAppearance.DEFAULT;
    public DoorGlassAppearance glassAppearance(){return glassAppearance;}
    public boolean supportsGlassAppearance(){return design==15||design==16||design==29;}
    public boolean hasGlassOverride(){return supportsGlassAppearance()&&glassAppearance.material!=DoorGlassAppearance.ORIGINAL;}
    public boolean opaqueGlass(){return hasGlassOverride()&&GlassMaterials.opaque(glassAppearance.material,glassAppearance.tint);}
    public void setGlassAppearance(DoorGlassAppearance appearance){if(appearance==null||glassAppearance.equals(appearance))return;glassAppearance=appearance;markDirty();if(world!=null)world.notifyBlockUpdate(pos,world.getBlockState(pos),world.getBlockState(pos),3);}


    // Runtime outlet ownership survives chunk reloads, but is excluded from itemSettings.
    private int waterOutlets;
    public int waterOutlets(){return waterOutlets;}
    public void setWaterOutlets(int mask){if(waterOutlets!=mask){waterOutlets=mask;markDirty();}}
    public final com.vandorlabs.blocks.ContainedFluids fluids=new com.vandorlabs.blocks.ContainedFluids(this);
    public static final String[] DESIGNS={"observation","airlock","standard","security","reactor","viewport","laboratory","cargo","ventilation",
            "cargo_lift","blast_shield","glazed_hangar","quarantine_seal","reactor_barrier","modular_shutter","white_glass","dark_glass","plain_cargo","stepped_freight","observation_leaf","reinforced_leaf","warehouse_shutter","slotted_bay","cross_braced_bay","split_view_bay","offset_cargo","twin_observation","armored_biparting","service_freight","slim_glass"};
    public static final int FIRST_DOUBLE_DESIGN=21, DEFAULT_LARGE_DESIGN=23;
    public static final String[] DETAILS={"low","low"};
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
    private int textureLayout=-1;
    public int getTextureLayout(){return textureLayout<0?(tileTexture?1:0):textureLayout;}
    public void setTextureLayout(int mode){if(!com.vandorlabs.render.TextureLayoutMode.valid(mode))throw new IllegalArgumentException("Invalid texture layout");if(getTextureLayout()==mode)return;tileTexture=mode==1;textureLayout=mode;markDirty();if(world!=null)world.notifyBlockUpdate(pos,world.getBlockState(pos),world.getBlockState(pos),3);}
    public boolean isTileTexture(){return tileTexture;}
    public void setTileTexture(boolean value){if(tileTexture==value)return;textureLayout=-1;tileTexture=value;markDirty();if(world!=null)world.notifyBlockUpdate(pos,world.getBlockState(pos),world.getBlockState(pos),2);}
    public int getFaceTexture(){return faceTexture;}
    public void setFaceTexture(int choice){faceTexture=choice<0?-1:ScreenHousingTextures.clamp(choice);markDirty();if(world!=null){IBlockState state=world.getBlockState(pos);world.notifyBlockUpdate(pos,state,state,2);}}

    private int slideDirection;
    private boolean framed=true, sliding;
    private boolean middle, hinges=true, panel=true;
    /** 0 centre, 1 near edge, 2 far edge. */
    private int placementDepth = 1;
    private int panelMode=1,securityMode;
    private boolean securityToggled;
    public static int securityBehavior(int mode){return mode==1?3:mode==3?1:mode==4?2:0;}
    public static String securityLabel(int mode){return mode==0?"Off":TileEntityTokenSlot.behaviorName(securityBehavior(mode));}
    public boolean securityToggle(){return securityToggled;}
    void toggleCredential(){securityToggled=!securityToggled;BlockPos other=mate();net.minecraft.tileentity.TileEntity raw=other==null?null:world.getTileEntity(other);if(raw instanceof TileEntitySpaceDoor&&((TileEntitySpaceDoor)raw).securityMode()==4){TileEntitySpaceDoor paired=(TileEntitySpaceDoor)raw;paired.securityToggled=securityToggled;paired.securityChanged();}securityChanged();}
    private final TileEntityTokenSlot keyReader=new TileEntityTokenSlot(false).forDoor(this,0),rearKeyReader=new TileEntityTokenSlot(false).forDoor(this,1);
    boolean usableReader(net.minecraft.entity.player.EntityPlayer player){
        if(world instanceof com.vandorlabs.vehicle.VehicleWorld){com.vandorlabs.vehicle.EntityGroundVehicle craft=((com.vandorlabs.vehicle.VehicleWorld)world).craft;return craft!=null&&!craft.isDead&&!craft.ownershipBlocked&&!craft.transferInProgress&&player.world==craft.world&&player.getDistanceSqToCenter(new BlockPos(craft.toWorld(new net.minecraft.util.math.Vec3d(pos).addVector(.5,.5,.5))))<=64;}
        return usable(player);
    }
    public int panelMode(){return panel?panelMode==2&&getPlacementDepth()==0?2:1:0;}
    public void setPanelMode(int value){panelMode=Math.max(0,Math.min(getPlacementDepth()==0?2:1,value));panel=panelMode>0;markDirty();}
    public int securityMode(){return securityMode;}
    public boolean isSecurityRoot(){return isLowerDoor();}
    public TileEntityTokenSlot keyReader(){return keyReader(0);}
    public TileEntityTokenSlot keyReader(int side){TileEntityTokenSlot reader=side==1?rearKeyReader:keyReader;if(world!=null){reader.setWorld(world);reader.setPos(pos);}return reader;}
    public void tickReaders(){keyReader(0).scheduledTick();keyReader(1).scheduledTick();}
    private boolean readerOpen(){return panelMode()>0&&keyReader(0).getOutputLevel()>0||panelMode()==2&&keyReader(1).getOutputLevel()>0;}
    public int readerIndex(net.minecraft.util.math.Vec3d hit){
        if(panelMode()!=2)return 0;net.minecraft.util.EnumFacing front=world.getBlockState(pos).getValue(BlockVandorDoor.FACING);
        double x=hit.x-pos.getX(),z=hit.z-pos.getZ(),normal=front==net.minecraft.util.EnumFacing.SOUTH?z:front==net.minecraft.util.EnumFacing.NORTH?1-z:front==net.minecraft.util.EnumFacing.EAST?x:1-x;
        double first=(com.vandorlabs.render.SpaceDoorControlPanel.z0(isSliding(),getPlacementDepth()==2)+1.5)/16+positionOffset();return Math.abs(normal-first)<=Math.abs(normal-(1-first))?0:1;
    }
    public boolean readerHit(net.minecraft.util.math.Vec3d hit,net.minecraft.util.EnumFacing face){
        if(!hasPanel()||world==null)return false;
        IBlockState state=world.getBlockState(pos);if(!(state.getBlock() instanceof com.vandorlabs.blocks.BlockConfigurableSpaceDoor))return false;
        state=state.getBlock().getActualState(state,world,pos);net.minecraft.util.EnumFacing front=state.getValue(BlockVandorDoor.FACING);
        com.vandorlabs.render.SpaceDoorControlPanel.Side side=this instanceof TileEntityLargeProgrammableDoor?(isSliding()?com.vandorlabs.render.SpaceDoorControlPanel.Side.RIGHT:com.vandorlabs.render.SpaceDoorControlPanel.Side.LEFT):com.vandorlabs.blocks.BlockConfigurableSpaceDoor.panelSide(world,pos,state);
        if(side==com.vandorlabs.render.SpaceDoorControlPanel.Side.NONE||face!=(side==com.vandorlabs.render.SpaceDoorControlPanel.Side.RIGHT?front.rotateY():front.rotateYCCW()))return false;
        double x=hit.x-pos.getX(),z=hit.z-pos.getZ(),normal=front==net.minecraft.util.EnumFacing.SOUTH?z:front==net.minecraft.util.EnumFacing.NORTH?1-z:front==net.minecraft.util.EnumFacing.EAST?x:1-x;
        return com.vandorlabs.blocks.BlockConfigurableSpaceDoor.panelContains(this,normal,hit.y-pos.getY(),this instanceof TileEntityLargeProgrammableDoor?1.5:1);
    }
    public static int singleCompany(int mask){mask&=127;return (mask&1)!=0?127:Integer.lowestOneBit(mask);}
    public void configureSecurity(int mode,int mask){mask=singleCompany(mask);if(securityMode!=mode||keyReader.effectiveAllowed()!=(mask&127))securityToggled=false;securityMode=Math.max(0,Math.min(4,mode));if(securityMode>0&&panelMode()==0)setPanelMode(1);keyReader(0).configure(false,mask,securityBehavior(securityMode));keyReader(1).configure(false,mask,securityBehavior(securityMode));securityChanged();}
    public boolean securityOpen(){
        if(readerOpen())return true;BlockPos other=mate();net.minecraft.tileentity.TileEntity tile=other==null?null:world.getTileEntity(other);
        return tile instanceof TileEntitySpaceDoor&&((TileEntitySpaceDoor)tile).securityMode()>0&&((TileEntitySpaceDoor)tile).readerOpen();
    }
    void securityChanged(){
        markDirty();if(world==null||world.isRemote||world.getTileEntity(pos)!=this)return;
        net.minecraft.block.state.IBlockState state=world.getBlockState(pos);
        if(world instanceof com.vandorlabs.vehicle.VehicleWorld){com.vandorlabs.vehicle.VehicleWorld view=(com.vandorlabs.vehicle.VehicleWorld)world;if(view.craft!=null)view.craft.signals.changed(pos);return;}
        if(state.getBlock() instanceof com.vandorlabs.blocks.BlockConfigurableSpaceDoor){((com.vandorlabs.blocks.BlockConfigurableSpaceDoor)state.getBlock()).updateRedstoneState(world,pos,state);BlockPos other=mate();if(other!=null){net.minecraft.block.state.IBlockState paired=world.getBlockState(other);if(paired.getBlock() instanceof com.vandorlabs.blocks.BlockConfigurableSpaceDoor)((com.vandorlabs.blocks.BlockConfigurableSpaceDoor)paired.getBlock()).updateRedstoneState(world,other,paired);}}
        world.notifyBlockUpdate(pos,state,state,2);
    }
    public boolean holdingCredential(net.minecraft.entity.player.EntityPlayer player,net.minecraft.util.EnumHand hand){return keyReader().accepts(player.getHeldItem(hand))||hand==net.minecraft.util.EnumHand.MAIN_HAND&&keyReader().accepts(player.getHeldItemOffhand());}
    public boolean useSecurity(net.minecraft.entity.player.EntityPlayer player,net.minecraft.util.EnumHand hand){return useSecurity(player,hand,0);}
    public boolean useSecurity(net.minecraft.entity.player.EntityPlayer player,net.minecraft.util.EnumHand hand,int side){
        if(world.isRemote)return true;TileEntityTokenSlot reader=keyReader(side);if(!reader.isUsableByPlayer(player))return true;net.minecraft.item.ItemStack held=player.getHeldItem(hand);
        if(hand==net.minecraft.util.EnumHand.MAIN_HAND&&!reader.accepts(held)&&reader.accepts(player.getHeldItemOffhand()))return false;
        if(held.isEmpty()){if(reader.isEmpty())reader.cycleLogo();else{net.minecraft.item.ItemStack item=reader.removeStackFromSlot(0);if(!player.inventory.addItemStackToInventory(item)&&!item.isEmpty())player.dropItem(item,false);}return true;}
        if(!reader.accepts(held))return true;if(!reader.authorized(held)){reader.reject();return true;}if(!reader.isEmpty()||!reader.isUsableByPlayer(player))return true;
        if(reader.behavior>=2)reader.insertToggle(player,held);else{net.minecraft.item.ItemStack inserted=held.copy();inserted.setCount(1);reader.setInventorySlotContents(0,inserted);if(!player.capabilities.isCreativeMode)held.shrink(1);}return true;
    }
    private void writeSecurity(NBTTagCompound tag,boolean contents){tag.setInteger("DoorPanelMode",panelMode());tag.setInteger("DoorSecurityMode",securityMode);if(contents)tag.setBoolean("DoorSecurityToggled",securityToggled);tag.setInteger("DoorSecurityAllowed",keyReader.effectiveAllowed());if(contents){tag.setTag("DoorKeyReader",keyReader.writeToNBT(new NBTTagCompound()));tag.setTag("DoorRearKeyReader",rearKeyReader.writeToNBT(new NBTTagCompound()));}}
    private void readSecurity(NBTTagCompound tag){panelMode=tag.hasKey("DoorPanelMode",3)?Math.max(0,Math.min(2,tag.getInteger("DoorPanelMode"))):panel?1:0;securityMode=Math.max(0,Math.min(4,tag.getInteger("DoorSecurityMode")));securityToggled=tag.getBoolean("DoorSecurityToggled");if(tag.hasKey("DoorKeyReader",10))keyReader.readFromNBT(tag.getCompoundTag("DoorKeyReader"));else keyReader.configure(false,tag.hasKey("DoorSecurityAllowed",3)?tag.getInteger("DoorSecurityAllowed")&127:127,securityBehavior(securityMode));if(tag.hasKey("DoorRearKeyReader",10))rearKeyReader.readFromNBT(tag.getCompoundTag("DoorRearKeyReader"));else rearKeyReader.configure(false,tag.hasKey("DoorSecurityAllowed",3)?tag.getInteger("DoorSecurityAllowed")&127:127,securityBehavior(securityMode));keyReader.configure(false,singleCompany(keyReader.effectiveAllowed()),securityBehavior(securityMode));rearKeyReader.configure(false,singleCompany(rearKeyReader.effectiveAllowed()),securityBehavior(securityMode));}
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
        if(depth!=0&&panelMode==2)panelMode=1;
        middle = depth == 0;
        markDirty();
        if (world != null && pos != null) {
            IBlockState state = world.getBlockState(pos);
            world.notifyBlockUpdate(pos, state, state, 3);
        }
    }
    public boolean acceptsDesign(int design) { return design<FIRST_DOUBLE_DESIGN || design==29 || this instanceof TileEntityLargeProgrammableDoor; }
    public static boolean valid(int design,int detail) { return design>=0 && design<DESIGNS.length && detail>=0 && detail<3; }

    public BlockSpaceDoor model(boolean sliding) {
        int key = (sliding ? 2 : 0) | (framed ? 1 : 0);
        BlockSpaceDoor resolved = RESOLVED_MODELS.get(key);
        if (resolved != null) return resolved;
        resolved = com.vandorlabs.blocks.ModBlocks.doorMotionTemplate(MOTION_MODELS[key].getResourcePath());
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
        super.writeToNBT(tag);frameAppearance.write(tag);glassAppearance.write(tag);
        new com.vandorlabs.persistence.SpaceDoorData(design,detail,framed,slideDirection,middle,sliding,hinges,trigger,panel)
                .write(new com.vandorlabs.persistence.NbtPrimitiveData(tag));
        tag.setInteger("DoorFaceTexture",faceTexture);tag.setBoolean("DoorTileTexture",tileTexture);tag.setInteger("DoorTextureLayout",getTextureLayout());
        tag.setInteger("SpaceDoorPlacementDepth", placementDepth);
        writeSecurity(tag,true);fluids.write(tag);tag.setInteger("DoorWaterOutlets",waterOutlets);return tag;
    }
    /** Copy user choices only, not tile coordinates, power or animation state. */
    public NBTTagCompound itemSettings() {
        migrateLegacyMotion();
        NBTTagCompound tag=new NBTTagCompound();frameAppearance.write(tag);glassAppearance.write(tag);
        new com.vandorlabs.persistence.SpaceDoorData(design,detail,framed,slideDirection,middle,sliding,hinges,trigger,panel)
                .write(new com.vandorlabs.persistence.NbtPrimitiveData(tag));
        tag.setInteger("DoorFaceTexture",faceTexture);tag.setBoolean("DoorTileTexture",tileTexture);tag.setInteger("DoorTextureLayout",getTextureLayout());
        tag.setInteger("SpaceDoorChannel",getRedstoneChannel());
        com.vandorlabs.redstone.ChannelData.write(tag,getRedstoneChannels());
        tag.setInteger("SpaceDoorPlacementDepth",placementDepth);writeSecurity(tag,false);return tag;
    }
    public void applyItemSettings(NBTTagCompound tag) {frameAppearance.read(tag);glassAppearance=DoorGlassAppearance.read(tag);
        setTextureLayout(tag.hasKey("DoorTextureLayout",3)?tag.getInteger("DoorTextureLayout"):tag.getBoolean("DoorTileTexture")?1:0);
        setFaceTexture(tag.hasKey("DoorFaceTexture",3)?tag.getInteger("DoorFaceTexture"):-1);
        com.vandorlabs.persistence.SpaceDoorData data=com.vandorlabs.persistence.SpaceDoorData.read(
                new com.vandorlabs.persistence.NbtPrimitiveData(tag));
        configure(data.design,data.detail,data.framed,data.direction,data.middle,data.sliding,data.hinges,data.trigger,data.panel);
        setRedstoneChannels(com.vandorlabs.redstone.ChannelData.read(tag,tag.getInteger("SpaceDoorChannel")));
        if(tag.hasKey("SpaceDoorPlacementDepth",3))setPlacementDepth(tag.getInteger("SpaceDoorPlacementDepth"));readSecurity(tag);
    }
    @Override public void readFromNBT(NBTTagCompound tag) {
        super.readFromNBT(tag);frameAppearance.read(tag);glassAppearance=DoorGlassAppearance.read(tag);fluids.read(tag);waterOutlets=tag.getInteger("DoorWaterOutlets")&255;
        tileTexture=tag.getBoolean("DoorTileTexture");
        textureLayout=tag.hasKey("DoorTextureLayout",3) && com.vandorlabs.render.TextureLayoutMode.valid(tag.getInteger("DoorTextureLayout"))?tag.getInteger("DoorTextureLayout"):-1;
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
        panel=data.panel;readSecurity(tag);
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
    @Override public void onLoad() { super.onLoad(); if(world!=null&&world.isRemote)migrateLegacyMotion();else DeferredTileLoad.schedule(this,()->{com.vandorlabs.blocks.DoorWaterGush.schedule(world,pos);migrateLegacyMotion();if(isLowerDoor()&&securityMode>0)tickReaders();}); }
    public static boolean hasGlassDesign(int design) { return design==0 || design==5 || design==6 || design==11 || design==15 || design==16 || design==29; }
    public boolean hasGlass() { return faceTexture<0 && hasGlassDesign(design); }
    @Override
    @net.minecraftforge.fml.relauncher.SideOnly(net.minecraftforge.fml.relauncher.Side.CLIENT)
    public boolean hasFastRenderer() {
        return !(world!=null&&com.vandorlabs.blocks.RenderFluidPresence.wet(this))&&com.vandorlabs.client.OpaqueDoorBatch.available(this);
    }
    @Override public boolean shouldRenderInPass(int pass) {
        return pass==1&&world!=null&&com.vandorlabs.blocks.RenderFluidPresence.wet(this)||isLowerDoor() && (pass == 0 || pass == 1 && hasGlass() && !opaqueGlass());
    }
    @Override public net.minecraft.util.math.AxisAlignedBB getRenderBoundingBox() {
        return slideDirection==0?super.getRenderBoundingBox():
                new net.minecraft.util.math.AxisAlignedBB(pos.add(-1,-2,-1),pos.add(2,4,2));
    }
}
