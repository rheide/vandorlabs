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

/** A mount and up to four owned cells reserved for its moving piston and wheel. */
public final class BlockTelescopicLandingGear extends BlockLandingGear {
    public static final PropertyBool LOWER=PropertyBool.create("lower"),EXTENDED=PropertyBool.create("extended");
    public BlockTelescopicLandingGear(String name){
        super(name);setDefaultState(blockState.getBaseState().withProperty(FACING,EnumFacing.NORTH).withProperty(LOWER,false).withProperty(EXTENDED,false));
    }
    protected BlockStateContainer createBlockState(){return new BlockStateContainer(this,FACING,LOWER,EXTENDED);}
    public IBlockState getStateFromMeta(int meta){return getDefaultState().withProperty(FACING,EnumFacing.getHorizontal(meta&3)).withProperty(LOWER,(meta&4)!=0).withProperty(EXTENDED,(meta&8)!=0);}
    public int getMetaFromState(IBlockState s){return s.getValue(FACING).getHorizontalIndex()|(s.getValue(LOWER)?4:0)|(s.getValue(EXTENDED)?8:0);}
    public boolean hasTileEntity(IBlockState s){return true;}
    public TileEntity createTileEntity(World world,IBlockState s){return new TileEntityLandingGear();}
    public TileEntityLandingGear root(IBlockAccess world,BlockPos pos){
        TileEntity raw=world.getTileEntity(pos);if(!(raw instanceof TileEntityLandingGear))return null;
        TileEntityLandingGear tile=(TileEntityLandingGear)raw;
        if(tile.owner()==null)return world.getBlockState(pos).getBlock()==this && !world.getBlockState(pos).getValue(LOWER)?tile:null;
        BlockPos owner=tile.owner();if(owner==null || owner.getX()!=pos.getX()||owner.getZ()!=pos.getZ()
                ||owner.getY()<=pos.getY()||owner.getY()-pos.getY()>4
                ||world instanceof World&&!((World)world).isBlockLoaded(owner))return null;
        IBlockState state=world.getBlockState(owner);
        if(state.getBlock()!=this||state.getValue(LOWER))return null;
        raw=world.getTileEntity(owner);return raw instanceof TileEntityLandingGear?(TileEntityLandingGear)raw:null;
    }
    private boolean owned(World world,BlockPos pos,BlockPos root){
        if(!world.isBlockLoaded(pos))return false;
        TileEntity raw=world.getTileEntity(pos);
        return world.getBlockState(pos).getBlock()==this && raw instanceof TileEntityLandingGear && root.equals(((TileEntityLandingGear)raw).owner());
    }
    public boolean reserve(World world,BlockPos root,float distance){
        int count=(int)Math.ceil(distance);if(count>4||root.getY()<count)return false;
        for(int i=1;i<=count;i++){
            BlockPos p=root.down(i);if(!world.isBlockLoaded(p))return false;
            if(!owned(world,p,root)&&!world.isAirBlock(p))return false;
        }
        for(int i=1;i<=count;i++){
            BlockPos p=root.down(i);if(owned(world,p,root))continue;
            world.setBlockState(p,world.getBlockState(root).withProperty(LOWER,true).withProperty(EXTENDED,true),2);
            TileEntity raw=world.getTileEntity(p);if(raw instanceof TileEntityLandingGear)((TileEntityLandingGear)raw).setOwner(root);
        }
        return true;
    }
    public void releaseBelow(World world,BlockPos root,int keep){
        for(int i=keep+1;i<=4;i++){
            BlockPos p=root.down(i);if(owned(world,p,root)){
                // Removing ownership first distinguishes internal cleanup from mining a wheel.
                world.removeTileEntity(p);world.setBlockState(p,net.minecraft.init.Blocks.AIR.getDefaultState(),2);
            }
        }
    }
    public boolean setExtended(World world,BlockPos pos,boolean extend){
        IBlockState state=world.getBlockState(pos);if(state.getBlock()!=this||state.getValue(LOWER))return false;
        TileEntityLandingGear tile=root(world,pos);if(tile==null)return false;
        if(extend&&!reserve(world,pos,Math.max(tile.progress,tile.getExtensionPixels()/16F)))return false;
        if(state.getValue(EXTENDED)!=extend){world.setBlockState(pos,state.withProperty(EXTENDED,extend),3);tile.markDirty();tile.sync();}
        return true;
    }
    public void onBlockPlacedBy(World world,BlockPos pos,IBlockState s,EntityLivingBase placer,ItemStack stack){
        if(!world.isRemote){TileEntityLandingGear tile=root(world,pos);if(tile!=null)tile.inputChanged();}
    }
    public void neighborChanged(IBlockState s,World world,BlockPos pos,Block block,BlockPos from){
        if(world.isRemote)return;
        TileEntityLandingGear tile=root(world,pos);
        if(s.getValue(LOWER)){if(tile==null){world.removeTileEntity(pos);world.setBlockToAir(pos);}}
        else if(tile!=null)tile.inputChanged();
    }
    public boolean onBlockActivated(World world,BlockPos pos,IBlockState state,EntityPlayer player,EnumHand hand,EnumFacing face,float x,float y,float z){
        if(hand!=EnumHand.MAIN_HAND)return false;
        TileEntityLandingGear tile=root(world,pos);if(tile==null)return false;pos=tile.getPos();
        if(player.isSneaking()){
            if(!com.vandorlabs.items.ConfigurationAccess.canConfigure(player))return false;
            if(!world.isRemote)player.openGui(com.vandorlabs.VandorLabs.instance,com.vandorlabs.GuiHandler.GUI_LANDING_GEAR,world,pos.getX(),pos.getY(),pos.getZ());
        }else if(!world.isRemote && player.canPlayerEdit(pos,face,player.getHeldItem(hand))&&world.isBlockModifiable(player,pos))
            setExtended(world,pos,!world.getBlockState(pos).getValue(EXTENDED));
        return true;
    }
    protected List<AxisAlignedBB> boxes(IBlockState state,IBlockAccess world,BlockPos pos){
        TileEntityLandingGear tile=root(world,pos);double t=tile==null?0:tile.progress;
        int offset=tile==null?0:tile.getPos().getY()-pos.getY();
        boolean large=getRegistryName().getResourcePath().startsWith("large");
        double anchor=large?14/16D:12/16D,pistonBottom=large?13/16D:10/16D;
        List<AxisAlignedBB> result=new ArrayList<>();
        for(AxisAlignedBB source:parts){
            AxisAlignedBB b=source;
            if(b.minY>=anchor){}
            else if(b.minY==pistonBottom&&b.maxY==anchor)b=new AxisAlignedBB(b.minX,b.minY-t,b.minZ,b.maxX,b.maxY,b.maxZ);
            else b=b.offset(0,-t,0);
            b=b.offset(0,offset,0);
            if(b.maxY>0&&b.minY<1)result.add(new AxisAlignedBB(b.minX,Math.max(0,b.minY),b.minZ,b.maxX,Math.min(1,b.maxY),b.maxZ));
        }
        return result;
    }
    public void breakBlock(World world,BlockPos pos,IBlockState s){
        TileEntityLandingGear tile=root(world,pos);
        if(s.getValue(LOWER)){
            if(tile!=null)world.setBlockToAir(tile.getPos());
        }else releaseBelow(world,pos,0);
        super.breakBlock(world,pos,s);
    }
    public int damageDropped(IBlockState s){return 0;}
    private ItemStack configured(TileEntityLandingGear tile){
        ItemStack stack=new ItemStack(this);
        if(tile!=null){net.minecraft.nbt.NBTTagCompound tag=new net.minecraft.nbt.NBTTagCompound();
            tag.setInteger("ExtensionPixels",tile.getExtensionPixels());tag.setInteger("RedstoneMode",tile.getMode());tag.setInteger("RedstoneChannel",tile.getRedstoneChannel());stack.setTagInfo("BlockEntityTag",tag);}
        return stack;
    }
    public ItemStack getPickBlock(IBlockState s,RayTraceResult hit,World world,BlockPos pos,EntityPlayer player){return configured(root(world,pos));}
    public void getDrops(NonNullList<ItemStack> drops,IBlockAccess world,BlockPos pos,IBlockState state,int fortune){drops.add(configured(root(world,pos)));}
    public boolean removedByPlayer(IBlockState state,World world,BlockPos pos,EntityPlayer player,boolean willHarvest){return willHarvest||super.removedByPlayer(state,world,pos,player,false);}
    public void harvestBlock(World world,EntityPlayer player,BlockPos pos,IBlockState state,TileEntity tile,ItemStack tool){super.harvestBlock(world,player,pos,state,tile,tool);world.setBlockToAir(pos);}
}
