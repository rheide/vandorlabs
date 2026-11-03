package com.vandorlabs.persistence;

import com.vandorlabs.blocks.ModBlocks;
import com.vandorlabs.tiles.TileEntityAnimatedScreenSelector;
import com.vandorlabs.vehicle.*;
import com.vandorlabs.programmer.ChipJournal;
import java.io.*;
import java.util.*;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.*;
import net.minecraft.tileentity.TileEntityChest;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldServer;

/** Previous-JAR native upgrade and portable references must preserve every metadata bit and payload. */
public final class BlockRenameChecks {
    private static final int[] META={0,1,2,3,4,5,8,9,10,11,12,13};
    private static void require(boolean ok,String why){if(!ok)throw new IllegalStateException("Block rename: "+why);}
    public static void run(WorldServer world,String phase)throws Exception{
        verify(world,8);if(!phase.equals("near"))verify(world,520);
        TileEntityChest chest=(TileEntityChest)world.getTileEntity(new BlockPos(8,80,10));require(chest!=null,"source chest exists");Block block=Block.getBlockFromName(RenamedBlocks.NEW);require(block!=null&&RenamedBlocks.NEW.equals(block.getRegistryName().toString()),"canonical registered block name");
        for(int i=0;i<META.length;i++){ItemStack stack=chest.getStackInSlot(i);require(stack.getItem()==net.minecraft.item.Item.getItemFromBlock(block)&&stack.getMetadata()==META[i]&&stack.getCount()==13+i,"native inventory metadata/count "+i);require(stack.getDisplayName().equals("Legacy half screen "+META[i])&&stack.getTagCompound().getCompoundTag("Opaque").getInteger("Proof")==28,"named native stack payload "+i);}
        boolean dropped=false;for(net.minecraft.entity.Entity entity:world.loadedEntityList)if(entity instanceof EntityItem){ItemStack stack=((EntityItem)entity).getItem();if(stack.getItem()==net.minecraft.item.Item.getItemFromBlock(block)&&stack.getMetadata()==11&&stack.getCount()==3)dropped=true;}require(dropped,"native dropped item rename");
        probes(world,block);System.out.println("[vandorlabs] block-rename-"+phase+" PASS previous-JAR native metadata inventories inserted-chip dropped-item cold-restart portable-palettes two-chip-codecs nested-payloads unknown-properties");
    }
    private static void verify(WorldServer world,int base){
        Block block=Block.getBlockFromName(RenamedBlocks.NEW);
        for(int i=0;i<META.length;i++){BlockPos at=new BlockPos(base+i,80,base);world.getChunkFromBlockCoords(at);IBlockState state=world.getBlockState(at);require(state.getBlock()==block&&block.getMetaFromState(state)==META[i],"native state "+base+" metadata="+META[i]);}
        TileEntityAnimatedScreenSelector tile=(TileEntityAnimatedScreenSelector)world.getTileEntity(new BlockPos(base,80,base));require(tile.hasChipSurface(0)&&ChipJournal.title(tile.ownedChip()).equals("Retained reader"),"inserted chip and selected Chip surface retained");
        require(world.getBlockState(new BlockPos(base+13,80,base)).getBlock()==net.minecraft.init.Blocks.STONE,"vanilla neighbour retained");
    }
    private static void probes(WorldServer world,Block block)throws Exception{
        List<VehicleStructure.Cell> cells=new ArrayList<>();
        for(int i=0;i<META.length;i++){BlockPos at=new BlockPos(8+i,80,8);NBTTagCompound tile=VehicleTileData.translated(world.getTileEntity(at).writeToNBT(new NBTTagCompound()),new BlockPos(-8,-80,-8));cells.add(new VehicleStructure.Cell(new BlockPos(i,0,0),block.getStateFromMeta(META[i]),tile));}
        VehicleStructure display=VehicleStructure.display(cells);NBTTagCompound old=legacy(display.write()),original=old.copy();require(VehicleStructure.readDisplay(old).sameSnapshot(display)&&old.equals(original),"portable palette migration preserves input and tile data");
        NBTTagCompound bad=old.copy();bad.getTagList("Palette",10).getCompoundTagAt(0).getCompoundTag("Properties").setString("unknown","true");boolean refused=false;try{VehicleStructure.readDisplay(bad);}catch(IllegalArgumentException expected){refused=true;}require(refused,"unknown properties still refused");
        BlockPos seat=new BlockPos(13,0,0);List<VehicleStructure.Cell> live=new ArrayList<>(cells);live.add(new VehicleStructure.Cell(seat,ModBlocks.PILOT_SEAT.getDefaultState(),null));VehicleStructure expected=new VehicleStructure(live,seat,EnumFacing.NORTH,1);NBTTagCompound vehicle=legacy(expected.write());ByteArrayOutputStream bytes=new ByteArrayOutputStream();CompressedStreamTools.write(vehicle,new DataOutputStream(bytes));
        for(int codec=0;codec<2;codec++){byte[] payload=bytes.toByteArray();if(codec==1){ByteArrayOutputStream zipped=new ByteArrayOutputStream();try(OutputStream out=new java.util.zip.GZIPOutputStream(zipped)){out.write(payload);}payload=zipped.toByteArray();}ItemStack chip=new ItemStack(com.vandorlabs.items.ModItems.DATA_CHIP);VehicleChip.blank(chip);chip.getTagCompound().setInteger("VehicleDesignCodec",codec);chip.getTagCompound().setByteArray("VehicleDesign",payload);require(VehicleChip.design(chip).sameSnapshot(expected),"legacy Vehicle Chip codec "+codec);}
        NBTTagCompound arm=new NBTTagCompound();arm.setString("id","vandorlabs:gear");arm.setTag("ArmPayload",old);NBTTagCompound kept=arm.copy(),moved=RetiredCockpitGlass.tileData(arm);require(arm.equals(kept)&&VehicleStructure.readDisplay(moved.getCompoundTag("ArmPayload")).sameSnapshot(display),"nested arm display migration without source mutation");
        for(int meta:META){String encoded=LegacyBlockStates.encode(block.getStateFromMeta(meta)).replace(RenamedBlocks.NEW,RenamedBlocks.OLD);require(LegacyBlockStates.decode(encoded)==block.getStateFromMeta(meta),"legacy state string "+meta);}
        NBTTagCompound item=new ItemStack(net.minecraft.item.Item.getItemFromBlock(block),7,11).writeToNBT(new NBTTagCompound());item.setString("id",RenamedBlocks.OLD);item.setString("Opaque","retained");NBTTagCompound itemBefore=item.copy(),converted=RetiredCockpitGlass.itemData(item);NBTTagCompound itemExpected=item.copy();itemExpected.setString("id",RenamedBlocks.NEW);require(item.equals(itemBefore)&&converted.equals(itemExpected),"item-only migration preserves all other fields");
        NBTTagCompound ramp=new NBTTagCompound();ramp.setString("id","vandorlabs:controlled_ramp");ramp.setString("SourceState",LegacyBlockStates.encode(block.getStateFromMeta(11)).replace(RenamedBlocks.NEW,RenamedBlocks.OLD));NBTTagCompound rampBefore=ramp.copy();require(RetiredCockpitGlass.tileData(ramp).getString("SourceState").equals(LegacyBlockStates.encode(block.getStateFromMeta(11)))&&ramp.equals(rampBefore),"retained ramp material reference");
    }
    private static NBTTagCompound legacy(NBTTagCompound source){NBTTagCompound copy=source.copy();NBTTagList palette=copy.getTagList("Palette",10);for(int i=0;i<palette.tagCount();i++)if(palette.getCompoundTagAt(i).getString("Name").equals(RenamedBlocks.NEW))palette.getCompoundTagAt(i).setString("Name",RenamedBlocks.OLD);return copy;}
    private BlockRenameChecks(){}
}
