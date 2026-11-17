package com.vandorlabs.items;

import com.vandorlabs.appliance.ApplianceCatalog;
import com.vandorlabs.blocks.ModBlocks;
import com.vandorlabs.tiles.ScreenHousingTextures;
import net.minecraft.block.Block;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import java.util.Arrays;

/** Creative shortcuts remain ordinary programmable items and ordinary placed blocks. */
public final class AppliancePresets {
    private AppliancePresets(){}
    private static final int[][] CHOICES=loadChoices();
    private static int[][] loadChoices(){int[][] result=new int[4][6];for(int index=0;index<4;index++)for(int face=0;face<6;face++){String path=ApplianceCatalog.texture(index,face);result[index][face]=ScreenHousingTextures.screenIndex(path);if(result[index][face]==0)throw new IllegalStateException("Missing appliance material "+path);}return result;}
    public static int[] faces(int index){return CHOICES[index].clone();}
    public static ItemStack stack(int index){
        if(!ApplianceCatalog.preset(index))throw new IllegalArgumentException("Not a programmable preset");
        ItemStack stack=new ItemStack(ApplianceCatalog.slab(index)?ModBlocks.PROGRAMMABLE_SLAB:ModBlocks.PROGRAMMABLE_BLOCK);int[] faces=faces(index);NBTTagCompound settings=new NBTTagCompound();settings.setInteger("housingTexture",faces[2]);settings.setInteger("HousingTextureVersion",1);settings.setInteger("MainTextureLayout",0);settings.setBoolean("SlabTileSides",false);settings.setBoolean("FaceTexturesEnabled",true);settings.setIntArray("FaceTextures",faces);stack.setTagInfo("BlockEntityTag",settings);NBTTagCompound display=new NBTTagCompound();display.setString("LocName","tile.vandorlabs."+ApplianceCatalog.IDS[index]+".name");stack.setTagInfo("display",display);return stack;
    }
    @net.minecraftforge.fml.relauncher.SideOnly(net.minecraftforge.fml.relauncher.Side.CLIENT)
    public static void creative(Block block,CreativeTabs tab,java.util.List<ItemStack> items){if(tab!=com.vandorlabs.VandorLabs.VANDOR_LABS_TAB&&tab!=CreativeTabs.SEARCH)return;for(int i=0;i<4;i++)if(block==(ApplianceCatalog.slab(i)?ModBlocks.PROGRAMMABLE_SLAB:ModBlocks.PROGRAMMABLE_BLOCK))items.add(stack(i));}
    /** Also recognizes picked/mined presets after the cosmetic inventory name is discarded. */
    public static int matching(ItemStack stack){
        Block block=Block.getBlockFromItem(stack.getItem());if(block!=ModBlocks.PROGRAMMABLE_BLOCK&&block!=ModBlocks.PROGRAMMABLE_SLAB)return -1;NBTTagCompound tag=stack.getSubCompound("BlockEntityTag");if(tag==null||!tag.getBoolean("FaceTexturesEnabled")||tag.getInteger("MainTextureLayout")!=0)return -1;int[] saved=tag.getIntArray("FaceTextures");for(int i=0;i<4;i++)if(block==(ApplianceCatalog.slab(i)?ModBlocks.PROGRAMMABLE_SLAB:ModBlocks.PROGRAMMABLE_BLOCK)&&Arrays.equals(saved,CHOICES[i]))return i;return -1;
    }
}
