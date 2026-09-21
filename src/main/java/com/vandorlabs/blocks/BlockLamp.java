package com.vandorlabs.blocks;

import com.vandorlabs.GuiHandler;
import com.vandorlabs.VandorLabs;
import com.vandorlabs.tiles.TileEntityRedstoneLight;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import net.minecraft.tileentity.TileEntity;

public class BlockLamp extends BlockVandorConsole {

    public BlockLamp(String name) {
        super(name);
        // Vanilla's state-change path compares this base value when deciding
        // whether a block swap needs a lighting update.
        setLightLevel(1.0F);
    }

    @Override public boolean hasTileEntity(IBlockState state) { return true; }
    @Override public TileEntity createTileEntity(World world, IBlockState state) {
        return new TileEntityRedstoneLight();
    }

    @Override
    public int getLightValue(IBlockState state, IBlockAccess world, BlockPos pos) {
        return 15;
    }

    @Override
    public boolean onBlockActivated(World world, BlockPos pos, IBlockState state, EntityPlayer player, EnumHand hand, EnumFacing facing, float hitX, float hitY, float hitZ) {
        if (player.isSneaking()) {
            if (!world.isRemote && world.getTileEntity(pos) instanceof TileEntityRedstoneLight)
                player.openGui(VandorLabs.instance, GuiHandler.GUI_REDSTONE_CHANNEL,
                        world, pos.getX(), pos.getY(), pos.getZ());
            return true;
        }
        if (!world.isRemote) {
            TileEntity tile = world.getTileEntity(pos);
            if (tile instanceof TileEntityRedstoneLight)
                ((TileEntityRedstoneLight) tile).toggleManualState();
        }
        return true;
    }

    @Override public void neighborChanged(IBlockState state, World world, BlockPos pos,
            Block block, BlockPos fromPos) {
        if (!world.isRemote) {
            TileEntity tile = world.getTileEntity(pos);
            if (tile instanceof TileEntityRedstoneLight)
                ((TileEntityRedstoneLight) tile).refreshLocalInput();
        }
    }

    @Override public void breakBlock(World world, BlockPos pos, IBlockState state) {
        IBlockState replacement = world.getBlockState(pos);
        if (replacement.getBlock() instanceof BlockLamp
                || replacement.getBlock() instanceof BlockLampOff) return;
        super.breakBlock(world, pos, state);
    }
}
