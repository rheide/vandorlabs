package com.vandorlabs.items;

import com.vandorlabs.VandorLabs;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.EnumHand;
import net.minecraft.util.EnumActionResult;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.*;

/** Consume the gesture before a block or the Configurizer opens its own settings. */
@Mod.EventBusSubscriber(modid=VandorLabs.MODID)
public final class ArmorTextureSampling {
    private ArmorTextureSampling() { }
    @SubscribeEvent(priority=EventPriority.HIGH)
    public static void onBlock(PlayerInteractEvent.RightClickBlock event) {
        EntityPlayer player=event.getEntityPlayer();
        if(!player.isSneaking())return;
        EnumHand hand=event.getHand();
        if(hand==EnumHand.MAIN_HAND && player.getHeldItemMainhand().getItem()==ModItems.CONFIGURIZER
                && player.getHeldItemOffhand().getItem() instanceof ItemProgrammableArmor)hand=EnumHand.OFF_HAND;
        if(!(player.getHeldItem(hand).getItem() instanceof ItemProgrammableArmor))return;
        event.setCanceled(true);event.setCancellationResult(EnumActionResult.SUCCESS);
        if(event.getWorld().isRemote)
            com.vandorlabs.client.ArmorTextureSampler.sample(player,hand,event.getPos(),event.getFace(),event.getHitVec());
    }
}
