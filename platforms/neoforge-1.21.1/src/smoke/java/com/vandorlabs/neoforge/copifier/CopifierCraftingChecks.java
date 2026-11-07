package com.vandorlabs.neoforge.copifier;

import com.google.gson.*;
import com.vandorlabs.neoforge.light.NativeLightBlock;
import com.vandorlabs.neoforge.door.*;
import com.vandorlabs.neoforge.glass.*;
import com.vandorlabs.neoforge.storage.NativeStorageBlock;
import com.vandorlabs.screen.ScreenFamily;
import com.vandorlabs.neoforge.trapdoor.*;
import com.vandorlabs.neoforge.NativeTriggerBlock;
import com.vandorlabs.neoforge.segmented.NativeSegmentedDoorBlock;
import com.vandorlabs.neoforge.canopy.*;
import java.nio.file.*;
import java.util.*;
import java.util.zip.GZIPInputStream;
import net.minecraft.core.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.*;
import net.minecraft.nbt.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.crafting.*;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStartedEvent;

/** Actual canonical dynamic recipe and independent released recipe outputs. */
@EventBusSubscriber(modid="vandorlabs")
public final class CopifierCraftingChecks {
    private static NativeCopifierItem tool;
    private static RecipeSerializer<?> serializer;
    private static int checks;
    private static boolean enabled(){return "copifier-crafting".equals(System.getProperty("vandorlabs.transmitterPhase"));}
    private static void need(boolean ok,String message){checks++;if(!ok)throw new AssertionError("Copied crafting: "+message);}
    private static NativeCopifierCraftingRecipe recipe(CraftingBookCategory category){return new NativeCopifierCraftingRecipe(category,()->serializer,NativeCopifierCraftingItems::apply);}
    private static ItemStack tool(CompoundTag data){var stack=new ItemStack(tool);stack.set(DataComponents.CUSTOM_DATA,CustomData.of(data));return stack;}
    private static CompoundTag data(ItemStack stack){return stack.getOrDefault(DataComponents.CUSTOM_DATA,CustomData.EMPTY).copyTag();}
    private static void run(net.minecraft.server.level.ServerLevel level)throws Exception{
        JsonArray rows;try(var in=new GZIPInputStream(Files.newInputStream(Path.of(System.getProperty("vandorlabs.copifierRecipeReference"))))){rows=JsonParser.parseString(new String(in.readAllBytes(),java.nio.charset.StandardCharsets.UTF_8)).getAsJsonArray();}
        tool=NativeCopifierRegistrations.ITEM.get();serializer=NativeCopifierRegistrations.COPIED_SETTINGS.get();
        var recipe=(NativeCopifierCraftingRecipe)level.getRecipeManager().byKey(ResourceLocation.parse("vandorlabs:copied_settings")).orElseThrow().value();
        need(BuiltInRegistries.ITEM.get(ResourceLocation.parse("vandorlabs:duplifier"))==tool&&!BuiltInRegistries.ITEM.containsKey(ResourceLocation.parse("vandorlabs:copifier_crafting_fixture")),"canonical tool and loaded public recipe without fixture substitutes");
        CopifierCraftingContainerChecks.run(level);int cases=0,grids=0;
        for(var element:rows){var row=element.getAsJsonObject();var stacks=NonNullList.withSize(row.has("grid")?4:row.get("side").getAsInt()*row.get("side").getAsInt(),ItemStack.EMPTY);
            String canopy=null;boolean segmented=false;boolean trigger=false;Boolean diagonal=null;ScreenFamily screen=null;boolean glass=false;String doorId=null;NativeLightBlock.Kind lightKind=null;NativeCopifierHousingCapture.Family family=null;ItemStack originalTool=null,originalInput=null;int toolSlot=-1,inputSlot=-1;
            if(row.has("grid")){
                int mask=row.get("grid").getAsInt();for(int i=0;i<4;i++)switch(mask>>2*i&3){case 1->{var root=new CompoundTag();var values=new CompoundTag();values.putInt("wall_texture",7);root.put("DuplifierSettings",values);stacks.set(i,tool(root));}case 2->stacks.set(i,new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.parse("vandorlabs:programmable_block")),17));case 3->stacks.set(i,new ItemStack(Items.STICK));}grids++;
            }else{
                var source=TagParser.parseTag(row.get("input").getAsString());String id=source.getString("id");if(Boolean.getBoolean("vandorlabs.copifierRecipeConfigured")&&!BuiltInRegistries.ITEM.containsKey(ResourceLocation.parse(id)))continue;
                try{family=NativeCopifierHousingCapture.Family.of(id.substring(id.indexOf(':')+1));}catch(IllegalArgumentException unsupported){var block=BuiltInRegistries.BLOCK.get(ResourceLocation.parse(id));if(block instanceof NativeCanopyBlock cb)canopy=cb.kind();else if(block instanceof NativeSegmentedDoorBlock)segmented=true;else if(block instanceof NativeTriggerBlock)trigger=true;else if(block instanceof NativeTrapdoorBlock td)diagonal=td.diagonal();else if(ScreenFamily.fromId(ResourceLocation.parse(id).getPath()) instanceof ScreenFamily sf)screen=sf;else if(block instanceof NativeStorageBlock)family=NativeCopifierHousingCapture.Family.CUBE;else if(block instanceof NativeGlassBlock)glass=true;else if(block instanceof NativeLightBlock light)lightKind=light.kind();else if(block instanceof NativeProgrammableDoorBlock||block instanceof NativeLargeDoorBlock)doorId=ResourceLocation.parse(id).getPath();else {/* Source-ineligible items still exercise the actual refusal path. */}}
                var item=BuiltInRegistries.ITEM.get(ResourceLocation.parse(id));need(item instanceof BlockItem,"registered input "+id);originalInput=new ItemStack(item,source.getByte("Count"));originalInput.set(DataComponents.CUSTOM_DATA,CustomData.of(source.getCompound("tag")));
                originalTool=tool(TagParser.parseTag(row.get("tool").getAsString()).getCompound("tag"));toolSlot=row.get("toolSlot").getAsInt();inputSlot=row.get("inputSlot").getAsInt();stacks.set(toolSlot,originalTool);stacks.set(inputSlot,originalInput);
            }
            var originals=new ArrayList<ItemStack>();for(var stack:stacks)originals.add(stack.copy());int side=row.has("grid")?2:row.get("side").getAsInt();var input=CraftingInput.of(side,side,stacks);var output=recipe.assemble(input,level.registryAccess());var expected=TagParser.parseTag(row.get("output").getAsString());boolean empty=expected.isEmpty()||expected.getString("id").equals("minecraft:air");
            need(output.isEmpty()==empty,"Source applicability case="+cases+" grid="+row.get("grid")+" family="+family);
            need(recipe.matches(input,level)==!output.isEmpty(),"actual matches/assemble");
            if(!output.isEmpty()){need(output.getCount()==1,"one output");var selectedFamily=family==null?NativeCopifierHousingCapture.Family.CUBE:family;var actual=canopy!=null?data(output):data(output).getCompound("BlockEntityTag");var source=canopy!=null?expected.getCompound("tag"):expected.getCompound("tag").getCompound("BlockEntityTag");if(canopy!=null)need(!data(output).contains("BlockEntityTag")&&!output.has(DataComponents.BLOCK_ENTITY_DATA),"Source outer Canopy envelope");need((canopy!=null?NativeCopifierCanopyEdit.capture(canopy,NativeCanopySettings.read(source)):segmented?NativeCopifierSegmentedEdit.capture(NativeCopifierSegmentedEdit.read(source)):trigger?NativeCopifierTriggerEdit.capture(source):diagonal!=null?NativeCopifierTrapdoorEdit.capture(new NativeCopifierTrapdoorEdit.State(NativeTrapdoorSettings.read(source,diagonal),!diagonal)):screen!=null?NativeCopifierScreenEdit.capture(NativeCopifierScreenEdit.read(screen,source)):glass?NativeCopifierGlassEdit.capture(NativeGlassSettings.read(source)):doorId!=null?NativeCopifierDoorEdit.capture(NativeCopifierDoorEdit.read(doorId,source)):lightKind==null?NativeCopifierHousingCapture.capture(selectedFamily,source):NativeCopifierLightEdit.capture(lightKind,source)).equals(canopy!=null?NativeCopifierCanopyEdit.capture(canopy,NativeCanopySettings.read(actual)):segmented?NativeCopifierSegmentedEdit.capture(NativeCopifierSegmentedEdit.read(actual)):trigger?NativeCopifierTriggerEdit.capture(actual):diagonal!=null?NativeCopifierTrapdoorEdit.capture(new NativeCopifierTrapdoorEdit.State(NativeTrapdoorSettings.read(actual,diagonal),!diagonal)):screen!=null?NativeCopifierScreenEdit.capture(NativeCopifierScreenEdit.read(screen,actual)):glass?NativeCopifierGlassEdit.capture(NativeGlassSettings.read(actual)):doorId!=null?NativeCopifierDoorEdit.capture(NativeCopifierDoorEdit.read(doorId,actual)):lightKind==null?NativeCopifierHousingCapture.capture(selectedFamily,actual):NativeCopifierLightEdit.capture(lightKind,actual)),"Source resulting choices case="+cases+" family="+family+" expected="+source+" actual="+actual);if(glass)need(expected.getCompound("tag").getCompound("ProgrammableGlassSettings").equals(data(output).getCompound("ProgrammableGlassSettings")),"Source dedicated glass envelope");if(originalInput!=null)need(data(output).getString("PrivateItem").equals(data(originalInput).getString("PrivateItem")),"private outer item data retained");}
            var remaining=recipe.getRemainingItems(input);for(int i=0;i<stacks.size();i++)need(ItemStack.matches(originals.get(i),stacks.get(i)),"input unchanged");for(int i=0;i<input.size();i++){var current=input.getItem(i);need(current.getItem() instanceof NativeCopifierItem?ItemStack.matches(remaining.get(i),current)&&remaining.get(i)!=current:remaining.get(i).isEmpty(),"complete independent tool remainder");}cases++;
        }
        ownership(level,recipe);if(Boolean.getBoolean("vandorlabs.copifierRecipeConfigured"))CopifierCanopyPlacementChecks.run(level);need(Boolean.getBoolean("vandorlabs.copifierRecipeConfigured")?grids==0&&cases>1000:grids==256&&cases==6076,"independent Source coverage");need(recipe.canCraftInDimensions(1,2)&&!recipe.canCraftInDimensions(1,1)&&recipe.getSerializer()==serializer,"native recipe identity/dimensions");System.out.println("[vandorlabs][copifier-crafting] PASS cases="+cases+" grids="+grids+" checks="+checks+" actual recipe/shared slots/Source housing choices/masks/remainders/input immutability; canonical loaded recipe/tool; Ramp adapters pending");
    }
    private static void ownership(net.minecraft.server.level.ServerLevel level,NativeCopifierCraftingRecipe recipe){
        canopyOwnership(level,recipe);
        var item=BuiltInRegistries.ITEM.get(ResourceLocation.parse("vandorlabs:programmable_storage"));
        var clipboard=new CompoundTag();var values=new CompoundTag();values.putInt("wall_texture",7);clipboard.put("DuplifierSettings",values);
        for(String id:new String[]{"programmable_block","programmable_storage","programmable_door","large_programmable_door","programmable_glass","programmable_trapdoor","programmable_diagonal_trapdoor","segmented_door","programmable_viewscreen","programmable_light","programmable_trigger_block"}){
            var input=new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.parse("vandorlabs:"+id)),2);input.set(DataComponents.CONTAINER,net.minecraft.world.item.component.ItemContainerContents.fromItems(List.of(new ItemStack(Items.DIAMOND,7))));var copy=input.copy();var grid=CraftingInput.of(2,1,List.of(tool(clipboard),input));need(recipe.assemble(grid,level.registryAccess()).isEmpty()&&ItemStack.matches(input,copy),"nested physical contents refused intact="+id);
        }
        for(String text:new String[]{"{DoorPlacementRecovery:1b}","{DoorCredentialRecovery:{Opaque:[B;1b,2b]}}","{DoorKeyReader:{InsertedToken:{id:\"minecraft:diamond\",Count:1b}}}","{DoorRearKeyReader:\"opaque owner\"}","{ContainedFluids:{Side0:{FluidName:\"minecraft:water\",Amount:1000}}}"})for(String id:new String[]{"programmable_door","large_programmable_door"}){
            try{var input=new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.parse("vandorlabs:"+id)),2);var root=new CompoundTag();root.put("BlockEntityTag",TagParser.parseTag(text));input.set(DataComponents.CUSTOM_DATA,CustomData.of(root));var copy=input.copy();var grid=CraftingInput.of(2,1,List.of(tool(clipboard),input));need(recipe.assemble(grid,level.registryAccess()).isEmpty()&&ItemStack.matches(input,copy),"Door owner refusal="+id+" "+text);}catch(com.mojang.brigadier.exceptions.CommandSyntaxException invalid){throw new AssertionError(invalid);}
        }
        for(int code=0;code<8;code++){
            var owner=new CompoundTag();switch(code){
                case 0->{var list=new ListTag();var physical=new CompoundTag();physical.putString("id","minecraft:diamond");physical.putByte("Count",(byte)7);physical.putByte("Slot",(byte)0);list.add(physical);owner.put("Items",list);}
                case 1->owner.putString("Items","unknown physical data");
                case 2->{var list=new ListTag();list.add(StringTag.valueOf("unknown future owner"));owner.put("Items",list);}
                case 3->owner.putInt("NativeStorageSchema",99);
                case 4->owner.put("NativeStorageRecoverySnapshot",new CompoundTag());
                case 5->owner.put("NativeStoragePlacementRecovery",new CompoundTag());
                case 6->owner.put("NativeStoragePreviousInventory",new CompoundTag());
                case 7->{owner.putInt("NativeStorageInventorySchema",1);owner.putInt("NativeStorageItemDataVersion",Integer.MAX_VALUE);owner.put("Items",new ListTag());}
            }
            owner.putByteArray("OpaqueOwner",new byte[65536]);var root=new CompoundTag();root.put("BlockEntityTag",owner);root.putString("PrivateItem","retain exactly");var input=new ItemStack(item,2);input.set(DataComponents.CUSTOM_DATA,CustomData.of(root));var original=input.copy();var tool=tool(clipboard);var originalTool=tool.copy();
            var grid=CraftingInput.of(2,1,List.of(tool,input));need(recipe.assemble(grid,level.registryAccess()).isEmpty(),"unsupported Storage ownership refused="+code);need(ItemStack.matches(input,original)&&ItemStack.matches(tool,originalTool),"refused complete owner unchanged="+code);
        }
    }
    private static void canopyOwnership(net.minecraft.server.level.ServerLevel level,NativeCopifierCraftingRecipe recipe){
        var values=new CompoundTag();values.putInt("glass_shade",2);var clipboard=new CompoundTag();clipboard.put("DuplifierSettings",values);
        for(String text:new String[]{"{CanopyRecoverySnapshot:{Opaque:[B;1b,2b]}}","{CanopyPlacementItem:{Opaque:[B;3b]}}","{CanopyUninitialized:1b}","{BlockEntityTag:{CanopyRecoverySnapshot:{Opaque:[B;4b]}}}","{BlockEntityTag:{NativeCanopySchema:99}}","{BlockEntityTag:{ForgeCaps:{owner:{Opaque:[B;5b]}}}}"}){
            try{var input=new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.parse("vandorlabs:canopy")),2);input.set(DataComponents.CUSTOM_DATA,CustomData.of(TagParser.parseTag(text)));var before=input.copy();need(recipe.assemble(CraftingInput.of(2,1,List.of(tool(clipboard),input)),level.registryAccess()).isEmpty()&&ItemStack.matches(input,before),"Canopy recovery/future ownership refuses intact="+text);}catch(com.mojang.brigadier.exceptions.CommandSyntaxException invalid){throw new AssertionError(invalid);}
        }
        var input=new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.parse("vandorlabs:canopy")),2);input.set(DataComponents.CONTAINER,net.minecraft.world.item.component.ItemContainerContents.fromItems(List.of(new ItemStack(Items.DIAMOND,7))));var before=input.copy();need(recipe.assemble(CraftingInput.of(2,1,List.of(tool(clipboard),input)),level.registryAccess()).isEmpty()&&ItemStack.matches(input,before),"Canopy nested physical owner refuses intact");
    }
    @SubscribeEvent public static void started(ServerStartedEvent event){if(!enabled())return;try{run(event.getServer().overworld());event.getServer().halt(false);}catch(Throwable failure){failure.printStackTrace();event.getServer().halt(false);throw new IllegalStateException("Copied recipe failed",failure);}}
}
