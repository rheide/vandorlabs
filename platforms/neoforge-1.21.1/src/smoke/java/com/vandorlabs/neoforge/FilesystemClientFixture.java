package com.vandorlabs.neoforge;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

@EventBusSubscriber(modid="vandorlabs")
public final class FilesystemClientFixture {
    @SubscribeEvent public static void login(PlayerEvent.PlayerLoggedInEvent event){
        if(!System.getProperty("vandorlabs.filesystemPhase","").equals("client")||!(event.getEntity() instanceof ServerPlayer player))return;
        var level=player.serverLevel();for(int x=-4;x<=4;x++)for(int z=-5;z<=4;z++)level.setBlock(new BlockPos(x,79,z),Blocks.STONE.defaultBlockState(),3);
        level.setBlock(FilesystemRuntimeChecks.POS,VandorLabsBootstrap.PROGRAMMABLE_BLOCK.get().defaultBlockState(),3);
        ((ProgrammableBlockEntity)level.getBlockEntity(FilesystemRuntimeChecks.POS)).apply(new com.vandorlabs.ui.ProgrammableBlockDialog.State(FilesystemRuntimeChecks.CHOICE,0,false,new int[]{-1,-1,-1,-1,-1,-1},-1));
        player.setGameMode(GameType.CREATIVE);player.teleportTo(.5,80,-4);player.setYRot(0);player.setXRot(8);player.getInventory().selected=0;player.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(VandorLabsBootstrap.CONFIGURIZER.get()));player.inventoryMenu.broadcastChanges();
        level.setDayTime(6000);level.getGameRules().getRule(net.minecraft.world.level.GameRules.RULE_DAYLIGHT).set(false,level.getServer());level.getGameRules().getRule(net.minecraft.world.level.GameRules.RULE_DOMOBSPAWNING).set(false,level.getServer());
        System.out.println("[vandorlabs][filesystem] client fixture READY");
    }
    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent event){
        if(!System.getProperty("vandorlabs.filesystemPhase","").equals("client")||!(event.getEntity() instanceof ServerPlayer player))return;
        var tile=(ProgrammableBlockEntity)player.serverLevel().getBlockEntity(FilesystemRuntimeChecks.POS);FilesystemRuntimeChecks.require(tile.settings().mainTexture==FilesystemRuntimeChecks.CHOICE,"actual picker roundtrip lost filesystem ID");
        System.out.println("[vandorlabs][filesystem] server-client-actions PASS saved filesystem choice from native client editing session");
    }
}
