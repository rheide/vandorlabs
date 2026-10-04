package com.vandorlabs.blocks;

import com.vandorlabs.entity.EntityChairSeat;
import net.minecraft.block.properties.PropertyEnum;
import net.minecraft.block.state.*;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.*;
import net.minecraft.util.math.*;
import net.minecraft.world.*;

/** Automatically joined seats. An invisible upper cell reserves the backrest. */
public final class BlockConnectedSeat extends BlockVandorDirectional {
    public enum Part implements IStringSerializable {
        SINGLE, LEFT, MIDDLE, RIGHT;
        public String getName() { return name().toLowerCase(java.util.Locale.ROOT); }
    }
    public static final PropertyEnum<Part> PART = CachedProperties.enumeration("part", Part.class);
    public static final net.minecraftforge.common.property.IUnlistedProperty<Integer> HEIGHT=ProgrammableHousingState.integer("seat_height");
    private final double height;
    public static com.vandorlabs.tiles.TileEntityConnectedSeat settings(IBlockAccess world,BlockPos pos) {
        IBlockState state=world.getBlockState(pos);
        if(state.getBlock() instanceof BlockConnectedSeat && state.getValue(BlockBridgeChair.UPPER))pos=pos.down();
        net.minecraft.tileentity.TileEntity tile=world.getTileEntity(pos);
        return tile instanceof com.vandorlabs.tiles.TileEntityConnectedSeat?(com.vandorlabs.tiles.TileEntityConnectedSeat)tile:null;
    }
    public static int offset(IBlockAccess world,BlockPos pos) {
        com.vandorlabs.tiles.TileEntityConnectedSeat tile=settings(world,pos);return tile==null?1:tile.getHeightOffsetPixels();
    }
    public double seatHeight(IBlockAccess world,BlockPos pos){return (height==1.5?9:8)/16D+offset(world,pos)/16D;}
    @Override public boolean hasTileEntity(IBlockState state){return !state.getValue(BlockBridgeChair.UPPER);}
    @Override public net.minecraft.tileentity.TileEntity createTileEntity(World world,IBlockState state){return new com.vandorlabs.tiles.TileEntityConnectedSeat();}
    @Override public IBlockState getExtendedState(IBlockState state,IBlockAccess world,BlockPos pos){
        return ((net.minecraftforge.common.property.IExtendedBlockState)state).withProperty(HEIGHT,offset(world,pos));
    }
    public BlockConnectedSeat(String name) {
        super(name);
        height = name.equals("luxury_seat") ? 1.5 : 1.25;
        setDefaultState(blockState.getBaseState().withProperty(FACING, EnumFacing.NORTH)
                .withProperty(BlockBridgeChair.UPPER, false).withProperty(PART, Part.SINGLE));
        setLightOpacity(0);
    }
    protected BlockStateContainer createBlockState() {
        return new net.minecraftforge.common.property.ExtendedBlockState(this,new net.minecraft.block.properties.IProperty[]{FACING,BlockBridgeChair.UPPER,PART},new net.minecraftforge.common.property.IUnlistedProperty[]{HEIGHT});
    }
    public IBlockState getStateFromMeta(int meta) {
        return getDefaultState().withProperty(FACING, EnumFacing.getHorizontal(meta & 3))
                .withProperty(BlockBridgeChair.UPPER, (meta & 4) != 0);
    }
    public int getMetaFromState(IBlockState state) {
        return state.getValue(FACING).getHorizontalIndex() | (state.getValue(BlockBridgeChair.UPPER) ? 4 : 0);
    }
    private boolean joins(IBlockAccess world, BlockPos pos, IBlockState state) {
        if (world instanceof World && !((World)world).isBlockLoaded(pos)) return false;
        IBlockState other = world.getBlockState(pos);
        if(other.getBlock()!=this || other.getValue(BlockBridgeChair.UPPER)||other.getValue(FACING)!=state.getValue(FACING))return false;
        com.vandorlabs.tiles.TileEntityConnectedSeat tile=settings(world,pos);
        return tile==null||tile.isJoin();
    }
    public IBlockState getActualState(IBlockState state, IBlockAccess world, BlockPos pos) {
        if (state.getValue(BlockBridgeChair.UPPER)) return state;
        com.vandorlabs.tiles.TileEntityConnectedSeat tile=settings(world,pos);
        if(tile!=null&&!tile.isJoin())return state.withProperty(PART,Part.SINGLE);
        EnumFacing right = state.getValue(FACING).rotateY();
        boolean leftJoin = joins(world, pos.offset(right.getOpposite()), state) && offset(world,pos)==offset(world,pos.offset(right.getOpposite()));
        boolean rightJoin = joins(world, pos.offset(right), state) && offset(world,pos)==offset(world,pos.offset(right));
        return state.withProperty(PART, leftJoin ? rightJoin ? Part.MIDDLE : Part.RIGHT
                : rightJoin ? Part.LEFT : Part.SINGLE);
    }
    public boolean canPlaceBlockAt(World world, BlockPos pos) {
        return super.canPlaceBlockAt(world, pos) && pos.getY() < world.getHeight() - 1
                && world.getBlockState(pos.up()).getBlock().isReplaceable(world, pos.up());
    }
    public void onBlockPlacedBy(World world, BlockPos pos, IBlockState state, EntityLivingBase placer, ItemStack stack) {
        if (!world.isRemote) world.setBlockState(pos.up(), state.withProperty(BlockBridgeChair.UPPER, true), 3);
    }
    public boolean isOpaqueCube(IBlockState state) { return false; }
    public boolean isFullCube(IBlockState state) { return false; }
    public AxisAlignedBB getBoundingBox(IBlockState state, IBlockAccess world, BlockPos pos) {
        return state.getValue(BlockBridgeChair.UPPER) ? new AxisAlignedBB(0,0,0,1,height-1+offset(world,pos)/16D,1) : FULL_BLOCK_AABB;
    }
    public void breakBlock(World world, BlockPos pos, IBlockState state) {
        BlockPos lower = state.getValue(BlockBridgeChair.UPPER) ? pos.down() : pos;
        for (EntityChairSeat seat : world.getEntitiesWithinAABB(EntityChairSeat.class, new AxisAlignedBB(lower).grow(.25,1,.25)))
            if (lower.equals(seat.getChairPos())) { seat.removePassengers(); seat.setDead(); }
        BlockPos other = state.getValue(BlockBridgeChair.UPPER) ? pos.down() : pos.up();
        if (world.getBlockState(other).getBlock() == this) world.setBlockToAir(other);
        super.breakBlock(world,pos,state);
    }
    public boolean onBlockActivated(World world, BlockPos pos, IBlockState state, EntityPlayer player,
            EnumHand hand, EnumFacing face, float x, float y, float z) {
        if(hand==EnumHand.MAIN_HAND && player.isSneaking() && player.capabilities.isCreativeMode) {
            BlockPos lower=state.getValue(BlockBridgeChair.UPPER)?pos.down():pos;
            if(!world.isRemote)player.openGui(com.vandorlabs.VandorLabs.instance,com.vandorlabs.GuiHandler.GUI_PROGRAMMABLE_CHAIR,world,lower.getX(),lower.getY(),lower.getZ());
            return true;
        }
        if (player.isSneaking() || hand != EnumHand.MAIN_HAND) return false;
        if (world.isRemote) return true;
        BlockPos lower = state.getValue(BlockBridgeChair.UPPER) ? pos.down() : pos;
        for (EntityChairSeat seat : world.getEntitiesWithinAABB(EntityChairSeat.class, new AxisAlignedBB(lower).grow(.25,1,.25))) {
            if (seat.isDead || !lower.equals(seat.getChairPos())) continue;
            if (seat.getPassengers().isEmpty()) player.startRiding(seat, true);
            return true;
        }
        EntityChairSeat seat = new EntityChairSeat(world, lower, seatHeight(world,lower));
        seat.rotationYaw = state.getValue(FACING).getHorizontalAngle();
        if (world.spawnEntity(seat)) player.startRiding(seat, true);
        return true;
    }
    private ItemStack configured(net.minecraft.tileentity.TileEntity tile) {
        ItemStack stack=new ItemStack(this);
        if(tile!=null){net.minecraft.nbt.NBTTagCompound tag=tile.writeToNBT(new net.minecraft.nbt.NBTTagCompound());
            for(String key:new String[]{"id","x","y","z"})tag.removeTag(key);
            stack.setTagInfo("BlockEntityTag",tag);}
        return stack;
    }
    @Override public void getDrops(NonNullList<ItemStack> drops,IBlockAccess world,BlockPos pos,IBlockState state,int fortune){drops.add(configured(settings(world,pos)));}
    @Override public boolean removedByPlayer(IBlockState state,World world,BlockPos pos,EntityPlayer player,boolean willHarvest){
        // Keep both cells and the lower settings tile until harvest has read the drop.
        return willHarvest || super.removedByPlayer(state,world,pos,player,false);
    }
    @Override public void harvestBlock(World world,EntityPlayer player,BlockPos pos,IBlockState state,
            net.minecraft.tileentity.TileEntity tile,ItemStack tool){
        super.harvestBlock(world,player,pos,state,tile,tool);world.setBlockToAir(pos);
    }
    public int damageDropped(IBlockState state) { return 0; }
    public ItemStack getPickBlock(IBlockState state, RayTraceResult hit, World world, BlockPos pos, EntityPlayer player) {
        return configured(settings(world,pos));
    }
}
