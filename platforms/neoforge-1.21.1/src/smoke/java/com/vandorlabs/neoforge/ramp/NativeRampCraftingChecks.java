package com.vandorlabs.neoforge.ramp;

import com.google.gson.*;
import com.vandorlabs.neoforge.copifier.*;
import java.nio.file.*;
import java.util.*;
import java.util.zip.GZIPInputStream;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.crafting.*;

/** Actual canonical recipe dispatch for the released Ramp corpus and real result-slot ownership. */
public final class NativeRampCraftingChecks {
    private static int checks;
    private static void need(boolean yes,String why){checks++;if(!yes)throw new AssertionError("Ramp crafting: "+why);}
    static void run(ServerLevel world,Item[] rampItems)throws Exception {
        JsonArray rows;try(var in=new GZIPInputStream(Files.newInputStream(Path.of(System.getProperty("vandorlabs.copifierRecipeReference"))))){rows=JsonParser.parseString(new String(in.readAllBytes(),java.nio.charset.StandardCharsets.UTF_8)).getAsJsonArray();}
        var recipe=(CraftingRecipe)world.getRecipeManager().byKey(ResourceLocation.parse("vandorlabs:copied_settings")).orElseThrow().value();int count=0,outputs=0,emptySelected=0;
        for(var element:rows){var row=element.getAsJsonObject();if(!row.has("input"))continue;var source=TagParser.parseTag(row.get("input").getAsString());String id=source.getString("id");if(!id.equals("vandorlabs:programmable_ramp")&&!id.equals("vandorlabs:programmable_ramp_slab"))continue;
            var input=new ItemStack(rampItems[id.endsWith("_slab")?1:0],source.getByte("Count"));input.set(DataComponents.CUSTOM_DATA,CustomData.of(source.getCompound("tag")));
            var sourceTool=TagParser.parseTag(row.get("tool").getAsString());var tool=new ItemStack(NativeCopifierRegistrations.ITEM.get(),sourceTool.getByte("Count"));tool.set(DataComponents.CUSTOM_DATA,CustomData.of(sourceTool.getCompound("tag")));
            var before=input.copy();var beforeTool=tool.copy();int side=row.get("side").getAsInt();var cells=new ArrayList<ItemStack>(Collections.nCopies(side*side,ItemStack.EMPTY));cells.set(row.get("inputSlot").getAsInt(),input);cells.set(row.get("toolSlot").getAsInt(),tool);var grid=CraftingInput.of(side,side,cells);
            var result=recipe.assemble(grid,world.registryAccess());var expected=TagParser.parseTag(row.get("output").getAsString());boolean empty=expected.isEmpty()||expected.getString("id").equals("minecraft:air");need(result.isEmpty()==empty,"actual output admission case="+count);
            need(ItemStack.matches(input,before)&&ItemStack.matches(tool,beforeTool),"recipe leaves input and full tool intact");
            var remainder=recipe.getRemainingItems(grid);int tools=0;for(var stack:remainder)if(!stack.isEmpty()){need(ItemStack.matches(stack,tool),"exact tool remainder");tools++;}need(tools==1,"one remaining tool");
            if(!empty){var actual=result.get(DataComponents.CUSTOM_DATA).copyTag();var wanted=expected.getCompound("tag");need(result.getItem()==input.getItem()&&result.getCount()==1,"actual family/count");need(NativeRampSettings.read(actual.getCompound("BlockEntityTag")).capture(2).equals(NativeRampSettings.read(wanted.getCompound("BlockEntityTag")).capture(2)),"Source actor-free choices case="+count);need(actual.getCompound(NativeRampItem.COPIED).equals(wanted.getCompound(NativeRampItem.COPIED)),"complete Source placement marker");need(actual.getString("PrivateItem").equals(wanted.getString("PrivateItem")),"outer item metadata");if(source.getCompound("tag").contains(NativeRampItem.CONFIGURATION))need(actual.getCompound(NativeRampItem.CONFIGURATION).equals(source.getCompound("tag").getCompound(NativeRampItem.CONFIGURATION)),"ordinary configured envelope remains separate");if(actual.getCompound(NativeRampItem.COPIED).isEmpty())emptySelected++;outputs++;}
            count++;
        }
        need(count==384&&outputs==352&&emptySelected==32,"Source coverage count="+count+" outputs="+outputs+" emptySelected="+emptySelected);
        for(var item:rampItems)CopifierCraftingContainerChecks.runTarget(world,item);
        System.out.println("[vandorlabs][ramp-crafting] PASS Source384/outputs352/emptySelected32 checks="+checks+" canonical dispatcher/2x2/3x3/pickup/quickmove");
    }
    private NativeRampCraftingChecks(){}
}
