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
        if (block != null && (block.getClass() == com.vandorlabs.blocks.BlockProgrammableBlock.class
                || block instanceof com.vandorlabs.blocks.BlockProgrammableSlab)) return false;
        return super.shouldRenderInPass(pass);
    }

    /** Only atlas-backed opaque solids can join Forge's shared tile vertex buffer. */
    @Override public boolean hasFastRenderer() {
        if (world == null) return false;
        net.minecraft.block.Block block = world.getBlockState(pos).getBlock();
        return block instanceof com.vandorlabs.blocks.BlockProgrammableTrigger
                || block instanceof com.vandorlabs.blocks.BlockProgrammableLight;
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

    private String selectedScreen = "engineering_screen";
    private boolean redstoneEnabled = false;
    private int displayMode = MODE_ANIMATED;
    private boolean framed = true;
    private int animationSpeedIndex = 1;
    private String inputPanel = INPUT_PANELS[0];
    private String secondaryInputPanel = INPUT_PANELS[0];
    /** -1 means a legacy block whose lower/top position comes from metadata. */
    private int wallPosition = -1;
    private boolean smallInput = false;
    private int redstoneChannel;
    private boolean channelSignal;
    private int housingTexture;
    private boolean slabTileSides;
    private boolean diagonalFullWidth;
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
        markDirty();
        if (world != null) world.notifyBlockUpdate(pos, world.getBlockState(pos),
                world.getBlockState(pos), 3);
    }
    public void setJoinPortholes(boolean join) {
        if (join == joinPortholes) return;
        joinPortholes = join;
        portholeRevision++;
        markDirty();
        if (world != null) world.notifyBlockUpdate(pos, world.getBlockState(pos),
                world.getBlockState(pos), 3);
    }

    public int getGlassShade() { return glassShade; }
    public void setGlassShade(int shade) {
        if (shade < 0 || shade > 2 || shade == glassShade) return;
        glassShade = shade;
        markDirty();
        if (world != null) world.notifyBlockUpdate(pos, world.getBlockState(pos),
                world.getBlockState(pos), 3);
    }

    public int getHousingTexture() { return housingTexture; }
    public boolean isSlabTileSides() { return slabTileSides; }
    public boolean isDiagonalFullWidth() { return diagonalFullWidth; }
    public void setDiagonalFullWidth(boolean fullWidth) {
        if (diagonalFullWidth == fullWidth) return;
        diagonalFullWidth = fullWidth;
        markDirty();
        if (world != null) world.notifyBlockUpdate(pos, world.getBlockState(pos),
                world.getBlockState(pos), 3);
    }
    public void setSlabTileSides(boolean tileSides) {
        if (slabTileSides == tileSides) return;
        slabTileSides = tileSides;
        markDirty();
        if (world != null) world.notifyBlockUpdate(pos, world.getBlockState(pos),
                world.getBlockState(pos), 3);
    }
    public void setHousingTexture(int choice) {
        int next = ScreenHousingTextures.clamp(choice);
        if (housingTexture == next) return;
        housingTexture = next;
        markDirty();
        if (world != null && world.isRemote) world.markBlockRangeForRenderUpdate(pos,pos);
    }

    @Override public TileEntity channelTile() { return this; }
    @Override public int getRedstoneChannel() { return redstoneChannel; }
    @Override public boolean hasLocalRedstoneSignal() {
        return world != null && pos != null && world.isBlockPowered(pos);
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
        markDirty();
        RedstoneChannels.channelChanged(this, old);
    }
    public void localInputChanged() { RedstoneChannels.inputChanged(this); }
    @Override public void onLoad() { super.onLoad(); RedstoneChannels.register(this); }
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
        this.inputPanel = isValidInputPanel(inputPanel) ? inputPanel : INPUT_PANELS[0];
        markDirty();
    }

    public String getSecondaryInputPanel() {
        return secondaryInputPanel;
    }

    public void setSecondaryInputPanel(String inputPanel) {
        this.secondaryInputPanel = isValidInputPanel(inputPanel)
                ? inputPanel : INPUT_PANELS[0];
        markDirty();
    }

    public int getWallPosition(int legacyPosition) {
        return wallPosition < 0 ? legacyPosition : wallPosition;
    }

    public void setWallPosition(int wallPosition) {
        this.wallPosition = Math.max(0, Math.min(2, wallPosition));
        markDirty();
    }

    public boolean isSmallInput() {
        return smallInput;
    }

    public void setSmallInput(boolean smallInput) {
        this.smallInput = smallInput;
        markDirty();
    }

    public String getSelectedScreen() {
        return selectedScreen;
    }

    public void setSelectedScreen(String selectedScreen) {
        this.selectedScreen = selectedScreen;
        markDirty();
    }

    public boolean isRedstoneEnabled() {
        return redstoneEnabled;
    }

    public void setRedstoneEnabled(boolean redstoneEnabled) {
        this.redstoneEnabled = redstoneEnabled;
        markDirty();
    }

    public int getDisplayMode() {
        return displayMode;
    }

    public void setDisplayMode(int displayMode) {
        this.displayMode = ScreenBehavior.clampMode(displayMode);
        markDirty();
    }

    public boolean isFramed() {
        return framed;
    }

    public void setFramed(boolean framed) {
        this.framed = framed;
        markDirty();
    }

    public int getAnimationSpeedIndex() {
        return animationSpeedIndex;
    }

    public void setAnimationSpeedIndex(int animationSpeedIndex) {
        this.animationSpeedIndex = ScreenBehavior.clampSpeedIndex(animationSpeedIndex);
        markDirty();
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
        boolean powered = (world != null && pos != null && world.isBlockPowered(pos)) || channelSignal;
        return ScreenBehavior.effectiveMode(displayMode,redstoneEnabled,powered);
    }

    protected boolean isTriggerPowered() {
        return (world != null && pos != null && world.isBlockPowered(pos)) || channelSignal;
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
        new ScreenData(selectedScreen, redstoneEnabled, displayMode, framed,
                animationSpeedIndex, inputPanel, secondaryInputPanel, wallPosition,
                smallInput, redstoneChannel, channelSignal, housingTexture)
                .write(new NbtPrimitiveData(compound));
        compound.setInteger("GlassShade", glassShade);
        compound.setBoolean("JoinPortholes", joinPortholes);
        compound.setInteger("PortholeShape", portholeShape);
        compound.setBoolean("SlabTileSides", slabTileSides);
        compound.setBoolean("DiagonalFullWidth", diagonalFullWidth);
        compound.setInteger("HousingTextureVersion", 1);
        return compound;
    }

    @Override
    public void readFromNBT(NBTTagCompound compound) {
        int previousHousing = housingTexture;
        boolean previousSlabSides = slabTileSides;
        boolean previousDiagonalWidth = diagonalFullWidth;
        int oldChannel = redstoneChannel;
        super.readFromNBT(compound);
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
        glassShade = compound.hasKey("GlassShade", 3)
                ? Math.max(0, Math.min(2, compound.getInteger("GlassShade"))) : 2;
        joinPortholes = compound.getBoolean("JoinPortholes");
        portholeShape = compound.hasKey("PortholeShape",3)
                ? Math.max(0,Math.min(3,compound.getInteger("PortholeShape"))) : 0;
        slabTileSides = compound.getBoolean("SlabTileSides");
        diagonalFullWidth = compound.getBoolean("DiagonalFullWidth");
        if (world != null && world.isRemote
                && (previousHousing != housingTexture || previousSlabSides != slabTileSides
                || previousDiagonalWidth != diagonalFullWidth))
            world.markBlockRangeForRenderUpdate(pos,pos);
        portholeRevision++;
        if (world != null && !world.isRemote && oldChannel != redstoneChannel)
            RedstoneChannels.channelChanged(this, oldChannel);
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
