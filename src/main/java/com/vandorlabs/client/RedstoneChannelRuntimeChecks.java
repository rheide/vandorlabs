package com.vandorlabs.client;

import com.vandorlabs.blocks.BlockVandorDoor;
import com.vandorlabs.blocks.BlockVandorSwitch;
import com.vandorlabs.blocks.BlockLampOff;
import com.vandorlabs.blocks.BlockLamp;
import com.vandorlabs.blocks.BlockPropulsionLight;
import com.vandorlabs.blocks.BlockConnectedPropulsionLight;
import com.vandorlabs.blocks.BlockTrianglePropulsionLight;
import com.vandorlabs.blocks.BlockIndustrialLever;
import com.vandorlabs.redstone.RedstoneChannelMember;
import com.vandorlabs.tiles.TileEntityRedstoneChannel;
import com.vandorlabs.tiles.TileEntitySlidingDoor;
import com.vandorlabs.tiles.TileEntityRedstoneLight;
import net.minecraft.block.Block;
import net.minecraft.block.BlockDoor;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3i;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

/** Integrated-world channel contracts, run by the opt-in ReproLab. */
final class RedstoneChannelRuntimeChecks {
    private RedstoneChannelRuntimeChecks() { }

    static void run(World world, EntityPlayer player) {
        checkTrianglePlacement(world, player);
        checkLeverPlacement(world,player);
        Block rawSwitch = Block.REGISTRY.getObject(new ResourceLocation("vandorlabs", "switch_rocker"));
        Block rawDoor = Block.REGISTRY.getObject(new ResourceLocation("vandorlabs", "door_security"));
        Block rawLight = Block.REGISTRY.getObject(new ResourceLocation("vandorlabs", "wall_lightbar_unlit"));
        Block rawPropulsion = Block.REGISTRY.getObject(
                new ResourceLocation("vandorlabs", "ion_thruster"));
        require(rawSwitch instanceof BlockVandorSwitch && rawDoor instanceof BlockVandorDoor
                        && rawLight instanceof BlockLampOff
                        && rawPropulsion instanceof BlockPropulsionLight,
                "channel test blocks are missing");
        BlockVandorSwitch channelSwitch = (BlockVandorSwitch) rawSwitch;
        BlockVandorDoor door = (BlockVandorDoor) rawDoor;
        BlockLampOff light = (BlockLampOff) rawLight;
        BlockPropulsionLight propulsion = (BlockPropulsionLight) rawPropulsion;
        BlockPos switchPos = new BlockPos(24, 25, 24);
        BlockPos secondSwitchPos = switchPos.add(0, 0, -4);
        BlockPos doorPos = switchPos.add(4, 0, 0);
        // Keep one receiver directly beside the first switch. This exercises
        // the case where the same block contributes physical power and also
        // follows the channel while another transmitter remains on.
        BlockPos lightPos = switchPos.east();
        BlockPos propulsionPos = switchPos.east(2);

        world.setBlockState(switchPos.down(), Blocks.STONE.getDefaultState(), 3);
        world.setBlockState(secondSwitchPos.down(), Blocks.STONE.getDefaultState(), 3);
        world.setBlockState(doorPos.down(), Blocks.STONE.getDefaultState(), 3);
        world.setBlockState(switchPos, channelSwitch.getDefaultState()
                .withProperty(BlockVandorSwitch.FACING, EnumFacing.UP)
                .withProperty(BlockVandorSwitch.ON, false), 3);
        world.setBlockState(secondSwitchPos, channelSwitch.getDefaultState()
                .withProperty(BlockVandorSwitch.FACING, EnumFacing.UP)
                .withProperty(BlockVandorSwitch.ON, false), 3);
        IBlockState lower = door.getDefaultState()
                .withProperty(BlockVandorDoor.HALF, BlockDoor.EnumDoorHalf.LOWER)
                .withProperty(BlockVandorDoor.FACING, EnumFacing.NORTH)
                .withProperty(BlockVandorDoor.HINGE, BlockDoor.EnumHingePosition.LEFT)
                .withProperty(BlockVandorDoor.OPEN, false)
                .withProperty(BlockVandorDoor.POWERED, false);
        world.setBlockState(doorPos, lower, 3);
        world.setBlockState(doorPos.up(), lower.withProperty(BlockVandorDoor.HALF,
                BlockDoor.EnumDoorHalf.UPPER), 3);
        world.setBlockState(lightPos, light.getDefaultState(), 3);
        world.setBlockState(propulsionPos, propulsion.getDefaultState()
                .withProperty(BlockPropulsionLight.FACING, EnumFacing.EAST), 3);

        RedstoneChannelMember switchTile = (RedstoneChannelMember) world.getTileEntity(switchPos);
        RedstoneChannelMember secondSwitchTile = (RedstoneChannelMember)
                world.getTileEntity(secondSwitchPos);
        RedstoneChannelMember doorTile = (RedstoneChannelMember) world.getTileEntity(doorPos);
        RedstoneChannelMember lightTile = (RedstoneChannelMember) world.getTileEntity(lightPos);
        RedstoneChannelMember propulsionTile = (RedstoneChannelMember)
                world.getTileEntity(propulsionPos);
        switchTile.setRedstoneChannel(4271);
        secondSwitchTile.setRedstoneChannel(4271);
        doorTile.setRedstoneChannel(4271);
        lightTile.setRedstoneChannel(4271);
        propulsionTile.setRedstoneChannel(4271);
        require(world.getBlockState(lightPos).getBlock() instanceof BlockLampOff,
                "unpowered channel light started illuminated");
        require(!world.getBlockState(propulsionPos).getValue(BlockPropulsionLight.POWERED),
                "unpowered channel propulsion fixture started illuminated");
        channelSwitch.onBlockActivated(world, switchPos, world.getBlockState(switchPos), player,
                EnumHand.MAIN_HAND, EnumFacing.UP, .5F, .5F, .5F);
        require(world.getBlockState(doorPos).getValue(BlockVandorDoor.OPEN),
                "switch high did not propagate to door");
        require(world.getBlockState(lightPos).getBlock() instanceof BlockLamp,
                "switch high kept unlit block variant: "
                        + world.getBlockState(lightPos).getBlock().getRegistryName());
        require(world.getBlockState(propulsionPos).getValue(BlockPropulsionLight.POWERED)
                        && world.getBlockState(propulsionPos).getValue(BlockPropulsionLight.FACING)
                                == EnumFacing.EAST,
                "switch high did not illuminate propulsion fixture or changed its facing");
        channelSwitch.onBlockActivated(world, secondSwitchPos,
                world.getBlockState(secondSwitchPos), player,
                EnumHand.MAIN_HAND, EnumFacing.UP, .5F, .5F, .5F);
        channelSwitch.onBlockActivated(world, switchPos, world.getBlockState(switchPos), player,
                EnumHand.MAIN_HAND, EnumFacing.UP, .5F, .5F, .5F);
        require(world.getBlockState(doorPos).getValue(BlockVandorDoor.OPEN),
                "first switch low released channel while second switch remained high");
        require(world.getBlockState(lightPos).getBlock() instanceof BlockLamp,
                "first switch low extinguished light while second switch remained high");
        channelSwitch.onBlockActivated(world, secondSwitchPos,
                world.getBlockState(secondSwitchPos), player,
                EnumHand.MAIN_HAND, EnumFacing.UP, .5F, .5F, .5F);
        require(!world.getBlockState(doorPos).getValue(BlockVandorDoor.OPEN),
                "last of two switches low did not release door");
        require(world.getBlockState(lightPos).getBlock() instanceof BlockLampOff,
                "last of two switches low kept lit block variant: "
                        + world.getBlockState(lightPos).getBlock().getRegistryName());
        require(!world.getBlockState(propulsionPos).getValue(BlockPropulsionLight.POWERED),
                "last of two switches low kept propulsion fixture illuminated");

        require(lightTile.getRedstoneChannel() == 4271,
                "redstone-driven lamp toggle discarded its channel");
        NBTTagCompound lightNbt = ((TileEntityRedstoneLight) lightTile)
                .writeToNBT(new NBTTagCompound());
        NBTTagCompound propulsionNbt = ((TileEntityRedstoneLight) propulsionTile)
                .writeToNBT(new NBTTagCompound());
        lightTile.setRedstoneChannel(0);
        light.onBlockActivated(world, lightPos, world.getBlockState(lightPos), player,
                EnumHand.MAIN_HAND, EnumFacing.UP, .5F, .5F, .5F);
        require(world.getBlockState(lightPos).getBlock() instanceof BlockLamp,
                "channel-zero lamp did not preserve manual right-click behavior");
        propulsionTile.setRedstoneChannel(0);
        propulsion.onBlockActivated(world, propulsionPos, world.getBlockState(propulsionPos),
                player, EnumHand.MAIN_HAND, EnumFacing.UP, .5F, .5F, .5F);
        require(world.getBlockState(propulsionPos).getValue(BlockPropulsionLight.POWERED)
                        && ((TileEntityRedstoneLight) propulsionTile)
                                .isParticleStreamSelected()
                        && propulsion.getActualState(world.getBlockState(propulsionPos),
                                world, propulsionPos)
                                .getValue(BlockPropulsionLight.PARTICLES),
                "propulsion fixture did not enter particle-stream mode");
        propulsion.onBlockActivated(world, propulsionPos, world.getBlockState(propulsionPos),
                player, EnumHand.MAIN_HAND, EnumFacing.UP, .5F, .5F, .5F);
        require(!world.getBlockState(propulsionPos).getValue(BlockPropulsionLight.POWERED),
                "particle-stream mode did not cycle to off");
        require(!propulsion.getActualState(world.getBlockState(propulsionPos), world,
                        propulsionPos).getValue(BlockPropulsionLight.PARTICLES),
                "off propulsion fixture retained its stream texture");
        propulsion.onBlockActivated(world, propulsionPos, world.getBlockState(propulsionPos),
                player, EnumHand.MAIN_HAND, EnumFacing.UP, .5F, .5F, .5F);
        require(world.getBlockState(propulsionPos).getValue(BlockPropulsionLight.POWERED)
                        && !((TileEntityRedstoneLight) propulsionTile)
                                .isParticleStreamSelected(),
                "off mode did not cycle back to plain on");

        checkConnectedThrusters(world, player, (BlockConnectedPropulsionLight) propulsion,
                switchPos.add(8, 0, 0));

        NBTTagCompound switchNbt = ((TileEntityRedstoneChannel) switchTile)
                .writeToNBT(new NBTTagCompound());
        NBTTagCompound doorNbt = ((TileEntitySlidingDoor) doorTile)
                .writeToNBT(new NBTTagCompound());
        require(switchNbt.getInteger("RedstoneChannel") == 4271
                        && doorNbt.getInteger("RedstoneChannel") == 4271
                        && lightNbt.getInteger("RedstoneChannel") == 4271
                        && propulsionNbt.getInteger("RedstoneChannel") == 4271,
                "channel assignments were not persisted");

        world.setBlockToAir(switchPos);
        world.setBlockToAir(switchPos.down());
        world.setBlockToAir(secondSwitchPos);
        world.setBlockToAir(secondSwitchPos.down());
        world.setBlockToAir(doorPos.up());
        world.setBlockToAir(doorPos);
        world.setBlockToAir(doorPos.down());
        world.setBlockToAir(lightPos);
        world.setBlockToAir(propulsionPos);
        System.out.println("[vandorlabs][reprolab] redstone-channel-runtime PASS");
    }

    private static void checkLeverPlacement(World world,EntityPlayer player) {
        BlockPos pos=new BlockPos(20,25,20);
        world.setBlockState(pos.down(),Blocks.STONE.getDefaultState(),3);
        for (String id:new String[]{"industrial_lever","compact_lever"}) {
            Block raw=Block.REGISTRY.getObject(new ResourceLocation("vandorlabs",id));
            require(raw instanceof BlockIndustrialLever,id+" is missing");
            BlockIndustrialLever lever=(BlockIndustrialLever)raw;
            require(lever.canPlaceBlockOnSide(world,pos,EnumFacing.UP),
                    id+" cannot mount on a solid floor");
            IBlockState floor=lever.getStateForPlacement(world,pos,EnumFacing.UP,
                    .5F,.5F,.5F,0,player);
            require(floor.getValue(BlockIndustrialLever.FLOOR),
                    id+" floor placement did not retain its mount");
            require(lever.getStateFromMeta(lever.getMetaFromState(floor)).equals(floor),
                    id+" floor placement does not survive metadata");
        }
        world.setBlockToAir(pos);
        world.setBlockToAir(pos.down());
    }

    private static void checkTrianglePlacement(World world, EntityPlayer player) {
        String base = "rocket_thruster_triangle";
        require(base.equals(BlockTrianglePropulsionLight.variantIdForHit(base,
                EnumFacing.NORTH, 0.25F, 0.25F, 0.0F)),
                "north triangle bottom-left placement is wrong");
        require((base + "_bottom_right").equals(
                BlockTrianglePropulsionLight.variantIdForHit(base,
                        EnumFacing.NORTH, 0.75F, 0.25F, 0.0F)),
                "north triangle bottom-right placement is wrong");
        require((base + "_top_right").equals(
                BlockTrianglePropulsionLight.variantIdForHit(base,
                        EnumFacing.NORTH, 0.75F, 0.75F, 0.0F)),
                "north triangle top-right placement is wrong");
        require((base + "_top_left").equals(
                BlockTrianglePropulsionLight.variantIdForHit(base,
                        EnumFacing.NORTH, 0.25F, 0.75F, 0.0F)),
                "north triangle top-left placement is wrong");

        // The same local bottom-left corner after each blockstate rotation.
        require(base.equals(BlockTrianglePropulsionLight.variantIdForHit(base,
                EnumFacing.SOUTH, 0.75F, 0.25F, 1.0F)),
                "south triangle local axes are wrong");
        require(base.equals(BlockTrianglePropulsionLight.variantIdForHit(base,
                EnumFacing.EAST, 1.0F, 0.25F, 0.25F)),
                "east triangle local axes are wrong");
        require(base.equals(BlockTrianglePropulsionLight.variantIdForHit(base,
                EnumFacing.WEST, 0.0F, 0.25F, 0.75F)),
                "west triangle local axes are wrong");
        require(base.equals(BlockTrianglePropulsionLight.variantIdForHit(base,
                EnumFacing.UP, 0.25F, 1.0F, 0.25F)),
                "up triangle local axes are wrong");
        require(base.equals(BlockTrianglePropulsionLight.variantIdForHit(base,
                EnumFacing.DOWN, 0.25F, 0.0F, 0.75F)),
                "down triangle local axes are wrong");

        String[] families = {"rocket_thruster", "ion_thruster", "plasma_thruster",
                "impulse_engine"};
        String[] suffixes = {"", "_bottom_right", "_top_right", "_top_left"};
        for (String family : families) {
            for (String suffix : suffixes) {
                Block block = Block.REGISTRY.getObject(new ResourceLocation("vandorlabs",
                        family + "_triangle" + suffix));
                require(block instanceof BlockTrianglePropulsionLight,
                        "triangle variant is missing: " + family + suffix);
            }
        }

        BlockTrianglePropulsionLight triangle = (BlockTrianglePropulsionLight)
                Block.REGISTRY.getObject(new ResourceLocation("vandorlabs", base));
        IBlockState placed = triangle.getStateForPlacement(world, BlockPos.ORIGIN,
                EnumFacing.NORTH, 0.75F, 0.75F, 0.0F, 0, player, EnumHand.MAIN_HAND);
        require((base + "_top_right").equals(
                        placed.getBlock().getRegistryName().getResourcePath())
                        && placed.getValue(BlockPropulsionLight.FACING) == EnumFacing.NORTH
                        && placed.getValue(BlockPropulsionLight.POWERED),
                "triangle ItemBlock placement did not select the registered corner state");
    }

    private static void checkConnectedThrusters(World world, EntityPlayer player,
            BlockConnectedPropulsionLight thruster, BlockPos anchor) {
        for (int x = 0; x < 2; x++) {
            for (int y = 0; y < 2; y++) {
                world.setBlockState(anchor.add(x, y, 0), thruster.getDefaultState()
                        .withProperty(BlockPropulsionLight.FACING, EnumFacing.NORTH), 3);
            }
        }
        require(thruster.getActualState(world.getBlockState(anchor), world, anchor)
                        .getValue(BlockConnectedPropulsionLight.PART)
                        == BlockConnectedPropulsionLight.ConnectedPart.BOTTOM_LEFT,
                "isolated 2x2 did not derive its bottom-left quadrant");
        BlockPos extra = anchor.add(2, 0, 0);
        world.setBlockState(extra, thruster.getDefaultState()
                .withProperty(BlockPropulsionLight.FACING, EnumFacing.NORTH), 3);
        require(thruster.getActualState(world.getBlockState(anchor), world, anchor)
                        .getValue(BlockConnectedPropulsionLight.PART)
                        == BlockConnectedPropulsionLight.ConnectedPart.SINGLE,
                "larger touching group was accepted as a 2x2");
        world.setBlockToAir(extra);

        TileEntityRedstoneLight first = (TileEntityRedstoneLight) world.getTileEntity(anchor);
        first.toggleManualState();
        TileEntityRedstoneLight restored = new TileEntityRedstoneLight();
        restored.readFromNBT(first.writeToNBT(new NBTTagCompound()));
        require(first.isParticleStreamSelected()
                        && restored.isParticleStreamSelected()
                        && thruster.getActualState(world.getBlockState(anchor.add(1, 0, 0)),
                                world, anchor.add(1, 0, 0))
                                .getValue(BlockConnectedPropulsionLight.PART)
                                == BlockConnectedPropulsionLight.ConnectedPart.SINGLE,
                "mixed plain/streaming modes remained connected");
        for (BlockPos member : new BlockPos[] {anchor.add(1, 0, 0), anchor.add(0, 1, 0),
                anchor.add(1, 1, 0)})
            ((TileEntityRedstoneLight) world.getTileEntity(member)).toggleManualState();
        require(thruster.getActualState(world.getBlockState(anchor.add(1, 1, 0)), world,
                        anchor.add(1, 1, 0)).getValue(BlockConnectedPropulsionLight.PART)
                        == BlockConnectedPropulsionLight.ConnectedPart.TOP_RIGHT,
                "matching particle-stream modes did not reconnect the 2x2");
        thruster.onBlockActivated(world, anchor, world.getBlockState(anchor), player,
                EnumHand.MAIN_HAND, EnumFacing.NORTH, .5F, .5F, .5F);
        for (int x = 0; x < 2; x++) for (int y = 0; y < 2; y++) {
            BlockPos member = anchor.add(x, y, 0);
            require(!world.getBlockState(member).getValue(BlockPropulsionLight.POWERED),
                    "combined right-click did not turn off member " + x + "," + y);
        }
        thruster.onBlockActivated(world, anchor.add(1, 1, 0),
                world.getBlockState(anchor.add(1, 1, 0)), player, EnumHand.MAIN_HAND,
                EnumFacing.NORTH, .5F, .5F, .5F);
        for (int x = 0; x < 2; x++) for (int y = 0; y < 2; y++) {
            BlockPos member = anchor.add(x, y, 0);
            require(world.getBlockState(member).getValue(BlockPropulsionLight.POWERED)
                            && !((TileEntityRedstoneLight) world.getTileEntity(member))
                                    .isParticleStreamSelected(),
                    "combined right-click did not turn on member " + x + "," + y);
        }
        thruster.configureAssembly(world, anchor, 4272, true, true);
        for (int x = 0; x < 2; x++) for (int y = 0; y < 2; y++) {
            TileEntityRedstoneLight tile = (TileEntityRedstoneLight)
                    world.getTileEntity(anchor.add(x, y, 0));
            require(tile.getRedstoneChannel() == 4272 && tile.isParticleStreamSelected(),
                    "combined channel dialog did not configure member " + x + "," + y);
        }
        thruster.configureAssembly(world, anchor, 0, true, false);
        for (int x = 0; x < 2; x++)
            for (int y = 0; y < 2; y++) world.setBlockToAir(anchor.add(x, y, 0));

        checkSquare(world, thruster, anchor, 3, EnumFacing.NORTH,
                EnumFacing.EAST, EnumFacing.UP);
        checkSquare(world, thruster, anchor, 3, EnumFacing.EAST,
                EnumFacing.SOUTH, EnumFacing.UP);
        checkSquare(world, thruster, anchor, 3, EnumFacing.SOUTH,
                EnumFacing.WEST, EnumFacing.UP);
        checkSquare(world, thruster, anchor, 3, EnumFacing.WEST,
                EnumFacing.NORTH, EnumFacing.UP);
        checkSquare(world, thruster, anchor, 3, EnumFacing.UP,
                EnumFacing.EAST, EnumFacing.SOUTH);
        checkSquare(world, thruster, anchor, 3, EnumFacing.DOWN,
                EnumFacing.EAST, EnumFacing.NORTH);
        checkSquare(world, thruster, anchor, 8, EnumFacing.NORTH,
                EnumFacing.EAST, EnumFacing.UP);
    }

    private static void checkSquare(World world, BlockConnectedPropulsionLight thruster,
            BlockPos anchor, int size, EnumFacing facing, EnumFacing right, EnumFacing up) {
        double centerX = 0;
        double centerY = 0;
        double centerZ = 0;
        for (int x = 0; x < size; x++) {
            for (int y = 0; y < size; y++) {
                BlockPos member = move(move(anchor, right, x), up, y);
                world.setBlockState(member, thruster.getDefaultState()
                        .withProperty(BlockPropulsionLight.FACING, facing), 3);
                centerX += member.getX() + .5D;
                centerY += member.getY() + .5D;
                centerZ += member.getZ() + .5D;
            }
        }
        double members = size * size;
        Vec3d expectedCenter = new Vec3d(centerX / members, centerY / members,
                centerZ / members);
        for (int x = 0; x < size; x++) {
            for (int y = 0; y < size; y++) {
                BlockPos member = move(move(anchor, right, x), up, y);
                BlockConnectedPropulsionLight.ConnectedPart part = thruster.getActualState(
                        world.getBlockState(member), world, member)
                        .getValue(BlockConnectedPropulsionLight.PART);
                require(part.size == size && part.x == x && part.y == y,
                        size + "x" + size + " " + facing + " mapped " + x + "," + y
                                + " to " + part.getName());
                Vec3d particleCenter = BlockConnectedPropulsionLight.particleCenter(
                        member, facing, part);
                require(particleCenter.squareDistanceTo(expectedCenter) < 0.000001D,
                        size + "x" + size + " " + facing
                                + " produced inconsistent particle centers");
            }
        }
        for (int x = 0; x < size; x++)
            for (int y = 0; y < size; y++)
                world.setBlockToAir(move(move(anchor, right, x), up, y));
    }

    private static BlockPos move(BlockPos pos, EnumFacing facing, int distance) {
        Vec3i direction = facing.getDirectionVec();
        return pos.add(direction.getX() * distance, direction.getY() * distance,
                direction.getZ() * distance);
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException("redstone channel check: " + message);
    }
}
