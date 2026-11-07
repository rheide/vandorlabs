package com.vandorlabs.neoforge.ramp;

import java.util.*;
import net.minecraft.core.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.*;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.phys.*;
import net.minecraft.world.phys.shapes.*;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStartedEvent;

/** Actual lazy collision consumption, nested/failing scopes and real Player/Item shallow support. */
@EventBusSubscriber(modid="vandorlabs")
public final class NativeRampRiderWorldChecks {
    private static final BlockPos BASE=new BlockPos(28000,90,28000),ROOT=BASE.south(4),OTHER=ROOT.east(4);
    private static final Block[] BLOCKS=new Block[2];private static int checks,ownCalls,foreignCalls;private static boolean nested,inside,fail;private static ServerPlayer actor;
    private static boolean enabled(){return "ramp-rider-world".equals(System.getProperty("vandorlabs.transmitterPhase"));}
    private static void need(boolean yes,String why){checks++;if(!yes)throw new AssertionError("Ramp rider world: "+why);}
    private static final class Probe extends Block {
        private final boolean own;
        Probe(boolean own){super(BlockBehaviour.Properties.of().strength(1).dynamicShape().noOcclusion());this.own=own;}
        @Override protected VoxelShape getCollisionShape(BlockState state,BlockGetter view,BlockPos pos,CollisionContext context){
            if(view instanceof ServerLevel world&&NativeRampRiders.carrying(world,own?ROOT:OTHER)){
                if(own){ownCalls++;if(fail)throw new IllegalStateException("fixture collision failure");if(nested&&!inside){inside=true;try{need(NativeRampRiders.obstacles(world,OTHER,actor,new AABB(BASE.east(3)).inflate(.01)).isEmpty(),"nested foreign-root suppression while inner iterable consumed");need(NativeRampRiders.carrying(world,ROOT)&&!NativeRampRiders.carrying(world,OTHER),"outer scope restored after nested query");}finally{inside=false;nested=false;}}}
                else foreignCalls++;
                return Shapes.empty();
            }
            return Shapes.block();
        }
    }
    @EventBusSubscriber(modid="vandorlabs",bus=EventBusSubscriber.Bus.MOD)public static final class Registration {
        @SubscribeEvent public static void register(net.neoforged.neoforge.registries.RegisterEvent event){if(!enabled())return;event.register(Registries.BLOCK,h->{for(int i=0;i<2;i++){BLOCKS[i]=new Probe(i==0);h.register(ResourceLocation.parse("vandorlabs:ramp_rider_scope_"+i),BLOCKS[i]);}});}
    }
    private static void run(ServerLevel world){
        actor=new net.neoforged.neoforge.common.util.FakePlayer(world,new com.mojang.authlib.GameProfile(new UUID(0,290712),"RampRider"));actor.setGameMode(GameType.SURVIVAL);
        for(int x=-1;x<=1;x++)for(int z=-1;z<=1;z++)world.getChunkAt(BASE.offset(x*16,0,z*16));
        world.setBlock(BASE,BLOCKS[0].defaultBlockState(),18);world.setBlock(BASE.east(3),BLOCKS[1].defaultBlockState(),18);actor.setPos(BASE.getX()+.5,BASE.getY()+1,BASE.getZ()+.5);
        var query=new AABB(BASE.getX()-.1,BASE.getY()-.1,BASE.getZ()-.1,BASE.getX()+4.1,BASE.getY()+2,BASE.getZ()+1.1);nested=true;
        var obstacles=NativeRampRiders.obstacles(world,ROOT,actor,query);
        need(ownCalls>0&&foreignCalls>0,"actual deferred shape calls saw both scoped owners");need(obstacles.contains(new AABB(BASE.east(3)))&&obstacles.stream().noneMatch(b->b.intersects(new AABB(BASE))),"only exact own root excluded; foreign collision remains");need(!NativeRampRiders.carrying(world,ROOT)&&!NativeRampRiders.carrying(world,OTHER),"scope retired after materialization");
        boolean ordinary=false;for(var shape:world.getBlockCollisions(actor,new AABB(BASE).inflate(.01)))for(var b:shape.toAabbs())ordinary|=b.intersects(new AABB(BASE));need(ordinary,"ordinary collision restored after query");
        fail=true;try{NativeRampRiders.obstacles(world,ROOT,actor,query);throw new AssertionError("missing query failure");}catch(IllegalStateException expected){need(expected.getMessage().equals("fixture collision failure"),"actual lazy failure propagated");}finally{fail=false;}need(!NativeRampRiders.carrying(world,ROOT),"exception retires scope");
        var cold=new BlockPos(1000000,90,1000000);need(world.getChunkSource().getChunkNow(cold.getX()>>4,cold.getZ()>>4)==null,"cold query fixture");try{NativeRampRiders.obstacles(world,ROOT,actor,new AABB(cold));throw new AssertionError("cold query accepted");}catch(IllegalArgumentException expected){}need(world.getChunkSource().getChunkNow(cold.getX()>>4,cold.getZ()>>4)==null,"cold query does not force chunks");
        boolean refused=java.util.concurrent.CompletableFuture.supplyAsync(()->{try{NativeRampRiders.obstacles(world,ROOT,actor,query);return false;}catch(IllegalStateException expected){return true;}}).join();need(refused,"worker refused before traversal");
        var item=new ItemEntity(world,BASE.getX()+.5,BASE.getY()+1,BASE.getZ()+.5,new ItemStack(Items.STONE));
        for(var entity:List.of(actor,item)){entity.setDeltaMovement(0,.1,0);double feet=entity.getBoundingBox().minY;var shallow=new AABB(BASE.getX(),feet-.5,BASE.getZ(),BASE.getX()+1,feet+.5,BASE.getZ()+1);need(NativeRampRiders.eligible(entity)&&NativeRampRiders.supportCollision(shallow,entity,true).maxY==feet,"real player/item shallow support at boundary");need(NativeRampRiders.supportCollision(shallow,entity,false)==shallow,"stationary support unchanged");entity.setDeltaMovement(0,Math.nextUp(.1),0);need(NativeRampRiders.supportCollision(shallow,entity,true)==shallow,"jumping support remains solid");entity.setDeltaMovement(Vec3.ZERO);entity.noPhysics=true;need(!NativeRampRiders.eligible(entity)&&NativeRampRiders.supportCollision(shallow,entity,true)==shallow,"no-physics entity excluded");entity.noPhysics=false;}
        var boat=new net.minecraft.world.entity.vehicle.Boat(world,BASE.getX(),BASE.getY()+1,BASE.getZ());var cart=new net.minecraft.world.entity.vehicle.Minecart(world,BASE.getX(),BASE.getY()+1,BASE.getZ());for(var entity:List.of(boat,cart)){entity.setDeltaMovement(Vec3.ZERO);need(NativeRampRiders.eligible(entity),"vanilla boat/cart not blanket excluded from parent rider policy");}
        actor.getAbilities().flying=true;need(!NativeRampRiders.eligible(actor),"flying player excluded");actor.getAbilities().flying=false;
    }
    @SubscribeEvent public static void started(ServerStartedEvent event){if(!enabled())return;run(event.getServer().overworld());System.out.println("[vandorlabs][ramp-rider-world] PASS checks="+checks+" real lazy/nested/exception/cold/worker/Player+Item; transport still pending");event.getServer().halt(false);}
}
