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
    public static final String[] SIZES={"small","medium","large","extra_large"};
    private final List<List<List<AxisAlignedBB>>> geometry=new ArrayList<>();
    public static float pistonAnchor(int size){return (size==0?12:size==1?14:size==2?15:14)/16F;}
    public static float pistonLength(int size){return (size==0?2:size==1?1:size==2?.5F:1)/16F;}
    public BlockTelescopicLandingGear(String name){
        super(name);
        for(String size:SIZES){
            List<List<AxisAlignedBB>> groups=new ArrayList<>();
            for(String group:new String[]{"fixed","wheel","piston"})groups.add(loadParts("item/landing_gear_"+size+"_"+group));
            geometry.add(groups);
        }
        setDefaultState(blockState.getBaseState().withProperty(FACING,EnumFacing.NORTH).withProperty(LOWER,false).withProperty(EXTENDED,false));
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
        BlockPos owner=tile.owner();if(owner==null || owner.getY()<pos.getY()||owner.getY()-pos.getY()>5
                ||world instanceof World&&!((World)world).isBlockLoaded(owner))return null;
        IBlockState state=world.getBlockState(owner);
        if(state.getBlock()!=this||state.getValue(LOWER))return null;
        raw=world.getTileEntity(owner);
        if(!(raw instanceof TileEntityLandingGear))return null;
        TileEntityLandingGear root=(TileEntityLandingGear)raw;
        return Math.abs(owner.getX()-pos.getX())<=1&&Math.abs(owner.getZ()-pos.getZ())<=1
                && (owner.getX()==pos.getX()&&owner.getZ()==pos.getZ()||root.getSize()==3)?root:null;
    }
    private boolean owned(World world,BlockPos pos,BlockPos root){
        if(!world.isBlockLoaded(pos))return false;
        TileEntity raw=world.getTileEntity(pos);
        return world.getBlockState(pos).getBlock()==this && raw instanceof TileEntityLandingGear && root.equals(((TileEntityLandingGear)raw).owner());
    }
    public boolean reserve(World world,BlockPos root,float distance,int size){
        int count=(int)Math.ceil(distance)+(size==3?1:0);
        if(count>5||root.getY()<count)return false;
        int radius=size==3?1:0;
        for(int i=0;i<=count;i++)for(int x=-radius;x<=radius;x++)for(int z=-radius;z<=radius;z++){
            if(i==0&&x==0&&z==0)continue;
            BlockPos p=root.add(x,-i,z);if(!world.isBlockLoaded(p))return false;
            if(!owned(world,p,root)&&!world.isAirBlock(p))return false;
        }
        for(int i=0;i<=count;i++)for(int x=-radius;x<=radius;x++)for(int z=-radius;z<=radius;z++){
            if(i==0&&x==0&&z==0)continue;
            BlockPos p=root.add(x,-i,z);if(owned(world,p,root))continue;
            world.setBlockState(p,world.getBlockState(root).withProperty(LOWER,true).withProperty(EXTENDED,true),2);
            TileEntity raw=world.getTileEntity(p);if(raw instanceof TileEntityLandingGear)((TileEntityLandingGear)raw).setOwner(root);
        }
        return true;
    }
    public boolean reserve(World world,BlockPos root,float distance){
        TileEntityLandingGear tile=root(world,root);
        return tile!=null&&reserve(world,root,distance,tile.getSize());
    }
    public void releaseBelow(World world,BlockPos root,int keep){
        TileEntityLandingGear tile=root(world,root);
        int radius=tile!=null&&tile.getSize()==3?1:0;
        if(radius==1)keep++;
        for(int i=0;i<=5;i++)for(int x=-1;x<=1;x++)for(int z=-1;z<=1;z++){
            if(i==0&&x==0&&z==0)continue;
            if(i<=keep && Math.abs(x)<=radius && Math.abs(z)<=radius && (i>0||radius==1))continue;
            BlockPos p=root.add(x,-i,z);if(owned(world,p,root)){
                // Removing ownership first distinguishes internal cleanup from mining a wheel.
                world.removeTileEntity(p);world.setBlockState(p,net.minecraft.init.Blocks.AIR.getDefaultState(),2);
            }
        }
    }
    public boolean setExtended(World world,BlockPos pos,boolean extend){
        IBlockState state=world.getBlockState(pos);if(state.getBlock()!=this||state.getValue(LOWER))return false;
        TileEntityLandingGear tile=root(world,pos);if(tile==null)return false;
        if(extend&&!reserve(world,pos,Math.max(tile.progress,tile.getExtensionPixels()/16F),tile.getSize()))return false;
        if(state.getValue(EXTENDED)!=extend){world.setBlockState(pos,state.withProperty(EXTENDED,extend),3);tile.markDirty();tile.sync();}
        return true;
    }
    public void onBlockPlacedBy(World world,BlockPos pos,IBlockState s,EntityLivingBase placer,ItemStack stack){
        if(!world.isRemote){TileEntityLandingGear tile=root(world,pos);if(tile!=null){
            if(tile.getSize()==3&&!reserve(world,pos,0,3)){world.destroyBlock(pos,true);return;}
            tile.placed();
        }}
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
        int dx=tile==null?0:tile.getPos().getX()-pos.getX(),dz=tile==null?0:tile.getPos().getZ()-pos.getZ();
        int size=tile==null?0:tile.getSize();
        List<AxisAlignedBB> result=new ArrayList<>();
        for(int group=0;group<3;group++)for(AxisAlignedBB source:geometry.get(size).get(group)){
            AxisAlignedBB b=source;
            if(group==1)b=b.offset(0,-t,0);
            else if(group==2)b=new AxisAlignedBB(b.minX,b.minY-t,b.minZ,b.maxX,b.maxY,b.maxZ);
            b=b.offset(dx,offset,dz);
            if(b.maxY>0&&b.minY<1&&b.maxX>0&&b.minX<1&&b.maxZ>0&&b.minZ<1)
                result.add(new AxisAlignedBB(Math.max(0,b.minX),Math.max(0,b.minY),Math.max(0,b.minZ),
                        Math.min(1,b.maxX),Math.min(1,b.maxY),Math.min(1,b.maxZ)));
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
            tag.setInteger("GearSize",tile.getSize());tag.setInteger("ExtensionPixels",tile.getExtensionPixels());tag.setInteger("RedstoneMode",tile.getMode());tag.setInteger("RedstoneChannel",tile.getRedstoneChannel());stack.setTagInfo("BlockEntityTag",tag);}
        return stack;
    }
    public ItemStack getPickBlock(IBlockState s,RayTraceResult hit,World world,BlockPos pos,EntityPlayer player){return configured(root(world,pos));}
    public void getDrops(NonNullList<ItemStack> drops,IBlockAccess world,BlockPos pos,IBlockState state,int fortune){drops.add(configured(root(world,pos)));}
    public boolean removedByPlayer(IBlockState state,World world,BlockPos pos,EntityPlayer player,boolean willHarvest){return willHarvest||super.removedByPlayer(state,world,pos,player,false);}
    public void harvestBlock(World world,EntityPlayer player,BlockPos pos,IBlockState state,TileEntity tile,ItemStack tool){super.harvestBlock(world,player,pos,state,tile,tool);world.setBlockToAir(pos);}
}
