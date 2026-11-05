package com.vandorlabs.copychecks;

import com.vandorlabs.neoforge.VandorLabsBootstrap;
import com.vandorlabs.neoforge.redstone.NativeTransmitterItem;
import com.vandorlabs.redstone.ChannelList;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerContainerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

public final class CopyServerChecks {
    public static final BlockPos TABLE=new BlockPos(0,80,0);
    private static ServerPlayer player;private static CraftingMenu active;private static int round,complete;private static ItemStack source,target;
    static boolean enabled(){return System.getProperty("vandorlabs.transmitterPhase","").equals("copy-client");}
    static void require(boolean value,String message){if(!value)throw new IllegalStateException("Native transmitter copy: "+message);}
    static ItemStack configured(int tier,int channel,String key,int value){ItemStack stack=NativeTransmitterItem.variant(VandorLabsBootstrap.REDSTONE_TRANSMITTER.get(),tier);NativeTransmitterItem.configure(stack,ChannelList.of(channel));var tag=stack.get(net.minecraft.core.component.DataComponents.CUSTOM_DATA).copyTag();tag.putInt(key,value);stack.set(net.minecraft.core.component.DataComponents.CUSTOM_DATA,net.minecraft.world.item.component.CustomData.of(tag));return stack;}
    @SubscribeEvent public static void login(PlayerEvent.PlayerLoggedInEvent event){
        if(!enabled()||!(event.getEntity() instanceof ServerPlayer serverPlayer))return;player=serverPlayer;var level=player.serverLevel();
        for(int x=-4;x<=4;x++)for(int z=-5;z<=3;z++)level.setBlock(new BlockPos(x,79,z),Blocks.STONE.defaultBlockState(),3);level.setBlock(TABLE,Blocks.CRAFTING_TABLE.defaultBlockState(),3);
        player.setGameMode(GameType.SURVIVAL);player.teleportTo(.5,80,-3.5);player.setYRot(0);player.setXRot(8);player.getInventory().clearContent();player.getInventory().selected=0;player.inventoryMenu.broadcastChanges();
        System.out.println("[vandorlabs][transmitter-copy] client fixture READY survival empty-held selected0 actual crafting table");
    }
    @SubscribeEvent public static void opened(PlayerContainerEvent.Open event){
        if(!enabled()||event.getEntity()!=player||!(event.getContainer() instanceof CraftingMenu menu))return;
        require(complete==round,"new table before previous result ownership accepted");require(round<2,"unexpected extra crafting menu");round++;player.getInventory().clearContent();player.inventoryMenu.broadcastChanges();
        source=configured(1,7,"SourceOpaque",23);target=configured(3,9,"TargetOpaque",31);active=menu;
        menu.getSlot(5).set(source.copy());menu.getSlot(round==1?4:6).set(target.copy());menu.broadcastChanges();
        System.out.println("[vandorlabs][transmitter-copy] table round READY "+round+" source-center target="+(round==1?"left":"right"));
    }
    @SubscribeEvent public static void tick(ServerTickEvent.Post event){
        if(!enabled()||active==null||complete==round)return;
        int targetSlot=round==1?4:6;if(!active.getSlot(targetSlot).getItem().isEmpty())return;
        require(active.getSlot(0).getItem().isEmpty(),"result not withdrawn after craft");ItemStack remaining=active.getSlot(5).getItem();require(remaining.getCount()==1&&ItemStack.isSameItemSameComponents(remaining,source),"original center source not returned exactly once");
        ItemStack expected=target.copy();NativeTransmitterItem.configure(expected,NativeTransmitterItem.channels(source));int count=ItemStack.isSameItemSameComponents(active.getCarried(),expected)?active.getCarried().getCount():0;
        for(int slot=0;slot<player.getInventory().getContainerSize();slot++){var stack=player.getInventory().getItem(slot);if(ItemStack.isSameItemSameComponents(stack,expected))count+=stack.getCount();}
        require(count==1,"exactly one target output across cursor and inventory count="+count);complete=round;
        System.out.println("[vandorlabs][transmitter-copy] server table PASS round="+round+" actualresulttake source-exact target-consumed output-once "+(round==1?"pickup":"shiftcraft"));
    }
    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent event){if(enabled()&&event.getEntity()==player){require(complete==2,"actual table rounds incomplete="+complete);System.out.println("[vandorlabs][transmitter-copy] server-client-actions PASS actualtable left/right pickup shiftcraft exact-source-remainders");}}
}
