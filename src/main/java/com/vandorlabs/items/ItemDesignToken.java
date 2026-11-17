package com.vandorlabs.items;

import com.vandorlabs.VandorLabs;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.NonNullList;

/** Handheld design cards and chips. Metadata is stable across creative variants and saved stacks. */
public final class ItemDesignToken extends Item {
    public static final String[] DESIGNS=com.vandorlabs.credentials.CredentialCatalog.DESIGNS;
    private final String kind;
    public ItemDesignToken(String kind){
        this.kind=kind;
        setRegistryName(VandorLabs.MODID,kind);
        setUnlocalizedName("vandorlabs."+kind);
        setHasSubtypes(true);
        setCreativeTab(VandorLabs.VANDOR_LABS_TAB);
    }
    /** The unbranded icon represents a wildcard only in credential selectors. */
    public static String filterName(ItemStack stack){
        if(stack.getMetadata()==0&&stack.getItem() instanceof ItemDesignToken)return ((ItemDesignToken)stack.getItem()).kind.equals("data_chip")?"Any Chip":"Any Card";
        return stack.getDisplayName();
    }
    @Override public String getUnlocalizedName(ItemStack stack){
        int design=stack.getMetadata();
        return "item.vandorlabs."+kind+"."+DESIGNS[design>=0&&design<DESIGNS.length?design:0];
    }
    @Override public String getItemStackDisplayName(ItemStack stack){
        if(!kind.equals("data_chip"))return super.getItemStackDisplayName(stack);
        if(com.vandorlabs.programmer.CatalogChipData.catalog(stack))return "Catalog Chip"+(stack.getTagCompound().getString("CatalogName").isEmpty()?"":" ("+com.vandorlabs.programmer.ChipJournal.bounded(stack.getTagCompound().getString("CatalogName"),40)+")");
        if("navigation".equals(chipType(stack)))return com.vandorlabs.navigation.NavigationChip.programmed(stack)?"Navigation Chip - "+com.vandorlabs.navigation.NavigationChip.value(stack,"X")+", "+com.vandorlabs.navigation.NavigationChip.value(stack,"Y")+", "+com.vandorlabs.navigation.NavigationChip.value(stack,"Z"):"Navigation Chip (blank)";
        if(com.vandorlabs.vehicle.VehicleChip.isVehicle(stack)){String name=com.vandorlabs.vehicle.VehicleChip.name(stack);return "Vehicle Data Chip"+(name.isEmpty()?" (blank)":" - "+name);}
        if(com.vandorlabs.programmer.ChipJournal.journal(stack)){
            String title=com.vandorlabs.programmer.ChipJournal.title(stack);
            return "Journal Data Chip"+(title.isEmpty()?"":" - "+title);
        }
        return super.getItemStackDisplayName(stack);
    }
    public static String chipType(ItemStack stack){return stack.hasTagCompound()?stack.getTagCompound().getString("ChipType"):"";}
    public static int chipAppearance(ItemStack stack){String type=chipType(stack);return "journal".equals(type)?1:"vehicle".equals(type)?2:"navigation".equals(type)?3:"catalog".equals(type)?4:0;}
    @Override public void getSubItems(CreativeTabs tab,NonNullList<ItemStack> items){
        if(isInCreativeTab(tab)){for(int i=0;i<DESIGNS.length;i++)items.add(new ItemStack(this,1,i));if(kind.equals("data_chip")){ItemStack journal=new ItemStack(this);com.vandorlabs.programmer.ChipJournal.write(journal,true,"",new String[0]);items.add(journal);ItemStack vehicle=new ItemStack(this);com.vandorlabs.vehicle.VehicleChip.blank(vehicle);items.add(vehicle);ItemStack navigation=new ItemStack(this);net.minecraft.nbt.NBTTagCompound tag=new net.minecraft.nbt.NBTTagCompound();tag.setString("ChipType","navigation");navigation.setTagCompound(tag);items.add(navigation);items.add(com.vandorlabs.programmer.CatalogChipData.masterStack());}}
    }
}
