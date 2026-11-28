package com.vandorlabs.client;

import com.vandorlabs.blocks.*;
import com.vandorlabs.container.ContainerAnimatedScreenSelector;
import com.vandorlabs.display.DisplayArmGeometry;
import com.vandorlabs.display.DisplayArmSettings;
import com.vandorlabs.items.ItemDisplayArm;
import com.vandorlabs.network.MessageDisplayArm;
import com.vandorlabs.render.ScreenSurface;
import com.vandorlabs.tiles.TileEntityDisplayArm;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.*;
import java.util.ArrayList;
import java.util.UUID;

/** Actual Forge adapters: item NBT, server admission, posed controls and outside-cell selection. */
public final class DisplayArmChecks {
    private static int checks;
    private static final BlockPos AT = new BlockPos(0, 80, 0);
    private static net.minecraft.server.MinecraftServer marker;
    private static final class World extends NonRenderingChecks.MemoryWorld {
        World() { super(false); }
        @Override public net.minecraft.server.MinecraftServer getMinecraftServer() { return marker; }
    }
    private static final class Player extends EntityPlayer {
        boolean editable = true;
        int gui = -1;
        Player(World world) {
            super(world, new com.mojang.authlib.GameProfile(new UUID(0, 902), "DisplayArm"));
            capabilities.allowEdit = true;
            capabilities.isCreativeMode = true;
            setPosition(0.5, 80, -2);
        }
        @Override public boolean isCreative() { return capabilities.isCreativeMode; }
        @Override public boolean isSpectator() { return false; }
        @Override public boolean canPlayerEdit(BlockPos pos, EnumFacing face, ItemStack held) { return editable; }
        @Override public void openGui(Object mod, int id, net.minecraft.world.World world, int x, int y, int z) { gui = id; }
    }
    private static void require(boolean test, String message) {
        checks++;
        if (!test) throw new AssertionError(message);
    }

    public static void main(String[] args) throws Exception {
        net.minecraft.init.Bootstrap.register();
        com.vandorlabs.VandorLabs.proxy = new com.vandorlabs.CommonProxy();
        java.lang.reflect.Field unsafe = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
        unsafe.setAccessible(true);
        marker = (net.minecraft.server.MinecraftServer)((sun.misc.Unsafe)unsafe.get(null))
                .allocateInstance(net.minecraft.server.dedicated.DedicatedServer.class);
        net.minecraftforge.fml.common.registry.GameRegistry.registerTileEntity(TileEntityDisplayArm.class,
                new ResourceLocation("minecraft:display_arm_checks"));
        BlockDisplayArm block = (BlockDisplayArm)ModBlocks.PROGRAMMABLE_DISPLAY_ARM;
        net.minecraftforge.fml.common.registry.ForgeRegistries.BLOCKS.register(block);
        ItemDisplayArm item = new ItemDisplayArm(block);
        item.setRegistryName(block.getRegistryName());
        net.minecraftforge.fml.common.registry.ForgeRegistries.ITEMS.register(item);
        net.minecraftforge.fml.common.registry.ForgeRegistries.ITEMS.register(com.vandorlabs.items.ModItems.CONFIGURIZER);
        persistence();
        packets();
        for (EnumFacing mount : EnumFacing.values()) for (EnumFacing facing : EnumFacing.HORIZONTALS)
            for (int variant = 0; variant < 4; variant++) {
                World world = new World();
                Player player = new Player(world);
                world.states.put(AT.offset(mount.getOpposite()), Blocks.STONE.getDefaultState());
                TileEntityDisplayArm configured = new TileEntityDisplayArm();
                DisplayArmSettings settings = new DisplayArmSettings((variant & 1) != 0, (variant & 2) != 0,
                        19, -11, 27, 37, -48, 23);
                configured.setArmSettings(settings);
                configured.setMountFace(mount.getOpposite());
                configured.setInputPanel(com.vandorlabs.tiles.TileEntityAnimatedScreenSelector.INPUT_PANELS[1]);
                NBTTagCompound saved = configured.writeToNBT(new NBTTagCompound());
                saved.setInteger(TileEntityDisplayArm.ITEM_FACING, facing.getIndex());
                ItemStack stack = new ItemStack(item);
                stack.setTagInfo("BlockEntityTag", saved);
                player.setHeldItem(EnumHand.MAIN_HAND, stack);
                IBlockState placement = block.getStateForPlacement(world, AT, mount, .5F, .5F, .5F, 0, player);
                require(item.placeBlockAt(stack, player, world, AT, mount, .5F, .5F, .5F, placement), "placement refused");
                TileEntityDisplayArm tile = (TileEntityDisplayArm)world.getTileEntity(AT);
                require(tile.getMountFace() == mount, "old item mount overrides clicked face");
                require(tile.getScreenFacing() == facing && settings.equals(tile.getArmSettings()), "configured pose lost on placement");
                require(tile.getInputPanel().equals(configured.getInputPanel()), "input selection lost");
                NBTTagCompound drop = block.createConfiguredDrop(tile).getSubCompound("BlockEntityTag");
                require(TileEntityDisplayArm.readSettings(drop).equals(settings), "configured drop lost pose");
                require(drop.getInteger(TileEntityDisplayArm.ITEM_FACING) == facing.getIndex(), "configured drop lost direction");
                require(!drop.getCompoundTag(TileEntityDisplayArm.SETTINGS).hasKey("Mount"), "drop kept old support face");
                require(!com.vandorlabs.tiles.DisplayJoinGeometry.supportsJoining(tile), "posed displays auto-join unrelated planes");
                controls(world, tile, facing);
                geometry(world, tile);
                permissions(world, player, tile, block);
                tile.onChunkUnload();
                tile.invalidate();
                com.vandorlabs.redstone.RedstoneChannels.worldUnloaded(new net.minecraftforge.event.world.WorldEvent.Unload(world));
            }
        require(com.vandorlabs.config.BlockCategories.of(BlockDisplayArm.NAME).equals("consoles_inputs"), "wrong catalog category");
        System.out.println("Display arm Forge adapters PASS " + checks + " persistence/placement/drops/packets/permissions/posed-controls/selection/collision");
    }

    private static void persistence() {
        TileEntityDisplayArm tile = new TileEntityDisplayArm();
        tile.readFromNBT(new NBTTagCompound());
        require(tile.getArmSettings().equals(DisplayArmSettings.DEFAULT), "absent settings default");
        for (EnumFacing mount : EnumFacing.values()) {
            tile.setMountFace(mount);
            tile.setArmSettings(new DisplayArmSettings(true, true, -32, 32, 17, -180, 90, 180));
            NBTTagCompound saved = tile.writeToNBT(new NBTTagCompound());
            TileEntityDisplayArm read = new TileEntityDisplayArm();
            read.readFromNBT(saved);
            require(read.getMountFace() == mount && read.getArmSettings().equals(tile.getArmSettings()), "NBT round trip");
            saved.getCompoundTag(TileEntityDisplayArm.SETTINGS).setInteger("Height", Integer.MIN_VALUE);
            saved.getCompoundTag(TileEntityDisplayArm.SETTINGS).setInteger("Pitch", Integer.MAX_VALUE);
            read.readFromNBT(saved);
            require(read.getArmSettings().height == -32 && read.getArmSettings().pitch == 90, "persisted values not bounded");
        }
    }

    private static void packets() {
        ByteBuf source = Unpooled.buffer();
        new MessageDisplayArm(AT, EnumFacing.WEST, new DisplayArmSettings(true, true, -32, 32, 12, -179, -89, 177)).toBytes(source);
        require(source.readableBytes() == 29, "fixed wire length");
        MessageDisplayArm read = new MessageDisplayArm();
        read.fromBytes(source.copy());
        ByteBuf copy = Unpooled.buffer(); read.toBytes(copy);
        require(source.equals(copy), "wire round trip");
        for (int size : new int[]{0, 8, 24, 28, 30, 128}) {
            ByteBuf bad = Unpooled.buffer(size).writeZero(size);
            rejected(bad, "wire length " + size);
        }
        for (int[] edit : new int[][]{{8, 0}, {8, 6}, {9, 2}, {10, 255}}) {
            ByteBuf bad = source.copy(); bad.setByte(edit[0], edit[1]); rejected(bad, "invalid enum/flag");
        }
        for (int offset : new int[]{11, 15, 19}) for (int value : new int[]{-33, 33, Integer.MAX_VALUE}) {
            ByteBuf bad = source.copy(); bad.setInt(offset, value); rejected(bad, "invalid position");
        }
        for (int offset : new int[]{23, 25, 27}) {
            ByteBuf bad = source.copy(); bad.setShort(offset, offset == 25 ? 91 : 181); rejected(bad, "invalid angle");
        }
        source.release(); copy.release();
    }
    private static void rejected(ByteBuf data, String message) {
        boolean refused = false;
        try { new MessageDisplayArm().fromBytes(data); } catch (IllegalArgumentException expected) { refused = true; }
        finally { data.release(); }
        require(refused, message);
    }

    private static void controls(World world, TileEntityDisplayArm tile, EnumFacing facing) {
        IBlockState state = world.getBlockState(AT);
        for (int slot = 0; slot < 2; slot++) {
            ScreenSurface.Quad q = tile.geometry().surface(slot);
            int row = slot == 0 ? 3 : 1;
            double u = 110D / 128, v = (RedstoneScreenInteractions.rowTop(state.getBlock(), slot) + row * 12 + 5D)
                    / RedstoneScreenInteractions.displayHeight(state.getBlock(), slot);
            double x = q.topRight.x + (q.topLeft.x - q.topRight.x) * u;
            double y = q.topRight.y + (q.bottomRight.y - q.topRight.y) * v;
            double z = q.topRight.z + (q.bottomRight.z - q.topRight.z) * v;
            Vec3d start = placed(tile.geometry().display.point(x, y + q.ny * 16, z + q.nz * 16));
            Vec3d end = placed(tile.geometry().display.point(x, y - q.ny * 16, z - q.nz * 16));
            if (slot == 1 && !tile.getArmSettings().input) {
                require(RedstoneScreenInteractions.hitRow(state, tile, slot, start, end, 4) == -1, "hidden deck is interactive");
            } else {
                require(RedstoneScreenInteractions.hitRow(state, tile, slot, start, end, 8) == row, "posed control row " + facing);
                require(Math.abs(RedstoneScreenInteractions.hitColumn(state, tile, slot, start, end) - 110) < 1e-7,
                        "posed control column " + facing);
            }
        }
    }
    private static Vec3d placed(DisplayArmGeometry.Vec p) { return new Vec3d(p.x / 16, p.y / 16, p.z / 16); }

    private static void geometry(World world, TileEntityDisplayArm tile) {
        DisplayArmGeometry g = tile.geometry();
        Vec3d start = placed(g.display.point(5, 0, -20)).addVector(AT.getX(), AT.getY(), AT.getZ());
        Vec3d end = placed(g.display.point(5, 0, 0)).addVector(AT.getX(), AT.getY(), AT.getZ());
        RayTraceResult direct = tile.shape().trace(AT, start, end);
        require(direct != null && direct.getBlockPos().equals(AT), "exact displaced-screen ray");
        DisplayArmInteractions.INDEX.add(tile);
        RayTraceResult indexed = DisplayArmInteractions.trace(world, start, end);
        require(indexed != null && indexed.getBlockPos().equals(AT), "outside-voxel selection index");
        AxisAlignedBB bounds = tile.getRenderBoundingBox();
        for (DisplayArmGeometry.Box box : g.boxes) for (DisplayArmGeometry.Vec point : box.corners()) {
            Vec3d p = placed(point).addVector(AT.getX(), AT.getY(), AT.getZ());
            require(bounds.grow(1e-7).contains(p), "render bounds omit geometry");
        }
        ArrayList<AxisAlignedBB> boxes = new ArrayList<>();
        tile.shape().addCollisions(AT, bounds, boxes);
        require(boxes.size() > 1 && !boxes.contains(bounds), "empty hull became one solid collision cube");
        DisplayArmInteractions.INDEX.remove(tile);
        require(DisplayArmInteractions.trace(world, start, end) == null, "removed index entry remains selectable");
    }

    private static void permissions(World world, Player player, TileEntityDisplayArm tile, BlockDisplayArm block) {
        player.setHeldItem(EnumHand.MAIN_HAND, ItemStack.EMPTY);
        player.openContainer = new ContainerAnimatedScreenSelector(player.inventory, tile);
        require(MessageDisplayArm.canApply(player, tile), "valid editor rejected");
        player.editable = false;
        require(!MessageDisplayArm.canApply(player, tile) && !BlockDisplayArm.openSettings(player, world, AT), "protected edit admitted");
        player.editable = true;
        player.setPosition(100, 80, 100);
        require(!MessageDisplayArm.canApply(player, tile), "distant editor admitted");
        player.setPosition(.5, 80, -2);
        player.capabilities.isCreativeMode = false;
        require(!MessageDisplayArm.canApply(player, tile), "survival without tool admitted");
        player.setHeldItem(EnumHand.MAIN_HAND, new ItemStack(com.vandorlabs.items.ModItems.CONFIGURIZER));
        require(MessageDisplayArm.canApply(player, tile), "survival Configurizer rejected");
        player.openContainer = player.inventoryContainer;
        require(!MessageDisplayArm.canApply(player, tile), "closed editor admitted");
        player.capabilities.isCreativeMode = true;
        player.setHeldItem(EnumHand.MAIN_HAND, ItemStack.EMPTY);
        require(block.onBlockActivated(world, AT, world.getBlockState(AT), player, EnumHand.MAIN_HAND,
                EnumFacing.NORTH, .5F, .5F, .5F) && player.gui == com.vandorlabs.GuiHandler.GUI_ANIMATED_SCREEN_SELECTOR,
                "ordinary right-click did not open screen dialog");
    }
}
