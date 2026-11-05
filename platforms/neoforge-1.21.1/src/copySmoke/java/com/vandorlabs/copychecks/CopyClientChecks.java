package com.vandorlabs.copychecks;

import com.vandorlabs.neoforge.redstone.NativeTransmitterItem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.*;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;

public final class CopyClientChecks {
    private static int ticks,stage,at,round=1;private static boolean capture,captured;
    @SubscribeEvent public static void tick(ClientTickEvent.Post event){
        if(!CopyServerChecks.enabled())return;var mc=Minecraft.getInstance();if(mc.screen instanceof net.minecraft.client.gui.screens.AccessibilityOnboardingScreen screen){screen.onClose();return;}if(mc.player==null||mc.level==null||mc.gameMode==null)return;
        CopyServerChecks.require(++ticks<1600,"client timeout stage="+stage+" round="+round+" mode="+mc.gameMode.getPlayerMode()+" selected="+mc.player.getInventory().selected+" held="+mc.player.getMainHandItem());
        if(stage==0){
            if(ticks<80||mc.gameMode.getPlayerMode()!=GameType.SURVIVAL||!mc.player.getMainHandItem().isEmpty()||!mc.level.getBlockState(CopyServerChecks.TABLE).is(net.minecraft.world.level.block.Blocks.CRAFTING_TABLE))return;
            if(round==1)TransmitterCopyRuntimeChecks.cache(mc.level);
            var result=mc.gameMode.useItemOn(mc.player,InteractionHand.MAIN_HAND,new BlockHitResult(new Vec3(.5,80.5,0),Direction.NORTH,CopyServerChecks.TABLE,false));CopyServerChecks.require(result.consumesAction(),"actual table interaction refused");stage=1;at=ticks;
        }else if(stage==1){
            if(!(mc.player.containerMenu instanceof CraftingMenu menu)||menu.getSlot(0).getItem().isEmpty())return;
            CopyServerChecks.require(menu.getSlot(5).getItem().getCount()==1&&NativeTransmitterItem.channels(menu.getSlot(5).getItem()).first()==7,"client observed exact center source");
            CopyServerChecks.require(NativeTransmitterItem.tier(menu.getSlot(0).getItem())==3&&NativeTransmitterItem.channels(menu.getSlot(0).getItem()).first()==7,"actual table result preserves target tier and copied channels");
            if(round==2&&!captured){capture=true;stage=2;at=ticks;}else take(mc,menu);
        }else if(stage==2){if(captured&&ticks>at+5)take(mc,(CraftingMenu)mc.player.containerMenu);
        }else if(stage==3){
            var menu=(CraftingMenu)mc.player.containerMenu;if(!menu.getSlot(round==1?4:6).getItem().isEmpty()||!menu.getSlot(0).getItem().isEmpty()||ticks<at+15)return;
            CopyServerChecks.require(menu.getSlot(5).getItem().getCount()==1&&NativeTransmitterItem.channels(menu.getSlot(5).getItem()).first()==7,"client observes exact source remainder");
            if(round==1){CopyServerChecks.require(NativeTransmitterItem.tier(menu.getCarried())==3&&NativeTransmitterItem.channels(menu.getCarried()).first()==7,"real pickup cursor output");mc.player.closeContainer();mc.player.getInventory().selected=8;mc.player.connection.send(new net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket(8));round=2;stage=4;at=ticks;}
            else {int output=0;for(ItemStack stack:mc.player.getInventory().items)if(NativeTransmitterItem.tier(stack)==3&&NativeTransmitterItem.channels(stack).first()==7)output+=stack.getCount();CopyServerChecks.require(output==1,"real shiftcraft inventory output once");System.out.println("[vandorlabs][transmitter-copy] client PASS actualtable both-neighbors pickup shiftcraft native-origin-context exact-source-remainders");mc.stop();stage=5;}
        }else if(stage==4&&ticks>at+25&&mc.player.getMainHandItem().isEmpty()){stage=0;}
    }
    private static void take(Minecraft mc,CraftingMenu menu){mc.gameMode.handleInventoryMouseClick(menu.containerId,0,0,round==1?ClickType.PICKUP:ClickType.QUICK_MOVE,mc.player);stage=3;at=ticks;}
    @SubscribeEvent public static void frame(RenderFrameEvent.Post event){if(capture){capture=false;var mc=Minecraft.getInstance();Screenshot.grab(mc.gameDirectory,"transmitter-copy-native.png",mc.getMainRenderTarget(),message->captured=true);}}
}
