package com.vandorlabs.programmer;

import com.vandorlabs.VandorLabs;
import net.minecraft.block.*;
import net.minecraft.block.material.Material;
import net.minecraft.block.properties.*;
import net.minecraft.block.state.*;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.InventoryHelper;
import net.minecraft.item.*;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.*;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.*;

/** Four reserved cells, with one inventory and one rendering owner. */
public final class ProgrammerBlock extends Block {
    public static final PropertyDirection FACING=PropertyDirection.create("facing",EnumFacing.Plane.HORIZONTAL);
    public static final PropertyInteger PART=PropertyInteger.create("part",0,3);
    public ProgrammerBlock(){super(Material.IRON);setRegistryName("vandorlabs","data_chip_programmer");setUnlocalizedName("vandorlabs.data_chip_programmer");setCreativeTab(VandorLabs.VANDOR_LABS_TAB);setHardness(3.5F);setDefaultState(blockState.getBaseState().withProperty(FACING,EnumFacing.NORTH).withProperty(PART,0));}
    @Override protected BlockStateContainer createBlockState(){return new BlockStateContainer(this,FACING,PART);}
    @Override public IBlockState getStateFromMeta(int meta){return getDefaultState().withProperty(FACING,EnumFacing.getHorizontal(meta&3)).withProperty(PART,(meta>>2)&3);}
    @Override public int getMetaFromState(IBlockState state){return state.getValue(FACING).getHorizontalIndex()|(state.getValue(PART)<<2);}
    @Override public boolean isFullCube(IBlockState state){return false;}
    @Override public boolean isOpaqueCube(IBlockState state){return false;}
    @Override public EnumBlockRenderType getRenderType(IBlockState state){return EnumBlockRenderType.ENTITYBLOCK_ANIMATED;}
    @Override public boolean hasTileEntity(IBlockState state){return state.getValue(PART)==0;}
    @Override public TileEntity createTileEntity(World world,IBlockState state){return new ProgrammerTile();}
    public static BlockPos cell(BlockPos root,EnumFacing facing,int part){return root.offset(facing.rotateY(),part&1).up(part>>1);}
    public static BlockPos root(BlockPos pos,IBlockState state){int part=state.getValue(PART);return pos.offset(state.getValue(FACING).rotateY(),-(part&1)).down(part>>1);}
    public static ProgrammerTile at(World world,BlockPos pos){IBlockState state=world.getBlockState(pos);if(!(state.getBlock() instanceof ProgrammerBlock))return null;TileEntity tile=world.getTileEntity(root(pos,state));return tile instanceof ProgrammerTile?(ProgrammerTile)tile:null;}
    @Override public boolean onBlockActivated(World world,BlockPos pos,IBlockState state,EntityPlayer player,EnumHand hand,EnumFacing side,float x,float y,float z){ProgrammerTile tile=at(world,pos);if(tile!=null&&!world.isRemote&&tile.usable(player))player.openGui(VandorLabs.instance,24,world,tile.getPos().getX(),tile.getPos().getY(),tile.getPos().getZ());return true;}
    @Override public int quantityDropped(IBlockState state,int fortune,java.util.Random random){return state.getValue(PART)==0?1:0;}
    @Override public int damageDropped(IBlockState state){return 0;}
    @Override public ItemStack getItem(World world,BlockPos pos,IBlockState state){return new ItemStack(this);}
    @Override public void breakBlock(World world,BlockPos pos,IBlockState state){
        BlockPos root=root(pos,state);if(state.getValue(PART)!=0){if(world.isBlockLoaded(root)&&world.getBlockState(root).getBlock()==this)world.destroyBlock(root,true);super.breakBlock(world,pos,state);return;}
        TileEntity tile=world.getTileEntity(pos);if(!world.isRemote&&tile instanceof ProgrammerTile)InventoryHelper.dropInventoryItems(world,pos,(ProgrammerTile)tile);
        for(int i=1;i<4;i++){BlockPos member=cell(root,state.getValue(FACING),i);if(world.isBlockLoaded(member)){IBlockState s=world.getBlockState(member);if(s.getBlock()==this&&s.getValue(PART)==i&&s.getValue(FACING)==state.getValue(FACING))world.setBlockToAir(member);}}
        super.breakBlock(world,pos,state);
    }
    public static final class BlockItem extends ItemBlock {
        public BlockItem(Block block){super(block);}
        @Override public boolean placeBlockAt(ItemStack stack,EntityPlayer player,World world,BlockPos pos,EnumFacing side,float x,float y,float z,IBlockState ignored){
            EnumFacing facing=player.getHorizontalFacing().getOpposite();
            for(int i=0;i<4;i++){BlockPos p=cell(pos,facing,i);if(!world.isBlockLoaded(p)||p.getY()<0||p.getY()>=world.getHeight()||!world.getWorldBorder().contains(p)||!world.getBlockState(p).getBlock().isReplaceable(world,p)||!player.canPlayerEdit(p,side,stack)||!world.isBlockModifiable(player,p))return false;}
            IBlockState state=block.getDefaultState().withProperty(FACING,facing);
            for(int i=0;i<4;i++)if(!world.setBlockState(cell(pos,facing,i),state.withProperty(PART,i),3)){for(int j=0;j<i;j++)world.setBlockToAir(cell(pos,facing,j));return false;}
            block.onBlockPlacedBy(world,pos,state,player,stack);return true;
        }
    }
}
