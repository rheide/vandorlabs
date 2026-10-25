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
        String[] pages={"Line one\nLine two","Second page", "", "", "", "", "", "Final page"};
        for(int company=1;company<7;company++)for(boolean chip:new boolean[]{false,true}){
            ProgrammerTile tile=fresh();ItemStack master=new ItemStack(ModItems.DATA_CHIP,1,company),input=new ItemStack(chip?ModItems.DATA_CHIP:ModItems.ACCESS_CARD);tile.setInventorySlotContents(0,master);tile.setInventorySlotContents(1,input);tile.configure(chip,"Test journal",pages);
            require(tile.canProgram()&&tile.program(),"program blank INPUT company "+company+" chip="+chip);require(tile.getStackInSlot(1).isEmpty()&&!tile.canProgram(),"consumed input disables Program");require(tile.getStackInSlot(0)==master&&master.getCount()==1,"master retained");require(tile.getStackInSlot(2).getMetadata()==company&&tile.getStackInSlot(2).getItem()==input.getItem(),"input becomes same physical type company output");
            if(chip){require(ChipJournal.journal(tile.getStackInSlot(2)),"journal type");require(ChipJournal.page(tile.getStackInSlot(2),7).equals("Final page"),"all eight pages");require(tile.getStackInSlot(2).getDisplayName().contains("Test journal"),"journal title in item name");}
            ItemStack immutable=tile.getStackInSlot(2).copy();tile.configure(true,"Must not edit Output",new String[]{"No"});require(!tile.program()&&ItemStack.areItemStacksEqual(immutable,tile.getStackInSlot(2)),"output-only draft/program cannot mutate result");
            ProgrammerTile saved=fresh();saved.readFromNBT(tile.writeToNBT(new NBTTagCompound()));require(ItemStack.areItemStacksEqual(immutable,saved.getStackInSlot(2)),"retained result exact reload");ItemStack moved=saved.removeStackFromSlot(2);saved.setInventorySlotContents(1,moved);require(saved.canProgram(),"same retained controller re-enables Program when output moves back into input");
            ProgrammerTile reinput=fresh();reinput.setInventorySlotContents(0,master.copy());reinput.setInventorySlotContents(1,moved);require(reinput.canProgram(),"moving result back to Input enables Program");if(chip)require(reinput.title.equals("Test journal")&&reinput.pages[7].equals("Final page"),"input Journal loads title and every page");
            reinput.setInventorySlotContents(2,new ItemStack(ModItems.DATA_CHIP));ItemStack occupied=reinput.getStackInSlot(2).copy();require(!reinput.program()&&ItemStack.areItemStacksEqual(occupied,reinput.getStackInSlot(2)),"occupied Output is never overwritten");
            tile=fresh();tile.setInventorySlotContents(0,master.copy());tile.setInventorySlotContents(1,new ItemStack(ModItems.DATA_CHIP,1,company%6+1));require(!tile.program(),"foreign input company rejected");
            tile=fresh();tile.setInventorySlotContents(0,master.copy());ItemStack tagged=new ItemStack(chip?ModItems.DATA_CHIP:ModItems.ACCESS_CARD);NBTTagCompound custom=new NBTTagCompound();custom.setString("ExamplePreserved","yes");tagged.setTagCompound(custom);tile.setInventorySlotContents(1,tagged);require(tile.program()&&tile.getStackInSlot(2).getTagCompound().getString("ExamplePreserved").equals("yes"),"unrelated input NBT preserved during consume/move");
        }
        ProgrammerTile tile=fresh();tile.setInventorySlotContents(0,new ItemStack(ModItems.DATA_CHIP,1,3));tile.setInventorySlotContents(1,new ItemStack(ModItems.ACCESS_CARD));tile.configure(true,"ignored card journal",pages);require(!tile.journal&&tile.program()&&tile.getStackInSlot(2).getItem()==ModItems.ACCESS_CARD&&tile.getStackInSlot(2).getMetadata()==3,"Vandor Master chip + blank INPUT Access Card -> Vandor Access Card OUTPUT");
        ItemStack journal=new ItemStack(ModItems.DATA_CHIP,1,3);ChipJournal.write(journal,true,"Journal",new String[]{"Text"});require(!tile.isItemValidForSlot(0,journal),"journal cannot authorize master slot");ProgrammerTile editor=fresh();editor.setInventorySlotContents(1,journal.copy());require(editor.title.equals("Journal")&&editor.pages[0].equals("Text"),"only input journal loads contents");editor.configure(true,"Edited",new String[]{"Changed"});require(editor.program()&&ChipJournal.title(editor.getStackInSlot(2)).equals("Edited"),"edited INPUT journal moves to Output");
        String huge=String.join("",java.util.Collections.nCopies(600,"x"));ChipJournal.write(journal,true,huge,new String[]{huge});require(ChipJournal.title(journal).length()==40&&ChipJournal.page(journal,0).length()==512,"bounded saved text");
        ProgrammerMessage original=new ProgrammerMessage(42,true,true,"Unicode \u03a9",new String[]{"One","Two","","","","","","Eight"});io.netty.buffer.ByteBuf bytes=Unpooled.buffer();original.toBytes(bytes);ProgrammerMessage decoded=new ProgrammerMessage();decoded.fromBytes(bytes);require(decoded.decodedValid(),"bounded message accepted");io.netty.buffer.ByteBuf copy=Unpooled.buffer();decoded.toBytes(copy);bytes.readerIndex(0);require(java.util.Arrays.equals(io.netty.buffer.ByteBufUtil.getBytes(bytes),io.netty.buffer.ByteBufUtil.getBytes(copy)),"network exact round trip");bytes.release();copy.release();
        io.netty.buffer.ByteBuf invalid=Unpooled.buffer();invalid.writeInt(42).writeBoolean(true).writeBoolean(true).writeShort(65535);ProgrammerMessage rejected=new ProgrammerMessage();rejected.fromBytes(invalid);require(!rejected.decodedValid(),"oversized text rejected before allocation");invalid.release();
        invalid=Unpooled.buffer();invalid.writeInt(42);rejected=new ProgrammerMessage();rejected.fromBytes(invalid);require(!rejected.decodedValid(),"truncated message rejected");invalid.release();
        Block block=Block.getBlockFromName("vandorlabs:data_chip_programmer");require(block instanceof ProgrammerBlock,"registered programmer");
        for(EnumFacing facing:EnumFacing.HORIZONTALS){
            BlockPos p=origin.offset(facing,5);for(int part=0;part<4;part++)world.setBlockState(ProgrammerBlock.cell(p,facing,part),block.getDefaultState().withProperty(ProgrammerBlock.FACING,facing).withProperty(ProgrammerBlock.PART,part),3);
            ProgrammerTile live=ProgrammerBlock.at(world,p);require(live!=null&&live.isProgrammerController(),"live rendering owner "+facing);
            net.minecraftforge.common.util.FakePlayer actor=net.minecraftforge.common.util.FakePlayerFactory.getMinecraft(world);actor.setPosition(p.getX()+.5,p.getY()+.5,p.getZ()+.5);net.minecraft.item.ItemStack heldBefore=actor.getHeldItemMainhand().copy();
            try{for(int slot=0;slot<3;slot++){
                double cx=slot==0?16.3:slot==1?28:4,cz=slot==0?10.05:8.75;ProgrammerTransform.Affine transform=ProgrammerGeometry.transform(facing);BlockPos modelOrigin=live.getProgrammerOrigin();net.minecraft.util.math.Vec3d target=new net.minecraft.util.math.Vec3d(modelOrigin.getX()+transform.x(cx/16,cz/16),p.getY()+16.02/16,modelOrigin.getZ()+transform.z(cx/16,cz/16)),start=target.addVector(facing.getFrontOffsetX()*1.5,.8,facing.getFrontOffsetZ()*1.5),end=target.addVector(0,-.01,0);net.minecraft.util.math.RayTraceResult hit=null;
                for(int part=0;part<4;part++){net.minecraft.util.math.RayTraceResult candidate=block.collisionRayTrace(world.getBlockState(ProgrammerBlock.cell(p,facing,part)),world,ProgrammerBlock.cell(p,facing,part),start,end);if(candidate!=null&&(hit==null||candidate.hitVec.squareDistanceTo(start)<hit.hitVec.squareDistanceTo(start)))hit=candidate;}
                require(hit!=null&&hit.subHit==slot,"native authored socket ray "+facing+" slot="+slot);
                actor.setHeldItem(net.minecraft.util.EnumHand.MAIN_HAND,new ItemStack(slot==0?ModItems.DATA_CHIP:ModItems.ACCESS_CARD,2,slot==0?1:0));require(live.interactSocket(actor,net.minecraft.util.EnumHand.MAIN_HAND,slot)&&live.getStackInSlot(slot).getCount()==1&&actor.getHeldItemMainhand().getCount()==1,"socket inserts one physical held token "+slot);actor.setHeldItem(net.minecraft.util.EnumHand.MAIN_HAND,ItemStack.EMPTY);require(live.interactSocket(actor,net.minecraft.util.EnumHand.MAIN_HAND,slot)&&live.getStackInSlot(slot).isEmpty()&&actor.getHeldItemMainhand().getCount()==1,"empty-hand socket removal "+slot);
            }}finally{actor.setHeldItem(net.minecraft.util.EnumHand.MAIN_HAND,heldBefore);}

            for(int part=0;part<4;part++){BlockPos member=ProgrammerBlock.cell(p,facing,part);require(ProgrammerBlock.at(world,member)==live,"shared inventory member "+part+" "+facing);require(block.getMetaFromState(block.getStateFromMeta(block.getMetaFromState(world.getBlockState(member))))==block.getMetaFromState(world.getBlockState(member)),"metadata round trip");}
            world.setBlockToAir(ProgrammerBlock.cell(p,facing,3));for(int part=0;part<4;part++)require(world.isAirBlock(ProgrammerBlock.cell(p,facing,part)),"whole footprint dismantled "+facing);
        }
        ProgrammerJournalChecks.run(world,origin);
        System.out.println("[vandorlabs][reprolab] programmer PASS assertions="+checks+" input-consume/output-immutable company matrices cards chips journal input pages exact inventory reload bounded network and native sockets in four facings");
    }
    private ProgrammerRuntimeChecks(){}
}
