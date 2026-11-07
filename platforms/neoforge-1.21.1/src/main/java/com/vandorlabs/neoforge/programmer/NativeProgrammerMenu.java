package com.vandorlabs.neoforge.programmer;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.*;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;

/** One authorized three-slot session. Clients receive ordinary menu slots into a separate shadow. */
public final class NativeProgrammerMenu extends AbstractContainerMenu {
    public final NativeProgrammerEntity tile;
    private final Container contents;
    private final NativeProgrammerBlock block;
    private boolean journalPage;
    public NativeProgrammerMenu(MenuType<?> type,int id,Inventory inventory,NativeProgrammerEntity tile) {
        super(type,id);this.tile=java.util.Objects.requireNonNull(tile);
        if(!(tile.getBlockState().getBlock() instanceof NativeProgrammerBlock owner))throw new IllegalArgumentException("Missing Programmer root");
        block=owner;contents=inventory.player.level().isClientSide?new SimpleContainer(3):tile;
        for(int i=0;i<3;i++){final int slot=i;addSlot(new Slot(contents,i,18+i*90,38){
            @Override public boolean mayPlace(ItemStack stack){return NativeProgrammerRules.validSlot(slot,stack)&&(!(tile.getLevel() instanceof ServerLevel)||tile.canPlaceItem(slot,stack));}
            @Override public int getMaxStackSize(){return 1;}
        });}
        for(int row=0;row<3;row++)for(int col=0;col<9;col++)addPlayerSlot(inventory,col+row*9+9,44+col*18,104+row*18);
        for(int col=0;col<9;col++)addPlayerSlot(inventory,col,44+col*18,166);
    }
    public NativeProgrammerMenu(MenuType<?> type,int id,Inventory inventory,BlockPos pos){this(type,id,inventory,require(inventory.player,pos));}
    private static NativeProgrammerEntity require(Player player,BlockPos pos){
        if(!player.level().hasChunkAt(pos)||!(player.level().getBlockEntity(pos) instanceof NativeProgrammerEntity tile))throw new IllegalArgumentException("Missing loaded Programmer root");
        return tile;
    }
    private void addPlayerSlot(Inventory inventory,int index,int x,int y){addSlot(new Slot(inventory,index,x,y){@Override public boolean isActive(){return !journalPage;}});}
    /** Local visibility only; fixed slot indices and server inventory ownership never change. */
    public void journalPage(boolean journal){if(tile.getLevel()!=null&&tile.getLevel().isClientSide)journalPage=journal;}
    public Container contents(){return contents;}
    @Override public boolean stillValid(Player player){
        if(tile.getBlockState().getBlock()!=block)return false;
        if(tile.getLevel() instanceof ServerLevel)return tile.usable(player);
        return !tile.inert()&&com.vandorlabs.neoforge.NativeConfigurationAccess.canEditDialog(player,tile);
    }
    @Override public ItemStack quickMoveStack(Player player,int index){
        if(!stillValid(player)||index<0||index>=slots.size())return ItemStack.EMPTY;
        Slot slot=slots.get(index);if(!slot.hasItem())return ItemStack.EMPTY;
        ItemStack stack=slot.getItem(),original=stack.copy();
        if(index<3){if(!moveItemStackTo(stack,3,39,true))return ItemStack.EMPTY;}
        else{boolean moved=false;for(int target=0;target<3;target++)if(NativeProgrammerRules.validSlot(target,stack)&&moveItemStackTo(stack,target,target+1,false)){moved=true;break;}if(!moved)return ItemStack.EMPTY;}
        if(stack.isEmpty())slot.set(ItemStack.EMPTY);else slot.setChanged();
        if(original.getCount()==stack.getCount())return ItemStack.EMPTY;
        slot.onTake(player,stack);tile.setChanged();return original;
    }
    public static boolean apply(ServerPlayer player,ProgrammerSettingsPayload edit){
        if(edit==null||!player.getServer().isSameThread()||!(player.containerMenu instanceof NativeProgrammerMenu menu)||menu.containerId!=edit.window()||!menu.stillValid(player))return false;
        boolean result;
        if(edit.operation()==2)result=menu.tile.copy(player);
        else{menu.tile.configure(player,edit.draft());result=edit.operation()==0||menu.tile.program(player);}
        menu.broadcastChanges();return result;
    }
}
