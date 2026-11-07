package com.vandorlabs.client;

import com.google.gson.*;
import com.vandorlabs.blocks.ModBlocks;
import com.vandorlabs.items.*;
import java.nio.file.*;
import java.util.*;
import net.minecraft.block.Block;
import net.minecraft.inventory.*;
import net.minecraft.item.*;
import net.minecraft.nbt.*;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ResourceLocation;

/** Execute the released dynamic recipe, retaining complete input/output envelopes and tool remainders. */
public final class CopifierRecipeLegacyChecks {
 private static RecipeCopiedSettings RECIPE;
 private static InventoryCrafting grid(int side){return new InventoryCrafting(new Container(){public boolean canInteractWith(net.minecraft.entity.player.EntityPlayer player){return true;}},side,side);}
 private static NBTTagCompound values(int code){NBTTagCompound v=new NBTTagCompound();switch(code){
  case 0:break;case 1:v.setInteger("wall_texture",7);break;case 2:v.setIntArray("redstone_channel",new int[]{23,29});break;
  case 3:v.setInteger("door_design",15);break;case 4:v.setInteger("primary_surface",7);v.setInteger("secondary_surface",-2);break;
  case 5:v.setInteger("glass_size",1);v.setInteger("glass_shade",3);break;case 6:v.setBoolean("join",false);break;
  case 7:v.setTag("FrameAppearance",new NBTTagCompound());break;case 8:v.setInteger("wall_texture",Integer.MAX_VALUE);break;
  case 9:v.setString("wall_texture","wrong");break;case 10:v.setInteger("texture_layout",2);break;
  default:v.setInteger("wall_texture",7);v.setIntArray("redstone_channel",new int[]{23,29});v.setInteger("door_design",15);v.setInteger("primary_surface",7);v.setBoolean("join",false);
 }return v;}
 private static String stack(ItemStack stack){return stack.writeToNBT(new NBTTagCompound()).toString();}
 private static void observe(JsonArray rows,ItemStack input,int code,int side,int toolSlot,int inputSlot,boolean mask){
  InventoryCrafting inv=grid(side);ItemStack tool=new ItemStack(ModItems.DUPLIFIER);tool.setTagInfo(ItemDuplifier.SETTINGS_TAG,values(code));tool.setTagInfo("PrivateTool",new NBTTagString("retained"));
  if(mask)DuplifierApplyOptions.setMask(tool,new long[(DuplifierApplyOptions.OPTIONS.length+63)/64]);
  inv.setInventorySlotContents(toolSlot,tool);inv.setInventorySlotContents(inputSlot,input);String beforeTool=stack(tool),beforeInput=stack(input);
  ItemStack output=RECIPE.getCraftingResult(inv);net.minecraft.util.NonNullList<ItemStack> left=RECIPE.getRemainingItems(inv);
  if(!beforeTool.equals(stack(tool))||!beforeInput.equals(stack(input)))throw new AssertionError("Recipe mutated an input");
  if(!beforeTool.equals(stack(left.get(toolSlot)))||left.get(toolSlot)==tool)throw new AssertionError("Tool remainder must be an independent complete copy");
  for(int i=0;i<left.size();i++)if(i!=toolSlot&&!left.get(i).isEmpty())throw new AssertionError("Unexpected input remainder");
  if(RECIPE.matches(inv,null)!=!output.isEmpty())throw new AssertionError("Recipe match disagrees with output");
  JsonObject row=new JsonObject();row.addProperty("input",beforeInput);row.addProperty("tool",beforeTool);row.addProperty("side",side);row.addProperty("toolSlot",toolSlot);row.addProperty("inputSlot",inputSlot);row.addProperty("masked",mask);row.addProperty("output",stack(output));row.addProperty("remainingTool",stack(left.get(toolSlot)));rows.add(row);
 }
 @SuppressWarnings("unchecked") public static void main(String[] args)throws Exception{
  net.minecraft.init.Bootstrap.register();net.minecraftforge.fml.common.ModMetadata metadata=new net.minecraftforge.fml.common.ModMetadata();metadata.modId="vandorlabs";metadata.name="Vandor Labs";metadata.version="2.9";
  java.lang.reflect.Field controller=net.minecraftforge.fml.common.Loader.class.getDeclaredField("modController");controller.setAccessible(true);controller.set(net.minecraftforge.fml.common.Loader.instance(),new net.minecraftforge.fml.common.LoadController(net.minecraftforge.fml.common.Loader.instance()));net.minecraftforge.fml.common.Loader.instance().setActiveModContainer(new net.minecraftforge.fml.common.DummyModContainer(metadata));
  RECIPE=new RecipeCopiedSettings();net.minecraftforge.fml.common.registry.ForgeRegistries.ITEMS.register(ModItems.DUPLIFIER);
  java.lang.reflect.Field mapping=Item.class.getDeclaredField("BLOCK_TO_ITEM");mapping.setAccessible(true);Map<Block,Item> items=(Map<Block,Item>)mapping.get(null);Set<Class<? extends TileEntity>> types=new HashSet<>();types.add(com.vandorlabs.tiles.TileEntityTokenSlot.class);net.minecraftforge.fml.common.registry.GameRegistry.registerTileEntity(com.vandorlabs.tiles.TileEntityTokenSlot.class,new ResourceLocation("vandorlabs","recipe_reader"));JsonArray rows=new JsonArray();int families=0;
  Set<String> ids=new HashSet<>(Arrays.asList("programmable_block","programmable_slab","programmable_stairs","programmable_wall","programmable_porthole_wall","programmable_porthole_block","programmable_diagonal_wall","programmable_diagonal_glass","programmable_diagonal_porthole","programmable_storage","programmable_door","large_programmable_door","programmable_trapdoor","programmable_diagonal_trapdoor","programmable_light","programmable_light_frame","programmable_light_slab","programmable_trigger_block","programmable_viewscreen","programmable_console","programmable_diagonal_screen","programmable_diagonal_half_screen","programmable_half_console","programmable_input","programmable_half_input","programmable_glass","access_card_slot","data_chip_slot","small_power_lever"));
  for(Block block:ModBlocks.BLOCKS){if(!ids.remove(block.getRegistryName().getResourcePath()))continue;net.minecraftforge.fml.common.registry.ForgeRegistries.BLOCKS.register(block);ItemBlock item=(ItemBlock)new ItemBlock(block).setRegistryName(block.getRegistryName());net.minecraftforge.fml.common.registry.ForgeRegistries.ITEMS.register(item);items.put(block,item);TileEntity sample=block.createTileEntity(null,block.getDefaultState());if(sample!=null&&types.add(sample.getClass()))net.minecraftforge.fml.common.registry.GameRegistry.registerTileEntity(sample.getClass(),new ResourceLocation("vandorlabs","recipe_"+block.getRegistryName().getResourcePath()));
   for(int meta=0;meta<16;meta++)for(int code=0;code<12;code++){ItemStack input=new ItemStack(item,17,meta);NBTTagCompound privateTag=new NBTTagCompound();privateTag.setString("PrivateItem","retained");if((code&1)!=0){NBTTagCompound existing=new NBTTagCompound();existing.setInteger("housingTexture",14);existing.setInteger("HousingTextureVersion",1);existing.setInteger("RedstoneChannel",11);existing.setString("PrivateTile","retained");privateTag.setTag("BlockEntityTag",existing);}input.setTagCompound(privateTag);observe(rows,input,code,2,code%4,(code+1)%4,false);}families++;
  }
  if(!ids.isEmpty())throw new AssertionError("Missing recipe families "+ids);
  ItemStack plain=new ItemStack(items.get(ModBlocks.PROGRAMMABLE_BLOCK),17);
  for(int mask=0;mask<256;mask++){
   InventoryCrafting inv=grid(2);int tools=0,inputs=0,other=0;
   for(int i=0;i<4;i++){int kind=mask>>2*i&3;ItemStack value=ItemStack.EMPTY;if(kind==1){value=new ItemStack(ModItems.DUPLIFIER);value.setTagInfo(ItemDuplifier.SETTINGS_TAG,values(1));tools++;}else if(kind==2){value=plain.copy();inputs++;}else if(kind==3){value=new ItemStack(net.minecraft.init.Items.STICK);other++;}inv.setInventorySlotContents(i,value);}
   ItemStack result=RECIPE.getCraftingResult(inv);if(!result.isEmpty()!=(tools==1&&inputs==1&&other==0))throw new AssertionError("Grid acceptance "+mask);JsonObject row=new JsonObject();row.addProperty("grid",mask);row.addProperty("output",stack(result));JsonArray remaining=new JsonArray();for(ItemStack value:RECIPE.getRemainingItems(inv))remaining.add(stack(value));row.add("remaining",remaining);rows.add(row);
  }
  for(int side:new int[]{2,3})for(int a=0;a<side*side;a++)for(int b=0;b<side*side;b++)if(a!=b)for(boolean mask:new boolean[]{false,true})observe(rows,plain.copy(),1,side,a,b,mask);
  // Slot acceptance is independent of the supported block converter; exercise every 2x2 and 3x3 ordering.
  for(int side:new int[]{2,3})for(int a=0;a<side*side;a++)for(int b=0;b<side*side;b++)if(a!=b)observe(rows,new ItemStack(net.minecraft.init.Blocks.STONE,17),1,side,a,b,false);
  if(!RECIPE.canFit(1,2)||RECIPE.canFit(1,1)||!RECIPE.isDynamic()||!RECIPE.getRecipeOutput().isEmpty())throw new AssertionError("Dynamic recipe contract changed");
  byte[] raw=new Gson().toJson(rows).getBytes(java.nio.charset.StandardCharsets.UTF_8);Path output=Paths.get(args[0]);Files.createDirectories(output.getParent());Files.write(output,raw);StringBuilder hash=new StringBuilder();for(byte b:java.security.MessageDigest.getInstance("SHA-256").digest(raw))hash.append(String.format(Locale.ROOT,"%02x",b&255));if(!hash.toString().equals("acdab7ca97641eca086ae384002d20553df960e534d9b0909e3a4ed539cbddc8"))throw new AssertionError("Released recipe changed "+hash);System.out.println("Copied recipe Source PASS families="+families+" cases="+rows.size()+" unavailable="+ids+" sha256="+hash);
 }
}
