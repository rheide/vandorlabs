package com.vandorlabs.neoforge;

import com.vandorlabs.ui.ProgrammableBlockDialog;
import java.util.Arrays;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.GameType;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.event.server.ServerStartedEvent;

/** Opt-in disposable-world checks. Never enabled by ordinary mod startup. */
@EventBusSubscriber(modid="vandorlabs")
public final class ModernRuntimeChecks {
    private static net.minecraft.server.MinecraftServer pendingRead;
    private static int readTicks;
    private static long readDeadline;
    private static void require(boolean condition,String message) { if(!condition)throw new IllegalStateException("Modern runtime check: "+message); }
    @SubscribeEvent public static void started(ServerStartedEvent event) {
        String phase=System.getProperty("vandorlabs.modernSmoke", "");
        if(!phase.equals("write")&&!phase.equals("read"))return;
        var server=event.getServer();var level=server.overworld();var pos=new BlockPos(0,80,0);
        level.getChunkAt(pos);
        if(phase.equals("write")) {
            level.setBlock(pos,VandorLabsModern.PROGRAMMABLE_BLOCK.get().defaultBlockState(),3);
            var player=FakePlayerFactory.getMinecraft(level);player.setGameMode(GameType.CREATIVE);player.setPos(.5,81,.5);
            player.containerMenu=new ProgrammableMenu(1,player.getInventory(),pos);
            FinishPayload settings=new FinishPayload(pos,3,2,true,new int[]{0,1,2,3,-1,5});
            require(FinishPayload.apply(player,settings),"authorized settings denied");
            ProgrammableBlockEntity tile=(ProgrammableBlockEntity)level.getBlockEntity(pos);
            require(tile.settings().mainTexture==3&&tile.settings().layout==2&&tile.settings().facesEnabled,"settings not applied");
            var drops=net.minecraft.world.level.block.Block.getDrops(tile.getBlockState(),level,pos,tile,player,new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.IRON_PICKAXE));
            require(drops.size()==1&&drops.get(0).has(net.minecraft.core.component.DataComponents.BLOCK_ENTITY_DATA),"configured drop lost block entity data");
            CompoundTag dropped=drops.get(0).get(net.minecraft.core.component.DataComponents.BLOCK_ENTITY_DATA).copyTag();
            require(dropped.getInt(ProgrammableBlockDialog.MAIN_TEXTURE_KEY)==3,"configured drop lost finish");
            var picked=tile.getBlockState().getBlock().getCloneItemStack(tile.getBlockState(),new net.minecraft.world.phys.BlockHitResult(net.minecraft.world.phys.Vec3.atCenterOf(pos),net.minecraft.core.Direction.NORTH,pos,false),level,pos,player);
            require(picked.get(net.minecraft.core.component.DataComponents.BLOCK_ENTITY_DATA).copyTag().getInt(ProgrammableBlockDialog.MAIN_TEXTURE_KEY)==3,"creative pick lost configured finish");
            CompoundTag saved=tile.saveWithoutMetadata(level.registryAccess());
            var restored=new ProgrammableBlockEntity(pos,tile.getBlockState());restored.loadWithComponents(saved,level.registryAccess());
            require(restored.settings().mainTexture==3&&restored.settings().layout==2&&Arrays.equals(restored.settings().faces(),settings.faces()),"NBT roundtrip mismatch");
            require(!FinishPayload.apply(player,new FinishPayload(pos,-1,0,false,new int[]{-1,-1,-1,-1,-1,-1})),"invalid main accepted");
            require(!FinishPayload.apply(player,new FinishPayload(pos,0,3,false,new int[]{-1,-1,-1,-1,-1,-1})),"invalid layout accepted");
            require(!FinishPayload.apply(player,new FinishPayload(new BlockPos(999999,80,999999),0,0,false,new int[]{-1,-1,-1,-1,-1,-1})),"remote chunk accepted");
            var editing=player.containerMenu;player.containerMenu=player.inventoryMenu;require(!FinishPayload.apply(player,settings),"missing editing session accepted");player.containerMenu=editing;
            player.setGameMode(GameType.SURVIVAL);require(!FinishPayload.apply(player,settings),"survival editor accepted");
            player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,new net.minecraft.world.item.ItemStack(VandorLabsModern.CONFIGURIZER.get()));require(FinishPayload.apply(player,settings),"survival configurizer denied");
            player.setGameMode(GameType.CREATIVE);player.setPos(200,81,200);require(!FinishPayload.apply(player,settings),"remote editor accepted");
            CompoundTag legacy=saved.copy();legacy.remove("HousingTextureVersion");legacy.putInt(ProgrammableBlockDialog.MAIN_TEXTURE_KEY,15);
            restored.loadWithComponents(legacy,level.registryAccess());require(restored.settings().mainTexture==14,"legacy removed finish migration mismatch");
            com.vandorlabs.neoforge.redstone.WirelessRuntimeChecks.run(level);
            com.vandorlabs.neoforge.screen.ScreenRuntimeChecks.run(level);
            CompoundTag unresolved=saved.copy();unresolved.putInt(ProgrammableBlockDialog.MAIN_TEXTURE_KEY,0x23456789);unresolved.putIntArray(ProgrammableBlockDialog.FACES_KEY,new int[]{0x456789ab,-1,-1,-1,-1,-1});
            restored.loadWithComponents(unresolved,level.registryAccess());
            CompoundTag preserved=restored.saveWithoutMetadata(level.registryAccess());
            require(preserved.getInt(ProgrammableBlockDialog.MAIN_TEXTURE_KEY)==0x23456789&&preserved.getIntArray(ProgrammableBlockDialog.FACES_KEY)[0]==0x456789ab,"unresolved external material identity lost on save");
            server.saveEverything(true,true,true);
            System.out.println("[vandorlabs][modern] server-write PASS settings, fixed-size payload bounds, permissions, distance, unloaded target, NBT roundtrip, legacy migration, disk save");
        } else {
            com.vandorlabs.neoforge.screen.RedstoneOverlayRuntimeChecks.prepareRead(level);
            for(BlockPos at:java.util.List.of(pos,new BlockPos(4,80,0),new BlockPos(10,80,0),new BlockPos(14,80,0))) { level.getChunkAt(at);level.getBlockEntity(at); }
            pendingRead=server;readTicks=0;readDeadline=System.nanoTime()+java.util.concurrent.TimeUnit.SECONDS.toNanos(30);
            return;
        }
        server.halt(false);
    }
    @SubscribeEvent(priority=net.neoforged.bus.api.EventPriority.LOWEST)
    public static void ready(net.neoforged.neoforge.event.tick.LevelTickEvent.Post event) {
        if(pendingRead==null||event.getLevel()!=pendingRead.overworld())return;
        require(System.nanoTime()<=readDeadline,"loaded-world read checks exceeded thirty seconds");
        if(++readTicks<2)return;
        var server=pendingRead;pendingRead=null;var level=server.overworld();var pos=new BlockPos(0,80,0);
        require(level.getBlockEntity(pos) instanceof ProgrammableBlockEntity,"saved programmable block missing");
        var tile=(ProgrammableBlockEntity)level.getBlockEntity(pos);
        require(tile.settings().mainTexture==3&&tile.settings().layout==2&&tile.settings().facesEnabled&&Arrays.equals(tile.settings().faces(),new int[]{0,1,2,3,-1,5}),"disk reload mismatch");
        com.vandorlabs.neoforge.redstone.WirelessRuntimeChecks.read(level);
        com.vandorlabs.neoforge.screen.ScreenRuntimeChecks.read(level);
        System.out.println("[vandorlabs][modern] server-read PASS exact programmable settings recovered from disk after two real loaded-world ticks");
        server.halt(false);
    }
    @SubscribeEvent public static void stopped(net.neoforged.neoforge.event.server.ServerStoppedEvent event) { if(pendingRead==event.getServer())pendingRead=null; }
    private ModernRuntimeChecks() {}
}
