package com.vandorlabs.neoforge.copifier;

import java.util.*;
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
import net.neoforged.neoforge.client.model.data.ModelData;

/** Actual item override resolution, padded pixels, resource reload and disabled-draw control. */
@EventBusSubscriber(modid="vandorlabs",value=Dist.CLIENT)
public final class CopifierItemRenderChecks {
    private static boolean active(){return Boolean.getBoolean("vandorlabs.copifierItemCheck");}
    private static final ResourceLocation ID=ResourceLocation.parse("vandorlabs:duplifier");
    @EventBusSubscriber(modid="vandorlabs",value=Dist.CLIENT,bus=EventBusSubscriber.Bus.MOD)
    public static final class Registration {
        @SubscribeEvent public static void register(net.neoforged.neoforge.registries.RegisterEvent event){
            if(!active()||com.vandorlabs.neoforge.VandorLabsBootstrap.ITEMS.getEntries().stream().anyMatch(entry->entry.getId().equals(ID)))return;
            event.register(Registries.ITEM,h->h.register(ID,new NativeCopifierItem(new Item.Properties(),()->null)));
        }
    }
    private static final class Gallery extends Screen {
        Gallery(){super(net.minecraft.network.chat.Component.literal("Copied tool icons"));}
        @Override public void render(net.minecraft.client.gui.GuiGraphics gui,int x,int y,float partial){}
    }
    private static ItemStack[] stacks;private static int frames,generation,checks;private static boolean capture,done;private static java.util.concurrent.CompletableFuture<Void> reload;
    private static void need(boolean ok,String why){checks++;if(!ok)throw new AssertionError("Copier icons generation="+generation+": "+why);}
    private static void prepare(){
        var mc=Minecraft.getInstance();var item=BuiltInRegistries.ITEM.get(ID);need(item instanceof NativeCopifierItem,"actual tool factory");stacks=new ItemStack[16];
        for(int i=0;i<stacks.length;i++){
            var root=new CompoundTag();root.putByteArray("OpaqueOwner",new byte[65536]);root.putBoolean("DuplifierConnected",i%4==2||i%4==3);
            if(i%4==1||i%4==2){var choices=new CompoundTag();choices.putInt("wall_texture",7);root.put("DuplifierSettings",choices);}else if(i%4==3)root.putString("DuplifierSettings","wrong type");
            var stack=new ItemStack(item);stack.set(DataComponents.CUSTOM_DATA,CustomData.of(root));stacks[i]=stack;int mode=i%4==3?0:i%4;need(NativeCopifierClient.mode(stack)==mode,"component-owned mode="+i);
            var model=mc.getItemRenderer().getModel(stack,null,null,0);String expected="vandorlabs:item/duplifier_"+new String[]{"off","on","multi"}[mode];need(model.getParticleIcon(ModelData.EMPTY).contents().name().toString().equals(expected),"actual baked override="+i+" expected="+expected+" actual="+model.getParticleIcon(ModelData.EMPTY).contents().name());
        }
        var beforeData=stacks[2].get(DataComponents.CUSTOM_DATA).copyTag();for(int i=0;i<20000;i++)NativeCopifierClient.mode(stacks[i%16]);
        var meter=(com.sun.management.ThreadMXBean)java.lang.management.ManagementFactory.getThreadMXBean();meter.setThreadAllocatedMemoryEnabled(true);long thread=Thread.currentThread().threadId(),before=meter.getThreadAllocatedBytes(thread);for(int i=0;i<10000;i++)NativeCopifierClient.mode(stacks[i%16]);long bytes=meter.getThreadAllocatedBytes(thread)-before;need(bytes<=128,"read-only warm predicate allocated="+bytes);need(beforeData.equals(stacks[2].get(DataComponents.CUSTOM_DATA).copyTag()),"predicate preserves complete opaque owner");System.out.println("[vandorlabs][copifier-item-render] generation="+generation+" modes=3 icons=16 warm10000="+bytes);
    }
    @SubscribeEvent public static void draw(ScreenEvent.Render.Post event){
        if(!active()||done)return;var mc=Minecraft.getInstance();if(mc.getOverlay()!=null)return;if(event.getScreen() instanceof AccessibilityOnboardingScreen intro){intro.onClose();return;}if(event.getScreen() instanceof TitleScreen){mc.getWindow().setWindowed(1280,720);mc.options.guiScale().set(2);mc.resizeDisplay();mc.setScreen(new Gallery());return;}if(!(event.getScreen() instanceof Gallery))return;
        if(reload!=null){if(!reload.isDone())return;reload.join();reload=null;}if(stacks==null)prepare();var gui=event.getGuiGraphics();gui.fill(0,0,mc.getWindow().getGuiScaledWidth(),mc.getWindow().getGuiScaledHeight(),0xff202830);gui.flush();com.mojang.blaze3d.systems.RenderSystem.clear(org.lwjgl.opengl.GL11.GL_DEPTH_BUFFER_BIT,Minecraft.ON_OSX);
        if(frames>=4)for(int i=0;i<16;i++)gui.renderItem(stacks[i],40+(i%4)*32,40+(i/4)*32);gui.flush();frames++;capture=frames==4||frames==9;
    }
    @SubscribeEvent public static void shot(RenderFrameEvent.Post event)throws Exception{
        if(!active()||!capture||done)return;capture=false;var mc=Minecraft.getInstance();try(var image=Screenshot.takeScreenshot(mc.getMainRenderTarget())){
            var path=mc.gameDirectory.toPath().resolve("screenshots/copifier-item-"+generation+"-"+(frames==4?"disabled":"positive")+".png");java.nio.file.Files.createDirectories(path.getParent());image.writeToFile(path);double scale=mc.getWindow().getGuiScale();
            for(int i=0;i<16;i++){int ink=0;for(int y=0;y<16*scale;y++)for(int x=0;x<16*scale;x++){int px=(int)((40+i%4*32)*scale)+x,py=(int)((40+i/4*32)*scale)+y;if((image.getPixelRGBA(px,py)&0xffffff)!=0x302820){ink++;need(x>=1&&y>=1&&x<16*scale-1&&y<16*scale-1,"visible slot padding="+i+" pixel="+x+","+y);}}need(frames==4?ink==0:ink>100,"actual icon/disabled="+i+" ink="+ink);}
        }
        if(frames==9){if(generation++==0){frames=0;stacks=null;reload=mc.reloadResourcePacks();}else{done=true;System.out.println("[vandorlabs][copifier-item-render] title PASS checks="+checks+" mode overrides/padding/reload/disabled/opaque-readonly");mc.stop();}}
    }
}
