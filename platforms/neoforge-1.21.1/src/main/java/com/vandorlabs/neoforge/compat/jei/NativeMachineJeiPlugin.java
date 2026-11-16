package com.vandorlabs.neoforge.compat.jei;

import com.vandorlabs.neoforge.VandorLabsBootstrap;
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
    public record Recipe(MachineRecipeDisplay.Kind kind,ItemStack input,ItemStack output){}
    public static final RecipeType<Recipe> INFUSING=new RecipeType<>(ResourceLocation.parse(MachineRecipeDisplay.INFUSER),Recipe.class);
    public static final RecipeType<Recipe> SYNTHESIS=new RecipeType<>(ResourceLocation.parse(MachineRecipeDisplay.TOWER),Recipe.class);
    private static ItemStack block(String path){var id=ResourceLocation.fromNamespaceAndPath("vandorlabs",path);return new ItemStack(BuiltInRegistries.BLOCK.getOptional(id).orElseThrow(()->new IllegalStateException("Missing machine recipe item "+id)));}
    @Override public ResourceLocation getPluginUid(){return ResourceLocation.fromNamespaceAndPath("vandorlabs","machines");}
    @Override public void registerCategories(IRecipeCategoryRegistration registration){var gui=registration.getJeiHelpers().getGuiHelper();registration.addRecipeCategories(new Category(gui,false),new Category(gui,true));}
    @Override public void registerRecipes(IRecipeRegistration registration){
        var charged=new ItemStack(NativeRackBindings.BATTERY);NativeBatteryModule.transfer(charged,NativeBatteryModule.CAPACITY,false,true);
        registration.addRecipes(INFUSING,List.of(new Recipe(MachineRecipeDisplay.Kind.INFUSING,new ItemStack(Items.IRON_INGOT),new ItemStack(VandorLabsBootstrap.MATTER_INGOT.get())),new Recipe(MachineRecipeDisplay.Kind.CHARGING,new ItemStack(NativeRackBindings.BATTERY),charged)));
        var cores=new ArrayList<Recipe>();for(String design:new String[]{"synthesis_tower","assembly_press","enclosed_gantry"})cores.add(new Recipe(MachineRecipeDisplay.Kind.SYNTHESIS,block("programmable_matter_block"),block(design+"_core")));
        registration.addRecipes(SYNTHESIS,cores);
        registration.addItemStackInfo(new ItemStack(NativeInfuserBindings.CORE),Component.literal("Infuser Cores are currently available in Creative mode. Use one in the Matter Infuser crafting recipe."));
    }
    @Override public void registerRecipeCatalysts(IRecipeCatalystRegistration registration){registration.addRecipeCatalyst(NativeInfuserBindings.BLOCK,INFUSING);registration.addRecipeCatalyst(block("synthesis_tower_core"),SYNTHESIS);}
    @Override public void registerGuiHandlers(IGuiHandlerRegistration registration){registration.addRecipeClickArea(NativeInfuserScreen.class,InfuserDialog.PROGRESS_X-1,InfuserDialog.PROGRESS_Y-1,InfuserDialog.PROGRESS_WIDTH+2,InfuserDialog.PROGRESS_HEIGHT+2,INFUSING);}
    @Override public void registerRecipeTransferHandlers(IRecipeTransferRegistration registration){registration.addRecipeTransferHandler(NativeInfuserMenu.class,NativeInfuserBindings.MENU,INFUSING,0,2,3,36);}
    private static final class Category implements IRecipeCategory<Recipe> {
        private final boolean tower;private final IDrawable icon,background;
        Category(IGuiHelper gui,boolean tower){this.tower=tower;icon=gui.createDrawableItemStack(block(tower?"synthesis_tower_core":"programmable_matter_infuser"));background=gui.createBlankDrawable(MachineRecipeDisplay.WIDTH,MachineRecipeDisplay.HEIGHT);}
        @Override public RecipeType<Recipe> getRecipeType(){return tower?SYNTHESIS:INFUSING;}
        @Override public Component getTitle(){return Component.literal(MachineRecipeDisplay.title(tower));}
        @Override public IDrawable getIcon(){return icon;}
        @Override public IDrawable getBackground(){return background;}
        @Override public int getWidth(){return MachineRecipeDisplay.WIDTH;}
        @Override public int getHeight(){return MachineRecipeDisplay.HEIGHT;}
        @Override public void setRecipe(IRecipeLayoutBuilder builder,Recipe recipe,IFocusGroup focus){
            builder.addInputSlot(MachineRecipeDisplay.INPUT_X+1,MachineRecipeDisplay.SLOT_Y+1).setStandardSlotBackground().addItemStack(recipe.input().copy());
            if(!tower)builder.addInputSlot(MachineRecipeDisplay.CATALYST_X+1,MachineRecipeDisplay.SLOT_Y+1).setStandardSlotBackground().addItemStacks(List.of(new ItemStack(Items.REDSTONE,7),new ItemStack(Items.REDSTONE_BLOCK)));
            builder.addOutputSlot(MachineRecipeDisplay.OUTPUT_X+1,MachineRecipeDisplay.SLOT_Y+1).setStandardSlotBackground().addItemStack(recipe.output().copy());
        }
        @Override public void draw(Recipe recipe,IRecipeSlotsView slots,GuiGraphics graphics,double mouseX,double mouseY){var font=Minecraft.getInstance().font;graphics.drawString(font,"->",102,25,0x404040,false);var lines=MachineRecipeDisplay.lines(recipe.kind());for(int i=0;i<lines.length;i++)graphics.drawString(font,lines[i],4,46+i*13,0x404040,false);}
    }
}
