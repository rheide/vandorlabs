package com.vandorlabs.neoforge.compat.jei;
import com.vandorlabs.neoforge.builders.*;
import com.vandorlabs.neoforge.infuser.*;
import com.vandorlabs.neoforge.infuser.client.NativeInfuserScreen;
import com.vandorlabs.neoforge.rack.NativeBatteryModule;
import com.vandorlabs.ui.MachineRecipeDisplay;
import net.minecraft.client.Minecraft;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.*;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.*;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.*;
import java.util.*;
/** Actual installed browser API, real transfer packet and rendered category windows. */
public final class NativeMachineJeiChecks {
 private static int stage,ticks,wait;private static boolean capture;private static int captured;
 private static void need(boolean v,String why){if(!v)throw new AssertionError("Native machine JEI stage="+stage+": "+why);}
 private static void next(int n){stage=n;wait=0;}
 public static void tick(ClientTickEvent.Post e){if(!NativeMachineJeiFixture.enabled())return;var m=Minecraft.getInstance();try{if(m.screen instanceof net.minecraft.client.gui.screens.AccessibilityOnboardingScreen intro){intro.onClose();return;}if(stage==12&&m.level==null){System.out.println("[vandorlabs][machine-jei] client PASS pluginDiscovery/categories4/recipes2+6+5+8/catalysts/costPolicy/catalogGates/charged10000RF/actualBrowser/realTransfer");stage=13;m.stop();return;}if(m.player==null||m.level==null||m.gameMode==null)return;need(++ticks<1200&&++wait<400,"timeout screen="+m.screen);var runtime=NativeMachineJeiPlugin.runtime();if(runtime==null)return;var manager=runtime.getRecipeManager();
  if(stage==0){verifyRecipes(manager);next(1);return;}
  if(stage==1){if(m.gameMode.getPlayerMode()!=GameType.SURVIVAL||m.player.getInventory().selected!=0||!m.player.getMainHandItem().isEmpty()||m.player.position().distanceToSqr(.5,96,-2.5)>.01||!(m.level.getBlockEntity(NativeMachineJeiFixture.POS) instanceof NativeInfuserEntity))return;var target=new Vec3(.5,96.5,0).subtract(m.player.getEyePosition()).normalize();if(m.player.getLookAngle().dot(target)<.9999)return;m.gameMode.useItemOn(m.player,InteractionHand.MAIN_HAND,new BlockHitResult(new Vec3(.5,96.5,0),Direction.NORTH,NativeMachineJeiFixture.POS,false));next(2);return;}
  if(stage==2){if(!(m.screen instanceof NativeInfuserScreen gui)||wait<10)return;var category=manager.getRecipeCategory(NativeMachineJeiPlugin.INFUSING);var recipe=manager.createRecipeLookup(NativeMachineJeiPlugin.INFUSING).get().filter(r->r.kind()==MachineRecipeDisplay.Kind.INFUSING).findFirst().orElseThrow();var handler=runtime.getRecipeTransferManager().getRecipeTransferHandler(gui.getMenu(),category).orElseThrow();var dummy=new NativeBuilderEntity(NativeMachineJeiFixture.POS,NativeBuilderBindings.CORES.get("synthesis_tower").defaultBlockState());dummy.setLevel(m.level);var towerMenu=new NativeBuilderMenu(NativeBuilderBindings.MENU,99,m.player.getInventory(),dummy);need(runtime.getRecipeTransferManager().getRecipeTransferHandler(towerMenu,manager.getRecipeCategory(NativeMachineJeiPlugin.SYNTHESIS)).isEmpty(),"Tower generic transfer absent");var layout=manager.createRecipeLayoutDrawable(category,recipe,runtime.getJeiHelpers().getFocusFactory().getEmptyFocusGroup()).orElseThrow();need(handler.transferRecipe(gui.getMenu(),recipe,layout.getRecipeSlotsView(),m.player,false,false)==null,"actual recipe transfer preflight");need(handler.transferRecipe(gui.getMenu(),recipe,layout.getRecipeSlotsView(),m.player,false,true)==null,"actual JEI transfer packet");next(3);return;}
  if(stage==3){if(!(m.screen instanceof NativeInfuserScreen gui)||!gui.getMenu().contents().getItem(0).is(Items.IRON_INGOT)||gui.getMenu().contents().getItem(1).getCount()!=7||!m.player.getInventory().getItem(9).isEmpty()||!m.player.getInventory().getItem(10).isEmpty())return;System.out.println("[vandorlabs][machine-jei] transfer PASS realSurvivalMenu/JEIpacket/iron1+redstone7");runtime.getRecipesGui().showTypes(List.of(NativeMachineJeiPlugin.INFUSING));next(4);return;}
  if(stage==4||stage==6||stage==8||stage==10){if(wait<20)return;need(m.screen!=null&&m.screen.getClass().getName().startsWith("mezz.jei."),"actual browser open");capture=true;next(stage+1);return;}
  if(stage==5){if(capture)return;runtime.getRecipesGui().showTypes(List.of(NativeMachineJeiPlugin.SYNTHESIS));next(6);return;}
  if(stage==7){if(capture)return;runtime.getRecipesGui().showTypes(List.of(NativeMachineJeiPlugin.PRESS));next(8);return;}
  if(stage==9){if(capture)return;runtime.getRecipesGui().showTypes(List.of(NativeMachineJeiPlugin.GANTRY));next(10);return;}
  if(stage==11){if(capture)return;need(captured==4,"four actual browser captures");m.level.disconnect();m.disconnect();next(12);}
 }catch(Throwable failure){failure.printStackTrace();System.out.println("[vandorlabs][machine-jei] FAIL "+failure);m.stop();}}
 private static void verifyRecipes(mezz.jei.api.recipe.IRecipeManager manager){
  need(manager.getRecipeCategory(NativeMachineJeiPlugin.INFUSING)!=null,"Infuser category discovered");
  var recipes=manager.createRecipeLookup(NativeMachineJeiPlugin.INFUSING).get().toList();
  need(recipes.size()==2,"Infuser has exactly two recipes");
  need(recipes.stream().anyMatch(r->r.kind()==MachineRecipeDisplay.Kind.CHARGING&&NativeBatteryModule.energy(r.input())==0&&NativeBatteryModule.energy(r.output())==10000),"physical fully charged output");
  need(recipes.stream().anyMatch(r->r.kind()==MachineRecipeDisplay.Kind.INFUSING&&r.input().is(Items.IRON_INGOT)&&r.output().is(com.vandorlabs.neoforge.VandorLabsBootstrap.MATTER_INGOT.get())),"iron to Matter Ingot");
  need(manager.createRecipeCatalystLookup(NativeMachineJeiPlugin.INFUSING).getItemStack().anyMatch(s->s.is(NativeInfuserBindings.BLOCK.asItem())),"Infuser catalyst");
  verifyBuilder(manager,NativeMachineJeiPlugin.SYNTHESIS,"synthesis_tower","programmable_matter_block",
       Set.of("synthesis_tower_core","assembly_press_core","enclosed_gantry_core"),
       Set.of("standalone_antenna_core","standalone_dish_core","programmable_ramp"),20);
  verifyBuilder(manager,NativeMachineJeiPlugin.PRESS,"assembly_press","programmable_matter_ingot",
       Set.of("data_chip","access_card"),Set.of("battery_module","autopilot_module","navigation_data_module"),40);
  verifyBuilder(manager,NativeMachineJeiPlugin.GANTRY,"enclosed_gantry","programmable_matter_block",
       Set.of("gear_extender","gear_swing_arm","large_gear_extender","large_gear_swing_arm","landing_wheel",
              "large_landing_wheel","landing_ski","landing_plate"),Set.of(),40);
 }
 private static String key(ItemStack stack){return net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath();}
 private static void verifyBuilder(mezz.jei.api.recipe.IRecipeManager manager,mezz.jei.api.recipe.RecipeType<NativeMachineJeiPlugin.Recipe> type,
       String machine,String input,Set<String> defaults,Set<String> catalog,int rfPerTick){
  var category=manager.getRecipeCategory(type);
  need(category!=null,"native category discovered for "+machine);
  need(manager.createRecipeCatalystLookup(type).getItemStack().anyMatch(s->s.is(NativeBuilderBindings.CORES.get(machine).asItem())),
       "native Core catalyst for "+machine);
  var actualDefaults=new HashSet<String>();var actualCatalog=new HashSet<String>();
  for(var recipe:manager.createRecipeLookup(type).get().toList()){
   need(recipe.kind()==MachineRecipeDisplay.Kind.SYNTHESIS&&recipe.machine().equals(machine)&&key(recipe.input()).equals(input),
        "native recipe machine and input match "+machine);
   String blueprint=key(recipe.output());
   need((recipe.catalog()?actualCatalog:actualDefaults).add(blueprint),"one recipe per blueprint "+blueprint);
   int cost=machine.equals("synthesis_tower")&&blueprint.endsWith("_core")?16000:8000;
   var lines=NativeMachineJeiPlugin.lines(recipe);
   need(lines[0].equals(cost+" RF / item")&&lines[1].equals(cost/rfPerTick/20+" seconds minimum")
          &&lines[2].equals(recipe.catalog()?"Catalog Chip design required.":"Default machine blueprint."),
        "actual displayed RF/time and chip requirement for "+blueprint);
  }
  need(actualDefaults.equals(defaults)&&actualCatalog.equals(catalog),"exact recipe and gate inventory for "+machine);
 }
 public static void frame(RenderFrameEvent.Post e)throws Exception{if(!capture)return;var m=Minecraft.getInstance();if(m.screen==null)return;try(var image=net.minecraft.client.Screenshot.takeScreenshot(m.getMainRenderTarget())){var colors=new HashSet<Integer>();for(int y=image.getHeight()/4;y<image.getHeight()*3/4;y+=2)for(int x=image.getWidth()/4;x<image.getWidth()*3/4;x+=2)colors.add(image.getPixelRGBA(x,y));need(colors.size()>64,"actual browser pixels contain authored textured recipes");image.writeToFile(m.gameDirectory.toPath().resolve("machine-jei-"+List.of("infusing","synthesis","assembly-press","enclosed-gantry").get(captured++)+".png"));capture=false;}}
 private NativeMachineJeiChecks(){}
}
