package com.vandorlabs.machines;

import com.vandorlabs.blocks.*;
import com.vandorlabs.programmer.*;
import net.minecraft.block.*;
import net.minecraft.block.state.IBlockState;
import net.minecraft.block.material.Material;
import net.minecraft.init.Blocks;
import net.minecraft.item.*;
import net.minecraft.nbt.*;
import net.minecraft.util.EnumBlockRenderType;
import net.minecraft.util.math.*;
import java.util.*;

/** One inert, validated block template. Never admits arbitrary tile inventories or machine ownership. */
public final class BlockRecipeData {
    public static final String ID="digitized_block";public static final int VERSION=1,MAX_BYTES=16384;
    private static final Set<String> APPEARANCE=new HashSet<>(Arrays.asList("housingTexture","HousingTexture","HousingTextureVersion","SideTexture","MainTextureLayout","SlabTileSides","FaceTexturesEnabled","FaceTextures","SurfaceTextureLayout","SurfaceTileSides","SelectedScreen","PrimarySurfaceTexture","SecondarySurfaceTexture","MainTexture","TextureVersion","PropulsionShape","PropulsionSideTexture","ParticleLevel","ParticleStream","SignalBrightness","ParticleThreshold","RedstoneInverted","PropulsionJoin","RedstoneChannel","RedstoneChannels","ManualOn","id","x","y","z"));
    private BlockRecipeData(){}
    public static boolean eligible(ItemStack stack){
        try{return eligibleState(stack);}catch(IllegalArgumentException | IndexOutOfBoundsException invalidMetadata){return false;}
    }
    private static boolean eligibleState(ItemStack stack){
        if(stack.isEmpty()||!(stack.getItem() instanceof ItemBlock)||stack.getMetadata()<0||stack.getMetadata()>15)return false;
        Block block=Block.getBlockFromItem(stack.getItem());if(block==Blocks.AIR||block.getRegistryName()==null)return false;
        String namespace=block.getRegistryName().getResourceDomain(),id=block.getRegistryName().getResourcePath();
        if(namespace.equals("vandorlabs")){
            if(block instanceof BlockMountedSwitch||block instanceof BlockIndustrialLever||block instanceof BlockPilotSeat||block instanceof com.vandorlabs.gear.GearBlock||id.contains("canopy")||id.contains("chair")||id.contains("seat"))return false;
            boolean special=block instanceof BlockConstructorCore||block instanceof BlockPropulsionLight||block instanceof BlockRampController||block instanceof BlockVehicleController||id.equals("programmable_block")||id.equals("programmable_slab")||id.startsWith("programmable_")&&(id.endsWith("screen")||id.endsWith("console"));if(!special&&!whole(state(stack)))return false;
        }else if(namespace.equals("minecraft")){
            if(block instanceof BlockDoor||block instanceof BlockBed||block instanceof BlockLeaves||block instanceof BlockLog||block instanceof BlockBush||block instanceof BlockCactus||block instanceof BlockReed||block instanceof BlockVine||block instanceof BlockHugeMushroom||block instanceof BlockPumpkin||block instanceof BlockMelon||block instanceof BlockPistonExtension||block instanceof BlockPistonMoving)return false;
            IBlockState state=state(stack);Material material=state.getMaterial();if(material==Material.PLANTS||material==Material.LEAVES||material==Material.VINE||material==Material.CACTUS||material==Material.GOURD)return false;
            if(state.getRenderType()!=EnumBlockRenderType.MODEL)return false;
            if(!(block instanceof BlockSlab||block instanceof BlockTrapDoor)){AxisAlignedBB box;try{box=state.getBoundingBox(null,BlockPos.ORIGIN);}catch(RuntimeException unsafe){return false;}if(box==null||box.minX!=0||box.minY!=0||box.minZ!=0||box.maxX!=1||box.maxY!=1||box.maxZ!=1)return false;}
        }else return false;
        if(stack.hasTagCompound()){
            NBTTagCompound data=stack.getTagCompound();for(String key:data.getKeySet())if(!key.equals("BlockEntityTag")&&!key.equals("display"))return false;
            if(data.hasKey("BlockEntityTag")&&!data.hasKey("BlockEntityTag",10))return false;
            NBTTagCompound tile=data.getCompoundTag("BlockEntityTag");for(String key:tile.getKeySet())if(!APPEARANCE.contains(key))return false;
            if(data.hasKey("display",10)){NBTTagCompound display=data.getCompoundTag("display");for(String key:display.getKeySet())if(!key.equals("Name")&&!key.equals("LocName"))return false;}
        }
        try{java.io.ByteArrayOutputStream bytes=new java.io.ByteArrayOutputStream();net.minecraft.nbt.CompressedStreamTools.write(stack.writeToNBT(new NBTTagCompound()),new java.io.DataOutputStream(bytes));return bytes.size()<=MAX_BYTES&&!stack.serializeNBT().hasKey("ForgeCaps");}catch(java.io.IOException invalid){return false;}
    }
    private static boolean whole(IBlockState state){if(state.getRenderType()!=EnumBlockRenderType.MODEL)return false;try{AxisAlignedBB box=state.getBoundingBox(null,BlockPos.ORIGIN);return box!=null&&box.minX==0&&box.minY==0&&box.minZ==0&&box.maxX==1&&box.maxY==1&&box.maxZ==1;}catch(RuntimeException invalid){return false;}}
    public static IBlockState state(ItemStack stack){return Block.getBlockFromItem(stack.getItem()).getStateFromMeta(stack.getMetadata());}
    public static ItemStack read(ItemStack chip){
        if(!CatalogChipData.catalog(chip)||!chip.getTagCompound().hasKey("CatalogBlock",10))return ItemStack.EMPTY;NBTTagCompound data=chip.getTagCompound().getCompoundTag("CatalogBlock");if(!data.hasKey("Version",3)||data.getInteger("Version")!=VERSION||!data.hasKey("Stack",10)||!data.hasKey("State",10))return ItemStack.EMPTY;
        ItemStack stack=new ItemStack(com.vandorlabs.persistence.RenamedBlocks.itemData(data.getCompoundTag("Stack")));if(stack.getCount()!=1||!eligible(stack)||!NBTUtil.writeBlockState(new NBTTagCompound(),state(stack)).equals(com.vandorlabs.persistence.RenamedBlocks.stateTag(data.getCompoundTag("State"))))return ItemStack.EMPTY;return stack;
    }
    public static boolean canDigitizeChip(ItemStack chip){
        if(!ChipJournal.isChip(chip))return false;
        NBTTagCompound tag=chip.getTagCompound();
        return tag==null||!com.vandorlabs.credentials.CatalogChipPolicy.isMaster(
                tag.getString("ChipType"),tag.getString("CatalogName"),tag.hasKey("CatalogBlock",10));
    }
    public static void write(ItemStack chip,ItemStack input){
        if(!canDigitizeChip(chip))throw new IllegalArgumentException("Digitize requires a writable Data Chip. The Master Catalog is protected.");
        if(!eligible(input))throw new IllegalArgumentException("This block cannot be digitized");ItemStack block=input.copy();block.setCount(1);NBTTagCompound tag=new NBTTagCompound(),catalog=new NBTTagCompound(),designs=new NBTTagCompound(),payload=new NBTTagCompound();NBTTagList list=new NBTTagList();list.appendTag(new NBTTagString(ID));designs.setTag("synthesis_tower",list);catalog.setInteger("Version",1);catalog.setTag("Designs",designs);payload.setInteger("Version",VERSION);payload.setTag("Stack",block.writeToNBT(new NBTTagCompound()));payload.setTag("State",NBTUtil.writeBlockState(new NBTTagCompound(),state(block)));tag.setString("ChipType","catalog");tag.setString("CatalogName",ChipJournal.bounded(block.getDisplayName(),40));tag.setTag("Catalog",catalog);tag.setTag("CatalogBlock",payload);chip.setTagCompound(tag);
    }
}
