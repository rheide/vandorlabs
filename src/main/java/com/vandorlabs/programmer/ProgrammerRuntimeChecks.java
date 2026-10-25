package com.vandorlabs.programmer;

import com.vandorlabs.items.ModItems;
import io.netty.buffer.Unpooled;
import net.minecraft.block.Block;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldServer;

/** Actual registered stacks and tiles; no asynchronous writes or fixture ownership outside this area. */
public final class ProgrammerRuntimeChecks {
    private static int checks;
    private static void require(boolean value,String message){checks++;if(!value)throw new IllegalStateException("Programmer: "+message);}
    private static ProgrammerTile fresh(){return new ProgrammerTile();}
    public static void run(WorldServer world,BlockPos origin){
        checks=0;
        for(int company=1;company<7;company++)for(boolean chip:new boolean[]{false,true}){
            ProgrammerTile tile=fresh();ItemStack master=new ItemStack(ModItems.ACCESS_CARD,1,company),output=new ItemStack(chip?ModItems.DATA_CHIP:ModItems.ACCESS_CARD);
            tile.setInventorySlotContents(0,master);tile.setInventorySlotContents(2,output);
            String[] pages={"Line one\nLine two","Second page", "", "", "", "", "", "Final page"};tile.configure(true,"Test journal",pages);
            require(tile.program(),"program blank destination company "+company+" chip="+chip);
            require(tile.getStackInSlot(2).getMetadata()==company,"company written");require(tile.getStackInSlot(0)==master&&master.getCount()==1,"master retained");require(tile.getStackInSlot(1).isEmpty(),"scratch input optional");require(tile.getStackInSlot(2).getCount()==1,"one destination");
            if(chip){require(ChipJournal.journal(tile.getStackInSlot(2)),"journal type");require(ChipJournal.page(tile.getStackInSlot(2),7).equals("Final page"),"all eight pages");require(tile.getStackInSlot(2).getDisplayName().contains("Test journal"),"title in item name");}
            ProgrammerTile saved=fresh();saved.readFromNBT(tile.writeToNBT(new NBTTagCompound()));require(ItemStack.areItemStacksEqual(tile.getStackInSlot(2),saved.getStackInSlot(2)),"retained output reload");require(saved.title.equals("Test journal")&&saved.pages[1].equals("Second page"),"draft reload");
            tile=fresh();tile.setInventorySlotContents(0,new ItemStack(ModItems.DATA_CHIP,1,company));tile.setInventorySlotContents(2,new ItemStack(ModItems.DATA_CHIP,1,company%6+1));require(!tile.program(),"wrong-company destination rejected");
            tile=fresh();tile.setInventorySlotContents(0,master.copy());ItemStack input=new ItemStack(chip?ModItems.DATA_CHIP:ModItems.ACCESS_CARD,1,company);NBTTagCompound custom=new NBTTagCompound();custom.setString("ExamplePreserved","yes");input.setTagCompound(custom);if(chip)ChipJournal.write(input,true,"Source",pages);tile.setInventorySlotContents(1,input);tile.setInventorySlotContents(2,new ItemStack(chip?ModItems.DATA_CHIP:ModItems.ACCESS_CARD));
            require(tile.program(),"duplicate input");require(tile.getStackInSlot(1)==input&&input.getCount()==1,"input retained");require(tile.getStackInSlot(2).getTagCompound().getString("ExamplePreserved").equals("yes"),"unrelated input tags copied");
            if(chip)require(ChipJournal.title(tile.getStackInSlot(2)).equals("Source"),"source draft loaded");
        }
        ProgrammerTile tile=fresh();tile.setInventorySlotContents(1,new ItemStack(ModItems.ACCESS_CARD,1,2));tile.setInventorySlotContents(2,new ItemStack(ModItems.DATA_CHIP));ItemStack before=tile.getStackInSlot(2).copy();require(!tile.program()&&ItemStack.areItemStacksEqual(before,tile.getStackInSlot(2)),"type mismatch leaves output intact");
        tile=fresh();tile.setInventorySlotContents(0,new ItemStack(ModItems.DATA_CHIP,1,3));tile.setInventorySlotContents(2,new ItemStack(ModItems.DATA_CHIP));tile.configure(false,"",new String[0]);require(tile.program()&&ChipJournal.master(tile.getStackInSlot(2)),"master chip programming");require(!tile.program(),"repeated activation gated");
        ItemStack journal=tile.getStackInSlot(2).copy();ChipJournal.write(journal,true,"Journal",new String[]{"Text"});require(!tile.isItemValidForSlot(0,journal),"journal cannot authorize master slot");
        ProgrammerTile editor=fresh();editor.setInventorySlotContents(0,new ItemStack(ModItems.ACCESS_CARD,1,3));editor.setInventorySlotContents(2,journal.copy());require(editor.title.equals("Journal")&&editor.pages[0].equals("Text"),"output-only journal loads for editing");editor.configure(true,"Edited",new String[]{"Changed"});require(editor.program()&&ChipJournal.title(editor.getStackInSlot(2)).equals("Edited"),"edit same physical output journal");
        String huge=String.join("",java.util.Collections.nCopies(600,"x"));ChipJournal.write(journal,true,huge,new String[]{huge});require(ChipJournal.title(journal).length()==40&&ChipJournal.page(journal,0).length()==512,"bounded saved text");
        ProgrammerMessage original=new ProgrammerMessage(42,true,true,"Unicode \u03a9",new String[]{"One","Two","","","","","","Eight"});io.netty.buffer.ByteBuf bytes=Unpooled.buffer();original.toBytes(bytes);ProgrammerMessage decoded=new ProgrammerMessage();decoded.fromBytes(bytes);require(decoded.decodedValid(),"bounded message accepted");io.netty.buffer.ByteBuf copy=Unpooled.buffer();decoded.toBytes(copy);bytes.readerIndex(0);require(java.util.Arrays.equals(io.netty.buffer.ByteBufUtil.getBytes(bytes),io.netty.buffer.ByteBufUtil.getBytes(copy)),"network exact round trip");bytes.release();copy.release();
        io.netty.buffer.ByteBuf invalid=Unpooled.buffer();invalid.writeInt(42).writeBoolean(true).writeBoolean(true).writeShort(65535);ProgrammerMessage rejected=new ProgrammerMessage();rejected.fromBytes(invalid);require(!rejected.decodedValid(),"oversized text rejected before allocation");invalid.release();
        invalid=Unpooled.buffer();invalid.writeInt(42);rejected=new ProgrammerMessage();rejected.fromBytes(invalid);require(!rejected.decodedValid(),"truncated message rejected");invalid.release();
        Block block=Block.getBlockFromName("vandorlabs:data_chip_programmer");require(block instanceof ProgrammerBlock,"registered programmer");
        for(EnumFacing facing:EnumFacing.HORIZONTALS){
            BlockPos p=origin.offset(facing,5);for(int part=0;part<4;part++)world.setBlockState(ProgrammerBlock.cell(p,facing,part),block.getDefaultState().withProperty(ProgrammerBlock.FACING,facing).withProperty(ProgrammerBlock.PART,part),3);
            ProgrammerTile live=ProgrammerBlock.at(world,p);require(live!=null&&live.isProgrammerController(),"live rendering owner "+facing);
            for(int part=0;part<4;part++){BlockPos member=ProgrammerBlock.cell(p,facing,part);require(ProgrammerBlock.at(world,member)==live,"shared inventory member "+part+" "+facing);require(block.getMetaFromState(block.getStateFromMeta(block.getMetaFromState(world.getBlockState(member))))==block.getMetaFromState(world.getBlockState(member)),"metadata round trip");}
            world.setBlockToAir(ProgrammerBlock.cell(p,facing,3));for(int part=0;part<4;part++)require(world.isAirBlock(ProgrammerBlock.cell(p,facing,part)),"whole footprint dismantled "+facing);
        }
        System.out.println("[vandorlabs][reprolab] programmer PASS assertions="+checks+" company matrices cards chips journal draft inventory reload network and four footprint facings");
    }
    private ProgrammerRuntimeChecks(){}
}
