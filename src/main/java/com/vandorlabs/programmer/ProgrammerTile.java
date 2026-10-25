package com.vandorlabs.programmer;

import com.vandorlabs.items.*;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.*;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.*;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.play.server.SPacketUpdateTileEntity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.*;
import net.minecraft.util.math.*;
import net.minecraft.util.text.*;
import net.minecraftforge.fml.relauncher.*;

/** Three retained physical stacks; programming changes only the existing destination item. */
public final class ProgrammerTile extends TileEntity implements IInventory, ITickable, ProgrammerHost {
    private final NonNullList<ItemStack> stacks=NonNullList.withSize(3,ItemStack.EMPTY);
    public boolean journal=true;
    public String title="";
    public final String[] pages=new String[ChipJournal.MAX_PAGES];
    private int writing,complete;
    public ProgrammerTile(){java.util.Arrays.fill(pages,"");}
    public boolean usable(EntityPlayer player){return world!=null&&world.isBlockLoaded(pos)&&world.getTileEntity(pos)==this&&ConfigurationAccess.canEditDialog(player,world,pos);}
    public static boolean token(ItemStack stack){return !stack.isEmpty()&&(stack.getItem()==ModItems.ACCESS_CARD||stack.getItem()==ModItems.DATA_CHIP)&&stack.getMetadata()>=0&&stack.getMetadata()<7;}
    public int company(){ItemStack m=stacks.get(0);return !m.isEmpty()&&m.getMetadata()>0&&m.getMetadata()<7&&(m.getItem()==ModItems.ACCESS_CARD||ChipJournal.master(m))?m.getMetadata():0;}
    public String problem(){
        ItemStack input=stacks.get(1),output=stacks.get(2);
        if(output.isEmpty())return "Insert an output card or chip";
        if(!token(output))return "Invalid output";
        if(!input.isEmpty()&&(!token(input)||input.getItem()!=output.getItem()))return "Input and output types must match";
        if(output.getCount()!=1)return "Output must contain one item";
        int company=company();
        if(!stacks.get(0).isEmpty()&&company==0)return "Master needs a company card or Master Data Chip";
        if(output.getMetadata()!=0&&output.getMetadata()!=company)return "Output belongs to another company";
        if(!input.isEmpty()&&company>0&&input.getMetadata()!=0&&input.getMetadata()!=company)return "Input belongs to another company";
        if(output.getItem()==ModItems.ACCESS_CARD&&company==0&&input.isEmpty())return "Insert a company master or input card";
        if(output.getItem()==ModItems.DATA_CHIP&&!journal&&company==0)return "Master chips require a company master";
        return "";
    }
    public boolean program(){
        if(world!=null&&world.isRemote||writing>0||!problem().isEmpty())return false;
        ItemStack input=stacks.get(1),output=stacks.get(2),result=output.copy();
        int company=company();if(company==0&&!input.isEmpty())company=input.getMetadata();
        if(!input.isEmpty())result.setTagCompound(input.hasTagCompound()?input.getTagCompound().copy():null);
        result.setItemDamage(company);
        if(result.getItem()==ModItems.DATA_CHIP)ChipJournal.write(result,journal,title,pages);
        stacks.set(2,result);writing=20;complete=0;changed();return true;
    }
    public void configure(boolean type,String text,String[] contents){journal=type;title=ChipJournal.bounded(text,ChipJournal.TITLE_LIMIT);for(int i=0;i<pages.length;i++)pages[i]=ChipJournal.bounded(i<contents.length?contents[i]:"",ChipJournal.PAGE_LIMIT);changed();}
    private void readDraft(ItemStack input){if(ChipJournal.isChip(input)){journal=ChipJournal.journal(input);title=ChipJournal.title(input);for(int i=0;i<pages.length;i++)pages[i]=ChipJournal.page(input,i);}}
    public void changed(){markDirty();if(world!=null&&!world.isRemote)world.notifyBlockUpdate(pos,world.getBlockState(pos),world.getBlockState(pos),3);}
    @Override public void update(){if(world==null||world.isRemote)return;if(writing>0&&--writing==0){complete=60;changed();}else if(complete>0&&--complete==0)changed();}
    @Override public boolean isProgrammerController(){return world!=null&&!isInvalid()&&world.getTileEntity(pos)==this&&world.getBlockState(pos).getBlock() instanceof ProgrammerBlock&&world.getBlockState(pos).getValue(ProgrammerBlock.PART)==0;}
    @Override public ProgrammerStatus getProgrammerStatus(){return writing>0?ProgrammerStatus.WRITING:complete>0?ProgrammerStatus.COMPLETE:ProgrammerStatus.READY;}
    @Override public EnumFacing getProgrammerFacing(){return world!=null&&world.getBlockState(pos).getBlock() instanceof ProgrammerBlock?world.getBlockState(pos).getValue(ProgrammerBlock.FACING):EnumFacing.NORTH;}
    @Override public BlockPos getProgrammerOrigin(){EnumFacing f=getProgrammerFacing();return f==EnumFacing.SOUTH?pos.west():f==EnumFacing.WEST?pos.north():pos;}
    @Override @SideOnly(Side.CLIENT) public AxisAlignedBB getRenderBoundingBox(){BlockPos p=getProgrammerOrigin();EnumFacing f=getProgrammerFacing();return new AxisAlignedBB(p.getX(),p.getY(),p.getZ(),p.getX()+(f.getAxis()==EnumFacing.Axis.Z?2:1),p.getY()+2,p.getZ()+(f.getAxis()==EnumFacing.Axis.Z?1:2));}
    @Override public NBTTagCompound writeToNBT(NBTTagCompound tag){super.writeToNBT(tag);ItemStackHelper.saveAllItems(tag,stacks);tag.setBoolean("Journal",journal);tag.setString("Title",title);NBTTagList list=new NBTTagList();for(String page:pages)list.appendTag(new NBTTagString(page));tag.setTag("Pages",list);tag.setInteger("Writing",writing);tag.setInteger("Complete",complete);return tag;}
    @Override public void readFromNBT(NBTTagCompound tag){super.readFromNBT(tag);stacks.clear();ItemStackHelper.loadAllItems(tag,stacks);journal=!tag.hasKey("Journal")||tag.getBoolean("Journal");title=ChipJournal.bounded(tag.getString("Title"),ChipJournal.TITLE_LIMIT);NBTTagList list=tag.getTagList("Pages",8);for(int i=0;i<pages.length;i++)pages[i]=ChipJournal.bounded(i<list.tagCount()?list.getStringTagAt(i):"",ChipJournal.PAGE_LIMIT);writing=Math.max(0,Math.min(20,tag.getInteger("Writing")));complete=Math.max(0,Math.min(60,tag.getInteger("Complete")));}
    @Override public NBTTagCompound getUpdateTag(){return writeToNBT(new NBTTagCompound());}
    @Override public SPacketUpdateTileEntity getUpdatePacket(){return new SPacketUpdateTileEntity(pos,0,getUpdateTag());}
    @Override public void onDataPacket(NetworkManager manager,SPacketUpdateTileEntity packet){readFromNBT(packet.getNbtCompound());}
    @Override public int getSizeInventory(){return 3;}
    @Override public boolean isEmpty(){for(ItemStack s:stacks)if(!s.isEmpty())return false;return true;}
    @Override public ItemStack getStackInSlot(int slot){return stacks.get(slot);}
    @Override public ItemStack decrStackSize(int slot,int amount){ItemStack s=ItemStackHelper.getAndSplit(stacks,slot,amount);if(!s.isEmpty())changed();return s;}
    @Override public ItemStack removeStackFromSlot(int slot){ItemStack s=ItemStackHelper.getAndRemove(stacks,slot);changed();return s;}
    @Override public void setInventorySlotContents(int slot,ItemStack stack){stacks.set(slot,stack);if(!stack.isEmpty()&&stack.getCount()>1)stack.setCount(1);if(slot==1)readDraft(stack);else if(slot==2&&stacks.get(1).isEmpty()&&ChipJournal.journal(stack))readDraft(stack);complete=0;changed();}
    @Override public int getInventoryStackLimit(){return 1;}
    @Override public boolean isUsableByPlayer(EntityPlayer player){return usable(player);}
    @Override public void openInventory(EntityPlayer player){}
    @Override public void closeInventory(EntityPlayer player){}
    @Override public boolean isItemValidForSlot(int slot,ItemStack stack){if(!token(stack))return false;return slot!=0||stack.getMetadata()>0&&(stack.getItem()==ModItems.ACCESS_CARD||ChipJournal.master(stack));}
    @Override public int getField(int id){return 0;}
    @Override public void setField(int id,int value){}
    @Override public int getFieldCount(){return 0;}
    @Override public void clear(){stacks.clear();changed();}
    @Override public String getName(){return "Card / Chip Programmer";}
    @Override public boolean hasCustomName(){return false;}
    @Override public ITextComponent getDisplayName(){return new TextComponentString(getName());}
}
