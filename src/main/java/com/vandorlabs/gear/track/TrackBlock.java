package com.vandorlabs.gear.track;

import com.vandorlabs.gear.track.core.TrackTopology;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.block.Block;
import net.minecraft.block.ITileEntityProvider;
import net.minecraft.block.SoundType;
import net.minecraft.block.material.Material;
import net.minecraft.block.properties.IProperty;
import net.minecraft.block.properties.PropertyDirection;
import net.minecraft.block.state.BlockFaceShape;
import net.minecraft.block.state.BlockStateContainer;
import net.minecraft.block.state.IBlockState;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.BlockRenderLayer;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import net.minecraftforge.common.property.ExtendedBlockState;
import net.minecraftforge.common.property.IExtendedBlockState;
import net.minecraftforge.common.property.IUnlistedProperty;

public final class TrackBlock extends com.vandorlabs.gear.GearBlock {
    private final TrackTopology.Style style;

    public TrackBlock(String name, TrackTopology.Style style) {
        super(name,Kind.TRACK);
        this.style = style;
        setUnlocalizedName(com.vandorlabs.VandorLabs.MODID + "." + name);
        setCreativeTab(com.vandorlabs.VandorLabs.VANDOR_LABS_TAB);
        setSoundType(SoundType.METAL);
        setHardness(2.5F);
        setResistance(8.0F);
        setLightOpacity(0);
        setDefaultState(blockState.getBaseState().withProperty(FACING, EnumFacing.NORTH).withProperty(ASSEMBLED,false));
    }

    public TrackTopology.Style style() { return style; }

    @Override protected BlockStateContainer createBlockState() {
        return new ExtendedBlockState(this, new IProperty<?>[] { FACING, ASSEMBLED }, new IUnlistedProperty<?>[] { TrackAppearance.PROPERTY });
    }

    @Override public IBlockState getStateFromMeta(int meta) { return super.getStateFromMeta(meta); }
    @Override public int getMetaFromState(IBlockState state) { return super.getMetaFromState(state); }
    @Override public int damageDropped(IBlockState state) { return 0; }

    @Override
    public IBlockState getStateForPlacement(World world, BlockPos pos, EnumFacing side, float hitX,
                                            float hitY, float hitZ, int meta, EntityLivingBase placer, EnumHand hand) {
        return getDefaultState().withProperty(FACING, placer.getHorizontalFacing().getOpposite());
    }

    @Override public boolean hasTileEntity(IBlockState state) { return true; }
    @Override public TileEntity createNewTileEntity(World world, int meta) { return new TrackTile(); }
    @Override public boolean isOpaqueCube(IBlockState state) { return false; }
    @Override public boolean isFullCube(IBlockState state) { return false; }
    @Override public BlockRenderLayer getBlockLayer() { return BlockRenderLayer.CUTOUT; }
    @Override public BlockFaceShape getBlockFaceShape(IBlockAccess world, IBlockState state, BlockPos pos, EnumFacing face) { return BlockFaceShape.UNDEFINED; }

    @Override
    public IBlockState getExtendedState(IBlockState state, IBlockAccess world, BlockPos pos) {
        return ((IExtendedBlockState) state).withProperty(TrackAppearance.PROPERTY, appearance(world, pos));
    }

    public static TrackAppearance appearance(IBlockAccess world, BlockPos pos) {
        TileEntity tile = world.getTileEntity(pos);
        return tile instanceof TrackTile ? ((TrackTile) tile).appearance() : TrackAppearance.SINGLE;
    }

    @Override public boolean onBlockActivated(World world,BlockPos pos,IBlockState state,EntityPlayer player,EnumHand hand,EnumFacing side,float x,float y,float z){
        if(hand!=EnumHand.MAIN_HAND)return false;
        if(player.isSneaking()||player.getHeldItem(hand).getItem()==com.vandorlabs.items.ModItems.CONFIGURIZER){
            if(!world.isRemote&&com.vandorlabs.items.ConfigurationAccess.canEditDialog(player,world,pos))player.openGui(com.vandorlabs.VandorLabs.instance,com.vandorlabs.GuiHandler.GUI_MODULAR_GEAR,world,pos.getX(),pos.getY(),pos.getZ());return true;
        }
        return super.onBlockActivated(world,pos,state,player,hand,side,x,y,z);
    }

    @Override public void onBlockAdded(World world, BlockPos pos, IBlockState state) { TrackWorld.requestAround(world, pos); }
    @Override public void onBlockPlacedBy(World world, BlockPos pos, IBlockState state, EntityLivingBase placer, ItemStack stack) { super.onBlockPlacedBy(world,pos,state,placer,stack);TrackWorld.requestAround(world, pos); }
    @Override public void neighborChanged(IBlockState state, World world, BlockPos pos, Block block, BlockPos fromPos) { super.neighborChanged(state,world,pos,block,fromPos);TrackWorld.requestAround(world, pos); }

    @Override
    public void breakBlock(World world, BlockPos pos, IBlockState state) {
        TileEntity tile = world.getTileEntity(pos);
        if (!world.isRemote && tile instanceof TrackTile) TrackWorld.releaseCachedGroup(world, (TrackTile) tile);
        super.breakBlock(world, pos, state);
        TrackWorld.requestAround(world, pos);
    }

    @Override
    public AxisAlignedBB getBoundingBox(IBlockState state, IBlockAccess world, BlockPos pos) {
        double height = appearance(world, pos).mount == TrackAppearance.Mount.TOP_HIGH ? 1.5 : 1;
        return new AxisAlignedBB(0, 0, 0, 1, height, 1);
    }

    @Override @Nullable
    public AxisAlignedBB getCollisionBoundingBox(IBlockState state, IBlockAccess world, BlockPos pos) {
        return getBoundingBox(state, world, pos);
    }

    @Override
    public void addCollisionBoxToList(IBlockState state, World world, BlockPos pos, AxisAlignedBB entityBox,
                                     List<AxisAlignedBB> boxes, @Nullable Entity entity, boolean actualState) {
        if(com.vandorlabs.gear.GearAssembly.isFoot(state)){TileEntity tile=world.getTileEntity(pos);if(tile instanceof com.vandorlabs.gear.GearTileEntity&&((com.vandorlabs.gear.GearTileEntity)tile).hasLoadedController()){super.addCollisionBoxToList(state,world,pos,entityBox,boxes,entity,actualState);return;}}
        // Static construction collision is intentionally conservative, not a wheel/triangle simulation.
        addCollisionBoxToList(pos, entityBox, boxes, new AxisAlignedBB(0, 0, 0, 1, 14.0 / 16, 1));
        TrackAppearance a = appearance(world, pos);
        if (a.mount != TrackAppearance.Mount.NONE) {
            addCollisionBoxToList(pos, entityBox, boxes, new AxisAlignedBB(4.0 / 16, 14.0 / 16, 4.0 / 16,
                    12.0 / 16, a.mount == TrackAppearance.Mount.TOP_HIGH ? 1.5 : 1, 12.0 / 16));
        }
        if ((a.sideMask & 1) != 0) addCollisionBoxToList(pos, entityBox, boxes, new AxisAlignedBB(5.0 / 16, 13.0 / 16, 0, 11.0 / 16, 15.0 / 16, 0.5));
        if ((a.sideMask & 2) != 0) addCollisionBoxToList(pos, entityBox, boxes, new AxisAlignedBB(0.5, 13.0 / 16, 5.0 / 16, 1, 15.0 / 16, 11.0 / 16));
        if ((a.sideMask & 4) != 0) addCollisionBoxToList(pos, entityBox, boxes, new AxisAlignedBB(5.0 / 16, 13.0 / 16, 0.5, 11.0 / 16, 15.0 / 16, 1));
        if ((a.sideMask & 8) != 0) addCollisionBoxToList(pos, entityBox, boxes, new AxisAlignedBB(0, 13.0 / 16, 5.0 / 16, 0.5, 15.0 / 16, 11.0 / 16));
    }
}
