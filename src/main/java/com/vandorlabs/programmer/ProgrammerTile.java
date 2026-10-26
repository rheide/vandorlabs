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

/** Three retained physical stacks; programming consumes the input and leaves an immutable result in output. */
public final class ProgrammerTile extends TileEntity implements IInventory, ITickable, ProgrammerHost {
    private final NonNullList<ItemStack> stacks=NonNullList.withSize(3,ItemStack.EMPTY);
    public boolean journal;
    public String title="";
    public final String[] pages=new String[ChipJournal.MAX_PAGES];
    private int writing,complete;
    private int displayPage;
    private boolean displayDirty=true;
    private String displayKey;
    private ProgrammerJournalView display;
    public ProgrammerTile(){java.util.Arrays.fill(pages,"");}
    public boolean usable(EntityPlayer player){return world!=null&&world.isBlockLoaded(pos)&&world.getTileEntity(pos)==this&&ConfigurationAccess.canEditDialog(player,world,pos);}
    public static boolean token(ItemStack stack){return !stack.isEmpty()&&(stack.getItem()==ModItems.ACCESS_CARD||stack.getItem()==ModItems.DATA_CHIP)&&stack.getMetadata()>=0&&stack.getMetadata()<7;}
    public int company(){ItemStack m=stacks.get(0);return !m.isEmpty()&&m.getMetadata()>0&&m.getMetadata()<7&&(m.getItem()==ModItems.ACCESS_CARD||ChipJournal.master(m))?m.getMetadata():0;}
    public String problem(){return problem(journal);}
    public String problem(boolean selectedJournal){
        ItemStack input=stacks.get(1);
        if(input.isEmpty())return "Insert an input card or chip";
        if(!token(input)||input.getCount()!=1)return "Input must contain one card or chip";
        if(!stacks.get(2).isEmpty())return "Remove the output item first";
        int company=company();
        if(!stacks.get(0).isEmpty()&&company==0)return "Master needs a company card or Master Data Chip";
        if(input.getItem()==ModItems.ACCESS_CARD&&company==0&&input.getMetadata()==0)return "Insert a company master";
        if(input.getItem()==ModItems.DATA_CHIP&&!selectedJournal&&company==0&&input.getMetadata()==0)return "Master chips require a company master";
        return "";
    }
    public boolean canProgram(){return canProgram(journal);}
    public boolean canProgram(boolean selectedJournal){return problem(selectedJournal).isEmpty();}
    public String copyProblem(){
        ItemStack source=stacks.get(1),target=stacks.get(2);
        if(source.isEmpty()||target.isEmpty())return "Insert both Input and Output items to copy";
        if(!token(source)||!token(target)||source.getCount()!=1||target.getCount()!=1)return "Copy requires one valid item in each slot";
        return source.getItem()==target.getItem()?"":"Copy needs two cards or two chips";
    }
    public boolean canCopy(){return copyProblem().isEmpty();}
    /** Reuse the one output material item; copy actual saved source data, never pending editor fields. */
    public boolean copyInput(){
        if(world!=null&&world.isRemote||!canCopy())return false;
        ItemStack result=stacks.get(1).copy();result.setCount(1);stacks.set(2,result);writing=20;complete=0;changed();return true;
    }
    public boolean editableJournal(){return ChipJournal.isChip(stacks.get(1))&&journal;}
    public ProgrammerJournalView journalDisplay(){
        if(displayDirty){ProgrammerJournalView next=ProgrammerJournalView.from(this);String key=next==null?null:next.key;if(!java.util.Objects.equals(key,displayKey))displayPage=0;displayKey=key;display=next;displayDirty=false;}
        return display;
    }
    public int journalDisplayPage(){ProgrammerJournalView view=journalDisplay();return view==null?0:Math.max(0,Math.min(displayPage,view.pages.size()-1));}
    /** World-screen navigation changes only transient viewing state, never either retained item. */
    public boolean turnJournalPage(EntityPlayer player,int direction){
        if(world==null||world.isRemote||!usable(player)||direction!=1&&direction!=-1)return false;ProgrammerJournalView view=journalDisplay();if(view==null)return false;
        int next=Math.max(0,Math.min(journalDisplayPage()+direction,view.pages.size()-1));if(next==displayPage)return true;displayPage=next;visualChanged();return true;
    }
    public boolean program(){
        if(world!=null&&world.isRemote||!canProgram())return false;
        ItemStack result=stacks.get(1).copy();result.setCount(1);
        int company=company();if(company>0)result.setItemDamage(company);
        if(result.getItem()==ModItems.DATA_CHIP)ChipJournal.write(result,journal,title,pages);
        stacks.set(1,ItemStack.EMPTY);stacks.set(2,result);readDraft(ItemStack.EMPTY);writing=20;complete=0;changed();return true;
    }
    public void configure(boolean type,String text,String[] contents){
        ItemStack input=stacks.get(1);if(!ChipJournal.isChip(input))return;
        String nextTitle=ChipJournal.bounded(text,ChipJournal.TITLE_LIMIT);boolean different=journal!=type||!title.equals(nextTitle);journal=type;title=nextTitle;for(int i=0;i<pages.length;i++){String next=ChipJournal.bounded(i<contents.length?contents[i]:"",ChipJournal.PAGE_LIMIT);different|=!pages[i].equals(next);pages[i]=next;}if(different)changed();
    }
    private void readDraft(ItemStack input){journal=ChipJournal.isChip(input)&&ChipJournal.journal(input);title=ChipJournal.title(input);for(int i=0;i<pages.length;i++)pages[i]=ChipJournal.page(input,i);}
    /** A socket click exchanges one actual physical token without opening a dialog. */
    public boolean interactSocket(EntityPlayer player,EnumHand hand,int slot){
        if(slot<0||slot>=3||!usable(player))return false;
        ItemStack held=player.getHeldItem(hand),existing=stacks.get(slot);
        if(held.isEmpty()){if(!existing.isEmpty())player.setHeldItem(hand,removeStackFromSlot(slot));}
        else if(existing.isEmpty()&&isItemValidForSlot(slot,held)){ItemStack inserted=held.splitStack(1);setInventorySlotContents(slot,inserted);}
        player.inventory.markDirty();return true;
    }
    @Override public void markDirty(){displayDirty=true;super.markDirty();}
    private boolean fullUpdate;
    private void visualChanged(){super.markDirty();if(world!=null&&!world.isRemote)world.notifyBlockUpdate(pos,world.getBlockState(pos),world.getBlockState(pos),3);}
    public void changed(){fullUpdate=true;markDirty();journalDisplay();if(world!=null&&!world.isRemote)world.notifyBlockUpdate(pos,world.getBlockState(pos),world.getBlockState(pos),3);}
    @Override public void update(){if(world==null||world.isRemote)return;if(writing>0&&--writing==0){complete=60;visualChanged();}else if(complete>0&&--complete==0)visualChanged();}
    @Override public boolean isProgrammerController(){return world!=null&&!isInvalid()&&world.getTileEntity(pos)==this&&world.getBlockState(pos).getBlock() instanceof ProgrammerBlock&&world.getBlockState(pos).getValue(ProgrammerBlock.PART)==0;}
    @Override public ProgrammerStatus getProgrammerStatus(){return writing>0?ProgrammerStatus.WRITING:complete>0?ProgrammerStatus.COMPLETE:ProgrammerStatus.READY;}
    @Override public EnumFacing getProgrammerFacing(){return world!=null&&world.getBlockState(pos).getBlock() instanceof ProgrammerBlock?world.getBlockState(pos).getValue(ProgrammerBlock.FACING):EnumFacing.NORTH;}
    @Override public BlockPos getProgrammerOrigin(){EnumFacing f=getProgrammerFacing();return f==EnumFacing.SOUTH?pos.west():f==EnumFacing.WEST?pos.north():pos;}
    @Override @SideOnly(Side.CLIENT) public AxisAlignedBB getRenderBoundingBox(){BlockPos p=getProgrammerOrigin();EnumFacing f=getProgrammerFacing();return new AxisAlignedBB(p.getX(),p.getY(),p.getZ(),p.getX()+(f.getAxis()==EnumFacing.Axis.Z?2:1),p.getY()+2,p.getZ()+(f.getAxis()==EnumFacing.Axis.Z?1:2));}
    @Override public NBTTagCompound writeToNBT(NBTTagCompound tag){super.writeToNBT(tag);ItemStackHelper.saveAllItems(tag,stacks);tag.setBoolean("Journal",journal);tag.setString("Title",title);NBTTagList list=new NBTTagList();for(String page:pages)list.appendTag(new NBTTagString(page));tag.setTag("Pages",list);tag.setInteger("Writing",writing);tag.setInteger("Complete",complete);return tag;}
    @Override public void readFromNBT(NBTTagCompound tag){super.readFromNBT(tag);stacks.clear();ItemStackHelper.loadAllItems(tag,stacks);journal=ChipJournal.isChip(stacks.get(1))&&tag.getBoolean("Journal");title=ChipJournal.bounded(tag.getString("Title"),ChipJournal.TITLE_LIMIT);NBTTagList list=tag.getTagList("Pages",8);for(int i=0;i<pages.length;i++)pages[i]=ChipJournal.bounded(i<list.tagCount()?list.getStringTagAt(i):"",ChipJournal.PAGE_LIMIT);writing=Math.max(0,Math.min(20,tag.getInteger("Writing")));complete=Math.max(0,Math.min(60,tag.getInteger("Complete")));displayDirty=true;displayKey=null;journalDisplay();displayPage=display==null?0:Math.max(0,Math.min(tag.getInteger("JournalDisplayPage"),display.pages.size()-1));}
    @Override public NBTTagCompound getUpdateTag(){NBTTagCompound tag=writeToNBT(new NBTTagCompound());tag.setInteger("JournalDisplayPage",journalDisplayPage());return tag;}
    @Override public SPacketUpdateTileEntity getUpdatePacket(){NBTTagCompound tag;if(fullUpdate){tag=getUpdateTag();fullUpdate=false;}else{tag=new NBTTagCompound();tag.setBoolean("VisualOnly",true);tag.setInteger("Writing",writing);tag.setInteger("Complete",complete);tag.setInteger("JournalDisplayPage",journalDisplayPage());}return new SPacketUpdateTileEntity(pos,0,tag);}
    @Override @SideOnly(Side.CLIENT) public void onDataPacket(NetworkManager manager,SPacketUpdateTileEntity packet){applyUpdateTag(packet.getNbtCompound());}
    void applyUpdateTag(NBTTagCompound tag){if(tag.getBoolean("VisualOnly")){writing=Math.max(0,Math.min(20,tag.getInteger("Writing")));complete=Math.max(0,Math.min(60,tag.getInteger("Complete")));ProgrammerJournalView view=journalDisplay();displayPage=view==null?0:Math.max(0,Math.min(tag.getInteger("JournalDisplayPage"),view.pages.size()-1));}else readFromNBT(tag);}
    @Override public int getSizeInventory(){return 3;}
    @Override public boolean isEmpty(){for(ItemStack s:stacks)if(!s.isEmpty())return false;return true;}
    @Override public ItemStack getStackInSlot(int slot){return stacks.get(slot);}
    @Override public ItemStack decrStackSize(int slot,int amount){ItemStack s=ItemStackHelper.getAndSplit(stacks,slot,amount);if(!s.isEmpty()){if(slot==1)readDraft(stacks.get(1));changed();}return s;}
    @Override public ItemStack removeStackFromSlot(int slot){ItemStack s=ItemStackHelper.getAndRemove(stacks,slot);if(slot==1)readDraft(ItemStack.EMPTY);changed();return s;}
    @Override public void setInventorySlotContents(int slot,ItemStack stack){stacks.set(slot,stack);if(!stack.isEmpty()&&stack.getCount()>1)stack.setCount(1);if(slot==1)readDraft(stack);complete=0;changed();}
    @Override public int getInventoryStackLimit(){return 1;}
    @Override public boolean isUsableByPlayer(EntityPlayer player){return usable(player);}
    @Override public void openInventory(EntityPlayer player){}
    @Override public void closeInventory(EntityPlayer player){}
    @Override public boolean isItemValidForSlot(int slot,ItemStack stack){if(!token(stack))return false;return slot!=0||stack.getMetadata()>0&&(stack.getItem()==ModItems.ACCESS_CARD||ChipJournal.master(stack));}
    @Override public int getField(int id){return 0;}
    @Override public void setField(int id,int value){}
    @Override public int getFieldCount(){return 0;}
    @Override public void clear(){stacks.clear();readDraft(ItemStack.EMPTY);changed();}
    @Override public String getName(){return "Card / Chip Programmer";}
    @Override public boolean hasCustomName(){return false;}
    @Override public ITextComponent getDisplayName(){return new TextComponentString(getName());}
}
