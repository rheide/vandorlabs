package com.vandorlabs.neoforge.compat.jei;

import com.vandorlabs.machines.BuilderRecipeCatalog;
import com.vandorlabs.machines.SynthesisRecipePolicy;
import com.vandorlabs.neoforge.VandorLabsBootstrap;
import com.vandorlabs.neoforge.builders.NativeBuilderBindings;
import com.vandorlabs.neoforge.builders.NativeBuilderProducts;
import com.vandorlabs.neoforge.gear.NativeGearBindings;
import com.vandorlabs.neoforge.infuser.NativeInfuserBindings;
import com.vandorlabs.neoforge.infuser.NativeInfuserMenu;
import com.vandorlabs.neoforge.infuser.client.NativeInfuserScreen;
import com.vandorlabs.neoforge.rack.NativeBatteryModule;
import com.vandorlabs.neoforge.rack.NativeRackBindings;
import com.vandorlabs.ui.InfuserDialog;
import com.vandorlabs.ui.MachineRecipeDisplay;
import mezz.jei.api.*;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.*;
import mezz.jei.api.recipe.category.IRecipeCategory;
import mezz.jei.api.registration.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.*;
import java.util.*;

/** Discovered only by the optional client recipe browser; gameplay does not link this class. */
@JeiPlugin
public final class NativeMachineJeiPlugin implements IModPlugin {
    private static java.lang.ref.WeakReference<mezz.jei.api.runtime.IJeiRuntime> current=new java.lang.ref.WeakReference<>(null);
    @Override public void onRuntimeAvailable(mezz.jei.api.runtime.IJeiRuntime runtime){current=new java.lang.ref.WeakReference<>(runtime);}
    @Override public void onRuntimeUnavailable(){current.clear();}
    public static mezz.jei.api.runtime.IJeiRuntime runtime(){return current.get();}
    public record Recipe(MachineRecipeDisplay.Kind kind,ItemStack input,ItemStack output,String machine,boolean catalog) {
        Recipe(MachineRecipeDisplay.Kind kind,ItemStack input,ItemStack output){this(kind,input,output,null,false);}
    }
    public static final RecipeType<Recipe> INFUSING=new RecipeType<>(ResourceLocation.parse(MachineRecipeDisplay.INFUSER),Recipe.class);
    public static final RecipeType<Recipe> SYNTHESIS=new RecipeType<>(ResourceLocation.parse(MachineRecipeDisplay.TOWER),Recipe.class);
    public static final RecipeType<Recipe> PRESS=new RecipeType<>(id("assembly_press"),Recipe.class);
    public static final RecipeType<Recipe> GANTRY=new RecipeType<>(id("enclosed_gantry"),Recipe.class);
    private record Builder(String machine,String title,RecipeType<Recipe> type) {
        boolean enabled(){
            return NativeBuilderBindings.CORES.containsKey(machine)&&(!machine.equals("enclosed_gantry")||NativeGearBindings.enabled());
        }
        ItemStack core(){return new ItemStack(NativeBuilderBindings.CORES.get(machine));}
    }
    private static final List<Builder> BUILDERS=List.of(
            new Builder("synthesis_tower","Synthesis Tower",SYNTHESIS),
            new Builder("assembly_press","Assembly Press",PRESS),
            new Builder("enclosed_gantry","Enclosed Gantry",GANTRY));
    private static ResourceLocation id(String path){return ResourceLocation.fromNamespaceAndPath("vandorlabs",path);}
    private static ItemStack item(String path){return new ItemStack(BuiltInRegistries.ITEM.get(id(path)));}

    @Override public ResourceLocation getPluginUid(){return id("machines");}
    @Override public void registerCategories(IRecipeCategoryRegistration registration){
        var gui=registration.getJeiHelpers().getGuiHelper();
        registration.addRecipeCategories(new Category(gui,INFUSING,"Matter Infuser",new ItemStack(NativeInfuserBindings.BLOCK)));
        for(var builder:BUILDERS)if(builder.enabled())registration.addRecipeCategories(
                new Category(gui,builder.type(),builder.title(),builder.core()));
    }
    @Override public void registerRecipes(IRecipeRegistration registration){
        var infuser=new ArrayList<Recipe>();
        infuser.add(new Recipe(MachineRecipeDisplay.Kind.INFUSING,new ItemStack(Items.IRON_INGOT),new ItemStack(VandorLabsBootstrap.MATTER_INGOT.get())));
        if(NativeRackBindings.BATTERY!=null){
            var charged=new ItemStack(NativeRackBindings.BATTERY);
            NativeBatteryModule.transfer(charged,NativeBatteryModule.CAPACITY,false,true);
            infuser.add(new Recipe(MachineRecipeDisplay.Kind.CHARGING,new ItemStack(NativeRackBindings.BATTERY),charged));
        }
        registration.addRecipes(INFUSING,infuser);
        for(var builder:BUILDERS){
            if(!builder.enabled())continue;
            String machine=builder.machine();
            var input=item(BuilderRecipeCatalog.input(machine));
            var defaults=BuilderRecipeCatalog.defaults(machine);
            var recipes=new ArrayList<Recipe>();
            for(String blueprint:BuilderRecipeCatalog.known(machine)){
                if(!NativeBuilderProducts.output(machine,id(blueprint).toString()))continue;
                var output=item(blueprint);
                if(!output.isEmpty())recipes.add(new Recipe(MachineRecipeDisplay.Kind.SYNTHESIS,input.copy(),output,machine,!defaults.contains(blueprint)));
            }
            registration.addRecipes(builder.type(),recipes);
        }
        registration.addItemStackInfo(new ItemStack(NativeInfuserBindings.CORE),Component.literal("Infuser Cores are currently available in Creative mode. Use one in the Matter Infuser crafting recipe."));
    }
    @Override public void registerRecipeCatalysts(IRecipeCatalystRegistration registration){
        registration.addRecipeCatalyst(NativeInfuserBindings.BLOCK,INFUSING);
        for(var builder:BUILDERS)if(builder.enabled())registration.addRecipeCatalyst(builder.core(),builder.type());
    }
    @Override public void registerGuiHandlers(IGuiHandlerRegistration registration){registration.addRecipeClickArea(NativeInfuserScreen.class,InfuserDialog.PROGRESS_X-1,InfuserDialog.PROGRESS_Y-1,InfuserDialog.PROGRESS_WIDTH+2,InfuserDialog.PROGRESS_HEIGHT+2,INFUSING);}
    @Override public void registerRecipeTransferHandlers(IRecipeTransferRegistration registration){registration.addRecipeTransferHandler(NativeInfuserMenu.class,NativeInfuserBindings.MENU,INFUSING,0,2,3,36);}
    private static final class Category implements IRecipeCategory<Recipe> {
        private final RecipeType<Recipe> type;
        private final Component title;
        private final IDrawable icon,background;
        Category(IGuiHelper gui,RecipeType<Recipe> type,String title,ItemStack icon){
            this.type=type;
            this.title=Component.literal(title);
            this.icon=gui.createDrawableItemStack(icon);
            background=gui.createBlankDrawable(MachineRecipeDisplay.WIDTH,MachineRecipeDisplay.HEIGHT);
        }
        @Override public RecipeType<Recipe> getRecipeType(){return type;}
        @Override public Component getTitle(){return title;}
        @Override public IDrawable getIcon(){return icon;}
        @Override public IDrawable getBackground(){return background;}
        @Override public int getWidth(){return MachineRecipeDisplay.WIDTH;}
        @Override public int getHeight(){return MachineRecipeDisplay.HEIGHT;}
        @Override public void setRecipe(IRecipeLayoutBuilder builder,Recipe recipe,IFocusGroup focus){
            builder.addInputSlot(MachineRecipeDisplay.INPUT_X+1,MachineRecipeDisplay.SLOT_Y+1).setStandardSlotBackground().addItemStack(recipe.input().copy());
            if(type==INFUSING)builder.addInputSlot(MachineRecipeDisplay.CATALYST_X+1,MachineRecipeDisplay.SLOT_Y+1).setStandardSlotBackground().addItemStacks(List.of(new ItemStack(Items.REDSTONE,7),new ItemStack(Items.REDSTONE_BLOCK)));
            builder.addOutputSlot(MachineRecipeDisplay.OUTPUT_X+1,MachineRecipeDisplay.SLOT_Y+1).setStandardSlotBackground().addItemStack(recipe.output().copy());
        }
        @Override public void draw(Recipe recipe,IRecipeSlotsView slots,GuiGraphics graphics,double mouseX,double mouseY){
            var font=Minecraft.getInstance().font;
            graphics.drawString(font,"->",102,25,0x404040,false);
            var lines=lines(recipe);
            for(int i=0;i<lines.length;i++)graphics.drawString(font,lines[i],4,46+i*13,0x404040,false);
        }
    }
    static String[] lines(Recipe recipe){
        if(recipe.kind()!=MachineRecipeDisplay.Kind.SYNTHESIS)return MachineRecipeDisplay.lines(recipe.kind());
        String blueprint=BuiltInRegistries.ITEM.getKey(recipe.output().getItem()).getPath();
        return new String[]{
                SynthesisRecipePolicy.requiredRf(recipe.machine(),blueprint)+" RF / item",
                SynthesisRecipePolicy.minimumTicks(recipe.machine(),blueprint)/20+" seconds minimum",
                recipe.catalog()?"Catalog Chip design required.":"Default machine blueprint.",
                "Select in Production; supply RF."
        };
    }
}
