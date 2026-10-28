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

/** Real module and hotbar slots attached to the original authenticated configuration container. */
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
        if(tile instanceof com.vandorlabs.tiles.TileEntityCockpit)for(int i=0;i<((com.vandorlabs.tiles.TileEntityCockpit)tile).controls.size();i++)binding.add(100+i);
        if(container instanceof ContainerAnimatedScreenSelector){int screen=((ContainerAnimatedScreenSelector)container).redstoneSlot;if(screen>=0)for(int i=0;i<8;i++)binding.add(1+screen*8+i);}
        for(int i=0;i<9;i++){VisibleSlot slot=new VisibleSlot(player.inventory,i);binding.hotbar.add(slot);append(container,slot);}
    }
    private static void append(Container container,Slot slot){slot.slotNumber=container.inventorySlots.size();container.inventorySlots.add(slot);container.inventoryItemStacks.add(ItemStack.EMPTY);}
    public static final class Binding {
        private final WeakReference<Container> owner;
        public final Map<Integer,ModuleSlot> modules=new LinkedHashMap<>();
        public final List<VisibleSlot> hotbar=new ArrayList<>();
        Binding(Container container){owner=new WeakReference<>(container);}
        public RedstoneChannelMember source(int selector){Container container=owner.get();return container==null?null:ContainerTransmitterSlot.resolve(com.vandorlabs.network.MessageTransmitterSlot.openedTile(container),selector);}
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
        ModuleSlot(Binding binding,int selector){super(new ModuleInventory(binding,selector),0);this.binding=binding;this.selector=selector;}
        @Override public int getSlotStackLimit(){return 1;}
        @Override public boolean isItemValid(ItemStack stack){return binding.source(selector)!=null&&ItemRedstoneTransmitter.valid(stack);}
        @Override public boolean canTakeStack(EntityPlayer player){return binding.source(selector)!=null&&binding.usable(player);}
    }
    /** A live one-slot inventory, refreshing only when its exact saved compound or member changes. */
    private static final class ModuleInventory extends InventoryBasic {
        final Binding binding;final int selector;private boolean ready,refreshing;private RedstoneChannelMember previous;private NBTTagCompound observed;
        ModuleInventory(Binding binding,int selector){super("Redstone Transmitter",false,1);this.binding=binding;this.selector=selector;refresh();ready=true;}
        @Override public int getInventoryStackLimit(){return 1;}
        private void refresh(){if(refreshing)return;RedstoneChannelMember member=binding.source(selector);NBTTagCompound slots=member==null?null:TransmitterSlots.savedSlots(member.channelTile());NBTTagCompound current=slots!=null&&slots.hasKey(member.transmitterSlot(),10)?slots.getCompoundTag(member.transmitterSlot()):null;if(member==previous&&current==observed)return;refreshing=true;try{previous=member;observed=current;super.setInventorySlotContents(0,current==null?ItemStack.EMPTY:new ItemStack(current));}finally{refreshing=false;}}
        @Override public ItemStack getStackInSlot(int slot){refresh();return super.getStackInSlot(slot);}
        @Override public ItemStack decrStackSize(int slot,int count){refresh();return super.decrStackSize(slot,count);}
        @Override public ItemStack removeStackFromSlot(int slot){refresh();ItemStack removed=super.removeStackFromSlot(slot);if(!removed.isEmpty())markDirty();return removed;}
        @Override public void markDirty(){super.markDirty();if(!ready||refreshing)return;RedstoneChannelMember member=binding.source(selector);if(member==null)return;net.minecraft.world.World world=member.channelTile().getWorld();if(world==null||ContainerTransmitterSlot.physicalWorld(world).isRemote)return;TransmitterSlots.set(member,super.getStackInSlot(0));previous=member;NBTTagCompound slots=TransmitterSlots.savedSlots(member.channelTile());observed=slots.hasKey(member.transmitterSlot(),10)?slots.getCompoundTag(member.transmitterSlot()):null;}
    }
}
