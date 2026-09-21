package com.vandorlabs.blocks;

import com.vandorlabs.GuiHandler;
import com.vandorlabs.VandorLabs;
import com.vandorlabs.tiles.TileEntityRedstoneLight;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import net.minecraft.tileentity.TileEntity;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

public class BlockLampOff extends BlockVandorConsole {

    private static final Map<Block, BlockLampOff> BY_ON = new HashMap<>();
    private final Block onBlock;

    public BlockLampOff(String name, Block onBlock) {
        super(name);
        this.onBlock = onBlock;
        BY_ON.put(onBlock, this);
    }

    public static BlockLampOff byOn(Block onBlock) {
        return BY_ON.get(onBlock);
    }

    public Block getOnBlock() { return onBlock; }

    @Override public boolean hasTileEntity(IBlockState state) { return true; }
    @Override public TileEntity createTileEntity(World world, IBlockState state) {
        return new TileEntityRedstoneLight();
    }

    @Override
    public int getLightValue(IBlockState state, IBlockAccess world, BlockPos pos) {
        return 0;
    }

    @Override
    public void neighborChanged(IBlockState state, World world, BlockPos pos, Block blockIn, BlockPos fromPos) {
        if (!world.isRemote) {
            TileEntity tile = world.getTileEntity(pos);
            if (tile instanceof TileEntityRedstoneLight)
                ((TileEntityRedstoneLight) tile).refreshLocalInput();
            world.checkLight(pos);
        }
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

    @Override public void breakBlock(World world, BlockPos pos, IBlockState state) {
        IBlockState replacement = world.getBlockState(pos);
        if (replacement.getBlock() instanceof BlockLamp
                || replacement.getBlock() instanceof BlockLampOff) return;
        super.breakBlock(world, pos, state);
    }

    @Override
    public Item getItemDropped(IBlockState state, Random rand, int fortune) {
        return Item.getItemFromBlock(onBlock);
    }

    @Override
    public ItemStack getPickBlock(IBlockState state, RayTraceResult target, World world, BlockPos pos, EntityPlayer player) {
        return new ItemStack(onBlock);
    }
}
