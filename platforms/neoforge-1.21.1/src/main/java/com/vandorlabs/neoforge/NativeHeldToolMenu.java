package com.vandorlabs.neoforge;

import java.util.function.Predicate;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;

/** One authenticated held-object session and a non-transferable, synchronized editor preview. */
public abstract class NativeHeldToolMenu extends AbstractContainerMenu {
    public final InteractionHand hand;
    private final ItemStack original;private final int selected;private final Predicate<ItemStack> valid;
    private final SimpleContainer preview=new SimpleContainer(1);
    public boolean synchronizedStack;
    protected NativeHeldToolMenu(MenuType<?> type,int id,Inventory inventory,InteractionHand hand,Predicate<ItemStack> valid){
        super(type,id);this.hand=hand;this.valid=java.util.Objects.requireNonNull(valid);original=inventory.player.getItemInHand(hand);selected=inventory.selected;
        preview.setItem(0,original.copy());addSlot(new Slot(preview,0,-10000,-10000){@Override public boolean mayPlace(ItemStack stack){return false;}@Override public boolean mayPickup(Player player){return false;}});
    }
    public final ItemStack editorStack(){return preview.getItem(0);}
    protected final ItemStack heldStack(){return original;}
    protected final void synchronizeTool(Player player){player.getInventory().setChanged();preview.setItem(0,original.copy());broadcastChanges();}
    @Override public void setItem(int slot,int stateId,ItemStack stack){super.setItem(slot,stateId,stack);synchronizedStack=true;}
    @Override public void initializeContents(int stateId,java.util.List<ItemStack> items,ItemStack carried){super.initializeContents(stateId,items,carried);synchronizedStack=true;}
    @Override public final boolean stillValid(Player player){return player!=null&&player.isAlive()&&!player.isSpectator()&&player.getInventory().selected==selected&&player.getItemInHand(hand)==original&&valid.test(original);}
    @Override public final ItemStack quickMoveStack(Player player,int index){return ItemStack.EMPTY;}
    @Override public final void clicked(int slot,int button,ClickType type,Player player){} // A preview never becomes an inventory owner.
}
