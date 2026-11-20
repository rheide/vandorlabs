package com.vandorlabs.ports;

import net.minecraft.inventory.ItemStackHelper;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.NonNullList;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.items.CapabilityItemHandler;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemHandlerHelper;

/** Nine bounded stack slots. No loose-inventory drops accompany its resource-bearing block item. */
public final class MatterPortTile extends ResourcePortTile {
    public static final int SLOTS=9;
    private final NonNullList<ItemStack> items=NonNullList.withSize(SLOTS,ItemStack.EMPTY);
    private final IItemHandler[] faces=new IItemHandler[6];
    private final int[] pullCursors=new int[6];
    public MatterPortTile(){for(int i=0;i<6;i++)faces[i]=new Face(i);}
    public ItemStack stored(int slot){checkSlot(slot);return items.get(slot).copy();}
    @Override protected int storedUnits(){int total=0;for(ItemStack stack:items)total+=stack.getCount();return total;}
    @Override protected int simulateOutputCapacity(int side,int maximum){Object raw=neighbor(side);if(!(raw instanceof IItemHandler))return 0;int total=0;for(ItemStack stack:items)if(!stack.isEmpty()&&total<maximum){int offer=Math.min(stack.getCount(),maximum-total);ItemStack remainder=ItemHandlerHelper.insertItem((IItemHandler)raw,portion(stack,offer),true);total+=offer-Math.min(offer,remainder.getCount());}return total;}
    private static void checkSlot(int slot){if(slot<0||slot>=SLOTS)throw new IllegalArgumentException("Invalid matter port slot");}
    private static boolean same(ItemStack a,ItemStack b){return ItemStack.areItemsEqual(a,b)&&ItemStack.areItemStackTagsEqual(a,b);}
    private static ItemStack portion(ItemStack stack,int amount){if(amount<=0)return ItemStack.EMPTY;ItemStack part=stack.copy();part.setCount(amount);return part;}
    private int room(int slot,ItemStack stack){ItemStack current=items.get(slot);return current.isEmpty()?Math.min(64,stack.getMaxStackSize()):same(current,stack)?Math.max(0,Math.min(64,current.getMaxStackSize())-current.getCount()):0;}
    private int insert(int slot,ItemStack stack,int max,boolean commit){int count=Math.min(Math.min(stack.getCount(),Math.max(0,max)),room(slot,stack));if(commit&&count>0){ItemStack current=items.get(slot);if(current.isEmpty())items.set(slot,portion(stack,count));else current.grow(count);}return count;}
    private int room(ItemStack stack){int total=0;for(int i=0;i<SLOTS;i++)total+=room(i,stack);return total;}
    private int insert(ItemStack stack){int left=stack.getCount();for(int i=0;i<SLOTS&&left>0;i++)left-=insert(i,stack,left,true);return stack.getCount()-left;}
    @Override protected Object findHandler(TileEntity tile,EnumFacing side){return ResourcePortConnections.itemHandler(tile,side);}
    @Override protected boolean compatible(Object handler,boolean receiving){return handler instanceof IItemHandler&&((IItemHandler)handler).getSlots()>0;}
    @Override protected void transferTick(){
        if(settings().pullInputs)for(int side=0;side<6;side++)if(input(side)){
            Object raw=neighbor(side);if(!(raw instanceof IItemHandler))continue;IItemHandler source=(IItemHandler)raw;int slots=Math.max(0,source.getSlots());if(slots==0)continue;
            int cursor=pullCursors[side]%slots;
            // Large foreign inventories make bounded progress without a full per-tick scan.
            for(int attempt=0;attempt<Math.min(32,slots);attempt++){int slot=cursor;cursor=cursor+1==slots?0:cursor+1;ItemStack offer=source.extractItem(slot,64,true);if(offer.isEmpty())continue;int count=Math.min(offer.getCount(),room(offer));if(count<=0)continue;
                ItemStack received=source.extractItem(slot,count,false);if(received.isEmpty())continue;int accepted=insert(received);if(accepted<received.getCount())ItemHandlerHelper.insertItem(source,portion(received,received.getCount()-accepted),false);if(accepted>0)transferred(side,accepted,false);
            }
            pullCursors[side]=cursor;
        }
        for(int slot=0;slot<SLOTS&&availableOutput()>0;slot++){
            ItemStack stack=items.get(slot);if(stack.isEmpty())continue;int offer=boundedOutput(stack.getCount());
            for(int side=0;side<6;side++){capacities[side]=0;if(output(side)){Object raw=neighbor(side);if(raw instanceof IItemHandler){ItemStack remainder=ItemHandlerHelper.insertItem((IItemHandler)raw,portion(stack,offer),true);capacities[side]=offer-Math.min(offer,remainder.getCount());}}}
            allocate(stack.getCount());
            for(int side=0;side<6;side++)if(grants[side]>0&&!stack.isEmpty()){
                Object raw=neighbor(side);if(!(raw instanceof IItemHandler))continue;int count=(int)Math.min(grants[side],boundedOutput(stack.getCount()));if(count<=0)continue;
                ItemStack remainder=ItemHandlerHelper.insertItem((IItemHandler)raw,portion(stack,count),false);int accepted=count-Math.min(count,remainder.getCount());if(accepted>0){stack.shrink(accepted);transferred(side,accepted,true);}
            }
            if(stack.isEmpty())items.set(slot,ItemStack.EMPTY);
        }
    }
    @Override protected void writeContents(NBTTagCompound tag){ItemStackHelper.saveAllItems(tag,items);}
    @Override protected void readContents(NBTTagCompound tag){
        if(tag.hasKey("PortEnergy")||tag.hasKey("PortFluid"))throw new IllegalArgumentException("Mixed matter port resource owners");
        items.clear();if(!tag.hasKey("Items"))return;
        if(!tag.hasKey("Items",9))throw new IllegalArgumentException("Invalid saved matter port inventory");
        net.minecraft.nbt.NBTTagList rows=(net.minecraft.nbt.NBTTagList)tag.getTag("Items");
        if(rows.tagCount()>SLOTS||rows.tagCount()>0&&rows.getTagType()!=10)throw new IllegalArgumentException("Invalid saved matter port inventory");
        boolean[] seen=new boolean[SLOTS];
        for(int index=0;index<rows.tagCount();index++){
            NBTTagCompound row=rows.getCompoundTagAt(index);if(!row.hasKey("Slot",1))throw new IllegalArgumentException("Invalid saved matter port slot");int slot=row.getByte("Slot")&255;checkSlot(slot);
            if(seen[slot])throw new IllegalArgumentException("Duplicate saved matter port slot");seen[slot]=true;
            ItemStack stack=new ItemStack(row);NBTTagCompound raw=row.copy();raw.removeTag("Slot");
            if(stack.isEmpty()||stack.getCount()>Math.min(64,stack.getMaxStackSize())||!stack.writeToNBT(new NBTTagCompound()).equals(raw))throw new IllegalArgumentException("Unknown saved matter port stack");
            items.set(slot,stack);
        }
    }
    @Override public boolean hasCapability(Capability<?> cap,EnumFacing side){return cap==CapabilityItemHandler.ITEM_HANDLER_CAPABILITY?!inert()&&side!=null&&settings().face(side.getIndex())!=ResourcePortSettings.Face.DISABLED:super.hasCapability(cap,side);}
    @Override public <T>T getCapability(Capability<T> cap,EnumFacing side){if(cap==CapabilityItemHandler.ITEM_HANDLER_CAPABILITY)return hasCapability(cap,side)?CapabilityItemHandler.ITEM_HANDLER_CAPABILITY.cast(faces[side.getIndex()]):null;return super.getCapability(cap,side);}
    private final class Face implements IItemHandler {
        final int side;Face(int side){this.side=side;}
        @Override public int getSlots(){return SLOTS;}
        @Override public ItemStack getStackInSlot(int slot){return stored(slot);}
        @Override public ItemStack insertItem(int slot,ItemStack stack,boolean simulate){checkSlot(slot);if(stack.isEmpty()||!input(side)||!transferAllowed())return stack;int accepted=insert(slot,stack,stack.getCount(),!simulate);if(!simulate&&accepted>0)transferred(side,accepted,false);return portion(stack,stack.getCount()-accepted);}
        @Override public ItemStack extractItem(int slot,int max,boolean simulate){checkSlot(slot);if(max<=0||!output(side)||!transferAllowed())return ItemStack.EMPTY;ItemStack stack=items.get(slot);if(stack.isEmpty())return ItemStack.EMPTY;int count=Math.min(stack.getCount(),boundedOutput(side,max));ItemStack result=portion(stack,count);if(!simulate&&count>0){stack.shrink(count);if(stack.isEmpty())items.set(slot,ItemStack.EMPTY);transferred(side,count,true);}return result;}
        @Override public int getSlotLimit(int slot){checkSlot(slot);return 64;}
    }
}
