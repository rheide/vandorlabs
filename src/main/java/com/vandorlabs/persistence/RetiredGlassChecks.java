package com.vandorlabs.persistence;

import com.vandorlabs.vehicle.*;
import com.vandorlabs.items.ModItems;
import java.util.*;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.*;
import net.minecraft.tileentity.TileEntityChest;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldServer;

/** Previous-JAR world checks and bounded native/NEID/portable migration probes. */
public final class RetiredGlassChecks {
    private static void require(boolean value,String message){if(!value)throw new IllegalStateException("Retired glass: "+message);}
    public static void run(WorldServer world,String phase)throws Exception{
        verify(world,8);if(!phase.equals("near"))verify(world,520);
        TileEntityChest chest=(TileEntityChest)world.getTileEntity(new BlockPos(8,80,10));require(chest!=null,"previous-JAR chest missing");
        for(int i=0;i<3;i++){ItemStack stack=chest.getStackInSlot(i);IBlockState expected=RetiredCockpitGlass.state(i);require(stack.getItem()==net.minecraft.item.Item.getItemFromBlock(expected.getBlock())&&stack.getMetadata()==expected.getBlock().getMetaFromState(expected)&&stack.getCount()==17,"inventory colour/count "+i);require(stack.getDisplayName().equals("Legacy "+RetiredCockpitGlass.IDS[i])&&stack.getTagCompound().getCompoundTag("Opaque").getInteger("Proof")==28,"item data changed");}
        boolean entity=false;for(net.minecraft.entity.Entity e:world.loadedEntityList)if(e instanceof EntityItem){ItemStack stack=((EntityItem)e).getItem();if(stack.getCount()==3&&stack.getItem()==net.minecraft.item.Item.getItemFromBlock(Blocks.STAINED_GLASS)&&stack.getMetadata()==9)entity=true;}require(entity,"dropped item migration");
        probes(world);System.out.println("[vandorlabs] retired-glass-"+phase+" PASS previous-JAR native blocks vanilla sentinels named inventory dropped item persisted ID table NEID nibbles portable snapshots chips ramps unknown-property refusal");
    }
    private static void verify(WorldServer world,int base){for(int i=0;i<3;i++)require(world.getBlockState(new BlockPos(base+i,80,base))==RetiredCockpitGlass.state(i),"native colour at "+base+" variant="+i+" actual="+world.getBlockState(new BlockPos(base+i,80,base)));int[] meta={-1,0,7,9,14};for(int i=0;i<meta.length;i++)require(world.getBlockState(new BlockPos(base+3+i,80,base))==(meta[i]<0?Blocks.GLASS.getDefaultState():Blocks.STAINED_GLASS.getStateFromMeta(meta[i])),"vanilla sentinel changed "+i);}
    public static void probes(WorldServer world)throws Exception{
        Map<Integer,Integer> ids=new HashMap<>();ids.put(3000,0);ids.put(5000,1);ids.put(60000,2);
        byte[] blocks=new byte[4096],data=new byte[2048],add=new byte[2048],add2=new byte[2048];int[] legacy={3000,5000,60000};
        for(int i=0;i<3;i++){blocks[i]=(byte)legacy[i];nibble(add,i,legacy[i]>>8&15);nibble(add2,i,legacy[i]>>12&15);}blocks[7]=(byte)Block.getIdFromBlock(Blocks.STAINED_GLASS);nibble(data,7,14);
        NBTTagCompound section=new NBTTagCompound();section.setByteArray("Blocks",blocks);section.setByteArray("Data",data);section.setByteArray("Add",add);section.setByteArray("Add2",add2);NBTTagList sections=new NBTTagList();sections.appendTag(section);NBTTagCompound root=new NBTTagCompound(),level=new NBTTagCompound();level.setTag("Sections",sections);root.setTag("Level",level);RetiredCockpitGlass.migrateChunk(root,ids);
        for(int i=0;i<3;i++){IBlockState target=RetiredCockpitGlass.state(i);require((section.getByteArray("Blocks")[i]&255)==Block.getIdFromBlock(target.getBlock()),"native block ID rewrite");require(get(data,i)==target.getBlock().getMetaFromState(target)&&get(add,i)==0&&get(add2,i)==0,"NEID high bits/metadata rewrite");}require(get(data,7)==14,"neighbour nibble corrupted");
        List<VehicleStructure.Cell> cells=new ArrayList<>();for(int i=0;i<3;i++)cells.add(new VehicleStructure.Cell(new BlockPos(i,0,0),RetiredCockpitGlass.state(i),null));VehicleStructure shape=VehicleStructure.display(cells);NBTTagCompound saved=shape.write();NBTTagList palette=saved.getTagList("Palette",10);for(int i=0;i<3;i++){NBTTagCompound old=new NBTTagCompound();old.setString("Name","vandorlabs:"+RetiredCockpitGlass.IDS[i]);palette.set(i,old);}NBTTagCompound original=saved.copy();VehicleStructure loaded=VehicleStructure.readDisplay(saved);for(int i=0;i<3;i++)require(loaded.at(new BlockPos(i,0,0)).state==RetiredCockpitGlass.state(i),"portable snapshot colour");require(saved.equals(original),"snapshot input mutated");
        List<VehicleStructure.Cell> liveCells=new ArrayList<>(cells);BlockPos seat=new BlockPos(3,0,0);liveCells.add(new VehicleStructure.Cell(seat,com.vandorlabs.blocks.ModBlocks.PILOT_SEAT.getDefaultState(),null));NBTTagCompound legacySnapshot=new VehicleStructure(liveCells,seat,EnumFacing.NORTH,1).write();NBTTagList livePalette=legacySnapshot.getTagList("Palette",10);for(int i=0;i<3;i++){NBTTagCompound old=new NBTTagCompound();old.setString("Name","vandorlabs:"+RetiredCockpitGlass.IDS[i]);livePalette.set(i,old);}
        java.io.ByteArrayOutputStream raw=new java.io.ByteArrayOutputStream();CompressedStreamTools.write(legacySnapshot,new java.io.DataOutputStream(raw));for(int codec=0;codec<2;codec++){ItemStack chip=new ItemStack(ModItems.DATA_CHIP);VehicleChip.blank(chip);java.io.ByteArrayOutputStream encoded=new java.io.ByteArrayOutputStream();if(codec==1)try(java.util.zip.GZIPOutputStream gzip=new java.util.zip.GZIPOutputStream(encoded)){gzip.write(raw.toByteArray());}else encoded.write(raw.toByteArray());chip.getTagCompound().setInteger("VehicleDesignCodec",codec);chip.getTagCompound().setByteArray("VehicleDesign",encoded.toByteArray());VehicleStructure decoded=VehicleChip.design(chip);for(int i=0;i<3;i++)require(decoded.at(new BlockPos(i,0,0)).state==RetiredCockpitGlass.state(i),"legacy Vehicle Chip colour codec="+codec);}
        for(int i=0;i<3;i++){require(LegacyBlockStates.decode("vandorlabs:"+RetiredCockpitGlass.IDS[i])==RetiredCockpitGlass.state(i),"legacy ramp state");NBTTagCompound invalid=new NBTTagCompound();invalid.setString("Name","vandorlabs:"+RetiredCockpitGlass.IDS[i]);NBTTagCompound properties=new NBTTagCompound();properties.setString("unknown","true");invalid.setTag("Properties",properties);boolean refused=false;try{RetiredCockpitGlass.stateTag(invalid);}catch(IllegalArgumentException expected){refused=true;}require(refused,"unknown legacy properties accepted");}
        NBTTagCompound ramp=new NBTTagCompound();ramp.setString("id","vandorlabs:controlled_ramp");ramp.setString("SourceState","vandorlabs:smoked_cockpit_glass");NBTTagCompound source=new NBTTagCompound();source.setString("Name","vandorlabs:smoked_cockpit_glass");ramp.setTag("Source",source);NBTTagCompound originalRamp=ramp.copy(),converted=RetiredCockpitGlass.tileData(ramp);require(converted.getString("SourceState").equals("minecraft:stained_glass[color=gray]")&&converted.getCompoundTag("Source").getCompoundTag("Properties").getString("color").equals("gray")&&ramp.equals(originalRamp),"ramp saved fields did not migrate without mutating input");
        NBTTagCompound blankRamp=new NBTTagCompound();blankRamp.setString("id","vandorlabs:controlled_ramp");blankRamp.setString("SourceState","");require(RetiredCockpitGlass.tileData(blankRamp)==blankRamp,"ordinary legacy ramp data was changed/copied");
        NBTTagCompound item=new NBTTagCompound();item.setString("id","vandorlabs:pale_cyan_cockpit_glass");item.setByte("Count",(byte)23);item.setShort("Damage",(short)0);item.setString("Opaque","unchanged");NBTTagCompound inventory=new NBTTagCompound();inventory.setTag("Nested",item);RetiredCockpitGlass.migrateItems(inventory);require(item.getString("id").equals("minecraft:stained_glass")&&item.getShort("Damage")==9&&item.getByte("Count")==23&&item.getString("Opaque").equals("unchanged"),"nested item migration");
    }
    private static void nibble(byte[] data,int i,int value){data[i>>1]=(byte)((data[i>>1]&~(15<<((i&1)*4)))|value<<((i&1)*4));}
    private static int get(byte[] data,int i){return data[i>>1]>>((i&1)*4)&15;}
}
