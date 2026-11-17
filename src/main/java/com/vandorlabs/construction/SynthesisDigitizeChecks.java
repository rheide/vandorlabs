package com.vandorlabs.construction;

import com.vandorlabs.blocks.*;
import com.vandorlabs.tiles.*;
import com.vandorlabs.machines.*;
import com.vandorlabs.items.*;
import net.minecraft.block.Block;
import net.minecraft.item.*;
import net.minecraft.nbt.*;
import net.minecraft.init.Blocks;
import net.minecraft.util.EnumFacing;
import net.minecraft.world.World;
import net.minecraftforge.energy.*;

/** Real formed tower, physical input ownership, resumption and template synthesis. */
@net.minecraftforge.fml.relauncher.SideOnly(net.minecraftforge.fml.relauncher.Side.CLIENT)
public final class SynthesisDigitizeChecks {
    private static int checks;
    private static void need(boolean ok,String why){checks++;if(!ok)throw new IllegalStateException("digitize: "+why);}
    public static void run(World world,TileEntityConstructor original){
        TileEntityConstructor tile=reset(world,original);need(!tile.digitizing(),"old absent mode defaults Synthesize");
        for(Block block:new Block[]{Blocks.STONE,Blocks.DIRT,Blocks.PLANKS,Blocks.GLASS,Blocks.STONE_SLAB,Blocks.WOODEN_SLAB,Blocks.TRAPDOOR,Blocks.IRON_TRAPDOOR,Blocks.FURNACE,Blocks.CRAFTING_TABLE,Blocks.BRICK_BLOCK,Blocks.REDSTONE_BLOCK})need(BlockRecipeData.eligible(new ItemStack(block)),"vanilla sample "+block);
        for(Block block:new Block[]{Blocks.LOG,Blocks.LOG2,Blocks.LEAVES,Blocks.LEAVES2,Blocks.SAPLING,Blocks.WHEAT,Blocks.CACTUS,Blocks.PUMPKIN,Blocks.BED,Blocks.OAK_DOOR,Blocks.CHEST,Blocks.ENDER_CHEST,Blocks.TORCH})need(!BlockRecipeData.eligible(new ItemStack(block)),"excluded organic/multipart/nonregular "+block);
        for(Block block:ModBlocks.BLOCKS)if(Item.getItemFromBlock(block)!=net.minecraft.init.Items.AIR&&(block instanceof BlockConstructorCore||block instanceof BlockPropulsionLight||block instanceof BlockRampController||block instanceof BlockVehicleController))need(BlockRecipeData.eligible(new ItemStack(block)),"supported machine/controller "+block);
        for(int preset=0;preset<4;preset++)need(BlockRecipeData.eligible(AppliancePresets.stack(preset)),"configured programmable AC faces "+preset);
        ItemStack inventory=new ItemStack(Blocks.FURNACE);NBTTagCompound data=new NBTTagCompound(),contents=new NBTTagCompound();contents.setTag("Items",new NBTTagList());data.setTag("BlockEntityTag",contents);inventory.setTagCompound(data);need(!BlockRecipeData.eligible(inventory),"never digitize inventories");
        need(tile.setDigitizing(true),"idle tower enters Digitize");ItemStack sample=new ItemStack(Blocks.STONE_SLAB,1,3),chip=new ItemStack(ModItems.DATA_CHIP,1,4);tile.setInventorySlotContents(0,chip);need(tile.synthesisInventory().insertItem(0,sample,false).isEmpty(),"single physical sample input");need(!tile.setDigitizing(false),"occupied mode switch refused");
        for(int i=0;i<200;i++)power(world,tile);need(tile.processing()&&tile.buildProgress()==.5F,"half scan requires half RF");need(ItemStack.areItemStacksEqual(sample,tile.synthesisInventory().getStackInSlot(0))&&tile.getStackInSlot(0).getTagCompound()==null,"input and blank chip stay physically owned until completion");
        NBTTagCompound partial=tile.writeToNBT(new NBTTagCompound());TileEntityConstructor loaded=new TileEntityConstructor();loaded.setWorld(world);loaded.setPos(tile.getPos());loaded.readFromNBT(partial);world.setTileEntity(tile.getPos(),loaded);tile=loaded;DeferredTileLoad.flush(world);tile.refreshInputs();tile.configureSynthesisRedstone(0,com.vandorlabs.redstone.ChannelList.EMPTY);need(tile.digitizing()&&tile.processing()&&tile.buildProgress()==.5F,"partial scan reload resumes exact work");for(int i=0;i<200;i++)power(world,tile);
        need(!tile.processing()&&tile.synthesisInventory().getStackInSlot(0).isEmpty()&&tile.synthesisInventory().getStackInSlot(1).isEmpty(),"completion consumes exactly one sample, no block output");ItemStack catalog=tile.getStackInSlot(0).copy();need(catalog.getMetadata()==4&&ItemStack.areItemStacksEqual(sample,BlockRecipeData.read(catalog)),"chip company and exact slab variant preserved");need(tile.builderRecipes().contains(BlockRecipeData.ID),"inserted scanned block available to tower");need(com.vandorlabs.programmer.ProgrammerJournalView.from(catalog).designs.contains(BlockRecipeData.ID),"Catalog screen can resolve scanned design");
        need(tile.setDigitizing(false),"completed scanner returns to Synthesize");tile.selectDesign(tile.builderRecipeIndex(BlockRecipeData.ID));ItemStack matter=new ItemStack(Block.getBlockFromName("vandorlabs:programmable_matter_block"));tile.synthesisInventory().insertItem(0,matter,false);power(world,tile);
        tile.setInventorySlotContents(0,ItemStack.EMPTY);
        NBTTagCompound running=tile.writeToNBT(new NBTTagCompound());
        loaded=new TileEntityConstructor();loaded.setWorld(world);loaded.setPos(tile.getPos());loaded.readFromNBT(running);world.setTileEntity(tile.getPos(),loaded);tile=loaded;
        DeferredTileLoad.flush(world);tile.refreshInputs();tile.configureSynthesisRedstone(0,com.vandorlabs.redstone.ChannelList.EMPTY);
        for(int i=1;i<400;i++)power(world,tile);need(ItemStack.areItemStacksEqual(sample,tile.synthesisInventory().getStackInSlot(1)),"scanned recipe synthesizes exact vanilla variant");need(tile.getStackInSlot(0).isEmpty(),"synthesis keeps committed template across chip removal and reload");
        tile=reset(world,tile);need(tile.setDigitizing(true),"second scan mode");tile.setInventorySlotContents(0,new ItemStack(ModItems.DATA_CHIP));tile.synthesisInventory().insertItem(0,new ItemStack(Blocks.GLASS),false);power(world,tile);tile.setInventorySlotContents(0,new ItemStack(ModItems.DATA_CHIP,1,2));world.setTotalWorldTime(world.getTotalWorldTime()+1);tile.update();need(tile.synthesisInventory().getStackInSlot(0).getItem()==Item.getItemFromBlock(Blocks.GLASS)&&tile.buildProgress()==0,"changed chip cancels prior work without consuming sample");
        NBTTagCompound corrupt=partial.copy();corrupt.getCompoundTag("Synthesis").setInteger("Mode",99);TileEntityConstructor inert=new TileEntityConstructor();inert.setWorld(world);inert.setPos(tile.getPos());inert.readFromNBT(corrupt);need(!inert.processing()&&inert.writeToNBT(new NBTTagCompound()).getCompoundTag("Synthesis").equals(corrupt.getCompoundTag("Synthesis")),"unknown saved mode remains inert and retained");
        world.setTileEntity(tile.getPos(),inert);int before=slabs(world,inert.getPos());inert.dismantle();need(slabs(world,inert.getPos())==before+1,"inert scan dismantling releases its one owned sample exactly once");NBTTagCompound empty=inert.writeToNBT(new NBTTagCompound()).getCompoundTag("Synthesis");need(empty.getCompoundTag("Inventory").getTagList("Items",10).tagCount()==0&&!empty.getBoolean("Active"),"retained failure snapshot cannot resurrect dropped inventory");
        reset(world,inert);System.out.println("[vandorlabs] synthesis-digitize-runtime PASS checks="+checks+" eligibility exact slab/template company half-scan reload cancelled-target no-loss futureMode retention");
    }
    private static int slabs(World world,net.minecraft.util.math.BlockPos pos){int count=0;for(net.minecraft.entity.item.EntityItem entity:world.getEntitiesWithinAABB(net.minecraft.entity.item.EntityItem.class,new net.minecraft.util.math.AxisAlignedBB(pos).grow(3)))if(!entity.isDead&&entity.getItem().getItem()==Item.getItemFromBlock(Blocks.STONE_SLAB))count+=entity.getItem().getCount();return count;}
    private static TileEntityConstructor reset(World world,TileEntityConstructor old){NBTTagCompound tag=old.writeToNBT(new NBTTagCompound());tag.removeTag("Synthesis");tag.removeTag("InsertedToken");TileEntityConstructor tile=new TileEntityConstructor();tile.setWorld(world);tile.setPos(old.getPos());tile.readFromNBT(tag);world.setTileEntity(tile.getPos(),tile);DeferredTileLoad.flush(world);tile.refreshInputs();tile.configureSynthesisRedstone(0,com.vandorlabs.redstone.ChannelList.EMPTY);return tile;}
    private static void power(World world,TileEntityConstructor tile){world.setTotalWorldTime(world.getTotalWorldTime()+1);net.minecraft.tileentity.TileEntity port=world.getTileEntity(tile.synthesisPowerPort());IEnergyStorage energy=port.getCapability(CapabilityEnergy.ENERGY,EnumFacing.SOUTH);need(energy!=null&&energy.receiveEnergy(20,false)==20,"20RF/tick scan/build admission");}
}
