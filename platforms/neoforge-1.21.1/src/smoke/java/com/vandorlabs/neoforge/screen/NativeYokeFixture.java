package com.vandorlabs.neoforge.screen;

import com.vandorlabs.neoforge.VandorLabsBootstrap;
import com.vandorlabs.neoforge.redstone.NativeRedstoneChannels;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

@EventBusSubscriber(modid = "vandorlabs")
public final class NativeYokeFixture {
    static final BlockPos POS = new BlockPos(0,96,0);
    static final Vec3 START = new Vec3(.5,96,-2.5), CONTROLS = new Vec3(.5,96,2.5);
    static final int MAIN=8561, TOUCH=8562;
    private static int opened;

    static boolean enabled(){return Boolean.getBoolean("vandorlabs.yokeScreenCheck");}
    static void check(boolean pass,String message){if(!pass)throw new AssertionError("Yoke and Screen: "+message);}

    @SubscribeEvent public static void login(PlayerEvent.PlayerLoggedInEvent event){
        if(!enabled()||!(event.getEntity() instanceof ServerPlayer actor))return;
        var level=actor.serverLevel();
        for(int x=-3;x<=3;x++)for(int z=-4;z<=4;z++)level.setBlock(POS.offset(x,-1,z),Blocks.STONE.defaultBlockState(),3);
        check(level.getRecipeManager().byKey(net.minecraft.resources.ResourceLocation.parse("vandorlabs:yoke_screen")).isPresent(),"canonical recipe");
        var state=NativeScreenRegistrations.YOKE.get().defaultBlockState();
        check(state.is(net.minecraft.tags.BlockTags.MINEABLE_WITH_PICKAXE)
            &&new ItemStack(net.minecraft.world.item.Items.STONE_PICKAXE).isCorrectToolForDrops(state),"canonical harvest");
        actor.setGameMode(GameType.CREATIVE);
        actor.connection.teleport(START.x,START.y,START.z,0,0);
        actor.getInventory().selected=0;
        actor.getInventory().setItem(0,new ItemStack(NativeScreenRegistrations.YOKE_ITEM.get()));
        actor.getInventory().setItem(1,new ItemStack(VandorLabsBootstrap.CONFIGURIZER.get()));
        actor.inventoryMenu.broadcastChanges();
        System.out.println("[vandorlabs][yoke-screen] setup PASS");
    }

    @SubscribeEvent public static void open(net.neoforged.neoforge.event.entity.player.PlayerContainerEvent.Open event){
        if(!enabled()||!(event.getEntity() instanceof ServerPlayer actor)||!(event.getContainer() instanceof NativeScreenMenu menu))return;
        check(menu.tile.yoke()&&menu.tile.getBlockPos().equals(POS),"opened the registered Yoke host");
        if(++opened==1){
            check(actor.getMainHandItem().is(VandorLabsBootstrap.CONFIGURIZER.get()),"Configurer path");
            actor.connection.teleport(CONTROLS.x,CONTROLS.y,CONTROLS.z,180,0);
        }else check(opened==2&&actor.getMainHandItem().isEmpty()&&actor.isShiftKeyDown(),"ordinary creative interaction");
    }

    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent event){
        if(!enabled()||!(event.getEntity() instanceof ServerPlayer actor))return;
        var level=actor.serverLevel();
        check(opened==2&&level.getBlockEntity(POS) instanceof NativeScreenEntity,"both real dialogs opened");
        var tile=(NativeScreenEntity)level.getBlockEntity(POS);
        check(tile.controls(0).rows().size()==6&&tile.controls(1).rows().size()==3,"six main and three touch rows");
        check(tile.controls(0).rows().get(5).label().equals("Last")&&tile.controls(1).rows().get(2).label().equals("Trim"),"edited the final rows on both surfaces");
        check(NativeRedstoneChannels.level(level,POS,MAIN)==15&&NativeRedstoneChannels.level(level,POS,TOUCH)==10,
            "separate actual main toggle and touch slider outputs");
        System.out.println("[vandorlabs][yoke-screen] completed PASS");
        System.out.println("[vandorlabs][yoke-screen] logout PASS");
    }
}
