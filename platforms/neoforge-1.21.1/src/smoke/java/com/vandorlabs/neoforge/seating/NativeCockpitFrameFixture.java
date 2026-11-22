package com.vandorlabs.neoforge.seating;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

@EventBusSubscriber(modid="vandorlabs")
public final class NativeCockpitFrameFixture {
    static final BlockPos ROOT=new BlockPos(0,96,0);
    static final Vec3 START=new Vec3(.5,96,2.75);
    static final BlockPos MINE=ROOT.east();
    static final int TOUCH_CHANNEL=8641,CENTER_CHANNEL=8642,DISPLAY_CHANNEL=8643,EXTRA_CHANNEL=8644,THROTTLE_CHANNEL=8645;
    private static ServerPlayer player;
    private static boolean sawSignal,sawCenter,sawIndependent,sawThrottle;
    static boolean enabled(){return Boolean.getBoolean("vandorlabs.cockpitFrameCheck");}
    static void check(boolean pass,String message){if(!pass)throw new AssertionError("Stationary Cockpit: "+message);}

    @SubscribeEvent public static void login(PlayerEvent.PlayerLoggedInEvent event) {
        if(!enabled()||!(event.getEntity() instanceof ServerPlayer actor))return;
        player=actor;
        var level=actor.serverLevel();
        for(int x=-3;x<=4;x++)for(int z=-4;z<=4;z++)level.setBlock(ROOT.offset(x,-1,z),Blocks.STONE.defaultBlockState(),3);
        var item=NativeSeatBindings.ITEMS.get(NativeSeatKind.COCKPIT);
        check(level.getRecipeManager().byKey(net.minecraft.resources.ResourceLocation.parse("vandorlabs:cockpit")).isPresent(),"canonical recipe");
        actor.setGameMode(GameType.SURVIVAL);
        actor.connection.teleport(START.x,START.y,START.z,180,0);
        actor.getInventory().selected=0;
        var stack=new ItemStack(item);
        actor.setItemInHand(InteractionHand.MAIN_HAND,stack);
        var obstructed=ROOT.offset(1,1,-1);
        level.setBlock(obstructed,Blocks.STONE.defaultBlockState(),3);
        var support=ROOT.below();
        var hit=new BlockHitResult(Vec3.atCenterOf(support).add(0,.5,0),Direction.UP,support,false);
        check(!stack.useOn(new UseOnContext(actor,InteractionHand.MAIN_HAND,hit)).consumesAction()
            &&stack.getCount()==1&&level.isEmptyBlock(ROOT)&&level.getBlockState(obstructed).is(Blocks.STONE),
            "blocked remote corner refuses formation and keeps the real item");
        for(var facing:Direction.Plane.HORIZONTAL){
            var cells=new java.util.HashSet<BlockPos>();
            for(int part=0;part<NativeCockpitGeometry.CELLS;part++)
                check(cells.add(NativeCockpitGeometry.offset(part,facing)),"eight unique reservations in each facing");
            check(cells.contains(BlockPos.ZERO)&&NativeCockpitGeometry.shape(0,facing).bounds().minY==0,
                "fixed anchor and authored floor collision "+facing);
            for(int slot=0;slot<NativeCockpitTouch.MAX_SURFACES;slot++){
                var face=NativeCockpitTouch.face(facing,slot,0);
                check(face!=null&&face.height()>=10&&face.right().dot(face.normal())<1e-5
                    &&face.down().dot(face.normal())<1e-5,"authored Cockpit ray plane "+facing+" slot "+slot);
            }
        }
        level.removeBlock(obstructed,false);
        actor.getInventory().setItem(1,new ItemStack(Items.DIAMOND_PICKAXE));
        actor.getInventory().setItem(2,new ItemStack(com.vandorlabs.neoforge.VandorLabsBootstrap.CONFIGURIZER.get()));
        actor.inventoryMenu.broadcastChanges();
        System.out.println("[vandorlabs][cockpit-frame] setup PASS");
    }

    @SubscribeEvent public static void tick(net.neoforged.neoforge.event.tick.ServerTickEvent.Post event){
        if(!enabled()||player==null||(sawSignal&&sawCenter&&sawIndependent&&sawThrottle))return;
        var level=player.serverLevel();
        if(!sawIndependent&&level.getBlockEntity(ROOT) instanceof NativeChairEntity owner
            &&owner.cockpitTouch().settings(2).activation()==2&&owner.cockpitTouch().controls(6).rows().size()==1){
            var controls=owner.cockpitTouch();
            var display=controls.settings(2);
            var row=controls.controls(6).rows().getFirst();
            check(display.artwork()==NativeCockpitSurfaceEdit.BLACK&&display.channels().contains(DISPLAY_CHANNEL)&&controls.active(2)
                &&controls.settings(6).artwork()==NativeCockpitSurfaceEdit.REDSTONE&&row.label().equals("Sensors")
                &&row.status()&&!controls.usesRow(6,0)&&controls.statusRow(6,0)&&row.channels().contains(EXTRA_CHANNEL)&&controls.controls(0).rows().size()==3&&controls.controls(1).rows().size()==6,
                "real UI keeps seven-surface artwork, activation and rows independent");
            var saved=owner.saveWithoutMetadata(level.registryAccess());
            var copy=new NativeChairEntity(ROOT,owner.getBlockState());
            copy.loadWithComponents(saved,level.registryAccess());
            check(!copy.inert()&&copy.cockpitTouch().settings(2).equals(display)
                &&copy.cockpitTouch().controls(6).rows().getFirst().equals(row),"independent surface configuration reloads");
            sawIndependent=true;
            System.out.println("[vandorlabs][cockpit-frame] independent artwork/rows/receiver/save PASS");
        }
        if(!sawThrottle&&com.vandorlabs.neoforge.redstone.NativeRedstoneChannels.level(level,ROOT,THROTTLE_CHANNEL)>0
            &&level.getBlockEntity(ROOT) instanceof NativeChairEntity seat&&seat.throttleCount()==1){
            var copy=new NativeChairEntity(ROOT,seat.getBlockState());
            copy.loadWithComponents(seat.saveWithoutMetadata(level.registryAccess()),level.registryAccess());
            check(!copy.inert()&&copy.throttleCount()==1&&copy.armrestChannels(NativeCockpitThrottle.LEFT).contains(THROTTLE_CHANNEL)
                &&copy.armrestLevel(NativeCockpitThrottle.LEFT)==seat.armrestLevel(NativeCockpitThrottle.LEFT),
                "physical detent and count/channel settings survive save");
            sawThrottle=true;
            System.out.println("[vandorlabs][cockpit-frame] physical single/dual throttle PASS");
        }
        sawCenter|=com.vandorlabs.neoforge.redstone.NativeRedstoneChannels.level(player.serverLevel(),ROOT,CENTER_CHANNEL)==15;
        sawSignal|=com.vandorlabs.neoforge.redstone.NativeRedstoneChannels.level(player.serverLevel(),ROOT,TOUCH_CHANNEL)==10;
    }

    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent event) {
        if(!enabled()||!(event.getEntity() instanceof ServerPlayer actor))return;
        var level=actor.serverLevel();
        for(int part=0;part<NativeCockpitGeometry.CELLS;part++)
            check(level.isEmptyBlock(ROOT.offset(NativeCockpitGeometry.offset(part,Direction.NORTH))),"mining one proxy clears reservation index "+part);
        var item=NativeSeatBindings.ITEMS.get(NativeSeatKind.COCKPIT);
        var drops=level.getEntitiesOfClass(ItemEntity.class,new AABB(ROOT).inflate(4)).stream()
            .filter(entity->entity.getItem().is(item)).mapToInt(entity->entity.getItem().getCount()).sum();
        for(int i=0;i<actor.getInventory().getContainerSize();i++){
            var stack=actor.getInventory().getItem(i);
            if(stack.is(item))drops+=stack.getCount();
        }
        check(drops==1,"one Cockpit item after teardown from a side");
        check(sawSignal&&sawCenter&&sawIndependent&&sawThrottle&&com.vandorlabs.neoforge.redstone.NativeRedstoneChannels.level(level,ROOT,TOUCH_CHANNEL)==0,
            "seated touch operated actual shared wireless Redstone and teardown withdrew its source");
        System.out.println("[vandorlabs][cockpit-frame] completed PASS");
        System.out.println("[vandorlabs][cockpit-frame] logout PASS");
    }
}
