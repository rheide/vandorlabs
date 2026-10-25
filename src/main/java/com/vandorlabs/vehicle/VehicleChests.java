package com.vandorlabs.vehicle;

import net.minecraft.block.BlockChest;
import net.minecraft.entity.player.*;
import net.minecraft.inventory.*;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.*;
import net.minecraft.tileentity.*;
import net.minecraft.util.math.*;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.world.*;
import java.util.*;

/** Vanilla chest windows bound to the actual carried inventory and its physical reach. */
public final class VehicleChests {
    private VehicleChests(){}
    public static boolean supports(VehicleStructure.Cell cell){return cell!=null&&cell.state.getBlock() instanceof BlockChest;}
    public static boolean open(EntityPlayerMP player,EntityGroundVehicle craft,BlockPos pos){
        Inventory inventory=inventory(player,craft,pos);if(inventory==null)return true;
        if(inventory.isUsableByPlayer(player))player.displayGUIChest(inventory);return true;
    }
    static Inventory inventory(EntityPlayer player,EntityGroundVehicle craft,BlockPos pos){
        if(craft==null||craft.view==null||!supports(craft.structure.at(pos)))return null;
        BlockChest block=(BlockChest)craft.view.getBlockState(pos).getBlock();
        ILockableContainer delegate=block.getLockableContainer(craft.view,pos);if(delegate==null)return null;
        List<TileEntityChest> tiles=new ArrayList<>();TileEntity raw=craft.view.getTileEntity(pos);if(!(raw instanceof TileEntityChest))return null;tiles.add((TileEntityChest)raw);
        for(net.minecraft.util.EnumFacing side:net.minecraft.util.EnumFacing.HORIZONTALS){BlockPos at=pos.offset(side);if(craft.view.getBlockState(at).getBlock()==block){TileEntity next=craft.view.getTileEntity(at);if(next instanceof TileEntityChest)tiles.add((TileEntityChest)next);}}
        if(tiles.size()>2)return null;Inventory inventory=new Inventory(craft,delegate,tiles);
        if(!inventory.isUsableByPlayer(player))return null;
        if(!delegate.isLocked()||player.canOpen(delegate.getLockCode())){
            for(TileEntityChest chest:tiles)if(chest.getLootTable()!=null){
                // Native loot requires a real WorldServer; fill an isolated copy, then adopt exact local NBT.
                TileEntityChest shadow=new TileEntityChest();shadow.readFromNBT(chest.writeToNBT(new NBTTagCompound()));shadow.setWorld(craft.world);shadow.setPos(new BlockPos(craft.toWorld(new Vec3d(chest.getPos()).addVector(.5,.5,.5))));shadow.fillWithLoot(player);shadow.setPos(chest.getPos());
                NBTTagCompound next=shadow.writeToNBT(new NBTTagCompound());Map<BlockPos,NBTTagCompound> proposed=new HashMap<>();proposed.put(chest.getPos(),next);if(!fits(craft,proposed))throw new IllegalArgumentException("Chest contents exceed the vehicle's saved-data limit");chest.readFromNBT(next);chest.markDirty();
            }
            craft.signals.persist();
        }
        return inventory;
    }
    public static void close(EntityGroundVehicle craft){for(EntityPlayer player:craft.world.playerEntities)if(player.openContainer instanceof ChestContainer&&((ChestContainer)player.openContainer).inventory.craft==craft)player.closeScreen();}
    private static boolean fits(EntityGroundVehicle craft,Map<BlockPos,NBTTagCompound> data){try{List<VehicleStructure.Cell> cells=new ArrayList<>();for(VehicleStructure.Cell cell:craft.structure.cells)cells.add(data.containsKey(cell.pos)?new VehicleStructure.Cell(cell.pos,cell.state,data.get(cell.pos)):cell);new VehicleStructure(cells,craft.structure.seat,craft.structure.facing,craft.structure.gearCount).encode();return true;}catch(Exception rejected){return false;}}
    static final class Inventory implements ILockableContainer {
        final EntityGroundVehicle craft;final VehicleWorld view;final ILockableContainer delegate;final List<TileEntityChest> tiles;
        Inventory(EntityGroundVehicle craft,ILockableContainer delegate,List<TileEntityChest> tiles){this.craft=craft;view=craft.view;this.delegate=delegate;this.tiles=Collections.unmodifiableList(new ArrayList<>(tiles));}
        boolean current(){if(craft.isDead||craft.ownershipBlocked||craft.transferInProgress||craft.view!=view)return false;for(TileEntityChest tile:tiles)if(view.getTileEntity(tile.getPos())!=tile||!supports(craft.structure.at(tile.getPos())))return false;return true;}
        @Override public boolean isUsableByPlayer(EntityPlayer player){if(player==null||player.isDead||player.isSpectator()||player.world!=craft.world||!current())return false;for(TileEntityChest tile:tiles){Vec3d at=craft.toWorld(new Vec3d(tile.getPos()).addVector(.5,.5,.5));if(!craft.world.isBlockLoaded(new BlockPos(at),false)||player.getDistanceSq(at.x,at.y,at.z)>64)return false;}return true;}
        Map<BlockPos,NBTTagCompound> saved(){Map<BlockPos,NBTTagCompound> result=new LinkedHashMap<>();for(TileEntityChest tile:tiles)result.put(tile.getPos(),tile.writeToNBT(new NBTTagCompound()));return result;}
        void restore(Map<BlockPos,NBTTagCompound> saved){for(TileEntityChest tile:tiles){tile.readFromNBT(saved.get(tile.getPos()));tile.markDirty();}}
        @Override public int getSizeInventory(){return delegate.getSizeInventory();}
        @Override public boolean isEmpty(){return delegate.isEmpty();}
        @Override public ItemStack getStackInSlot(int slot){return delegate.getStackInSlot(slot);}
        @Override public ItemStack decrStackSize(int slot,int count){return delegate.decrStackSize(slot,count);}
        @Override public ItemStack removeStackFromSlot(int slot){return delegate.removeStackFromSlot(slot);}
        @Override public void setInventorySlotContents(int slot,ItemStack stack){delegate.setInventorySlotContents(slot,stack);}
        @Override public int getInventoryStackLimit(){return delegate.getInventoryStackLimit();}
        @Override public void markDirty(){delegate.markDirty();for(TileEntityChest tile:tiles)craft.signals.changed(tile.getPos());}
        @Override public void openInventory(EntityPlayer player){delegate.openInventory(player);}
        @Override public void closeInventory(EntityPlayer player){delegate.closeInventory(player);if(current())craft.signals.persist();}
        @Override public boolean isItemValidForSlot(int slot,ItemStack stack){return delegate.isItemValidForSlot(slot,stack);}
        @Override public int getField(int id){return delegate.getField(id);}
        @Override public void setField(int id,int value){delegate.setField(id,value);}
        @Override public int getFieldCount(){return delegate.getFieldCount();}
        @Override public void clear(){delegate.clear();markDirty();}
        @Override public String getName(){return delegate.getName();}
        @Override public boolean hasCustomName(){return delegate.hasCustomName();}
        @Override public ITextComponent getDisplayName(){return delegate.getDisplayName();}
        @Override public void setLockCode(LockCode code){delegate.setLockCode(code);}
        @Override public LockCode getLockCode(){return delegate.getLockCode();}
        @Override public boolean isLocked(){return delegate.isLocked();}
        @Override public String getGuiID(){return "minecraft:chest";}
        @Override public Container createContainer(InventoryPlayer player,EntityPlayer actor){return new ChestContainer(player,this,actor);}
    }
    static final class ChestContainer extends ContainerChest {
        final Inventory inventory;private boolean editing;
        ChestContainer(InventoryPlayer player,Inventory inventory,EntityPlayer actor){super(player,inventory,actor);this.inventory=inventory;}
        @Override public boolean canInteractWith(EntityPlayer player){return inventory.isUsableByPlayer(player);}
        private interface Action {ItemStack run();}
        private ItemStack edit(EntityPlayer player,Action action){
            if(!canInteractWith(player)){player.closeScreen();return ItemStack.EMPTY;}if(editing)return action.run();editing=true;
            Map<BlockPos,NBTTagCompound> before=inventory.saved();NBTTagList playerBefore=player.inventory.writeToNBT(new NBTTagList());ItemStack cursor=player.inventory.getItemStack().copy();
            try{ItemStack result=action.run();if(!fits(inventory.craft,inventory.saved()))throw new IllegalArgumentException("Chest contents exceed the vehicle's saved-data limit");inventory.markDirty();inventory.craft.signals.persist();return result;}
            catch(RuntimeException rejected){inventory.restore(before);player.inventory.readFromNBT(playerBefore);player.inventory.setItemStack(cursor);inventory.craft.signals.persist();if(player instanceof EntityPlayerMP)((EntityPlayerMP)player).sendContainerToPlayer(this);player.sendStatusMessage(new net.minecraft.util.text.TextComponentString(rejected.getMessage()==null?"Chest change refused":rejected.getMessage()),true);return ItemStack.EMPTY;}
            finally{editing=false;}
        }
        @Override public ItemStack slotClick(int slot,int drag,ClickType type,EntityPlayer player){return edit(player,()->super.slotClick(slot,drag,type,player));}
        @Override public ItemStack transferStackInSlot(EntityPlayer player,int slot){return edit(player,()->super.transferStackInSlot(player,slot));}
    }
}
