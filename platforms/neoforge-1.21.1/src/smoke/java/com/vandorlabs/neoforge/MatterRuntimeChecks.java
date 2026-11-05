package com.vandorlabs.neoforge;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;

/** Opt-in disposable-world checks, including an actual process restart. */
@EventBusSubscriber(modid="vandorlabs")
public final class MatterRuntimeChecks {
    static final BlockPos BLOCK_POS = new BlockPos(0,80,0);
    static final BlockPos CHEST_POS = new BlockPos(2,80,0);
    private static boolean clientPlaced,clientMined;
    private static final BlockPos CLIENT_PLACED = new BlockPos(0,80,-1);
    @SubscribeEvent public static void placed(net.neoforged.neoforge.event.level.BlockEvent.EntityPlaceEvent event) {
        if(System.getProperty("vandorlabs.matterPhase", "").equals("client") && event.getLevel() instanceof net.minecraft.server.level.ServerLevel && event.getPos().equals(CLIENT_PLACED)) {
            require(event.getPlacedBlock().is(VandorLabsBootstrap.MATTER_BLOCK.get()),"server placed wrong block");
            clientPlaced=true;
        }
    }
    @SubscribeEvent public static void broken(net.neoforged.neoforge.event.level.BlockEvent.BreakEvent event) {
        if(System.getProperty("vandorlabs.matterPhase", "").equals("client") && event.getPos().equals(CLIENT_PLACED)) {
            require(clientPlaced && event.getState().is(VandorLabsBootstrap.MATTER_BLOCK.get()),"server mining without placement");
            clientMined=true;
        }
    }
    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent event) {
        if(!System.getProperty("vandorlabs.matterPhase", "").equals("client") || !(event.getEntity() instanceof ServerPlayer player))return;
        require(clientPlaced && clientMined && player.serverLevel().getBlockState(CLIENT_PLACED).isAir(),"server did not observe placement/mining packets");
        System.out.println("[vandorlabs][matter] server-client-actions PASS real placement and mining");
    }
    private static void require(boolean value, String message) {
        if (!value) throw new IllegalStateException("Native matter: " + message);
    }
    @SubscribeEvent public static void started(ServerStartedEvent event) {
        String phase = System.getProperty("vandorlabs.matterPhase", "");
        if (!phase.equals("write") && !phase.equals("read")) return;
        var server = event.getServer(); var level = server.overworld();
        level.getChunkAt(BLOCK_POS); level.getChunkAt(CHEST_POS);
        var state = VandorLabsBootstrap.MATTER_BLOCK.get().defaultBlockState();
        require(state.is(BlockTags.MINEABLE_WITH_PICKAXE) && state.is(BlockTags.NEEDS_STONE_TOOL), "harvest tags");
        require(state.getDestroySpeed(level,BLOCK_POS)==5 && VandorLabsBootstrap.MATTER_BLOCK.get().getExplosionResistance()==6, "legacy hardness/resistance");
        require(new ItemStack(Items.STONE_PICKAXE).isCorrectToolForDrops(state), "stone pickaxe refused");
        require(!new ItemStack(Items.WOODEN_PICKAXE).isCorrectToolForDrops(state), "wooden pickaxe accepted");
        recipe(level, "programmable_matter_block", 3, 3,
            java.util.Collections.nCopies(9,VandorLabsBootstrap.MATTER_INGOT.get()),VandorLabsBootstrap.MATTER_BLOCK_ITEM.get(),1);
        recipe(level, "programmable_matter_unpack", 1, 1,
            List.of(VandorLabsBootstrap.MATTER_BLOCK_ITEM.get()),VandorLabsBootstrap.MATTER_INGOT.get(),9);
        var alloy = new ArrayList<Item>(java.util.Collections.nCopies(9,Items.IRON_INGOT));
        alloy.set(4,VandorLabsBootstrap.MATTER_INGOT.get());
        recipe(level, "industrial_alloy_ingot",3,3,alloy,VandorLabsBootstrap.ALLOY_INGOT.get(),9);
        if (phase.equals("write")) {
            level.setBlock(BLOCK_POS.below(),Blocks.STONE.defaultBlockState(),3);
            level.setBlock(BLOCK_POS,state,3);
            level.setBlock(CHEST_POS,Blocks.CHEST.defaultBlockState(),3);
            var chest = (ChestBlockEntity)level.getBlockEntity(CHEST_POS);
            chest.setItem(0,new ItemStack(VandorLabsBootstrap.MATTER_INGOT.get(),23));
            chest.setItem(1,new ItemStack(VandorLabsBootstrap.ALLOY_INGOT.get(),37));
            chest.setChanged();
        } else {
            require(level.getBlockState(BLOCK_POS).is(VandorLabsBootstrap.MATTER_BLOCK.get()),"saved block missing after restart");
            var chest = (ChestBlockEntity)level.getBlockEntity(CHEST_POS);
            require(chest!=null && chest.getItem(0).is(VandorLabsBootstrap.MATTER_INGOT.get()) && chest.getItem(0).getCount()==23,"saved matter ingots");
            require(chest.getItem(1).is(VandorLabsBootstrap.ALLOY_INGOT.get()) && chest.getItem(1).getCount()==37,"saved alloy ingots");
            var drops = Block.getDrops(state,level,BLOCK_POS,null,null,new ItemStack(Items.STONE_PICKAXE));
            require(drops.size()==1 && drops.getFirst().is(VandorLabsBootstrap.MATTER_BLOCK_ITEM.get()) && drops.getFirst().getCount()==1,"native loot table");
        }
        server.saveEverything(true,true,true);
        System.out.println("[vandorlabs][matter] server-"+phase+" PASS registry, tool tiers, recipes, native block and item persistence");
        server.halt(false);
    }
    private static void recipe(net.minecraft.server.level.ServerLevel level,String id,int width,int height,List<Item> items,Item output,int count) {
        var holder=level.getRecipeManager().byKey(ResourceLocation.fromNamespaceAndPath("vandorlabs",id)).orElseThrow();
        var recipe=(net.minecraft.world.item.crafting.CraftingRecipe)holder.value();
        var input=CraftingInput.of(width,height,items.stream().map(ItemStack::new).toList());
        require(recipe.matches(input,level),id+" recipe mismatch");
        var result=recipe.assemble(input,level.registryAccess());
        require(result.is(output)&&result.getCount()==count,id+" output mismatch");
        var wrong=new ArrayList<ItemStack>(items.stream().map(ItemStack::new).toList());
        wrong.set(0,new ItemStack(Items.DIRT));
        require(!recipe.matches(CraftingInput.of(width,height,wrong),level),id+" accepts wrong ingredient");
    }
    @SubscribeEvent public static void login(PlayerEvent.PlayerLoggedInEvent event) {
        if(!System.getProperty("vandorlabs.matterPhase", "").equals("client") || !(event.getEntity() instanceof ServerPlayer player)) return;
        var level=player.serverLevel();
        for(int x=-5;x<=5;x++) for(int z=-6;z<=4;z++) level.setBlock(new BlockPos(x,79,z),Blocks.SMOOTH_STONE.defaultBlockState(),3);
        level.setBlock(BLOCK_POS,VandorLabsBootstrap.MATTER_BLOCK.get().defaultBlockState(),3);
        level.setDayTime(6000);level.getGameRules().getRule(net.minecraft.world.level.GameRules.RULE_DAYLIGHT).set(false,level.getServer());
        player.setGameMode(GameType.CREATIVE);player.teleportTo(0.5,80,-4);player.setYRot(0);player.setXRot(8);
        player.getInventory().setItem(0,new ItemStack(VandorLabsBootstrap.MATTER_BLOCK_ITEM.get()));
        player.getInventory().setItem(1,new ItemStack(VandorLabsBootstrap.MATTER_INGOT.get()));
        player.getInventory().setItem(2,new ItemStack(VandorLabsBootstrap.ALLOY_INGOT.get()));
        player.inventoryMenu.broadcastChanges();
        System.out.println("[vandorlabs][matter] client fixture READY");
    }
}
