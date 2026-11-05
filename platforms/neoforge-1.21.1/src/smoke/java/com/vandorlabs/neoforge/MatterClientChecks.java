package com.vandorlabs.neoforge;

import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;

/** Explicit native client smoke; dormant during ordinary gameplay. */
@EventBusSubscriber(modid="vandorlabs",value=Dist.CLIENT)
public final class MatterClientChecks {
    private static int ticks,stage,stageTick;
    private static volatile boolean captured;
    private static boolean capture;
    private static final BlockPos PLACED = new BlockPos(0,80,-1);
    private static void require(boolean value,String message) {
        if(!value) throw new IllegalStateException("Native matter client: "+message);
    }
    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        if(!System.getProperty("vandorlabs.matterPhase", "").equals("client"))return;
        Minecraft mc=Minecraft.getInstance();
        if(mc.screen instanceof net.minecraft.client.gui.screens.AccessibilityOnboardingScreen onboarding) { onboarding.onClose();return; }
        if(mc.player==null || mc.level==null || mc.gameMode==null)return;
        require(++ticks<1800,"timeout stage="+stage+" mode="+mc.gameMode.getPlayerMode()+" held="+mc.player.getMainHandItem());
        if(stage==0) {
            if(ticks<80 || !mc.gameMode.getPlayerMode().isCreative() || !mc.player.getMainHandItem().is(VandorLabsBootstrap.MATTER_BLOCK_ITEM.get()) || !mc.level.getBlockState(MatterRuntimeChecks.BLOCK_POS).is(VandorLabsBootstrap.MATTER_BLOCK.get()))return;
            var model=mc.getBlockRenderer().getBlockModel(VandorLabsBootstrap.MATTER_BLOCK.get().defaultBlockState());
            require(model!=mc.getModelManager().getMissingModel(),"missing block model");
            for(Direction face:Direction.values()) {
                var quads=model.getQuads(VandorLabsBootstrap.MATTER_BLOCK.get().defaultBlockState(),face,RandomSource.create(0));
                require(quads.size()==1,"cube face count "+face);
                require(quads.getFirst().getSprite().contents().name().toString().equals("vandorlabs:block/matter/matter"),"wrong block artwork");
            }
            for(var item:java.util.List.of(VandorLabsBootstrap.MATTER_BLOCK_ITEM.get(),VandorLabsBootstrap.MATTER_INGOT.get(),VandorLabsBootstrap.ALLOY_INGOT.get())) {
                var itemModel=mc.getItemRenderer().getModel(new ItemStack(item),mc.level,mc.player,0);
                require(itemModel!=mc.getModelManager().getMissingModel(),"missing item model "+item);
            }
            mc.getToasts().clear();mc.gui.getChat().clearMessages(true);mc.options.hideGui=false;mc.player.setYRot(0);mc.player.setXRot(8);
            capture=true;stage=1;stageTick=ticks;
        } else if(stage==1 && captured && ticks>stageTick+10) {
            require(mc.player.getInventory().selected==0,"selected hotbar slot");
            var result=mc.gameMode.useItemOn(mc.player,InteractionHand.MAIN_HAND,
                new BlockHitResult(new Vec3(0.5,80,-0.5),Direction.UP,PLACED.below(),false));
            require(result.consumesAction(),"real block placement refused");stage=2;stageTick=ticks;
        } else if(stage==2 && ticks>stageTick+20) {
            require(mc.level.getBlockState(PLACED).is(VandorLabsBootstrap.MATTER_BLOCK.get()),"placed world block missing");
            mc.gameMode.startDestroyBlock(PLACED,Direction.NORTH);stage=3;stageTick=ticks;
        } else if(stage==3 && ticks>stageTick+20) {
            require(mc.level.getBlockState(PLACED).isAir(),"creative mining failed");
            System.out.println("[vandorlabs][matter] client PASS native models, six artwork faces, three item models, hotbar screenshot, real placement/mining packets");
            mc.stop();stage=4;
        }
    }
    @SubscribeEvent public static void icons(net.neoforged.neoforge.client.event.RenderGuiEvent.Post event) {
        if(!System.getProperty("vandorlabs.matterPhase", "").equals("client"))return;
        var graphics=event.getGuiGraphics();
        var items=java.util.List.of(VandorLabsBootstrap.MATTER_BLOCK_ITEM.get(),VandorLabsBootstrap.MATTER_INGOT.get(),VandorLabsBootstrap.ALLOY_INGOT.get());
        for(int index=0;index<items.size();index++) {
            int x=20+24*index;
            graphics.fill(x,20,x+18,38,0xFF21384B);
        }
        graphics.flush();
        for(int index=0;index<items.size();index++) graphics.renderItem(new ItemStack(items.get(index)),21+24*index,21);
        graphics.flush();
    }
    @SubscribeEvent public static void frame(RenderFrameEvent.Post event) {
        if(!capture)return;
        capture=false;Minecraft mc=Minecraft.getInstance();
        Screenshot.grab(mc.gameDirectory,"matter-native.png",mc.getMainRenderTarget(),message->{captured=true;System.out.println("[vandorlabs][matter] screenshot "+message.getString());});
    }
}
