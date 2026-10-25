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
        return new ExtendedBlockState(this, new IProperty<?>[] { FACING, ASSEMBLED }, new IUnlistedProperty<?>[] { TrackAppearance.PROPERTY,TrackAppearance.STYLE });
    }

    @Override public IBlockState getStateFromMeta(int meta) { return super.getStateFromMeta(meta); }
    @Override public int getMetaFromState(IBlockState state) { return super.getMetaFromState(state); }
    @Override public net.minecraft.item.Item getItemDropped(IBlockState state,java.util.Random random,int fortune){return net.minecraft.item.Item.getItemFromBlock(TrackRegistry.COMPACT);}
    @Override public ItemStack getItem(World world,BlockPos pos,IBlockState state){return new ItemStack(TrackRegistry.COMPACT);}
    private ItemStack configuredItem(TileEntity raw){ItemStack stack=new ItemStack(TrackRegistry.COMPACT);if(raw instanceof TrackTile){TrackTile track=(TrackTile)raw;com.vandorlabs.gear.GearTileEntity root=track.loadedController();net.minecraft.nbt.NBTTagCompound settings=(root==null?track:root).settings();settings.setInteger("TrackStyle",track.style().ordinal());stack.setTagInfo("ModularGearSettings",settings);}return stack;}
    @Override public ItemStack getPickBlock(IBlockState state,net.minecraft.util.math.RayTraceResult hit,World world,BlockPos pos,EntityPlayer actor){return configuredItem(world.getTileEntity(pos));}
    @Override public void getDrops(net.minecraft.util.NonNullList<ItemStack> drops,IBlockAccess world,BlockPos pos,IBlockState state,int fortune){TileEntity tile=world.getTileEntity(pos);if(tile instanceof TrackTile&&((TrackTile)tile).hasRetainedPayload())return;drops.add(configuredItem(tile));}
    @Override public void harvestBlock(World world,EntityPlayer actor,BlockPos pos,IBlockState state,TileEntity tile,ItemStack tool){if(tile instanceof TrackTile&&((TrackTile)tile).recoveryDropped())return;net.minecraft.stats.StatBase stat=net.minecraft.stats.StatList.getBlockStats(this);if(stat!=null)actor.addStat(stat);actor.addExhaustion(.005F);spawnAsEntity(world,pos,configuredItem(tile));}
    @Override public int damageDropped(IBlockState state) { return 0; }

    @Override
    public IBlockState getStateForPlacement(World world, BlockPos pos, EnumFacing side, float hitX,
                                            float hitY, float hitZ, int meta, EntityLivingBase placer, EnumHand hand) {
        return getStateForPlacement(world,pos,side,hitX,hitY,hitZ,meta,placer);
    }

    @Override public IBlockState getStateForPlacement(World world,BlockPos pos,EnumFacing side,float x,float y,float z,int meta,EntityLivingBase placer){IBlockState clicked=world.getBlockState(pos.offset(side.getOpposite()));EnumFacing facing=clicked.getBlock() instanceof TrackBlock||com.vandorlabs.gear.GearAssembly.isExtender(clicked)?clicked.getValue(FACING):side.getAxis().isHorizontal()?side.rotateY():placer.getHorizontalFacing().getOpposite();return getDefaultState().withProperty(FACING,facing);}
    public static TrackTopology.Style style(IBlockAccess world,BlockPos pos){TileEntity raw=world.getTileEntity(pos);return raw instanceof TrackTile?((TrackTile)raw).style():world.getBlockState(pos).getBlock() instanceof TrackBlock?((TrackBlock)world.getBlockState(pos).getBlock()).style():TrackTopology.Style.COMPACT;}
    @Override public boolean hasTileEntity(IBlockState state) { return true; }
    @Override public TileEntity createNewTileEntity(World world, int meta) { return new TrackTile(); }
    @Override public boolean isOpaqueCube(IBlockState state) { return false; }
    @Override public boolean isFullCube(IBlockState state) { return false; }
    @Override public BlockRenderLayer getBlockLayer() { return BlockRenderLayer.CUTOUT; }
    @Override public BlockFaceShape getBlockFaceShape(IBlockAccess world, IBlockState state, BlockPos pos, EnumFacing face) { return BlockFaceShape.UNDEFINED; }

    @Override
    public IBlockState getExtendedState(IBlockState state, IBlockAccess world, BlockPos pos) {
        // Carried tracks use the live gear renderer so tread travel never rebuilds the hull VBO.
        if(world instanceof com.vandorlabs.vehicle.VehicleWorld&&((com.vandorlabs.vehicle.VehicleWorld)world).craft!=null)state=state.withProperty(ASSEMBLED,true);
        return ((IExtendedBlockState) state).withProperty(TrackAppearance.PROPERTY, appearance(world, pos)).withProperty(TrackAppearance.STYLE,style(world,pos).ordinal());
    }

    public static TrackAppearance appearance(IBlockAccess world, BlockPos pos) {
        TileEntity tile = world.getTileEntity(pos);
        return tile instanceof TrackTile ? ((TrackTile) tile).appearance() : TrackAppearance.SINGLE;
    }

    @Override public boolean onBlockActivated(World world,BlockPos pos,IBlockState state,EntityPlayer player,EnumHand hand,EnumFacing side,float x,float y,float z){
        if(hand!=EnumHand.MAIN_HAND)return false;
        if(player.getHeldItem(hand).isEmpty()||player.isSneaking()||player.getHeldItem(hand).getItem()==com.vandorlabs.items.ModItems.CONFIGURIZER){
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
        if(tile instanceof TrackTile)((TrackTile)tile).rememberStyle();
        if (!world.isRemote && tile instanceof TrackTile) {
            com.vandorlabs.gear.GearTileEntity root=((TrackTile)tile).loadedController();
            TrackWorld.releaseCachedGroup(world,(TrackTile)tile);
            // The world already contains the replacement state here. Rescan a
            // stationary root before generic part removal can reset its pose.
            if(root!=null&&root!=tile&&!root.isInvalid())root.rebuild();
        }
        super.breakBlock(world, pos, state);
        TrackWorld.requestAround(world, pos);
    }

    @Override
    public AxisAlignedBB getBoundingBox(IBlockState state, IBlockAccess world, BlockPos pos) {
        return TrackGeometry.bounds(style(world,pos),state.getValue(FACING),appearance(world,pos));
    }

    @Override @Nullable
    public AxisAlignedBB getCollisionBoundingBox(IBlockState state, IBlockAccess world, BlockPos pos) {
        TileEntity raw=world.getTileEntity(pos);com.vandorlabs.gear.GearTileEntity controller=raw instanceof TrackTile?((TrackTile)raw).loadedController():null;return controller!=null&&controller.isOperational()?NULL_AABB:getBoundingBox(state,world,pos);
    }

    @Override
    public void addCollisionBoxToList(IBlockState state, World world, BlockPos pos, AxisAlignedBB entityBox,
                                     List<AxisAlignedBB> boxes, @Nullable Entity entity, boolean actualState) {
        if(com.vandorlabs.gear.GearAssembly.isFoot(state)){TileEntity tile=world.getTileEntity(pos);if(tile instanceof com.vandorlabs.gear.GearTileEntity&&((com.vandorlabs.gear.GearTileEntity)tile).hasLoadedController()){super.addCollisionBoxToList(state,world,pos,entityBox,boxes,entity,actualState);return;}}
        for(AxisAlignedBB box:TrackGeometry.boxes(style(world,pos),state.getValue(FACING),appearance(world,pos)))addCollisionBoxToList(pos,entityBox,boxes,box);
    }
    @Override public net.minecraft.util.math.RayTraceResult collisionRayTrace(IBlockState state,World world,BlockPos pos,net.minecraft.util.math.Vec3d start,net.minecraft.util.math.Vec3d end){
        TileEntity raw=world.getTileEntity(pos);if(raw instanceof TrackTile&&((TrackTile)raw).hasLoadedController())return super.collisionRayTrace(state,world,pos,start,end);
        net.minecraft.util.math.RayTraceResult nearest=null;for(AxisAlignedBB box:TrackGeometry.boxes(style(world,pos),state.getValue(FACING),appearance(world,pos))){net.minecraft.util.math.RayTraceResult hit=box.offset(pos).calculateIntercept(start,end);if(hit!=null&&(nearest==null||start.squareDistanceTo(hit.hitVec)<start.squareDistanceTo(nearest.hitVec)))nearest=new net.minecraft.util.math.RayTraceResult(hit.hitVec,hit.sideHit,pos);}return nearest;
    }
}
