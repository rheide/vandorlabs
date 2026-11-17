package com.vandorlabs.blocks;

import com.vandorlabs.tiles.TileEntityChipAppliance;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.*;
import net.minecraft.util.math.*;
import net.minecraft.world.World;

/** Ordinary appliance housing with real embedded controls and a persistent original document. */
public final class BlockChipAppliance extends BlockVandorDirectional {
    private final boolean scanner;
    public BlockChipAppliance(String name,boolean scanner){super(name);this.scanner=scanner;}
    @Override public boolean hasTileEntity(IBlockState state){return true;}
    @Override public TileEntity createTileEntity(World world,IBlockState state){return new TileEntityChipAppliance(scanner);}
    public static MachineMountSurface front(TileEntityChipAppliance tile,boolean button){EnumFacing facing=tile.getWorld().getBlockState(tile.getPos()).getValue(FACING);Vec3d p=MachineMountSurface.rotate(new Vec3d(button?.25:.75,.75,-.015),facing,1,1);return new MachineMountSurface(p,facing,button?.20:.375,button?.20:.375);}
    @Override public boolean onBlockActivated(World world,BlockPos pos,IBlockState state,EntityPlayer actor,EnumHand hand,EnumFacing side,float x,float y,float z){
        TileEntity raw=world.getTileEntity(pos);if(!(raw instanceof TileEntityChipAppliance))return false;TileEntityChipAppliance tile=(TileEntityChipAppliance)raw;if(!tile.isUsableByPlayer(actor))return false;
        if(!actor.isSneaking()&&actor.getHeldItem(hand).getItem()!=com.vandorlabs.items.ModItems.CONFIGURIZER){
            EnumFacing facing=state.getValue(FACING);
            MachineMountSurface chip=front(tile,false),button=front(tile,true);Vec3d hit=new Vec3d(x,y,z);
            if(side==facing&&near(hit,chip)){return TokenSlotInteractions.activate(tile,actor,hand);}
            if(side==facing&&near(hit,button)){if(!world.isRemote)tile.activate(actor);return true;}
            if(scanner&&side==EnumFacing.UP&&x>=.125&&x<=.875&&z>=.125&&z<=.875){
                if(!world.isRemote){ItemStack held=actor.getHeldItem(hand);if(held.isEmpty()){ItemStack original=tile.removeStackFromSlot(1);if(!actor.inventory.addItemStackToInventory(original)&&!original.isEmpty())actor.dropItem(original,false);}else if(tile.getStackInSlot(1).isEmpty()&&tile.isItemValidForSlot(1,held)){ItemStack original=held.copy();original.setCount(1);tile.setInventorySlotContents(1,original);if(!actor.capabilities.isCreativeMode)held.shrink(1);}}return true;
            }
        }
        if(!world.isRemote)actor.openGui(com.vandorlabs.VandorLabs.instance,com.vandorlabs.GuiHandler.GUI_TOKEN_SLOT,world,pos.getX(),pos.getY(),pos.getZ());return true;
    }
    private static boolean near(Vec3d hit,MachineMountSurface surface){Vec3d p=hit.subtract(surface.center);return Math.abs(p.dotProduct(surface.u))<=surface.width/2&&Math.abs(p.dotProduct(surface.v))<=surface.height/2;}
    @Override public void breakBlock(World world,BlockPos pos,IBlockState state){if(!world.isRemote&&!com.vandorlabs.vehicle.VehicleTransfer.isMutating(world)){TileEntity raw=world.getTileEntity(pos);if(raw instanceof TileEntityChipAppliance){TileEntityChipAppliance tile=(TileEntityChipAppliance)raw;for(int i=0;i<tile.getSizeInventory();i++){ItemStack stack=tile.removeStackFromSlot(i);if(!stack.isEmpty())net.minecraft.inventory.InventoryHelper.spawnItemStack(world,pos.getX()+.5,pos.getY()+.5,pos.getZ()+.5,stack);}}}super.breakBlock(world,pos,state);}
}
