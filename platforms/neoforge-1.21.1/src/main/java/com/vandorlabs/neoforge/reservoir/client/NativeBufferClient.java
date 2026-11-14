package com.vandorlabs.neoforge.reservoir.client;
import com.vandorlabs.neoforge.reservoir.*;import com.mojang.blaze3d.vertex.PoseStack;import java.util.*;import net.minecraft.client.Minecraft;import net.minecraft.client.renderer.*;import net.minecraft.core.component.DataComponents;import net.minecraft.world.item.*;import net.minecraft.world.item.component.CustomData;import net.neoforged.api.distmarker.Dist;import net.neoforged.bus.api.SubscribeEvent;import net.neoforged.fml.common.EventBusSubscriber;import net.neoforged.neoforge.client.event.*;import net.neoforged.neoforge.client.extensions.common.*;
@EventBusSubscriber(modid="vandorlabs",bus=EventBusSubscriber.Bus.MOD,value=Dist.CLIENT)
public final class NativeBufferClient {
 @SubscribeEvent public static void renderers(EntityRenderersEvent.RegisterRenderers e){if(NativeBufferBindings.enabled()){e.registerBlockEntityRenderer(NativeBufferBindings.BATTERY_ENTITY,NativeBufferRenderer::new);e.registerBlockEntityRenderer(NativeBufferBindings.TANK_ENTITY,NativeBufferRenderer::new);}}
 @SubscribeEvent public static void screens(RegisterMenuScreensEvent e){if(NativeBufferBindings.enabled())e.register(NativeBufferBindings.MENU,NativeBufferScreen::new);}
 @SubscribeEvent public static void items(RegisterClientExtensionsEvent e){if(NativeBufferBindings.enabled())e.registerItem(new IClientItemExtensions(){private BlockEntityWithoutLevelRenderer renderer;@Override public BlockEntityWithoutLevelRenderer getCustomRenderer(){if(renderer==null)renderer=new ItemRenderer();return renderer;}},NativeBufferBindings.BATTERY.asItem(),NativeBufferBindings.TANK.asItem());}
 @SubscribeEvent public static void reload(ModelEvent.BakingCompleted e){clear();}
 private record Cached(CustomData component,boolean battery,NativeBufferEntity owner,long bytes){}
 private static final List<Cached> ITEMS=new ArrayList<>();private static long itemBytes;
 public static void clear(){ITEMS.clear();itemBytes=0;NativeBufferRenderer.clear();}
 private static NativeBufferEntity item(ItemStack stack){boolean battery=stack.getItem() instanceof NativeBufferItem item&&((NativeBufferBlock)item.getBlock()).battery;var component=stack.get(DataComponents.BLOCK_ENTITY_DATA);if(component==null)component=stack.getOrDefault(DataComponents.CUSTOM_DATA,CustomData.EMPTY);for(int i=0;i<ITEMS.size();i++){var cached=ITEMS.get(i);if(cached.component==component&&cached.battery==battery){if(i>0){ITEMS.remove(i);ITEMS.add(0,cached);}return cached.owner;}}var block=battery?NativeBufferBindings.BATTERY:NativeBufferBindings.TANK;var state=block.defaultBlockState().setValue(NativeBufferBlock.FACING,net.minecraft.core.Direction.UP);var owner=(NativeBufferEntity)block.newBlockEntity(net.minecraft.core.BlockPos.ZERO,state);var level=Minecraft.getInstance().level;var data=NativeBufferItem.snapshot(stack);if(level!=null)owner.loadWithComponents(data,level.registryAccess());long bytes=512L+3L*data.sizeInBytes();while((itemBytes+bytes>4*1024*1024L||ITEMS.size()>=64)&&!ITEMS.isEmpty())itemBytes-=ITEMS.remove(ITEMS.size()-1).bytes;if(bytes<=4*1024*1024L){ITEMS.add(0,new Cached(component,battery,owner,bytes));itemBytes+=bytes;}return owner;}
 private static final class ItemRenderer extends BlockEntityWithoutLevelRenderer {
  private final NativeBufferRenderer<NativeBufferEntity> renderer=new NativeBufferRenderer<>(null);
  ItemRenderer(){super(Minecraft.getInstance().getBlockEntityRenderDispatcher(),Minecraft.getInstance().getEntityModels());}
  @Override public void renderByItem(ItemStack stack,ItemDisplayContext context,PoseStack pose,MultiBufferSource buffers,int light,int overlay){renderer.render(item(stack),0,pose,buffers,light,overlay);}
 }
 @EventBusSubscriber(modid="vandorlabs",value=Dist.CLIENT) public static final class Lifecycle {
  @SubscribeEvent public static void unload(net.neoforged.neoforge.event.level.LevelEvent.Unload e){if(e.getLevel().isClientSide())clear();}
 }
 private NativeBufferClient(){}
}
