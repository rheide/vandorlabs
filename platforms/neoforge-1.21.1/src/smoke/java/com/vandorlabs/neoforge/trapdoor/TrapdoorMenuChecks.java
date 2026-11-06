package com.vandorlabs.neoforge.trapdoor;

import com.vandorlabs.neoforge.VandorLabsBootstrap;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;

/** Real loaded leaf and player; transport checks precede the host's all-member transaction. */
public final class TrapdoorMenuChecks {
    private static int checks;
    private static void need(boolean ok,String why){checks++;if(!ok)throw new AssertionError("Trapdoor menu: "+why);}
    public static void run(NativeTrapdoorEntity tile,ServerPlayer player,NativeTrapdoorSettings next) {
        AbstractContainerMenu previous=player.containerMenu;ItemStack held=player.getMainHandItem().copy();
        var menu=new NativeTrapdoorMenu(null,77,tile);var edit=new NativeTrapdoorEdit(77,next,tile.inverted());
        try {
            player.containerMenu=menu;player.setItemInHand(InteractionHand.MAIN_HAND,ItemStack.EMPTY);
            var before=tile.settings();need(!menu.stillValid(player)&&!NativeTrapdoorMenu.apply(player,edit)&&tile.settings()==before,"survival empty-hand edit refused");
            player.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(VandorLabsBootstrap.CONFIGURIZER.get()));
            need(menu.stillValid(player),"Configurator authorizes installed leaf");
            need(!NativeTrapdoorMenu.apply(player,new NativeTrapdoorEdit(76,next,tile.inverted()))&&tile.settings()==before,"stale window refused");
            player.containerMenu=player.inventoryMenu;
            need(!NativeTrapdoorMenu.apply(player,edit)&&tile.settings()==before,"closed dialog refused");player.containerMenu=menu;
            var wrongFamily=new NativeTrapdoorSettings(next.choices(),next.channels(),next.facing(),next.hingeSide(),next.rotationFlipped(),!next.diagonal());
            need(!NativeTrapdoorMenu.apply(player,new NativeTrapdoorEdit(77,wrongFamily,tile.inverted()))&&tile.settings()==before,"forged family refused before mutation");
            double x=player.getX(),y=player.getY(),z=player.getZ();player.setPos(x+32,y,z);
            need(!menu.stillValid(player)&&!NativeTrapdoorMenu.apply(player,edit)&&tile.settings()==before,"distant editor refused");player.setPos(x,y,z);
            need(NativeTrapdoorMenu.apply(player,edit)&&tile.settings().channels().equals(next.channels()),"current authorized menu configures real host");
        }finally{player.containerMenu=previous;player.setItemInHand(InteractionHand.MAIN_HAND,held);}
        System.out.println("[vandorlabs][trapdoor-menu] PASS checks="+checks+" current-window/closed/distance/held-Configurator/family/installed-host");
    }
}
