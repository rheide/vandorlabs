package com.vandorlabs.tiles;

import com.vandorlabs.blocks.BlockAnimatedScreenSelector;
import com.vandorlabs.blocks.DisplayArmShape;
import com.vandorlabs.display.DisplayArmGeometry;
import com.vandorlabs.display.DisplayArmSettings;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.AxisAlignedBB;

/** Screen configuration is inherited; only the mounting and display pose are new. */
public final class TileEntityDisplayArm extends TileEntityAnimatedScreenSelector {
    public static final String SETTINGS = "DisplayArm";
    public static final String ITEM_FACING = "DisplayArmFacing";
    private EnumFacing mountFace = EnumFacing.UP;
    private boolean mountInitialized;
    private DisplayArmSettings arm = DisplayArmSettings.DEFAULT;
    private DisplayArmGeometry geometry;
    private DisplayArmShape shape;
    private EnumFacing geometryFacing;

    public DisplayArmSettings getArmSettings() { return arm; }
    public EnumFacing getMountFace() { return mountFace; }
    public boolean hasMount() { return mountInitialized; }

    public EnumFacing getScreenFacing() {
        if (world == null) return EnumFacing.NORTH;
        net.minecraft.block.state.IBlockState state = world.getBlockState(pos);
        if (!state.getPropertyKeys().contains(BlockAnimatedScreenSelector.FACING)) return EnumFacing.NORTH;
        EnumFacing facing = state.getValue(BlockAnimatedScreenSelector.FACING);
        return facing.getAxis().isHorizontal() ? facing : EnumFacing.NORTH;
    }

    public void setMountFace(EnumFacing value) {
        if (value == null || value == mountFace && mountInitialized) return;
        mountFace = value;
        mountInitialized = true;
        changed();
    }

    public void setArmSettings(DisplayArmSettings value) {
        if (value == null || value.equals(arm)) return;
        arm = value;
        changed();
    }

    private void changed() {
        geometry = null;
        shape = null;
        markDirty();
        if (world != null) {
            net.minecraft.block.state.IBlockState state = world.getBlockState(pos);
            world.notifyBlockUpdate(pos, state, state, 3);
        }
    }

    public DisplayArmGeometry geometry() {
        EnumFacing facing = getScreenFacing();
        if (geometry == null || geometryFacing != facing) {
            geometry = DisplayArmGeometry.create(mountFace.getIndex(), facing.getIndex(), arm);
            geometryFacing = facing;
            shape = null;
        }
        return geometry;
    }

    public DisplayArmShape shape() {
        DisplayArmGeometry current = geometry();
        if (shape == null) shape = new DisplayArmShape(current);
        return shape;
    }

    public static DisplayArmSettings readSettings(NBTTagCompound root) {
        if (root == null || !root.hasKey(SETTINGS, 10)) return DisplayArmSettings.DEFAULT;
        NBTTagCompound data = root.getCompoundTag(SETTINGS);
        return new DisplayArmSettings(data.getBoolean("Half"), data.getBoolean("Input"),
                data.getInteger("Right"), data.getInteger("Height"),
                data.hasKey("Forward", 3) ? data.getInteger("Forward") : DisplayArmSettings.DEFAULT.forward,
                data.getInteger("Yaw"), data.getInteger("Pitch"), data.getInteger("Roll"));
    }

    @Override public NBTTagCompound writeToNBT(NBTTagCompound root) {
        super.writeToNBT(root);
        NBTTagCompound data = new NBTTagCompound();
        if (mountInitialized) data.setByte("Mount", (byte) mountFace.getIndex());
        data.setBoolean("Half", arm.half);
        data.setBoolean("Input", arm.input);
        data.setInteger("Right", arm.right);
        data.setInteger("Height", arm.height);
        data.setInteger("Forward", arm.forward);
        data.setInteger("Yaw", arm.yaw);
        data.setInteger("Pitch", arm.pitch);
        data.setInteger("Roll", arm.roll);
        root.setTag(SETTINGS, data);
        return root;
    }

    @Override public void readFromNBT(NBTTagCompound root) {
        arm = readSettings(root);
        NBTTagCompound data = root.getCompoundTag(SETTINGS);
        int mount = data.hasKey("Mount", 1) ? data.getByte("Mount") : EnumFacing.UP.getIndex();
        mountFace = mount >= 0 && mount < 6 ? EnumFacing.getFront(mount) : EnumFacing.UP;
        mountInitialized = data.hasKey("Mount", 1);
        geometry = null;
        shape = null;
        super.readFromNBT(root);
    }

    @Override public AxisAlignedBB getRenderBoundingBox() {
        // Include the small surface/frame depth biases at every display angle.
        return shape().bounds.grow(1.0 / 128).offset(pos);
    }
    @Override public boolean hasFastRenderer() { return false; }
    @Override public boolean shouldRenderInPass(int pass) { return pass == 0; }

    @Override public boolean isUsableByPlayer(net.minecraft.entity.player.EntityPlayer player) {
        return super.isUsableByPlayer(player)
                && com.vandorlabs.items.ConfigurationAccess.canKeepDialogOpen(player, world, pos);
    }

    @Override public void onLoad() {
        super.onLoad();
        com.vandorlabs.blocks.DisplayArmInteractions.INDEX.add(this);
    }

    @Override public void invalidate() {
        com.vandorlabs.blocks.DisplayArmInteractions.INDEX.remove(this);
        super.invalidate();
    }

    @Override public void onChunkUnload() {
        com.vandorlabs.blocks.DisplayArmInteractions.INDEX.remove(this);
        super.onChunkUnload();
    }
}
