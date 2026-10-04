package com.vandorlabs.tiles;

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

public class TileEntityAnimatedScreenSelector extends TileEntity implements RedstoneChannelMember {

    @Override public boolean shouldRenderInPass(int pass) {
        net.minecraft.block.Block block = getBlockType();
        if(world!=null && com.vandorlabs.blocks.DiagonalWallState.baked(block,pos))return false;
        if (block != null && (block.getClass() == com.vandorlabs.blocks.BlockProgrammableBlock.class
                || block instanceof com.vandorlabs.blocks.BlockProgrammableStairs
                || block instanceof com.vandorlabs.blocks.BlockProgrammableSlab)) return false;
        if (block instanceof com.vandorlabs.blocks.BlockProgrammableWall
                && ((com.vandorlabs.blocks.BlockProgrammableWall) block).isPortholeShape())
            return pass == 0 || pass == 1;
        return super.shouldRenderInPass(pass);
    }

    /** Only atlas-backed opaque solids can join Forge's shared tile vertex buffer. */
    @Override public boolean hasFastRenderer() {
        if (world == null) return false;
        net.minecraft.block.Block block = world.getBlockState(pos).getBlock();
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
        Integer frames = INPUT_FRAME_COUNTS.get(id);
        return frames == null ? 1 : frames;
    }

    public static boolean isAnimatedInputPanel(String id) {
        return getInputFrameCount(id) > 1;
    }

    private long settingsRevision;
    public long getSettingsRevision() { return settingsRevision; }
    private void settingsChanged() { settingsRevision++; markDirty(); }

    private String selectedScreen = "engineering_screen";
    private boolean redstoneEnabled = false;
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
        // These walls are world geometry. Loaded chunks and the normal render
        // frustum bound their visibility, rather than the 64-block tile cutoff.
        if (world != null && getBlockType()
                instanceof com.vandorlabs.blocks.BlockProgrammableWall) return Double.MAX_VALUE;
        return super.getMaxRenderDistanceSquared();
    }

    @Override public net.minecraft.util.math.AxisAlignedBB getRenderBoundingBox() {
        if (world != null && world.getBlockState(pos).getBlock() instanceof com.vandorlabs.blocks.BlockProgrammableWall
                && ((com.vandorlabs.blocks.BlockProgrammableWall)world.getBlockState(pos).getBlock()).isDiagonalShape())
            return new net.minecraft.util.math.AxisAlignedBB(pos).grow(.125);
        if (ceilingMounted) return new net.minecraft.util.math.AxisAlignedBB(
                pos.add(-1,0,-1),pos.add(2,1,2));
        return super.getRenderBoundingBox();
    }
    public void setCeilingMounted(boolean value) {
        if(ceilingMounted==value)return;
        ceilingMounted=value;settingsChanged();
        if(world!=null){net.minecraft.block.state.IBlockState state=world.getBlockState(pos);world.notifyBlockUpdate(pos,state,state,3);}
    }
    private boolean smallInput = false;
    private int primarySurface=-1,secondarySurface=-1;
    public int getSurfaceTexture(int slot){return slot==1?secondarySurface:primarySurface;}
    public void setSurfaceTexture(int slot,int choice) {
        int next=choice<0?-1:ScreenHousingTextures.clamp(choice);
        if(getSurfaceTexture(slot)==next)return;
        if(slot==1)secondarySurface=next;else primarySurface=next;
        settingsChanged();if(world!=null){net.minecraft.block.state.IBlockState state=world.getBlockState(pos);world.notifyBlockUpdate(pos,state,state,2);}
    }
    private int redstoneChannel;
    private boolean channelSignal;
    private int housingTexture;
    private int sideTexture=-1;
    private FaceTextures faceTextures = FaceTextures.DEFAULT;
    private boolean slabTileSides;
    private boolean surfaceTileSides=true;
    public boolean isSurfaceTileSides(){return surfaceTileSides;}
    public void setSurfaceTileSides(boolean value){if(surfaceTileSides==value)return;surfaceTileSides=value;settingsChanged();if(world!=null){net.minecraft.block.state.IBlockState state=world.getBlockState(pos);world.notifyBlockUpdate(pos,state,state,2);}}
    private boolean diagonalFullWidth;
    private boolean diagonalHalfHeight;
    private int diagonalFill;
    private int glassShade = 2;
    private boolean joinPortholes;
    private int portholeShape;
    private static long portholeRevision;

    public static long getPortholeRevision() { return portholeRevision; }

    public boolean isJoinPortholes() { return joinPortholes; }
    public int getPortholeShape() { return portholeShape; }
    public void setPortholeShape(int shape) {
        if (shape < 0 || shape > 3 || shape == portholeShape) return;
        portholeShape = shape;
        portholeRevision++;
        settingsChanged();
        if (world != null) world.notifyBlockUpdate(pos, world.getBlockState(pos),
                world.getBlockState(pos), 3);
    }
    public void setJoinPortholes(boolean join) {
        if (join == joinPortholes) return;
        joinPortholes = join;
        portholeRevision++;
        settingsChanged();
        if (world != null) world.notifyBlockUpdate(pos, world.getBlockState(pos),
                world.getBlockState(pos), 3);
    }

    public int getGlassShade() { return glassShade; }
    public void setGlassShade(int shade) {
        if (shade < 0 || shade > 2 || shade == glassShade) return;
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

    public int getHousingTexture() { return housingTexture; }
    public int getSideTexture(){return sideTexture;}
    public static boolean supportsSideTexture(net.minecraft.block.Block block) {
        return block instanceof com.vandorlabs.blocks.BlockProgrammableSlab
                || block instanceof com.vandorlabs.blocks.BlockProgrammableStairs
                || block instanceof com.vandorlabs.blocks.BlockProgrammableWall
                && ((com.vandorlabs.blocks.BlockProgrammableWall)block).isPortholeShape();
    }
    public void setSideTexture(int choice) {
        int next=choice<0?-1:ScreenHousingTextures.clamp(choice);
        if(sideTexture==next)return;
        sideTexture=next;settingsChanged();
        if(world!=null){world.notifyBlockUpdate(pos,world.getBlockState(pos),world.getBlockState(pos),3);if(world.isRemote)world.markBlockRangeForRenderUpdate(pos,pos);}
    }
    public boolean isSlabTileSides() { return slabTileSides; }
    public boolean isDiagonalHalfHeight() { return diagonalHalfHeight; }
    public int getDiagonalFill() { return diagonalFill; }
    public void setDiagonalGeometry(int mode, int fill) {
        boolean halfHeight=mode==2,fullWidth=mode==1 || mode==2;
        int nextFill=Math.max(0,Math.min(3,fill));
        if(diagonalHalfHeight==halfHeight && diagonalFullWidth==fullWidth && diagonalFill==nextFill)return;
        diagonalHalfHeight=halfHeight;
        diagonalFullWidth=fullWidth;
        diagonalFill=nextFill;
        settingsChanged(); portholeRevision++;
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
        portholeRevision++;
        settingsChanged();
        if (world != null) world.notifyBlockUpdate(pos, world.getBlockState(pos),
                world.getBlockState(pos), 3);
    }
    public void setSlabTileSides(boolean tileSides) {
        if (slabTileSides == tileSides) return;
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

    @Override public TileEntity channelTile() { return this; }
    @Override public int getRedstoneChannel() { return redstoneChannel; }
    @Override public boolean hasLocalRedstoneSignal() {
        return com.vandorlabs.redstone.LoadedRedstonePower.isPowered(world, pos);
    }
    @Override public void setChannelSignal(boolean powered) {
        if (channelSignal == powered) return;
        channelSignal = powered;
        if (world != null && !world.isRemote) {
            net.minecraft.block.state.IBlockState state = world.getBlockState(pos);
            world.notifyBlockUpdate(pos, state, state, 2);
            world.checkLightFor(net.minecraft.world.EnumSkyBlock.BLOCK, pos);
        }
    }
    @Override public void setRedstoneChannel(int value) {
        int next = Math.max(0, value);
        if (next == redstoneChannel) return;
        int old = redstoneChannel;
        redstoneChannel = next;
        settingsChanged();
        RedstoneChannels.channelChanged(this, old);
    }
    public void localInputChanged() { RedstoneChannels.inputChanged(this); }
    @Override public void onLoad() {
        super.onLoad();
        DeferredTileLoad.schedule(this, this::finishLoading);
    }

    protected void finishLoading() { RedstoneChannels.register(this); }
    @Override public void invalidate() { RedstoneChannels.unregister(this); super.invalidate(); }
    @Override public void onChunkUnload() { RedstoneChannels.unregister(this); super.onChunkUnload(); }

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
        return inputPanel;
    }

    public void setInputPanel(String inputPanel) {
        String next=isValidInputPanel(inputPanel)?inputPanel:INPUT_PANELS[0];
        if(this.inputPanel.equals(next))return;
        this.inputPanel=next;
        settingsChanged();
    }

    public String getSecondaryInputPanel() {
        return secondaryInputPanel;
    }

    public void setSecondaryInputPanel(String inputPanel) {
        String next=isValidInputPanel(inputPanel)?inputPanel:INPUT_PANELS[0];
        if(this.secondaryInputPanel.equals(next))return;
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
        return selectedScreen;
    }

    public void setSelectedScreen(String selectedScreen) {
        if(java.util.Objects.equals(this.selectedScreen,selectedScreen))return;
        this.selectedScreen = selectedScreen;
        settingsChanged();
    }

    public boolean isRedstoneEnabled() {
        return redstoneEnabled;
    }

    public void setRedstoneEnabled(boolean redstoneEnabled) {
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
        return animationSpeedIndex;
    }

    public void setAnimationSpeedIndex(int animationSpeedIndex) {
        int next=ScreenBehavior.clampSpeedIndex(animationSpeedIndex);
        if(this.animationSpeedIndex==next)return;
        this.animationSpeedIndex=next;
        settingsChanged();
    }

    public int getAnimationSpeedTicks() {
        return ScreenBehavior.animationTicks(animationSpeedIndex);
    }

    /**
     * Single source of truth for what the block shows, shared by the
     * renderer and the light level so they can never disagree. Asleep
     * (redstone on, unpowered) is always dark; a powered OFF wakes to
     * animated, mirroring the sequenced displays' wake-only behavior.
     */
    public int getEffectiveMode() {
        // Ungated Static/Animated do not depend on power. Off deliberately does:
        // it wakes to Animated even when the redstone gate is disabled.
        int mode = ScreenBehavior.clampMode(displayMode);
        if (!redstoneEnabled && mode != MODE_OFF) return mode;
        boolean powered = channelSignal || com.vandorlabs.redstone.LoadedRedstonePower.isPowered(world, pos);
        return ScreenBehavior.effectiveMode(mode,redstoneEnabled,powered);
    }

    protected boolean isTriggerPowered() {
        return channelSignal || com.vandorlabs.redstone.LoadedRedstonePower.isPowered(world, pos);
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
        super.writeToNBT(compound);
        compound.setInteger("PrimarySurfaceTexture",primarySurface);compound.setInteger("SecondarySurfaceTexture",secondarySurface);
        compound.setBoolean("CeilingMounted",ceilingMounted);
        if (ceilingPosition>=0) compound.setInteger("CeilingPosition",ceilingPosition);
        new ScreenData(selectedScreen, redstoneEnabled, displayMode, framed,
                animationSpeedIndex, inputPanel, secondaryInputPanel, wallPosition,
                smallInput, redstoneChannel, channelSignal, housingTexture)
                .write(new NbtPrimitiveData(compound));
        compound.setInteger("GlassShade", glassShade);
        compound.setBoolean("JoinPortholes", joinPortholes);
        compound.setInteger("PortholeShape", portholeShape);
        compound.setBoolean("FaceTexturesEnabled", faceTextures.enabled);
        compound.setIntArray("FaceTextures", faceTextures.choices());
        compound.setInteger("SideTexture",sideTexture);
        compound.setBoolean("SlabTileSides", slabTileSides);
        compound.setBoolean("SurfaceTileSides",surfaceTileSides);
        compound.setBoolean("DiagonalFullWidth", diagonalFullWidth);
        compound.setBoolean("DiagonalHalfHeight", diagonalHalfHeight);
        compound.setInteger("DiagonalFill", diagonalFill);
        compound.setInteger("HousingTextureVersion", 1);
        return compound;
    }

    @Override
    public void readFromNBT(NBTTagCompound compound) {
        FaceTextures previousFaces = faceTextures;
        int previousHousing = housingTexture;
        int previousSide=sideTexture;
        boolean previousSlabSides = slabTileSides;
        boolean previousSurfaceSides=surfaceTileSides;
        surfaceTileSides=!compound.hasKey("SurfaceTileSides",1)||compound.getBoolean("SurfaceTileSides");
        boolean previousHalfHeight = diagonalHalfHeight;
        int previousFill = diagonalFill;
        boolean previousDiagonalWidth = diagonalFullWidth;
        int oldChannel = redstoneChannel;
        super.readFromNBT(compound);
        primarySurface=compound.hasKey("PrimarySurfaceTexture",3) && compound.getInteger("PrimarySurfaceTexture")>=0?ScreenHousingTextures.clamp(compound.getInteger("PrimarySurfaceTexture")):-1;
        secondarySurface=compound.hasKey("SecondarySurfaceTexture",3) && compound.getInteger("SecondarySurfaceTexture")>=0?ScreenHousingTextures.clamp(compound.getInteger("SecondarySurfaceTexture")):-1;
        ceilingMounted=compound.getBoolean("CeilingMounted");
        ceilingPosition=compound.hasKey("CeilingPosition",3)
                ?Math.max(0,Math.min(2,compound.getInteger("CeilingPosition"))):-1;
        // NBT is world data, never trust it blindly. The portable codec keeps
        // these defaults identical in every version-specific block entity.
        ScreenData data = ScreenData.read(new NbtPrimitiveData(compound),
                "engineering_screen", INPUT_PANELS[0],
                TileEntityAnimatedScreenSelector::isValidInputPanel);
        selectedScreen = data.selectedScreen;
        redstoneEnabled = data.redstoneEnabled;
        displayMode = data.displayMode;
        framed = data.framed;
        animationSpeedIndex = data.animationSpeedIndex;
        inputPanel = data.inputPanel;
        secondaryInputPanel = data.secondaryInputPanel;
        wallPosition = data.wallPosition;
        smallInput = data.smallInput;
        redstoneChannel = data.redstoneChannel;
        channelSignal = data.channelSignal;
        int savedHousing = data.housingTexture;
        if (!compound.hasKey("HousingTextureVersion", 3)) {
            // The removed vent grille occupied index 14 in existing worlds.
            if (savedHousing == 14) savedHousing = 0;
            else if (savedHousing > 14) savedHousing--;
        }
        housingTexture = ScreenHousingTextures.clamp(savedHousing);
        sideTexture=compound.hasKey("SideTexture",3) && compound.getInteger("SideTexture")>=0?ScreenHousingTextures.clamp(compound.getInteger("SideTexture")):-1;
        glassShade = compound.hasKey("GlassShade", 3)
                ? Math.max(0, Math.min(2, compound.getInteger("GlassShade"))) : 2;
        joinPortholes = compound.getBoolean("JoinPortholes");
        portholeShape = compound.hasKey("PortholeShape",3)
                ? Math.max(0,Math.min(3,compound.getInteger("PortholeShape"))) : 0;
        faceTextures = new FaceTextures(compound.getBoolean("FaceTexturesEnabled"),
                compound.getIntArray("FaceTextures"));
        if (compound.hasKey("SlabTileSides", 1))
            slabTileSides = compound.getBoolean("SlabTileSides");
        diagonalFullWidth = compound.getBoolean("DiagonalFullWidth");
        diagonalHalfHeight = compound.getBoolean("DiagonalHalfHeight");
        diagonalFill = Math.max(0, Math.min(3, compound.getInteger("DiagonalFill")));
        if (world != null && world.isRemote
                && (!previousFaces.equals(faceTextures) || previousSide!=sideTexture || previousHousing != housingTexture || previousSlabSides != slabTileSides || previousSurfaceSides!=surfaceTileSides
                || previousDiagonalWidth != diagonalFullWidth || previousHalfHeight != diagonalHalfHeight
                || previousFill != diagonalFill))
        {
            if(world.getBlockState(pos).getBlock() instanceof com.vandorlabs.blocks.BlockProgrammableWall
                    && ((com.vandorlabs.blocks.BlockProgrammableWall)world.getBlockState(pos).getBlock()).getShape()
                            == com.vandorlabs.blocks.BlockProgrammableWall.Shape.DIAGONAL)
                world.markBlockRangeForRenderUpdate(pos.add(-1,-1,-1),pos.add(1,1,1));
            else world.markBlockRangeForRenderUpdate(pos,pos);
        }
        portholeRevision++;
        if (world != null && !world.isRemote && oldChannel != redstoneChannel)
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
