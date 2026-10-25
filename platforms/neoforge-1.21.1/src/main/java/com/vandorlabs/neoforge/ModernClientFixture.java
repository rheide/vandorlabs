package com.vandorlabs.neoforge;

import com.vandorlabs.ui.ProgrammableBlockDialog;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

/** Explicit local client-smoke scene; ordinary servers never execute this hook. */
@EventBusSubscriber(modid="vandorlabs")
public final class ModernClientFixture {
    @SubscribeEvent public static void login(PlayerEvent.PlayerLoggedInEvent event) {
        if(!Boolean.getBoolean("vandorlabs.modernClientFixture")||!(event.getEntity() instanceof ServerPlayer player))return;
        var level=player.serverLevel();
        if(System.getProperty("vandorlabs.modernClientSuite","").equals("table")){
            com.vandorlabs.neoforge.table.TableRuntimeChecks.clientFixture(level);player.setGameMode(GameType.CREATIVE);player.getAbilities().flying=false;player.onUpdateAbilities();player.teleportTo(2.5,79,-4);player.setYRot(0);player.setXRot(0);
            System.out.println("[vandorlabs][modern] client-fixture READY disposable table gallery and boundary pairs");return;
        }
        for(int x=-3;x<68;x++)for(int z=-6;z<4;z++)level.setBlock(new BlockPos(x,79,z),Blocks.SMOOTH_STONE.defaultBlockState(),3);
        BlockPos cube=new BlockPos(0,80,0);level.setBlock(cube,VandorLabsModern.PROGRAMMABLE_BLOCK.get().defaultBlockState(),3);
        ((ProgrammableBlockEntity)level.getBlockEntity(cube)).apply(new ProgrammableBlockDialog.State(0,1,true,new int[]{0,1,2,3,-1,5},-1));
        var screenPos=new BlockPos(4,80,0);level.setBlock(screenPos,com.vandorlabs.neoforge.screen.ScreenRegistration.BLOCK.get().defaultBlockState(),3);
        var screen=(com.vandorlabs.neoforge.screen.ProgrammableScreenEntity)level.getBlockEntity(screenPos);
        screen.configure("engineering_screen",new com.vandorlabs.ui.ScreenSelectorDialog.State(false,0,2,1,0,1,true,true),com.vandorlabs.redstone.ChannelList.parse("5, 9"),0,-1);
        level.setBlock(BasicHousingRuntimeChecks.MATTER_POS,VandorLabsModern.MATTER_BLOCK.get().defaultBlockState(),3);
        com.vandorlabs.neoforge.trigger.TriggerRuntimeChecks.clientFixture(level);
        com.vandorlabs.neoforge.shape.ShapeRuntimeChecks.clientFixture(level);
        com.vandorlabs.neoforge.shape.ShapeChunkRuntimeChecks.clientFixture(level);
        com.vandorlabs.neoforge.fluid.NativeFluidRuntimeChecks.clientFixture(level);
        FilesystemRuntimeChecks.clientFixture(level);
        JoinedCubeRuntimeChecks.clientFixture(level);
        com.vandorlabs.neoforge.control.ControlRuntimeChecks.clientFixture(level);
        com.vandorlabs.neoforge.control.ExpandedControlRuntimeChecks.clientFixture(level);
        com.vandorlabs.neoforge.screen.ScreenFamilyRuntimeChecks.clientFixture(level);
        com.vandorlabs.neoforge.screen.RedstoneOverlayRuntimeChecks.clientFixture(level);
        com.vandorlabs.neoforge.redstone.WirelessRuntimeChecks.clientFixture(level);
        player.setGameMode(GameType.CREATIVE);player.getAbilities().flying=false;player.onUpdateAbilities();player.teleportTo(2.5,80,-4);player.setYRot(0);player.setXRot(16);
        System.out.println("[vandorlabs][modern] client-fixture READY disposable blocks, creative player and local edit permissions");
    }
    private ModernClientFixture() {}
}
