package com.vandorlabs.neoforge;

import com.vandorlabs.neoforge.client.*;
import com.vandorlabs.persistence.HousingSettings;
import com.vandorlabs.tiles.CustomMaterialIdentity;
import com.vandorlabs.ui.ProgrammableBlockDialog;
import java.nio.file.*;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import net.neoforged.neoforge.client.model.data.ModelData;

/** Actual survival menu/inventory interaction and immutable model-bake reload checks. */
@EventBusSubscriber(modid="vandorlabs",value=Dist.CLIENT)
public final class SampledMaterialClientChecks {
    private static int ticks,stage,wait;
    private static NativeCubeScreen picker;
    private static List<ItemStack> inventory;
    private static BakedModel oldWorld,oldItem;
    private static BakedQuad oldWorldQuad,oldItemQuad;
    private static int[] oldWorldVertices,oldItemVertices;
    private static java.util.concurrent.CompletableFuture<Void> reload;
    private static String screenshot;
    private static volatile boolean captured;
    private static final int STONE=0x33dd5f15;
    private static void require(boolean value,String message){if(!value)throw new IllegalStateException("Native sampled material client: stage="+stage+" tick="+ticks+" "+message);}
    private static ModelData data(int choice){return ModelData.builder().with(ProgrammableBlockEntity.SETTINGS,new ProgrammableBlockDialog.State(choice,1,false,new int[]{-1,-1,-1,-1,-1,-1},-1)).build();}
    private static BakedQuad world(BakedModel model,int choice,Direction face){var state=VandorLabsBootstrap.PROGRAMMABLE_BLOCK.get().defaultBlockState();var quads=model.getQuads(state,face,RandomSource.create(0),data(choice),null);require(quads.size()==1,"world quad count");return quads.getFirst();}
    private static ItemStack configured(int choice){var item=new ItemStack(VandorLabsBootstrap.PROGRAMMABLE_ITEM.get());var tag=new CompoundTag();tag.putInt(HousingSettings.MAIN,choice);tag.putInt(HousingSettings.LAYOUT,1);tag.putString("OpaqueFutureField","kept");item.set(DataComponents.BLOCK_ENTITY_DATA,CustomData.of(tag));return item;}
    private static void checkInventory(Minecraft mc){for(int i=0;i<36;i++)require(ItemStack.matches(inventory.get(i),mc.player.getInventory().getItem(i)),"sampling changed client inventory slot="+i+" expected="+inventory.get(i)+" actual="+mc.player.getInventory().getItem(i));}
    private static void custom(NativeCubeScreen screen){
        int visible=Math.max(1,ProgrammableBlockDialog.listRows(screen.panel(),screen.editingState())-1);
        int target=screen.visibleRows().indexOf(NativeCubeScreen.CUSTOM_ROW);require(target>=0,"custom row missing");
        for(int count=0;target-screen.scroll()>=visible&&count<5000;count++)screen.mouseScrolled(screen.panel().listX+20,ProgrammableBlockDialog.listY(screen.panel(),screen.editingState())+30,0,-1);
        int row=target-screen.scroll();require(row>=0&&row<visible,"custom row inaccessible");
        screen.mouseClicked(screen.panel().listX+30,ProgrammableBlockDialog.listY(screen.panel(),screen.editingState())+22+row*22+8,0);require(screen.inventoryPicker()!=null,"custom row did not open inline inventory");
    }
    private static void sample(NativeCubeScreen screen,int slot,int button){var grid=screen.inventoryPicker().layout;screen.mouseClicked(grid.cellX(slot)+8,grid.cellY(slot)+8,button);}
    private static int current(Minecraft mc){return ((ProgrammableBlockEntity)mc.level.getBlockEntity(SampledMaterialRuntimeChecks.POS)).settings().mainTexture;}
    private static void click(NativeCubeScreen screen,int operation){for(var control:ProgrammableBlockDialog.controls(screen.panel(),screen.editingState(),"Done"))if(control.id==operation){screen.mouseClicked(control.x+control.width/2.,control.y+control.height/2.,0);return;}throw new AssertionError("Missing shared control "+operation);}
    private static void scene(Minecraft mc){
        for(var pos:new net.minecraft.core.BlockPos[]{SampledMaterialRuntimeChecks.OLD,SampledMaterialRuntimeChecks.MISSING}){
            require(mc.level.getBlockEntity(pos) instanceof ProgrammableBlockEntity,"scene cube missing "+pos);var tile=(ProgrammableBlockEntity)mc.level.getBlockEntity(pos);int expected=pos.equals(SampledMaterialRuntimeChecks.OLD)?SampledMaterialRuntimeChecks.LEGACY:SampledMaterialRuntimeChecks.UNAVAILABLE;
            require(tile.settings().mainTexture==expected&&!tile.settings().facesEnabled,"scene cube settings not synchronized "+pos+" main="+tile.settings().mainTexture+" faces="+tile.settings().facesEnabled);
            var model=mc.getBlockRenderer().getBlockModel(tile.getBlockState());var quad=model.getQuads(tile.getBlockState(),Direction.NORTH,RandomSource.create(0),tile.getModelData(),null).getFirst();
            require(quad.getSprite()==world(model,expected,Direction.NORTH).getSprite(),"actual scene tile model-data material mismatch "+pos);
            System.out.println("[vandorlabs][sampled-material] scene "+pos+" main="+expected+" sprite="+quad.getSprite().contents().name());
        }
    }
    private static void open(Minecraft mc){mc.gameMode.useItemOn(mc.player,InteractionHand.MAIN_HAND,new BlockHitResult(new Vec3(.5,80.5,0),Direction.NORTH,SampledMaterialRuntimeChecks.POS,false));}
    private static Path override(Minecraft mc){return mc.gameDirectory.toPath().resolve("resourcepacks/sample-fixture/assets/minecraft/models/block/stone.json");}
    private static void capture(String name){screenshot=name;captured=false;}
    private static void conflicts(Minecraft mc){
        String[] names={"dispenser","stone_slab","stone_stairs","pumpkin","dropper","purple_shulker_box","structure_block"};
        var model=mc.getBlockRenderer().getBlockModel(VandorLabsBootstrap.PROGRAMMABLE_BLOCK.get().defaultBlockState());
        for(String name:names){int old=CustomMaterialIdentity.identifier("minecraft:"+name,0),fresh=CustomMaterialIdentity.flattenedBlockIdentifier("minecraft:"+name);var oldMaterial=NativeBlockMaterials.material(old);var newMaterial=NativeBlockMaterials.material(fresh);
            require(oldMaterial!=null&&newMaterial!=null&&oldMaterial.lower()!=newMaterial.lower(),"old/new conflicting identity overwritten "+name);
            require(world(model,old,Direction.NORTH).getSprite()==oldMaterial.lower()&&world(model,fresh,Direction.NORTH).getSprite()==newMaterial.lower(),"actual cube model ignored retained/default sample "+name);
            for(int choice:new int[]{old,fresh}){var stack=configured(choice);var before=stack.copy();var item=mc.getItemRenderer().getModel(stack,mc.level,mc.player,0);var quad=item.getQuads(null,Direction.NORTH,RandomSource.create(0)).getFirst();require(quad.getSprite()==NativeBlockMaterials.material(choice).lower()&&ItemStack.matches(before,stack),"configured item material/payload changed "+name);}
        }
        var door=NativeBlockMaterials.material(CustomMaterialIdentity.flattenedBlockIdentifier("minecraft:oak_door"));require(door!=null&&door.door()&&door.lower()!=door.upper(),"native default door halves missing");
        require(world(model,SampledMaterialRuntimeChecks.UNAVAILABLE,Direction.NORTH).getSprite()==world(model,0,Direction.NORTH).getSprite(),"unavailable saved ID did not use fallback");
        CubeReferenceChecks.run(mc);System.out.println("[vandorlabs][sampled-material] model PASS seven legacy/default conflicts, configured items unchanged, door halves, unavailable fallback and independent Forge oracle");
    }
    private static void explicit(Minecraft mc){
        var lamp=net.minecraft.world.level.block.Blocks.REDSTONE_LAMP;var wheat=net.minecraft.world.level.block.Blocks.WHEAT;
        net.minecraft.world.level.block.state.BlockState[] states={lamp.defaultBlockState(),lamp.defaultBlockState().setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.LIT,true),wheat.defaultBlockState(),wheat.defaultBlockState().setValue(net.minecraft.world.level.block.CropBlock.AGE,7)};
        String[] expected={"redstone_lamp","redstone_lamp_on","wheat_stage0","wheat_stage7"};var model=mc.getBlockRenderer().getBlockModel(VandorLabsBootstrap.PROGRAMMABLE_BLOCK.get().defaultBlockState());
        for(int i=0;i<states.length;i++){int choice=NativeMaterialRegistry.snapshot().stateSamples().get(states[i]);var material=NativeBlockMaterials.material(choice);require(material!=null&&material.lower().contents().name().toString().equals("minecraft:block/"+expected[i]),"explicit vanilla state sprite "+states[i]);require(world(model,choice,Direction.NORTH).getSprite()==material.lower(),"actual housing model selects explicit-state artwork");var stack=configured(choice);var before=stack.copy();var item=mc.getItemRenderer().getModel(stack,mc.level,mc.player,0);require(item.getQuads(null,Direction.NORTH,RandomSource.create(0)).getFirst().getSprite()==material.lower()&&ItemStack.matches(before,stack),"configured item explicit artwork/payload");}
        System.out.println("[vandorlabs][sampled-material] explicit-state PASS lamp off/on, crop ages, actual housing/item, saved choice unchanged");
    }
    private static void remember(Minecraft mc){
        oldWorld=mc.getBlockRenderer().getBlockModel(VandorLabsBootstrap.PROGRAMMABLE_BLOCK.get().defaultBlockState());oldWorldQuad=world(oldWorld,STONE,Direction.NORTH);oldWorldVertices=oldWorldQuad.getVertices().clone();
        oldItem=mc.getItemRenderer().getModel(configured(STONE),mc.level,mc.player,0);oldItemQuad=oldItem.getQuads(null,Direction.NORTH,RandomSource.create(0)).getFirst();oldItemVertices=oldItemQuad.getVertices().clone();
        require(oldWorldQuad.getSprite().contents().name().toString().equals("minecraft:block/stone"),"initial stone fixture texture");
    }
    private static void oldUnchanged(){
        require(Arrays.equals(oldWorldVertices,oldWorldQuad.getVertices())&&Arrays.equals(oldItemVertices,oldItemQuad.getVertices()),"reload mutated cached world/item vertices");
        Map<Integer,ModernFinishCatalog.Entry> restore=new HashMap<>();NativeBlockMaterials.snapshot().forEach((choice,material)->restore.put(choice,material.entry()));
        ModernFinishCatalog.publishCustom(Map.of());
        try{require(world(oldWorld,STONE,Direction.SOUTH).getSprite()==oldWorldQuad.getSprite(),"old world model consulted global availability after reload");require(oldItem.getQuads(null,Direction.SOUTH,RandomSource.create(0)).getFirst().getSprite()==oldItemQuad.getSprite(),"old item model consulted global availability after reload");}
        finally{ModernFinishCatalog.publishCustom(restore);}
    }
    @SubscribeEvent public static void tick(ClientTickEvent.Post event)throws Exception{
        if(!System.getProperty("vandorlabs.sampledMaterialPhase","").equals("client"))return;var mc=Minecraft.getInstance();
        if(mc.screen instanceof net.minecraft.client.gui.screens.AccessibilityOnboardingScreen screen){screen.onClose();return;}
        if(mc.player==null||mc.level==null||mc.gameMode==null)return;
        require(++ticks<6000,"timeout mode="+mc.gameMode.getPlayerMode()+" slot="+mc.player.getInventory().selected+" held="+mc.player.getMainHandItem());
        if(stage==0){
            if(ticks<100||!(mc.level.getBlockEntity(SampledMaterialRuntimeChecks.POS) instanceof ProgrammableBlockEntity)||mc.player.getInventory().getItem(1).getCount()!=7)return;
            require(mc.gameMode.getPlayerMode()==GameType.SURVIVAL&&mc.player.getInventory().selected==0&&mc.player.getMainHandItem().is(VandorLabsBootstrap.CONFIGURIZER.get()),"survival mode/slot/tool not synchronized");
            scene(mc);inventory=new ArrayList<>();for(int i=0;i<36;i++)inventory.add(mc.player.getInventory().getItem(i).copy());conflicts(mc);explicit(mc);open(mc);stage=1;wait=ticks;
        }else if(stage==1){
            if(!(mc.screen instanceof NativeCubeScreen screen))return;picker=screen;custom(screen);sample(screen,1,1);require(screen.inventoryPicker()!=null&&screen.editingState().mainTexture==0,"right click selected sample");sample(screen,5,0);require(screen.inventoryPicker()!=null&&screen.editingState().mainTexture==0,"non-block sample accepted");sample(screen,1,0);stage=2;wait=ticks;
        }else if(stage==2&&ticks>wait+15){
            require(current(mc)==SampledMaterialRuntimeChecks.NEW,"real survival inventory click did not roundtrip new default-state ID");checkInventory(mc);picker.onClose();stage=3;wait=ticks;
        }else if(stage==3&&ticks>wait+10){open(mc);stage=4;
        }else if(stage==4){
            if(!(mc.screen instanceof NativeCubeScreen screen))return;picker=screen;require(screen.editingState().mainTexture==SampledMaterialRuntimeChecks.NEW,"reopened menu lost saved sample");custom(screen);sample(screen,6,0);stage=5;wait=ticks;
        }else if(stage==5&&ticks>wait+15){
            require(current(mc)==SampledMaterialRuntimeChecks.LEGACY,"configured programmable item sample rehashed old saved ID");custom(picker);sample(picker,7,0);stage=50;wait=ticks;
        }else if(stage==50&&ticks>wait+15){
            require(current(mc)==SampledMaterialRuntimeChecks.LEGACY,"nested CUSTOM_DATA configured cube sample disagrees with renderer");checkInventory(mc);custom(picker);sample(picker,3,0);stage=6;wait=ticks;
        }else if(stage==6&&ticks>wait+15){
            require(current(mc)==CustomMaterialIdentity.flattenedBlockIdentifier("minecraft:oak_door"),"new native door sample did not roundtrip");checkInventory(mc);custom(picker);mc.getToasts().clear();mc.gui.getChat().clearMessages(true);capture("sampled-material-inventory.png");stage=7;wait=ticks;
        }else if(stage==7&&captured&&ticks>wait+10){
            var grid=picker.inventoryPicker().layout;picker.mouseClicked(grid.x+3,grid.y+3,1);require(picker.inventoryPicker()!=null,"right back header should only consume");picker.mouseClicked(grid.x+3,grid.y+3,0);require(picker.inventoryPicker()==null,"left back header failed");
            picker.init(mc,320,200);custom(picker);var compact=picker.inventoryPicker();require(compact.layout.maxScroll()>0,"compact grid not scrollable");picker.mouseScrolled(compact.layout.x+3,compact.layout.y+3,0,-1);require(compact.scroll()==1,"compact inventory wheel did not scroll one row");
            picker.init(mc,mc.getWindow().getGuiScaledWidth(),mc.getWindow().getGuiScaledHeight());custom(picker);sample(picker,1,0);stage=8;wait=ticks;
        }else if(stage==8&&ticks>wait+15){
            require(current(mc)==SampledMaterialRuntimeChecks.NEW,"restore new sample");click(picker,ProgrammableBlockDialog.FACE_OVERRIDES);click(picker,ProgrammableBlockDialog.FIRST_FACE+2);custom(picker);sample(picker,3,0);stage=60;wait=ticks;
        }else if(stage==60&&ticks>wait+15){
            var tile=(ProgrammableBlockEntity)mc.level.getBlockEntity(SampledMaterialRuntimeChecks.POS);int door=CustomMaterialIdentity.flattenedBlockIdentifier("minecraft:oak_door");require(tile.settings().mainTexture==SampledMaterialRuntimeChecks.NEW&&tile.settings().facesEnabled&&tile.settings().face(2)==door,"real inventory sample did not target face override");
            var model=mc.getBlockRenderer().getBlockModel(tile.getBlockState());require(model.getQuads(tile.getBlockState(),Direction.NORTH,RandomSource.create(0),tile.getModelData(),null).getFirst().getSprite()==NativeBlockMaterials.material(door).lower(),"actual face override model material");click(picker,ProgrammableBlockDialog.FACE_OVERRIDES);stage=61;wait=ticks;
        }else if(stage==61&&ticks>wait+15){
            var tile=(ProgrammableBlockEntity)mc.level.getBlockEntity(SampledMaterialRuntimeChecks.POS);require(!tile.settings().facesEnabled&&tile.settings().face(2)==CustomMaterialIdentity.flattenedBlockIdentifier("minecraft:oak_door"),"disabled custom face override was erased");checkInventory(mc);remember(mc);custom(picker);Files.writeString(override(mc),"{\"parent\":\"minecraft:block/cube_all\",\"textures\":{\"all\":\"minecraft:block/__missing_sample_fixture\"}}");reload=mc.reloadResourcePacks();stage=9;wait=ticks;
        }else if(stage==9&&reload.isDone()&&mc.getOverlay()==null&&ticks>wait+10){
            reload.join();require(mc.screen==picker&&picker.inventoryPicker()!=null&&picker.editingState().mainTexture==SampledMaterialRuntimeChecks.NEW,"same open inventory picker lost selected sample during reload screen="+mc.screen+" inventory="+(picker.inventoryPicker()!=null)+" choice="+picker.editingState().mainTexture);oldUnchanged();
            var material=NativeBlockMaterials.material(STONE);var model=mc.getBlockRenderer().getBlockModel(VandorLabsBootstrap.PROGRAMMABLE_BLOCK.get().defaultBlockState());require(material.lower().contents().name().toString().equals(ModernFinishCatalog.entry(0).texture())&&world(model,STONE,Direction.NORTH).getSprite()==material.lower(),"missing source did not rebake sampled fallback");
            Files.delete(override(mc));reload=mc.reloadResourcePacks();stage=10;wait=ticks;
        }else if(stage==10&&reload.isDone()&&mc.getOverlay()==null&&ticks>wait+10){
            reload.join();oldUnchanged();require(NativeBlockMaterials.material(STONE).lower().contents().name().toString().equals("minecraft:block/stone"),"source restore did not rebake sample");checkInventory(mc);scene(mc);explicit(mc);CubeReferenceChecks.run(mc);picker.onClose();capture("sampled-material-world.png");stage=11;wait=ticks;
        }else if(stage==11&&captured&&ticks>wait+10){
            require(current(mc)==SampledMaterialRuntimeChecks.NEW,"final saved sample");System.out.println("[vandorlabs][sampled-material] client PASS survival Configurizer real inventory clicks, nonconsumption, invalid/right input, configured legacy ID, nested item NBT, reopen, custom face/dormant override, compact wheel/back, both door halves, immutable old models/global availability, missing/restore reload and independent Forge oracle");mc.stop();stage=99;
        }
    }
    @SubscribeEvent public static void frame(RenderFrameEvent.Post event){if(screenshot==null)return;var mc=Minecraft.getInstance();String name=screenshot;screenshot=null;Screenshot.grab(mc.gameDirectory,name,mc.getMainRenderTarget(),message->{captured=true;System.out.println("[vandorlabs][sampled-material] screenshot "+name);});}
}
