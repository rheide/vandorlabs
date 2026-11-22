package com.vandorlabs.neoforge.vehicle;

import com.vandorlabs.neoforge.VandorLabsBootstrap;
import com.vandorlabs.neoforge.seating.NativeSeatBindings;
import com.vandorlabs.neoforge.seating.NativeSeatKind;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

@EventBusSubscriber(modid="vandorlabs")
public final class NativeVehiclePlayerFixture {
    public static final BlockPos ROOT=new BlockPos(8,67,8);
    private static NativeGroundVehicle craft;
    private static ServerPlayer actor;
    private static boolean drove,rode,positioned,finished;
    private static Vec3 origin;
    private static BlockPos landing;

    public static boolean enabled() {return Boolean.getBoolean("vandorlabs.vehiclePlayerChecks");}

    public static boolean emergency() { return Boolean.getBoolean("vandorlabs.vehicleEmergencyPark"); }

    @SubscribeEvent public static void login(PlayerEvent.PlayerLoggedInEvent event) {
        if (!enabled() || !(event.getEntity() instanceof ServerPlayer player)) return;
        actor=player;
        var level=player.serverLevel();
        require(level.getRecipeManager().byKey(ResourceLocation.parse("vandorlabs:vehicle_controller")).isPresent(),"Controller crafting recipe");
        var top=Blocks.STONE_SLAB.defaultBlockState().setValue(SlabBlock.TYPE,SlabType.TOP);
        for (int x=-6;x<=8;x++) for(int z=-24;z<=24;z++) {
            level.setBlockAndUpdate(ROOT.offset(x,-2,z),Blocks.STONE.defaultBlockState());
            for(int y=-1;y<=2;y++) level.setBlockAndUpdate(ROOT.offset(x,y,z),Blocks.AIR.defaultBlockState());
        }
        for (int x=0;x<3;x++) for (int z=0;z<2;z++) level.setBlockAndUpdate(ROOT.offset(x,-1,z),top);
        player.setGameMode(GameType.SURVIVAL);
        player.getInventory().clearContent();
        player.teleportTo(ROOT.getX()+1.5,ROOT.getY()-1,ROOT.getZ()-1.7);
        player.setYRot(0);
        place(player,ROOT,new ItemStack(NativeVehicleBindings.CONTROLLER));
        place(player,ROOT.east(),new ItemStack(NativeSeatBindings.ITEMS.get(NativeSeatKind.PILOT)));
        require(level.getBlockEntity(ROOT) instanceof NativeVehicleControllerEntity tile && player.getUUID().equals(tile.ownerId()),"placed Controller records owner");
        require(!level.isEmptyBlock(ROOT.east().above()),"Pilot Seat backrest formed");
        player.getInventory().selected=0;
        player.setItemInHand(InteractionHand.MAIN_HAND,ItemStack.EMPTY);
        player.getInventory().setItem(1,new ItemStack(VandorLabsBootstrap.CONFIGURIZER.get()));
        player.inventoryMenu.broadcastChanges();
    }

    private static void place(ServerPlayer player,BlockPos pos,ItemStack item) {
        player.setItemInHand(InteractionHand.MAIN_HAND,item);
        var hit=new BlockHitResult(Vec3.atBottomCenterOf(pos),Direction.UP,pos.below(),false);
        require(item.useOn(new UseOnContext(player,InteractionHand.MAIN_HAND,hit)).consumesAction(),"normal item placement at "+pos);
    }

    @SubscribeEvent public static void tick(ServerTickEvent.Post event) {
        if (!enabled() || actor==null || finished) return;
        var world=actor.serverLevel();
        if (craft==null) {
            var nearby=world.getEntitiesOfClass(NativeGroundVehicle.class,new AABB(ROOT).inflate(12));
            if (nearby.size()==1 && nearby.get(0).admitted()) {
                craft=nearby.get(0); origin=craft.position();
                require(craft.snapshot().cells().size()==9,"six deck cells, controller and complete Pilot Seat");
            }
            return;
        }
        if (actor.getVehicle()==craft) {
            rode=true;
            if (craft.position().subtract(origin).horizontalDistanceSqr()>=1) drove=true;
        } else if (rode && drove && !positioned && craft.canPark()) {
            require(actor.isAlive() && actor.position().distanceToSqr(craft.position())<36,
                "ordinary dismount lands beside the vehicle within Controller reach");
            landing=NativeVehicleTransfer.anchor(craft); positioned=true;
            if (emergency()) {
                require(!craft.mayEmergencyPark(actor), "Survival owner cannot trigger emergency parking");
                actor.setGameMode(GameType.CREATIVE);
                var foreign = net.neoforged.neoforge.common.util.FakePlayerFactory.get(world,
                    new com.mojang.authlib.GameProfile(new java.util.UUID(0, 8903), "AnotherPilot"));
                foreign.setPos(actor.position());
                foreign.setGameMode(GameType.CREATIVE);
                foreign.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(VandorLabsBootstrap.CONFIGURIZER.get()));
                require(!craft.mayEmergencyPark(foreign), "a different Creative player cannot emergency park the hull");
            }
        }
        if (craft.isRemoved() && positioned && landing!=null) {
            for(BlockPos pos:BlockPos.betweenClosed(landing.offset(-2,-2,-2),landing.offset(2,2,2))) {
                if(world.getBlockEntity(pos) instanceof NativeVehicleControllerEntity tile && actor.getUUID().equals(tile.ownerId())) {
                    finished=true;
                    System.out.println("[vandorlabs][vehicle-player] server PASS saved-owner assembly board drive brake park");
                    break;
                }
            }
        }
    }

    public static void require(boolean value,String detail){if(!value)throw new IllegalStateException("Vehicle player: "+detail);}
    private NativeVehiclePlayerFixture(){}
}
