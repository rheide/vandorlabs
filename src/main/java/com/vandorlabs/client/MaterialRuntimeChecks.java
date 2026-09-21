package com.vandorlabs.client;

import com.vandorlabs.blocks.BlockLightStrip;
import com.vandorlabs.blocks.BlockGlassWall;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;

/** Live contracts for the placeable emissive material tiles. */
final class MaterialRuntimeChecks {

    private MaterialRuntimeChecks() {}

    static void run(EntityPlayer player) {
        for (String id : new String[] {"material_cyan_strip", "material_amber_strip"}) {
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
        System.out.println("[vandorlabs][reprolab] material-runtime PASS");
    }

    private static void checkGlassPlacement(EntityPlayer player) {
        Block raw = Block.REGISTRY.getObject(
                new ResourceLocation("vandorlabs", "glass_wall"));
        require(raw instanceof BlockGlassWall, "glass_wall has wrong block class");
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
