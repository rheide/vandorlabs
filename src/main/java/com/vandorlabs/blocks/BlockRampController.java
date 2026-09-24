package com.vandorlabs.blocks;

import com.vandorlabs.GuiHandler;
import com.vandorlabs.VandorLabs;
import com.vandorlabs.tiles.TileEntityRampController;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/** Its facing is the scan direction, away from the player on placement. */
public class BlockRampController extends BlockVandorDirectional {
    public static final net.minecraft.block.properties.PropertyBool ACTIVE=net.minecraft.block.properties.PropertyBool.create("active");
    public BlockRampController() {
        super("programmable_ramp");
        setDefaultState(blockState.getBaseState().withProperty(FACING,EnumFacing.NORTH).withProperty(ACTIVE,false));
    }
    @Override protected net.minecraft.block.state.BlockStateContainer createBlockState() {
        return new net.minecraft.block.state.BlockStateContainer(this,FACING,ACTIVE);
    }
    @Override public IBlockState getStateFromMeta(int meta) {
        return getDefaultState().withProperty(FACING,EnumFacing.getHorizontal(meta&3)).withProperty(ACTIVE,(meta&4)!=0);
    }
    @Override public int getMetaFromState(IBlockState state) {
        return state.getValue(FACING).getHorizontalIndex() | (state.getValue(ACTIVE)?4:0);
    }
    @Override public boolean hasTileEntity(IBlockState state) { return true; }
    @Override public TileEntity createTileEntity(World world, IBlockState state) { return new TileEntityRampController(); }
    @Override public IBlockState getStateForPlacement(World world, BlockPos pos, EnumFacing side,
            float hitX, float hitY, float hitZ, int meta, net.minecraft.entity.EntityLivingBase placer) {
        return getDefaultState().withProperty(FACING,placer.getHorizontalFacing());
    }
    @Override public boolean onBlockActivated(World world, BlockPos pos, IBlockState state, EntityPlayer player,
            EnumHand hand, EnumFacing side, float hitX, float hitY, float hitZ) {
        if (!world.isRemote && hand==EnumHand.MAIN_HAND) player.openGui(VandorLabs.instance,
                GuiHandler.GUI_RAMP_CONTROLLER,world,pos.getX(),pos.getY(),pos.getZ());
        return true;
    }
    @Override public void neighborChanged(IBlockState state, World world, BlockPos pos, Block block, BlockPos fromPos) {
        TileEntity te=world.getTileEntity(pos);
        if (te instanceof TileEntityRampController) ((TileEntityRampController)te).updatePower();
    }
    @Override public void breakBlock(World world, BlockPos pos, IBlockState state) {
        TileEntity te=world.getTileEntity(pos);
        if (!world.isRemote && te instanceof TileEntityRampController) ((TileEntityRampController)te).recover(true);
        super.breakBlock(world,pos,state);
    }
    @Override public void onBlockPlacedBy(World world,BlockPos pos,IBlockState state,
            net.minecraft.entity.EntityLivingBase placer,net.minecraft.item.ItemStack stack) {
        TileEntity te=world.getTileEntity(pos);
        if (!world.isRemote && te instanceof TileEntityRampController) {
            if (placer instanceof EntityPlayer) ((TileEntityRampController)te).setOwner((EntityPlayer)placer);
            ((TileEntityRampController)te).updatePower();
        }
    }
    @Override public void updateTick(World world,BlockPos pos,IBlockState state,java.util.Random random) {
        TileEntity te=world.getTileEntity(pos);
        if (te instanceof TileEntityRampController) ((TileEntityRampController)te).scheduledTick();
    }
}
