package com.vandorlabs.navigation;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

/** Navigation payload leaves company metadata and unrelated item data intact. */
public final class NavigationChip {
    private NavigationChip(){}
    public static boolean programmed(ItemStack stack){
        if(stack.isEmpty()||stack.getItem()!=com.vandorlabs.items.ModItems.DATA_CHIP||!stack.hasTagCompound())return false;
        NBTTagCompound t=stack.getTagCompound();return "navigation".equals(t.getString("ChipType"))&&t.hasKey("NavigationLocation",10)&&valid(t.getCompoundTag("NavigationLocation"));
    }
    private static boolean valid(NBTTagCompound t){return t.hasKey("Dimension",3)&&t.hasKey("X",3)&&t.hasKey("Y",3)&&t.hasKey("Z",3)&&Math.abs((long)t.getInteger("X"))<=NavigationMap.LIMIT&&Math.abs((long)t.getInteger("Z"))<=NavigationMap.LIMIT;}
    public static int value(ItemStack stack,String key){return stack.getTagCompound().getCompoundTag("NavigationLocation").getInteger(key);}
    public static boolean inDimension(ItemStack stack,int dimension){return programmed(stack)&&value(stack,"Dimension")==dimension;}
    public static void write(ItemStack stack,int dimension,int x,int z){
        if(stack.isEmpty()||stack.getItem()!=com.vandorlabs.items.ModItems.DATA_CHIP)throw new IllegalArgumentException("A data chip is required");
        int y=programmed(stack)?value(stack,"Y"):64;
        NBTTagCompound t=stack.hasTagCompound()?stack.getTagCompound().copy():new NBTTagCompound(),location=new NBTTagCompound();
        location.setInteger("Dimension",dimension);location.setInteger("X",x);location.setInteger("Y",y);location.setInteger("Z",z);for(String key:new String[]{"JournalTitle","JournalPages","JournalFont","VehicleDesign","VehicleDesignCodec","VehicleShape","VehicleName","Catalog","CatalogName","CatalogBlock"})t.removeTag(key);t.removeTag("NavigationMapPixels");t.setString("ChipType","navigation");t.setTag("NavigationLocation",location);stack.setTagCompound(t);
    }
    public static SavedMapPixels savedMap(ItemStack stack){
        if(!programmed(stack)||!stack.getTagCompound().hasKey("NavigationMapPixels",10))return null;
        NBTTagCompound t=stack.getTagCompound().getCompoundTag("NavigationMapPixels");
        for(String key:new String[]{"Version","CenterX","CenterZ","Scale","Hash"})if(!t.hasKey(key,3))return null;
        if(t.getInteger("Version")!=1||!t.hasKey("Colors",7)||!t.hasKey("Palette",11))return null;
        try{SavedMapPixels map=new SavedMapPixels(Integer.toString(value(stack,"Dimension")),t.getInteger("CenterX"),t.getInteger("CenterZ"),t.getInteger("Scale"),t.getByteArray("Colors"),t.getIntArray("Palette"));return map.hash()==t.getInteger("Hash")&&map.centerX()==value(stack,"X")&&map.centerZ()==value(stack,"Z")?map:null;}catch(IllegalArgumentException invalid){return null;}
    }
    public static void writeMap(ItemStack stack,SavedMapPixels map){
        int dimension=Integer.parseInt(map.dimension());write(stack,dimension,map.centerX(),map.centerZ());NBTTagCompound t=new NBTTagCompound();t.setInteger("Version",1);t.setInteger("CenterX",map.centerX());t.setInteger("CenterZ",map.centerZ());t.setInteger("Scale",map.scale());t.setByteArray("Colors",map.colors());t.setIntArray("Palette",map.palette());t.setInteger("Hash",map.hash());stack.getTagCompound().setTag("NavigationMapPixels",t);
    }
    /** Public update projection only; never replace the inventory or saved stack with this copy. */
    public static ItemStack summary(ItemStack stack){ItemStack copy=stack.copy();if(copy.hasTagCompound()&&copy.getTagCompound().hasKey("NavigationMapPixels",10)){NBTTagCompound t=copy.getTagCompound().getCompoundTag("NavigationMapPixels");t.removeTag("Colors");t.removeTag("Palette");}return copy;}

}
