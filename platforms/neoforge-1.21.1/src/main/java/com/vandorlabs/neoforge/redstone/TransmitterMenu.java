package com.vandorlabs.neoforge.redstone;

import com.vandorlabs.neoforge.VandorLabsBootstrap;
import com.vandorlabs.redstone.ChannelList;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;

/** Editing session bound to the exact held object, hand and original selected hotbar slot. */
public final class TransmitterMenu extends AbstractContainerMenu {
    public final InteractionHand hand;
    private final ItemStack original;private final int selected;
    private final SimpleContainer preview=new SimpleContainer(1);
    public boolean synchronizedStack;
    public TransmitterMenu(int id,Inventory inventory,InteractionHand hand){
        super(VandorLabsBootstrap.TRANSMITTER_MENU.get(),id);this.hand=hand;original=inventory.player.getItemInHand(hand);selected=inventory.selected;
        preview.setItem(0,original.copy());addSlot(new Slot(preview,0,-10000,-10000){public boolean mayPlace(ItemStack stack){return false;}public boolean mayPickup(Player player){return false;}});
    }
    public ItemStack editorStack(){return preview.getItem(0);}
    @Override public void setItem(int slot,int stateId,ItemStack stack){super.setItem(slot,stateId,stack);synchronizedStack=true;}
    @Override public void initializeContents(int stateId,java.util.List<ItemStack> items,ItemStack carried){super.initializeContents(stateId,items,carried);synchronizedStack=true;}
    @Override public boolean stillValid(Player player){return player!=null&&player.isAlive()&&!player.isSpectator()&&player.getInventory().selected==selected&&player.getItemInHand(hand)==original&&NativeTransmitterItem.valid(original);}
    public boolean configure(Player player,ChannelList channels){if(!stillValid(player))return false;NativeTransmitterItem.configure(original,channels);player.getInventory().setChanged();preview.setItem(0,original.copy());broadcastChanges();return true;}
    @Override public ItemStack quickMoveStack(Player player,int index){return ItemStack.EMPTY;}
    @Override public void clicked(int slot,int button,ClickType type,Player player){} // Preview is never an inventory source or destination.
}
