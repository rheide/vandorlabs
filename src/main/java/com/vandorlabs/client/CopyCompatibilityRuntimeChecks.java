package com.vandorlabs.client;

import com.vandorlabs.blocks.BlockAnimatedScreenSelector;
import com.vandorlabs.blocks.BlockVandorDoor;
import com.vandorlabs.blocks.BlockPropulsionLight;
import com.vandorlabs.blocks.BlockTrianglePropulsionLight;
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
        BlockVandorDoor door = observationDoor();
        BlockPos doorSource = new BlockPos(13, 21, 6);
        BlockPos doorTarget = new BlockPos(14, 21, 6);
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

        checkMixedWandSources(world, player, wand);
        player.setHeldItem(EnumHand.MAIN_HAND, oldHeld);
    }

    private static void checkMixedWandSources(World world, EntityPlayerMP player,
            ItemStack wand) {
        Block[] blocks = {ModBlocks.PROGRAMMABLE_WALL, ModBlocks.PROGRAMMABLE_BLOCK,
                ModBlocks.PROGRAMMABLE_CONSOLE};
        for (int b = 0; b < blocks.length; b++) {
            for (EnumFacing face : EnumFacing.values()) {
                BlockPos origin = new BlockPos(30 + b * 8, 40 + face.ordinal() * 4, 0);
                EnumFacing along = face.getAxis() == EnumFacing.Axis.X
                        ? EnumFacing.SOUTH : EnumFacing.EAST;
                BlockPos[] sources = new BlockPos[3];
                BlockPos[] targets = new BlockPos[3];
                for (int i = 0; i < sources.length; i++) {
                    sources[i] = origin.offset(along, i);
                    targets[i] = sources[i].offset(face);
                    world.setBlockState(sources[i], blocks[b].getDefaultState()
                            .withProperty(BlockAnimatedScreenSelector.FACING,
                                    EnumFacing.HORIZONTALS[i]), 2);
                    TileEntityAnimatedScreenSelector tile =
                            (TileEntityAnimatedScreenSelector) world.getTileEntity(sources[i]);
                    tile.setHousingTexture(7 + i);
                    tile.setWallPosition(i);
                    world.setBlockToAir(targets[i]);
                }
                BetterBuildersWandsCompat.INSTANCE.onRightClickBlock(
                        new PlayerInteractEvent.RightClickBlock(player,
                                EnumHand.MAIN_HAND, sources[0], face,
                                new Vec3d(0.5D, 0.5D, 0.5D)));
                for (BlockPos target : targets) {
                    world.setBlockState(target, blocks[b].getDefaultState(), 2);
                }
                setLastPlaced(wand, targets);
                require(BetterBuildersWandsCompat.finishPending(player),
                        "BBW mixed-source journal not consumed");
                for (int i = 0; i < targets.length; i++) {
                    TileEntityAnimatedScreenSelector tile =
                            (TileEntityAnimatedScreenSelector) world.getTileEntity(targets[i]);
                    require(tile.getHousingTexture() == 7 + i
                                    && tile.getWallPosition(-1) == i
                                    && world.getBlockState(targets[i]).equals(
                                            world.getBlockState(sources[i])),
                            "BBW lost per-source settings for " + blocks[b].getRegistryName()
                                    + " face " + face + " cell " + i);
                }
                // Reusing the previous journal on a failed click must do nothing.
                BetterBuildersWandsCompat.INSTANCE.onRightClickBlock(
                        new PlayerInteractEvent.RightClickBlock(player,
                                EnumHand.MAIN_HAND, sources[0], face, Vec3d.ZERO));
                require(!BetterBuildersWandsCompat.finishPending(player),
                        "BBW replayed a stale journal");
                for (BlockPos target : targets) world.setBlockToAir(target);
                for (BlockPos source : sources) world.setBlockToAir(source);
            }
        }
    }

    private static void captureBbw(EntityPlayerMP player, BlockPos source) {
        BetterBuildersWandsCompat.INSTANCE.onRightClickBlock(
                new PlayerInteractEvent.RightClickBlock(player,
                        EnumHand.MAIN_HAND, source, EnumFacing.EAST,
                        new Vec3d(0.5D, 1.0D, 0.5D)));
    }

    private static void setLastPlaced(ItemStack wand, BlockPos... targets) {
        NBTTagCompound root = wand.hasTagCompound()
                ? wand.getTagCompound() : new NBTTagCompound();
        NBTTagCompound bbw = root.hasKey("bbw", 10)
                ? root.getCompoundTag("bbw") : new NBTTagCompound();
        int[] packed = new int[targets.length * 3];
        for (int i = 0; i < targets.length; i++) {
            packed[i * 3] = targets[i].getX();
            packed[i * 3 + 1] = targets[i].getY();
            packed[i * 3 + 2] = targets[i].getZ();
        }
        bbw.setIntArray("lastPlaced", packed);
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
            BlockVandorDoor door = observationDoor();
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
            checkWorldEditRotation(world,forgeWorld,forgeWorldClass,vectorClass,baseBlockClass);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("WorldEdit integration failed", e);
        }
    }

    /** Exercise the same metadata transform invoked by //rotate before paste. */
    private static void checkWorldEditRotation(World world,Object forgeWorld,Class<?> forgeWorldClass,
            Class<?> vectorClass,Class<?> baseBlockClass) throws ReflectiveOperationException {
        Object worldData=forgeWorldClass.getMethod("getWorldData").invoke(forgeWorld);
        Object registry=worldData.getClass().getMethod("getBlockRegistry").invoke(worldData);
        Object rotation=Class.forName("com.sk89q.worldedit.math.transform.AffineTransform")
                .getMethod("rotateY",double.class)
                .invoke(Class.forName("com.sk89q.worldedit.math.transform.AffineTransform")
                        .newInstance(),90.0);
        Method transform=Class.forName("com.sk89q.worldedit.extent.transform.BlockTransformExtent")
                .getMethod("transform",baseBlockClass,
                        Class.forName("com.sk89q.worldedit.math.transform.Transform"),
                        Class.forName("com.sk89q.worldedit.world.registry.BlockRegistry"));
        Constructor<?> base=baseBlockClass.getConstructor(int.class,int.class);
        Method data=baseBlockClass.getMethod("getData"),id=baseBlockClass.getMethod("getId");
        Method get=forgeWorldClass.getMethod("getBlock",vectorClass);
        Method set=forgeWorldClass.getMethod("setBlock",vectorClass,baseBlockClass,boolean.class);
        Object reference=transform.invoke(null,base.newInstance(Block.getIdFromBlock(Blocks.CHEST),2),rotation,registry);
        EnumFacing rotated=Blocks.CHEST.getStateFromMeta((Integer)data.invoke(reference))
                .getValue(net.minecraft.block.BlockChest.FACING);
        require(rotated!=EnumFacing.NORTH,"WorldEdit test rotation actually turns blocks");
        BlockPos from=new BlockPos(13,21,-12),to=new BlockPos(15,21,-12);
        Constructor<?> vector=vectorClass.getConstructor(int.class,int.class,int.class);
        Object fromVector=vector.newInstance(from.getX(),from.getY(),from.getZ());
        Object toVector=vector.newInstance(to.getX(),to.getY(),to.getZ());
        int checked=0;
        for (Block block:Block.REGISTRY) {
            if (!(block instanceof BlockVandorDoor) && !(block instanceof BlockPropulsionLight)) continue;
            IBlockState state=block.getDefaultState();
            if (block instanceof BlockVandorDoor)
                state=state.withProperty(BlockVandorDoor.FACING,EnumFacing.NORTH)
                        .withProperty(BlockVandorDoor.OPEN,true);
            else state=state.withProperty(BlockPropulsionLight.FACING,EnumFacing.NORTH)
                    .withProperty(BlockPropulsionLight.POWERED,false);
            Object rotatedBlock=transform.invoke(null,base.newInstance(Block.getIdFromBlock(block),
                    block.getMetaFromState(state)),rotation,registry);
            IBlockState placed=block.getStateFromMeta((Integer)data.invoke(rotatedBlock));
            EnumFacing actual=block instanceof BlockVandorDoor
                    ?placed.getValue(BlockVandorDoor.FACING):placed.getValue(BlockPropulsionLight.FACING);
            require(actual==rotated && ((Integer)id.invoke(rotatedBlock))==Block.getIdFromBlock(block),
                    "WorldEdit //rotate left facing unchanged for "+block.getRegistryName());
            if (block instanceof BlockVandorDoor)
                require(placed.getValue(BlockVandorDoor.OPEN),"rotated door lost open state");
            else require(!placed.getValue(BlockPropulsionLight.POWERED),"rotated thruster lost power state");
            if (block instanceof BlockTrianglePropulsionLight) for (EnumFacing face:new EnumFacing[]{EnumFacing.UP,EnumFacing.DOWN}) {
                IBlockState floor=block.getDefaultState().withProperty(BlockPropulsionLight.FACING,face);
                Object corner=transform.invoke(null,base.newInstance(Block.getIdFromBlock(block),
                        block.getMetaFromState(floor)),rotation,registry);
                Block changed=Block.getBlockById((Integer)id.invoke(corner));
                require(changed instanceof BlockTrianglePropulsionLight && changed!=block
                        && ((Integer)data.invoke(corner)&7)==face.getIndex(),
                        "WorldEdit //rotate lost floor/ceiling triangle corner for "+block.getRegistryName());
            }
            checked++;
        }
        require(checked>20,"WorldEdit rotation did not cover all doors and thrusters");
        BlockVandorDoor spaceDoor=(BlockVandorDoor)Block.REGISTRY.getObject(
                new ResourceLocation("vandorlabs","programmable_door"));
        require(spaceDoor!=null,"Space Door is unavailable");
        BlockPos doorFrom=new BlockPos(13,21,-15),doorTo=new BlockPos(15,21,-15);
        IBlockState lower=spaceDoor.getDefaultState()
                .withProperty(BlockVandorDoor.FACING,EnumFacing.NORTH);
        IBlockState upper=lower.withProperty(BlockVandorDoor.HALF,BlockDoor.EnumDoorHalf.UPPER)
                .withProperty(BlockVandorDoor.HINGE,BlockDoor.EnumHingePosition.RIGHT);
        world.setBlockState(doorFrom.down(),Blocks.STONE.getDefaultState(),2);
        world.setBlockState(doorTo.down(),Blocks.STONE.getDefaultState(),2);
        world.setBlockState(doorFrom,lower,2);
        world.setBlockState(doorFrom.up(),upper,2);
        for (int dy=0;dy<2;dy++) {
            Object source=vector.newInstance(doorFrom.getX(),doorFrom.getY()+dy,doorFrom.getZ());
            Object destination=vector.newInstance(doorTo.getX(),doorTo.getY()+dy,doorTo.getZ());
            set.invoke(forgeWorld,destination,
                    transform.invoke(null,get.invoke(forgeWorld,source),rotation,registry),true);
        }
        IBlockState pastedLower=world.getBlockState(doorTo);
        IBlockState pastedUpper=world.getBlockState(doorTo.up());
        require(pastedLower.getBlock()==spaceDoor && pastedUpper.getBlock()==spaceDoor
                && pastedLower.getValue(BlockVandorDoor.FACING)==rotated
                && !pastedLower.getValue(BlockVandorDoor.OPEN)
                && pastedUpper.getValue(BlockVandorDoor.HINGE)==BlockDoor.EnumHingePosition.RIGHT
                && !pastedUpper.getValue(BlockVandorDoor.POWERED)
                && spaceDoor.getActualState(pastedUpper,world,doorTo.up()).getValue(BlockVandorDoor.FACING)==rotated,
                "WorldEdit rotated Space Door lost orientation or upper-half metadata: expected facing "
                +rotated+", lower="+pastedLower+", upper="+pastedUpper
                +", upper actual="+spaceDoor.getActualState(pastedUpper,world,doorTo.up()));

        // Representative ForgeWorld pastes verify the transformed data crosses the adapter.
        for (Block block:new Block[]{observationDoor(),
                Block.REGISTRY.getObject(new ResourceLocation("vandorlabs","rocket_thruster")),
                Block.REGISTRY.getObject(new ResourceLocation("vandorlabs","rocket_thruster_wedge"))}) {
            IBlockState source=block.getDefaultState();
            source=block instanceof BlockVandorDoor
                    ?source.withProperty(BlockVandorDoor.FACING,EnumFacing.NORTH)
                    :source.withProperty(BlockPropulsionLight.FACING,EnumFacing.NORTH);
            world.setBlockState(from,source,2);
            Object rotatedBlock=transform.invoke(null,get.invoke(forgeWorld,fromVector),rotation,registry);
            set.invoke(forgeWorld,toVector,rotatedBlock,true);
            IBlockState target=world.getBlockState(to);
            EnumFacing actual=block instanceof BlockVandorDoor
                    ?target.getValue(BlockVandorDoor.FACING):target.getValue(BlockPropulsionLight.FACING);
            require(actual==rotated && target.getBlock()==block,
                    "WorldEdit paste lost rotated orientation for "+block.getRegistryName());
        }
        for (int upperMeta:new int[]{8,9,10,11,12,13,14,15}) {
            Object rotatedUpper=transform.invoke(null,
                    base.newInstance(Block.getIdFromBlock(observationDoor()),upperMeta),rotation,registry);
            require((Integer)data.invoke(rotatedUpper)==upperMeta,"WorldEdit changed upper door hinge or power metadata");
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

    private static BlockVandorDoor observationDoor() {
        Block block = Block.REGISTRY.getObject(new ResourceLocation("vandorlabs",
                "space_observation_rotating_door_framed"));
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
