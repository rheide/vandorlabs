package com.vandorlabs.ports;

import com.vandorlabs.blocks.BlockVandor;
import com.vandorlabs.blocks.ProgrammableHousingState;
import net.minecraft.block.Block;
import net.minecraft.block.properties.IProperty;
import net.minecraft.block.properties.PropertyDirection;
import net.minecraft.block.state.*;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.*;
import net.minecraft.util.math.*;
import net.minecraft.world.*;
import net.minecraftforge.common.property.*;

/** Six configurable native capability faces with static chunk-rendered artwork. */
public final class ResourcePortBlock extends BlockVandor {
    public enum Kind{POWER("power","Power Port"),FLUID("fluid","Fluid Port"),MATTER("matter","Matter Port");public final String texture,title;Kind(String texture,String title){this.texture=texture;this.title=title;}}
    public static final PropertyDirection FACING=PropertyDirection.create("facing");
    public static final IUnlistedProperty<Integer> PORT_FACES=ProgrammableHousingState.integer("resource_port_faces"),LIT=ProgrammableHousingState.integer("resource_port_lit"),HOUSING=ProgrammableHousingState.integer("resource_port_housing");
    public final Kind kind;
    public ResourcePortBlock(Kind kind){super(kind.texture+"_port");this.kind=kind;setDefaultState(blockState.getBaseState().withProperty(FACING,EnumFacing.NORTH));}
    @Override protected BlockStateContainer createBlockState(){return new ExtendedBlockState(this,new IProperty[]{FACING},new IUnlistedProperty[]{PORT_FACES,LIT,HOUSING});}
    @Override public IBlockState getStateFromMeta(int meta){return getDefaultState().withProperty(FACING,meta>=0&&meta<6?EnumFacing.getFront(meta):EnumFacing.NORTH);}
    @Override public int getMetaFromState(IBlockState state){return state.getValue(FACING).getIndex();}
    @Override public IBlockState getStateForPlacement(World world,BlockPos pos,EnumFacing clicked,float x,float y,float z,int meta,EntityLivingBase placer){return getDefaultState().withProperty(FACING,com.vandorlabs.blocks.DirectionalPlacement.towardView(placer));}
    @Override public IBlockState withRotation(IBlockState state,Rotation rotation){return state.withProperty(FACING,rotation.rotate(state.getValue(FACING)));}
    @Override public IBlockState withMirror(IBlockState state,Mirror mirror){return state.withProperty(FACING,mirror.mirror(state.getValue(FACING)));}
    @Override public boolean hasTileEntity(IBlockState state){return true;}
    @Override public TileEntity createTileEntity(World world,IBlockState state){return kind==Kind.POWER?new PowerPortTile():kind==Kind.FLUID?new FluidPortTile():new MatterPortTile();}
    @Override public void onBlockPlacedBy(World world,BlockPos pos,IBlockState state,EntityLivingBase placer,ItemStack stack){
        super.onBlockPlacedBy(world,pos,state,placer,stack);if(world.isRemote)return;TileEntity tile=world.getTileEntity(pos);NBTTagCompound saved=stack.getSubCompound("BlockEntityTag");
        if(tile instanceof ResourcePortTile&&(saved==null||!saved.hasKey(ResourcePortData.FACES,11)))((ResourcePortTile)tile).place(state.getValue(FACING));
    }
    @Override public IBlockState getExtendedState(IBlockState state,IBlockAccess world,BlockPos pos){
        TileEntity raw=world.getTileEntity(pos);ResourcePortSettings settings=raw instanceof ResourcePortTile?((ResourcePortTile)raw).settings():ResourcePortSettings.placed(state.getValue(FACING).getIndex());int faces=0;for(int i=0;i<6;i++)faces|=settings.face(i).ordinal()<<(i*2);
        return ((IExtendedBlockState)state).withProperty(PORT_FACES,faces).withProperty(HOUSING,settings.housingChoice).withProperty(LIT,raw instanceof ResourcePortTile?((ResourcePortTile)raw).appearanceMask():0);
    }
    @Override public void neighborChanged(IBlockState state,World world,BlockPos pos,Block block,BlockPos from){if(!world.isRemote&&world.isBlockLoaded(pos)){TileEntity tile=world.getTileEntity(pos);if(tile instanceof ResourcePortTile)((ResourcePortTile)tile).neighborChanged();}}
    @Override public boolean onBlockActivated(World world,BlockPos pos,IBlockState state,EntityPlayer player,EnumHand hand,EnumFacing side,float x,float y,float z){if(player.getHeldItem(hand).getItem() instanceof net.minecraft.item.ItemBlock)return false;return ResourcePorts.open(player,world,pos);}
    private ItemStack drop(TileEntity tile){ItemStack result=new ItemStack(this);if(tile instanceof ResourcePortTile){NBTTagCompound data=tile.writeToNBT(new NBTTagCompound());for(String key:new String[]{"id","x","y","z"})data.removeTag(key);result.setTagInfo("BlockEntityTag",data);}return result;}
    @Override public void getDrops(NonNullList<ItemStack> drops,IBlockAccess world,BlockPos pos,IBlockState state,int fortune){drops.add(drop(world.getTileEntity(pos)));}
    @Override public boolean removedByPlayer(IBlockState state,World world,BlockPos pos,EntityPlayer player,boolean willHarvest){if(!world.isRemote&&!willHarvest){TileEntity tile=world.getTileEntity(pos);if(tile instanceof ResourcePortTile&&((ResourcePortTile)tile).inert())spawnAsEntity(world,pos,drop(tile));}return super.removedByPlayer(state,world,pos,player,willHarvest);}
    @Override public void harvestBlock(World world,EntityPlayer player,BlockPos pos,IBlockState state,TileEntity tile,ItemStack tool){spawnAsEntity(world,pos,drop(tile));}
    @Override public ItemStack getPickBlock(IBlockState state,RayTraceResult hit,World world,BlockPos pos,EntityPlayer player){return drop(world.getTileEntity(pos));}
    @Override @net.minecraftforge.fml.relauncher.SideOnly(net.minecraftforge.fml.relauncher.Side.CLIENT)
    public void addInformation(ItemStack stack,World world,java.util.List<String> lines,net.minecraft.client.util.ITooltipFlag advanced){if(kind==Kind.MATTER)lines.add("Transfers items");lines.add("Transfers resources; does not generate them");}
}
