package com.vandorlabs.neoforge;

import net.minecraft.client.*;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.world.item.*;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.*;

/** Title-only actual item-render path; does not activate the standalone Screen registry. */
@EventBusSubscriber(modid="vandorlabs",value=Dist.CLIENT)
public final class ScreenItemRenderChecks {
    private static final class Gallery extends net.minecraft.client.gui.screens.Screen {
        Gallery(){super(net.minecraft.network.chat.Component.literal("Screen inventory fixture"));}
        @Override public void render(net.minecraft.client.gui.GuiGraphics gui,int mouseX,int mouseY,float partialTick){}
    }
    private static BakedModel[] models;private static int frames,generation;private static boolean capture,done;
    @SubscribeEvent public static void draw(ScreenEvent.Render.Post event){
        if(done||!"true".equals(System.getenv("VANDOR_SCREEN_ITEM_RENDER_CHECK")))return;
        var mc=Minecraft.getInstance();if(mc.getOverlay()!=null)return;if(event.getScreen() instanceof net.minecraft.client.gui.screens.TitleScreen){mc.setScreen(new Gallery());return;}if(!(event.getScreen() instanceof Gallery))return;if(models==null)models=NativeScreenItemModelChecks.run();var gui=event.getGuiGraphics();gui.fill(0,0,mc.getWindow().getGuiScaledWidth(),mc.getWindow().getGuiScaledHeight(),0xff202830);gui.flush();var buffers=mc.renderBuffers().bufferSource();
        for(int scene=0;scene<8;scene++){var pose=gui.pose();pose.pushPose();try{pose.translate(80+(scene%4)*155,85+(scene/4)*165,250);pose.scale(100,-100,100);mc.getItemRenderer().render(new ItemStack(Items.STONE),ItemDisplayContext.GUI,false,pose,buffers,0xf000f0,net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY,models[scene]);}finally{pose.popPose();}buffers.endBatch();gui.flush();}
        capture=++frames>=8;
    }
    @SubscribeEvent public static void frame(RenderFrameEvent.Post event)throws Exception{
        if(done||!capture)return;done=true;var mc=Minecraft.getInstance();try(var image=Screenshot.takeScreenshot(mc.getMainRenderTarget())){double scale=mc.getWindow().getGuiScale();for(int scene=0;scene<8;scene++){int ink=0,x0=(int)((30+(scene%4)*155)*scale),y0=(int)((35+(scene/4)*165)*scale),size=(int)(100*scale);for(int y=0;y<size;y++)for(int x=0;x<size;x++)if((image.getPixelRGBA(x+x0,y+y0)&0xffffff)!=0x302820)ink++;if(ink<100)throw new AssertionError("Empty Screen item "+scene+" pixels="+ink);}var path=mc.gameDirectory.toPath().resolve("screenshots/screen-items-"+generation+".png");java.nio.file.Files.createDirectories(path.getParent());image.writeToFile(path);}
        if(generation++==0){models=null;frames=0;capture=false;done=false;mc.reloadResourcePacks();return;}System.out.println("[vandorlabs][screen-item-render] title PASS eight item variants visible/padded/atlas reload; private backend only");mc.stop();
    }
}
