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
                if(own){ownCalls++;if(fail)throw new IllegalStateException("fixture collision failure");if(nested&&!inside){inside=true;try{var inner=NativeRampRiders.obstacles(world,OTHER,actor,new AABB(BASE.east(3)).inflate(.01));need(inner.isEmpty(),"nested foreign-root suppression while inner iterable consumed: "+inner);need(NativeRampRiders.carrying(world,ROOT)&&!NativeRampRiders.carrying(world,OTHER),"outer scope restored after nested query");}finally{inside=false;nested=false;}}}
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
        // The server runner uses an ordinary random-seed world; isolate only this fixture's volume.
        for(var position:BlockPos.betweenClosed(BASE.offset(-2,-2,-2),BASE.offset(13,5,2)))world.setBlock(position,Blocks.AIR.defaultBlockState(),18);
        for(var entity:world.getEntities((net.minecraft.world.entity.Entity)null,new AABB(BASE.getX()-2,BASE.getY()-2,BASE.getZ()-2,BASE.getX()+14,BASE.getY()+6,BASE.getZ()+3)))entity.discard();
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
        // Real entities, not policy stand-ins: one player correction after authoritative movement.
        actor.setPos(BASE.getX()+.5,BASE.getY()+1,BASE.getZ()+.5);actor.setDeltaMovement(.2,-.3,.4);actor.fallDistance=12;
        var old=actor.position();double feet=actor.getBoundingBox().minY;var packets=new ArrayList<NativeRampMotionPayload>();
        var receipt=NativeRampRiders.move(actor,.125,.25,-.125,.5,(player,packet)->{need(player==actor&&player.getY()==old.y+.25,"correction sent after authoritative movement");packets.add(packet);});
        need(packets.size()==1&&receipt.correction()==packets.getFirst(),"exactly one correction and matching receipt");var packet=packets.getFirst();
        need(packet.x()==old.x&&packet.z()==old.z&&packet.before()==feet-.25&&packet.after()==feet+.25&&packet.sequence()>0,"support anchor and distinct support displacement");
        need(actor.onGround()&&actor.fallDistance==0&&actor.getDeltaMovement().equals(new Vec3(.2,0,.4)),"player fall reset and horizontal momentum retained");
        var buffer=new net.minecraft.network.RegistryFriendlyByteBuf(io.netty.buffer.Unpooled.buffer(),world.registryAccess());
        try{NativeRampMotionPayload.CODEC.encode(buffer,packet);int bytes=buffer.readableBytes();need(bytes==76,"overworld native correction wire size: 76 bytes versus released numeric-dimension 60");need(NativeRampMotionPayload.CODEC.decode(buffer).equals(packet)&&buffer.readableBytes()==0,"actual wire round trip");}finally{buffer.release();}
        for(int length:new int[]{0,56,98363}){var malformed=new net.minecraft.network.RegistryFriendlyByteBuf(io.netty.buffer.Unpooled.buffer(length),world.registryAccess());try{malformed.writeZero(length);boolean rejected=false;try{NativeRampMotionPayload.CODEC.decode(malformed);}catch(RuntimeException expected){rejected=true;}need(rejected,"bounded malformed packet "+length);}finally{malformed.release();}}
        var beforeInvalid=actor.position();try{NativeRampRiders.move(actor,Double.NaN,0,0,(p,q)->{throw new AssertionError("invalid correction sent");});throw new AssertionError("invalid motion accepted");}catch(IllegalArgumentException expected){}need(actor.position().equals(beforeInvalid),"invalid motion rejected before mutation");
        item.setPos(BASE.getX()+.5,BASE.getY()+1,BASE.getZ()+.5);item.noPhysics=false;item.setDeltaMovement(.2,-.3,.4);item.fallDistance=9;
        NativeRampRiders.move(item,.125,.25,0,null);need(item.onGround()&&item.fallDistance==0&&item.getDeltaMovement().equals(new Vec3(.2,0,.4)),"ordinary item transport preserves horizontal momentum");
        NativeRampRiders.moveBelt(item,.125,0,0,null);need(!item.noPhysics&&item.hasImpulse&&item.getDeltaMovement().equals(Vec3.ZERO),"belt item uses explicit tracked teleport and stops toss momentum");
        var floor=new AABB(item.getX()-.5,item.getBoundingBox().minY-.5,item.getZ()-.5,item.getX()+.5,item.getBoundingBox().minY,item.getZ()+.5);NativeRampRiders.supported(item);item.setPos(item.getX(),item.getY()-.0001,item.getZ());
        need(NativeRampRiders.recentCollision(floor,item).maxY==item.getBoundingBox().minY,"recent proven support permits shallow overlap");
        var foreignFloor=floor.move(0,.01,0);need(NativeRampRiders.recentCollision(foreignFloor,item)==foreignFloor,"recent support never clips a different floor height");
        var slabPos=BASE.east(7);world.setBlock(slabPos,Blocks.OAK_SLAB.defaultBlockState(),18);item.setPos(slabPos.getX()+.5,slabPos.getY()+.5,slabPos.getZ()+.5);NativeRampRiders.supported(item);item.setPos(item.getX(),item.getY()-.0001,item.getZ());
        double actualFloor=Double.NEGATIVE_INFINITY;for(var shape:world.getBlockCollisions(item,item.getBoundingBox().inflate(.01)))for(var box:shape.toAabbs())if(box.minX<item.getX()&&box.maxX>item.getX()&&box.minZ<item.getZ()&&box.maxZ>item.getZ())actualFloor=Math.max(actualFloor,box.maxY);
        need(actualFloor==item.getBoundingBox().minY,"actual vanilla materialized slab clips recent proven support floor");
        boat.setPos(BASE.getX()+10.5,BASE.getY()+1,BASE.getZ()+.5);need(world.addFreshEntity(boat),"real support hull added");actor.setPos(boat.getX(),boat.getBoundingBox().maxY,boat.getZ());NativeRampRiders.supported(actor);actor.setPos(actor.getX(),actor.getY()-.0001,actor.getZ());
        var hulls=world.getEntityCollisions(actor,actor.getBoundingBox().inflate(.1));need(!hulls.isEmpty(),"real entity support hull enumerated");for(var hull:hulls)need(hull.max(Direction.Axis.Y)==actor.getBoundingBox().minY,"actual entity hull clips recent proven support floor");boat.discard();
        var plain=List.of(Shapes.block());need(NativeRampRiders.recentCollisions(world,new ItemEntity(world,0,0,0,new ItemStack(Items.STONE)),plain)==plain,"unsupported query retains exact original iterable");
        var memo=new NativeEntityMemo<String>(2);var sameId=new ItemEntity(world,0,0,0,new ItemStack(Items.STONE));sameId.setId(item.getId());memo.put(item,"original");memo.put(sameId,"other");need(memo.get(item).equals("original")&&memo.get(sameId).equals("other"),"weak entity identity does not alias reused numeric IDs");memo.get(item);memo.put(actor,"actor");need(memo.size()==2&&memo.get(sameId)==null&&memo.get(item).equals("original"),"bounded least-recent entry eviction");memo.clearWorld(world);need(memo.size()==0,"world unload removes live weak entries");
        for(int i=0;i<20000;i++)NativeRampRiders.supported(item);
        var allocations=(com.sun.management.ThreadMXBean)java.lang.management.ManagementFactory.getThreadMXBean();long thread=Thread.currentThread().threadId(),start=allocations.getThreadAllocatedBytes(thread);for(int i=0;i<10000;i++)NativeRampRiders.supported(item);long allocated=allocations.getThreadAllocatedBytes(thread)-start;
        need(allocated==0,"warmed support publication allocates no bytes: "+allocated);
        System.out.println("[vandorlabs][ramp-rider-world] transport correction=76B support=10000/"+allocated+"B");
        world.setBlock(BASE.south(),BLOCKS[0].defaultBlockState(),18);world.setBlock(BASE.south().below(),Blocks.STONE.defaultBlockState(),18);
        var first=new ItemEntity(world,BASE.getX()+.5,BASE.getY()+1,BASE.getZ()+.5,new ItemStack(Items.STONE));var second=new ItemEntity(world,BASE.getX()+.5,BASE.getY()+1,BASE.getZ()+1.5,new ItemStack(Items.STONE));for(var rider:List.of(first,second)){rider.setDeltaMovement(.125,0,-.125);need(world.addFreshEntity(rider),"actual planned rider installed");}
        var firstPosition=first.position();var secondPosition=second.position();var cube=List.of(new AABB(0,0,0,1,1,1));var origins=List.of(new NativeRampGeometry.Origin(BASE,cube),new NativeRampGeometry.Origin(BASE.south(),cube));
        var spec=new NativeRampFootprint.Spec(ROOT,Direction.SOUTH,BASE.getY(),BASE.getZ(),2,0,1,0,-2,8,false,0,false,false,0,false,false,origins);
        need(NativeRampRiderPlan.prepare(world,spec,false,0,1,true,false)==null&&first.position().equals(firstPosition)&&second.position().equals(secondPosition),"one obstructed rider prevents every rider movement");
        world.setBlock(BASE.south().below(),Blocks.AIR.defaultBlockState(),18);var changed=NativeRampRiderPlan.prepare(world,spec,false,0,1,true,false);need(changed!=null&&changed.size()==2&&changed.ready(),"both actual world riders prepared before mutation");second.setPos(second.getX()+.1,second.getY(),second.getZ());need(!changed.commit(null)&&first.position().equals(firstPosition),"changed rider refuses entire prepared transport");second.setPos(secondPosition.x,secondPosition.y,secondPosition.z);
        var plan=NativeRampRiderPlan.prepare(world,spec,false,0,1,true,false);need(plan!=null&&plan.size()==2&&plan.recheck(),"fresh complete transport rechecks");world.setBlock(BASE.south().below(),Blocks.STONE.defaultBlockState(),18);need(!plan.recheck()&&first.position().equals(firstPosition)&&second.position().equals(secondPosition),"foreign obstruction inserted after prepare refuses every rider before commit");world.setBlock(BASE.south().below(),Blocks.AIR.defaultBlockState(),18);need(plan.recheck()&&plan.commit(null),"fresh complete transport commits after foreign obstacle cleared");need(first.getY()==BASE.getY()+.33333333333333&&second.getY()<first.getY(),"actual item target matches released first-row 80.33333333333333 reference");need(first.getDeltaMovement().equals(new Vec3(.125,0,-.125))&&first.onGround()&&second.onGround()&&!plan.commit(null),"planned transport preserves momentum and is single use");first.discard();second.discard();
        world.setBlock(BASE.above(),BLOCKS[0].defaultBlockState(),18);var upright=new ItemEntity(world,BASE.getX()+.5,BASE.getY()+1,BASE.getZ()+.5,new ItemStack(Items.STONE));upright.setDeltaMovement(Vec3.ZERO);need(world.addFreshEntity(upright),"actual vertical-layout rider installed");
        var verticalOrigins=List.of(new NativeRampGeometry.Origin(BASE,cube),new NativeRampGeometry.Origin(BASE.above(),cube));var verticalSpec=new NativeRampFootprint.Spec(ROOT,Direction.SOUTH,BASE.getY(),BASE.getY(),2,0,1,0,-2,8,false,0,false,false,0,true,false,verticalOrigins);
        var verticalPlan=NativeRampRiderPlan.prepare(world,verticalSpec,false,0,1,true,false);need(verticalPlan!=null&&verticalPlan.size()==1&&verticalPlan.commit(null),"vertical-layout actual rider commit");need(upright.getY()==BASE.getY()+1&&upright.getZ()<BASE.getZ()+.5&&upright.onGround(),"vertical-layout carries horizontally without moving feet height");upright.discard();
        world.setBlock(BASE.south(),Blocks.AIR.defaultBlockState(),18);var moatPig=net.minecraft.world.entity.EntityType.PIG.create(world);need(moatPig!=null&&moatPig.isPickable()&&!moatPig.canBeCollidedWith(),"native pickable living body maps released ray-collidable predicate, not vehicle hull predicate");moatPig.setPos(BASE.getX()+.5,BASE.getY()+1,BASE.getZ()+.5);moatPig.setDeltaMovement(.125,0,-.125);var loose=new ItemEntity(world,BASE.getX()+.5,BASE.getY()+1,BASE.getZ()+.5,new ItemStack(Items.STONE));loose.setDeltaMovement(Vec3.ZERO);need(world.addFreshEntity(moatPig)&&world.addFreshEntity(loose),"actual Moat living rider and loose item installed");var loosePosition=loose.position();
        var moatSpec=new NativeRampFootprint.Spec(ROOT,Direction.SOUTH,BASE.getY(),BASE.getZ(),2,0,1,0,-2,4,false,0,false,false,0,false,true,verticalOrigins);var moatPlan=NativeRampRiderPlan.prepare(world,moatSpec,false,0,1,true,false);need(moatPlan!=null&&moatPlan.size()==1&&moatPlan.commit(null),"actual Moat carries living rider only");need(Math.abs(moatPig.getY()-(BASE.getY()+1-6D/7D))<1e-10&&Math.abs(moatPig.getZ()-(BASE.getZ()+1.25))<1e-10&&moatPig.onGround()&&loose.position().equals(loosePosition),"actual Moat target matches released 80.14285714285714/1.25 and excludes loose item");moatPig.discard();loose.discard();
        actor.getAbilities().flying=true;need(!NativeRampRiders.eligible(actor),"flying player excluded");actor.getAbilities().flying=false;
    }
    @SubscribeEvent public static void started(ServerStartedEvent event){if(!enabled())return;run(event.getServer().overworld());System.out.println("[vandorlabs][ramp-rider-world] PASS checks="+checks+" real lazy/nested/exception/cold/worker/Player+Item/transport+codec+weak-identity");event.getServer().halt(false);}
}
