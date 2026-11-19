package com.vandorlabs.neoforge.builders;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.*;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;
/** Seven stable physical owner slots plus ordinary player inventory; client copies are display only. */
public final class NativeBuilderMenu extends AbstractContainerMenu{
 public final NativeBuilderEntity tile;private final Container contents;private final Object block;
 private final ContainerData values;private int page;
 public void page(int value){page=Math.clamp(value,0,1);}
 public boolean digitizing(){return value(9)==1;}
 private boolean active(int slot){return page==0&&!(digitizing()&&slot==1);}
 public NativeBuilderMenu(MenuType<?> type,int id,Inventory inventory,NativeBuilderEntity owner){
  super(type,id);tile=java.util.Objects.requireNonNull(owner);block=owner.getBlockState().getBlock();boolean server=inventory.player.level() instanceof ServerLevel;contents=server?owner:new SimpleContainer(7);
  var layout=new com.vandorlabs.ui.SynthesisDialog.Layout(true);int[] xs=new int[7];for(int n=0;n<7;n++)xs[n]=layout.machineX(n);for(int i=0;i<7;i++){final int slot=i;addSlot(new Slot(contents,i,xs[i],layout.machineY){@Override public boolean isActive(){return active(slot);}@Override public boolean mayPlace(ItemStack stack){return mutable(inventory.player)&&slot!=1&&tile.canPlaceItem(slot,stack);}@Override public boolean mayPickup(Player p){return mutable(p);}@Override public int getMaxStackSize(){return slot>=2||slot==0&&digitizing()?1:64;}});}
  for(int row=0;row<3;row++)for(int col=0;col<9;col++)addSlot(new Slot(inventory,col+row*9+9,layout.inventoryX+col*18,row*18+layout.inventoryY){@Override public boolean isActive(){return page==0;}});
  for(int col=0;col<9;col++)addSlot(new Slot(inventory,col,layout.inventoryX+col*18,layout.hotbarY){@Override public boolean isActive(){return page==0;}});
  values=server?new ContainerData(){public int getCount(){return 11;}public void set(int i,int v){}public int get(int i){return switch(i){case 0->tile.selectedDesign();case 1->tile.buildDesign();case 2->Math.round(tile.buildProgress()*8000);case 3->tile.batteryEnergy();case 4->tile.batteryCapacity();case 5->tile.connected()?1:0;case 6->tile.processing()?1:0;case 7->tile.activation();case 8->tile.enabled()?1:0;case 9->tile.mode();default->tile.choices();};}}:new SimpleContainerData(11);addDataSlots(values);
 }
 public NativeBuilderMenu(MenuType<?> type,int id,Inventory inventory,BlockPos pos){this(type,id,inventory,require(inventory.player,pos));}
 private static NativeBuilderEntity require(Player player,BlockPos pos){if(!player.level().hasChunkAt(pos)||!(player.level().getBlockEntity(pos) instanceof NativeBuilderEntity owner))throw new IllegalArgumentException("Missing loaded Builder");return owner;}
 public int value(int index){return index>=0&&index<11?(index==3||index==4?values.get(index)&0xffff:values.get(index)):0;}public Container contents(){return contents;}
 @Override public boolean stillValid(Player p){return tile.getBlockState().getBlock()==block&&tile.production()&&tile.formed()&&!tile.inert()&&tile.canKeepUse(p)&&(!(p.level() instanceof ServerLevel)||contents==tile);}
 private boolean mutable(Player p){return stillValid(p)&&!tile.transactionBlocked()&&(!(p.level() instanceof ServerLevel)||tile.available());}
 @Override public void clicked(int slot,int button,ClickType type,Player player){if(mutable(player)&&!(slot==1&&tile.digitizing()))super.clicked(slot,button,type,player);}
 @Override public boolean clickMenuButton(Player player,int mode){if(!(player instanceof ServerPlayer)||mode<0||mode>1||!tile.synthesis()||!mutable(player))return false;boolean changed=tile.setMode(mode);if(!changed&&tile.mode()!=mode)player.displayClientMessage(net.minecraft.network.chat.Component.literal("Wait for processing and remove the input/output before changing mode."),true);broadcastChanges();return changed;}
 public static boolean apply(ServerPlayer player,NativeBuilderEdit edit){if(edit==null||!player.getServer().isSameThread()||!(player.containerMenu instanceof NativeBuilderMenu menu)||menu.containerId!=edit.window()||!menu.tile.getBlockPos().equals(edit.pos())||!menu.mutable(player))return false;if(edit.operation()==0)menu.tile.selectDesign(edit.value());else menu.tile.configureRedstone(edit.value(),edit.channels());menu.broadcastChanges();return true;}
 @Override public ItemStack quickMoveStack(Player p,int index){if(!mutable(p)||index<0||index>=slots.size()||index==1&&tile.digitizing())return ItemStack.EMPTY;var slot=slots.get(index);if(!slot.hasItem())return ItemStack.EMPTY;var stack=slot.getItem();var before=stack.copy();if(index<7){if(!moveItemStackTo(stack,7,43,true))return ItemStack.EMPTY;}else{boolean moved=false;for(int target:new int[]{0,2,4,5,6,3})if(slots.get(target).mayPlace(stack)&&moveItemStackTo(stack,target,target+1,false)){moved=true;break;}if(!moved){if(index<34){if(!moveItemStackTo(stack,34,43,false))return ItemStack.EMPTY;}else if(!moveItemStackTo(stack,7,34,false))return ItemStack.EMPTY;}}if(stack.isEmpty())slot.set(ItemStack.EMPTY);else slot.setChanged();if(stack.getCount()==before.getCount())return ItemStack.EMPTY;slot.onTake(p,stack);return before;}
}
