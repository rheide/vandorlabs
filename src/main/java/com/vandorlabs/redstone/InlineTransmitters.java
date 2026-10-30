package com.vandorlabs.redstone;

import com.vandorlabs.container.*;
import com.vandorlabs.items.ItemRedstoneTransmitter;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.*;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraftforge.fml.relauncher.*;
import java.lang.ref.WeakReference;
import java.util.*;

/** Shared settings bindings; old inventory indices remain inert for window compatibility. */
public final class InlineTransmitters {
    private static final Map<Container,Binding> BINDINGS=Collections.synchronizedMap(new WeakHashMap<Container,Binding>());
    private InlineTransmitters(){}
    public static Binding binding(Container container){return BINDINGS.get(container);}
    public static void attach(Container container,EntityPlayer player){
        if(BINDINGS.containsKey(container)||container instanceof ContainerTransmitterSlot||container instanceof ContainerTransmitterItem)return;
        TileEntity tile=com.vandorlabs.network.MessageTransmitterSlot.openedTile(container);
        if(tile==null||tile instanceof com.vandorlabs.tiles.TileEntitySecuredDisplay)return;
        Binding binding=new Binding(container);BINDINGS.put(container,binding);
        if(tile instanceof RedstoneChannelMember)binding.add(0);
        if(container instanceof ContainerAnimatedScreenSelector)for(int screen=0;screen<2;screen++)for(int i=0;i<8;i++)binding.add(1+screen*8+i);
        for(int i=0;i<9;i++){VisibleSlot slot=new VisibleSlot(player.inventory,i);binding.hotbar.add(slot);append(container,slot);}
    }
    private static void append(Container container,Slot slot){slot.slotNumber=container.inventorySlots.size();container.inventorySlots.add(slot);container.inventoryItemStacks.add(ItemStack.EMPTY);}
    public static final class Binding {
        private final WeakReference<Container> owner;
        public final Map<Integer,ModuleSlot> modules=new LinkedHashMap<>();
        public final List<VisibleSlot> hotbar=new ArrayList<>();
        Binding(Container container){owner=new WeakReference<>(container);}
        /** Old row slots retain their indices but never alias the shared module inventory. */
        public RedstoneChannelMember source(int selector){Container container=owner.get();if(container instanceof ContainerAnimatedScreenSelector&&selector>=1&&selector<=16)return null;return container==null?null:ContainerTransmitterSlot.resolve(com.vandorlabs.network.MessageTransmitterSlot.openedTile(container),selector);}
        public boolean usable(EntityPlayer player){Container container=owner.get();return container!=null&&player.openContainer==container&&container.canInteractWith(player);}
        void add(int selector){Container container=owner.get();if(container==null)return;ModuleSlot slot=new ModuleSlot(this,selector);modules.put(selector,slot);append(container,slot);}
        public void hide(){for(ModuleSlot slot:modules.values()){slot.visible=false;slot.xPos=slot.yPos=-10000;}for(VisibleSlot slot:hotbar){slot.visible=false;slot.xPos=slot.yPos=-10000;}}
    }
    public static class VisibleSlot extends Slot {
        public boolean visible;
        VisibleSlot(IInventory inventory,int index){super(inventory,index,-10000,-10000);}
        @Override @SideOnly(Side.CLIENT) public boolean isEnabled(){return visible;}
    }
    public static final class ModuleSlot extends VisibleSlot {
        public final int selector;private final Binding binding;
        ModuleSlot(Binding binding,int selector){super(new InventoryBasic("Transmission settings",false,1),0);this.binding=binding;this.selector=selector;}
        @Override public int getSlotStackLimit(){return 1;}
        @Override public boolean isItemValid(ItemStack stack){return false;}
        @Override public boolean canTakeStack(EntityPlayer player){return false;}
    }
}
