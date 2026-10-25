package com.vandorlabs.items;

import com.vandorlabs.VandorLabs;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.NonNullList;

/** Handheld design cards and chips. Metadata is stable across creative variants and saved stacks. */
public final class ItemDesignToken extends Item {
    public static final String[] DESIGNS={"unbranded","pinnacle","ashiro","paladyne","vektor","seyr","vandor"};
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
        if(com.vandorlabs.programmer.ChipJournal.journal(stack)){
            String title=com.vandorlabs.programmer.ChipJournal.title(stack);
            String company=stack.getMetadata()>0&&stack.getMetadata()<DESIGNS.length?DESIGNS[stack.getMetadata()]:"";
            if(!company.isEmpty())company=Character.toUpperCase(company.charAt(0))+company.substring(1)+" ";
            return company+"Journal Data Chip"+(title.isEmpty()?"":" - "+title);
        }
        return super.getItemStackDisplayName(stack);
    }
    @Override public void getSubItems(CreativeTabs tab,NonNullList<ItemStack> items){
        if(isInCreativeTab(tab))for(int i=0;i<DESIGNS.length;i++)items.add(new ItemStack(this,1,i));
    }
}
