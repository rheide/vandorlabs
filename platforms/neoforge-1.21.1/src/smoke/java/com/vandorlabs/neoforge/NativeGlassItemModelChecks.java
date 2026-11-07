package com.vandorlabs.neoforge;

import com.vandorlabs.neoforge.glass.*;
import java.util.*;
import net.minecraft.client.*;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.resources.model.*;
import net.minecraft.core.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.block.*;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.*;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.*;
import net.neoforged.neoforge.client.model.data.ModelData;

/** Actual ItemRenderer for all304 Source templates, including active tiny pack geometry and reload. */
@EventBusSubscriber(modid="vandorlabs",value=Dist.CLIENT)
public final class NativeGlassItemModelChecks {
 private static final ResourceLocation ID=ResourceLocation.parse("vandorlabs:flat_glass_icon_fixture");private static NativeGlassBlock block;private static NativeGlassItem item;
 private static NativeGlassItemModel model;private static Map<ModelResourceLocation,BakedModel> baked;private static ItemStack[] stacks;private static int stage,ticks,frames,generation,checks;private static volatile Object sink;private static boolean capture,done;private static java.util.concurrent.CompletableFuture<Void> reloading;
 private static boolean enabled(){return "true".equals(System.getenv("VANDOR_FLAT_GLASS_ITEM_CHECK"));}
 private static void need(boolean yes,String text){checks++;if(!yes)throw new IllegalStateException("Glass item generation="+generation+" "+text);}
 @EventBusSubscriber(modid="vandorlabs",bus=EventBusSubscriber.Bus.MOD,value=Dist.CLIENT)public static final class Binding {
  @SubscribeEvent public static void registry(net.neoforged.neoforge.registries.RegisterEvent e){if(!enabled())return;e.register(Registries.BLOCK,h->{block=new NativeGlassBlock(Block.Properties.of().strength(.5F,1.2F),()->null);h.register(ID,block);});e.register(Registries.ITEM,h->{item=new NativeGlassItem(block,new Item.Properties());h.register(ID,item);});}
  @SubscribeEvent(priority=EventPriority.LOWEST)public static void models(ModelEvent.ModifyBakingResult e){if(!enabled())return;baked=e.getModels();String plain=NativeFlatGlassAssets.item(1,0,1,true);var source=baked.get(ModelResourceLocation.standalone(ResourceLocation.parse(plain)));need(source!=null,"actual Source default authored model");model=new NativeGlassItemModel(source,id->baked.get(ModelResourceLocation.standalone(ResourceLocation.parse(id))),Minecraft.getInstance().getResourceManager());baked.put(new ModelResourceLocation(ID,"inventory"),model);}
 }
 private static ItemStack stack(CompoundTag configuration){var result=new ItemStack(item);var tag=new CompoundTag();tag.put(NativeGlassItem.CONFIGURATION,configuration);result.set(DataComponents.CUSTOM_DATA,CustomData.of(tag));return result;}
 private static NativeGlassItemModel.Selected selected(ItemStack stack){var selected=Minecraft.getInstance().getItemRenderer().getModel(stack,null,null,0);need(selected instanceof NativeGlassItemModel.Selected,"actual ItemRenderer selects production model");return (NativeGlassItemModel.Selected)selected;}
 private static void geometry(String id,NativeGlassItemModel.Selected selected){var source=baked.get(ModelResourceLocation.standalone(ResourceLocation.parse(id)));List<BakedQuad> expected;
  if(NativeDoorArtwork.canonicalGeometry(id,Minecraft.getInstance().getResourceManager()))expected=NativeDoorArtwork.quads(id,source);else{var all=new ArrayList<BakedQuad>();for(Direction side:new Direction[]{null,Direction.UP,Direction.DOWN,Direction.NORTH,Direction.SOUTH,Direction.EAST,Direction.WEST})all.addAll(source.getQuads(null,side,RandomSource.create(0),ModelData.EMPTY,null));expected=NativeDoorArtwork.content(all);}
  var actual=selected.getQuads(null,null,RandomSource.create(0),ModelData.EMPTY,null);need(actual.size()==expected.size(),"active Source face count "+id);for(int i=0;i<actual.size();i++)need(Arrays.equals(actual.get(i).getVertices(),expected.get(i).getVertices())&&actual.get(i).getDirection()==expected.get(i).getDirection()&&actual.get(i).getSprite()==expected.get(i).getSprite(),"Source/active-pack vertex payload "+id+" face="+i);need(!NativeItemPadding.needsFit(selected,selected.getTransforms()),"projected GUI slot padding "+id);
 }
 private static void prepare(){var values=new ArrayList<ItemStack>();for(int size=0;size<2;size++)for(int shade=0;shade<com.vandorlabs.tiles.GlassTextures.IDS.length;shade++)for(int tint=0;tint<4;tint++)for(boolean framed:new boolean[]{false,true}){var tag=new CompoundTag();tag.putInt("Size",size);tag.putInt("Shade",shade);tag.putInt("Tint",tint);tag.putBoolean("Framed",framed);tag.putInt("Material",64);tag.putInt("FrameInterior",0);var stack=stack(tag);String id=NativeFlatGlassAssets.item(size,shade,tint,framed);var selected=selected(stack);need(selected.modelId().equals(id),"all304 exact Source selectors");geometry(id,selected);need(selected.applyTransform(ItemDisplayContext.GUI,new com.mojang.blaze3d.vertex.PoseStack(),false).getRenderTypes(stack,false).equals(List.of(net.minecraft.client.renderer.RenderType.itemEntityTranslucentCull(net.minecraft.client.renderer.texture.TextureAtlas.LOCATION_BLOCKS))),"Source GUI alpha blend/cutoff0.1 item pass");values.add(stack);}stacks=values.toArray(ItemStack[]::new);
  need(stacks.length==304,"Source304 item templates");need(selected(new ItemStack(item)).modelId().equals(NativeFlatGlassAssets.item(1,0,1,true)),"plain item defaults medium");need(selected(stack(new CompoundTag())).modelId().equals(NativeFlatGlassAssets.item(0,0,1,true)),"present empty outer settings default Size0");String pack=NativeFlatGlassAssets.item(0,0,0,false);need(!NativeDoorArtwork.canonicalGeometry(pack,Minecraft.getInstance().getResourceManager()),"active1e-5 pack geometry remains distinguishable");
  var meter=(com.sun.management.ThreadMXBean)java.lang.management.ManagementFactory.getThreadMXBean();meter.setThreadAllocatedMemoryEnabled(true);long thread=Thread.currentThread().threadId();var choice=stacks[100];for(int i=0;i<4;i++)meter.getThreadAllocatedBytes(thread);for(int i=0;i<2000;i++)sink=NativeGlassItem.sourceModel(choice);long uncachedBefore=meter.getThreadAllocatedBytes(thread);for(int i=0;i<1000;i++)sink=NativeGlassItem.sourceModel(choice);long uncached=meter.getThreadAllocatedBytes(thread)-uncachedBefore;need(uncached>0,"uncached Source projection baseline captures defensive NBT copies");for(int i=0;i<30000;i++)sink=model.getOverrides().resolve(model,choice,null,null,0);long before=meter.getThreadAllocatedBytes(thread);for(int i=0;i<10000;i++)sink=model.getOverrides().resolve(model,choice,null,null,0);long allocated=meter.getThreadAllocatedBytes(thread)-before;need(allocated==0,"warmed10k Source item selections allocate0, actual="+allocated);need(model.retainedBytes()<=8*1024*1024,"combined8MiB item mesh budget");System.out.println("[vandorlabs][glass-item-model] generation="+generation+" templates304 retainedBytes="+model.retainedBytes()+" uncachedProjections1000Bytes="+uncached+" selections10000 allocated="+allocated);
 }
 @SubscribeEvent public static void tick(ClientTickEvent.Post e){if(!enabled()||done)return;var mc=Minecraft.getInstance();if(mc.screen instanceof net.minecraft.client.gui.screens.AccessibilityOnboardingScreen s){s.onClose();return;}need(++ticks<2200,"title timeout");if(reloading!=null){if(!reloading.isDone())return;reloading.join();reloading=null;}if(mc.getOverlay()!=null||!(mc.screen instanceof TitleScreen))return;if(stage==0){mc.getWindow().setWindowed(1280,720);mc.options.guiScale().set(1);mc.resizeDisplay();mc.getResourcePackRepository().reload();var packs=new ArrayList<>(mc.getResourcePackRepository().getSelectedIds());need(mc.getResourcePackRepository().getAvailableIds().contains("file/glass-item-tiny"),"tiny authored override pack discovered");packs.add("file/glass-item-tiny");mc.getResourcePackRepository().setSelected(packs);reloading=mc.reloadResourcePacks();stage=1;}else if(stage==1&&mc.getWindow().getGuiScaledWidth()>=1200){prepare();frames=0;stage=2;}}
 @SubscribeEvent public static void draw(ScreenEvent.Render.Post e){if(!enabled()||stage!=2||!(e.getScreen() instanceof TitleScreen)||Minecraft.getInstance().getOverlay()!=null)return;var mc=Minecraft.getInstance();var gui=e.getGuiGraphics();gui.flush();com.mojang.blaze3d.systems.RenderSystem.clear(org.lwjgl.opengl.GL11.GL_DEPTH_BUFFER_BIT,Minecraft.ON_OSX);gui.pose().pushPose();gui.pose().translate(0,0,400);try{gui.fill(0,0,mc.getWindow().getGuiScaledWidth(),mc.getWindow().getGuiScaledHeight(),0xff000000);gui.drawString(mc.font,Component.literal("Ordinary Glass: all 304 Source inventory variants"),24,15,0xffffffff);for(int i=0;i<stacks.length;i++)gui.renderItem(stacks[i],24+i%16*36,40+i/16*32);gui.flush();if(++frames==12)capture=true;}finally{gui.pose().popPose();}}
 @SubscribeEvent public static void frame(RenderFrameEvent.Post e)throws Exception{if(!enabled()||!capture)return;capture=false;var mc=Minecraft.getInstance();try(var image=Screenshot.takeScreenshot(mc.getMainRenderTarget())){var file=mc.gameDirectory.toPath().resolve("screenshots/glass-items-"+generation+".png");java.nio.file.Files.createDirectories(file.getParent());image.writeToFile(file);}if(generation++==0){stage=1;reloading=mc.reloadResourcePacks();return;}done=true;System.out.println("[vandorlabs][glass-item-model] title PASS checks="+checks+" actualItemRenderer/all304/SourcePartialDefaults/activeTinyPack/geometryUV/padding/reload/zeroHotAllocation");mc.stop();}
 private NativeGlassItemModelChecks(){}
}
