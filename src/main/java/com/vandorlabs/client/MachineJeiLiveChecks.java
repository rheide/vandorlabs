package com.vandorlabs.client;

import com.vandorlabs.compat.jei.MachineJeiPlugin;
import com.vandorlabs.container.*;
import com.vandorlabs.items.ModItems;
import com.vandorlabs.rack.ModuleBatteryData;
import com.vandorlabs.tiles.*;
import com.vandorlabs.ui.MachineRecipeDisplay;
import mezz.jei.api.*;
import mezz.jei.api.recipe.*;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.*;
import net.minecraft.item.ItemStack;
import net.minecraft.util.*;
import net.minecraft.util.math.*;
import net.minecraft.world.GameType;
import net.minecraftforge.fml.relauncher.*;
import java.io.File;
import java.util.*;

/** Installed JEI discovery, real transfer packets and actual recipe-browser rendering. */
@SideOnly(Side.CLIENT)
final class MachineJeiLiveChecks {
 private static final BlockPos POS=new BlockPos(100,120,100);
 private static int stage,ticks;
 private static IRecipeCategory<MachineJeiPlugin.Recipe> infuser,tower;
 private static List<MachineJeiPlugin.Recipe> recipes;
 private static EntityPlayerMP player(Minecraft mc){return mc.getIntegratedServer().getPlayerList().getPlayers().get(0);}
 private static void need(boolean value,String why){if(!value)throw new IllegalStateException("Machine JEI stage="+stage+": "+why);}
 @SuppressWarnings("unchecked") static boolean tick(Minecraft mc,File out)throws Exception{
  if(stage==6)return true;
  if(++ticks>600)throw new IllegalStateException("Machine JEI timeout stage="+stage+" screen="+mc.currentScreen);
  IJeiRuntime runtime=MachineJeiPlugin.runtime();if(runtime==null)return false;IRecipeRegistry registry=runtime.getRecipeRegistry();
  if(stage==0){infuser=(IRecipeCategory<MachineJeiPlugin.Recipe>)registry.getRecipeCategory(MachineRecipeDisplay.INFUSER);tower=(IRecipeCategory<MachineJeiPlugin.Recipe>)registry.getRecipeCategory(MachineRecipeDisplay.TOWER);need(infuser!=null&&tower!=null,"plugin discovered both categories");recipes=registry.getRecipeWrappers(infuser);need(recipes.size()==2&&registry.getRecipeWrappers(tower).size()==3,"exact2infuser/3core recipes");Set<String> outputs=new HashSet<>();for(MachineJeiPlugin.Recipe recipe:registry.getRecipeWrappers(tower))outputs.add(recipe.output.getItem().getRegistryName().toString());need(outputs.equals(new HashSet<>(Arrays.asList("vandorlabs:synthesis_tower_core","vandorlabs:assembly_press_core","vandorlabs:enclosed_gantry_core"))),"three exact Core outputs");boolean iron=false,charge=false;for(MachineJeiPlugin.Recipe recipe:recipes){if(recipe.kind==MachineRecipeDisplay.Kind.CHARGING)charge=ModuleBatteryData.energy(recipe.output)==10000&&ModuleBatteryData.energy(recipe.input)==0;else iron=recipe.input.getItem()==Items.IRON_INGOT&&recipe.output.getItem()==ModItems.PROGRAMMABLE_MATTER_INGOT;}need(iron&&charge,"iron and physical10000RF module outputs");catalyst(registry,infuser,"programmable_matter_infuser");catalyst(registry,tower,"synthesis_tower_core");
   mc.playerController.setGameType(GameType.SURVIVAL);mc.player.inventory.currentItem=0;mc.getIntegratedServer().addScheduledTask(()->{EntityPlayerMP p=player(mc);p.interactionManager.setGameType(GameType.SURVIVAL);p.inventory.clear();p.inventory.currentItem=0;p.inventory.setInventorySlotContents(9,new ItemStack(Items.IRON_INGOT));p.inventory.setInventorySlotContents(10,new ItemStack(Items.REDSTONE,7));p.setHeldItem(EnumHand.MAIN_HAND,ItemStack.EMPTY);p.setHeldItem(EnumHand.OFF_HAND,ItemStack.EMPTY);p.capabilities.allowFlying=true;p.capabilities.isFlying=true;p.sendPlayerAbilities();p.world.setBlockState(POS,net.minecraft.block.Block.getBlockFromName("vandorlabs:programmable_matter_infuser").getDefaultState());p.world.setBlockState(POS.up(),Blocks.STONE.getDefaultState());p.connection.setPlayerLocation(100.5,120.5-p.getEyeHeight(),97.5,0,0);p.inventoryContainer.detectAndSendChanges();}).get();next(1);return false;
  }
  if(stage==1){if(ticks<15||mc.playerController.getCurrentGameType()!=GameType.SURVIVAL||mc.player.inventory.currentItem!=0||!mc.player.getHeldItemMainhand().isEmpty()||mc.player.getPositionEyes(1).squareDistanceTo(new Vec3d(100.5,120.5,97.5))>.025||!(mc.world.getTileEntity(POS) instanceof TileEntityMatterInfuser))return false;need(mc.playerController.processRightClickBlock(mc.player,mc.world,POS,EnumFacing.NORTH,new Vec3d(100.5,120.5,100),EnumHand.MAIN_HAND)==EnumActionResult.SUCCESS,"ordinary Survival menu packet");next(2);return false;}
  if(stage==2){if(!(mc.currentScreen instanceof GuiMatterInfuser)||ticks<10)return false;ContainerMatterInfuser menu=(ContainerMatterInfuser)mc.player.openContainer;mezz.jei.api.recipe.transfer.IRecipeTransferHandler handler=registry.getRecipeTransferHandler(menu,infuser);need(handler!=null,"Infuser transfer registered");need(registry.getRecipeTransferHandler(new ContainerSynthesisTower(mc.player.inventory,new TileEntityConstructor()),tower)==null,"Tower generic transfer absent");MachineJeiPlugin.Recipe iron=recipes.stream().filter(r->r.kind==MachineRecipeDisplay.Kind.INFUSING).findFirst().get();mezz.jei.api.gui.IRecipeLayoutDrawable layout=registry.createRecipeLayoutDrawable(infuser,iron,registry.createFocus(IFocus.Mode.INPUT,iron.input));need(layout!=null&&handler.transferRecipe(menu,layout,mc.player,false,false)==null,"real available recipe transfer preflight");need(handler.transferRecipe(menu,layout,mc.player,false,true)==null,"real recipe transfer packet");next(3);return false;}
  if(stage==3){boolean transferred=mc.getIntegratedServer().callFromMainThread(()->{EntityPlayerMP p=player(mc);TileEntityMatterInfuser t=(TileEntityMatterInfuser)p.world.getTileEntity(POS);return t.getStackInSlot(0).getItem()==Items.IRON_INGOT&&t.getStackInSlot(0).getCount()==1&&t.getStackInSlot(1).getItem()==Items.REDSTONE&&t.getStackInSlot(1).getCount()==1&&p.inventory.getStackInSlot(9).isEmpty()&&p.inventory.getStackInSlot(10).getItem()==Items.REDSTONE&&p.inventory.getStackInSlot(10).getCount()==6;}).get();if(!transferred)return false;System.out.println("[vandorlabs] machine-jei-transfer PASS actualSurvivalMenu/JEIpacket/iron1+redstone1/playerRetains6/exactserverOwnership/noTowerTransfer");runtime.getRecipesGui().showCategories(Collections.singletonList(MachineRecipeDisplay.INFUSER));next(4);return false;}
  if(stage==4||stage==5){if(ticks<15)return false;need(mc.currentScreen!=null&&mc.currentScreen.getClass().getName().startsWith("mezz.jei."),"actual recipe browser open");mc.getFramebuffer().bindFramebuffer(true);mc.entityRenderer.setupOverlayRendering();mc.currentScreen.drawScreen(-1,-1,0);java.awt.image.BufferedImage image=net.minecraft.util.ScreenShotHelper.createScreenshot(mc.displayWidth,mc.displayHeight,mc.getFramebuffer());javax.imageio.ImageIO.write(image,"png",new File(out,stage==4?"machine-jei-infusing.png":"machine-jei-synthesis.png"));Set<Integer> colors=new HashSet<>();for(int y=image.getHeight()/4;y<image.getHeight()*3/4;y+=2)for(int x=image.getWidth()/4;x<image.getWidth()*3/4;x+=2)colors.add(image.getRGB(x,y));need(colors.size()>64,"actual textured browser/category screenshot");if(stage==4){runtime.getRecipesGui().showCategories(Collections.singletonList(MachineRecipeDisplay.TOWER));next(5);return false;}next(6);mc.player.closeScreen();System.out.println("[vandorlabs] machine-jei PASS installedPlugin/categories2+recipes2+3/catalysts/charged10000RF/browserCaptures/actualTransfer");return true;}
  return false;
 }
 private static void next(int value){stage=value;ticks=0;}
 private static void catalyst(IRecipeRegistry registry,IRecipeCategory<?> category,String id){need(registry.getRecipeCatalysts(category).stream().anyMatch(v->v instanceof ItemStack&&((ItemStack)v).getItem()==net.minecraft.item.Item.getItemFromBlock(net.minecraft.block.Block.getBlockFromName("vandorlabs:"+id))),"actual catalyst "+id);}
}
