package com.vandorlabs.blocks;

import com.vandorlabs.GuiHandler;
import com.vandorlabs.VandorLabs;
import com.vandorlabs.tiles.TileEntitySpaceDoor;
import net.minecraft.block.BlockDoor;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

public class BlockConfigurableSpaceDoor extends BlockSpaceDoor {
    public BlockConfigurableSpaceDoor(String name,boolean sliding,BlockDetailedDoor paired) {
        super(name,sliding,true,paired);
    }
    @Override public TileEntity createTileEntity(World world,IBlockState state) {
        TileEntitySpaceDoor tile=new TileEntitySpaceDoor();
        if (isSlidingModel()) tile.configure(tile.getDesign(),tile.getDetail(),tile.isFramed(),0,true,true);
        return tile;
    }
    @Override public boolean onBlockActivated(World world,BlockPos pos,IBlockState state,EntityPlayer player,
            EnumHand hand,EnumFacing face,float x,float y,float z) {
        if (!player.isSneaking()) return super.onBlockActivated(world,pos,state,player,hand,face,x,y,z);
        BlockPos lower=state.getValue(HALF)==BlockDoor.EnumDoorHalf.UPPER?pos.down():pos;
        if (!world.isRemote) player.openGui(VandorLabs.instance,GuiHandler.GUI_SPACE_DOOR,world,
                lower.getX(),lower.getY(),lower.getZ());
        return true;
    }
    @Override public void onBlockPlacedBy(World world,BlockPos pos,IBlockState state,EntityLivingBase placer,ItemStack stack) {
        super.onBlockPlacedBy(world,pos,state,placer,stack);
        TileEntity raw=world.getTileEntity(pos);
        if (!world.isRemote && raw instanceof TileEntitySpaceDoor) {
            TileEntitySpaceDoor tile=(TileEntitySpaceDoor)raw;
            if (stack.hasTagCompound() && stack.getTagCompound().hasKey("SpaceDoorSettings",10)) {
                // An explicitly picked configuration takes precedence over
                // both placement defaults and neighbor appearance inheritance.
                tile.applyItemSettings(stack.getTagCompound().getCompoundTag("SpaceDoorSettings"));
                return;
            }
            BlockPos mate=tile.mate();
            if (mate!=null && world.isBlockLoaded(mate) && world.getTileEntity(mate) instanceof TileEntitySpaceDoor) {
                TileEntitySpaceDoor other=(TileEntitySpaceDoor)world.getTileEntity(mate);
                tile.configure(other.getDesign(),other.getDetail(),other.isFramed(),other.getSlideDirection(),
                        other.isMiddle(),other.isSliding(),other.hasHinges());
            } else if (isSlidingModel()) {
                // Both the unified block and legacy sliding item default to
                // sideways sliding on the centre track.
                tile.configure(tile.getDesign(),tile.getDetail(),tile.isFramed(),
                        tile.getSlideDirection(),true,true);
            }
        }
    }
    @Override public ItemStack getPickBlock(IBlockState state,net.minecraft.util.math.RayTraceResult target,
            World world,BlockPos pos,EntityPlayer player) {
        net.minecraft.block.Block unified=net.minecraft.block.Block.REGISTRY.getObject(
                new net.minecraft.util.ResourceLocation("vandorlabs","space_door"));
        ItemStack stack=new ItemStack(unified);
        TileEntitySpaceDoor tile=settings(state,world,pos);
        if (tile!=null) stack.setTagInfo("SpaceDoorSettings",tile.itemSettings());
        return stack;
    }
    private TileEntitySpaceDoor settings(IBlockState state,net.minecraft.world.IBlockAccess world,BlockPos pos) {
        BlockPos lower=state.getValue(HALF)==BlockDoor.EnumDoorHalf.UPPER?pos.down():pos;
        TileEntity raw=world.getTileEntity(lower);
        return raw instanceof TileEntitySpaceDoor?(TileEntitySpaceDoor)raw:null;
    }
    @Override public net.minecraft.util.math.AxisAlignedBB getBoundingBox(IBlockState state,
            net.minecraft.world.IBlockAccess world,BlockPos pos) {
        TileEntitySpaceDoor tile=settings(state,world,pos);
        IBlockState actual=getActualState(state,world,pos);
        net.minecraft.util.math.AxisAlignedBB box=tile==null?super.getBoundingBox(state,world,pos)
                :tile.model(tile.isSliding()).spaceBounds(actual,world,pos);
        if (tile!=null) {
            EnumFacing facing=actual.getValue(FACING);
            box=box.offset(facing.getFrontOffsetX()*tile.positionOffset(),0,
                    facing.getFrontOffsetZ()*tile.positionOffset());
        }
        return box;
    }
    @Override public net.minecraft.util.math.AxisAlignedBB getCollisionBoundingBox(IBlockState state,
            net.minecraft.world.IBlockAccess world,BlockPos pos) {
        TileEntitySpaceDoor tile=settings(state,world,pos);
        if (tile==null) return super.getCollisionBoundingBox(state,world,pos);
        IBlockState actual=getActualState(state,world,pos);
        if (tile.isSliding() && actual.getValue(OPEN)) return NULL_AABB;
        net.minecraft.util.math.AxisAlignedBB box=tile.model(tile.isSliding()).spaceBounds(actual,world,pos);
        EnumFacing facing=actual.getValue(FACING);
        return box.offset(facing.getFrontOffsetX()*tile.positionOffset(),0,
                facing.getFrontOffsetZ()*tile.positionOffset());
    }
}
