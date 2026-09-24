package com.vandorlabs.client;

import com.vandorlabs.blocks.BlockIndustrialDisplayTable;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/** Live Forge checks for the connected Industrial Display Table. */
public final class DisplayTableRuntimeChecks {
    private DisplayTableRuntimeChecks() {}

    public static void run(World world, EntityPlayer player) {
        Block raw = Block.REGISTRY.getObject(new ResourceLocation("vandorlabs", "industrial_display_table"));
        require(raw instanceof BlockIndustrialDisplayTable, "table registration");
        BlockIndustrialDisplayTable table = (BlockIndustrialDisplayTable) raw;
        ItemStack stack = new ItemStack(table);
        require("Industrial Display Table".equals(stack.getDisplayName()), "table item name");
        Minecraft mc = Minecraft.getMinecraft();
        require(mc.getRenderItem().getItemModelMesher().getItemModel(stack)
                != mc.getRenderItem().getItemModelMesher().getModelManager().getMissingModel(),
                "table item model");

        BlockPos left = new BlockPos(-30, 28, -30);
        BlockPos middle = left.east();
        BlockPos right = middle.east();
        BlockPos[] positions = {left, middle, right};
        IBlockState[] original = new IBlockState[positions.length];
        for (int i = 0; i < positions.length; i++) original[i] = world.getBlockState(positions[i]);
        IBlockState north = table.getDefaultState().withProperty(BlockIndustrialDisplayTable.FACING,
                EnumFacing.NORTH);
        try {
            for (BlockPos pos : positions) world.setBlockState(pos, north, 3);
            check(table, world, left, false, true);
            check(table, world, middle, true, true);
            check(table, world, right, true, false);
            world.setBlockState(middle, north.withProperty(BlockIndustrialDisplayTable.FACING,
                    EnumFacing.SOUTH), 3);
            check(table, world, left, false, false);
            check(table, world, right, false, false);
            world.setBlockState(middle, north, 3);
            world.setBlockToAir(middle);
            check(table, world, left, false, false);
            check(table, world, right, false, false);
            require(table.isSideSolid(north, world, left, EnumFacing.UP), "tabletop support");
        } finally {
            for (int i = 0; i < positions.length; i++)
                world.setBlockState(positions[i], original[i], 3);
        }
        System.out.println("[vandorlabs][reprolab] display-table-runtime PASS");
    }

    private static void check(BlockIndustrialDisplayTable table, World world, BlockPos pos,
            boolean left, boolean right) {
        IBlockState actual = table.getActualState(world.getBlockState(pos), world, pos);
        require(actual.getValue(BlockIndustrialDisplayTable.LEFT) == left
                        && actual.getValue(BlockIndustrialDisplayTable.RIGHT) == right,
                "table connections at " + pos);
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException("display table: " + message);
    }
}
