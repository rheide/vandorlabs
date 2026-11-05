package com.vandorlabs.neoforge;

import com.mojang.authlib.GameProfile;
import com.vandorlabs.neoforge.redstone.*;
import com.vandorlabs.redstone.ChannelList;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.*;
import net.minecraft.server.level.*;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStartedEvent;

/** Focused native adapters and independent JVM manual-source persistence. Trigger client acceptance is separate. */
@EventBusSubscriber(modid="vandorlabs")
public final class TransmitterRuntimeChecks {
    private static final BlockPos ORIGIN=new BlockPos(2100,80,2100);
    private static final int CHANNEL=65401;
    private static final BlockPos ITEM_CHEST=ORIGIN.above(4);
    private static int checks;
    private static void require(boolean value,String message){checks++;if(!value)throw new IllegalStateException("Native transmitter: "+message);}
    private static final class Receiver implements NativeChannelMember {
        final BlockEntity tile;ChannelList channels;int level,calls;
        Receiver(BlockEntity tile,int channel){this.tile=tile;channels=ChannelList.of(channel);}
        public BlockEntity channelBlockEntity(){return tile;}public ChannelList getRedstoneChannels(){return channels;}public void setChannelLevel(int level){this.level=level;calls++;}
    }
    private static Receiver receiver(ServerLevel level,BlockPos pos,int channel){level.getChunkAt(pos);level.setBlock(pos,Blocks.CHEST.defaultBlockState(),3);Receiver result=new Receiver(level.getBlockEntity(pos),channel);NativeRedstoneChannels.queueRegistration(result);return result;}
    @SubscribeEvent public static void started(ServerStartedEvent event){
        String phase=System.getProperty("vandorlabs.transmitterPhase","");if(!java.util.Set.of("write","read","off").contains(phase))return;
        checks=0;var server=event.getServer();var world=server.overworld();world.getChunkAt(ORIGIN);
        ServerPlayer player=new ServerPlayer(server,world,new GameProfile(UUID.fromString("144aa611-044d-488a-9bb2-064dc8e25e01"),"TransmitterFixture"),ClientInformation.createDefault());player.setPos(ORIGIN.getX()+.5,ORIGIN.getY(),ORIGIN.getZ()+.5);
        if(phase.equals("write")){
            require(ManualSavedData.get(world).entries().isEmpty(),"isolated empty source storage");
            recipes(world);
            for(int tier=0;tier<4;tier++){
                ItemStack stack=NativeTransmitterItem.variant(VandorLabsBootstrap.REDSTONE_TRANSMITTER.get(),tier);NativeTransmitterItem.configure(stack,ChannelList.of(CHANNEL+tier));player.setItemInHand(InteractionHand.MAIN_HAND,stack);
                int range=NativeTransmitterItem.range(stack),distance=range==0?1024:range;Receiver edge=receiver(world,ORIGIN.east(distance),CHANNEL+tier),outside=receiver(world,ORIGIN.offset(distance,1,0),CHANNEL+tier);
                require(edge.calls==0,"registration deferred until safe flush");NativeRedstoneChannels.flush(world);
                stack.getItem().use(world,player,InteractionHand.MAIN_HAND);
                require(edge.level==15&&outside.level==(range==0?15:0),"native item range sphere tier="+tier);
                require(NativeRedstoneChannels.level(world,ORIGIN,CHANNEL+tier)==15,"stationary source at use position");
                player.setPos(ORIGIN.getX()+10000,ORIGIN.getY(),ORIGIN.getZ());require(NativeRedstoneChannels.level(world,ORIGIN,CHANNEL+tier)==15,"manual source does not follow player");player.setPos(ORIGIN.getX()+.5,ORIGIN.getY(),ORIGIN.getZ()+.5);
                stack.getItem().use(world,player,InteractionHand.MAIN_HAND);require(edge.level==0&&outside.level==0,"second ordinary item use withdraws source");NativeRedstoneChannels.unregister(edge);NativeRedstoneChannels.unregister(outside);
            }
            Receiver stale=receiver(world,ORIGIN.north(8),CHANNEL);world.removeBlock(stale.tile.getBlockPos(),false);NativeRedstoneChannels.flush(world);require(stale.calls==0,"replaced before deferred flush never registers");
            world.setBlock(ITEM_CHEST,Blocks.CHEST.defaultBlockState(),3);var itemChest=(net.minecraft.world.level.block.entity.ChestBlockEntity)world.getBlockEntity(ITEM_CHEST);
            for(int tier=0;tier<4;tier++){ItemStack variant=NativeTransmitterItem.variant(VandorLabsBootstrap.REDSTONE_TRANSMITTER.get(),tier);NativeTransmitterItem.configure(variant,ChannelList.of(CHANNEL,tier+1));itemChest.setItem(tier,variant);}
            ItemStack offhand=NativeTransmitterItem.variant(VandorLabsBootstrap.REDSTONE_TRANSMITTER.get(),2);player.setItemInHand(InteractionHand.OFF_HAND,offhand);TransmitterMenu offMenu=new TransmitterMenu(76,player.getInventory(),InteractionHand.OFF_HAND);player.containerMenu=offMenu;
            require(TransmitterPayload.apply(player,new TransmitterPayload(76,"65409"))&&NativeTransmitterItem.channels(offhand).first()==65409,"offhand exact editor");player.setItemInHand(InteractionHand.OFF_HAND,ItemStack.EMPTY);require(!offMenu.stillValid(player),"dropped offhand editor rejects");
            require(NativeRedstoneChannels.level(server.getLevel(net.minecraft.world.level.Level.NETHER),ORIGIN,CHANNEL)==0,"dimensions remain independent");
            ItemStack stack=NativeTransmitterItem.variant(VandorLabsBootstrap.REDSTONE_TRANSMITTER.get(),0);player.setItemInHand(InteractionHand.MAIN_HAND,stack);TransmitterMenu menu=new TransmitterMenu(77,player.getInventory(),InteractionHand.MAIN_HAND);player.containerMenu=menu;
            require(TransmitterPayload.apply(player,new TransmitterPayload(77,"65401, 65402")),"authenticated exact held editor");
            require(!TransmitterPayload.apply(player,new TransmitterPayload(78,"9")),"wrong menu window rejected");
            player.setItemInHand(InteractionHand.MAIN_HAND,stack.copy());require(!TransmitterPayload.apply(player,new TransmitterPayload(77,"9")),"equal replacement object rejected");player.setItemInHand(InteractionHand.MAIN_HAND,stack);
            int selected=player.getInventory().selected;player.getInventory().selected=(selected+1)%9;require(!menu.stillValid(player),"selected slot change rejected");player.getInventory().selected=selected;
            NativeTransmitterItem.configure(stack,ChannelList.of(CHANNEL));stack.getItem().use(world,player,InteractionHand.MAIN_HAND);
            require(ManualSavedData.get(world).entries().size()==1,"one exact saved source before restart");
            CompoundTag saved=ManualSavedData.get(world).save(new CompoundTag(),world.registryAccess());ManualSavedData restored=ManualSavedData.load(saved,world.registryAccess());require(saved.equals(restored.save(new CompoundTag(),world.registryAccess())),"exact legacy Sources schema roundtrip");
        }else if(phase.equals("read")){
            world.getChunkAt(ITEM_CHEST);var itemChest=(net.minecraft.world.level.block.entity.ChestBlockEntity)world.getBlockEntity(ITEM_CHEST);require(itemChest!=null,"tier inventory persisted");
            for(int tier=0;tier<4;tier++){ItemStack stack=itemChest.getItem(tier);require(NativeTransmitterItem.tier(stack)==tier&&NativeTransmitterItem.range(stack)==NativeTransmitterItem.RANGES[tier]&&NativeTransmitterItem.channels(stack).equals(ChannelList.of(CHANNEL,tier+1)),"item components persisted tier="+tier);}
            var entries=ManualSavedData.get(world).entries();require(entries.size()==1&&entries.getFirst().pos.equals(ORIGIN)&&entries.getFirst().channel==CHANNEL&&entries.getFirst().range==16,"second JVM retains exact stationary tuple");
            Receiver receiver=receiver(world,ORIGIN.east(16),CHANNEL);NativeRedstoneChannels.flush(world);require(receiver.level==15,"loaded receiver discovers persisted source");
            ItemStack stack=NativeTransmitterItem.variant(VandorLabsBootstrap.REDSTONE_TRANSMITTER.get(),0);NativeTransmitterItem.configure(stack,ChannelList.of(CHANNEL));player.setItemInHand(InteractionHand.MAIN_HAND,stack);stack.getItem().use(world,player,InteractionHand.MAIN_HAND);require(receiver.level==0&&ManualSavedData.get(world).entries().isEmpty(),"ordinary use after restart withdraws saved source");NativeRedstoneChannels.unregister(receiver);
        }else require(ManualSavedData.get(world).entries().isEmpty(),"third JVM proves withdrawal persisted");
        server.saveEverything(true,true,true);System.out.println("[vandorlabs][transmitter] server-"+phase+" PASS checks="+checks+" current indices native item menu stationary Sources persistence");server.halt(false);
    }
    private static void recipes(ServerLevel world){
        String[] variants={"short","medium","long","infinite"};net.minecraft.world.item.Item[] centers={net.minecraft.world.item.Items.IRON_INGOT,net.minecraft.world.item.Items.GOLD_INGOT,net.minecraft.world.item.Items.DIAMOND,net.minecraft.world.item.Items.NETHER_STAR};
        for(int tier=0;tier<4;tier++){
            var id=net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("vandorlabs","redstone_transmitter_"+variants[tier]);var holder=world.getRecipeManager().byKey(id).orElseThrow();var recipe=(net.minecraft.world.item.crafting.CraftingRecipe)holder.value();
            var items=new java.util.ArrayList<ItemStack>(java.util.List.of(ItemStack.EMPTY,new ItemStack(net.minecraft.world.item.Items.REDSTONE),ItemStack.EMPTY,new ItemStack(net.minecraft.world.item.Items.REDSTONE),new ItemStack(centers[tier]),new ItemStack(net.minecraft.world.item.Items.REDSTONE),ItemStack.EMPTY,new ItemStack(net.minecraft.world.item.Items.IRON_INGOT),ItemStack.EMPTY));
            var input=net.minecraft.world.item.crafting.CraftingInput.of(3,3,items);require(recipe.matches(input,world),"canonical tier recipe matches="+variants[tier]);
            var selected=world.getRecipeManager().getRecipeFor(net.minecraft.world.item.crafting.RecipeType.CRAFTING,input,world).orElseThrow();require(selected.id().equals(id),"actual recipe manager selects canonical tier="+tier);
            ItemStack output=recipe.assemble(input,world.registryAccess());require(output.is(VandorLabsBootstrap.REDSTONE_TRANSMITTER.get())&&output.getCount()==1&&NativeTransmitterItem.tier(output)==tier&&NativeTransmitterItem.range(output)==NativeTransmitterItem.RANGES[tier],"single-ID tier/range/count output="+tier+" actualTier="+NativeTransmitterItem.tier(output)+" count="+output.getCount()+" data="+output.getOrDefault(net.minecraft.core.component.DataComponents.CUSTOM_DATA,net.minecraft.world.item.component.CustomData.EMPTY).copyTag());
            require(output.get(net.minecraft.core.component.DataComponents.CUSTOM_MODEL_DATA)!=null&&output.get(net.minecraft.core.component.DataComponents.CUSTOM_MODEL_DATA).value()==tier&&NativeTransmitterItem.channels(output).isEmpty(),"native tier artwork component and default disconnected channels="+tier);
            items.set(0,new ItemStack(net.minecraft.world.item.Items.DIRT));require(!recipe.matches(net.minecraft.world.item.crafting.CraftingInput.of(3,3,items),world),"extraneous ingredient rejected tier="+tier);
        }
        System.out.println("[vandorlabs][transmitter] recipes PASS canonical4patterns actual native manager tier range model components count malformed");
    }

}
