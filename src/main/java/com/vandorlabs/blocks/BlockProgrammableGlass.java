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
import net.minecraft.world.IBlockAccess;

/** One connected wall block with two detail sizes, legacy metadata support and a configurable metal frame. */
public final class BlockProgrammableGlass extends BlockGlassWall {
    public static final PropertyInteger SIZE = PropertyInteger.create("size", 0, 2);

    public BlockProgrammableGlass(String name) {
        super(name);
        setDefaultState(getDefaultState().withProperty(SIZE, 1));
    }
    @Override protected BlockStateContainer createBlockState() {
        return new BlockStateContainer(this, ROTATED, DEPTH, TOP, BOTTOM, LEFT, RIGHT,
                INNER_TL, INNER_TR, INNER_BL, INNER_BR, SIZE);
    }
    @Override public int getMetaFromState(IBlockState state) {
        int rotated = state.getValue(ROTATED) ? 1 : 0;
        int depth = state.getValue(DEPTH);
        // Metadata 0..5 belongs to existing worlds and retains its old size.
        // Offset panes use the tile for size, since 3 x 3 x 2 exceeds 16 states.
        return depth == 0 ? rotated | (state.getValue(SIZE) << 1)
                : 4 + depth * 2 + rotated;
    }
    @Override public IBlockState getStateFromMeta(int meta) {
        int size = meta < 6 ? (meta >> 1) & 3 : 1;
        int depth = meta >= 6 && meta < 10 ? (meta - 4) / 2 : 0;
        return getDefaultState().withProperty(ROTATED, (meta & 1) != 0)
                .withProperty(SIZE, size < 3 ? Math.min(size,1) : 1)
                .withProperty(DEPTH, depth);
    }
    @Override public IBlockState getActualState(IBlockState state,
            net.minecraft.world.IBlockAccess world, BlockPos pos) {
        IBlockState actual = super.getActualState(state, world, pos);
        TileEntity tile = world.getTileEntity(pos);
        if(PanelJoins.flatCorner(state,world,pos)!=null || tile instanceof TileEntityProgrammableGlass && (!((TileEntityProgrammableGlass)tile).isFramed() || ((TileEntityProgrammableGlass)tile).frameAppearance.front>=0 || ((TileEntityProgrammableGlass)tile).frameAppearance.interior>=0))
            actual=actual.withProperty(TOP,false).withProperty(BOTTOM,false).withProperty(LEFT,false).withProperty(RIGHT,false)
                    .withProperty(INNER_TL,false).withProperty(INNER_TR,false).withProperty(INNER_BL,false).withProperty(INNER_BR,false);
        return tile instanceof TileEntityProgrammableGlass
                ? actual.withProperty(SIZE, ((TileEntityProgrammableGlass) tile).getSize())
                : actual.withProperty(SIZE,Math.min(actual.getValue(SIZE),1));
    }
    @Override protected boolean joinsAt(IBlockAccess world, BlockPos pos) {
        TileEntity tile = world.getTileEntity(pos);
        return !(tile instanceof TileEntityProgrammableGlass)
                || ((TileEntityProgrammableGlass) tile).isJoin();
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
        TileEntity te = world.getTileEntity(pos);
        settings.setInteger("Size", te instanceof TileEntityProgrammableGlass
                ? ((TileEntityProgrammableGlass) te).getSize() : state.getValue(SIZE));
        settings.setInteger("Shade", te instanceof TileEntityProgrammableGlass
                ? ((TileEntityProgrammableGlass) te).getShade() : 0);
        settings.setBoolean("Join", !(te instanceof TileEntityProgrammableGlass)
                || ((TileEntityProgrammableGlass) te).isJoin());
        settings.setInteger("Tint",te instanceof TileEntityProgrammableGlass?((TileEntityProgrammableGlass)te).getTint():1);
        settings.setInteger("Material",te instanceof TileEntityProgrammableGlass?((TileEntityProgrammableGlass)te).getMaterial():-1);
        settings.setBoolean("Stretch",te instanceof TileEntityProgrammableGlass && ((TileEntityProgrammableGlass)te).isStretch());settings.setInteger("LayoutMode",te instanceof TileEntityProgrammableGlass?((TileEntityProgrammableGlass)te).getTextureLayout():1);
        settings.setBoolean("Framed",!(te instanceof TileEntityProgrammableGlass)||((TileEntityProgrammableGlass)te).isFramed());
        if(te instanceof TileEntityProgrammableGlass)((TileEntityProgrammableGlass)te).frameAppearance.write(settings);
        stack.setTagInfo("ProgrammableGlassSettings", settings);
        return stack;
    }
    @Override public void onBlockPlacedBy(World world, BlockPos pos, IBlockState state,
            EntityLivingBase placer, ItemStack stack) {
        super.onBlockPlacedBy(world, pos, state, placer, stack);
        if (world.isRemote) return;
        NBTTagCompound settings = stack.hasTagCompound()
                && stack.getTagCompound().hasKey("ProgrammableGlassSettings", 10)
                ? stack.getTagCompound().getCompoundTag("ProgrammableGlassSettings") : null;
        int size = settings == null ? state.getValue(SIZE) : settings.getInteger("Size");
        int shade = settings == null ? 0 : settings.getInteger("Shade");
        boolean join = settings == null || !settings.hasKey("Join")
                || settings.getBoolean("Join");
        if (size >= 0 && size <= 2 && state.getValue(DEPTH) == 0)
            world.setBlockState(pos, state.withProperty(SIZE, size), 3);
        TileEntity te = world.getTileEntity(pos);
        if (te instanceof TileEntityProgrammableGlass) {
            if(settings!=null)((TileEntityProgrammableGlass)te).frameAppearance.read(settings);
            ((TileEntityProgrammableGlass)te).setPanelFacing(placer.getHorizontalFacing().getOpposite());
            ((TileEntityProgrammableGlass)te).setSize(size);
            ((TileEntityProgrammableGlass)te).setShade(shade);
            ((TileEntityProgrammableGlass)te).setMaterial(settings!=null && settings.hasKey("Material",3)?settings.getInteger("Material"):-1);
            ((TileEntityProgrammableGlass)te).setTextureLayout(settings!=null && settings.hasKey("LayoutMode",3)?settings.getInteger("LayoutMode"):settings!=null && settings.getBoolean("Stretch")?0:1);
            ((TileEntityProgrammableGlass)te).setJoin(join);
            ((TileEntityProgrammableGlass)te).setAppearance(settings==null||!settings.hasKey("Tint",3)?1:settings.getInteger("Tint"),settings==null||!settings.hasKey("Framed")||settings.getBoolean("Framed"));
        }
    }
    @Override public boolean onBlockActivated(World world, BlockPos pos, IBlockState state,
            EntityPlayer player, EnumHand hand, EnumFacing face, float x, float y, float z) {
        if (hand != EnumHand.MAIN_HAND || !player.isSneaking()
                || !player.capabilities.isCreativeMode) return false;
        if (!world.isRemote) player.openGui(VandorLabs.instance, GuiHandler.GUI_PROGRAMMABLE_GLASS,
                world, pos.getX(), pos.getY(), pos.getZ());
        return true;
    }
    @Override public Boolean isAABBInsideMaterial(World world,BlockPos pos,net.minecraft.util.math.AxisAlignedBB box,net.minecraft.block.material.Material material){return WaterloggedParts.intersects(world,pos,box,material);}
    @Override public Boolean isAABBInsideLiquid(World world,BlockPos pos,net.minecraft.util.math.AxisAlignedBB box){return WaterloggedParts.liquid(world,pos,box);}
    @net.minecraftforge.fml.relauncher.SideOnly(net.minecraftforge.fml.relauncher.Side.CLIENT)
    @Override public net.minecraft.util.math.Vec3d getFogColor(net.minecraft.world.World world,BlockPos pos,IBlockState state,net.minecraft.entity.Entity entity,net.minecraft.util.math.Vec3d original,float partialTicks){return WaterloggedParts.fogColor(world,pos,entity,original,partialTicks);}
    @Override public IBlockState getStateAtViewpoint(IBlockState state,net.minecraft.world.IBlockAccess world,BlockPos pos,net.minecraft.util.math.Vec3d point){return WaterloggedParts.viewpoint(state,world,pos,point);}
    @Override public Boolean isEntityInsideMaterial(IBlockAccess world,BlockPos pos,IBlockState state,net.minecraft.entity.Entity entity,double height,net.minecraft.block.material.Material material,boolean head){return WaterloggedParts.entity(world,pos,entity,height,material,head);}
    @Override public int getLightValue(IBlockState state,IBlockAccess world,BlockPos pos){TileEntity tile=world==null?null:world.getTileEntity(pos);return Math.max(WaterloggedParts.light(world,pos),tile instanceof TileEntityProgrammableGlass&&((TileEntityProgrammableGlass)tile).isFramed()?((TileEntityProgrammableGlass)tile).frameAppearance.light():0);}
}