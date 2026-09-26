package com.vandorlabs.client;

import com.vandorlabs.blocks.BlockAnimatedScreenSelector;
import com.vandorlabs.blocks.ModBlocks;
import com.vandorlabs.blocks.BlockProgrammableLight;
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
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.util.NonNullList;
import com.vandorlabs.blocks.BlockProgrammableDiagonalScreen;
import com.vandorlabs.blocks.BlockProgrammableInput;
import com.vandorlabs.blocks.BlockProgrammableFullInput;
import com.vandorlabs.blocks.BlockProgrammableWall;

/** Serialization and placement contracts for programmable displays. */
final class ScreenRuntimeChecks {

    private ScreenRuntimeChecks() {}

    static void run(EntityPlayer player) {
        checkTilePersistence();
        checkProgrammableLight(player);
        checkProgrammableLightJoin(player);
        checkProgrammableSlab(player);
        require(((TileEntityAnimatedScreenSelector) ModBlocks.PROGRAMMABLE_PORTHOLE_WALL
                        .createTileEntity(player.world,
                                ModBlocks.PROGRAMMABLE_PORTHOLE_WALL.getDefaultState()))
                        .isJoinPortholes(),
                "new programmable portholes should join by default");
        checkHousingSprites();
        checkHousingCycling();
        checkSurvivalDropRoundTrip(player);
        checkPacketRoundTrip();
        checkFacings(ModBlocks.ANIMATED_SCREEN_SELECTOR);
        checkViewscreenPlacement(player);
        checkDiagonalPlacement(player);
        checkProgrammableWalls(player);
        checkPortholeBlock(player);
        checkInputPlacement(player);
        checkInputScrollbar();
        checkFullInputPlacement(player);
        DuplifierRuntimeChecks.run(player);
        System.out.println("[vandorlabs][reprolab] screen-runtime PASS");
    }

    private static void checkProgrammableLight(EntityPlayer player) {
        require(ModBlocks.PROGRAMMABLE_LIGHT != null,
                "programmable light is not registered");
        BlockPos placement = new BlockPos(34, 250, 32);
        try {
            for (EnumFacing side : EnumFacing.values()) {
                BlockPos support = placement.offset(side.getOpposite());
                player.world.setBlockState(support, net.minecraft.init.Blocks.STONE.getDefaultState(), 3);
                IBlockState placed = ModBlocks.PROGRAMMABLE_LIGHT.getStateForPlacement(
                        player.world, placement, side, .5F, .5F, .5F, 0, player);
                require(placed.getValue(BlockAnimatedScreenSelector.FACING) == side,
                        "programmable light did not face clicked surface: " + side);
                player.world.setBlockState(support,
                        ModBlocks.PROGRAMMABLE_LIGHT.getDefaultState().withProperty(
                                BlockAnimatedScreenSelector.FACING, EnumFacing.WEST), 3);
                placed = ModBlocks.PROGRAMMABLE_LIGHT.getStateForPlacement(
                        player.world, placement, side, .5F, .5F, .5F, 0, player);
                require(placed.getValue(BlockAnimatedScreenSelector.FACING) == EnumFacing.WEST,
                        "programmable light did not inherit adjacent direction: " + side);
                player.world.setBlockToAir(support);
            }
        } finally {
            for (EnumFacing side : EnumFacing.values())
                player.world.setBlockToAir(placement.offset(side.getOpposite()));
        }
        BlockPos pos = new BlockPos(32, 250, 32);
        player.world.setBlockState(pos, ModBlocks.PROGRAMMABLE_LIGHT.getDefaultState(), 3);
        try {
            com.vandorlabs.tiles.TileEntityProgrammableLight light =
                    (com.vandorlabs.tiles.TileEntityProgrammableLight)
                    player.world.getTileEntity(pos);
            require(light != null, "programmable light tile missing");
            for (int i = 0; i < com.vandorlabs.tiles.ProgrammableLightTextures.IDS.length; i++) {
                for (boolean on : new boolean[]{true, false}) {
                    String name = com.vandorlabs.tiles.ProgrammableLightTextures.texture(i, on);
                    require(name.equals(Minecraft.getMinecraft().getTextureMapBlocks()
                                    .getAtlasSprite(name).getIconName()),
                            "missing programmable light sprite: " + name);
                }
                light.configure(i, i + 1);
                require(light.getTexture() == i && light.getLightLevel() == i + 1
                                && ModBlocks.PROGRAMMABLE_LIGHT.getLightValue(
                                player.world.getBlockState(pos), player.world, pos) == i + 1,
                        "programmable light choice or emission failed: " + i);
            }
            light.setOn(false);
            require(ModBlocks.PROGRAMMABLE_LIGHT.getLightValue(
                            player.world.getBlockState(pos), player.world, pos) == 0,
                    "switched-off programmable light still emits");
            NBTTagCompound saved = light.writeToNBT(new NBTTagCompound());
            com.vandorlabs.tiles.TileEntityProgrammableLight restored =
                    new com.vandorlabs.tiles.TileEntityProgrammableLight();
            restored.readFromNBT(saved);
            require(restored.getTexture() ==
                            com.vandorlabs.tiles.ProgrammableLightTextures.IDS.length - 1
                            && restored.getLightLevel() ==
                            com.vandorlabs.tiles.ProgrammableLightTextures.IDS.length
                            && !restored.isOn(),
                    "programmable light settings did not persist");
        } finally {
            player.world.setBlockToAir(pos);
        }
    }

    private static void checkProgrammableLightJoin(EntityPlayer player) {
        BlockPos origin = new BlockPos(36, 250, 36);
        try {
            for (int dx = 0; dx < 2; dx++)
                for (int dy = 0; dy < 2; dy++) {
                    BlockPos at = origin.add(dx, dy, 0);
                    player.world.setBlockState(at,
                            ModBlocks.PROGRAMMABLE_LIGHT.getDefaultState(), 3);
                    ((com.vandorlabs.tiles.TileEntityProgrammableLight)
                            player.world.getTileEntity(at)).configure(2, 12, true, 0);
                }
            com.vandorlabs.tiles.TileEntityProgrammableLight first =
                    (com.vandorlabs.tiles.TileEntityProgrammableLight)
                    player.world.getTileEntity(origin);
            TEAnimatedScreenSelector.LightGroup group =
                    TEAnimatedScreenSelector.lightGroup(first,
                            player.world.getBlockState(origin));
            require(group.columns == 2 && group.rows == 2
                            && group.left(origin) == 0 && group.right(origin) == 8
                            && group.top(origin) == 8 && group.bottom(origin) == 16,
                    "joined light artwork did not span the 2 by 2 group");
            BlockPos east = origin.east();
            require(group.right(origin) == group.left(east)
                            && group.top(origin) == group.top(east),
                    "joined light artwork has a vertical seam");
            BlockPos above = origin.up();
            require(group.top(origin) == group.bottom(above)
                            && group.left(origin) == group.left(above),
                    "joined light artwork has a horizontal seam");
            BlockProgrammableLight lightBlock =
                    (BlockProgrammableLight) ModBlocks.PROGRAMMABLE_LIGHT;
            lightBlock.onBlockActivated(player.world, origin,
                    player.world.getBlockState(origin), player, EnumHand.MAIN_HAND,
                    EnumFacing.NORTH, .5F, .5F, .5F);
            for (int dx = 0; dx < 2; dx++)
                for (int dy = 0; dy < 2; dy++)
                    require(!((com.vandorlabs.tiles.TileEntityProgrammableLight)
                                    player.world.getTileEntity(origin.add(dx, dy, 0))).isOn(),
                            "right click did not switch the whole joined light group off");
            lightBlock.onBlockActivated(player.world, origin,
                    player.world.getBlockState(origin), player, EnumHand.MAIN_HAND,
                    EnumFacing.NORTH, .5F, .5F, .5F);
            for (int dx = 0; dx < 2; dx++)
                for (int dy = 0; dy < 2; dy++)
                    require(((com.vandorlabs.tiles.TileEntityProgrammableLight)
                                    player.world.getTileEntity(origin.add(dx, dy, 0))).isOn(),
                            "right click did not switch the whole joined light group on");
            first.configure(2, 12, false, 0);
            group = TEAnimatedScreenSelector.lightGroup(first,
                    player.world.getBlockState(origin));
            require(group.columns == 1 && group.rows == 1,
                    "disabling join did not separate the light");
            lightBlock.onBlockActivated(player.world, origin,
                    player.world.getBlockState(origin), player, EnumHand.MAIN_HAND,
                    EnumFacing.NORTH, .5F, .5F, .5F);
            require(!first.isOn() && ((com.vandorlabs.tiles.TileEntityProgrammableLight)
                            player.world.getTileEntity(east)).isOn(),
                    "Join Off right click switched a neighboring light");
            first.setOn(true);
            first.configure(3, 7, true, 4271, first.getHousingTexture(), 1);
            require(!first.isOn() && ModBlocks.PROGRAMMABLE_LIGHT.getLightValue(
                            player.world.getBlockState(origin), player.world, origin) == 0,
                    "unpowered channel light remained on");
            first.setChannelSignal(true);
            require(first.isOn() && ModBlocks.PROGRAMMABLE_LIGHT.getLightValue(
                            player.world.getBlockState(origin), player.world, origin) == 7,
                    "channel signal did not apply configured light level");
            com.vandorlabs.tiles.TileEntityProgrammableLight manualNeighbor =
                    (com.vandorlabs.tiles.TileEntityProgrammableLight)player.world.getTileEntity(east);
            manualNeighbor.configure(3, 7, true, 0);
            lightBlock.onBlockActivated(player.world, origin,
                    player.world.getBlockState(origin), player, EnumHand.MAIN_HAND,
                    EnumFacing.NORTH, .5F, .5F, .5F);
            require(first.isOn() && manualNeighbor.isOn(),
                    "clicking a channel-controlled light toggled a manual neighbor");
            ItemStack picked = ((BlockAnimatedScreenSelector) ModBlocks.PROGRAMMABLE_LIGHT)
                    .getPickBlock(player.world.getBlockState(origin), null,
                            player.world, origin, player);
            NBTTagCompound saved = picked.getSubCompound("BlockEntityTag");
            require(saved != null && saved.getInteger("LightTexture") == 3
                            && saved.getInteger("LightLevel") == 7
                            && saved.getBoolean("LightJoin")
                            && saved.getInteger("RedstoneChannel") == 4271
                            && saved.getInteger("LightTrigger") == 1,
                    "creative pick lost programmable light configuration");
            String pickedSprite = com.vandorlabs.tiles.ProgrammableLightTextures.texture(3,
                    first.isOn() && first.getLightLevel() > 0);
            require(pickedSprite.equals(Minecraft.getMinecraft().getRenderItem()
                            .getItemModelMesher().getItemModel(picked)
                            .getParticleTexture().getIconName()),
                    "picked programmable light hotbar artwork does not match configuration");
            BlockPos copyPos = origin.add(3, 0, 0);
            require(((ItemBlock) picked.getItem()).placeBlockAt(picked.copy(), player,
                            player.world, copyPos, EnumFacing.UP, .5F, .5F, .5F,
                            ModBlocks.PROGRAMMABLE_LIGHT.getDefaultState()),
                    "picked programmable light did not place");
            com.vandorlabs.tiles.TileEntityProgrammableLight copy =
                    (com.vandorlabs.tiles.TileEntityProgrammableLight)
                    player.world.getTileEntity(copyPos);
            require(copy != null && copy.getTexture() == 3 && copy.getLightLevel() == 7
                            && copy.isJoin() && copy.getRedstoneChannel() == 4271
                            && copy.getTrigger() == 1,
                    "placed programmable light did not restore its configuration");
            for (EnumFacing facing : new EnumFacing[]{EnumFacing.UP, EnumFacing.DOWN}) {
                BlockPos plane = origin.add(0, 0, 4);
                for (int dx = 0; dx < 2; dx++)
                    for (int dz = 0; dz < 2; dz++) {
                        BlockPos at = plane.add(dx, 0, dz);
                        player.world.setBlockState(at,
                                ModBlocks.PROGRAMMABLE_LIGHT.getDefaultState().withProperty(
                                        BlockAnimatedScreenSelector.FACING, facing), 3);
                        ((com.vandorlabs.tiles.TileEntityProgrammableLight)
                                player.world.getTileEntity(at)).configure(2, 12, true, 0);
                    }
                com.vandorlabs.tiles.TileEntityProgrammableLight tile =
                        (com.vandorlabs.tiles.TileEntityProgrammableLight)
                        player.world.getTileEntity(plane);
                TEAnimatedScreenSelector.LightGroup planeGroup =
                        TEAnimatedScreenSelector.lightGroup(tile,
                                player.world.getBlockState(plane));
                require(planeGroup.columns == 2 && planeGroup.rows == 2
                                && planeGroup.right(plane) == planeGroup.left(plane.east()),
                        "joined " + facing + " lights did not span the floor plane");
                BlockPos lowerRow = facing == EnumFacing.UP ? plane : plane.south();
                BlockPos rowNeighbor = lowerRow.offset(planeGroup.up);
                require(planeGroup.top(lowerRow) == planeGroup.bottom(rowNeighbor),
                        "joined " + facing + " lights have a row seam");
                for (int dx = 0; dx < 2; dx++)
                    for (int dz = 0; dz < 2; dz++)
                        player.world.setBlockToAir(plane.add(dx, 0, dz));
            }
        } finally {
            for (int dx = 0; dx < 5; dx++)
                for (int dy = 0; dy < 2; dy++)
                    player.world.setBlockToAir(origin.add(dx, dy, 0));
            for (int dx = 0; dx < 2; dx++)
                for (int dz = 0; dz < 2; dz++)
                    player.world.setBlockToAir(origin.add(dx, 0, dz + 4));
        }
    }

    private static void checkRoundPortholePixels() {
        for (int[] size : new int[][]{{1,1},{2,2},{3,3},{1,2},{2,1}}) {
            PortholeHex round = new PortholeHex(size[0], size[1], PortholeHex.ROUND);
            double cx = size[0] * 8D, cy = size[1] * 8D;
            double rx = cx - 2, ry = cy - 4;
            for (int col = 0; col < size[0]; col++) for (int row = 0; row < size[1]; row++) {
                PortholeHex.Slice slice = round.slice(col, row);
                java.util.List<double[]> all = new java.util.ArrayList<>(slice.frameQuads);
                all.addAll(slice.glassQuads);
                for (double[] quad : all) {
                    for (double coordinate : quad)
                        require(coordinate == Math.rint(coordinate), "round edge is between world pixels");
                    require(quad[1] == quad[3] && quad[5] == quad[7]
                                    && quad[0] == quad[6] && quad[2] == quad[4],
                            "round frame or glass has a diagonal edge");
                }
                for (int x = 0; x < 16; x++) for (int y = 0; y < 16; y++) {
                    int cover = 0, glass = 0;
                    for (double[] quad : all)
                        if (x + .5 > quad[0] && x + .5 < quad[2]
                                && y + .5 > quad[1] && y + .5 < quad[5]) cover++;
                    for (double[] quad : slice.glassQuads)
                        if (x + .5 > quad[0] && x + .5 < quad[2]
                                && y + .5 > quad[1] && y + .5 < quad[5]) glass++;
                    double dx = (col * 16 + x + .5 - cx) / rx;
                    double dy = (row * 16 + y + .5 - cy) / ry;
                    require(cover == 1 && glass == (dx * dx + dy * dy <= 1 ? 1 : 0),
                            "round glass and frame disagree at a pixel or joined seam");
                }
            }
        }
    }

    private static void checkProgrammableSlab(EntityPlayer player) {
        com.vandorlabs.blocks.BlockProgrammableSlab slab =
                (com.vandorlabs.blocks.BlockProgrammableSlab) ModBlocks.PROGRAMMABLE_SLAB;
        BlockPos pos = new BlockPos(41, 250, 41);
        IBlockState bottom = slab.getStateForPlacement(player.world, pos,
                EnumFacing.UP, .5F, .9F, .5F, 0, player);
        IBlockState top = slab.getStateForPlacement(player.world, pos,
                EnumFacing.DOWN, .5F, .1F, .5F, 0, player);
        require(bottom.getValue(com.vandorlabs.blocks.BlockProgrammableSlab.HALF)
                        == net.minecraft.block.BlockSlab.EnumBlockHalf.BOTTOM
                        && top.getValue(com.vandorlabs.blocks.BlockProgrammableSlab.HALF)
                        == net.minecraft.block.BlockSlab.EnumBlockHalf.TOP,
                "programmable slab did not follow clicked face");
        IBlockState sideTop = slab.getStateForPlacement(player.world, pos,
                EnumFacing.NORTH, .5F, .75F, .5F, 0, player);
        IBlockState sideBottom = slab.getStateForPlacement(player.world, pos,
                EnumFacing.NORTH, .5F, .25F, .5F, 0, player);
        require(sideTop.getValue(com.vandorlabs.blocks.BlockProgrammableSlab.HALF)
                        == net.minecraft.block.BlockSlab.EnumBlockHalf.TOP
                        && sideBottom.getValue(com.vandorlabs.blocks.BlockProgrammableSlab.HALF)
                        == net.minecraft.block.BlockSlab.EnumBlockHalf.BOTTOM,
                "programmable slab did not follow side hit height");
        require(slab.getBoundingBox(bottom, player.world, pos).maxY == .5
                        && slab.getBoundingBox(top, player.world, pos).minY == .5
                        && slab.getStateFromMeta(slab.getMetaFromState(top))
                        .getValue(com.vandorlabs.blocks.BlockProgrammableSlab.HALF)
                        == net.minecraft.block.BlockSlab.EnumBlockHalf.TOP,
                "programmable slab bounds or metadata failed");
        player.world.setBlockState(pos, top, 3);
        try {
            TileEntityAnimatedScreenSelector tile =
                    (TileEntityAnimatedScreenSelector) player.world.getTileEntity(pos);
            tile.setHousingTexture(ScreenHousingTextures.IDS.length - 1);
            ItemStack picked = slab.getPickBlock(top, null, player.world, pos, player);
            String pickedSprite = ScreenHousingTextures.texture(
                    ScreenHousingTextures.IDS.length - 1);
            require(pickedSprite.equals(Minecraft.getMinecraft().getRenderItem()
                            .getItemModelMesher().getItemModel(picked)
                            .getParticleTexture().getIconName()),
                    "picked programmable slab hotbar artwork does not match configuration");
            tile.setSlabTileSides(true);
            ItemStack tiled = slab.getPickBlock(top, null, player.world, pos, player);
            require(tiled.getSubCompound("BlockEntityTag").getBoolean("SlabTileSides")
                            && Minecraft.getMinecraft().getRenderItem()
                            .getItemModelMesher().getItemModel(picked)
                            != Minecraft.getMinecraft().getRenderItem()
                            .getItemModelMesher().getItemModel(tiled),
                    "picked programmable slab lost Tile side texture mode");
            BlockPos copyPos = pos.east();
            require(((ItemBlock) tiled.getItem()).placeBlockAt(tiled.copy(), player,
                            player.world, copyPos, EnumFacing.UP, .5F, .5F, .5F, top),
                    "picked programmable slab did not place");
            TileEntityAnimatedScreenSelector copy =
                    (TileEntityAnimatedScreenSelector) player.world.getTileEntity(copyPos);
            require(copy != null && copy.getHousingTexture()
                            == ScreenHousingTextures.IDS.length - 1
                            && copy.isSlabTileSides()
                            && player.world.getBlockState(copyPos).getValue(
                            com.vandorlabs.blocks.BlockProgrammableSlab.HALF)
                            == net.minecraft.block.BlockSlab.EnumBlockHalf.TOP,
                    "picked programmable slab lost its texture or top placement");
            player.world.setBlockToAir(copyPos);
        } finally {
            player.world.setBlockToAir(pos);
        }
    }

    private static void checkInputScrollbar() {
        require(GuiProgrammableInput.scrollForDrag(105, 100, 100, 20, 10, 5) == 0
                        && GuiProgrammableInput.scrollForDrag(145, 100, 100, 20, 10, 5) == 5
                        && GuiProgrammableInput.scrollForDrag(185, 100, 100, 20, 10, 5) == 10,
                "half-input scrollbar drag does not cover its full range");
        HousingTextureList finishes = new HousingTextureList(0, 0, 120, 0);
        finishes.wheel(10, 10, -1);
        finishes.click(10, 2, 0);
        require(finishes.selected() == 1, "housing finish list did not scroll and select");
        require(GuiProgrammableWall.scrollForDrag(105, 100, 100, 20, 4, 5) == 0
                        && GuiProgrammableWall.scrollForDrag(145, 100, 100, 20, 4, 5) == 2
                        && GuiProgrammableWall.scrollForDrag(185, 100, 100, 20, 4, 5) == 4,
                "wall texture scrollbar drag does not cover its full range");
    }

    private static void checkHousingSprites() {
        require(!java.util.Arrays.asList(ScreenHousingTextures.IDS).contains("thruster_side"),
                "Engine Side remains in the side texture list");
        String side = "vandorlabs:blocks/programmable_glass/metal_side";
        require(side.equals(Minecraft.getMinecraft().getTextureMapBlocks()
                        .getAtlasSprite(side).getIconName()),
                "missing programmable wall side sprite: " + side);
        for (int i = 0; i < ScreenHousingTextures.IDS.length; i++) {
            String name = ScreenHousingTextures.texture(i);
            require(name.equals(Minecraft.getMinecraft().getTextureMapBlocks()
                            .getAtlasSprite(name).getIconName()),
                    "missing housing sprite: " + name);
        }
    }

    private static void checkHousingCycling() {
        int last = ScreenHousingTextures.IDS.length - 1;
        require(ScreenHousingTextures.cycle(0, -1) == last
                        && ScreenHousingTextures.cycle(last, 1) == 0
                        && ScreenHousingTextures.cycle(4, -1) == 3
                        && ScreenHousingTextures.cycle(4, 1) == 5,
                "housing texture cycling does not move in both directions");
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
            source.setGlassShade(1);
            source.setJoinPortholes(true);
            source.setPortholeShape(PortholeHex.ROUND);
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
                            && restored.getHousingTexture() == 8
                            && restored.getGlassShade() == 1
                            && restored.isJoinPortholes()
                            && restored.getPortholeShape() == PortholeHex.ROUND,
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
                ModBlocks.PROGRAMMABLE_FULL_INPUT,
                ModBlocks.PROGRAMMABLE_WALL,
                ModBlocks.PROGRAMMABLE_BLOCK,
                ModBlocks.PROGRAMMABLE_PORTHOLE_WALL,
                ModBlocks.PROGRAMMABLE_DIAGONAL_WALL
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
        tile.setGlassShade(1);
        tile.setJoinPortholes(true);
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
                        && tile.getHousingTexture() == 8
                        && tile.getGlassShade() == 1
                        && tile.isJoinPortholes(),
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

    private static void checkPortholeBlock(EntityPlayer player) {
        BlockProgrammableWall block = (BlockProgrammableWall)
                ModBlocks.PROGRAMMABLE_PORTHOLE_BLOCK;
        BlockPos pos = new BlockPos(36, 250, 0);
        player.rotationYaw = EnumFacing.SOUTH.getHorizontalAngle();
        for (float hit : new float[] {.1F, .5F, .9F}) {
            IBlockState placed = block.getStateForPlacement(player.world, pos,
                    EnumFacing.UP, .5F, .5F, hit, 0, player);
            require(placed.getValue(BlockProgrammableWall.FACING) == EnumFacing.NORTH
                            && placed.getValue(BlockProgrammableWall.DEPTH) == 0,
                    "porthole block placement changed depth");
            require(block.getBoundingBox(placed, player.world, pos)
                            .equals(new AxisAlignedBB(0, 0, 0, 1, 1, 1)),
                    "porthole block collision is not full depth");
        }
        IBlockState state = block.getDefaultState();
        player.world.setBlockState(pos, state, 2);
        player.world.setBlockState(pos.east(), state, 2);
        TileEntityAnimatedScreenSelector tile =
                (TileEntityAnimatedScreenSelector) player.world.getTileEntity(pos);
        require(tile.isJoinPortholes(), "porthole block should join by default");
        for (int shape : new int[] {PortholeHex.HEXAGON, PortholeHex.OCTAGON,
                PortholeHex.ROUND}) {
            tile.setPortholeShape(shape);
            TEAnimatedScreenSelector.PortholeGroup group =
                    TEAnimatedScreenSelector.portholeGroup(tile, state);
            for (double[] vertex : group.hex.vertices)
                require(vertex[0] >= 3 && vertex[0] <= group.columns * 16 - 3
                                && vertex[1] >= 5 && vertex[1] <= group.rows * 16 - 5,
                        "porthole block opening extends into its border");
        }
        tile.setPortholeShape(PortholeHex.HEXAGON);
        require(TEAnimatedScreenSelector.portholeGroup(tile, state).columns == 2,
                "adjacent porthole blocks did not join");
        player.world.setBlockState(pos.east(),
                ModBlocks.PROGRAMMABLE_PORTHOLE_WALL.getDefaultState(), 2);
        require(!TEAnimatedScreenSelector.joinsPorthole(tile, state, true),
                "porthole block joined a thin wall");
        player.world.setBlockToAir(pos);
        player.world.setBlockToAir(pos.east());
    }

    private static void checkProgrammableWalls(EntityPlayer player) {
        Block[] variants = {ModBlocks.PROGRAMMABLE_WALL,
                ModBlocks.PROGRAMMABLE_PORTHOLE_WALL,
                ModBlocks.PROGRAMMABLE_DIAGONAL_WALL};
        BlockPos pos = new BlockPos(30, 250, 0);
        for (Block raw : variants) {
            require(raw instanceof BlockProgrammableWall, "programmable wall missing");
            BlockProgrammableWall block = (BlockProgrammableWall) raw;
            for (EnumFacing facing : EnumFacing.HORIZONTALS) {
                IBlockState state = block.getDefaultState()
                        .withProperty(BlockProgrammableWall.FACING, facing);
                if (block.getShape() == BlockProgrammableWall.Shape.DIAGONAL)
                    state = state.withProperty(BlockProgrammableWall.INVERTED, true);
                require(block.getStateFromMeta(block.getMetaFromState(state)).equals(state),
                        "wall facing/half metadata failed: " + block.getRegistryName());
                if (block.getShape() != BlockProgrammableWall.Shape.DIAGONAL)
                    for (int depth = 0; depth < 3; depth++) {
                        IBlockState shifted = state.withProperty(BlockProgrammableWall.DEPTH, depth);
                        require(block.getStateFromMeta(block.getMetaFromState(shifted))
                                        .equals(shifted),
                                "wall depth metadata failed: " + block.getRegistryName());
                        double expected = com.vandorlabs.blocks.PanelDepth.start(depth) / 16D;
                        require(block.getBoundingBox(shifted, player.world, pos).minZ == expected
                                        || facing != EnumFacing.NORTH,
                                "wall collision does not match depth");
                    }
            }
        }
        BlockProgrammableWall diagonal = (BlockProgrammableWall) variants[2];
        player.rotationYaw = EnumFacing.NORTH.getHorizontalAngle();
        IBlockState floor = diagonal.getStateForPlacement(player.world, pos,
                EnumFacing.UP, .5F, .2F, .5F, 0, player);
        IBlockState ceiling = diagonal.getStateForPlacement(player.world, pos,
                EnumFacing.DOWN, .5F, .8F, .5F, 0, player);
        require(!floor.getValue(BlockProgrammableWall.INVERTED)
                        && ceiling.getValue(BlockProgrammableWall.INVERTED),
                "diagonal wall did not follow stair half placement");
        for (int i = 0; i < 2; i++) {
            BlockProgrammableWall wall = (BlockProgrammableWall) variants[i];
            player.rotationYaw = EnumFacing.SOUTH.getHorizontalAngle();
            for (float hit : new float[] {.1F, .5F, .9F}) {
                IBlockState placed = wall.getStateForPlacement(player.world, pos,
                        EnumFacing.UP, .5F, .5F, hit, 0, player);
                int expected = hit < .2F ? 1 : hit > .8F ? 2 : 0;
                require(placed.getValue(BlockProgrammableWall.FACING) == EnumFacing.NORTH
                                && placed.getValue(BlockProgrammableWall.DEPTH) == expected,
                        "wall click did not select its depth");
            }
            AxisAlignedBB bounds = wall.getBoundingBox(wall.getDefaultState(),
                    player.world, pos);
            require(bounds.minX == 0 && bounds.maxX == 1
                            && bounds.minZ == 6 / 16D && bounds.maxZ == 10 / 16D,
                    "wall is not a full-width four-pixel panel: " + wall.getRegistryName());
        }
        BlockProgrammableWall porthole = (BlockProgrammableWall) variants[1];
        IBlockState north = porthole.getDefaultState()
                .withProperty(BlockProgrammableWall.FACING, EnumFacing.NORTH);
        player.world.setBlockState(pos, north, 2);
        player.world.setBlockState(pos.east(), north, 2);
        TileEntityAnimatedScreenSelector first = (TileEntityAnimatedScreenSelector)
                player.world.getTileEntity(pos);
        TileEntityAnimatedScreenSelector second = (TileEntityAnimatedScreenSelector)
                player.world.getTileEntity(pos.east());
        first.setJoinPortholes(true);
        second.setJoinPortholes(true);
        player.world.setBlockState(pos.east(), north.withProperty(
                BlockProgrammableWall.DEPTH, 1), 2);
        require(!TEAnimatedScreenSelector.joinsPorthole(first, north, true),
                "porthole joined panels at different depths");
        player.world.setBlockState(pos.east(), north, 2);
        second = (TileEntityAnimatedScreenSelector) player.world.getTileEntity(pos.east());
        second.setJoinPortholes(true);
        player.world.setBlockState(pos.up(), north, 2);
        player.world.setBlockState(pos.east().up(), north, 2);
        TileEntityAnimatedScreenSelector above = (TileEntityAnimatedScreenSelector)
                player.world.getTileEntity(pos.up());
        TileEntityAnimatedScreenSelector aboveRight = (TileEntityAnimatedScreenSelector)
                player.world.getTileEntity(pos.east().up());
        above.setJoinPortholes(true);
        aboveRight.setJoinPortholes(true);
        TEAnimatedScreenSelector.PortholeGroup group =
                TEAnimatedScreenSelector.portholeGroup(first, north);
        require(group.columns == 2 && group.rows == 2
                        && group.slice(pos).edgeOpening(0, 16) != null
                        && group.slice(pos).edgeOpening(1, 16) != null,
                "joined portholes do not make one hexagon across both seams");
        player.world.setBlockToAir(pos.east().up());
        first.setJoinPortholes(false);
        first.setJoinPortholes(true);
        for (TileEntityAnimatedScreenSelector member : new TileEntityAnimatedScreenSelector[] {
                first, second, above}) {
            TEAnimatedScreenSelector.PortholeGroup incomplete =
                    TEAnimatedScreenSelector.portholeGroup(member, north);
            require(incomplete.columns == (member == above ? 1 : 2) && incomplete.rows == 1,
                    "L-shaped portholes must partition into a pair and a single");
        }
        player.world.setBlockState(pos.east().up(), north, 2);
        ((TileEntityAnimatedScreenSelector) player.world.getTileEntity(pos.east().up()))
                .setJoinPortholes(true);
        group = TEAnimatedScreenSelector.portholeGroup(first, north);
        require(group.columns == 2 && group.rows == 2,
                "completing the rectangle must restore its joined hexagon");
        require(TEAnimatedScreenSelector.joinsPorthole(first, north, true)
                        && TEAnimatedScreenSelector.joinsPorthole(second, north, false),
                "north-facing portholes do not join at their shared edge");
        for (int shape = PortholeHex.HEXAGON; shape <= PortholeHex.ROUND; shape++) {
            first.setPortholeShape(shape);
            second.setPortholeShape(shape);
            above.setPortholeShape(shape);
            ((TileEntityAnimatedScreenSelector) player.world.getTileEntity(pos.east().up()))
                    .setPortholeShape(shape);
            group = TEAnimatedScreenSelector.portholeGroup(first, north);
            require(group.columns == 2 && group.rows == 2
                            && (shape == PortholeHex.ROUND
                            || group.hex.vertices.length == new int[] {6, 8, 4}[shape])
                            && group.slice(pos).edgeOpening(0, 16) != null
                            && group.slice(pos).edgeOpening(1, 16) != null,
                    "joined porthole shape is incomplete: " + shape);
            second.setPortholeShape((shape + 1) % 4);
            require(!TEAnimatedScreenSelector.joinsPorthole(first, north, true)
                            && TEAnimatedScreenSelector.portholeGroup(first, north).columns == 1,
                    "portholes joined across different shapes: " + shape);
            second.setPortholeShape(shape);
        }
        second.setJoinPortholes(false);
        require(!TEAnimatedScreenSelector.joinsPorthole(first, north, true),
                "porthole joins a neighbor with its toggle off");
        player.world.setBlockToAir(pos);
        player.world.setBlockToAir(pos.east());
        player.world.setBlockToAir(pos.up());
        player.world.setBlockToAir(pos.east().up());
        checkFlatWallCorners(player, pos.add(8, 0, 0),
                (BlockProgrammableWall) variants[0]);
        checkRoundPortholePixels();
        PortholeHex single = new PortholeHex(1, 1);
        require(single.vertices[0][1] == 4 && single.vertices[2][0] == 14,
                "single porthole hexagon is too large");
        for (int[] size : new int[][]{{2,2},{3,3},{1,2},{2,1}}) {
            PortholeHex joined = new PortholeHex(size[0],size[1]);
            double left=Double.POSITIVE_INFINITY,right=Double.NEGATIVE_INFINITY;
            double bottom=Double.POSITIVE_INFINITY,top=Double.NEGATIVE_INFINITY;
            for (double[] vertex:joined.vertices) {
                left=Math.min(left,vertex[0]); right=Math.max(right,vertex[0]);
                bottom=Math.min(bottom,vertex[1]); top=Math.max(top,vertex[1]);
            }
            require(left==2 && right==size[0]*16-2
                            && bottom==4 && top==size[1]*16-4,
                    "joined porthole margins differ from the single block");
        }
        PortholeHex vertical = new PortholeHex(1, 2);
        require(vertical.slice(0, 0).edgeOpening(1, 16) != null
                        && vertical.slice(0, 1).edgeOpening(1, 0) != null,
                "vertical portholes have a frame across their glass seam");
        java.util.List<AxisAlignedBB> lower = new java.util.ArrayList<>();
        java.util.List<AxisAlignedBB> upper = new java.util.ArrayList<>();
        AxisAlignedBB query = new AxisAlignedBB(pos).grow(2);
        IBlockState northLower = diagonal.getDefaultState()
                .withProperty(BlockProgrammableWall.FACING, EnumFacing.NORTH);
        diagonal.addCollisionBoxToList(northLower, player.world, pos, query,
                lower, null, false);
        diagonal.addCollisionBoxToList(northLower.withProperty(
                        BlockProgrammableWall.INVERTED, true), player.world, pos, query,
                upper, null, false);
        require(lower.size() == 16 && upper.size() == 16
                        && lower.get(0).minZ < lower.get(15).minZ
                        && upper.get(0).minZ > upper.get(15).minZ,
                "diagonal collision does not follow both continuous slopes");
        require(diagonal.getBoundingBox(northLower, player.world, pos).maxZ
                        == 10 / 16D
                        && diagonal.collisionRayTrace(northLower, player.world, pos,
                        new net.minecraft.util.math.Vec3d(pos.getX() + .5,
                                pos.getY() + .9, pos.getZ() - .5),
                        new net.minecraft.util.math.Vec3d(pos.getX() + .5,
                                pos.getY() + .9, pos.getZ() + .2)) == null,
                "diagonal selection still covers empty space in the block");
        for (boolean inverted : new boolean[] {false, true}) {
            for (boolean front : new boolean[] {false, true}) {
                for (EnumFacing turnFacing : new EnumFacing[] {
                        EnumFacing.EAST, EnumFacing.WEST}) {
                    IBlockState turn = northLower.withProperty(
                            BlockProgrammableWall.INVERTED, inverted);
                    BlockPos turnPos = pos.offset(front ? EnumFacing.NORTH
                            : EnumFacing.SOUTH);
                    player.world.setBlockState(pos, turn, 2);
                    player.world.setBlockState(turnPos, turn.withProperty(
                            BlockProgrammableWall.FACING, turnFacing), 2);
                    require(diagonal.corner(turn, player.world, pos) != null,
                            "diagonal walls do not form a corner in each half and direction");
                    java.util.List<AxisAlignedBB> joinedBoxes = new java.util.ArrayList<>();
                    diagonal.addCollisionBoxToList(turn, player.world, pos, query,
                            joinedBoxes, null, false);
                    require(joinedBoxes.size() == 32,
                            "diagonal turn does not add only the connecting arm");
                    AxisAlignedBB shortened = joinedBoxes.get(inverted ? 0 : 30);
                    require(turnFacing == EnumFacing.EAST
                                    ? shortened.minX > pos.getX()
                                    : shortened.maxX < pos.getX() + 1,
                            "diagonal corner leaves a full-width T junction");
                    player.world.setBlockToAir(turnPos);
                    player.world.setBlockToAir(pos);
                }
            }
        }
        for (boolean inverted : new boolean[] {false, true}) {
            IBlockState turn = northLower.withProperty(
                    BlockProgrammableWall.INVERTED, inverted);
            player.world.setBlockState(pos, turn, 2);
            player.world.setBlockState(pos.north(), turn.withProperty(
                    BlockProgrammableWall.FACING, EnumFacing.WEST), 2);
            player.world.setBlockState(pos.south(), turn.withProperty(
                    BlockProgrammableWall.FACING, EnumFacing.EAST), 2);
            BlockProgrammableWall.Corner both = diagonal.corner(turn, player.world, pos);
            java.util.List<AxisAlignedBB> threeBoxes = new java.util.ArrayList<>();
            diagonal.addCollisionBoxToList(turn, player.world, pos, query,
                    threeBoxes, null, false);
            require(both != null && both.frontRight != null
                            && both.backRight != null && threeBoxes.size() == 48,
                    "three diagonal walls do not retain both corner connections");
            player.world.setBlockToAir(pos.north());
            player.world.setBlockToAir(pos.south());
            player.world.setBlockToAir(pos);
        }
        for (boolean inverted : new boolean[] {false, true}) {
            IBlockState turn = northLower.withProperty(
                    BlockProgrammableWall.INVERTED, inverted);
            for (boolean right : new boolean[] {false, true}) {
                BlockPos straightPos = pos.offset(right ? EnumFacing.EAST
                        : EnumFacing.WEST);
                player.world.setBlockState(pos, turn, 2);
                player.world.setBlockState(straightPos, turn, 2);
                player.world.setBlockState(pos.south(), turn.withProperty(
                        BlockProgrammableWall.FACING,
                        right ? EnumFacing.EAST : EnumFacing.WEST), 2);
                BlockProgrammableWall.Corner junction =
                        diagonal.corner(turn, player.world, pos);
                require(junction != null && (right
                                ? junction.left(6) == 6 && junction.right(6) == 16
                                : junction.left(6) == 0 && junction.right(6) == 10),
                        "diagonal corner must run from the straight neighbor only to the bend");
                java.util.List<AxisAlignedBB> unwantedTail = new java.util.ArrayList<>();
                double probeX = right ? .05 : .87;
                double probeY = inverted ? .01 : .94;
                AxisAlignedBB tail = new AxisAlignedBB(probeX, probeY, .45,
                        probeX + .08, probeY + .05, .55).offset(pos);
                diagonal.addCollisionBoxToList(turn, player.world, pos, tail,
                        unwantedTail, null, false);
                require(unwantedTail.isEmpty(),
                        "L corner still has a collidable tail beyond the junction");
                player.world.setBlockToAir(pos.south());
                player.world.setBlockToAir(straightPos);
                player.world.setBlockToAir(pos);
            }
        }
    }

    private static void checkFlatWallCorners(EntityPlayer player, BlockPos pos,
            BlockProgrammableWall wall) {
        IBlockState across = wall.getDefaultState().withProperty(
                BlockProgrammableWall.FACING, EnumFacing.NORTH);
        IBlockState turn = across.withProperty(BlockProgrammableWall.FACING,
                EnumFacing.EAST);
        BlockPos bend = pos.east();
        player.world.setBlockState(pos, across, 2);
        player.world.setBlockState(bend, turn, 2);
        BlockProgrammableWall.FlatCorner corner = wall.flatCorner(turn, player.world, bend);
        require(corner != null && corner.backArm != null
                        && corner.frontArm == null && corner.left() == 0
                        && corner.right() == 10,
                "two plain walls do not form a short L corner");
        java.util.List<AxisAlignedBB> boxes = new java.util.ArrayList<>();
        wall.addCollisionBoxToList(turn, player.world, bend,
                new AxisAlignedBB(bend).grow(2), boxes, null, false);
        require(boxes.size() == 2, "L corner collision lacks its connecting arm");
        player.world.setBlockState(bend.north(), turn, 2);
        player.world.setBlockState(bend.south(), turn, 2);
        corner = wall.flatCorner(turn, player.world, bend);
        require(corner != null && corner.left() == 0 && corner.right() == 16,
                "plain wall T junction does not span both straight neighbors");
        player.world.setBlockToAir(bend.north());
        player.world.setBlockToAir(bend.south());
        player.world.setBlockState(bend.east(), across.withProperty(
                BlockProgrammableWall.FACING, EnumFacing.SOUTH), 2);
        corner = wall.flatCorner(turn, player.world, bend);
        require(corner != null && corner.frontArm != null && corner.backArm != null
                        && corner.left() == 6 && corner.right() == 10,
                "two perpendicular arms grew a false fourth branch");
        IBlockState opposite = turn.withProperty(BlockProgrammableWall.FACING,
                EnumFacing.WEST);
        player.world.setBlockState(bend.north(), opposite, 2);
        corner = wall.flatCorner(turn, player.world, bend);
        require(corner != null && corner.left() == 0 && corner.right() == 10,
                "opposite-facing straight extension disappeared from a three-way junction");
        player.world.setBlockState(bend.south(), opposite, 2);
        corner = wall.flatCorner(turn, player.world, bend);
        require(corner != null && corner.left() == 0 && corner.right() == 16,
                "opposite-facing extension did not complete the four-way junction");
        player.world.setBlockToAir(bend.east());
        player.world.setBlockToAir(bend.north());
        player.world.setBlockToAir(bend.south());
        IBlockState edgeAcross = across.withProperty(BlockProgrammableWall.FACING,
                EnumFacing.SOUTH).withProperty(BlockProgrammableWall.DEPTH, 1);
        IBlockState edgeTurn = turn.withProperty(BlockProgrammableWall.DEPTH, 1);
        player.world.setBlockState(pos, edgeAcross, 2);
        player.world.setBlockState(bend, edgeTurn, 2);
        corner = wall.flatCorner(edgeTurn, player.world, bend);
        require(corner != null && corner.left() == 12 && corner.right() == 16,
                "edge corner did not start at its neighboring wall");
        player.world.setBlockState(bend.north(), opposite.withProperty(
                BlockProgrammableWall.DEPTH, 2), 2);
        corner = wall.flatCorner(edgeTurn, player.world, bend);
        require(corner != null && corner.left() == 0 && corner.right() == 16,
                "edge corner did not extend across the full block");
        AxisAlignedBB edgeBounds = wall.getBoundingBox(edgeTurn, player.world, bend);
        require(edgeBounds.minZ == 0 && edgeBounds.maxZ == 1,
                "edge extension selection box stops at the short corner");
        player.world.setBlockToAir(bend.north());
        player.world.setBlockToAir(pos);
        player.world.setBlockToAir(bend);
    }

    private static void checkWallTurnOrders(EntityPlayer player) {
        checkPortholePartitions();
        BlockProgrammableWall block = (BlockProgrammableWall) ModBlocks.PROGRAMMABLE_DIAGONAL_WALL;
        BlockPos pos = new BlockPos(30, 250, 0);
        for (EnumFacing facing : EnumFacing.HORIZONTALS) {
            EnumFacing rightSide = facing.rotateY();
            for (boolean inverted : new boolean[] {false, true}) {
                IBlockState state = block.getDefaultState()
                        .withProperty(BlockProgrammableWall.FACING, facing)
                        .withProperty(BlockProgrammableWall.INVERTED, inverted);
                for (boolean front : new boolean[] {false, true}) {
                    BlockPos perpendicular = pos.offset(front ? facing : facing.getOpposite());
                    for (boolean right : new boolean[] {false, true}) {
                        IBlockState turn = state.withProperty(BlockProgrammableWall.FACING,
                                right ? rightSide : rightSide.getOpposite());
                        for (int straight = 0; straight < 4; straight++) {
                            for (boolean reverse : new boolean[] {false, true}) {
                                java.util.List<BlockPos> positions = new java.util.ArrayList<>();
                                positions.add(pos);
                                positions.add(perpendicular);
                                if ((straight & 1) != 0) positions.add(pos.offset(rightSide.getOpposite()));
                                if ((straight & 2) != 0) positions.add(pos.offset(rightSide));
                                if (reverse) java.util.Collections.reverse(positions);
                                for (BlockPos at : positions)
                                    player.world.setBlockState(at, at.equals(perpendicular) ? turn : state, 2);
                                BlockProgrammableWall.Corner corner = block.corner(state, player.world, pos);
                                require(corner != null, "wall turn missing after placement order change");
                                double armStart = right ? 9 : 3;
                                boolean keepLeft = (straight & 1) != 0 || (straight == 0 && !right);
                                boolean keepRight = (straight & 2) != 0 || (straight == 0 && right);
                                require(corner.left(3) == (keepLeft ? 0 : armStart)
                                                && corner.right(3) == (keepRight ? 16 : armStart + 4),
                                        "wall turn selects the wrong free end or truncates a T junction: "
                                                + facing + "/" + inverted + "/" + front + "/" + right
                                                + "/" + straight + "/" + reverse);
                                for (BlockPos at : positions) player.world.setBlockToAir(at);
                            }
                        }
                    }
                }
                // Clicking another diagonal must still honor the player's
                // intended direction, just as clicking the floor does.
                player.world.setBlockState(pos.offset(rightSide.getOpposite()),
                        state.withProperty(BlockProgrammableWall.FACING, rightSide), 2);
                player.rotationYaw = facing.getOpposite().getHorizontalAngle();
                IBlockState placed = block.getStateForPlacement(player.world, pos,
                        rightSide, .5F, inverted ? .75F : .25F, .5F, 0, player);
                require(placed.getValue(BlockProgrammableWall.FACING) == facing
                                && placed.getValue(BlockProgrammableWall.INVERTED) == inverted,
                        "diagonal side placement inherits the clicked block's facing");
                player.world.setBlockToAir(pos.offset(rightSide.getOpposite()));
            }
        }
    }

    private static void checkPortholePartitions() {
        // Exhaust every 3x3 occupancy pattern against a brute-force rectangle
        // search. Each chosen rectangle must be largest among remaining cells.
        for (int mask = 1; mask < 512; mask++) {
            java.util.Set<Long> remaining = new java.util.HashSet<>();
            for (int bit = 0; bit < 9; bit++) if ((mask & (1 << bit)) != 0)
                remaining.add(PortholeRectangles.cell(bit % 3 - 1, bit / 3 - 1));
            for (PortholeRectangles.Rect rect : PortholeRectangles.partition(remaining)) {
                int largest = 0;
                for (int x0 = -1; x0 <= 1; x0++) for (int x1 = x0; x1 <= 1; x1++)
                    for (int y0 = -1; y0 <= 1; y0++) for (int y1 = y0; y1 <= 1; y1++) {
                        boolean filled = true;
                        for (int x = x0; x <= x1; x++) for (int y = y0; y <= y1; y++)
                            filled &= remaining.contains(PortholeRectangles.cell(x, y));
                        if (filled) largest = Math.max(largest, (x1-x0+1)*(y1-y0+1));
                    }
                require(rect.width * rect.height == largest, "porthole rectangle is not largest available");
                for (int x = rect.x; x < rect.x + rect.width; x++)
                    for (int y = rect.y; y < rect.y + rect.height; y++)
                        require(remaining.remove(PortholeRectangles.cell(x, y)),
                                "porthole rectangles overlap or include missing cells");
            }
            require(remaining.isEmpty(), "porthole partition leaves cells unassigned");
        }
    }

    private static void checkDiagonalPlacement(EntityPlayer player) {
        checkWallTurnOrders(player);
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
