package com.vandorlabs.client;

import com.vandorlabs.blocks.ModBlocks;
import com.vandorlabs.items.ItemDuplifier;
import com.vandorlabs.items.DuplifierApplyOptions;
import com.vandorlabs.items.ModItems;
import com.vandorlabs.items.ProgrammableSettings;
import com.vandorlabs.tiles.TileEntityAnimatedScreenSelector;
import com.vandorlabs.tiles.TileEntityRedstoneLight;
import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/** Exercises semantic transfers between different placed programmable blocks. */
final class DuplifierRuntimeChecks {
    private DuplifierRuntimeChecks() { }

    static void run(EntityPlayer player) {
        World world = player.world;
        BlockPos source = new BlockPos(54, 245, 54);
        BlockPos target = source.east(3);
        try {
            Block rocket = Block.REGISTRY.getObject(new ResourceLocation("vandorlabs", "rocket_thruster"));
            world.setBlockState(source, rocket.getDefaultState(), 3);
            TileEntityRedstoneLight engine = (TileEntityRedstoneLight) world.getTileEntity(source);
            engine.setSideTexture(20);
            engine.setJoin(false);
            engine.setManualMode(2, true);
            engine.setRedstoneChannel(19);
            ItemStack tool = new ItemStack(ModItems.DUPLIFIER);
            require(ItemDuplifier.copyFrom(world, source, tool) != null
                            && tool.getDisplayName().contains("Rocket Thruster"),
                    "duplifier did not label the captured source");
            NBTTagCompound copied = tool.getSubCompound(ItemDuplifier.SETTINGS_TAG);
            require(copied != null && copied.getInteger(ProgrammableSettings.WALL_TEXTURE) == 20
                            && !copied.getBoolean(ProgrammableSettings.JOIN)
                            && copied.getInteger(ProgrammableSettings.CHANNEL) == 19,
                    "thruster settings were not captured under shared keys");

            world.setBlockState(target, ModBlocks.PROGRAMMABLE_PORTHOLE_WALL.getDefaultState(), 3);
            long filtered = DuplifierApplyOptions.ALL;
            for (int i = 0; i < DuplifierApplyOptions.OPTIONS.length; i++)
                if (ProgrammableSettings.JOIN.equals(DuplifierApplyOptions.OPTIONS[i].key)
                        || ProgrammableSettings.CHANNEL.equals(
                                DuplifierApplyOptions.OPTIONS[i].key))
                    filtered &= ~(1L << i);
            DuplifierApplyOptions.setMask(tool, filtered);
            require(ItemDuplifier.applyTo(world, target, tool, player),
                    "selected thruster settings did not apply to porthole");
            TileEntityAnimatedScreenSelector porthole =
                    (TileEntityAnimatedScreenSelector) world.getTileEntity(target);
            require(porthole.getHousingTexture() == 20 && porthole.isJoinPortholes()
                            && porthole.getRedstoneChannel() == 0,
                    "disabled Join or channel changed the porthole");
            DuplifierApplyOptions.setMask(tool, DuplifierApplyOptions.ALL);
            require(ItemDuplifier.applyTo(world, target, tool, player),
                    "all enabled thruster settings did not apply to porthole");
            require(porthole.getHousingTexture() == 20 && !porthole.isJoinPortholes()
                            && porthole.getRedstoneChannel() == 19,
                    "thruster to porthole wall texture, Join or channel transfer failed");

            NBTTagCompound fromPorthole = ProgrammableSettings.capture(world, target);
            Block ion = Block.REGISTRY.getObject(new ResourceLocation("vandorlabs", "ion_drive"));
            world.setBlockState(source, ion.getDefaultState(), 3);
            require(ProgrammableSettings.apply(world, source, fromPorthole),
                    "porthole settings did not apply to ion drive");
            TileEntityRedstoneLight ionTile = (TileEntityRedstoneLight) world.getTileEntity(source);
            require(ionTile.getSideTexture() == 20 && !ionTile.isJoin()
                            && ionTile.getRedstoneChannel() == 19,
                    "porthole to thruster shared settings transfer failed");

            world.setBlockState(source, ModBlocks.PROGRAMMABLE_BLOCK.getDefaultState(), 3);
            require(ProgrammableSettings.apply(world, source, fromPorthole),
                    "porthole finish did not apply to programmable block");
            require(((TileEntityAnimatedScreenSelector) world.getTileEntity(source))
                            .getHousingTexture() == 20,
                    "programmable block did not use the shared wall finish");

            world.setBlockState(source, ModBlocks.ANIMATED_SCREEN_SELECTOR.getDefaultState(), 3);
            TileEntityAnimatedScreenSelector screen =
                    (TileEntityAnimatedScreenSelector) world.getTileEntity(source);
            screen.setSelectedScreen("engineering_screen");
            screen.setDisplayMode(TileEntityAnimatedScreenSelector.MODE_STATIC);
            screen.setAnimationSpeedIndex(2);
            screen.setRedstoneEnabled(true);
            screen.setHousingTexture(14);
            require(ItemDuplifier.copyFrom(world, source, tool) != null,
                    "duplifier could not replace the captured source");
            require(DuplifierApplyOptions.mask(tool) == DuplifierApplyOptions.ALL,
                    "copying replaced the selected apply options");
            NBTTagCompound screenSettings = tool.getSubCompound(ItemDuplifier.SETTINGS_TAG);
            require(!screenSettings.hasKey(ProgrammableSettings.JOIN),
                    "new snapshot retained Join from the previous block");
            world.setBlockState(target, ModBlocks.PROGRAMMABLE_CONSOLE.getDefaultState(), 3);
            require(ItemDuplifier.applyTo(world, target, tool, player),
                    "screen settings did not apply to programmable console");
            TileEntityAnimatedScreenSelector console =
                    (TileEntityAnimatedScreenSelector) world.getTileEntity(target);
            require("engineering_screen".equals(console.getSelectedScreen())
                            && console.getDisplayMode() == TileEntityAnimatedScreenSelector.MODE_STATIC
                            && console.getAnimationSpeedIndex() == 2
                            && console.isRedstoneEnabled() && console.getHousingTexture() == 14,
                    "screen primary, display, redstone or wall settings transfer failed");
            require(!ProgrammableSettings.apply(world, source, new NBTTagCompound()),
                    "empty snapshot should not change target");
        } finally {
            world.setBlockToAir(source);
            world.setBlockToAir(target);
        }
        runConnected(player);
        System.out.println("[vandorlabs][reprolab] duplifier-runtime PASS");
    }

    private static void runConnected(EntityPlayer player) {
        World world = player.world;
        BlockPos origin = new BlockPos(54, 245, 54);
        BlockPos[] matching = {origin, origin.east(), origin.east().up(), origin.east(2).up()};
        BlockPos barrier = origin.east(3).up(), behind = origin.east(4).up();
        BlockPos diagonal = origin.west().up(), hiddenFaces = origin.west();
        BlockPos slab = origin.north(), facing = origin.south();
        java.util.List<BlockPos> all = new java.util.ArrayList<>(java.util.Arrays.asList(matching));
        java.util.Collections.addAll(all, barrier, behind, diagonal, hiddenFaces, slab, facing);
        ItemStack tool = new ItemStack(ModItems.DUPLIFIER);
        require(!DuplifierApplyOptions.connected(tool), "connected apply must default off");
        NBTTagCompound copy = new NBTTagCompound();
        copy.setInteger(ProgrammableSettings.WALL_TEXTURE, 20);
        copy.setInteger(ProgrammableSettings.CHANNEL, 7);
        tool.setTagInfo(ItemDuplifier.SETTINGS_TAG, copy);
        long mask = DuplifierApplyOptions.ALL;
        for (int i = 0; i < DuplifierApplyOptions.OPTIONS.length; i++)
            if (ProgrammableSettings.CHANNEL.equals(DuplifierApplyOptions.OPTIONS[i].key)) mask &= ~(1L << i);
        DuplifierApplyOptions.setMask(tool, mask);
        DuplifierApplyOptions.setConnected(tool, true);
        try {
            for (BlockPos pos : all) world.setBlockState(pos, ModBlocks.PROGRAMMABLE_BLOCK.getDefaultState(), 2);
            ((TileEntityAnimatedScreenSelector)world.getTileEntity(barrier)).setRedstoneChannel(1);
            ((TileEntityAnimatedScreenSelector)world.getTileEntity(hiddenFaces)).setFaceTextures(
                    new com.vandorlabs.tiles.FaceTextures(false, new int[]{1,-1,-1,-1,-1,-1}));
            world.setBlockState(slab, ModBlocks.PROGRAMMABLE_SLAB.getDefaultState(), 2);
            world.setBlockState(facing, ModBlocks.PROGRAMMABLE_BLOCK.getDefaultState().withProperty(
                    com.vandorlabs.blocks.BlockAnimatedScreenSelector.FACING, net.minecraft.util.EnumFacing.EAST), 2);
            boolean couldEdit = player.capabilities.allowEdit;
            try {
                player.capabilities.allowEdit = false;
                require(com.vandorlabs.items.DuplifierConnectedApply.apply(world, origin, tool, player,
                        net.minecraft.util.EnumFacing.UP) == 0, "connected apply bypassed edit permissions");
            } finally { player.capabilities.allowEdit = couldEdit; }
            require(com.vandorlabs.items.DuplifierConnectedApply.apply(world, origin, tool, player,
                    net.minecraft.util.EnumFacing.UP) == 6, "connected region did not follow the full bent chain");
            for (BlockPos pos : matching) {
                TileEntityAnimatedScreenSelector tile = (TileEntityAnimatedScreenSelector)world.getTileEntity(pos);
                require(tile.getHousingTexture() == 20 && tile.getRedstoneChannel() == 0,
                        "connected apply changed excluded properties or missed a match");
            }
            for (BlockPos pos : new BlockPos[]{barrier, behind, hiddenFaces, slab})
                require(((TileEntityAnimatedScreenSelector)world.getTileEntity(pos)).getHousingTexture() == 0,
                        "connected apply crossed a type, configuration or adjacency boundary");
            require(((TileEntityAnimatedScreenSelector)world.getTileEntity(facing)).getHousingTexture() == 20
                    && world.getBlockState(facing).getValue(
                            com.vandorlabs.blocks.BlockAnimatedScreenSelector.FACING) == net.minecraft.util.EnumFacing.EAST,
                    "connected apply excluded a different facing or changed its block orientation");
            require(((TileEntityAnimatedScreenSelector)world.getTileEntity(diagonal)).getHousingTexture()==20,
                    "diagonally touching block was missed");
            require(ItemDuplifier.copyFrom(world, origin, tool) != null
                    && DuplifierApplyOptions.connected(tool) && DuplifierApplyOptions.mask(tool) == mask,
                    "copying lost connected apply choices");
            ItemDuplifier.clearCopyState(tool);
            require(DuplifierApplyOptions.connected(tool) && DuplifierApplyOptions.mask(tool) == mask,
                    "clearing lost connected apply choices");
        } finally {
            for (BlockPos pos : all) world.setBlockToAir(pos);
        }
        BlockPos[] slabChain = {origin,origin.up(),origin.up().east().south(),origin.up(2).east(2).south(2)};
        BlockPos separate = origin.east(5);
        try {
            for(int i=0;i<slabChain.length;i++)
                world.setBlockState(slabChain[i],ModBlocks.PROGRAMMABLE_SLAB.getDefaultState().withProperty(
                        com.vandorlabs.blocks.BlockProgrammableSlab.HALF,
                        i%2==0 ? net.minecraft.block.BlockSlab.EnumBlockHalf.TOP
                                : net.minecraft.block.BlockSlab.EnumBlockHalf.BOTTOM),2);
            world.setBlockState(separate,ModBlocks.PROGRAMMABLE_SLAB.getDefaultState(),2);
            tool.setTagInfo(ItemDuplifier.SETTINGS_TAG,copy.copy());
            require(com.vandorlabs.items.DuplifierConnectedApply.apply(world,origin,tool,player,
                    net.minecraft.util.EnumFacing.UP)==slabChain.length,"vertical and corner-touching slabs missed");
            for(int i=0;i<slabChain.length;i++) {
                require(((TileEntityAnimatedScreenSelector)world.getTileEntity(slabChain[i])).getHousingTexture()==20,
                        "slab group did not receive settings");
                require(world.getBlockState(slabChain[i]).getValue(com.vandorlabs.blocks.BlockProgrammableSlab.HALF)
                        ==(i%2==0 ? net.minecraft.block.BlockSlab.EnumBlockHalf.TOP
                                : net.minecraft.block.BlockSlab.EnumBlockHalf.BOTTOM),"copy changed slab half");
            }
            require(((TileEntityAnimatedScreenSelector)world.getTileEntity(separate)).getHousingTexture()==0,
                    "copy crossed an air gap");
        } finally {
            for(BlockPos pos:slabChain)world.setBlockToAir(pos);
            world.setBlockToAir(separate);
        }
        BlockPos edge = new BlockPos(63, 245, 54);
        world.setBlockState(edge, ModBlocks.PROGRAMMABLE_BLOCK.getDefaultState(), 2);
        try {
            World boundary = new GuardedBoundaryWorld(world, edge);
            require(!boundary.isBlockLoaded(edge.east()), "boundary guard did not exclude adjacent chunk");
            tool.setTagInfo(ItemDuplifier.SETTINGS_TAG, copy.copy());
            require(com.vandorlabs.items.DuplifierConnectedApply.apply(boundary, edge, tool, player,
                    net.minecraft.util.EnumFacing.UP) == 1, "boundary block application failed");
        } finally { world.setBlockToAir(edge); }
        Block door = Block.REGISTRY.getObject(new ResourceLocation("vandorlabs", "programmable_door"));
        BlockPos secondDoor = origin.east();
        for (BlockPos root : new BlockPos[]{origin, secondDoor}) {
            world.setBlockState(root.down(), net.minecraft.init.Blocks.STONE.getDefaultState(), 2);
            world.setBlockState(root, door.getDefaultState().withProperty(
                    net.minecraft.block.BlockDoor.HALF, net.minecraft.block.BlockDoor.EnumDoorHalf.LOWER), 2);
            world.setBlockState(root.up(), door.getDefaultState().withProperty(
                    net.minecraft.block.BlockDoor.HALF, net.minecraft.block.BlockDoor.EnumDoorHalf.UPPER), 2);
        }
        try {
            NBTTagCompound doorCopy = new NBTTagCompound();
            doorCopy.setBoolean(ProgrammableSettings.DOOR_HINGES, false);
            tool.setTagInfo(ItemDuplifier.SETTINGS_TAG, doorCopy);
            require(com.vandorlabs.items.DuplifierConnectedApply.apply(world, origin.up(), tool, player,
                    net.minecraft.util.EnumFacing.NORTH) == 2, "door halves were missed or applied twice");
            for (BlockPos root : new BlockPos[]{origin, secondDoor})
                require(!((com.vandorlabs.tiles.TileEntitySpaceDoor)world.getTileEntity(root)).hasHinges(),
                        "connected door settings did not apply");
        } finally {
            for (BlockPos root : new BlockPos[]{origin, secondDoor}) {
                world.setBlockToAir(root.up()); world.setBlockToAir(root); world.setBlockToAir(root.down());
            }
        }
        java.util.List<BlockPos> largeRegion = new java.util.ArrayList<>();
        BlockPos largeOrigin = new BlockPos(96, 200, 96);
        try {
            for (int i = 0; i <= com.vandorlabs.items.DuplifierConnectedApply.MAX_CELLS; i++) {
                BlockPos pos = largeOrigin.add(i % 16, i / 256, (i / 16) % 16);
                largeRegion.add(pos);
                world.setBlockState(pos, ModBlocks.PROGRAMMABLE_BLOCK.getDefaultState(), 2);
            }
            tool.setTagInfo(ItemDuplifier.SETTINGS_TAG, copy.copy());
            require(com.vandorlabs.items.DuplifierConnectedApply.apply(world, largeOrigin, tool, player,
                    net.minecraft.util.EnumFacing.UP) == -1, "oversized matching group was accepted");
            for (BlockPos pos : largeRegion)
                require(((TileEntityAnimatedScreenSelector)world.getTileEntity(pos)).getHousingTexture() == 0,
                        "oversized group was partially changed");
        } finally { for (BlockPos pos : largeRegion) world.setBlockToAir(pos); }
        ItemStack held = player.getHeldItemMainhand();
        double oldX = player.posX, oldY = player.posY, oldZ = player.posZ;
        try {
            NBTTagCompound rampCopy = new NBTTagCompound();
            rampCopy.setInteger(ProgrammableSettings.RAMP_SPEED, 2);
            tool.setTagInfo(ItemDuplifier.SETTINGS_TAG, rampCopy);
            player.setHeldItem(net.minecraft.util.EnumHand.MAIN_HAND, tool);
            player.setPosition(origin.getX(), origin.getY(), origin.getZ());
            for (int i = 0; i < 12; i++) world.setBlockState(origin.east(i),
                    ModBlocks.PROGRAMMABLE_RAMP.getDefaultState(), 2);
            require(com.vandorlabs.items.DuplifierConnectedApply.apply(world, origin, tool, player,
                    net.minecraft.util.EnumFacing.UP) == 12, "distant matching controllers rejected copied settings");
            for (int i = 0; i < 12; i++)
                require(((com.vandorlabs.tiles.TileEntityRampController)world.getTileEntity(origin.east(i))).speed == 2,
                        "connected controller settings did not apply");
        } finally {
            for (int i = 0; i < 12; i++) world.setBlockToAir(origin.east(i));
            player.setHeldItem(net.minecraft.util.EnumHand.MAIN_HAND, held);
            player.setPosition(oldX, oldY, oldZ);
        }
        System.out.println("[vandorlabs][reprolab] duplifier-connected-runtime PASS");
    }

    private static final class GuardedBoundaryWorld extends World {
        private final World source;
        private final BlockPos origin;
        GuardedBoundaryWorld(World source, BlockPos origin) {
            super(source.getSaveHandler(), new net.minecraft.world.storage.WorldInfo(source.getWorldInfo()),
                    new net.minecraft.world.WorldProviderSurface(), new net.minecraft.profiler.Profiler(), false);
            this.source = source;
            this.origin = origin;
        }
        @Override protected net.minecraft.world.chunk.IChunkProvider createChunkProvider() { return null; }
        @Override protected boolean isChunkLoaded(int x, int z, boolean allowEmpty) {
            return x == (origin.getX() >> 4) && z == (origin.getZ() >> 4);
        }
        @Override public boolean isBlockModifiable(EntityPlayer player, BlockPos pos) {
            checkLoaded(pos); return source.isBlockModifiable(player, pos);
        }
        private void checkLoaded(BlockPos pos) {
            require(isBlockLoaded(pos), "connected apply read an unloaded chunk at " + pos);
        }
        @Override public net.minecraft.block.state.IBlockState getBlockState(BlockPos pos) {
            checkLoaded(pos); return source.getBlockState(pos);
        }
        @Override public net.minecraft.tileentity.TileEntity getTileEntity(BlockPos pos) {
            checkLoaded(pos); return source.getTileEntity(pos);
        }
        @Override public void notifyBlockUpdate(BlockPos pos, net.minecraft.block.state.IBlockState before,
                net.minecraft.block.state.IBlockState after, int flags) {
            checkLoaded(pos); source.notifyBlockUpdate(pos, before, after, flags);
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
}
