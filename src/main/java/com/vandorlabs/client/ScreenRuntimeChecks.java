package com.vandorlabs.client;

import com.vandorlabs.blocks.BlockAnimatedScreenSelector;
import com.vandorlabs.blocks.ModBlocks;
import com.vandorlabs.network.MessageSyncScreenSelector;
import com.vandorlabs.tiles.TileEntityAnimatedScreenSelector;
import com.vandorlabs.tiles.ScreenHousingTextures;
import net.minecraft.client.Minecraft;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.util.NonNullList;
import com.vandorlabs.blocks.BlockProgrammableDiagonalScreen;
import com.vandorlabs.blocks.BlockProgrammableInput;
import com.vandorlabs.blocks.BlockProgrammableFullInput;

/** Serialization and placement contracts for programmable displays. */
final class ScreenRuntimeChecks {

    private ScreenRuntimeChecks() {}

    static void run(EntityPlayer player) {
        checkTilePersistence();
        checkHousingSprites();
        checkSurvivalDropRoundTrip(player);
        checkPacketRoundTrip();
        checkFacings(ModBlocks.ANIMATED_SCREEN_SELECTOR);
        checkViewscreenPlacement(player);
        checkDiagonalPlacement(player);
        checkInputPlacement(player);
        checkInputScrollbar();
        checkFullInputPlacement(player);
        System.out.println("[vandorlabs][reprolab] screen-runtime PASS");
    }

    private static void checkInputScrollbar() {
        require(GuiProgrammableInput.scrollForDrag(105, 100, 100, 20, 10, 5) == 0
                        && GuiProgrammableInput.scrollForDrag(145, 100, 100, 20, 10, 5) == 5
                        && GuiProgrammableInput.scrollForDrag(185, 100, 100, 20, 10, 5) == 10,
                "half-input scrollbar drag does not cover its full range");
    }

    private static void checkHousingSprites() {
        for (int i = 0; i < ScreenHousingTextures.IDS.length; i++) {
            String name = ScreenHousingTextures.texture(i);
            require(name.equals(Minecraft.getMinecraft().getTextureMapBlocks()
                            .getAtlasSprite(name).getIconName()),
                    "missing housing sprite: " + name);
        }
    }

    private static void checkTilePersistence() {
        for (String panel : TileEntityAnimatedScreenSelector.INPUT_PANELS) {
            TileEntityAnimatedScreenSelector source = new TileEntityAnimatedScreenSelector();
            source.setSelectedScreen("engineering_screen");
            source.setRedstoneEnabled(true);
            source.setDisplayMode(TileEntityAnimatedScreenSelector.MODE_STATIC);
            source.setFramed(false);
            source.setAnimationSpeedIndex(2);
            source.setInputPanel(panel);
            source.setSecondaryInputPanel(TileEntityAnimatedScreenSelector.INPUT_PANELS[
                    TileEntityAnimatedScreenSelector.INPUT_PANELS.length - 1]);
            source.setWallPosition(1);
            source.setSmallInput(true);
            source.setRedstoneChannel(4271);
            source.setHousingTexture(8);
            NBTTagCompound tag = source.writeToNBT(new NBTTagCompound());
            TileEntityAnimatedScreenSelector restored =
                    new TileEntityAnimatedScreenSelector();
            restored.readFromNBT(tag);
            require("engineering_screen".equals(restored.getSelectedScreen())
                            && restored.isRedstoneEnabled()
                            && restored.getDisplayMode()
                            == TileEntityAnimatedScreenSelector.MODE_STATIC
                            && !restored.isFramed()
                            && restored.getAnimationSpeedIndex() == 2
                            && panel.equals(restored.getInputPanel())
                            && TileEntityAnimatedScreenSelector.INPUT_PANELS[
                                    TileEntityAnimatedScreenSelector.INPUT_PANELS.length - 1]
                                    .equals(restored.getSecondaryInputPanel())
                            && restored.getWallPosition(0) == 1
                            && restored.isSmallInput()
                            && restored.getRedstoneChannel() == 4271
                            && restored.getHousingTexture() == 8,
                    "tile NBT round trip failed for " + panel);
        }
        TileEntityAnimatedScreenSelector invalid = new TileEntityAnimatedScreenSelector();
        invalid.setInputPanel("not_a_real_texture");
        require(TileEntityAnimatedScreenSelector.INPUT_PANELS[0]
                        .equals(invalid.getInputPanel()),
                "invalid input panel was not rejected");
    }

    private static void checkSurvivalDropRoundTrip(EntityPlayer player) {
        Block[] programmableBlocks = {
                ModBlocks.ANIMATED_SCREEN_SELECTOR,
                ModBlocks.PROGRAMMABLE_CONSOLE,
                ModBlocks.PROGRAMMABLE_DIAGONAL_SCREEN,
                ModBlocks.PROGRAMMABLE_INPUT,
                ModBlocks.PROGRAMMABLE_HALF_CONSOLE,
                ModBlocks.PROGRAMMABLE_FULL_INPUT
        };
        for (int i = 0; i < programmableBlocks.length; i++) {
            Block raw = programmableBlocks[i];
            require(raw instanceof BlockAnimatedScreenSelector,
                    "programmable drop test received wrong block type");
            BlockAnimatedScreenSelector block = (BlockAnimatedScreenSelector) raw;
            BlockPos pos = new BlockPos(20 + i, 250, 0);
            IBlockState state = block.getDefaultState();
            player.world.setBlockState(pos, state, 2);
            TileEntityAnimatedScreenSelector source =
                    (TileEntityAnimatedScreenSelector) player.world.getTileEntity(pos);
            configureDistinctly(source);

            NonNullList<ItemStack> drops = NonNullList.create();
            block.getDrops(drops, player.world, pos, state, 0);
            require(drops.size() == 1, "programmable block did not drop exactly once: "
                    + block.getRegistryName());
            ItemStack drop = drops.get(0);
            NBTTagCompound itemData = drop.getSubCompound("BlockEntityTag");
            require(itemData != null && !itemData.hasKey("id")
                            && !itemData.hasKey("x") && !itemData.hasKey("y")
                            && !itemData.hasKey("z"),
                    "programmable drop has missing or position-bound NBT: "
                            + block.getRegistryName());

            player.world.setBlockToAir(pos);
            player.world.setBlockState(pos, state, 2);
            require(ItemBlock.setTileEntityNBT(player.world, player, pos, drop),
                    "vanilla ItemBlock did not restore programmable drop NBT: "
                            + block.getRegistryName());
            assertDistinctConfiguration((TileEntityAnimatedScreenSelector)
                    player.world.getTileEntity(pos), block.getRegistryName().toString());
            player.world.setBlockToAir(pos);
        }
    }

    private static void configureDistinctly(TileEntityAnimatedScreenSelector tile) {
        tile.setSelectedScreen("engineering_screen");
        tile.setRedstoneEnabled(true);
        tile.setDisplayMode(TileEntityAnimatedScreenSelector.MODE_STATIC);
        tile.setFramed(false);
        tile.setAnimationSpeedIndex(2);
        tile.setInputPanel(TileEntityAnimatedScreenSelector.INPUT_PANELS[1]);
        tile.setSecondaryInputPanel(TileEntityAnimatedScreenSelector.INPUT_PANELS[
                TileEntityAnimatedScreenSelector.INPUT_PANELS.length - 1]);
        tile.setWallPosition(1);
        tile.setSmallInput(true);
        tile.setRedstoneChannel(4271);
        tile.setHousingTexture(8);
    }

    private static void assertDistinctConfiguration(
            TileEntityAnimatedScreenSelector tile, String context) {
        require(tile != null && "engineering_screen".equals(tile.getSelectedScreen())
                        && tile.isRedstoneEnabled()
                        && tile.getDisplayMode()
                        == TileEntityAnimatedScreenSelector.MODE_STATIC
                        && !tile.isFramed() && tile.getAnimationSpeedIndex() == 2
                        && TileEntityAnimatedScreenSelector.INPUT_PANELS[1]
                        .equals(tile.getInputPanel())
                        && TileEntityAnimatedScreenSelector.INPUT_PANELS[
                                TileEntityAnimatedScreenSelector.INPUT_PANELS.length - 1]
                        .equals(tile.getSecondaryInputPanel())
                        && tile.getWallPosition(0) == 1 && tile.isSmallInput()
                        && tile.getRedstoneChannel() == 4271
                        && tile.getHousingTexture() == 8,
                "programmable drop configuration did not round-trip: " + context);
    }

    private static void checkPacketRoundTrip() {
        BlockPos pos = new BlockPos(12, 34, -56);
        MessageSyncScreenSelector source = new MessageSyncScreenSelector(pos,
                "engineering_screen", true,
                TileEntityAnimatedScreenSelector.MODE_ANIMATED, false, 2,
                TileEntityAnimatedScreenSelector.INPUT_PANELS[
                        TileEntityAnimatedScreenSelector.INPUT_PANELS.length - 1],
                TileEntityAnimatedScreenSelector.INPUT_PANELS[1], true, 4271, 8);
        ByteBuf bytes = Unpooled.buffer();
        source.toBytes(bytes);
        MessageSyncScreenSelector restored = new MessageSyncScreenSelector();
        restored.fromBytes(bytes);
        require(pos.equals(restored.getPos())
                        && "engineering_screen".equals(restored.getSelectedScreen())
                        && restored.isRedstoneEnabled()
                        && restored.getDisplayMode()
                        == TileEntityAnimatedScreenSelector.MODE_ANIMATED
                        && !restored.isFramed()
                        && restored.getAnimationSpeedIndex() == 2
                        && TileEntityAnimatedScreenSelector.INPUT_PANELS[
                                TileEntityAnimatedScreenSelector.INPUT_PANELS.length - 1]
                                .equals(restored.getInputPanel())
                        && TileEntityAnimatedScreenSelector.INPUT_PANELS[1]
                                .equals(restored.getSecondaryInputPanel())
                        && restored.isSmallInput()
                        && restored.getRedstoneChannel() == 4271
                        && restored.getHousingTexture() == 8,
                "selector packet round trip failed");
        bytes.release();
    }

    private static void checkFacings(Block raw) {
        require(raw instanceof BlockAnimatedScreenSelector,
                "programmable screen block missing");
        BlockAnimatedScreenSelector block = (BlockAnimatedScreenSelector) raw;
        for (EnumFacing facing : EnumFacing.values()) {
            IBlockState state = block.getDefaultState()
                    .withProperty(BlockAnimatedScreenSelector.FACING, facing);
            IBlockState restored = block.getStateFromMeta(block.getMetaFromState(state));
            require(restored.getValue(BlockAnimatedScreenSelector.FACING) == facing,
                    "facing metadata failed for " + block.getRegistryName() + " " + facing);
        }
    }

    private static void checkViewscreenPlacement(EntityPlayer player) {
        require(ModBlocks.ANIMATED_SCREEN_SELECTOR
                        instanceof BlockAnimatedScreenSelector,
                "programmable viewscreen block missing");
        BlockAnimatedScreenSelector block = (BlockAnimatedScreenSelector)
                ModBlocks.ANIMATED_SCREEN_SELECTOR;
        BlockPos pos = new BlockPos(0, 250, 0);
        for (EnumFacing side : EnumFacing.values()) {
            player.world.setBlockToAir(pos.offset(side));
        }
        for (EnumFacing playerFacing : EnumFacing.HORIZONTALS) {
            player.rotationYaw = playerFacing.getHorizontalAngle();
            EnumFacing expected = playerFacing.getOpposite();
            for (EnumFacing clickedFace : EnumFacing.values()) {
                IBlockState placed = block.getStateForPlacement(player.world,
                        pos, clickedFace, 0.5F, 0.5F, 0.5F, 0, player);
                require(placed.getValue(BlockAnimatedScreenSelector.FACING)
                                == expected,
                        "view screen orientation followed clicked face instead of player: "
                                + playerFacing + " / " + clickedFace);
            }
        }

        IBlockState neighbor = block.getDefaultState().withProperty(
                BlockAnimatedScreenSelector.FACING, EnumFacing.WEST);
        player.world.setBlockState(pos.down(), neighbor, 2);
        player.rotationYaw = EnumFacing.NORTH.getHorizontalAngle();
        IBlockState extended = block.getStateForPlacement(player.world, pos,
                EnumFacing.UP, 0.5F, 0.5F, 0.5F, 0, player);
        require(extended.getValue(BlockAnimatedScreenSelector.FACING)
                        == EnumFacing.WEST,
                "view screen did not inherit the orientation of an extended panel");
        player.world.setBlockToAir(pos.down());
    }

    private static void checkDiagonalPlacement(EntityPlayer player) {
        require(ModBlocks.PROGRAMMABLE_DIAGONAL_SCREEN
                        instanceof BlockProgrammableDiagonalScreen,
                "programmable diagonal screen block missing");
        BlockProgrammableDiagonalScreen block =
                (BlockProgrammableDiagonalScreen) ModBlocks.PROGRAMMABLE_DIAGONAL_SCREEN;
        for (EnumFacing horizontal : EnumFacing.HORIZONTALS) {
            IBlockState lower = block.getDefaultState()
                    .withProperty(BlockProgrammableDiagonalScreen.FACING, horizontal)
                    .withProperty(BlockProgrammableDiagonalScreen.INVERTED, false);
            IBlockState upper = lower.withProperty(
                    BlockProgrammableDiagonalScreen.INVERTED, true);
            require(block.getStateFromMeta(block.getMetaFromState(lower)).equals(lower)
                            && block.getStateFromMeta(block.getMetaFromState(upper)).equals(upper),
                    "diagonal screen metadata failed for " + horizontal);

            player.rotationYaw = horizontal.getOpposite().getHorizontalAngle();
            require(player.getHorizontalFacing().getOpposite() == horizontal,
                    "test player facing setup failed for " + horizontal);
            require(!placed(block, player, EnumFacing.NORTH, 0.25F)
                            .getValue(BlockProgrammableDiagonalScreen.INVERTED),
                    "side lower-half click did not place an upward wedge");
            require(placed(block, player, EnumFacing.NORTH, 0.75F)
                            .getValue(BlockProgrammableDiagonalScreen.INVERTED),
                    "side upper-half click did not place a downward wedge");
            require(!placed(block, player, EnumFacing.UP, 0.75F)
                            .getValue(BlockProgrammableDiagonalScreen.INVERTED),
                    "top-face click did not place an upward wedge");
            require(placed(block, player, EnumFacing.DOWN, 0.25F)
                            .getValue(BlockProgrammableDiagonalScreen.INVERTED),
                    "underside click did not place a downward wedge");
            require(placed(block, player, EnumFacing.DOWN, 0.25F)
                            .getValue(BlockProgrammableDiagonalScreen.FACING) == horizontal,
                    "underside click ignored player-facing direction");
        }
    }

    private static IBlockState placed(BlockProgrammableDiagonalScreen block,
            EntityPlayer player, EnumFacing side, float hitY) {
        return block.getStateForPlacement(player.world, BlockPos.ORIGIN, side,
                0.5F, hitY, 0.5F, 0, player);
    }

    private static void checkInputPlacement(EntityPlayer player) {
        require(ModBlocks.PROGRAMMABLE_INPUT instanceof BlockProgrammableInput,
                "programmable input block missing");
        BlockProgrammableInput block =
                (BlockProgrammableInput) ModBlocks.PROGRAMMABLE_INPUT;
        for (EnumFacing horizontal : EnumFacing.HORIZONTALS) {
            for (boolean keyboard : new boolean[] {false, true}) {
                for (boolean upper : new boolean[] {false, true}) {
                    IBlockState state = block.getDefaultState()
                            .withProperty(BlockProgrammableInput.FACING, horizontal)
                            .withProperty(BlockProgrammableInput.KEYBOARD, keyboard)
                            .withProperty(BlockProgrammableInput.UPPER, upper);
                    require(block.getStateFromMeta(block.getMetaFromState(state)).equals(state),
                            "programmable input metadata collision for " + state);
                }
            }
        }
        player.setSneaking(false);
        IBlockState lower = placed(block, player, EnumFacing.NORTH, 0.25F);
        IBlockState upper = placed(block, player, EnumFacing.NORTH, 0.75F);
        require(BlockProgrammableInput.wallPositionForHit(0.2F) == 0
                        && BlockProgrammableInput.wallPositionForHit(0.5F) == 1
                        && BlockProgrammableInput.wallPositionForHit(0.8F) == 2,
                "wall clicks were not divided into bottom/middle/top thirds");
        require(!lower.getValue(BlockProgrammableInput.KEYBOARD)
                        && !lower.getValue(BlockProgrammableInput.UPPER)
                        && lower.getValue(BlockProgrammableInput.FACING) == EnumFacing.NORTH,
                "normal lower wall click did not make a lower vertical panel");
        require(!upper.getValue(BlockProgrammableInput.KEYBOARD)
                        && upper.getValue(BlockProgrammableInput.UPPER),
                "normal upper wall click did not make an upper vertical panel");
        player.setSneaking(true);
        IBlockState shelf = placed(block, player, EnumFacing.NORTH, 0.25F);
        player.setSneaking(false);
        require(shelf.getValue(BlockProgrammableInput.KEYBOARD)
                        && !shelf.getValue(BlockProgrammableInput.UPPER)
                        && shelf.getValue(BlockProgrammableInput.FACING) == EnumFacing.NORTH,
                "sneak/lower wall click did not make a mid-height keyboard shelf");
        AxisAlignedBB wallBox = block.getBoundingBox(lower, player.world, BlockPos.ORIGIN);
        AxisAlignedBB shelfBox = block.getBoundingBox(shelf, player.world, BlockPos.ORIGIN);
        require(close(wallBox.maxY - wallBox.minY, 0.5D)
                        && close(wallBox.maxZ - wallBox.minZ, 1.0D / 16.0D),
                "vertical input collision is not half-height and one pixel deep");
        require(close(shelfBox.maxY - shelfBox.minY, 1.0D / 16.0D)
                        && close(shelfBox.maxZ - shelfBox.minZ, 0.5D),
                "keyboard collision is not one pixel high and half-depth");
        BlockPos testPos = new BlockPos(0, 250, 0);
        player.world.setBlockState(testPos, lower, 2);
        TileEntityAnimatedScreenSelector testTile =
                (TileEntityAnimatedScreenSelector) player.world.getTileEntity(testPos);
        testTile.setWallPosition(1);
        AxisAlignedBB middleBox = block.getBoundingBox(lower, player.world, testPos);
        require(close(middleBox.minY, 0.25D) && close(middleBox.maxY, 0.75D),
                "middle wall input collision is not vertically centered");
        testTile.setSmallInput(true);
        AxisAlignedBB smallMiddle = block.getBoundingBox(lower, player.world, testPos);
        require(close(smallMiddle.minX, 0.15D) && close(smallMiddle.maxX, 0.85D)
                        && close(smallMiddle.minY, 0.325D)
                        && close(smallMiddle.maxY, 0.675D),
                "small middle wall input is not 70% and centered");
        testTile.setWallPosition(0);
        AxisAlignedBB smallBottom = block.getBoundingBox(lower, player.world, testPos);
        testTile.setWallPosition(2);
        AxisAlignedBB smallTop = block.getBoundingBox(lower, player.world, testPos);
        require(close(smallBottom.minY, 0.0D) && close(smallBottom.maxY, 0.35D)
                        && close(smallTop.minY, 0.65D) && close(smallTop.maxY, 1.0D),
                "small wall input does not preserve bottom/top anchors");
        player.world.setBlockState(testPos, shelf, 2);
        AxisAlignedBB smallShelf = block.getBoundingBox(shelf, player.world, testPos);
        require(close(smallShelf.maxX - smallShelf.minX, 0.7D)
                        && close(smallShelf.maxZ - smallShelf.minZ, 0.35D)
                        && close(smallShelf.maxZ, 1.0D),
                "small keyboard is not 70% wide/deep and attached to wall");
        player.world.setBlockToAir(testPos);
        require(placed(block, player, EnumFacing.DOWN, 0.25F)
                        .getValue(BlockProgrammableInput.UPPER),
                "underside click did not select upper horizontal placement");
    }

    private static IBlockState placed(BlockProgrammableInput block,
            EntityPlayer player, EnumFacing side, float hitY) {
        return block.getStateForPlacement(player.world, BlockPos.ORIGIN, side,
                0.5F, hitY, 0.5F, 0, player);
    }

    private static void checkFullInputPlacement(EntityPlayer player) {
        require(ModBlocks.PROGRAMMABLE_FULL_INPUT instanceof BlockProgrammableFullInput,
                "full programmable input block missing");
        BlockProgrammableFullInput block =
                (BlockProgrammableFullInput) ModBlocks.PROGRAMMABLE_FULL_INPUT;
        player.setSneaking(false);
        IBlockState wall = placed(block, player, EnumFacing.NORTH, 0.25F);
        player.setSneaking(true);
        IBlockState floor = placed(block, player, EnumFacing.NORTH, 0.25F);
        player.setSneaking(false);
        AxisAlignedBB wallBox = block.getBoundingBox(wall, player.world, BlockPos.ORIGIN);
        AxisAlignedBB floorBox = block.getBoundingBox(floor, player.world, BlockPos.ORIGIN);
        require(close(wallBox.maxY - wallBox.minY, 1.0D)
                        && close(wallBox.maxZ - wallBox.minZ, 1.0D / 16.0D),
                "full input wall collision is not full-height and one pixel deep");
        require(close(floorBox.maxY - floorBox.minY, 1.0D / 16.0D)
                        && close(floorBox.maxZ - floorBox.minZ, 1.0D),
                "full input keyboard collision is not one pixel high and full-depth");
    }

    private static boolean close(double a, double b) {
        return Math.abs(a - b) < 0.000001D;
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException("screen runtime check: " + message);
        }
    }
}
