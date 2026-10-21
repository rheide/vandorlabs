package com.vandorlabs.tiles;

import com.vandorlabs.blocks.BlockTokenSlot;
import com.vandorlabs.items.ModItems;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.text.*;

/** One physical token, with a non-consuming list of allowed designs. */
public class TileEntityTokenSlot extends TileEntityRedstoneChannel implements IInventory {
    private ItemStack token=ItemStack.EMPTY;
    private boolean chip;
    public boolean any=true;
    public int allowed=127,behavior;
    private long pulseUntil;
    private boolean toggled,toggleApplied;
    private java.util.UUID returnTo;
    private long insertStarted;
    private long nextReturnAttempt;
    private int logoDesign;
    private long rejectedUntil;
    private TileEntitySpaceDoor door;
    TileEntityTokenSlot forDoor(TileEntitySpaceDoor owner){door=owner;return this;}
    protected boolean installed(){return world!=null&&world.getTileEntity(pos)==(door==null?this:door);}
    protected int returnPulseTicks(){return door==null?20:100;}
    public int logoDesign(){if((effectiveAllowed()&(1<<logoDesign))!=0)return logoDesign;for(int i=0;i<7;i++)if((effectiveAllowed()&(1<<i))!=0)return i;return 0;}
    public int effectiveAllowed(){return any?127:allowed;}
    public void cycleLogo(){int mask=effectiveAllowed();for(int i=1;i<=7;i++){int candidate=(logoDesign()+i)%7;if((mask&(1<<candidate))!=0){logoDesign=candidate;changed(false);return;}}}
    public static String behaviorName(int mode){return new String[]{"Stay Inserted","Stay Inserted: Pulse","Return After Open: Toggle","Return After Open: Pulse"}[Math.max(0,Math.min(3,mode))];}

    public TileEntityTokenSlot(){}
    public TileEntityTokenSlot(boolean chip){this.chip=chip;}
    public boolean chip(){return world!=null&&world.getBlockState(pos).getBlock() instanceof BlockTokenSlot?((BlockTokenSlot)world.getBlockState(pos).getBlock()).chip:chip;}
    public boolean accepts(ItemStack stack){return !stack.isEmpty()&&stack.getItem()==(chip()?ModItems.DATA_CHIP:ModItems.DATA_CARD)&&stack.getMetadata()>=0&&stack.getMetadata()<7;}
    public boolean authorized(ItemStack stack){return accepts(stack)&&(effectiveAllowed()&(1<<stack.getMetadata()))!=0;}
    public boolean matches(){return authorized(token);}
    public boolean rejected(){return world!=null&&world.getTotalWorldTime()<rejectedUntil;}
    public void reject(){if(world!=null&&!world.isRemote){rejectedUntil=world.getTotalWorldTime()+12;changed(false);}}
    public boolean returning(){return returnTo!=null&&!token.isEmpty();}
    public float animationAge(float partial){return !token.isEmpty()&&world!=null?(float)Math.max(0,Math.min(20,world.getTotalWorldTime()-insertStarted+partial)):20;}
    /** Borrows exactly one real item, including in Creative, until the animation finishes. */
    public boolean insertToggle(EntityPlayer player,ItemStack stack){
        if(world==null||world.isRemote||(behavior!=2&&behavior!=3)||!isEmpty()||!isUsableByPlayer(player)||!authorized(stack))return false;
        token=stack.copy();token.setCount(1);stack.shrink(1);returnTo=player.getUniqueID();insertStarted=world.getTotalWorldTime();toggleApplied=false;changed(false);sound(net.minecraft.init.SoundEvents.BLOCK_IRON_TRAPDOOR_OPEN,1.4F);schedule(8);return true;
    }
    private void sound(net.minecraft.util.SoundEvent sound,float pitch){if(world!=null&&!world.isRemote&&installed()&&!(world instanceof com.vandorlabs.vehicle.VehicleWorld))world.playSound(null,pos,sound,net.minecraft.util.SoundCategory.BLOCKS,.25F,pitch);}
    @Override public boolean isChannelLatch(){return false;}
    @Override public int getOutputLevel(){return behavior==2?((door==null?toggled:door.securityToggle())?15:0):behavior==3?(world!=null&&world.getTotalWorldTime()<pulseUntil?15:0):matches()&&(behavior==0||world!=null&&world.getTotalWorldTime()<pulseUntil)?15:0;}
    @Override public boolean hasLocalRedstoneSignal(){return getOutputLevel()>0;}
    @Override public boolean hasLocalRedstoneSignal(int channel){return hasLocalRedstoneSignal();}
    @Override public int localSignalLevel(int channel){return getOutputLevel();}
    // Generic switch or copy callbacks cannot manufacture a token signal.
    @Override public void setLocalOn(boolean ignored){refresh();}
    public void refresh(){
        if(door!=null){door.securityChanged();return;}
        if(world==null||world.isRemote)return;
        boolean powered=getOutputLevel()>0;super.setLocalOn(powered);
        if(world instanceof com.vandorlabs.vehicle.VehicleWorld)return;
        net.minecraft.block.state.IBlockState state=world.getBlockState(pos);
        if(state.getBlock() instanceof BlockTokenSlot)((BlockTokenSlot)state.getBlock()).applyLinkedState(world,pos,state,powered);
    }
    public boolean expire(long now){
        boolean dirty=false;
        if((behavior==1||behavior==3)&&pulseUntil>0&&now>=pulseUntil){pulseUntil=0;dirty=true;}
        if(returning()){
            if(!toggleApplied&&now>=insertStarted+8){toggleApplied=true;if((behavior==2||behavior==3)&&matches()){if(behavior==2){if(door==null)toggled=!toggled;else door.toggleCredential();}else pulseUntil=now+returnPulseTicks();sound(net.minecraft.init.SoundEvents.UI_BUTTON_CLICK,toggled?1.4F:.8F);}dirty=true;}
            if(world!=null&&!world.isRemote&&installed()&&now>=insertStarted+20&&now>=nextReturnAttempt){
                nextReturnAttempt=now+20;
                net.minecraft.server.MinecraftServer server=net.minecraftforge.fml.common.FMLCommonHandler.instance().getMinecraftServerInstance();
                EntityPlayer player=server==null?null:server.getPlayerList().getPlayerByUUID(returnTo);
                if(player!=null&&!player.isDead&&hasRoom(player,token)){
                    ItemStack returned=token.copy();
                    if(player.inventory.addItemStackToInventory(returned)&&returned.isEmpty()){
                        token=ItemStack.EMPTY;returnTo=null;player.inventory.markDirty();player.inventoryContainer.detectAndSendChanges();sound(net.minecraft.init.SoundEvents.BLOCK_IRON_TRAPDOOR_CLOSE,1.6F);dirty=true;
                    }
                }
            }
        }
        if(dirty)changed(false);return dirty;
    }
    private static boolean hasRoom(EntityPlayer player,ItemStack token){
        for(ItemStack slot:player.inventory.mainInventory)if(slot.isEmpty()||ItemStack.areItemsEqual(slot,token)&&ItemStack.areItemStackTagsEqual(slot,token)&&slot.getCount()<Math.min(slot.getMaxStackSize(),player.inventory.getInventoryStackLimit()))return true;
        for(ItemStack slot:player.inventory.offHandInventory)if(!slot.isEmpty()&&ItemStack.areItemsEqual(slot,token)&&ItemStack.areItemStackTagsEqual(slot,token)&&slot.getCount()<Math.min(slot.getMaxStackSize(),player.inventory.getInventoryStackLimit()))return true;
        return false;
    }
    private void schedule(int ticks){if(!(world instanceof com.vandorlabs.vehicle.VehicleWorld))world.scheduleUpdate(pos,world.getBlockState(pos).getBlock(),door==null?ticks:1);}
    public void scheduledTick(){
        if(world==null||world.isRemote)return;long now=world.getTotalWorldTime();expire(now);refresh();
        // Minecraft coalesces scheduled updates at a position. An earlier pulse
        // may fire before a replacement token's deadline, so rearm the remainder.
        if(returning())schedule(now>=insertStarted+20?20:(int)Math.max(1,(toggleApplied?insertStarted+20:insertStarted+8)-now));
        else if((behavior==1||behavior==3)&&pulseUntil>now)schedule((int)Math.min(20,pulseUntil-now));
    }
    private void changed(boolean inserted){
        if(world!=null&&!world.isRemote){
            if(inserted)insertStarted=world.getTotalWorldTime();
            if(inserted&&matches()&&behavior==1){pulseUntil=world.getTotalWorldTime()+returnPulseTicks();schedule(returnPulseTicks());}
            if(token.isEmpty()&&behavior==1)pulseUntil=0;
            refresh();world.notifyBlockUpdate(pos,world.getBlockState(pos),world.getBlockState(pos),2);
        }
        markDirty();
    }
    public void configure(boolean any,int allowed,int behavior){
        allowed&=127;behavior=behavior>=0&&behavior<=3?behavior:0;
        if(this.any!=any||this.allowed!=allowed||this.behavior!=behavior){pulseUntil=0;toggled=false;}
        this.any=any;this.allowed=allowed;this.behavior=behavior;changed(false);
    }
    @Override public void onLoad(){super.onLoad();if(world!=null&&!world.isRemote){pulseUntil=Math.min(pulseUntil,world.getTotalWorldTime()+20);scheduledTick();}}
    @Override public NBTTagCompound writeToNBT(NBTTagCompound tag){super.writeToNBT(tag);tag.setInteger("TokenLogo",logoDesign);tag.setLong("TokenRejectedUntil",rejectedUntil);tag.setBoolean("ChipSlot",chip());tag.setBoolean("AnyToken",any);tag.setInteger("AllowedTokens",allowed);tag.setInteger("TokenBehavior",behavior);tag.setLong("TokenPulseUntil",pulseUntil);tag.setBoolean("TokenToggled",toggled);tag.setBoolean("TokenToggleApplied",toggleApplied);tag.setLong("TokenInsertStarted",insertStarted);if(returnTo!=null)tag.setUniqueId("TokenReturnTo",returnTo);else{tag.removeTag("TokenReturnToMost");tag.removeTag("TokenReturnToLeast");}if(!token.isEmpty())tag.setTag("InsertedToken",token.writeToNBT(new NBTTagCompound()));else tag.removeTag("InsertedToken");return tag;}
    @Override public void readFromNBT(NBTTagCompound tag){super.readFromNBT(tag);logoDesign=Math.max(0,Math.min(6,tag.getInteger("TokenLogo")));rejectedUntil=tag.getLong("TokenRejectedUntil");chip=tag.getBoolean("ChipSlot");any=!tag.hasKey("AnyToken")||tag.getBoolean("AnyToken");allowed=tag.hasKey("AllowedTokens")?tag.getInteger("AllowedTokens")&127:127;behavior=tag.getInteger("TokenBehavior");if(behavior<0||behavior>3)behavior=0;pulseUntil=tag.getLong("TokenPulseUntil");toggled=tag.getBoolean("TokenToggled");toggleApplied=tag.getBoolean("TokenToggleApplied");insertStarted=tag.getLong("TokenInsertStarted");returnTo=tag.hasUniqueId("TokenReturnTo")?tag.getUniqueId("TokenReturnTo"):null;token=tag.hasKey("InsertedToken",10)?new ItemStack(tag.getCompoundTag("InsertedToken")):ItemStack.EMPTY;if(!token.isEmpty())token.setCount(1);else returnTo=null;}
    @Override public int getSizeInventory(){return 1;}
    @Override public boolean isEmpty(){return token.isEmpty();}
    @Override public ItemStack getStackInSlot(int slot){return slot==0?token:ItemStack.EMPTY;}
    public ItemStack takeForDrop(){ItemStack result=token;token=ItemStack.EMPTY;returnTo=null;changed(false);return result;}
    @Override public ItemStack decrStackSize(int slot,int count){if(slot!=0||count<=0||returning())return ItemStack.EMPTY;return takeForDrop();}
    @Override public ItemStack removeStackFromSlot(int slot){return decrStackSize(slot,1);}
    @Override public void setInventorySlotContents(int slot,ItemStack stack){if(slot!=0||returning()||!stack.isEmpty()&&(!authorized(stack)||behavior>=2))return;boolean inserted=!stack.isEmpty()&&(token.isEmpty()||!ItemStack.areItemStacksEqual(token,stack));token=stack.copy();if(!token.isEmpty())token.setCount(1);changed(inserted);}
    @Override public int getInventoryStackLimit(){return 1;}
    @Override public boolean isUsableByPlayer(EntityPlayer player){if(door!=null)return door.usableReader(player);return world!=null&&!isInvalid()&&installed()&&player.world==world&&com.vandorlabs.items.ConfigurationAccess.canEditDialog(player,world,pos);}
    @Override public void openInventory(EntityPlayer player){}
    @Override public void closeInventory(EntityPlayer player){}
    @Override public boolean isItemValidForSlot(int slot,ItemStack stack){return slot==0&&behavior<2&&!returning()&&authorized(stack);}
    @Override public int getField(int id){return id==0?allowed:id==1?any?1:0:behavior;}
    @Override public void setField(int id,int value){}
    @Override public int getFieldCount(){return 3;}
    @Override public void clear(){if(!returning())takeForDrop();}
    @Override public String getName(){return "tile.vandorlabs."+(chip()?"data_chip_slot":"data_card_slot")+".name";}
    @Override public boolean hasCustomName(){return false;}
    @Override public ITextComponent getDisplayName(){return new TextComponentTranslation(getName());}
 @Override public net.minecraft.util.math.AxisAlignedBB getRenderBoundingBox(){return new net.minecraft.util.math.AxisAlignedBB(pos).grow(.4);}

}
