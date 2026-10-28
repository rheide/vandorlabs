package com.vandorlabs.container;

import com.vandorlabs.redstone.*;
import com.vandorlabs.items.ItemRedstoneTransmitter;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.*;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;

/** Shared, real inventory slot. Vanilla window-click packets retain inventory ownership checks. */
public final class ContainerTransmitterSlot extends Container {
    public final RedstoneChannelMember member;
    public final int selector;
    private final InventoryBasic inventory;
    public ContainerTransmitterSlot(EntityPlayer player,RedstoneChannelMember member,int selector){
        this.member=member;this.selector=selector;
        final boolean[] ready={false};
        inventory=new InventoryBasic("Redstone Transmitter",false,1){@Override public int getInventoryStackLimit(){return 1;}@Override public void markDirty(){super.markDirty();if(ready[0]&&member.channelTile().getWorld()!=null&&!physicalWorld(member.channelTile().getWorld()).isRemote)TransmitterSlots.set(member,getStackInSlot(0));}};
        // Initialization must not publish a second logical change.
        inventory.setInventorySlotContents(0,TransmitterSlots.get(member));ready[0]=true;
        addSlotToContainer(new Slot(inventory,0,26,40){@Override public boolean isItemValid(ItemStack stack){return ItemRedstoneTransmitter.valid(stack);}@Override public int getSlotStackLimit(){return 1;}});
        for(int row=0;row<3;row++)for(int col=0;col<9;col++)addSlotToContainer(new Slot(player.inventory,9+row*9+col,8+18*col,86+18*row));
        for(int col=0;col<9;col++)addSlotToContainer(new Slot(player.inventory,col,8+18*col,144));
    }
    public static net.minecraft.world.World physicalWorld(net.minecraft.world.World world){return world instanceof com.vandorlabs.vehicle.VehicleWorld?((com.vandorlabs.vehicle.VehicleWorld)world).parent:world;}
    public static boolean usable(EntityPlayer player,RedstoneChannelMember member){TileEntity tile=member.channelTile();if(tile.getWorld() instanceof com.vandorlabs.vehicle.VehicleWorld){com.vandorlabs.vehicle.VehicleWorld view=(com.vandorlabs.vehicle.VehicleWorld)tile.getWorld();com.vandorlabs.vehicle.EntityGroundVehicle craft=view.craft;if(craft==null||craft.isDead||craft.ownershipBlocked||craft.transferInProgress||view.getTileEntity(tile.getPos())!=tile||craft.world!=player.world)return false;net.minecraft.util.math.Vec3d at=craft.toWorld(new net.minecraft.util.math.Vec3d(tile.getPos()).addVector(.5,.5,.5));BlockPos pos=new BlockPos(at);return player.getDistanceSq(at.x,at.y,at.z)<=64&&craft.world.isBlockLoaded(pos)&&craft.world.isBlockModifiable(player,pos)&&player.canPlayerEdit(pos,net.minecraft.util.EnumFacing.UP,player.getHeldItemMainhand())&&!player.isDead&&!player.isSpectator();}return tile.getWorld()!=null&&tile.getWorld().isBlockLoaded(tile.getPos())&&tile.getWorld().getTileEntity(tile.getPos())==tile&&com.vandorlabs.items.ConfigurationAccess.canEditDialog(player,tile.getWorld(),tile.getPos());}
    @Override public boolean canInteractWith(EntityPlayer player){return usable(player,member);}
    public static void open(EntityPlayer player,RedstoneChannelMember member,int selector,boolean edit){TileEntity tile=member.channelTile();BlockPos pos=tile.getPos();int id=(edit?2000:1000)+selector;if(tile.getWorld() instanceof com.vandorlabs.vehicle.VehicleWorld){com.vandorlabs.vehicle.EntityGroundVehicle craft=((com.vandorlabs.vehicle.VehicleWorld)tile.getWorld()).craft;if(craft==null)return;player.openGui(com.vandorlabs.VandorLabs.instance,id,player.world,craft.getEntityId(),pos.getX()|(pos.getY()<<6)|(pos.getZ()<<12),Integer.MIN_VALUE);}else player.openGui(com.vandorlabs.VandorLabs.instance,id,player.world,pos.getX(),pos.getY(),pos.getZ());}
    public static RedstoneChannelMember resolve(net.minecraft.world.World world,int x,int y,int z,int selector){if(z==Integer.MIN_VALUE){net.minecraft.entity.Entity entity=world.getEntityByID(x);if(!(entity instanceof com.vandorlabs.vehicle.EntityGroundVehicle))return null;com.vandorlabs.vehicle.EntityGroundVehicle craft=(com.vandorlabs.vehicle.EntityGroundVehicle)entity;return craft.view==null?null:resolve(craft.view.getTileEntity(new BlockPos(y&63,(y>>6)&63,(y>>12)&63)),selector);}BlockPos pos=new BlockPos(x,y,z);return world.isBlockLoaded(pos)?resolve(world.getTileEntity(pos),selector):null;}

    @Override public ItemStack transferStackInSlot(EntityPlayer player,int index){if(index<0||index>=inventorySlots.size())return ItemStack.EMPTY;Slot slot=inventorySlots.get(index);if(!slot.getHasStack())return ItemStack.EMPTY;ItemStack stack=slot.getStack(),copy=stack.copy();if(index==0){if(!mergeItemStack(stack,1,37,true))return ItemStack.EMPTY;}else if(!ItemRedstoneTransmitter.valid(stack)||!mergeItemStack(stack,0,1,false))return ItemStack.EMPTY;if(stack.isEmpty())slot.putStack(ItemStack.EMPTY);else slot.onSlotChanged();slot.onTake(player,stack);return copy;}
    public static RedstoneChannelMember resolve(TileEntity tile,int selector){
        if(selector==0)return tile instanceof RedstoneChannelMember?(RedstoneChannelMember)tile:null;
        if(selector>=100&&tile instanceof com.vandorlabs.tiles.TileEntityCockpit){java.util.List<com.vandorlabs.tiles.TileEntityCockpit.Control> list=((com.vandorlabs.tiles.TileEntityCockpit)tile).controls;int i=selector-100;return i<list.size()?list.get(i):null;}
        if(selector>=1&&selector<=16&&tile instanceof com.vandorlabs.tiles.TileEntityAnimatedScreenSelector){com.vandorlabs.tiles.RedstoneScreenContents contents=((com.vandorlabs.tiles.TileEntityAnimatedScreenSelector)tile).redstoneScreen((selector-1)/8);int i=(selector-1)%8;return i<contents.rows().size()?contents.rows().get(i):null;}
        return null;
    }
}
