package com.vandorlabs.client;

import com.vandorlabs.blocks.BlockLightStrip;
import com.vandorlabs.blocks.BlockGlassWall;
import com.vandorlabs.blocks.BlockProgrammableGlass;
import com.vandorlabs.tiles.TileEntityProgrammableGlass;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;

/** Live contracts for the placeable emissive material tiles. */
final class MaterialRuntimeChecks {

    private MaterialRuntimeChecks() {}

    static void run(EntityPlayer player) {
        for (String id : new String[] {"cyan_light_strip", "amber_light_strip"}) {
            Block raw = Block.REGISTRY.getObject(new ResourceLocation("vandorlabs", id));
            require(raw instanceof BlockLightStrip, id + " is not a light strip");
            BlockLightStrip strip = (BlockLightStrip) raw;
            IBlockState vertical = strip.getStateForPlacement(player.world, BlockPos.ORIGIN,
                    EnumFacing.NORTH, 0.5F, 0.5F, 0.5F, 0, player);
            IBlockState horizontal = strip.getStateForPlacement(player.world, BlockPos.ORIGIN,
                    EnumFacing.UP, 0.5F, 0.5F, 0.5F, 0, player);
            require(vertical.getValue(BlockLightStrip.VERTICAL),
                    id + " wall placement is not vertical");
            require(!horizontal.getValue(BlockLightStrip.VERTICAL),
                    id + " floor placement is not horizontal");
            require(strip.getLightValue(vertical, player.world, BlockPos.ORIGIN) == 15,
                    id + " does not emit maximum block light");
            require(strip.getStateFromMeta(strip.getMetaFromState(vertical)).equals(vertical),
                    id + " vertical state does not survive metadata");
        }
        checkGlassPlacement(player);
        BlockGlassWall space = (BlockGlassWall) Block.REGISTRY.getObject(
                new ResourceLocation("vandorlabs", "programmable_glass"));
        checkGlassConnections(player,space,new BlockPos(24,4,24),false,EnumFacing.EAST);
        checkGlassConnections(player,space,new BlockPos(24,4,24),true,EnumFacing.SOUTH);
        checkGlassSettings(player,(BlockProgrammableGlass)space);
        checkGlassDepth(player, (BlockProgrammableGlass)space);
        System.out.println("[vandorlabs][reprolab] material-runtime PASS");
    }

    private static void checkGlassSettings(EntityPlayer player, BlockProgrammableGlass glass) {
        BlockPos source = new BlockPos(29,4,29);
        BlockPos copy = source.east();
        IBlockState chosen = glass.getDefaultState().withProperty(BlockProgrammableGlass.SIZE,2);
        player.world.setBlockState(source, chosen, 3);
        TileEntityProgrammableGlass original = (TileEntityProgrammableGlass)
                player.world.getTileEntity(source);
        original.setShade(2);
        ItemStack picked = glass.getPickBlock(chosen, null, player.world, source, player);
        require(picked.hasTagCompound(), "creative pick lost glass settings");
        player.world.setBlockState(copy, glass.getDefaultState(), 3);
        glass.onBlockPlacedBy(player.world, copy, player.world.getBlockState(copy), player, picked);
        require(player.world.getBlockState(copy).getValue(BlockProgrammableGlass.SIZE)==2,
                "creative copy lost size");
        TileEntityProgrammableGlass copied = (TileEntityProgrammableGlass)
                player.world.getTileEntity(copy);
        require(copied.getShade()==2, "creative copy lost shade");
        player.world.setBlockState(copy, player.world.getBlockState(copy)
                .withProperty(BlockProgrammableGlass.SIZE,0),3);
        require(player.world.getTileEntity(copy)==copied,
                "size update replaced tile and would close dialog");
        NBTTagCompound saved=copied.writeToNBT(new NBTTagCompound());
        TileEntityProgrammableGlass loaded=new TileEntityProgrammableGlass();
        loaded.readFromNBT(saved);
        require(loaded.getShade()==2,"glass shade did not persist");
        player.world.setBlockToAir(source);
        player.world.setBlockToAir(copy);
    }

    private static void checkGlassPlacement(EntityPlayer player) {
        Block raw = Block.REGISTRY.getObject(
                new ResourceLocation("vandorlabs", "programmable_glass"));
        require(raw instanceof BlockGlassWall, "programmable_glass has wrong block class");
        BlockGlassWall glass = (BlockGlassWall) raw;
        BlockPos pos = new BlockPos(24, 4, 24);
        for (EnumFacing direction : EnumFacing.HORIZONTALS) {
            player.rotationYaw = direction.getHorizontalAngle();
            require(player.getHorizontalFacing() == direction,
                    "glass test player facing setup failed for " + direction);
            EnumFacing clickedSide = direction.rotateY();
            player.world.setBlockToAir(pos.offset(clickedSide.getOpposite()));
            IBlockState placed = glass.getStateForPlacement(player.world, pos,
                    clickedSide, 0.5F, 0.5F, 0.5F, 0, player,
                    EnumHand.MAIN_HAND);
            require(placed.getValue(BlockGlassWall.ROTATED)
                            == (direction.getAxis() == EnumFacing.Axis.X),
                    "side-click glass placement is perpendicular for " + direction);
        }
        checkGlassConnections(player,glass,pos,false,EnumFacing.EAST);
        checkGlassConnections(player,glass,pos,true,EnumFacing.SOUTH);
        player.world.setBlockToAir(pos);
    }

    private static void checkGlassDepth(EntityPlayer player, BlockProgrammableGlass glass) {
        BlockPos first = new BlockPos(25, 4, 25);
        BlockPos second = first.east();
        player.rotationYaw = EnumFacing.NORTH.getHorizontalAngle();
        float[] hits = {.1F, .5F, .9F};
        int[] depths = {1, 0, 2};
        for (int i = 0; i < hits.length; i++) {
            IBlockState placed = glass.getStateForPlacement(player.world, first,
                    EnumFacing.UP, .5F, .5F, hits[i], 0, player, EnumHand.MAIN_HAND);
            require(!placed.getValue(BlockGlassWall.ROTATED)
                            && placed.getValue(BlockGlassWall.DEPTH) == depths[i],
                    "glass click did not select its Z depth");
        }
        player.rotationYaw = EnumFacing.EAST.getHorizontalAngle();
        for (int i = 0; i < hits.length; i++) {
            IBlockState placed = glass.getStateForPlacement(player.world, first,
                    EnumFacing.UP, hits[i], .5F, .5F, 0, player, EnumHand.MAIN_HAND);
            require(placed.getValue(BlockGlassWall.ROTATED)
                            && placed.getValue(BlockGlassWall.DEPTH) == depths[2 - i],
                    "glass click did not select its X depth");
        }
        for (int depth = 0; depth < 3; depth++) {
            IBlockState state = glass.getDefaultState()
                    .withProperty(BlockGlassWall.DEPTH, depth);
            require(glass.getStateFromMeta(glass.getMetaFromState(state))
                            .getValue(BlockGlassWall.DEPTH) == depth,
                    "glass depth did not survive metadata");
            player.world.setBlockState(first, state, 3);
            double expected = com.vandorlabs.blocks.PanelDepth.start(depth) / 16D;
            require(glass.getBoundingBox(state, player.world, first).minZ == expected,
                    "glass collision does not match depth");
            IBlockState mismatched = glass.getDefaultState().withProperty(
                    BlockGlassWall.DEPTH, (depth + 1) % 3);
            player.world.setBlockState(second, mismatched, 3);
            require(glass.getActualState(state, player.world, first)
                            .getValue(BlockGlassWall.RIGHT),
                    "glass joined panels at different depths");
        }
        player.world.setBlockToAir(first);
        player.world.setBlockToAir(second);
    }

    private static void checkGlassConnections(EntityPlayer player,BlockGlassWall glass,
            BlockPos first,boolean rotated,EnumFacing right) {
        BlockPos second=first.offset(right);
        IBlockState base=glass.getDefaultState().withProperty(BlockGlassWall.ROTATED,rotated);
        player.world.setBlockState(first,base,2);
        player.world.setBlockState(second,base,2);
        IBlockState firstActual=glass.getActualState(base,player.world,first);
        IBlockState secondActual=glass.getActualState(base,player.world,second);
        require(firstActual.getValue(BlockGlassWall.LEFT)
                        &&!firstActual.getValue(BlockGlassWall.RIGHT),
                "first connected glass panel framed the joining side");
        require(!secondActual.getValue(BlockGlassWall.LEFT)
                        &&secondActual.getValue(BlockGlassWall.RIGHT),
                "second connected glass panel framed the joining side");
        player.world.setBlockToAir(first);
        player.world.setBlockToAir(second);
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException("vandorlabs material runtime check failed: " + message);
        }
    }
}
