package com.vandorlabs.neoforge.builders;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.*;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;
/** Four physical owner slots plus ordinary player inventory; client copies are display only. */
public final class NativeBuilderMenu extends AbstractContainerMenu{
 public final NativeBuilderEntity tile;private final Container contents;private final Object block;
 private final ContainerData values;
 public NativeBuilderMenu(MenuType<?> type,int id,Inventory inventory,NativeBuilderEntity owner){
  super(type,id);tile=java.util.Objects.requireNonNull(owner);block=owner.getBlockState().getBlock();boolean server=inventory.player.level() instanceof ServerLevel;contents=server?owner:new SimpleContainer(4);
  int[] xs={18,104,190,276};for(int i=0;i<4;i++){final int slot=i;addSlot(new Slot(contents,i,xs[i],136){@Override public boolean mayPlace(ItemStack stack){return mutable(inventory.player)&&slot!=1&&tile.canPlaceItem(slot,stack);}@Override public boolean mayPickup(Player p){return mutable(p);}@Override public int getMaxStackSize(){return slot>=2?1:64;}});}
  for(int row=0;row<3;row++)for(int col=0;col<9;col++)addSlot(new Slot(inventory,col+row*9+9,76+col*18,row*18+162));
  for(int col=0;col<9;col++)addSlot(new Slot(inventory,col,76+col*18,220));
  values=server?new ContainerData(){public int getCount(){return 9;}public void set(int i,int v){}public int get(int i){return switch(i){case 0->tile.selectedDesign();case 1->tile.buildDesign();case 2->Math.round(tile.buildProgress()*8000);case 3->tile.batteryEnergy();case 4->tile.batteryCapacity();case 5->tile.connected()?1:0;case 6->tile.processing()?1:0;case 7->tile.activation();default->tile.enabled()?1:0;};}}:new SimpleContainerData(9);addDataSlots(values);
 }
 public NativeBuilderMenu(MenuType<?> type,int id,Inventory inventory,BlockPos pos){this(type,id,inventory,require(inventory.player,pos));}
 private static NativeBuilderEntity require(Player player,BlockPos pos){if(!player.level().hasChunkAt(pos)||!(player.level().getBlockEntity(pos) instanceof NativeBuilderEntity owner))throw new IllegalArgumentException("Missing loaded Builder");return owner;}
 public int value(int index){return index>=0&&index<9?values.get(index):0;}public Container contents(){return contents;}
 @Override public boolean stillValid(Player p){return tile.getBlockState().getBlock()==block&&tile.synthesis()&&tile.formed()&&!tile.inert()&&tile.canKeepUse(p)&&(!(p.level() instanceof ServerLevel)||contents==tile);}
 private boolean mutable(Player p){return stillValid(p)&&!tile.transactionBlocked()&&(!(p.level() instanceof ServerLevel)||tile.available());}
 @Override public void clicked(int slot,int button,ClickType type,Player player){if(mutable(player))super.clicked(slot,button,type,player);}
 public static boolean apply(ServerPlayer player,NativeBuilderEdit edit){if(edit==null||!player.getServer().isSameThread()||!(player.containerMenu instanceof NativeBuilderMenu menu)||menu.containerId!=edit.window()||!menu.tile.getBlockPos().equals(edit.pos())||!menu.mutable(player))return false;if(edit.operation()==0)menu.tile.selectDesign(edit.value());else menu.tile.configureRedstone(edit.value(),edit.channels());menu.broadcastChanges();return true;}
 @Override public ItemStack quickMoveStack(Player p,int index){if(!mutable(p)||index<0||index>=slots.size())return ItemStack.EMPTY;var slot=slots.get(index);if(!slot.hasItem())return ItemStack.EMPTY;var stack=slot.getItem();var before=stack.copy();if(index<4){if(!moveItemStackTo(stack,4,40,true))return ItemStack.EMPTY;}else{boolean moved=false;for(int target:new int[]{0,2,3})if(slots.get(target).mayPlace(stack)&&moveItemStackTo(stack,target,target+1,false)){moved=true;break;}if(!moved){if(index<31){if(!moveItemStackTo(stack,31,40,false))return ItemStack.EMPTY;}else if(!moveItemStackTo(stack,4,31,false))return ItemStack.EMPTY;}}if(stack.isEmpty())slot.set(ItemStack.EMPTY);else slot.setChanged();if(stack.getCount()==before.getCount())return ItemStack.EMPTY;slot.onTake(p,stack);return before;}
}
