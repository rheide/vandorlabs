package com.vandorlabs.blocks;

import com.vandorlabs.GuiHandler;
import com.vandorlabs.VandorLabs;
import com.vandorlabs.tiles.TileEntityProgrammableGlass;
import net.minecraft.block.properties.PropertyInteger;
import net.minecraft.block.state.BlockStateContainer;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.BlockRenderLayer;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.world.World;

/** One connected wall block with three detail sizes and a fixed metal frame. */
public final class BlockProgrammableGlass extends BlockGlassWall {
    public static final PropertyInteger SIZE = PropertyInteger.create("size", 0, 2);

    public BlockProgrammableGlass(String name) {
        super(name);
        setDefaultState(getDefaultState().withProperty(SIZE, 1));
    }
    @Override protected BlockStateContainer createBlockState() {
        return new BlockStateContainer(this, ROTATED, TOP, BOTTOM, LEFT, RIGHT,
                INNER_TL, INNER_TR, INNER_BL, INNER_BR, SIZE);
    }
    @Override public int getMetaFromState(IBlockState state) {
        return (state.getValue(ROTATED) ? 1 : 0) | (state.getValue(SIZE) << 1);
    }
    @Override public IBlockState getStateFromMeta(int meta) {
        int size = (meta >> 1) & 3;
        return getDefaultState().withProperty(ROTATED, (meta & 1) != 0)
                .withProperty(SIZE, size < 3 ? size : 1);
    }
    @Override public boolean hasTileEntity(IBlockState state) { return true; }
    @Override public TileEntity createTileEntity(World world, IBlockState state) {
        return new TileEntityProgrammableGlass();
    }
    @Override public BlockRenderLayer getBlockLayer() { return BlockRenderLayer.SOLID; }
    @Override public ItemStack getPickBlock(IBlockState state, RayTraceResult target,
            World world, BlockPos pos, EntityPlayer player) {
        ItemStack stack = new ItemStack(this);
        NBTTagCompound settings = new NBTTagCompound();
        settings.setInteger("Size", state.getValue(SIZE));
        TileEntity te = world.getTileEntity(pos);
        settings.setInteger("Shade", te instanceof TileEntityProgrammableGlass
                ? ((TileEntityProgrammableGlass) te).getShade() : 0);
        stack.setTagInfo("ProgrammableGlassSettings", settings);
        return stack;
    }
    @Override public void onBlockPlacedBy(World world, BlockPos pos, IBlockState state,
            EntityLivingBase placer, ItemStack stack) {
        super.onBlockPlacedBy(world, pos, state, placer, stack);
        if (world.isRemote || !stack.hasTagCompound()
                || !stack.getTagCompound().hasKey("ProgrammableGlassSettings", 10)) return;
        NBTTagCompound settings = stack.getTagCompound()
                .getCompoundTag("ProgrammableGlassSettings");
        int size = settings.getInteger("Size");
        int shade = settings.getInteger("Shade");
        if (size >= 0 && size <= 2) world.setBlockState(pos, state.withProperty(SIZE, size), 3);
        TileEntity te = world.getTileEntity(pos);
        if (te instanceof TileEntityProgrammableGlass)
            ((TileEntityProgrammableGlass)te).setShade(shade);
    }
    @Override public boolean onBlockActivated(World world, BlockPos pos, IBlockState state,
            EntityPlayer player, EnumHand hand, EnumFacing face, float x, float y, float z) {
        if (hand != EnumHand.MAIN_HAND) return true;
        if (!world.isRemote) player.openGui(VandorLabs.instance, GuiHandler.GUI_PROGRAMMABLE_GLASS,
                world, pos.getX(), pos.getY(), pos.getZ());
        return true;
    }
}
