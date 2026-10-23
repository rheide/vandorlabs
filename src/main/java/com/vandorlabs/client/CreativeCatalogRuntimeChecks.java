package com.vandorlabs.client;

import com.vandorlabs.VandorLabs;
import net.minecraft.block.Block;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.item.*;
import net.minecraft.util.NonNullList;

/** Actual item registrations, creative search and optional JEI ingredient discovery. */
final class CreativeCatalogRuntimeChecks {
    private static final String[] VISIBLE={"small_power_lever","industrial_power_lever","gear_mount","gear_extender","landing_ski","landing_wheel","landing_plate"};
    private static final String[] HIDDEN={"large_power_lever","compact_power_lever","landing_gear","ski_landing_gear","landing_strut","angled_landing_strut"};
    static void run(){
        java.util.Collection<?> ingredients=null;
        if(net.minecraftforge.fml.common.Loader.isModLoaded("jei")){
            try{
                Object registry=Class.forName("mezz.jei.Internal").getMethod("getIngredientRegistry").invoke(null);
                ingredients=(java.util.Collection<?>)registry.getClass().getMethod("getAllIngredients",Class.class).invoke(registry,ItemStack.class);
            }catch(ReflectiveOperationException failure){throw new IllegalStateException("JEI catalog check",failure);}
        }
        for(boolean hidden:new boolean[]{false,true})for(String id:hidden?HIDDEN:VISIBLE){
            Block block=Block.getBlockFromName("vandorlabs:"+id);
            Item item=Item.getItemFromBlock(block);
            require(item instanceof com.vandorlabs.items.ItemCatalogBlock,"catalog-aware registration "+id);
            for(CreativeTabs tab:new CreativeTabs[]{VandorLabs.VANDOR_LABS_TAB,CreativeTabs.SEARCH}){
                NonNullList<ItemStack> menu=NonNullList.create();item.getSubItems(tab,menu);
                require(menu.size()==(hidden?0:1),"creative/search entry count "+id);
            }
            require(item.getCreativeTabs().length==(hidden?0:1),"browser tab discovery "+id);
            if(hidden){
                ItemBlock old=new ItemBlock(block);
                require(old.getCreativeTabs().length==1&&old.getCreativeTabs()[0]==null,"JEI fallback negative control "+id);
                require(!new ItemStack(item).isEmpty(),"legacy inventory stack preserved "+id);
            }
            if(ingredients!=null){
                long count=ingredients.stream().filter(value->value instanceof ItemStack&&((ItemStack)value).getItem()==item).count();
                require(count==(hidden?0:1),"actual JEI ingredient count "+id+" = "+count);
            }
        }
        if(ingredients!=null)System.out.println("[vandorlabs][reprolab] creative-catalog-JEI-live PASS canonical levers and modular gear visible; legacy levers and integrated gear hidden");
        System.out.println("[vandorlabs][reprolab] creative-catalog PASS creative search browser-discovery legacy-items negative-controls");
    }
    private static void require(boolean ok,String message){if(!ok)throw new IllegalStateException("Creative catalog: "+message);}
}
