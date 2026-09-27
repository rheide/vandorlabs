package com.vandorlabs.blocks;

import com.vandorlabs.tiles.TileEntityLandingGear;
import net.minecraft.block.Block;
import net.minecraft.block.properties.PropertyBool;
import net.minecraft.block.state.*;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.*;
import net.minecraft.util.math.*;
import net.minecraft.world.*;
import java.util.*;

/** Two-cell telescopic gear, extended by redstone or an ordinary right-click. */
public final class BlockTelescopicLandingGear extends BlockLandingGear {
    public static final PropertyBool LOWER = PropertyBool.create("lower");
    public static final PropertyBool EXTENDED = PropertyBool.create("extended");
    public BlockTelescopicLandingGear(String name) {
        super(name);
        setDefaultState(blockState.getBaseState().withProperty(FACING,EnumFacing.NORTH)
                .withProperty(LOWER,false).withProperty(EXTENDED,false));
    }
    protected BlockStateContainer createBlockState() { return new BlockStateContainer(this,FACING,LOWER,EXTENDED); }
    public IBlockState getStateFromMeta(int meta) {
        return getDefaultState().withProperty(FACING,EnumFacing.getHorizontal(meta & 3))
                .withProperty(LOWER,(meta & 4)!=0).withProperty(EXTENDED,(meta & 8)!=0);
    }
    public int getMetaFromState(IBlockState s) { return s.getValue(FACING).getHorizontalIndex() | (s.getValue(LOWER)?4:0) | (s.getValue(EXTENDED)?8:0); }
    public boolean hasTileEntity(IBlockState s) { return !s.getValue(LOWER); }
    public TileEntity createTileEntity(World world,IBlockState s) { return new TileEntityLandingGear(); }
    public void onBlockPlacedBy(World world,BlockPos pos,IBlockState s,EntityLivingBase placer,ItemStack stack) {
        if (!world.isRemote && world.isBlockPowered(pos)) setExtended(world,pos,true);
    }
    public void neighborChanged(IBlockState s,World world,BlockPos pos,Block block,BlockPos from) {
        if (world.isRemote) return;
        if (s.getValue(LOWER)) {
            if (world.getBlockState(pos.up()).getBlock()!=this) world.setBlockToAir(pos);
        } else {
            TileEntity raw=world.getTileEntity(pos);
            if (raw instanceof TileEntityLandingGear) {
                TileEntityLandingGear tile=(TileEntityLandingGear)raw;
                boolean powered=world.isBlockPowered(pos);
                if (powered!=tile.powered) { tile.powered=powered; setExtended(world,pos,powered); }
            }
        }
    }
    public boolean setExtended(World world,BlockPos pos,boolean extend) {
        IBlockState state=world.getBlockState(pos);
        if (state.getBlock()!=this || state.getValue(LOWER)) return false;
        if (extend) {
            if (pos.getY()<=0 || !world.isBlockLoaded(pos.down())) return false;
            IBlockState below=world.getBlockState(pos.down());
            if (!(below.getBlock()==this && below.getValue(LOWER))
                    && !below.getBlock().isReplaceable(world,pos.down())) return false;
            world.setBlockState(pos.down(),state.withProperty(LOWER,true).withProperty(EXTENDED,true),2);
        }
        world.setBlockState(pos,state.withProperty(EXTENDED,extend),3);
        return true;
    }
    public boolean onBlockActivated(World world,BlockPos pos,IBlockState state,EntityPlayer player,EnumHand hand,EnumFacing face,float x,float y,float z) {
        if (hand!=EnumHand.MAIN_HAND || player.isSneaking()) return false;
        if (state.getValue(LOWER)) { pos=pos.up();state=world.getBlockState(pos); }
        if (state.getBlock()!=this) return false;
        if (!world.isRemote) setExtended(world,pos,!state.getValue(EXTENDED));
        return true;
    }
    protected List<AxisAlignedBB> boxes(IBlockState state,IBlockAccess world,BlockPos pos) {
        boolean lower=state.getValue(LOWER);
        TileEntity raw=world.getTileEntity(lower?pos.up():pos);
        double t=raw instanceof TileEntityLandingGear ? ((TileEntityLandingGear)raw).progress : state.getValue(EXTENDED)?1:0;
        List<AxisAlignedBB> result=new ArrayList<>();
        for (int i=0;i<parts.size();i++) {
            AxisAlignedBB b=parts.get(i);
            // Authored fixed mount/sleeve start at y=12; piston is y=10..12.
            if (b.minY>=.75) { }
            else if (b.minY==.625 && b.maxY==.75) b=new AxisAlignedBB(b.minX,.625-t,b.minZ,b.maxX,b.maxY,b.maxZ);
            else b=b.offset(0,-t,0);
            if (lower) b=b.offset(0,1,0);
            if (b.maxY>0 && b.minY<1) result.add(new AxisAlignedBB(b.minX,Math.max(0,b.minY),b.minZ,b.maxX,Math.min(1,b.maxY),b.maxZ));
        }
        return result;
    }
    public void breakBlock(World world,BlockPos pos,IBlockState s) {
        BlockPos other=s.getValue(LOWER)?pos.up():pos.down();
        IBlockState otherState=world.getBlockState(other);
        boolean finishedRetraction = false;
        if (s.getValue(LOWER) && otherState.getBlock()==this && !otherState.getValue(EXTENDED)) {
            TileEntity raw=world.getTileEntity(other);
            finishedRetraction=raw instanceof TileEntityLandingGear && ((TileEntityLandingGear)raw).progress==0;
        }
        if (!finishedRetraction && otherState.getBlock()==this && otherState.getValue(LOWER)!=s.getValue(LOWER)) world.setBlockToAir(other);
        super.breakBlock(world,pos,s);
    }
    public int damageDropped(IBlockState s) { return 0; }
    public ItemStack getPickBlock(IBlockState s,RayTraceResult hit,World world,BlockPos pos,EntityPlayer player) { return new ItemStack(this); }
}
