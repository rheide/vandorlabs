package com.vandorlabs.neoforge.appliances;
import net.minecraft.core.BlockPos;import net.minecraft.world.*;import net.minecraft.world.entity.player.*;import net.minecraft.world.inventory.*;import net.minecraft.world.item.*;
/** Printer has only ink and paper input slots. Scanner exposes its retained original top item. */
public final class NativeApplianceMenu extends AbstractContainerMenu {
 public final NativeApplianceEntity tile;private final Container contents;private final int inputs;
 public NativeApplianceMenu(int window,Inventory inventory,NativeApplianceEntity tile){this(window,inventory,tile,tile.getLevel().isClientSide?new SimpleContainer(tile.getContainerSize()):tile);}
 private NativeApplianceMenu(int window,Inventory inventory,NativeApplianceEntity tile,Container contents){super(NativeApplianceBindings.MENU,window);this.tile=tile;this.contents=contents;inputs=tile.scanner()?2:3;if(tile.scanner())addSlot(input(contents,1,80,36));else{addSlot(input(contents,1,62,36));addSlot(input(contents,2,98,36));}addSlot(new Slot(contents,tile.outputSlot(),134,36){@Override public boolean mayPlace(ItemStack stack){return false;}});for(int row=0;row<3;row++)for(int col=0;col<9;col++)addSlot(new Slot(inventory,col+row*9+9,8+col*18,84+row*18));for(int col=0;col<9;col++)addSlot(new Slot(inventory,col,8+col*18,142));}
 public NativeApplianceMenu(int window,Inventory inventory,BlockPos pos){this(window,inventory,require(inventory.player,pos));}
 private static NativeApplianceEntity require(Player p,BlockPos pos){if(!p.level().hasChunkAt(pos)||!(p.level().getBlockEntity(pos) instanceof NativeApplianceEntity t))throw new IllegalArgumentException("Missing appliance");return t;}
 private Slot input(Container container,int index,int x,int y){return new Slot(container,index,x,y){@Override public boolean mayPlace(ItemStack stack){return tile.scanner()?NativeApplianceEntity.acceptedFrame(stack):index==1?stack.is(Items.INK_SAC):stack.is(Items.PAPER);}@Override public int getMaxStackSize(){return tile.scanner()?1:64;}};}
 @Override public boolean clickMenuButton(Player player,int id){
  return id==0&&player instanceof net.minecraft.server.level.ServerPlayer server&&stillValid(player)&&tile.activate(server);
 }
 @Override public boolean stillValid(Player player){return tile.stillValid(player);}
 @Override public ItemStack quickMoveStack(Player player,int index){if(index<0||index>=slots.size()||!stillValid(player))return ItemStack.EMPTY;var source=slots.get(index);if(!source.hasItem())return ItemStack.EMPTY;var stack=source.getItem();var original=stack.copy();if(index<inputs){if(!moveItemStackTo(stack,inputs,slots.size(),true))return ItemStack.EMPTY;}else{int target=tile.scanner()?0:stack.is(Items.INK_SAC)?0:stack.is(Items.PAPER)?1:-1;if(target<0||!slots.get(target).mayPlace(stack)||!moveItemStackTo(stack,target,target+1,false))return ItemStack.EMPTY;}if(stack.isEmpty())source.setByPlayer(ItemStack.EMPTY);else source.setChanged();return original;}
}
