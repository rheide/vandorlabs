package com.vandorlabs.items;

import com.vandorlabs.blocks.BlockProgrammableDiagonalTrapdoor;
import net.minecraft.block.state.IBlockState;
import com.vandorlabs.tiles.TileEntityProgrammableDiagonalTrapdoor;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.*;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/** Carry the clicked wall's geometry into the newly created tile, on both sides. */
public final class ItemDiagonalTrapdoor extends ItemBlock {
    public ItemDiagonalTrapdoor(BlockProgrammableDiagonalTrapdoor block){super(block);}
    @Override public boolean placeBlockAt(ItemStack stack,EntityPlayer player,World world,BlockPos pos,EnumFacing side,float x,float y,float z,IBlockState state) {
        NBTTagCompound saved=stack.getSubCompound("BlockEntityTag");
        boolean inheritRotation=saved==null||!saved.hasKey("DiagonalRotationFlipped",1);
        ItemStack configured=stack.copy();NBTTagCompound tag=stack.getSubCompound("BlockEntityTag");tag=tag==null?new NBTTagCompound():tag.copy();
        int mode=BlockProgrammableDiagonalTrapdoor.placementMode(world,pos.offset(side.getOpposite()),stack);
        tag.setInteger("TrapdoorPosition",mode);tag.removeTag("DiagonalReverse");configured.setTagInfo("BlockEntityTag",tag);
        net.minecraft.tileentity.TileEntity clicked=world.getTileEntity(pos.offset(side.getOpposite()));
        TileEntityProgrammableDiagonalTrapdoor reference=clicked instanceof TileEntityProgrammableDiagonalTrapdoor?(TileEntityProgrammableDiagonalTrapdoor)clicked:null;
        boolean inheritHinge=(saved==null||!saved.hasKey("DiagonalHingeSide",3)) && side.getAxis()==EnumFacing.Axis.Y
                && reference!=null && !reference.isSliding() && reference.getPosition()!=2 && reference.motionHinge()>0 && reference.motionHinge()<1;
        double clickedHinge=inheritHinge?reference.motionHinge():0;
        if(!super.placeBlockAt(configured,player,world,pos,side,x,y,z,state))return false;
        net.minecraft.tileentity.TileEntity support=world.getTileEntity(pos.offset(side.getOpposite())),placed=world.getTileEntity(pos);
        if(inheritHinge && placed instanceof TileEntityProgrammableDiagonalTrapdoor){
            TileEntityProgrammableDiagonalTrapdoor leaf=(TileEntityProgrammableDiagonalTrapdoor)placed;
            // Pairing may choose new automatic sides; retain the edge that was actually clicked.
            reference.setHingeSide(clickedHinge<.5?1:2);
            double hinge=leaf.facing()==reference.facing()?clickedHinge:1-clickedHinge;
            leaf.setHingeSide(hinge<.5?1:2);
        }
        if(inheritRotation && side.getAxis()==EnumFacing.Axis.Y && support instanceof com.vandorlabs.tiles.TileEntityProgrammableDiagonalTrapdoor && placed instanceof com.vandorlabs.tiles.TileEntityProgrammableDiagonalTrapdoor)
            ((com.vandorlabs.tiles.TileEntityProgrammableDiagonalTrapdoor)placed).matchRotation((com.vandorlabs.tiles.TileEntityProgrammableDiagonalTrapdoor)support);
        return true;
    }
}
