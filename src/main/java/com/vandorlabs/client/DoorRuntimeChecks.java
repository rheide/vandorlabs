package com.vandorlabs.client;

import com.vandorlabs.blocks.BlockVandorDoor;
import com.vandorlabs.blocks.BlockDetailedDoor;
import com.vandorlabs.blocks.BlockConnectingDetailedDoor;
import net.minecraft.block.Block;
import net.minecraft.block.BlockDoor;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.world.World;
import com.vandorlabs.tiles.TileEntitySlidingDoor;
import java.util.ArrayList;
import java.util.List;

/** Small integrated-world contract suite used by the opt-in ReproLab. */
final class DoorRuntimeChecks {

    private DoorRuntimeChecks() {}

    static void run(World world, EntityPlayer player) {
        BlockVandorDoor hingedA = door("door_security");
        BlockVandorDoor hingedB = door("door_airlock_glass");
        BlockVandorDoor sliding = door("sliding_security_door");
        BlockPos origin = new BlockPos(-8, 21, 6);

        List<BlockVandorDoor> allDoors = allDoors();
        require(allDoors.size() >= 12, "expected at least 12 registered door families");
        for (BlockVandorDoor door : allDoors) {
            checkMetadata(door);
            checkStateParity(world, origin, door);
            if (door instanceof BlockDetailedDoor) {
                checkDetailedBounds(world, origin, (BlockDetailedDoor) door);
            }
        }
        checkPair(world, player, origin, hingedA, hingedB, EnumFacing.NORTH, true,
                "aligned same-motion pair");
        checkPair(world, player, origin, hingedA, sliding, EnumFacing.NORTH, false,
                "different motion");
        checkPair(world, player, origin, hingedA, hingedB, EnumFacing.EAST, false,
                "misaligned facing");
        checkIncompletePair(world, player, origin, hingedA, hingedB);
        checkPlacement(world, player, origin, hingedA, hingedB, sliding);
        checkMotionPairMatrix(world, player, origin);
        checkConnectingDetailedDoors(world, origin);
        checkRedstoneEdges(world, player, origin, hingedA);
        checkBreakPair(world, origin, hingedA);
        clear(world, origin);
        clear(world, origin.east());
        System.out.println("[vandorlabs][reprolab] door-runtime PASS");
    }

    private static BlockVandorDoor door(String id) {
        Block block = Block.REGISTRY.getObject(new ResourceLocation("vandorlabs", id));
        require(block instanceof BlockVandorDoor, "missing test door " + id);
        return (BlockVandorDoor) block;
    }

    private static List<BlockVandorDoor> allDoors() {
        List<BlockVandorDoor> doors = new ArrayList<>();
        for (Block block : Block.REGISTRY) {
            if (block instanceof BlockVandorDoor) {
                doors.add((BlockVandorDoor) block);
            }
        }
        return doors;
    }

    private static void checkMetadata(BlockVandorDoor door) {
        for (EnumFacing facing : EnumFacing.HORIZONTALS) {
            for (boolean open : new boolean[] { false, true }) {
                IBlockState state = door.getDefaultState()
                        .withProperty(BlockVandorDoor.HALF, BlockDoor.EnumDoorHalf.LOWER)
                        .withProperty(BlockVandorDoor.FACING, facing)
                        .withProperty(BlockVandorDoor.OPEN, open);
                IBlockState decoded = door.getStateFromMeta(door.getMetaFromState(state));
                require(decoded.getValue(BlockVandorDoor.HALF) == BlockDoor.EnumDoorHalf.LOWER
                        && decoded.getValue(BlockVandorDoor.FACING) == facing
                        && decoded.getValue(BlockVandorDoor.OPEN) == open,
                        "lower metadata round trip failed");
            }
        }
        for (BlockDoor.EnumHingePosition hinge : BlockDoor.EnumHingePosition.values()) {
            for (boolean open : new boolean[] { false, true }) {
                for (boolean powered : new boolean[] { false, true }) {
                    IBlockState state = door.getDefaultState()
                            .withProperty(BlockVandorDoor.HALF, BlockDoor.EnumDoorHalf.UPPER)
                            .withProperty(BlockVandorDoor.HINGE, hinge)
                            .withProperty(BlockVandorDoor.OPEN, open)
                            .withProperty(BlockVandorDoor.POWERED, powered);
                    IBlockState decoded = door.getStateFromMeta(door.getMetaFromState(state));
                    require(decoded.getValue(BlockVandorDoor.HALF) == BlockDoor.EnumDoorHalf.UPPER
                            && decoded.getValue(BlockVandorDoor.HINGE) == hinge
                            && decoded.getValue(BlockVandorDoor.OPEN) == open
                            && decoded.getValue(BlockVandorDoor.POWERED) == powered,
                            "upper metadata round trip failed");
                }
            }
        }
    }

    private static void checkPair(World world, EntityPlayer player, BlockPos firstPos,
            BlockVandorDoor first, BlockVandorDoor second, EnumFacing secondFacing,
            boolean shouldToggle, String label) {
        clear(world, firstPos);
        clear(world, firstPos.east());
        place(world, firstPos, first, EnumFacing.NORTH, BlockDoor.EnumHingePosition.LEFT);
        place(world, firstPos.east(), second, secondFacing,
                BlockDoor.EnumHingePosition.RIGHT);
        IBlockState clicked = world.getBlockState(firstPos);
        first.onBlockActivated(world, firstPos, clicked, player, EnumHand.MAIN_HAND,
                EnumFacing.NORTH, 0.5F, 0.5F, 0.5F);
        require(world.getBlockState(firstPos).getValue(BlockVandorDoor.OPEN),
                "clicked door did not open: " + label);
        require(world.getBlockState(firstPos.up()).getValue(BlockVandorDoor.OPEN),
                "clicked upper half did not open: " + label);
        require(world.getBlockState(firstPos.east()).getValue(BlockVandorDoor.OPEN)
                        == shouldToggle,
                "neighbor pairing mismatch: " + label);
        require(world.getBlockState(firstPos.east().up()).getValue(BlockVandorDoor.OPEN)
                        == shouldToggle,
                "neighbor upper pairing mismatch: " + label);
    }

    private static void checkIncompletePair(World world, EntityPlayer player,
            BlockPos firstPos, BlockVandorDoor first, BlockVandorDoor second) {
        clear(world, firstPos);
        clear(world, firstPos.east());
        place(world, firstPos, first, EnumFacing.NORTH, BlockDoor.EnumHingePosition.LEFT);
        IBlockState orphan = lower(second, EnumFacing.NORTH, BlockDoor.EnumHingePosition.RIGHT);
        world.setBlockState(firstPos.east(), orphan, 2);
        first.onBlockActivated(world, firstPos, world.getBlockState(firstPos), player,
                EnumHand.MAIN_HAND, EnumFacing.NORTH, 0.5F, 0.5F, 0.5F);
        require(!world.getBlockState(firstPos.east()).getValue(BlockVandorDoor.OPEN),
                "incomplete neighbor was paired");
    }

    private static void checkPlacement(World world, EntityPlayer player, BlockPos pos,
            BlockVandorDoor door, BlockVandorDoor compatible, BlockVandorDoor incompatible) {
        for (EnumFacing facing : EnumFacing.HORIZONTALS) {
            player.rotationYaw = facing.getOpposite().getHorizontalAngle();
            require(player.getHorizontalFacing().getOpposite() == facing,
                    "test player facing setup failed");
            float leftX = 0.5F;
            float leftZ = 0.5F;
            switch (facing) {
                case NORTH: leftX = 0.75F; break;
                case SOUTH: leftX = 0.25F; break;
                case EAST: leftZ = 0.75F; break;
                case WEST: leftZ = 0.25F; break;
                default: break;
            }
            clear(world, pos);
            clear(world, pos.offset(facing.rotateY()));
            clear(world, pos.offset(facing.rotateYCCW()));
            IBlockState clickedLeft = door.getStateForPlacement(world, pos,
                    EnumFacing.UP, leftX, 0.5F, leftZ, 0, player, EnumHand.MAIN_HAND);
            require(clickedLeft.getValue(BlockVandorDoor.FACING) == facing
                            && clickedLeft.getValue(BlockVandorDoor.HINGE)
                            == BlockDoor.EnumHingePosition.RIGHT,
                    "visual-left placement mapping failed for " + facing);

            place(world, pos.offset(facing.rotateY()), compatible, facing,
                    BlockDoor.EnumHingePosition.RIGHT);
            IBlockState besideLeft = door.getStateForPlacement(world, pos,
                    EnumFacing.UP, 0.5F, 0.5F, 0.5F, 0, player, EnumHand.MAIN_HAND);
            require(besideLeft.getValue(BlockVandorDoor.HINGE)
                            == BlockDoor.EnumHingePosition.LEFT,
                    "compatible left neighbor did not choose outer hinge for " + facing);

            clear(world, pos.offset(facing.rotateY()));
            place(world, pos.offset(facing.rotateYCCW()), compatible, facing,
                    BlockDoor.EnumHingePosition.LEFT);
            IBlockState besideRight = door.getStateForPlacement(world, pos,
                    EnumFacing.UP, 0.5F, 0.5F, 0.5F, 0, player, EnumHand.MAIN_HAND);
            require(besideRight.getValue(BlockVandorDoor.HINGE)
                            == BlockDoor.EnumHingePosition.RIGHT,
                    "compatible right neighbor did not choose outer hinge for " + facing);

            clear(world, pos.offset(facing.rotateYCCW()));
            place(world, pos.offset(facing.rotateY()), incompatible, facing,
                    BlockDoor.EnumHingePosition.RIGHT);
            IBlockState ignored = door.getStateForPlacement(world, pos,
                    EnumFacing.UP, leftX, 0.5F, leftZ, 0, player, EnumHand.MAIN_HAND);
            require(ignored.getValue(BlockVandorDoor.HINGE)
                            == BlockDoor.EnumHingePosition.RIGHT,
                    "different-motion neighbor influenced placement for " + facing);
            clear(world, pos.offset(facing.rotateY()));
        }
    }

    private static void checkStateParity(World world, BlockPos pos, BlockVandorDoor door) {
        for (EnumFacing facing : EnumFacing.HORIZONTALS) {
            for (BlockDoor.EnumHingePosition hinge : BlockDoor.EnumHingePosition.values()) {
                for (boolean open : new boolean[] { false, true }) {
                    clear(world, pos);
                    place(world, pos, door, facing, hinge);
                    IBlockState lower = world.getBlockState(pos)
                            .withProperty(BlockVandorDoor.OPEN, open);
                    IBlockState upper = world.getBlockState(pos.up())
                            .withProperty(BlockVandorDoor.OPEN, open);
                    world.setBlockState(pos, lower, 2);
                    world.setBlockState(pos.up(), upper, 2);
                    require(door.getBoundingBox(lower, world, pos).equals(
                                    door.getBoundingBox(upper, world, pos.up())),
                            "upper/lower selection mismatch for " + door.getRegistryName());
                    Object lowerCollision = door.getCollisionBoundingBox(lower, world, pos);
                    Object upperCollision = door.getCollisionBoundingBox(upper, world, pos.up());
                    require(lowerCollision == null ? upperCollision == null
                                    : lowerCollision.equals(upperCollision),
                            "upper/lower collision mismatch for " + door.getRegistryName());
                    require(!open || (lowerCollision == null)
                                    == door.getDoorMotion().clearsOpening(),
                            "open collision disagrees with motion for " + door.getRegistryName());
                }
            }
        }
    }

    private static void checkDetailedBounds(World world, BlockPos pos,
            BlockDetailedDoor door) {
        for (EnumFacing facing : EnumFacing.HORIZONTALS) {
            clear(world, pos);
            place(world, pos, door, facing, BlockDoor.EnumHingePosition.LEFT);
            IBlockState state = world.getBlockState(pos);
            AxisAlignedBB box = door.getBoundingBox(state, world, pos);
            if (door.isSlidingModel()) {
                if (facing == EnumFacing.NORTH || facing == EnumFacing.SOUTH) {
                    require(box.minZ <= 0.35D && box.maxZ >= 0.70D,
                            "detailed sliding selection misses centre track for "
                            + door.getRegistryName());
                } else {
                    require(box.minX <= 0.30D && box.maxX >= 0.65D,
                            "detailed sliding selection misses centre track for "
                            + door.getRegistryName());
                }
            } else {
                switch (facing) {
                    case NORTH:
                        require(box.minZ == 0.0D && box.maxZ <= 0.35D,
                                "north rotating door is not on north edge");
                        break;
                    case SOUTH:
                        require(box.minZ >= 0.65D && box.maxZ == 1.0D,
                                "south rotating door is not on south edge");
                        break;
                    case WEST:
                        require(box.minX == 0.0D && box.maxX <= 0.35D,
                                "west rotating door is not on west edge");
                        break;
                    case EAST:
                        require(box.minX >= 0.65D && box.maxX == 1.0D,
                                "east rotating door is not on east edge");
                        break;
                    default:
                        throw new AssertionError("non-horizontal door facing");
                }
            }
        }
    }

    private static void checkConnectingDetailedDoors(World world, BlockPos pos) {
        String[][] families = {
                {"detail_engineering_rotating_single",
                        "detail_engineering_rotating_double"},
                {"detail_engineering_sliding_single",
                        "detail_engineering_sliding_double"},
                {"detail_observation_rotating_single",
                        "detail_observation_rotating_double"},
                {"detail_observation_sliding_single",
                        "detail_observation_sliding_double"}
        };
        for (String[] family : families) {
            BlockVandorDoor rawSingle = door(family[0]);
            require(rawSingle instanceof BlockConnectingDetailedDoor,
                    family[0] + " is not connection-aware");
            Block removed = Block.REGISTRY.getObject(
                    new ResourceLocation("vandorlabs", family[1]));
            require(removed == null || removed == Blocks.AIR,
                    family[1] + " is still registered as a block");
            BlockConnectingDetailedDoor single =
                    (BlockConnectingDetailedDoor) rawSingle;

            for (EnumFacing facing : EnumFacing.HORIZONTALS) {
                BlockPos matePos = pos.offset(facing.rotateY());
                clear(world, pos);
                clear(world, matePos);
                place(world, pos, single, facing,
                        BlockDoor.EnumHingePosition.LEFT);
                IBlockState standalone = single.getActualState(
                        world.getBlockState(pos), world, pos);
                require(!standalone.getValue(BlockConnectingDetailedDoor.PAIRED)
                                && single.getVisualModel(standalone) == single,
                        family[0] + " selected paired art while standalone");

                place(world, matePos, single, facing,
                        BlockDoor.EnumHingePosition.RIGHT);
                IBlockState first = single.getActualState(
                        world.getBlockState(pos), world, pos);
                IBlockState second = single.getActualState(
                        world.getBlockState(matePos), world, matePos);
                BlockDetailedDoor visual = single.getVisualModel(first);
                require(first.getValue(BlockConnectingDetailedDoor.PAIRED)
                                && second.getValue(BlockConnectingDetailedDoor.PAIRED)
                                && visual != single && visual.isDoubleModel()
                                && family[1].equals(visual.getRegistryName()
                                        .getResourcePath())
                                && single.getLeafMetadata(false, first) == 3
                                && single.getLeafMetadata(true, first) == 4,
                        family[0] + " did not select its paired frame/leaves for "
                                + facing);

                clear(world, matePos);
                place(world, matePos, single, facing,
                        BlockDoor.EnumHingePosition.LEFT);
                IBlockState sameHinge = single.getActualState(
                        world.getBlockState(pos), world, pos);
                require(!sameHinge.getValue(BlockConnectingDetailedDoor.PAIRED),
                        family[0] + " paired same-direction leaves for " + facing);

                clear(world, matePos);
                place(world, matePos, single, facing.rotateY(),
                        BlockDoor.EnumHingePosition.RIGHT);
                IBlockState misaligned = single.getActualState(
                        world.getBlockState(pos), world, pos);
                require(!misaligned.getValue(BlockConnectingDetailedDoor.PAIRED),
                        family[0] + " paired perpendicular doors for " + facing);
                clear(world, pos);
                clear(world, matePos);
            }
        }
    }

    private static void checkMotionPairMatrix(World world, EntityPlayer player, BlockPos pos) {
        BlockVandorDoor[] modes = {
                door("door_security"),
                door("sliding_security_door")
        };
        for (BlockVandorDoor first : modes) {
            for (BlockVandorDoor second : modes) {
                boolean compatible = first.getDoorMotion() == second.getDoorMotion();
                checkPair(world, player, pos, first, second, EnumFacing.NORTH,
                        compatible, first.getDoorMotion() + " / " + second.getDoorMotion());
            }
        }
    }

    private static void checkRedstoneEdges(World world, EntityPlayer player,
            BlockPos pos, BlockVandorDoor door) {
        clear(world, pos);
        world.setBlockToAir(pos.north());
        place(world, pos, door, EnumFacing.NORTH, BlockDoor.EnumHingePosition.LEFT);
        world.setBlockState(pos.north(), Blocks.REDSTONE_BLOCK.getDefaultState(), 3);
        door.neighborChanged(world.getBlockState(pos), world, pos,
                Blocks.REDSTONE_BLOCK, pos.north());
        require(world.getBlockState(pos).getValue(BlockVandorDoor.OPEN)
                        && world.getBlockState(pos.up()).getValue(BlockVandorDoor.POWERED),
                "redstone rising edge did not open both halves");
        world.setBlockToAir(pos.north());
        door.neighborChanged(world.getBlockState(pos), world, pos,
                Blocks.AIR, pos.north());
        require(!world.getBlockState(pos).getValue(BlockVandorDoor.OPEN)
                        && !world.getBlockState(pos.up()).getValue(BlockVandorDoor.POWERED),
                "redstone falling edge did not close both halves");
        door.onBlockActivated(world, pos, world.getBlockState(pos), player,
                EnumHand.MAIN_HAND, EnumFacing.NORTH, 0.5F, 0.5F, 0.5F);
        door.neighborChanged(world.getBlockState(pos), world, pos,
                Blocks.STONE, pos.south());
        require(world.getBlockState(pos).getValue(BlockVandorDoor.OPEN),
                "unpowered neighbor update undid a manual open state");
    }

    private static void checkBreakPair(World world, BlockPos pos, BlockVandorDoor door) {
        clear(world, pos);
        place(world, pos, door, EnumFacing.NORTH, BlockDoor.EnumHingePosition.LEFT);
        world.destroyBlock(pos.up(), false);
        require(world.isAirBlock(pos), "breaking upper half left lower half behind");
        place(world, pos, door, EnumFacing.NORTH, BlockDoor.EnumHingePosition.LEFT);
        world.destroyBlock(pos, false);
        require(world.isAirBlock(pos.up()), "breaking lower half left upper half behind");
    }

    private static void place(World world, BlockPos pos, BlockVandorDoor door,
            EnumFacing facing, BlockDoor.EnumHingePosition hinge) {
        IBlockState lower = lower(door, facing, hinge);
        IBlockState upper = lower.withProperty(BlockVandorDoor.HALF,
                BlockDoor.EnumDoorHalf.UPPER);
        world.setBlockState(pos, lower, 2);
        world.setBlockState(pos.up(), upper, 2);
        require(world.getTileEntity(pos) instanceof TileEntitySlidingDoor
                        && world.getTileEntity(pos.up()) instanceof TileEntitySlidingDoor,
                "Forge did not create both door renderer tiles");
    }

    private static IBlockState lower(BlockVandorDoor door, EnumFacing facing,
            BlockDoor.EnumHingePosition hinge) {
        return door.getDefaultState()
                .withProperty(BlockVandorDoor.HALF, BlockDoor.EnumDoorHalf.LOWER)
                .withProperty(BlockVandorDoor.FACING, facing)
                .withProperty(BlockVandorDoor.HINGE, hinge)
                .withProperty(BlockVandorDoor.OPEN, false)
                .withProperty(BlockVandorDoor.POWERED, false);
    }

    private static void clear(World world, BlockPos pos) {
        world.setBlockToAir(pos.up());
        world.setBlockToAir(pos);
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException("door runtime check: " + message);
        }
    }
}
