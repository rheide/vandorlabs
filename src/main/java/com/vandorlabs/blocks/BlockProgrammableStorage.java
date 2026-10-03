package com.vandorlabs.blocks;

import com.vandorlabs.GuiHandler;
import com.vandorlabs.VandorLabs;
import com.vandorlabs.tiles.TileEntityProgrammableStorage;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.InventoryHelper;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/** Independent 27-slot storage; adjacent blocks never merge inventories. */
public class BlockProgrammableStorage extends BlockProgrammableBlock {
    public BlockProgrammableStorage() { super("programmable_storage"); }
    @Override public TileEntity createNewTileEntity(World world, int meta) { return new TileEntityProgrammableStorage(); }
    @Override public IBlockState getStateFromMeta(int meta) {
        EnumFacing facing = EnumFacing.getFront(meta & 7);
        return getDefaultState().withProperty(FACING, facing.getAxis() == EnumFacing.Axis.Y ? EnumFacing.NORTH : facing);
    }
    @Override public IBlockState getStateForPlacement(World world, BlockPos pos, EnumFacing face,
            float hitX, float hitY, float hitZ, int meta, EntityLivingBase placer) {
        return getDefaultState().withProperty(FACING, placer.getHorizontalFacing().getOpposite());
    }
    @Override public boolean onBlockActivated(World world, BlockPos pos, IBlockState state, EntityPlayer player,
            EnumHand hand, EnumFacing face, float hitX, float hitY, float hitZ) {
        if (hand != EnumHand.MAIN_HAND) return false;
        if (player.isSneaking() && com.vandorlabs.items.ConfigurationAccess.canConfigure(player))
            return super.onBlockActivated(world, pos, state, player, hand, face, hitX, hitY, hitZ);
        if (!world.isRemote) player.openGui(VandorLabs.instance, GuiHandler.GUI_PROGRAMMABLE_STORAGE,
                world, pos.getX(), pos.getY(), pos.getZ());
        return true;
    }
    @Override public ItemStack createConfiguredDrop(TileEntity tile) {
        ItemStack stack = super.createConfiguredDrop(tile);
        if (stack.getSubCompound("BlockEntityTag") != null) stack.getSubCompound("BlockEntityTag").removeTag("Items");
        return stack;
    }
    @Override public void breakBlock(World world, BlockPos pos, IBlockState state) {
        TileEntity tile = world.getTileEntity(pos);
        if (!world.isRemote && tile instanceof TileEntityProgrammableStorage) {
            InventoryHelper.dropInventoryItems(world, pos, (TileEntityProgrammableStorage)tile);
            world.updateComparatorOutputLevel(pos, this);
        }
        super.breakBlock(world, pos, state);
    }
    @Override public boolean hasComparatorInputOverride(IBlockState state) { return true; }
    @Override public int getComparatorInputOverride(IBlockState state, World world, BlockPos pos) {
        TileEntity tile = world.getTileEntity(pos);
        return tile instanceof TileEntityProgrammableStorage ? Container.calcRedstoneFromInventory((TileEntityProgrammableStorage)tile) : 0;
    }
    @Override public void neighborChanged(IBlockState state, World world, BlockPos pos,
            net.minecraft.block.Block block, BlockPos from) { }
}
