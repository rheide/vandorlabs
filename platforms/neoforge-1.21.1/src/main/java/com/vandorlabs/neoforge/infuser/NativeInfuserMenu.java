package com.vandorlabs.neoforge.infuser;
import net.minecraft.core.BlockPos;
import com.vandorlabs.ui.InfuserDialog;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;
/** Vanilla furnace positions and real server-owned slots, with an isolated client slot shadow. */
public final class NativeInfuserMenu extends AbstractContainerMenu{
 public final NativeInfuserEntity tile;private final Container contents;private final ContainerData values;
 public NativeInfuserMenu(MenuType<?> type,int window,Inventory inventory,NativeInfuserEntity tile){super(type,window);this.tile=java.util.Objects.requireNonNull(tile);boolean server=inventory.player.level() instanceof ServerLevel;contents=server?tile:new SimpleContainer(3);
  for(int i=0;i<3;i++){final int index=i;addSlot(new Slot(contents,i,InfuserDialog.slotX(i),InfuserDialog.slotY(i)){@Override public boolean mayPlace(ItemStack stack){return index!=2&&mutable(inventory.player)&&tile.canPlaceItem(index,stack);}@Override public boolean mayPickup(Player player){return mutable(player);}});}
  for(int row=0;row<3;row++)for(int col=0;col<9;col++)addSlot(new Slot(inventory,col+row*9+9,InfuserDialog.slotX(3+row*9+col),InfuserDialog.slotY(3+row*9+col)));for(int col=0;col<9;col++)addSlot(new Slot(inventory,col,InfuserDialog.slotX(30+col),InfuserDialog.slotY(30+col)));
  values=server?new ContainerData(){public int getCount(){return 4;}public void set(int i,int value){}public int get(int i){return switch(i){case 0->tile.fuelRemaining();case 1->tile.fuelDuration();case 2->tile.progress();default->tile.hasSunlight()?1:0;};}}:new SimpleContainerData(4);addDataSlots(values);
 }
 public NativeInfuserMenu(MenuType<?> type,int window,Inventory inventory,BlockPos pos){this(type,window,inventory,require(inventory.player,pos));}
 private static NativeInfuserEntity require(Player player,BlockPos pos){if(!player.level().hasChunkAt(pos)||!(player.level().getBlockEntity(pos) instanceof NativeInfuserEntity tile))throw new IllegalArgumentException("Missing loaded Infuser");return tile;}
 public Container contents(){return contents;}public int value(int index){return index>=0&&index<4?values.get(index):0;}
 @Override public boolean stillValid(Player player){return tile.canUse(player)&&(!(player.level() instanceof ServerLevel)||contents==tile&&tile.available());}
 private boolean mutable(Player player){return stillValid(player)&&(!(player.level() instanceof ServerLevel)||tile.available());}
 @Override public void clicked(int slot,int button,ClickType click,Player player){if(mutable(player))super.clicked(slot,button,click,player);}
 @Override public ItemStack quickMoveStack(Player player,int index){if(!mutable(player)||index<0||index>=slots.size())return ItemStack.EMPTY;var slot=slots.get(index);if(!slot.hasItem())return ItemStack.EMPTY;var stack=slot.getItem();var before=stack.copy();if(index<3){if(!moveItemStackTo(stack,3,39,true))return ItemStack.EMPTY;}else if(NativeInfuserEntity.acceptsInput(stack)){if(!moveItemStackTo(stack,0,1,false))return ItemStack.EMPTY;}else if(NativeInfuserEntity.fuelTicks(stack)>0){if(!moveItemStackTo(stack,1,2,false))return ItemStack.EMPTY;}else if(index<30){if(!moveItemStackTo(stack,30,39,false))return ItemStack.EMPTY;}else if(!moveItemStackTo(stack,3,30,false))return ItemStack.EMPTY;if(stack.isEmpty())slot.set(ItemStack.EMPTY);else slot.setChanged();if(stack.getCount()==before.getCount())return ItemStack.EMPTY;slot.onTake(player,stack);return before;}
}
