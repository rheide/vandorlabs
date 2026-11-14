package com.vandorlabs.neoforge;

import com.google.gson.*;
import java.util.*;
import java.nio.file.*;
import java.util.zip.GZIPInputStream;
import net.minecraft.client.*;
import net.minecraft.client.gui.screens.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.*;
import net.minecraft.nbt.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.CustomData;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.*;

/** Real item registration, atlas bake, selector, projection and RenderItem submission. */
@EventBusSubscriber(modid="vandorlabs",value=Dist.CLIENT)
public final class CanopyItemRenderChecks {
    private static boolean active(){return Boolean.getBoolean("vandorlabs.canopyItemCheck");}
    @EventBusSubscriber(modid="vandorlabs",value=Dist.CLIENT,bus=EventBusSubscriber.Bus.MOD)
    public static final class Registration {
        @SubscribeEvent public static void register(net.neoforged.neoforge.registries.RegisterEvent event){if(active())event.register(Registries.ITEM,h->{for(String kind:NativeCanopyItemModel.KINDS){var id=ResourceLocation.fromNamespaceAndPath("vandorlabs",kind);if(VandorLabsBootstrap.ITEMS.getEntries().stream().noneMatch(entry->entry.getId().equals(id)))h.register(id,new Item(new Item.Properties()));}});}
    }
    private static final class Gallery extends Screen{Gallery(){super(net.minecraft.network.chat.Component.literal("Canopy icons"));}@Override public void render(net.minecraft.client.gui.GuiGraphics gui,int x,int y,float partial){}}
    private static final ItemDisplayContext[] CONTEXTS={ItemDisplayContext.GUI,ItemDisplayContext.FIRST_PERSON_RIGHT_HAND,ItemDisplayContext.FIRST_PERSON_LEFT_HAND,ItemDisplayContext.THIRD_PERSON_RIGHT_HAND,ItemDisplayContext.THIRD_PERSON_LEFT_HAND,ItemDisplayContext.GROUND,ItemDisplayContext.FIXED};
    private static int checks,generation,frames;private static boolean capture,done;private static ItemStack[] stacks;private static java.util.concurrent.CompletableFuture<Void> reload;
    private static void need(boolean ok,String why){checks++;if(!ok)throw new AssertionError("Canopy item generation="+generation+": "+why);}
    private static void prepare()throws Exception{
        var mc=Minecraft.getInstance();var icons=new ArrayList<ItemStack>();int cases=0;
        JsonArray rows;try(var in=new GZIPInputStream(Files.newInputStream(Path.of(System.getProperty("vandorlabs.canopyItemReference"))))){rows=JsonParser.parseString(new String(in.readAllBytes(),java.nio.charset.StandardCharsets.UTF_8)).getAsJsonArray();}
        for(var element:rows){var row=element.getAsJsonObject();if(row.get("aspect").getAsDouble()!=1)continue;String kind=row.get("id").getAsString(),input=row.get("input").getAsString();var stack=new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath("vandorlabs",kind)));need(!stack.isEmpty(),"registered item="+kind);var tag=input.isEmpty()?new CompoundTag():TagParser.parseTag(input);tag.putByteArray("Opaque",new byte[65536]);stack.set(DataComponents.CUSTOM_DATA,CustomData.of(tag));
            var model=mc.getItemRenderer().getModel(stack,null,null,0);var quads=model.getQuads(null,null,net.minecraft.util.RandomSource.create(0));need(!quads.isEmpty()&&quads.size()<row.get("quads").getAsInt()*3/4,"item geometry reduction="+kind+" profile="+row.get("profile")+" before="+row.get("quads")+" after="+quads.size());if(row.get("profile").getAsInt()==0)System.out.println("[vandorlabs][canopy-item-cost] "+kind+" before="+row.get("quads")+" after="+quads.size());for(var quad:quads){int[] words=quad.getVertices();for(int v=0;v<4;v++)for(int axis=0;axis<3;axis++){float point=Float.intBitsToFloat(words[v*8+axis]);need(point>=-1E-5&&point<=1.00001,"one-block item envelope="+kind);}}need(!NativeItemPadding.needsFit(model,model.getTransforms()),"GUI projection retains padding="+kind);for(var quad:quads)need(!quad.getSprite().contents().name().getPath().equals("missingno"),"authored sprite="+kind);
            var stone=mc.getItemRenderer().getModel(new ItemStack(Items.STONE),null,null,0);
            for(var context:CONTEXTS){if(context==ItemDisplayContext.GUI)continue;var actual=model.getTransforms().getTransform(context);var expected=stone.getTransforms().getTransform(context);need((!kind.equals("regular_canopy_glass")||context==ItemDisplayContext.GROUND||context==ItemDisplayContext.FIXED||actual.rotation.equals(expected.rotation))&&actual.translation.equals(expected.translation)&&actual.scale.equals(expected.scale),"ordinary block hand/ground/fixed transform="+kind+" context="+context);}
            if(row.get("profile").getAsInt()==0||row.get("profile").getAsInt()==3)icons.add(stack);cases++;
        }
        need(cases==88&&icons.size()==22,"actual item coverage="+cases);stacks=icons.toArray(ItemStack[]::new);for(int n=0;n<30000;n++)mc.getItemRenderer().getModel(stacks[n%stacks.length],null,null,0);var meter=(com.sun.management.ThreadMXBean)java.lang.management.ManagementFactory.getThreadMXBean();meter.setThreadAllocatedMemoryEnabled(true);long thread=Thread.currentThread().threadId(),before=meter.getThreadAllocatedBytes(thread);for(int n=0;n<10000;n++)mc.getItemRenderer().getModel(stacks[n%stacks.length],null,null,0);long bytes=meter.getThreadAllocatedBytes(thread)-before;need(bytes<=128,"warm selector bytes="+bytes);System.out.println("[vandorlabs][canopy-item-render] generation="+generation+" cases="+cases+" warm10000="+bytes);
    }
    @SubscribeEvent public static void draw(ScreenEvent.Render.Post event)throws Exception{
        if(!active()||done)return;var mc=Minecraft.getInstance();if(mc.getOverlay()!=null)return;if(event.getScreen() instanceof AccessibilityOnboardingScreen intro){intro.onClose();return;}if(event.getScreen() instanceof TitleScreen){mc.getWindow().setWindowed(1280,720);mc.options.guiScale().set(2);mc.resizeDisplay();mc.setScreen(new Gallery());return;}if(!(event.getScreen() instanceof Gallery))return;if(reload!=null){if(!reload.isDone())return;reload.join();reload=null;}if(stacks==null)prepare();var gui=event.getGuiGraphics();gui.fill(0,0,mc.getWindow().getGuiScaledWidth(),mc.getWindow().getGuiScaledHeight(),0xff202830);gui.flush();com.mojang.blaze3d.systems.RenderSystem.clear(org.lwjgl.opengl.GL11.GL_DEPTH_BUFFER_BIT,Minecraft.ON_OSX);if(frames>=4){com.mojang.blaze3d.platform.Lighting.setupFor3DItems();var buffers=mc.renderBuffers().bufferSource();for(int i=0;i<stacks.length;i++)for(int context=0;context<CONTEXTS.length;context++){var poses=gui.pose();poses.pushPose();try{int cell=i*CONTEXTS.length+context;poses.translate(26+cell%14*42,26+cell/14*30,200);poses.scale(28,-28,28);mc.getItemRenderer().render(stacks[i],CONTEXTS[context],false,poses,buffers,0xf000f0,net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY,mc.getItemRenderer().getModel(stacks[i],null,null,0));}finally{poses.popPose();}}buffers.endBatch();}gui.flush();frames++;capture=frames==4||frames==9;
    }
    @SubscribeEvent public static void shot(RenderFrameEvent.Post event)throws Exception{
        if(!active()||!capture||done)return;capture=false;var mc=Minecraft.getInstance();try(var image=Screenshot.takeScreenshot(mc.getMainRenderTarget())){var path=mc.gameDirectory.toPath().resolve("screenshots/canopy-item-"+generation+"-"+(frames==4?"disabled":"positive")+".png");Files.createDirectories(path.getParent());image.writeToFile(path);double scale=mc.getWindow().getGuiScale();for(int i=0;i<stacks.length;i++)for(int context=0;context<CONTEXTS.length;context++){int ink=0;int cell=i*CONTEXTS.length+context;for(int y=0;y<28*scale;y++)for(int x=0;x<28*scale;x++){int px=(int)((12+cell%14*42)*scale)+x,py=(int)((12+cell/14*30)*scale)+y;if((image.getPixelRGBA(px,py)&0xffffff)!=0x302820){ink++;need(x>=1&&y>=1&&x<28*scale-1&&y<28*scale-1,"actual padding icon="+i+" context="+CONTEXTS[context]+" pixel="+x+","+y);}}int minimum=CONTEXTS[context]==ItemDisplayContext.GROUND?3:10;need(frames==4?ink==0:ink>=minimum,"actual render/disabled icon="+i+" context="+CONTEXTS[context]+" pixels="+ink);}}
        if(frames==9){if(generation++==0){stacks=null;frames=0;reload=mc.reloadResourcePacks();}else{done=true;System.out.println("[vandorlabs][canopy-item-render] title PASS checks="+checks+" actual88/all-families/both-hands/ground/fixed/padding/reload/disabled/warm");mc.stop();}}
    }
}
