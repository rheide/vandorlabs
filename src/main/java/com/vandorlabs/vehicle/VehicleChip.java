package com.vandorlabs.vehicle;

import com.vandorlabs.programmer.ChipJournal;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import java.io.*;
import java.util.zip.*;

/** Exact bounded vehicle codec; the small shape is separate from the construction payload. */
public final class VehicleChip {
    // Two retained chips plus the programmer's own data must fit vanilla's 2 MiB tile-NBT reader.
    public static final int MAX_STORED_BYTES=com.vandorlabs.copier.core.BlueprintCompression.MAX_STORED_BYTES;
    private VehicleChip(){}
    public static boolean isVehicle(ItemStack stack){return ChipJournal.isChip(stack)&&stack.hasTagCompound()&&"vehicle".equals(stack.getTagCompound().getString("ChipType"));}
    public static String name(ItemStack stack){return isVehicle(stack)?ChipJournal.bounded(stack.getTagCompound().getString("VehicleName"),40):"";}
    public static void blank(ItemStack stack){NBTTagCompound tag=new NBTTagCompound();tag.setString("ChipType","vehicle");stack.setTagCompound(tag);}
    public static VehicleStructure design(ItemStack stack)throws IOException{
        if(!isVehicle(stack)||!stack.getTagCompound().hasKey("VehicleDesign",7))throw new IllegalArgumentException("Insert a written Vehicle Chip");
        NBTTagCompound tag=stack.getTagCompound();byte[] bytes=tag.getByteArray("VehicleDesign");
        return VehicleStructure.decode(com.vandorlabs.copier.core.BlueprintCompression.decode(tag.getInteger("VehicleDesignCodec"),bytes));
    }
    public static void write(ItemStack stack,VehicleStructure structure,String name)throws IOException{
        if(!isVehicle(stack))throw new IllegalArgumentException("Insert a Vehicle Chip in the attached Data Chip Mount");
        byte[] raw=structure.encode();ByteArrayOutputStream compressed=new ByteArrayOutputStream();try(OutputStream out=new GZIPOutputStream(compressed)){out.write(raw);}
        if(compressed.size()>MAX_STORED_BYTES)throw new IllegalArgumentException("Vehicle design exceeds the chip capacity (768 KiB compressed)");
        NBTTagCompound tag=new NBTTagCompound();tag.setString("ChipType","vehicle");tag.setString("VehicleName",ChipJournal.bounded(name,40));tag.setInteger("VehicleDesignCodec",1);tag.setByteArray("VehicleDesign",compressed.toByteArray());VehicleShape.of(structure).write(tag);stack.setTagCompound(tag);
    }
}
