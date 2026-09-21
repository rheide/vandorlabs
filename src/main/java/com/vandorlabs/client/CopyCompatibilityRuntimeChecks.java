package com.vandorlabs.client;

import com.vandorlabs.blocks.BlockAnimatedScreenSelector;
import com.vandorlabs.blocks.BlockVandorDoor;
import com.vandorlabs.blocks.ModBlocks;
import com.vandorlabs.compat.BetterBuildersWandsCompat;
import com.vandorlabs.tiles.TileEntityAnimatedScreenSelector;
import net.minecraft.block.Block;
import net.minecraft.block.BlockDoor;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.fml.common.Loader;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;

/** Live contracts at the same boundaries used by WorldEdit and BBW. */
final class CopyCompatibilityRuntimeChecks {

    private CopyCompatibilityRuntimeChecks() {}

    static void run(World world, EntityPlayerMP player) {
        checkEveryMetadataCodec();
        checkBetterBuildersWandsBoundary(world, player);
        if (Loader.isModLoaded("worldedit")) {
            checkWorldEditAdapter(world);
        } else {
            throw new IllegalStateException("WorldEdit missing from integration run");
        }
        System.out.println("[vandorlabs][reprolab] copy-compat-runtime PASS");
    }

    private static void checkEveryMetadataCodec() {
        for (Block block : Block.REGISTRY) {
            ResourceLocation id = block.getRegistryName();
            if (id == null || !"vandorlabs".equals(id.getResourceDomain())) {
                continue;
            }
            for (int meta = 0; meta < 16; meta++) {
                IBlockState decoded = block.getStateFromMeta(meta);
                IBlockState restored = block.getStateFromMeta(
                        block.getMetaFromState(decoded));
                require(decoded.equals(restored), "metadata codec is lossy for "
                        + id + " meta " + meta);
            }
        }
    }

    private static void checkBetterBuildersWandsBoundary(World world,
            EntityPlayerMP player) {
        Item wandItem = Item.REGISTRY.getObject(new ResourceLocation(
                "betterbuilderswands", "wandDiamond"));
        if (wandItem == null) {
            // Forge registry paths are normalized in some 1.12 builds.
            wandItem = Item.REGISTRY.getObject(new ResourceLocation(
                    "betterbuilderswands", "wanddiamond"));
        }
        require(wandItem != null, "Better Builder's Wands test jar not loaded");
        ItemStack oldHeld = player.getHeldItemMainhand().copy();
        ItemStack wand = new ItemStack(wandItem);
        player.setHeldItem(EnumHand.MAIN_HAND, wand);

        BlockPos source = new BlockPos(13, 21, 3);
        BlockPos target = new BlockPos(14, 21, 3);
        world.setBlockState(source, ModBlocks.PROGRAMMABLE_CONSOLE
                .getDefaultState().withProperty(BlockAnimatedScreenSelector.FACING,
                        EnumFacing.WEST), 2);
        configure(world, source);
        captureBbw(player, source);
        // This is BBW 0.11.1's compatibility boundary: setBlock(state), then
        // an int[] destination journal in the wand. Deliberately place the
        // default state to prove the compatibility layer restores both state
        // and tile data rather than passing accidentally.
        world.setBlockState(target,
                ModBlocks.PROGRAMMABLE_CONSOLE.getDefaultState(), 2);
        setLastPlaced(wand, target);
        require(BetterBuildersWandsCompat.finishPending(player),
                "BBW placement journal was not consumed");
        require(world.getBlockState(target).equals(world.getBlockState(source)),
                "BBW did not preserve programmable block orientation");
        requireConfigured(world, target, "BBW programmable tile data");

        // Multi-cell blocks need more than metadata: BBW places only the
        // clicked cell. The adapter reconstructs both door cells exactly.
        BlockVandorDoor door = detailedObservationDoor();
        BlockPos doorSource = new BlockPos(13, 21, 6);
        BlockPos doorTarget = new BlockPos(15, 21, 6);
        world.setBlockState(doorSource.down(), Blocks.STONE.getDefaultState(), 2);
        world.setBlockState(doorTarget.down(), Blocks.STONE.getDefaultState(), 2);
        IBlockState lower = door.getDefaultState()
                .withProperty(BlockVandorDoor.FACING, EnumFacing.EAST)
                .withProperty(BlockVandorDoor.HALF, BlockDoor.EnumDoorHalf.LOWER)
                .withProperty(BlockVandorDoor.OPEN, true);
        IBlockState upper = lower
                .withProperty(BlockVandorDoor.HALF, BlockDoor.EnumDoorHalf.UPPER)
                .withProperty(BlockVandorDoor.HINGE,
                        BlockDoor.EnumHingePosition.RIGHT)
                .withProperty(BlockVandorDoor.POWERED, false);
        world.setBlockState(doorSource, lower, 2);
        world.setBlockState(doorSource.up(), upper, 2);
        captureBbw(player, doorSource);
        world.setBlockState(doorTarget, door.getDefaultState(), 2);
        setLastPlaced(wand, doorTarget);
        require(BetterBuildersWandsCompat.finishPending(player),
                "BBW door placement journal was not consumed");
        require(door.getActualState(world.getBlockState(doorTarget), world,
                        doorTarget).equals(door.getActualState(
                        world.getBlockState(doorSource), world, doorSource))
                        && door.getActualState(world.getBlockState(
                        doorTarget.up()), world, doorTarget.up()).equals(
                        door.getActualState(world.getBlockState(
                        doorSource.up()), world, doorSource.up())),
                "BBW did not preserve both door halves");

        player.setHeldItem(EnumHand.MAIN_HAND, oldHeld);
    }

    private static void captureBbw(EntityPlayerMP player, BlockPos source) {
        BetterBuildersWandsCompat.INSTANCE.onRightClickBlock(
                new PlayerInteractEvent.RightClickBlock(player,
                        EnumHand.MAIN_HAND, source, EnumFacing.UP,
                        new Vec3d(0.5D, 1.0D, 0.5D)));
    }

    private static void setLastPlaced(ItemStack wand, BlockPos target) {
        NBTTagCompound root = wand.hasTagCompound()
                ? wand.getTagCompound() : new NBTTagCompound();
        NBTTagCompound bbw = root.hasKey("bbw", 10)
                ? root.getCompoundTag("bbw") : new NBTTagCompound();
        bbw.setIntArray("lastPlaced", new int[] {target.getX(), target.getY(),
                target.getZ()});
        root.setTag("bbw", bbw);
        wand.setTagCompound(root);
    }

    private static void checkWorldEditAdapter(World world) {
        try {
            Class<?> vectorClass = Class.forName("com.sk89q.worldedit.Vector");
            Class<?> baseBlockClass = Class.forName(
                    "com.sk89q.worldedit.blocks.BaseBlock");
            Class<?> forgeWorldClass = Class.forName(
                    "com.sk89q.worldedit.forge.ForgeWorld");
            Constructor<?> fwConstructor = forgeWorldClass
                    .getDeclaredConstructor(World.class);
            fwConstructor.setAccessible(true);
            Object forgeWorld = fwConstructor.newInstance(world);
            Constructor<?> vectorConstructor = vectorClass.getConstructor(
                    int.class, int.class, int.class);
            Method getBlock = forgeWorldClass.getMethod("getBlock", vectorClass);
            Method setBlock = forgeWorldClass.getMethod("setBlock", vectorClass,
                    baseBlockClass, boolean.class);

            BlockPos source = new BlockPos(13, 21, -3);
            BlockPos target = new BlockPos(15, 21, -3);
            IBlockState state = ModBlocks.PROGRAMMABLE_INPUT.getDefaultState()
                    .withProperty(com.vandorlabs.blocks.BlockProgrammableInput.FACING,
                            EnumFacing.SOUTH)
                    .withProperty(com.vandorlabs.blocks.BlockProgrammableInput.KEYBOARD,
                            false)
                    .withProperty(com.vandorlabs.blocks.BlockProgrammableInput.UPPER,
                            true);
            world.setBlockState(source, state, 2);
            configure(world, source);
            Object sourceVector = vectorConstructor.newInstance(source.getX(),
                    source.getY(), source.getZ());
            Object targetVector = vectorConstructor.newInstance(target.getX(),
                    target.getY(), target.getZ());
            Object clipboardBlock = getBlock.invoke(forgeWorld, sourceVector);
            setBlock.invoke(forgeWorld, targetVector, clipboardBlock, true);
            require(world.getBlockState(target).equals(state),
                    "WorldEdit lost programmable block metadata");
            requireConfigured(world, target, "WorldEdit programmable tile data");

            // Copy both cells, exactly as a region containing a complete door
            // does. A selection containing only half a door remains invalid by
            // vanilla/WorldEdit semantics.
            BlockVandorDoor door = detailedObservationDoor();
            BlockPos doorSource = new BlockPos(13, 21, -6);
            BlockPos doorTarget = new BlockPos(15, 21, -6);
            world.setBlockState(doorSource.down(),
                    Blocks.STONE.getDefaultState(), 2);
            world.setBlockState(doorTarget.down(),
                    Blocks.STONE.getDefaultState(), 2);
            IBlockState lower = door.getDefaultState()
                    .withProperty(BlockVandorDoor.FACING, EnumFacing.WEST)
                    .withProperty(BlockVandorDoor.HALF,
                            BlockDoor.EnumDoorHalf.LOWER)
                    .withProperty(BlockVandorDoor.OPEN, true);
            IBlockState upper = lower
                    .withProperty(BlockVandorDoor.HALF,
                            BlockDoor.EnumDoorHalf.UPPER)
                    .withProperty(BlockVandorDoor.HINGE,
                            BlockDoor.EnumHingePosition.RIGHT)
                    .withProperty(BlockVandorDoor.POWERED, false);
            world.setBlockState(doorSource, lower, 2);
            world.setBlockState(doorSource.up(), upper, 2);
            for (int dy = 0; dy < 2; dy++) {
                BlockPos from = doorSource.up(dy);
                BlockPos to = doorTarget.up(dy);
                Object fromVector = vectorConstructor.newInstance(from.getX(),
                        from.getY(), from.getZ());
                Object toVector = vectorConstructor.newInstance(to.getX(),
                        to.getY(), to.getZ());
                setBlock.invoke(forgeWorld, toVector,
                        getBlock.invoke(forgeWorld, fromVector), true);
            }
            IBlockState sourceLowerActual = door.getActualState(
                    world.getBlockState(doorSource), world, doorSource);
            IBlockState sourceUpperActual = door.getActualState(
                    world.getBlockState(doorSource.up()), world,
                    doorSource.up());
            IBlockState targetLowerActual = door.getActualState(
                    world.getBlockState(doorTarget), world, doorTarget);
            IBlockState targetUpperActual = door.getActualState(
                    world.getBlockState(doorTarget.up()), world,
                    doorTarget.up());
            require(targetLowerActual.equals(sourceLowerActual)
                            && targetUpperActual.equals(sourceUpperActual),
                    "WorldEdit lost door orientation/hinge/open/power metadata: "
                            + "expected " + sourceLowerActual + " / "
                            + sourceUpperActual + ", got " + targetLowerActual
                            + " / " + targetUpperActual);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("WorldEdit integration failed", e);
        }
    }

    private static void configure(World world, BlockPos pos) {
        TileEntity tile = world.getTileEntity(pos);
        require(tile instanceof TileEntityAnimatedScreenSelector,
                "programmable tile missing at " + pos);
        TileEntityAnimatedScreenSelector selector =
                (TileEntityAnimatedScreenSelector) tile;
        selector.setSelectedScreen("engineering_screen");
        selector.setRedstoneEnabled(true);
        selector.setDisplayMode(TileEntityAnimatedScreenSelector.MODE_STATIC);
        selector.setFramed(false);
        selector.setAnimationSpeedIndex(2);
        selector.setInputPanel(TileEntityAnimatedScreenSelector.INPUT_PANELS[1]);
        selector.setSecondaryInputPanel(TileEntityAnimatedScreenSelector.INPUT_PANELS[
                TileEntityAnimatedScreenSelector.INPUT_PANELS.length - 1]);
        selector.setWallPosition(1);
        selector.setSmallInput(true);
    }

    private static BlockVandorDoor detailedObservationDoor() {
        Block block = Block.REGISTRY.getObject(new ResourceLocation("vandorlabs",
                "detail_observation_rotating_single"));
        require(block instanceof BlockVandorDoor,
                "detailed observation door is unavailable");
        return (BlockVandorDoor) block;
    }

    private static void requireConfigured(World world, BlockPos pos, String label) {
        TileEntity tile = world.getTileEntity(pos);
        require(tile instanceof TileEntityAnimatedScreenSelector,
                label + " is missing");
        TileEntityAnimatedScreenSelector selector =
                (TileEntityAnimatedScreenSelector) tile;
        require("engineering_screen".equals(selector.getSelectedScreen())
                        && selector.isRedstoneEnabled()
                        && selector.getDisplayMode()
                        == TileEntityAnimatedScreenSelector.MODE_STATIC
                        && !selector.isFramed()
                        && selector.getAnimationSpeedIndex() == 2
                        && TileEntityAnimatedScreenSelector.INPUT_PANELS[1]
                        .equals(selector.getInputPanel())
                        && TileEntityAnimatedScreenSelector.INPUT_PANELS[
                        TileEntityAnimatedScreenSelector.INPUT_PANELS.length - 1]
                        .equals(selector.getSecondaryInputPanel())
                        && selector.getWallPosition(0) == 1
                        && selector.isSmallInput(), label + " was not preserved");
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException(message);
        }
    }
}
