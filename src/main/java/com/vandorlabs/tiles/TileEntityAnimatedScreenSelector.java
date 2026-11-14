package com.vandorlabs.tiles;

import com.vandorlabs.redstone.ChannelList;
import com.vandorlabs.redstone.ChannelData;

import com.vandorlabs.redstone.RedstoneChannelMember;
import com.vandorlabs.redstone.RedstoneChannels;
import com.vandorlabs.persistence.NbtPrimitiveData;
import com.vandorlabs.persistence.ScreenData;
import com.vandorlabs.animation.ScreenBehavior;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.play.server.SPacketUpdateTileEntity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Map;

public class TileEntityAnimatedScreenSelector extends TileEntity implements RedstoneChannelMember, com.vandorlabs.surface.TextureJoinOwner {
    public final FrameAppearance frameAppearance=new FrameAppearance();
    public final GlassPlayback glassPlayback=new GlassPlayback(this);

    public static final int REDSTONE_SURFACE=-2;
    public static final int VEHICLE_SURFACE=-3;
    public static final int CHIP_SURFACE=-4;
    private TileEntityTokenSlot chipReader;
    public TileEntityTokenSlot ownedChipReader(){if(chipReader==null)chipReader=new TileEntityTokenSlot(true).forScreen(this);chipReader.setWorld(world);chipReader.setPos(pos);return chipReader;}
    public net.minecraft.item.ItemStack ownedChip(){return chipReader==null?net.minecraft.item.ItemStack.EMPTY:chipReader.getStackInSlot(0);}
    public boolean hasChipSurface(int slot){return getSurfaceTexture(slot)==CHIP_SURFACE;}
    public void chipChanged(){chipView=null;chipViewStack=null;chipPage=0;pendingChipPage=0;markDirty();if(world instanceof com.vandorlabs.vehicle.VehicleWorld){com.vandorlabs.vehicle.VehicleWorld view=(com.vandorlabs.vehicle.VehicleWorld)world;if(view.craft!=null)view.craft.signals.changed(pos);}else if(world!=null&&!world.isRemote){net.minecraft.block.state.IBlockState state=world.getBlockState(pos);world.notifyBlockUpdate(pos,state,state,2);}}
    private com.vandorlabs.programmer.ProgrammerJournalView chipView;
    private net.minecraft.item.ItemStack chipViewStack;
    public int chipPage;private int pendingChipPage;
    public com.vandorlabs.programmer.ProgrammerJournalView chipDisplay(){net.minecraft.item.ItemStack stack=com.vandorlabs.blocks.ScreenChipSupport.stack(this);if(chipViewStack!=stack){chipViewStack=stack;chipView=com.vandorlabs.programmer.ProgrammerJournalView.from(stack);chipPage=chipView==null?0:Math.max(0,Math.min(pendingChipPage,chipView.pages.size()-1));pendingChipPage=0;}return chipView;}

    public boolean hasVehicleStatus(int slot){return getSurfaceTexture(slot)==VEHICLE_SURFACE;}
    private static final RedstoneScreenContents[] NO_CONTROLS=new RedstoneScreenContents[2];
    private RedstoneScreenContents[] controls=NO_CONTROLS;
    public RedstoneScreenContents redstoneScreen(int slot){
        if(slot<0 || slot>1)throw new IllegalArgumentException("surface slot");
        if(controls==NO_CONTROLS)controls=new RedstoneScreenContents[2];
        if(controls[slot]==null)controls[slot]=new RedstoneScreenContents(this,slot);
        return controls[slot];
    }
    public boolean hasRedstoneScreen(int slot){return controls[slot]!=null && controls[slot].enabled();}
    public NBTTagCompound redstoneConfiguration(){
        NBTTagCompound tag=new NBTTagCompound();
        for(int i=0;i<2;i++)tag.setTag(i==0?"Primary":"Secondary",redstoneScreen(i).configuration());
        return tag;
    }
    public boolean applyRedstoneConfiguration(NBTTagCompound tag){
        for(String key:new String[]{"Primary","Secondary"})if(!tag.hasKey(key,10) || !RedstoneScreenContents.validConfiguration(tag.getCompoundTag(key)))return false;
        for(int i=0;i<2;i++)redstoneScreen(i).applyConfiguration(tag.getCompoundTag(i==0?"Primary":"Secondary"));return true;
    }
    private ChannelList channels=ChannelList.EMPTY;
    @Override public ChannelList getRedstoneChannels(){return channels;}


    @Override public boolean shouldRenderInPass(int pass) {
        if(pass!=0 && pass!=1)return false;
        if(fluids.any() && pass==1)return true;
        int retained=com.vandorlabs.VandorLabs.proxy==null?-1:com.vandorlabs.VandorLabs.proxy.portholeRenderPass(this,pass);
        if(retained>=0)return retained!=0;
        net.minecraft.block.Block block = getBlockType();
        if (block != null && (block.getClass() == com.vandorlabs.blocks.BlockProgrammableBlock.class
                || block instanceof com.vandorlabs.blocks.BlockProgrammableStairs
                || block instanceof com.vandorlabs.blocks.BlockProgrammableSlab)) return false;
        if(block instanceof com.vandorlabs.blocks.BlockProgrammableWall){
            com.vandorlabs.blocks.BlockProgrammableWall wall=(com.vandorlabs.blocks.BlockProgrammableWall)block;
            if(block instanceof com.vandorlabs.blocks.BlockProgrammableDiagonalGlass || wall.isPortholeShape())return true;
            if(world!=null){
                if(wall.isDiagonalShape()){
                    if(getMainTextureLayout()==0 && com.vandorlabs.blocks.DiagonalWallState.baked(block,pos,
                            getBlockMetadata(),diagonalHalfHeight,diagonalFullWidth))return false;
                }else if(pass==1 && com.vandorlabs.blocks.PanelJoins.flatBar(world.getBlockState(pos),world,pos)!=null)return true;
            }
        }
        // Ordinary screens/inputs cannot form a flat wall T bar. Pass zero also
        // needs no topology query: both the bar and the ordinary panel draw then.
        return super.shouldRenderInPass(pass);
    }

    /** Only atlas-backed opaque solids can join Forge's shared tile vertex buffer. */
    @Override public boolean hasFastRenderer() {
        if (world == null) return false;
        net.minecraft.block.Block block = getBlockType();
        if(block instanceof com.vandorlabs.blocks.BlockProgrammableWall){
            com.vandorlabs.blocks.BlockProgrammableWall wall=(com.vandorlabs.blocks.BlockProgrammableWall)block;
            return wall.isDiagonalShape()&&!wall.isPortholeShape()&&!(block instanceof com.vandorlabs.blocks.BlockProgrammableDiagonalGlass)&&!fluids.any();
        }
        return block instanceof com.vandorlabs.blocks.BlockProgrammableTrigger
                || block.getClass()==com.vandorlabs.blocks.BlockProgrammableLight.class && !isSmallInput();
    }

    public static final int MODE_OFF = ScreenBehavior.OFF;
    public static final int MODE_STATIC = ScreenBehavior.STATIC;
    public static final int MODE_ANIMATED = ScreenBehavior.ANIMATED;

    public static final String[] ANIMATION_SPEEDS = {"slow", "normal", "fast"};
    public static final int[] ANIMATION_SPEED_TICKS = {20, 10, 5};

    /** Data-driven static and animated input surfaces packaged by the build. */
    public static final String[] INPUT_PANELS;
    private static final Map<String, Integer> INPUT_FRAME_COUNTS = new HashMap<>();

    static {
        String path = "/assets/vandorlabs/data/input_surfaces.json";
        InputStream in = TileEntityAnimatedScreenSelector.class.getResourceAsStream(path);
        if (in == null) {
            throw new IllegalStateException("vandorlabs: missing " + path);
        }
        try (InputStreamReader reader = new InputStreamReader(in, "UTF-8")) {
            JsonArray entries = new JsonParser().parse(reader).getAsJsonArray();
            INPUT_PANELS = new String[entries.size()];
            for (int i = 0; i < entries.size(); i++) {
                JsonObject entry = entries.get(i).getAsJsonObject();
                String id = entry.get("id").getAsString();
                INPUT_PANELS[i] = id;
                INPUT_FRAME_COUNTS.put(id, entry.get("animated").getAsBoolean()
                        ? Math.max(1, entry.get("frames").getAsInt()) : 1);
            }
        } catch (Exception e) {
            throw new IllegalStateException("vandorlabs: cannot parse " + path, e);
        }
    }

    public static int getInputFrameCount(String id) {
        Integer frames = INPUT_FRAME_COUNTS.get(ScreenHousingTextures.canonicalInput(id));
        return frames == null ? 1 : frames;
    }

    public static boolean isAnimatedInputPanel(String id) {
        return getInputFrameCount(id) > 1;
    }

    private long settingsRevision;
    public long getSettingsRevision() { return settingsRevision; }
    private boolean textureJoinPresent;
    private volatile com.vandorlabs.surface.TextureJoinSettings textureJoin=com.vandorlabs.surface.TextureJoinSettings.DEFAULT;
    @Override public com.vandorlabs.surface.TextureJoinSettings textureJoin(){return world!=null&&getBlockType() instanceof com.vandorlabs.blocks.BlockProgrammableWall&&((com.vandorlabs.blocks.BlockProgrammableWall)getBlockType()).isPortholeShape()&&!joinPortholes?textureJoin.disabled():textureJoin;}
    @Override public void setTextureJoin(com.vandorlabs.surface.TextureJoinSettings next){
        if(next.equals(textureJoin)&&joinPortholes==(next.direction!=0)&&glassTextureJoin==(next.direction!=0))return;textureJoin=next;joinPortholes=next.direction!=0;glassTextureJoin=next.direction!=0;layoutChanged();portholeChanged();
    }
    private void settingsChanged() { com.vandorlabs.redstone.JoinedDisplayPower.changed(this);settingsRevision++;GlassMaterials.changed();markDirty();if(com.vandorlabs.VandorLabs.proxy!=null)com.vandorlabs.VandorLabs.proxy.portholeAppearanceChanged(this); }

    private String selectedScreen = "engineering_screen";
    private boolean redstoneEnabled = false;
    private boolean redstoneInverted;
    public boolean isRedstoneInverted(){return redstoneInverted;}
    public void setRedstoneInverted(boolean value){if(redstoneInverted==value)return;redstoneInverted=value;settingsChanged();}
    private int displayMode = MODE_ANIMATED;
    private boolean framed = true;
    private int animationSpeedIndex = 1;
    private String inputPanel = INPUT_PANELS[0];
    private String secondaryInputPanel = INPUT_PANELS[0];
    /** -1 means a legacy block whose lower/top position comes from metadata. */
    private int wallPosition = -1;
    private boolean ceilingMounted;
    /** -1 keeps the geometry of ceiling-mounted inputs saved before slots existed. */
    private int ceilingPosition = -1;
    public boolean isCeilingMounted() { return ceilingMounted; }
    public int getCeilingPosition(int legacyPosition) {
        return ceilingPosition<0?legacyPosition:ceilingPosition;
    }
    public void setCeilingPosition(int position) {
        int next=Math.max(0,Math.min(2,position));
        if(ceilingPosition==next)return;
        ceilingPosition=next;settingsChanged();
        if(world!=null){net.minecraft.block.state.IBlockState state=world.getBlockState(pos);world.notifyBlockUpdate(pos,state,state,3);}
    }
    @Override public double getMaxRenderDistanceSquared() {
        if(world!=null&&getBlockType().getClass()==com.vandorlabs.blocks.BlockAnimatedScreenSelector.class&&hasChipSurface(0))return Double.MAX_VALUE;
        // These walls are world geometry. Loaded chunks and the normal render
        // frustum bound their visibility, rather than the 64-block tile cutoff.
        if (world != null && getBlockType()
                instanceof com.vandorlabs.blocks.BlockProgrammableWall) return Double.MAX_VALUE;
        return super.getMaxRenderDistanceSquared();
    }

    @Override public net.minecraft.util.math.AxisAlignedBB getRenderBoundingBox() {
        if(world!=null&&!DisplayJoinGeometry.installed(this))return new net.minecraft.util.math.AxisAlignedBB(pos);
        if(world!=null&&getBlockType().getClass()==com.vandorlabs.blocks.BlockAnimatedScreenSelector.class&&hasChipSurface(0)){com.vandorlabs.blocks.ScreenChipGroups.Group group=com.vandorlabs.blocks.ScreenChipGroups.group(this);if(group!=null)return group.bounds;}

        if (world != null && getBlockType() instanceof com.vandorlabs.blocks.BlockProgrammableWall
                && ((com.vandorlabs.blocks.BlockProgrammableWall)getBlockType()).isDiagonalShape()){
            net.minecraft.util.math.AxisAlignedBB bounds=new net.minecraft.util.math.AxisAlignedBB(pos).grow(.125);
            return com.vandorlabs.VandorLabs.proxy==null?bounds:com.vandorlabs.VandorLabs.proxy.portholeRenderBounds(this,bounds);
        }
        if (ceilingMounted){
            net.minecraft.util.math.AxisAlignedBB bounds=new net.minecraft.util.math.AxisAlignedBB(pos.add(-1,0,-1),pos.add(2,1,2));
            return com.vandorlabs.VandorLabs.proxy==null?bounds:com.vandorlabs.VandorLabs.proxy.portholeRenderBounds(this,bounds);
        }
        net.minecraft.util.math.AxisAlignedBB bounds=super.getRenderBoundingBox();
        if(fluids.any())bounds=bounds.union(new net.minecraft.util.math.AxisAlignedBB(pos));
        return com.vandorlabs.VandorLabs.proxy==null?bounds:com.vandorlabs.VandorLabs.proxy.portholeRenderBounds(this,bounds);
    }
    public void setCeilingMounted(boolean value) {
        if(ceilingMounted==value)return;
        ceilingMounted=value;settingsChanged();
        if(world!=null){net.minecraft.block.state.IBlockState state=world.getBlockState(pos);world.notifyBlockUpdate(pos,state,state,3);}
    }
    private boolean smallInput = false;
    public final DisplayActivation activation=new DisplayActivation();
    public static boolean supportsActivation(net.minecraft.block.Block block){return block==com.vandorlabs.blocks.ModBlocks.ANIMATED_SCREEN_SELECTOR||block instanceof com.vandorlabs.blocks.BlockProgrammableInput||block instanceof com.vandorlabs.blocks.BlockProgrammableConsole||block instanceof com.vandorlabs.blocks.BlockProgrammableHalfConsole||block instanceof com.vandorlabs.blocks.BlockDiagonalHalfConsole||block instanceof com.vandorlabs.blocks.BlockProgrammableDiagonalScreen;}
    public int activationMode(){return activation.mode<0?(redstoneEnabled?1:0):activation.mode;}
    public int activationState(){int level=getSignalLevel();if(redstoneInverted)level=15-level;return activationMode()==2?(level==0?0:level<=5?1:level<=10?2:3):level>0?3:0;}
    private String nativeArtworkKey;private boolean nativeArtworkInput;private int nativeArtworkChoice;
    public int activationSurfaceSlot(){return getBlockType() instanceof com.vandorlabs.blocks.BlockProgrammableHalfConsole?1:0;}
    private int nativeArtworkTexture(){return nativeArtworkTexture(0);}
    private int nativeArtworkTexture(int slot){
        net.minecraft.block.Block block=getBlockType();boolean input=block instanceof com.vandorlabs.blocks.BlockProgrammableInput&&!(block instanceof com.vandorlabs.blocks.BlockProgrammableFullInput)||block instanceof com.vandorlabs.blocks.BlockProgrammableHalfConsole||block instanceof com.vandorlabs.blocks.BlockDiagonalHalfConsole;
        String key=input?(slot==1&&block instanceof com.vandorlabs.blocks.BlockProgrammableHalfConsole?getSecondaryInputPanel():getInputPanel()):getSelectedScreen();
        if(nativeArtworkKey==null||nativeArtworkInput!=input||!nativeArtworkKey.equals(key)){nativeArtworkKey=key;nativeArtworkInput=input;nativeArtworkChoice=ScreenHousingTextures.clamp(ScreenHousingTextures.screenIndex((input?"console_inputs/":"")+key+"_static"));}
        return nativeArtworkChoice;
    }
    public int sharedArtworkTexture(){int slot=activationSurfaceSlot(),choice=baseSurfaceTexture(slot);return choice==-1?nativeArtworkTexture(slot):choice;}
    public int primaryArtworkTexture(){int choice=getSurfaceTexture(0);return choice==-1?nativeArtworkTexture():choice;}
    public boolean activationOverridesArtwork(){return activation.mode>0&&activation.texture(activationState())>=0;}
    public boolean splitDisplay(){return frameAppearance.split&&!activationOverridesArtwork();}
    public int activationTexture(int state){int choice=activation.texture(state),slot=activationSurfaceSlot(),shared=slot==1?secondarySurface:primarySurface;return choice>=0?choice:shared>=0?shared:nativeArtworkTexture(slot);}
    public int activationPlayback(int state){int value=activation.playback(state);return value>=0?value:state==0?MODE_OFF:displayMode==MODE_OFF?MODE_ANIMATED:displayMode;}
    public int activationSpeed(int state){int value=activation.speed(state);return value>=0?value:animationSpeedIndex;}
    public boolean configureActivation(int mode,int state,int texture,int playback,int speed){
        if(!supportsActivation(getBlockType())||!DisplayActivation.valid(mode,state,texture,playback,speed))return false;
        int previous=activationMode();boolean transition=activation.mode<0||previous!=mode;
        int sourceState=previous==1?3:state;
        int shared=activationSurfaceSlot()==1?secondarySurface:primarySurface;
        int inheritedTexture=previous>0?activationTexture(sourceState):shared>=0?shared:shared==-1?nativeArtworkTexture(activationSurfaceSlot()):-1;
        if(previous>0&&activation.texture(sourceState)<0&&shared<-1)inheritedTexture=-1;
        int inheritedPlayback=previous>0?activationPlayback(sourceState):displayMode;
        int inheritedSpeed=previous>0?activationSpeed(sourceState):animationSpeedIndex;
        activation.configure(mode,state,texture,playback,speed);
        if(transition&&mode>0)activation.inheritUnset(inheritedTexture,inheritedPlayback,inheritedSpeed);
        redstoneEnabled=mode>0;settingsChanged();
        if(world!=null){net.minecraft.block.state.IBlockState current=world.getBlockState(pos);world.notifyBlockUpdate(pos,current,current,3);world.checkLight(pos);}return true;
    }
    public int effectiveMode(int slot){return slot!=activationSurfaceSlot()&&activation.mode>0?displayMode:getEffectiveMode();}
    public int effectiveSpeed(int slot){return slot!=activationSurfaceSlot()?animationSpeedIndex:getAnimationSpeedIndex();}
    public int baseSurfaceTexture(int slot){return hasRedstoneScreen(slot)?REDSTONE_SURFACE:slot==1?secondarySurface:primarySurface;}
    public int baseAnimationSpeed(){return animationSpeedIndex;}
    private int primarySurface=-1,secondarySurface=-1;
    public int getSurfaceTexture(int slot){if((slot==1?secondarySurface:primarySurface)==CHIP_SURFACE&&!hasRedstoneScreen(slot))return CHIP_SURFACE;if(slot==activationSurfaceSlot()&&activationOverridesArtwork())return activation.texture(activationState());if(hasRedstoneScreen(slot))return REDSTONE_SURFACE;if(slot==activationSurfaceSlot()&&(slot==1?secondarySurface:primarySurface)!=VEHICLE_SURFACE&&activation.mode>0)return activationTexture(activationState());return slot==1?secondarySurface:primarySurface;}
    public void setSurfaceTexture(int slot,int choice) {
        if(choice==REDSTONE_SURFACE){
            // The row control is the active surface, replacing Vehicle Status.
            if((slot==1?secondarySurface:primarySurface)==VEHICLE_SURFACE){if(slot==1)secondarySurface=-1;else primarySurface=-1;settingsChanged();}
            redstoneScreen(slot).setEnabled(true);return;
        }
        if(controls[slot]!=null)controls[slot].setEnabled(false);
        int next=choice==CHIP_SURFACE?CHIP_SURFACE:choice==VEHICLE_SURFACE?VEHICLE_SURFACE:choice<0?-1:ScreenHousingTextures.clamp(choice);
        if((slot==1?secondarySurface:primarySurface)==next)return;
        if(slot==1)secondarySurface=next;else primarySurface=next;
        settingsChanged();if(world!=null){net.minecraft.block.state.IBlockState state=world.getBlockState(pos);world.notifyBlockUpdate(pos,state,state,2);}
    }
    private int redstoneChannel;
    private boolean channelSignal;
    private int channelLevel;
    public volatile com.vandorlabs.redstone.JoinedDisplayPower.Group joinedDisplayPower;
    public int getSignalLevel(){if(channelLevel==15)return 15;return Math.max(channelLevel,com.vandorlabs.redstone.JoinedDisplayPower.level(this));}
    @Override public int localSignalLevel(int channel){return com.vandorlabs.redstone.LoadedRedstonePower.level(world,pos);}
    @Override public void setChannelLevel(int level){
        if(channelLevel==level)return;
        channelLevel=level;GlassMaterials.changed();setChannelSignal(level>0);
        if(world!=null && !world.isRemote){
            world.notifyBlockUpdate(pos,world.getBlockState(pos),world.getBlockState(pos),3);
            world.checkLightFor(net.minecraft.world.EnumSkyBlock.BLOCK,pos);
        }
    }
    private int housingTexture;
    private int sideTexture=-1;
    private FaceTextures faceTextures = FaceTextures.DEFAULT;
    public final com.vandorlabs.blocks.ContainedFluids fluids=new com.vandorlabs.blocks.ContainedFluids(this);
    public boolean isWaterlogged(){return fluids.any();}
    public void setWaterlogged(boolean value){fluids.set(0,value?new net.minecraftforge.fluids.FluidStack(net.minecraftforge.fluids.FluidRegistry.WATER,1000):null);if(!value)fluids.set(1,null);}
    private boolean slabTileSides;
    private int mainTextureLayout=-1,surfaceTextureLayout=-1,glassTextureLayout=-1;
    public int getMainTextureLayout(){return mainTextureLayout<0?(slabTileSides?1:0):mainTextureLayout;}
    public int getSurfaceTextureLayout(){return surfaceTextureLayout<0?(surfaceTileSides?1:0):surfaceTextureLayout;}
    public int getGlassTextureLayout(){return com.vandorlabs.persistence.PanelGlassSettings.textureLayout(glassTextureLayout,glassStretch);}
    public void setMainTextureLayout(int mode){checkLayout(mode);if(getMainTextureLayout()==mode)return;slabTileSides=mode==1;mainTextureLayout=mode;layoutChanged();}
    public void setSurfaceTextureLayout(int mode){checkLayout(mode);if(getSurfaceTextureLayout()==mode)return;surfaceTileSides=mode==1;surfaceTextureLayout=mode;layoutChanged();}
    public void setGlassTextureLayout(int mode){checkLayout(mode);if(getGlassTextureLayout()==mode)return;glassStretch=mode!=1;glassTextureLayout=mode;layoutChanged();}
    private static void checkLayout(int mode){if(!com.vandorlabs.render.TextureLayoutMode.valid(mode))throw new IllegalArgumentException("Invalid texture layout");}
    private static int layoutValue(net.minecraft.nbt.NBTTagCompound tag,String key){int value=tag.getInteger(key);return tag.hasKey(key,3) && com.vandorlabs.render.TextureLayoutMode.valid(value)?value:-1;}
    private void layoutChanged(){settingsChanged();if(world!=null){world.notifyBlockUpdate(pos,world.getBlockState(pos),world.getBlockState(pos),3);world.markBlockRangeForRenderUpdate(pos.add(-1,-1,-1),pos.add(1,1,1));}}
    private boolean surfaceTileSides=true;
    public boolean isSurfaceTileSides(){return surfaceTileSides;}
    public void setSurfaceTileSides(boolean value){if(surfaceTileSides==value)return;surfaceTextureLayout=-1;surfaceTileSides=value;settingsChanged();if(world!=null){net.minecraft.block.state.IBlockState state=world.getBlockState(pos);world.notifyBlockUpdate(pos,state,state,2);}}
    private boolean diagonalFullWidth;
    private boolean diagonalHalfHeight;
    private int diagonalFill;
    private int glassShade = 2;
    private int glassTint = 1;
    private int glassMaterial=-1;
    private boolean glassStretch,glassTextureJoin=true;
    public boolean isGlassStretch(){return glassStretch;}
    public boolean isGlassTextureJoin(){return glassTextureJoin;}
    public void setGlassLayout(boolean stretch,boolean join){if(glassStretch==stretch && glassTextureJoin==join)return;glassTextureLayout=-1;glassStretch=stretch;glassTextureJoin=join;settingsChanged();if(world!=null){world.notifyBlockUpdate(pos,world.getBlockState(pos),world.getBlockState(pos),3);world.markBlockRangeForRenderUpdate(pos.add(-1,-1,-1),pos.add(1,1,1));}}
    public int getGlassMaterial(){return glassMaterial;}
    public void setGlassMaterial(int value){if(GlassMaterials.valid(value) && glassMaterial!=value){glassMaterial=value;settingsChanged();}}
    private boolean glassFramed=true;
    private boolean joinPortholes;
    private int portholeShape;
    private static final java.util.concurrent.atomic.AtomicLong portholeRevision = new java.util.concurrent.atomic.AtomicLong();

    public static long getPortholeRevision() { return portholeRevision.get(); }
    /** Local-only notification also covers packet and same-state tile replacements. */
    private void portholeChanged(){
        portholeRevision.incrementAndGet();
        if(com.vandorlabs.VandorLabs.proxy!=null)com.vandorlabs.VandorLabs.proxy.portholeGeometryChanged(this);
    }

    public boolean isJoinPortholes() { return joinPortholes; }
    public int getPortholeShape() { return portholeShape; }
    public void setPortholeShape(int shape) {
        if (shape < 0 || shape > 3 || shape == portholeShape) return;
        portholeShape = shape;
        portholeChanged();
        settingsChanged();
        if (world != null) world.notifyBlockUpdate(pos, world.getBlockState(pos),
                world.getBlockState(pos), 3);
    }
    public void setJoinPortholes(boolean join) {
        if (join == joinPortholes) return;
        joinPortholes = join;
        portholeChanged();
        settingsChanged();
        if (world != null) world.notifyBlockUpdate(pos, world.getBlockState(pos),
                world.getBlockState(pos), 3);
    }

    public int getGlassTint() { return glassTint; }
    public boolean isGlassFramed() { return glassFramed; }
    public void setGlassAppearance(int tint,boolean framed) {
        if(!GlassTextures.validTint(tint) || glassTint==tint && glassFramed==framed)return;
        glassTint=tint;glassFramed=framed;settingsChanged();
        if(world!=null) {
            world.notifyBlockUpdate(pos,world.getBlockState(pos),world.getBlockState(pos),3);
            world.markBlockRangeForRenderUpdate(pos.add(-1,-1,-1),pos.add(1,1,1));
        }
    }
    public int getGlassShade() { return glassShade; }
    public void setGlassShade(int shade) {
        if (!GlassTextures.valid(shade) || shade == glassShade) return;
        glassShade = shade;
        settingsChanged();
        if (world != null) world.notifyBlockUpdate(pos, world.getBlockState(pos),
                world.getBlockState(pos), 3);
    }

    public FaceTextures getFaceTextures() { return faceTextures; }
    public void setFaceTextures(FaceTextures value) {
        if (faceTextures.equals(value)) return;
        faceTextures = value;
        settingsChanged();
        if (world != null) {
            world.notifyBlockUpdate(pos, world.getBlockState(pos), world.getBlockState(pos), 3);
            if (world.isRemote) world.markBlockRangeForRenderUpdate(pos, pos);
        }
    }

    private int stairTread=8,stairRoll;
    public int stairTread(){return stairTread;}
    public int stairRoll(){return stairRoll;}
    public int stairGeometry(){return com.vandorlabs.persistence.StairState.packed(stairTread,stairRoll);}
    public void setStairGeometry(int tread,int roll){
        if(!com.vandorlabs.persistence.StairState.valid(tread,roll))return;
        if(stairTread==tread&&stairRoll==roll)return;
        stairTread=tread;stairRoll=roll;fluids.invalidateGeometry();settingsChanged();
        if(world!=null){world.notifyBlockUpdate(pos,world.getBlockState(pos),world.getBlockState(pos),3);world.markBlockRangeForRenderUpdate(pos.add(-1,-1,-1),pos.add(1,1,1));}
    }
    public int getHousingTexture() { return housingTexture; }
    public int getSideTexture(){return sideTexture;}
    public static boolean supportsSideTexture(net.minecraft.block.Block block) {
        return block instanceof com.vandorlabs.blocks.BlockProgrammableSlab
                || block instanceof com.vandorlabs.blocks.BlockProgrammableStairs
                || block instanceof com.vandorlabs.blocks.BlockProgrammableWall
                && ((com.vandorlabs.blocks.BlockProgrammableWall)block).isPortholeShape();
    }
    public void setSideTexture(int choice) {
        int next=choice==VEHICLE_SURFACE?VEHICLE_SURFACE:choice<0?-1:ScreenHousingTextures.clamp(choice);
        if(sideTexture==next)return;
        sideTexture=next;settingsChanged();
        if(world!=null){world.notifyBlockUpdate(pos,world.getBlockState(pos),world.getBlockState(pos),3);if(world.isRemote)world.markBlockRangeForRenderUpdate(pos,pos);}
    }
    public boolean isSlabTileSides() { return slabTileSides; }
    public boolean isDiagonalHalfHeight() { return diagonalHalfHeight; }
    private int diagonalJoin;
    public int getDiagonalJoin(){return diagonalJoin;}
    public void setDiagonalJoin(int choice){
        if(choice<0 || choice>11 || choice==diagonalJoin)return;
        diagonalJoin=choice;settingsChanged();portholeChanged();
        if(world!=null){world.notifyBlockUpdate(pos,world.getBlockState(pos),world.getBlockState(pos),3);world.markBlockRangeForRenderUpdate(pos.add(-1,-1,-1),pos.add(1,1,1));}
    }
    public int getDiagonalFill() { return diagonalFill; }
    public void setDiagonalGeometry(int mode, int fill) {
        boolean halfHeight=mode==2,fullWidth=mode==1 || mode==2;
        int nextFill=com.vandorlabs.persistence.WallSettings.fill(fill);
        if(diagonalHalfHeight==halfHeight && diagonalFullWidth==fullWidth && diagonalFill==nextFill)return;
        diagonalHalfHeight=halfHeight;
        diagonalFullWidth=fullWidth;
        diagonalFill=nextFill;
        settingsChanged(); portholeChanged();
        if (world != null) {
            world.notifyBlockUpdate(pos, world.getBlockState(pos), world.getBlockState(pos), 3);
            world.markBlockRangeForRenderUpdate(pos.add(-1,-1,-1), pos.add(1,1,1));
        }
    }
    public boolean isDiagonalFullWidth() { return diagonalFullWidth; }
    public void setDiagonalFullWidth(boolean fullWidth) {
        if (diagonalFullWidth == fullWidth && !diagonalHalfHeight) return;
        diagonalHalfHeight = false;
        diagonalFullWidth = fullWidth;
        portholeChanged();
        settingsChanged();
        if (world != null) world.notifyBlockUpdate(pos, world.getBlockState(pos),
                world.getBlockState(pos), 3);
    }
    public void setSlabTileSides(boolean tileSides) {
        if (slabTileSides == tileSides) return;
        mainTextureLayout=-1;
        slabTileSides = tileSides;
        settingsChanged();
        if (world != null) world.notifyBlockUpdate(pos, world.getBlockState(pos),
                world.getBlockState(pos), 3);
    }
    public void setHousingTexture(int choice) {
        int next = ScreenHousingTextures.clamp(choice);
        if (housingTexture == next) return;
        housingTexture = next;
        settingsChanged();
        if (world != null && world.isRemote) world.markBlockRangeForRenderUpdate(pos,pos);
    }

    // Rows own transmission; the host channel list is a display input, never a wire repeater.
    @Override public ChannelList transmittedChannels(){return ChannelList.EMPTY;}
    @Override public TileEntity channelTile() { return this; }
    @Override public int getRedstoneChannel() { return redstoneChannel; }
    @Override public boolean hasLocalRedstoneSignal() {
        return com.vandorlabs.redstone.LoadedRedstonePower.isPowered(world, pos);
    }
    @Override public void setChannelSignal(boolean powered) {
        if(!powered)channelLevel=0;else if(channelLevel==0)channelLevel=15;
        if (channelSignal == powered) return;
        channelSignal = powered;GlassMaterials.changed();
        if (world != null && !world.isRemote) {
            net.minecraft.block.state.IBlockState state = world.getBlockState(pos);
            world.notifyBlockUpdate(pos, state, state, 2);
            world.checkLightFor(net.minecraft.world.EnumSkyBlock.BLOCK, pos);
        }
    }
    @Override public void setRedstoneChannel(int value) {setRedstoneChannels(ChannelList.of(Math.max(0,value)));}
    @Override public void setRedstoneChannels(ChannelList next) {
        if (channels.equals(next)) return;
        ChannelList old = channels;
        channels=next;redstoneChannel=next.first();
        settingsChanged();
        RedstoneChannels.channelChanged(this, old);
    }
    public void localInputChanged() {if(joinedDisplayPower!=null)joinedDisplayPower.powerChanged();RedstoneChannels.inputChanged(this); }
    @Override public void onLoad() {
        super.onLoad();
        portholeChanged();
        DeferredTileLoad.schedule(this, this::finishLoading);
    }

    public boolean initializeDisplayArtwork(net.minecraft.block.Block block){
        if(!FrameAppearance.displayBlock(block)||frameAppearance.artworkVersion>=1)return false;
        frameAppearance.artworkVersion=1;
        if(primarySurface==-1&&"engineering_screen".equals(selectedScreen)&&frameAppearance.front<0){frameAppearance.migrateLegacyFrame();framed=false;}
        return true;
    }
    protected void finishLoading() {com.vandorlabs.redstone.JoinedDisplayPower.changed(this);if(world!=null&&initializeDisplayArtwork(world.getBlockState(pos).getBlock())){markDirty();net.minecraft.block.state.IBlockState state=world.getBlockState(pos);world.notifyBlockUpdate(pos,state,state,2);} RedstoneChannels.register(this);for(RedstoneScreenContents c:controls)if(c!=null)c.finishLoading(); }
    @Override public void invalidate() {portholeChanged();com.vandorlabs.redstone.JoinedDisplayPower.changed(this);RedstoneChannels.unregister(this);for(RedstoneScreenContents c:controls)if(c!=null)c.invalidate(); super.invalidate(); }
    @Override public void onChunkUnload() {portholeChanged();com.vandorlabs.redstone.JoinedDisplayPower.changed(this);RedstoneChannels.unregister(this);for(RedstoneScreenContents c:controls)if(c!=null)c.onChunkUnload(); super.onChunkUnload(); }

    public static boolean isValidInputPanel(String id) {
        if (id != null) {
            for (String candidate : INPUT_PANELS) {
                if (candidate.equals(id)) {
                    return true;
                }
            }
        }
        return false;
    }

    public String getInputPanel() {
        return ScreenHousingTextures.canonicalInput(inputPanel);
    }

    public void setInputPanel(String inputPanel) {
        String next=isValidInputPanel(inputPanel)?inputPanel:INPUT_PANELS[0];
        if(getInputPanel().equals(ScreenHousingTextures.canonicalInput(next)))return;
        this.inputPanel=next;
        settingsChanged();
    }

    public String getSecondaryInputPanel() {
        return ScreenHousingTextures.canonicalInput(secondaryInputPanel);
    }

    public void setSecondaryInputPanel(String inputPanel) {
        String next=isValidInputPanel(inputPanel)?inputPanel:INPUT_PANELS[0];
        if(getSecondaryInputPanel().equals(ScreenHousingTextures.canonicalInput(next)))return;
        this.secondaryInputPanel=next;
        settingsChanged();
    }

    public int getWallPosition(int legacyPosition) {
        return wallPosition < 0 ? legacyPosition : wallPosition;
    }

    public void setWallPosition(int wallPosition) {
        int next=Math.max(0,Math.min(2,wallPosition));
        if(this.wallPosition==next)return;
        this.wallPosition=next;
        settingsChanged();
    }

    public boolean isSmallInput() {
        return smallInput;
    }

    public void setSmallInput(boolean smallInput) {
        if(this.smallInput==smallInput)return;
        this.smallInput = smallInput;
        settingsChanged();
    }

    public String getSelectedScreen() {
        return ScreenHousingTextures.canonicalScreen(selectedScreen);
    }

    public void setSelectedScreen(String selectedScreen) {
        if(java.util.Objects.equals(getSelectedScreen(),ScreenHousingTextures.canonicalScreen(selectedScreen)))return;
        this.selectedScreen = selectedScreen;
        settingsChanged();
    }

    public boolean isRedstoneEnabled() {
        return redstoneEnabled;
    }

    public void setRedstoneEnabled(boolean redstoneEnabled) {
        if(activation.mode>=0)activation.mode=redstoneEnabled?Math.max(1,activation.mode):0;
        if(this.redstoneEnabled==redstoneEnabled)return;
        this.redstoneEnabled = redstoneEnabled;
        settingsChanged();
    }

    public int getDisplayMode() {
        return displayMode;
    }

    public void setDisplayMode(int displayMode) {
        int next=ScreenBehavior.clampMode(displayMode);
        if(this.displayMode==next)return;
        this.displayMode=next;
        settingsChanged();
    }

    public boolean isFramed() {
        return framed;
    }

    public void setFramed(boolean framed) {
        if(this.framed==framed)return;
        this.framed = framed;
        settingsChanged();
    }

    public int getAnimationSpeedIndex() {
        if(activation.mode>0)return activationSpeed(activationState());
        return animationSpeedIndex;
    }

    public void setAnimationSpeedIndex(int animationSpeedIndex) {
        int next=ScreenBehavior.clampSpeedIndex(animationSpeedIndex);
        if(this.animationSpeedIndex==next)return;
        this.animationSpeedIndex=next;
        settingsChanged();
    }

    public int getAnimationSpeedTicks() {
        return ScreenBehavior.animationTicks(getAnimationSpeedIndex());
    }

    /**
     * Single source of truth for what the block shows, shared by the
     * renderer and the light level so they can never disagree. Asleep
     * (redstone on, unpowered) is always dark; a powered OFF wakes to
     * animated, mirroring the sequenced displays' wake-only behavior.
     */
    public int getEffectiveMode() {
        if(activation.mode==0)return displayMode;
        if(activation.mode>0)return activationPlayback(activationState());
        // Ungated Static/Animated do not depend on power. Off deliberately does:
        // it wakes to Animated even when the redstone gate is disabled.
        int mode = ScreenBehavior.clampMode(displayMode);
        if (!redstoneEnabled && mode != MODE_OFF) return mode;
        boolean powered = channelSignal || com.vandorlabs.redstone.JoinedDisplayPower.level(this)>0;
        return ScreenBehavior.effectiveMode(mode,redstoneEnabled,redstoneEnabled && redstoneInverted ? !powered : powered);
    }

    protected boolean isTriggerPowered() {
        return channelSignal || com.vandorlabs.redstone.JoinedDisplayPower.level(this)>0;
    }

    public boolean isUsableByPlayer(EntityPlayer player) {
        if (world == null || pos == null) {
            return false;
        }
        if (world.getTileEntity(pos) != this) {
            return false;
        }
        return player.getDistanceSq(pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D) <= 64.0D;
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound compound) {
        NbtPrimitiveData primitiveData=new NbtPrimitiveData(compound);
        com.vandorlabs.persistence.StairState.write(primitiveData,stairTread,stairRoll);
        super.writeToNBT(compound);if(primarySurface==CHIP_SURFACE||secondarySurface==CHIP_SURFACE)compound.setInteger("ChipDisplayPage",chipPage);else compound.removeTag("ChipDisplayPage");if(chipReader!=null&&!chipReader.isEmpty())compound.setTag("ScreenChipReader",chipReader.writeEmbedded());else compound.removeTag("ScreenChipReader");activation.write(compound);frameAppearance.write(compound);glassPlayback.write(compound);compound.setBoolean("RedstoneInverted",redstoneInverted);compound.setInteger("ChannelLevel",channelLevel);ChannelData.write(compound,channels);
        compound.setInteger("PrimarySurfaceTexture",primarySurface);compound.setInteger("SecondarySurfaceTexture",secondarySurface);
        compound.setBoolean("CeilingMounted",ceilingMounted);
        if (ceilingPosition>=0) compound.setInteger("CeilingPosition",ceilingPosition);
        new ScreenData(selectedScreen, redstoneEnabled, displayMode, framed,
                animationSpeedIndex, inputPanel, secondaryInputPanel, wallPosition,
                smallInput, redstoneChannel, channelSignal, housingTexture)
                .write(primitiveData);
        com.vandorlabs.persistence.PanelGlassSettings.write(primitiveData,glassShade,glassTint,glassMaterial,
                glassStretch,glassTextureJoin,glassFramed,glassTextureLayout);

        com.vandorlabs.persistence.HousingSettings.write(primitiveData,housingTexture,sideTexture,mainTextureLayout,
                slabTileSides,faceTextures.enabled,faceTextures.choices());
        fluids.write(compound);
        textureJoin.write(new com.vandorlabs.persistence.NbtPrimitiveData(compound),textureJoinPresent);
        compound.setInteger("SurfaceTextureLayout",getSurfaceTextureLayout());
        compound.setBoolean("SurfaceTileSides",surfaceTileSides);
        com.vandorlabs.persistence.WallSettings.write(primitiveData,diagonalFullWidth,diagonalHalfHeight,
                diagonalFill,diagonalJoin,joinPortholes,portholeShape);
        for(int i=0;i<2;i++)if(controls[i]!=null)compound.setTag(i==0?"RedstonePrimary":"RedstoneSecondary",controls[i].writeToNBT(new NBTTagCompound()));
        return compound;
    }

    @Override
    public void readFromNBT(NBTTagCompound compound) {compound=com.vandorlabs.redstone.TransmitterSlots.normalize(compound);
        if(FrameAppearance.needsLegacyScreen(compound)){compound=compound.copy();FrameAppearance.migrateLegacyScreen(compound);}
        int previousStair=stairGeometry();
        stairTread=com.vandorlabs.persistence.StairState.readTread(compound.hasKey(com.vandorlabs.persistence.StairState.TREAD,3),compound.getInteger(com.vandorlabs.persistence.StairState.TREAD));
        stairRoll=com.vandorlabs.persistence.StairState.readRoll(compound.getInteger(com.vandorlabs.persistence.StairState.ROLL));if(previousStair!=stairGeometry())fluids.invalidateGeometry();
        ChannelList previousChannels=channels;
        FaceTextures previousFaces = faceTextures;
        int previousHousing = housingTexture;
        int previousSide=sideTexture;
        fluids.read(compound);
        boolean previousSlabSides = slabTileSides;
        int oldMainLayout=getMainTextureLayout(),oldSurfaceLayout=getSurfaceTextureLayout(),oldGlassLayout=getGlassTextureLayout();
        textureJoinPresent=compound.hasKey(com.vandorlabs.surface.TextureJoinSettings.DIRECTION_TAG)||compound.hasKey(com.vandorlabs.surface.TextureJoinSettings.PARITY_TAG);
        textureJoin=com.vandorlabs.surface.TextureJoinSettings.read(new com.vandorlabs.persistence.NbtPrimitiveData(compound));
        surfaceTextureLayout=layoutValue(compound,"SurfaceTextureLayout");glassTextureLayout=com.vandorlabs.persistence.PanelGlassSettings.rawLayout(compound.hasKey("GlassTextureLayout",3),compound.getInteger("GlassTextureLayout"));
        boolean previousSurfaceSides=surfaceTileSides;
        surfaceTileSides=!compound.hasKey("SurfaceTileSides",1)||compound.getBoolean("SurfaceTileSides");
        boolean previousHalfHeight = diagonalHalfHeight;
        int previousFill = diagonalFill;
        int previousJoin=diagonalJoin,previousGlassShade=glassShade;
        boolean previousPortholeJoin=joinPortholes;
        boolean previousDiagonalWidth = diagonalFullWidth;
        int oldChannel = redstoneChannel;
        super.readFromNBT(compound);if(compound.hasKey("ScreenChipReader",10)||chipReader!=null)ownedChipReader().readEmbedded(compound.getCompoundTag("ScreenChipReader"));chipView=null;chipViewStack=null;pendingChipPage=Math.max(0,Math.min(256,compound.getInteger("ChipDisplayPage")));chipPage=pendingChipPage;activation.read(compound);frameAppearance.read(compound);glassPlayback.read(compound);
        for(int i=0;i<2;i++){
            String key=i==0?"RedstonePrimary":"RedstoneSecondary";
            if(compound.hasKey(key,10))redstoneScreen(i).readFromNBT(compound.getCompoundTag(key));
            else if(controls[i]!=null)controls[i].readFromNBT(new NBTTagCompound());
        }
        if(controls!=NO_CONTROLS && world!=null && !world.isRemote)DeferredTileLoad.schedule(this,this::finishLoading);
        primarySurface=compound.getInteger("PrimarySurfaceTexture") ==CHIP_SURFACE?CHIP_SURFACE:compound.getInteger("PrimarySurfaceTexture")==VEHICLE_SURFACE?VEHICLE_SURFACE:compound.hasKey("PrimarySurfaceTexture",3) && compound.getInteger("PrimarySurfaceTexture")>=0?ScreenHousingTextures.clamp(compound.getInteger("PrimarySurfaceTexture")):-1;
        secondarySurface=compound.getInteger("SecondarySurfaceTexture") ==CHIP_SURFACE?CHIP_SURFACE:compound.getInteger("SecondarySurfaceTexture")==VEHICLE_SURFACE?VEHICLE_SURFACE:compound.hasKey("SecondarySurfaceTexture",3) && compound.getInteger("SecondarySurfaceTexture")>=0?ScreenHousingTextures.clamp(compound.getInteger("SecondarySurfaceTexture")):-1;
        ceilingMounted=compound.getBoolean("CeilingMounted");
        ceilingPosition=compound.hasKey("CeilingPosition",3)
                ?Math.max(0,Math.min(2,compound.getInteger("CeilingPosition"))):-1;
        // NBT is world data, never trust it blindly. The portable codec keeps
        // these defaults identical in every version-specific block entity.
        NbtPrimitiveData primitiveData=new NbtPrimitiveData(compound);
        ScreenData data = ScreenData.read(primitiveData,
                "engineering_screen", INPUT_PANELS[0],
                TileEntityAnimatedScreenSelector::isValidInputPanel);
        selectedScreen = data.selectedScreen;
        redstoneEnabled = activation.mode<0?data.redstoneEnabled:activation.mode>0;
        redstoneInverted=compound.getBoolean("RedstoneInverted");
        displayMode = data.displayMode;
        framed = data.framed;
        animationSpeedIndex = data.animationSpeedIndex;
        inputPanel = data.inputPanel;
        secondaryInputPanel = data.secondaryInputPanel;
        wallPosition = data.wallPosition;
        smallInput = data.smallInput;
        redstoneChannel = data.redstoneChannel;
        channels=ChannelData.read(compound,redstoneChannel);redstoneChannel=channels.first();
        channelSignal = data.channelSignal;
        channelLevel=compound.hasKey("ChannelLevel",3)?Math.max(0,Math.min(15,compound.getInteger("ChannelLevel"))):channelSignal?15:0;
        com.vandorlabs.persistence.HousingSettings housing=com.vandorlabs.persistence.HousingSettings.read(
                primitiveData,previousHousing,previousSlabSides,
                ScreenHousingTextures::clamp,ScreenHousingTextures::validChoice);
        housingTexture=housing.mainTexture;sideTexture=housing.sideTexture;
        mainTextureLayout=housing.rawLayout;slabTileSides=housing.tileSides;
        glassShade=com.vandorlabs.persistence.PanelGlassSettings.shade(compound.hasKey("GlassShade",3),compound.getInteger("GlassShade"));
        glassTint=com.vandorlabs.persistence.PanelGlassSettings.tint(compound.hasKey("GlassTint",3),compound.getInteger("GlassTint"));
        glassMaterial=compound.hasKey("GlassMaterial",3)?GlassMaterials.clamp(compound.getInteger("GlassMaterial")):-1;
        glassStretch=compound.getBoolean("GlassStretch");glassTextureJoin=com.vandorlabs.persistence.PanelGlassSettings.flag(compound.hasKey("GlassTextureJoin"),compound.getBoolean("GlassTextureJoin"));
        glassFramed=com.vandorlabs.persistence.PanelGlassSettings.flag(compound.hasKey("GlassFramed"),compound.getBoolean("GlassFramed"));
        joinPortholes = compound.getBoolean("JoinPortholes");
        portholeShape = compound.hasKey("PortholeShape",3)
                ? com.vandorlabs.persistence.WallSettings.portholeShape(true,compound.getInteger("PortholeShape")) : 0;
        faceTextures = FaceTextures.of(housing.facesEnabled,housing.faces());
        diagonalFullWidth = compound.getBoolean("DiagonalFullWidth");
        diagonalHalfHeight = compound.getBoolean("DiagonalHalfHeight");
        diagonalJoin=com.vandorlabs.persistence.WallSettings.join(compound.getInteger("DiagonalJoin"));
        diagonalFill = com.vandorlabs.persistence.WallSettings.fill(compound.getInteger("DiagonalFill"));
        if (world != null && world.isRemote
                && (previousStair!=stairGeometry() || oldMainLayout!=getMainTextureLayout() || oldSurfaceLayout!=getSurfaceTextureLayout() || oldGlassLayout!=getGlassTextureLayout() || !previousFaces.equals(faceTextures) || previousSide!=sideTexture || previousHousing != housingTexture || previousSlabSides != slabTileSides || previousSurfaceSides!=surfaceTileSides
                || previousDiagonalWidth != diagonalFullWidth || previousHalfHeight != diagonalHalfHeight
                || previousFill != diagonalFill || previousJoin!=diagonalJoin || previousGlassShade!=glassShade || previousPortholeJoin!=joinPortholes))
        {
            if(world.getBlockState(pos).getBlock() instanceof com.vandorlabs.blocks.BlockProgrammableWall
                    && ((com.vandorlabs.blocks.BlockProgrammableWall)world.getBlockState(pos).getBlock()).isDiagonalShape())
                world.markBlockRangeForRenderUpdate(pos.add(-1,-1,-1),pos.add(1,1,1));
            else world.markBlockRangeForRenderUpdate(pos,pos);
        }
        // Tile packets arrive after the unchanged-state block notification. A
        // layout rebuilt between those events must see the newly loaded artwork.
        com.vandorlabs.redstone.JoinedDisplayPower.changed(this);settingsRevision++;GlassMaterials.changed();
        portholeChanged();
        if (world != null && !world.isRemote && !previousChannels.equals(channels))
            DeferredTileLoad.schedule(this, () -> RedstoneChannels.channelChanged(this, oldChannel));
    }

    @Override
    public NBTTagCompound getUpdateTag() {
        return writeToNBT(new NBTTagCompound());
    }

    @Override
    public SPacketUpdateTileEntity getUpdatePacket() {
        return new SPacketUpdateTileEntity(getPos(), 0, getUpdateTag());
    }

    @Override
    public void onDataPacket(NetworkManager net, SPacketUpdateTileEntity pkt) {
        readFromNBT(pkt.getNbtCompound());
    }

    @Override
    public boolean shouldRefresh(World world, BlockPos pos, net.minecraft.block.state.IBlockState oldState, net.minecraft.block.state.IBlockState newState) {
        return oldState.getBlock() != newState.getBlock();
    }
}
