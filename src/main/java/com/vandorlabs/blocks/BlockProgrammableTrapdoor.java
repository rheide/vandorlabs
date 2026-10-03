package com.vandorlabs.blocks;

import com.vandorlabs.*;
import com.vandorlabs.render.TrapdoorGeometry;
import com.vandorlabs.tiles.TileEntityProgrammableTrapdoor;
import net.minecraft.block.*;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.*;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.*;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.*;
import net.minecraft.util.math.*;
import net.minecraft.world.*;
import javax.annotation.Nullable;

/** Vanilla trapdoor interaction with configurable rigid motion and finishes. */
public class BlockProgrammableTrapdoor extends BlockTrapDoor {
    public BlockProgrammableTrapdoor() { this("programmable_trapdoor"); }
    protected BlockProgrammableTrapdoor(String name) {
        super(Material.IRON);setRegistryName(VandorLabs.MODID,name);
        setUnlocalizedName("vandorlabs."+name);setCreativeTab(VandorLabs.VANDOR_LABS_TAB);
        setHardness(3);setResistance(10);setSoundType(SoundType.METAL);useNeighborBrightness=true;
    }
    @Override public boolean hasTileEntity(IBlockState state){return true;}
    @Override public TileEntity createTileEntity(World world,IBlockState state){return new TileEntityProgrammableTrapdoor();}
    @Override public EnumBlockRenderType getRenderType(IBlockState state){return EnumBlockRenderType.ENTITYBLOCK_ANIMATED;}
    @Override public IBlockState getStateForPlacement(World world,BlockPos pos,EnumFacing side,float x,float y,float z,int meta,EntityLivingBase placer) {
        EnumFacing facing=side.getAxis().isHorizontal()?side.getOpposite():placer.getHorizontalFacing().getOpposite();
        DoorHalf half=side.getAxis().isHorizontal()?(y>.5F?DoorHalf.TOP:DoorHalf.BOTTOM)
                :side==EnumFacing.UP?DoorHalf.BOTTOM:DoorHalf.TOP;
        return getDefaultState().withProperty(FACING,facing).withProperty(HALF,half)
                .withProperty(OPEN,com.vandorlabs.redstone.LoadedRedstonePower.isPowered(world,pos));
    }
    public static int quarterTurns(EnumFacing facing) {
        return facing==EnumFacing.NORTH?0:facing==EnumFacing.EAST?1:facing==EnumFacing.SOUTH?2:3;
    }
    @Override public AxisAlignedBB getBoundingBox(IBlockState state,IBlockAccess world,BlockPos pos) {
        TileEntity raw=world.getTileEntity(pos);TileEntityProgrammableTrapdoor tile=raw instanceof TileEntityProgrammableTrapdoor?(TileEntityProgrammableTrapdoor)raw:null;
        int position=tile==null?(state.getValue(HALF)==DoorHalf.TOP?2:0):tile.getPosition();
        double[] b=com.vandorlabs.render.DiagonalTrapdoorGeometry.bounds(tile==null?TrapdoorGeometry.corners(position,false,quarterTurns(state.getValue(FACING)),state.getValue(OPEN)?1:0):tile.corners(state,state.getValue(OPEN)?1:0));
        return new AxisAlignedBB(b[0],b[1],b[2],b[3],b[4],b[5]);
    }
    @Override public net.minecraft.block.state.BlockFaceShape getBlockFaceShape(IBlockAccess world,IBlockState state,BlockPos pos,EnumFacing face){return BlockFaceShape.UNDEFINED;}
    @Override public boolean isPassable(IBlockAccess world,BlockPos pos){return world.getBlockState(pos).getValue(OPEN);}
    @Override public boolean onBlockActivated(World world,BlockPos pos,IBlockState state,EntityPlayer player,EnumHand hand,EnumFacing face,float x,float y,float z) {
        if(hand!=EnumHand.MAIN_HAND)return false;
        if(player.isSneaking() && player.capabilities.isCreativeMode) {
            if(!world.isRemote)player.openGui(VandorLabs.instance,GuiHandler.GUI_PROGRAMMABLE_TRAPDOOR,world,pos.getX(),pos.getY(),pos.getZ());
            return true;
        }
        if(!world.isRemote && player.canPlayerEdit(pos,face,player.getHeldItem(hand)) && world.isBlockModifiable(player,pos)) {
            TileEntity raw=world.getTileEntity(pos);
            if(raw instanceof TileEntityProgrammableTrapdoor) {
                TileEntityProgrammableTrapdoor tile=(TileEntityProgrammableTrapdoor)raw;
                if(tile instanceof com.vandorlabs.tiles.TileEntityProgrammableDiagonalTrapdoor && tile.getPosition()!=1)tile.completeSquare(player,player.getHeldItem(hand));
                boolean editable=true;
                for(TileEntityProgrammableTrapdoor leaf:tile.group())
                    editable &= player.canPlayerEdit(leaf.getPos(),face,player.getHeldItem(hand)) && world.isBlockModifiable(player,leaf.getPos());
                if(editable)tile.requestOpen(!state.getValue(OPEN));
            }
        }
        return true;
    }
    @Override public void neighborChanged(IBlockState state,World world,BlockPos pos,Block block,BlockPos from) {
        if(!world.isRemote && world.getTileEntity(pos) instanceof TileEntityProgrammableTrapdoor)
            ((TileEntityProgrammableTrapdoor)world.getTileEntity(pos)).localInputChanged();
    }
    @Override public void onBlockPlacedBy(World world,BlockPos pos,IBlockState state,EntityLivingBase placer,ItemStack stack) {
        TileEntity raw=world.getTileEntity(pos);if(!(raw instanceof TileEntityProgrammableTrapdoor))return;
        TileEntityProgrammableTrapdoor tile=(TileEntityProgrammableTrapdoor)raw;
        NBTTagCompound saved=stack.getSubCompound("BlockEntityTag");
        if(saved!=null) {
            NBTTagCompound data=tile.writeToNBT(new NBTTagCompound());data.merge(saved);
            data.removeTag("TrapdoorPartner");data.removeTag("TrapdoorSquare");data.removeTag("ChannelSignal");
            data.removeTag("TrapdoorPowerKnown");data.removeTag("TrapdoorLastPower");
            data.setInteger("x",pos.getX());data.setInteger("y",pos.getY());data.setInteger("z",pos.getZ());tile.readFromNBT(data);
        } else tile.configure(0,state.getValue(HALF)==DoorHalf.TOP?2:0,false,
                com.vandorlabs.persistence.SpaceDoorData.TRIGGER_REDSTONE_ON,0);
        if(saved!=null)tile.configure(tile.getHousingTexture(),tile.getPosition(),tile.isSliding(),tile.getTrigger(),tile.getRedstoneChannel());
        if(saved!=null && tile.isCover() && saved.hasKey("TrapdoorCoverFacing",3))tile.setCoverFacing(EnumFacing.getHorizontal(saved.getInteger("TrapdoorCoverFacing")));
        if(world.isRemote){if(tile.isCover())world.setBlockState(pos,world.getBlockState(pos).withProperty(OPEN,true),2);return;}
        for(EnumFacing side:tile instanceof com.vandorlabs.tiles.TileEntityProgrammableDiagonalTrapdoor?EnumFacing.values():EnumFacing.HORIZONTALS) {
            BlockPos next=pos.offset(side);if(!world.isBlockLoaded(next))continue;
            TileEntity neighbor=world.getTileEntity(next);
            if(!(neighbor instanceof TileEntityProgrammableTrapdoor))continue;
            TileEntityProgrammableTrapdoor other=(TileEntityProgrammableTrapdoor)neighbor;
            if(other.hasPairLink() || !tile.compatible(other))continue;
            if(placer instanceof EntityPlayer && (!((EntityPlayer)placer).canPlayerEdit(next,side,stack)
                    || !world.isBlockModifiable((EntityPlayer)placer,next)))continue;
            tile.pairWith(other);if(tile.hasPairLink())break;
        }
        tile.completeSquare(placer instanceof EntityPlayer?(EntityPlayer)placer:null,stack);
        tile.evaluatePower(true);
        if(tile.isCover())tile.requestOpen(true);
    }
    public ItemStack configuredDrop(@Nullable TileEntity raw) {
        ItemStack stack=new ItemStack(Item.getItemFromBlock(this));
        if(raw instanceof TileEntityProgrammableTrapdoor)stack.setTagInfo("BlockEntityTag",((TileEntityProgrammableTrapdoor)raw).itemSettings());
        return stack;
    }
    @Override public ItemStack getPickBlock(IBlockState state,RayTraceResult hit,World world,BlockPos pos,EntityPlayer player){return configuredDrop(world.getTileEntity(pos));}
    @Override public void getDrops(NonNullList<ItemStack> drops,IBlockAccess world,BlockPos pos,IBlockState state,int fortune){drops.add(configuredDrop(world.getTileEntity(pos)));}
    @Override public void harvestBlock(World world,EntityPlayer player,BlockPos pos,IBlockState state,@Nullable TileEntity tile,ItemStack tool) {
        if(!world.isRemote && !player.capabilities.isCreativeMode)spawnAsEntity(world,pos,configuredDrop(tile));
    }
    @Override public void breakBlock(World world,BlockPos pos,IBlockState state) {
        TileEntity raw=world.getTileEntity(pos);
        if(!world.isRemote && raw instanceof TileEntityProgrammableTrapdoor)((TileEntityProgrammableTrapdoor)raw).unpair();
        super.breakBlock(world,pos,state);world.removeTileEntity(pos);
    }
}
