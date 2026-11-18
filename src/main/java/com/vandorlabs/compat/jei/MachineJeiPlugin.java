package com.vandorlabs.compat.jei;

import com.vandorlabs.items.ModItems;
import com.vandorlabs.machines.BuilderRecipeCatalog;
import com.vandorlabs.rack.ModuleBatteryData;
import com.vandorlabs.ui.InfuserDialog;
import com.vandorlabs.ui.MachineRecipeDisplay;
import mezz.jei.api.*;
import mezz.jei.api.gui.*;
import mezz.jei.api.ingredients.IIngredients;
import mezz.jei.api.recipe.*;
import mezz.jei.api.ingredients.VanillaTypes;
import net.minecraft.client.Minecraft;
import net.minecraft.init.*;
import net.minecraft.item.ItemStack;
import net.minecraft.block.Block;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.relauncher.*;
import java.util.*;

/** Optional client integration. No gameplay class references a JEI API type. */
@JEIPlugin
@SideOnly(Side.CLIENT)
public final class MachineJeiPlugin implements IModPlugin {
    private static java.lang.ref.WeakReference<IJeiRuntime> current=new java.lang.ref.WeakReference<IJeiRuntime>(null);
    @Override public void onRuntimeAvailable(IJeiRuntime runtime){current=new java.lang.ref.WeakReference<IJeiRuntime>(runtime);}
    public static IJeiRuntime runtime(){return current.get();}
    private static ItemStack block(String path){Block value=Block.REGISTRY.getObject(new ResourceLocation("vandorlabs",path));if(value==Blocks.AIR)throw new IllegalStateException("Missing machine recipe item "+path);return new ItemStack(value);}
    private static ItemStack item(String path){return new ItemStack(net.minecraft.item.Item.REGISTRY.getObject(new ResourceLocation("vandorlabs",path)));}
    private static String uid(String machine){return "synthesis_tower".equals(machine)?MachineRecipeDisplay.TOWER:"vandorlabs:"+machine;}
    @Override public void registerCategories(IRecipeCategoryRegistration registration){IGuiHelper gui=registration.getJeiHelpers().getGuiHelper();registration.addRecipeCategories(new Category(gui,null),new Category(gui,"synthesis_tower"),new Category(gui,"assembly_press"),new Category(gui,"enclosed_gantry"));}
    @Override public void register(IModRegistry registry){
        ItemStack charged=new ItemStack(ModItems.BATTERY_MODULE);ModuleBatteryData.receive(charged,ModuleBatteryData.CAPACITY,false);
        registry.addRecipes(Arrays.asList(new Recipe(MachineRecipeDisplay.Kind.INFUSING,new ItemStack(Items.IRON_INGOT),new ItemStack(ModItems.PROGRAMMABLE_MATTER_INGOT)),new Recipe(MachineRecipeDisplay.Kind.CHARGING,new ItemStack(ModItems.BATTERY_MODULE),charged)),MachineRecipeDisplay.INFUSER);
        for(String machine:Arrays.asList("synthesis_tower","assembly_press","enclosed_gantry")){
            List<Recipe> recipes=new ArrayList<>();for(String blueprint:BuilderRecipeCatalog.known(machine))recipes.add(new Recipe(MachineRecipeDisplay.Kind.SYNTHESIS,item(BuilderRecipeCatalog.input(machine)),item(blueprint),!BuilderRecipeCatalog.defaults(machine).contains(blueprint),machine));
            registry.addRecipes(recipes,uid(machine));registry.addRecipeCatalyst(block(machine+"_core"),uid(machine));
        }
        registry.addRecipeCatalyst(block("programmable_matter_infuser"),MachineRecipeDisplay.INFUSER);
        registry.addRecipeClickArea(com.vandorlabs.client.GuiMatterInfuser.class,InfuserDialog.PROGRESS_X-1,InfuserDialog.PROGRESS_Y-1,InfuserDialog.PROGRESS_WIDTH+2,InfuserDialog.PROGRESS_HEIGHT+2,MachineRecipeDisplay.INFUSER);
        registry.getRecipeTransferRegistry().addRecipeTransferHandler(com.vandorlabs.container.ContainerMatterInfuser.class,MachineRecipeDisplay.INFUSER,0,2,3,36);
        // Tower selection is an authoritative operation. A generic transfer handler would
        // fill the input without selecting the requested Core and could build another design.
        registry.addIngredientInfo(new ItemStack(ModItems.INFUSER_CORE),VanillaTypes.ITEM,"Infuser Cores are currently available in Creative mode. Use one in the Matter Infuser crafting recipe.");
    }
    @SideOnly(Side.CLIENT)
    public static final class Recipe implements IRecipeWrapper {
        public final MachineRecipeDisplay.Kind kind;public final ItemStack input,output;private final boolean catalog;private final String machine;
        Recipe(MachineRecipeDisplay.Kind kind,ItemStack input,ItemStack output){this(kind,input,output,false,"synthesis_tower");}
        Recipe(MachineRecipeDisplay.Kind kind,ItemStack input,ItemStack output,boolean catalog,String machine){this.kind=kind;this.input=input.copy();this.output=output.copy();this.catalog=catalog;this.machine=machine;}
        @Override public void getIngredients(IIngredients ingredients){List<List<ItemStack>> inputs=new ArrayList<>();inputs.add(Collections.singletonList(input.copy()));if(kind!=MachineRecipeDisplay.Kind.SYNTHESIS)inputs.add(Arrays.asList(new ItemStack(Items.REDSTONE,7),new ItemStack(Blocks.REDSTONE_BLOCK)));ingredients.setInputLists(VanillaTypes.ITEM,inputs);ingredients.setOutput(VanillaTypes.ITEM,output.copy());}
        @Override public void drawInfo(Minecraft mc,int width,int height,int mouseX,int mouseY){String recipe=output.isEmpty()?"":output.getItem().getRegistryName().getResourcePath();String[] lines=kind==MachineRecipeDisplay.Kind.SYNTHESIS?new String[]{com.vandorlabs.machines.SynthesisRecipePolicy.requiredRf(machine,recipe)+" RF / item",(com.vandorlabs.machines.SynthesisRecipePolicy.minimumTicks(machine,recipe)/20)+" seconds minimum",catalog?"Catalog Chip design required.":"Default machine blueprint.","Select in Production; supply RF."}:MachineRecipeDisplay.lines(kind);for(int i=0;i<lines.length;i++)mc.fontRenderer.drawString(lines[i],4,46+i*13,0x404040);}
    }
    @SideOnly(Side.CLIENT)
    private static final class Category implements IRecipeCategory<Recipe> {
        private final boolean tower;private final String machine;private final IDrawable background,icon,slot;
        Category(IGuiHelper gui,String machine){this.machine=machine;this.tower=machine!=null;background=gui.createBlankDrawable(MachineRecipeDisplay.WIDTH,MachineRecipeDisplay.HEIGHT);icon=gui.createDrawableIngredient(block(tower?machine+"_core":"programmable_matter_infuser"));slot=gui.getSlotDrawable();}
        @Override public String getUid(){return tower?uid(machine):MachineRecipeDisplay.INFUSER;}
        @Override public String getTitle(){return !tower?"Matter Infuser":"assembly_press".equals(machine)?"Assembly Press":"enclosed_gantry".equals(machine)?"Enclosed Gantry":"Synthesis Tower";}
        @Override public String getModName(){return "Vandor Labs";}
        @Override public IDrawable getBackground(){return background;}
        @Override public IDrawable getIcon(){return icon;}
        @Override public void drawExtras(Minecraft mc){slot.draw(mc,MachineRecipeDisplay.INPUT_X,MachineRecipeDisplay.SLOT_Y);if(!tower)slot.draw(mc,MachineRecipeDisplay.CATALYST_X,MachineRecipeDisplay.SLOT_Y);slot.draw(mc,MachineRecipeDisplay.OUTPUT_X,MachineRecipeDisplay.SLOT_Y);mc.fontRenderer.drawString("->",102,25,0x404040);}
        @Override public void setRecipe(IRecipeLayout layout,Recipe recipe,IIngredients ingredients){IGuiItemStackGroup stacks=layout.getItemStacks();stacks.init(0,true,MachineRecipeDisplay.INPUT_X,MachineRecipeDisplay.SLOT_Y);if(!tower)stacks.init(1,true,MachineRecipeDisplay.CATALYST_X,MachineRecipeDisplay.SLOT_Y);stacks.init(2,false,MachineRecipeDisplay.OUTPUT_X,MachineRecipeDisplay.SLOT_Y);stacks.set(ingredients);}
    }
}
