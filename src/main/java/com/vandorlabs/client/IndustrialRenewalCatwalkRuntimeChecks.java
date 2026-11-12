package com.vandorlabs.client;

import com.vandorlabs.blocks.BlockLargeProgrammableDoor;
import com.vandorlabs.blocks.BlockVandorDoor;
import com.vandorlabs.tiles.TileEntityLargeProgrammableDoor;
import net.minecraft.block.Block;
import net.minecraft.block.BlockDoor;
import net.minecraft.block.properties.IProperty;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import java.util.ArrayList;
import java.util.List;

/** Executes the installed Industrial Renewal mod's actual state and collision code. */
@SideOnly(Side.CLIENT)
public final class IndustrialRenewalCatwalkRuntimeChecks {
    private IndustrialRenewalCatwalkRuntimeChecks() { }

    public static void run(World world, BlockPos origin) throws Exception {
        Block[] catwalks = {required("industrialrenewal:catwalk"), required("industrialrenewal:catwalk_steel")};
        Block[] doors = {Blocks.IRON_DOOR, required("vandorlabs:programmable_door"), required("vandorlabs:large_programmable_door")};
        for (int x = -5; x <= 5; x++) for (int z = -5; z <= 5; z++) {
            world.getChunkFromBlockCoords(origin.add(x, 0, z));
            for (int y = -1; y <= 5; y++) world.setBlockToAir(origin.add(x, y, z));
        }
        int cases = 0, removals = 0;
        for (Block catwalk : catwalks) for (Block door : doors)
            for (EnumFacing facing : EnumFacing.HORIZONTALS) for (boolean open : new boolean[]{false, true}) {
                List<BlockPos> members = placeDoor(world, origin, door, facing, open);
                for (BlockPos member : members) for (EnumFacing side : EnumFacing.HORIZONTALS) {
                    BlockPos walk = member.offset(side);
                    if (members.contains(walk)) continue;
                    world.setBlockState(walk, catwalk.getDefaultState(), 3);
                    TileEntity tile = world.getTileEntity(walk);
                    require(tile != null, "catwalk tile exists");
                    // A manual setting on another side must remain owned by IR.
                    tile.getClass().getMethod("activeFacing", EnumFacing.class).invoke(tile, side);
                    NBTTagCompound before = tile.writeToNBT(new NBTTagCompound());
                    assertSide(world, walk, side.getOpposite(), false);
                    require(before.equals(tile.writeToNBT(new NBTTagCompound())), "automatic doorway must not mutate manual side settings");
                    world.setBlockToAir(walk);
                    cases++;
                }
                for (BlockPos member : members) world.setBlockToAir(member);
                BlockPos walk = origin.offset(facing);
                world.setBlockState(walk, catwalk.getDefaultState(), 3);
                assertSide(world, walk, facing.getOpposite(), true);
                world.setBlockToAir(walk);
                for (int x = 0; x < (door instanceof BlockLargeProgrammableDoor ? 3 : 1); x++)
                    world.setBlockToAir(origin.offset(facing.rotateYCCW(), x).down());
                removals++;
            }
        // Keep a simple synchronized fixture for the client-side model path.
        placeDoor(world, origin.east(), doors[1], EnumFacing.WEST, false);
        world.setBlockState(origin, catwalks[0].getDefaultState(), 3);
        assertSide(world, origin, EnumFacing.EAST, false);
        System.out.println("[vandorlabs][reprolab] industrial-renewal-catwalk PASS connectionCases=" + cases
                + " removalCases=" + removals + " actualMod=industrialrenewal manualSettingsPreserved=true");
    }

    /** False means the client has not received the final fixture yet. */
    public static boolean verifyClient(World world, BlockPos origin) {
        if (world.getBlockState(origin).getBlock() != required("industrialrenewal:catwalk")
                || !(world.getBlockState(origin.east()).getBlock() instanceof BlockVandorDoor)
                || world.getTileEntity(origin) == null) return false;
        assertSide(world, origin, EnumFacing.EAST, false);
        System.out.println("[vandorlabs][reprolab] industrial-renewal-catwalk-client PASS actual-state-and-collision");
        return true;
    }

    private static List<BlockPos> placeDoor(World world, BlockPos root, Block door, EnumFacing facing, boolean open) {
        int columns = door instanceof BlockLargeProgrammableDoor ? 3 : 1;
        int rows = columns == 3 ? 3 : 2;
        EnumFacing width = facing.rotateYCCW();
        List<BlockPos> result = new ArrayList<>();
        for (int x = 0; x < columns; x++) world.setBlockState(root.offset(width, x).down(), Blocks.STONE.getDefaultState(), 2);
        for (int x = 0; x < columns; x++) for (int y = 0; y < rows; y++) {
            BlockPos at = root.offset(width, x).up(y);
            IBlockState state = door.getDefaultState().withProperty(BlockDoor.FACING, facing).withProperty(BlockDoor.OPEN, open);
            if (columns == 1) state = state.withProperty(BlockDoor.HALF, y == 0 ? BlockDoor.EnumDoorHalf.LOWER : BlockDoor.EnumDoorHalf.UPPER);
            world.setBlockState(at, state, 2);
            if (columns == 3) ((TileEntityLargeProgrammableDoor) world.getTileEntity(at)).assign(root);
            result.add(at);
        }
        return result;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void assertSide(World world, BlockPos pos, EnumFacing facing, boolean rail) {
        IBlockState stored = world.getBlockState(pos), actual = stored.getActualState(world, pos);
        IProperty property = actual.getPropertyKeys().stream().filter(p -> p.getName().equals(facing.getName())).findFirst()
                .orElseThrow(() -> new AssertionError("Installed catwalk has no " + facing + " connection property"));
        require(Boolean.valueOf(rail).equals(actual.getValue(property)), "rendered catwalk rail " + facing + " should be " + rail);
        double x0 = .2, x1 = .8, z0 = .2, z1 = .8;
        if (facing == EnumFacing.WEST) { x0 = 0; x1 = .04; }
        if (facing == EnumFacing.EAST) { x0 = .96; x1 = 1; }
        if (facing == EnumFacing.NORTH) { z0 = 0; z1 = .04; }
        if (facing == EnumFacing.SOUTH) { z0 = .96; z1 = 1; }
        List<AxisAlignedBB> collisions = new ArrayList<>();
        stored.getBlock().addCollisionBoxToList(stored, world, pos,
                new AxisAlignedBB(pos.getX() + x0, pos.getY() + .2, pos.getZ() + z0,
                        pos.getX() + x1, pos.getY() + 1.2, pos.getZ() + z1), collisions, null, false);
        require(!collisions.isEmpty() == rail, "physical catwalk rail " + facing + " should be " + rail);
    }

    private static Block required(String id) {
        Block block = Block.getBlockFromName(id);
        require(block != null && block != Blocks.AIR, "fixture requires " + id);
        return block;
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
