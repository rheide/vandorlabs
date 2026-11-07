package com.vandorlabs.neoforge;

import com.google.gson.*;
import com.vandorlabs.neoforge.credentials.*;
import com.vandorlabs.persistence.ControlMountSettings;
import java.nio.file.*;
import java.util.*;
import net.minecraft.client.*;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.*;
import net.neoforged.neoforge.client.model.data.ModelData;

/** Exact released reader world/item output and actual128GUI icons; no gameplay activation. */
@EventBusSubscriber(modid="vandorlabs",value=Dist.CLIENT)
public final class StandaloneReaderModelChecks {
    private static int ticks,stage,frames;private static boolean captured;private static String capture;
    private static java.util.concurrent.CompletableFuture<Void> reload;
    private static final List<ItemStack> icons=new ArrayList<>();
    private static final Direction[] FACES={null,Direction.UP,Direction.DOWN,Direction.NORTH,Direction.SOUTH,Direction.EAST,Direction.WEST};
    private static boolean enabled(){return "true".equals(System.getenv("VANDOR_STANDALONE_READER_MODEL_CHECK"));}
    @EventBusSubscriber(modid="vandorlabs",bus=EventBusSubscriber.Bus.MOD,value=Dist.CLIENT)
    public static final class Models {@SubscribeEvent(priority=net.neoforged.bus.api.EventPriority.LOWEST)public static void bake(ModelEvent.ModifyBakingResult event){if(enabled())NativeCredentialReaderModels.prepare(event.getModels(),List.of(StandaloneReaderGeometryChecks.fixture(false),StandaloneReaderGeometryChecks.fixture(true)));}}
    private static void need(boolean value,String why){if(!value)throw new AssertionError("Reader models: "+why);}
    private static List<BakedQuad> quads(BakedModel model,BlockState state,ModelData data){var result=new ArrayList<BakedQuad>();for(Direction face:FACES)result.addAll(model.getQuads(state,face,RandomSource.create(0),data,null));return result;}
    private static JsonArray mapped(JsonArray rows){for(var entry:rows){var row=entry.getAsJsonObject();String name=row.get("sprite").getAsString();if(name.equals("minecraft:blocks/concrete_black"))name="minecraft:block/black_concrete";else if(name.startsWith("vandorlabs:blocks/"))name=name.replace("vandorlabs:blocks/","vandorlabs:reader-artwork/blocks/");row.addProperty("sprite",name);}return rows;}
    private static final RandomSource WARM_RANDOM=RandomSource.create(0);
    private static volatile int warmSink;
    private static void warm(Minecraft mc,int count){int result=0;for(int i=0;i<count;i++){var model=mc.getItemRenderer().getModel(icons.get(i%128),null,null,0);result+=model.getQuads(null,null,WARM_RANDOM,ModelData.EMPTY,null).size();}warmSink=result;}
    private static void allocation(Minecraft mc){var bean=(com.sun.management.ThreadMXBean)java.lang.management.ManagementFactory.getThreadMXBean();bean.setThreadAllocatedMemoryEnabled(true);long thread=Thread.currentThread().threadId();for(int i=0;i<8;i++){warm(mc,10000);bean.getThreadAllocatedBytes(thread);}for(int batch=0;batch<3;batch++){long before=bean.getThreadAllocatedBytes(thread);warm(mc,10000);long bytes=bean.getThreadAllocatedBytes(thread)-before;need(bytes==0,"warm item resolution/quads batch="+batch+" bytes="+bytes);}System.out.println("[vandorlabs][standalone-reader-model] warm PASS 3x10000 resolutions/quads 0B generation="+stage);}
    private static void run(Minecraft mc)throws Exception {
        JsonObject reference;try(var input=Files.newBufferedReader(Path.of(System.getenv("VANDOR_STANDALONE_READER_REFERENCE")))){reference=JsonParser.parseReader(input).getAsJsonObject();}need(reference.get("golden").getAsString().equals("b0242861865277cf4fd936f982e8cddab924deb7b0d5440e5851df4d495d4948"),"frozen actual Source renderer");int worlds=0,items=0;icons.clear();
        for(var entry:reference.getAsJsonObject("world").entrySet()){
            String[] key=entry.getKey().split("/");var block=StandaloneReaderGeometryChecks.fixture(key[0].equals("data_chip_slot"));int rotation=Integer.parseInt(key[2]),mount=Integer.parseInt(key[4]);var state=block.defaultBlockState().setValue(NativeMountedSwitchBlock.FACING,Direction.byName(key[1])).setValue(NativeMountedSwitchBlock.ON,Boolean.parseBoolean(key[3])).setValue(NativeMountedSwitchBlock.ROTATION,rotation);var settings=ControlMountSettings.of(mount%4,mount/4%4,mount/16);var data=ModelData.builder().with(NativeBinaryControlBlockEntity.VISUAL,new NativeBinaryControlBlockEntity.Visual(settings,rotation)).build();var model=mc.getBlockRenderer().getBlockModel(state);need(model instanceof NativeMountedControlModel,"inactive mounted reader wrapper");BinaryControlModelChecks.compare(mapped(entry.getValue().getAsJsonArray()),quads(model,state,data),entry.getKey());worlds++;
        }
        for(var entry:reference.getAsJsonObject("items").entrySet()){
            String[] key=entry.getKey().split("/");var block=StandaloneReaderGeometryChecks.fixture(key[0].equals("data_chip_slot"));int mount=Integer.parseInt(key[1]);var stack=new ItemStack(block);var settings=new CompoundTag();ControlMountSettings.write(new ModernHousingTags(settings),mount%4,mount/4%4,mount/16);var custom=new CompoundTag();custom.put("RedstoneChannelSettings",settings);stack.set(DataComponents.CUSTOM_DATA,CustomData.of(custom));var before=stack.copy();var model=mc.getItemRenderer().getModel(stack,null,null,0);need(ItemStack.matches(before,stack),"model resolution preserves configured stack");var row=entry.getValue().getAsJsonObject();BinaryControlModelChecks.compare(mapped(row.getAsJsonArray("quads")),quads(model,null,ModelData.EMPTY),entry.getKey());var gui=model.getTransforms().gui;float[] actual={gui.rotation.x(),gui.rotation.y(),gui.rotation.z(),gui.translation.x(),gui.translation.y(),gui.translation.z(),gui.scale.x(),gui.scale.y(),gui.scale.z()};for(int i=0;i<actual.length;i++)need(Math.abs(actual[i]-row.getAsJsonArray("gui").get(i).getAsFloat())<.00003,"source GUI transform "+entry.getKey()+" axis="+i);icons.add(stack);items++;
        }
        need(worlds==3072&&items==128,"complete reader Source corpus");allocation(mc);System.out.println("[vandorlabs][standalone-reader-model] matrix PASS world="+worlds+" items="+items+" generation="+stage);
    }
    private static void capture(String name){capture=name;captured=false;frames=0;}
    @SubscribeEvent public static void tick(ClientTickEvent.Post event)throws Exception {if(!enabled()||stage==9)return;var mc=Minecraft.getInstance();if(mc.screen instanceof net.minecraft.client.gui.screens.AccessibilityOnboardingScreen intro){intro.onClose();return;}need(++ticks<6000,"title timeout");if(ticks<80||mc.getOverlay()!=null||!(mc.screen instanceof TitleScreen))return;need(mc.level==null,"title-only model proof");if(stage==0){mc.getWindow().setWindowed(1280,720);mc.options.guiScale().set(2);mc.resizeDisplay();run(mc);capture("standalone-reader-icons.png");stage=1;}else if(stage==1&&captured){reload=mc.reloadResourcePacks();stage=2;}else if(stage==2&&reload.isDone()){reload.join();run(mc);capture("standalone-reader-icons-reloaded.png");stage=3;}else if(stage==3&&captured){System.out.println("[vandorlabs][standalone-reader-model] title PASS both Source generations; terrain/item meshes only, actual placed world separate");stage=9;mc.stop();}}
    @SubscribeEvent public static void draw(ScreenEvent.Render.Post event){if(!enabled()||icons.size()!=128||!(event.getScreen() instanceof TitleScreen)||Minecraft.getInstance().getOverlay()!=null)return;var graphics=event.getGuiGraphics();graphics.flush();graphics.pose().pushPose();try{graphics.pose().translate(0,0,300);graphics.fill(0,0,640,360,0xFF101012);for(int i=0;i<icons.size();i++)graphics.renderItem(icons.get(i),24+i%16*32,24+i/16*32);graphics.flush();}finally{graphics.pose().popPose();}}
    @SubscribeEvent public static void frame(RenderFrameEvent.Post event){if(!enabled()||capture==null||++frames<6)return;String name=capture;capture=null;var mc=Minecraft.getInstance();Screenshot.grab(mc.gameDirectory,name,mc.getMainRenderTarget(),ignored->{captured=true;});}
}
