package com.vandorlabs.neoforge.fluid;

import com.mojang.authlib.GameProfile;
import com.vandorlabs.neoforge.shape.ProgrammableShapeEntity;
import com.vandorlabs.neoforge.shape.ShapeRegistration;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.PacketSendListener;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.fluids.FluidStack;

/** Disposable real native entity, bucket, opaque storage and restart checks for partial fluid hosts. */
@net.neoforged.fml.common.EventBusSubscriber(modid="vandorlabs")
public final class NativeFluidRuntimeChecks {
    private record FixturePlayer(double scale,ItemStack seventh,ItemStack eighth){}
    private static final java.util.Map<ServerLevel,java.util.Map<UUID,FixturePlayer>> CLIENT_LEVELS=new java.util.WeakHashMap<>();
    @net.neoforged.bus.api.SubscribeEvent public static void clientScale(net.neoforged.neoforge.event.tick.LevelTickEvent.Post event){if(!(event.getLevel() instanceof ServerLevel level))return;var saved=CLIENT_LEVELS.get(level);if(saved==null)return;for(ServerPlayer player:level.players()){var scale=player.getAttribute(Attributes.SCALE);boolean inside=player.getX()>=276&&player.getX()<=300&&player.getZ()>=-5&&player.getZ()<=15;if(inside){if(!saved.containsKey(player.getUUID())){saved.put(player.getUUID(),new FixturePlayer(scale.getBaseValue(),player.getInventory().getItem(7).copy(),player.getInventory().getItem(8).copy()));player.getInventory().setItem(7,new ItemStack(Items.BUCKET));player.getInventory().setItem(8,new ItemStack(Items.WATER_BUCKET));}if(scale.getBaseValue()!=.25)scale.setBaseValue(.25);}else if(saved.containsKey(player.getUUID())){var before=saved.remove(player.getUUID());scale.setBaseValue(before.scale);player.getInventory().setItem(7,before.seventh);player.getInventory().setItem(8,before.eighth);}}}
    private static final BlockPos BOUNDARY_A=new BlockPos(296,80,8),BOUNDARY_B=BOUNDARY_A.east(),DIAGONAL=BOUNDARY_A.offset(-1,0,-1);
    private static final class BoundaryCycle {int stage;long next;CompoundTag opaque;boolean drained,restored;}
    private static final java.util.Map<ServerLevel,BoundaryCycle> BOUNDARY_CYCLES=new java.util.WeakHashMap<>();
    @net.neoforged.bus.api.SubscribeEvent public static void boundaryCycle(net.neoforged.neoforge.event.tick.LevelTickEvent.Post event){
        if(!(event.getLevel() instanceof ServerLevel level))return;var cycle=BOUNDARY_CYCLES.get(level);if(cycle==null||cycle.stage==12)return;
        if(cycle.stage==0){var baseline=level.getBlockEntity(BOUNDARY_B);if(baseline instanceof ProgrammableShapeEntity owner){if(!owner.containedFluids().occupied(0))cycle.drained=true;else if(cycle.drained&&owner.containedFluids().get(0).is(Fluids.WATER))cycle.restored=true;}if(!cycle.restored)return;if(level.players().stream().noneMatch(player->player.getInventory().selected==8&&Math.abs(player.getY()-82)<.25&&player.getX()>298.5&&player.getX()<300&&player.getZ()>4&&player.getZ()<6))return;
            var owner=(ProgrammableShapeEntity)level.getBlockEntity(BOUNDARY_B);need(owner!=null&&owner.containedFluids().get(0).is(Fluids.WATER),"two-host cycle starts only after real bucket restore");
            var root=owner.saveWithoutMetadata(level.registryAccess());var data=new CompoundTag();cycle.opaque=unknown();data.put("Side0",cycle.opaque.copy());root.put(NativeContainedFluids.KEY,data);owner.loadWithComponents(root,level.registryAccess());owner.containedFluidsChanged();NativeFluidWorld.queue(owner);cycle.stage=1;cycle.next=level.getGameTime()+80;return;
        }
        if(level.getGameTime()<cycle.next)return;
        if(cycle.stage==1){var owner=(ProgrammableShapeEntity)level.getBlockEntity(BOUNDARY_B);need(owner!=null&&cycle.opaque.equals(owner.containedFluids().raw(0)),"unknown occupied fixture retains exact original compound");owner.containedFluids().set(0,new FluidStack(Fluids.LAVA,1000));}
        else if(cycle.stage==2){((ProgrammableShapeEntity)level.getBlockEntity(BOUNDARY_B)).containedFluids().set(0,new FluidStack(Fluids.WATER,1000));}
        else if(cycle.stage==3){for(var face:java.util.List.of(Direction.NORTH,Direction.SOUTH,Direction.EAST))level.setBlock(BOUNDARY_B.relative(face),Blocks.STONE.defaultBlockState(),3);level.setBlock(BOUNDARY_B,Blocks.WATER.defaultBlockState(),3);}
        else if(cycle.stage==4||cycle.stage==6){level.setBlock(BOUNDARY_B,Blocks.AIR.defaultBlockState(),3);}
        else if(cycle.stage==5||cycle.stage==7){var owner=install(level,BOUNDARY_B,ShapeRegistration.SLAB.get().defaultBlockState());owner.containedFluids().set(0,new FluidStack(Fluids.WATER,1000));if(cycle.stage==7)for(var face:java.util.List.of(Direction.NORTH,Direction.SOUTH,Direction.EAST))level.setBlock(BOUNDARY_B.relative(face),Blocks.AIR.defaultBlockState(),3);}
        else if(cycle.stage==8){level.setBlock(DIAGONAL,Blocks.AIR.defaultBlockState(),3);for(var face:Direction.Plane.HORIZONTAL)level.setBlock(DIAGONAL.relative(face),Blocks.STONE.defaultBlockState(),3);}
        else if(cycle.stage==9){level.setBlock(DIAGONAL,Blocks.WATER.defaultBlockState(),3);}
        else if(cycle.stage==10){level.setBlock(DIAGONAL,Blocks.AIR.defaultBlockState(),3);}
        else{for(var face:Direction.Plane.HORIZONTAL)level.setBlock(DIAGONAL.relative(face),Blocks.AIR.defaultBlockState(),3);}
        cycle.stage++;cycle.next=level.getGameTime()+80;
    }
    private static final BlockPos PRIMARY=new BlockPos(280,80,8),SECOND=PRIMARY.east(),PLACED=new BlockPos(292,80,8);
    private NativeFluidRuntimeChecks(){}
    private static void need(boolean value,String message){if(!value)throw new IllegalStateException("Native contained fluids: "+message);}
    private static ProgrammableShapeEntity install(ServerLevel level,BlockPos pos,BlockState state){level.getChunkAt(pos);level.setBlock(pos,Blocks.AIR.defaultBlockState(),3);level.setBlock(pos.below(),Blocks.STONE.defaultBlockState(),3);level.setBlock(pos,state,3);var tile=(ProgrammableShapeEntity)level.getBlockEntity(pos);need(tile!=null,"installed host");return tile;}
    private static ServerPlayer player(ServerLevel level){var profile=new GameProfile(UUID.fromString("e390865f-1710-42e2-b574-29627c835ca2"),"ContainedFluidProbe");var player=new ServerPlayer(level.getServer(),level,profile,ClientInformation.createDefault());player.connection=new ServerGamePacketListenerImpl(level.getServer(),new Connection(PacketFlow.SERVERBOUND),player,CommonListenerCookie.createInitial(profile,false)){
        @Override public void send(Packet<?> packet){}
        @Override public void send(Packet<?> packet,PacketSendListener callback){}
        @Override public void tick(){}
    };player.setGameMode(GameType.SURVIVAL);player.setNoGravity(true);player.getAttribute(Attributes.SCALE).setBaseValue(.25);player.refreshDimensions();return player;}
    private static PlayerInteractEvent.RightClickBlock bucket(ServerPlayer player,BlockPos pos,ItemStack held){player.setItemInHand(InteractionHand.MAIN_HAND,held);var hit=new BlockHitResult(new Vec3(pos.getX()+.5,pos.getY()+.5,pos.getZ()),Direction.NORTH,pos,false);var event=new PlayerInteractEvent.RightClickBlock(player,InteractionHand.MAIN_HAND,pos,hit);NeoForge.EVENT_BUS.post(event);return event;}
    private static CompoundTag unknown(){var raw=new CompoundTag();raw.putString("id","missing_test:opaque_fluid");raw.putInt("amount",1000);var components=new CompoundTag();components.putString("missing_test:opaque_component","retain-exactly");raw.put("components",components);var extension=new CompoundTag();extension.putIntArray("foreign",new int[]{1,19,-7});raw.put("FutureFluidExtension",extension);return raw;}
    public static void run(ServerLevel level){
        var first=install(level,PRIMARY,ShapeRegistration.SLAB.get().defaultBlockState());var second=install(level,SECOND,ShapeRegistration.SLAB.get().defaultBlockState());
        var player=player(level);player.setPos(PRIMARY.getX()+.5,83,PRIMARY.getZ()+.5);for(int i=0;i<70;i++){player.tick();player.baseTick();}
        need(!player.isInvulnerableTo(level.damageSources().lava()),"probe must be actual vulnerable ServerPlayer, not FakePlayer");
        var event=bucket(player,PRIMARY,new ItemStack(Items.WATER_BUCKET));need(event.isCanceled()&&event.getCancellationResult()==InteractionResult.SUCCESS&&first.containedFluids().get(0).is(Fluids.WATER)&&player.getMainHandItem().is(Items.BUCKET),"actual bucket event fills1000mB and exchanges survival container");
        var before=first.containedFluids().raw(0);event=bucket(player,PRIMARY,new ItemStack(Items.LAVA_BUCKET));need(event.getCancellationResult()==InteractionResult.FAIL&&before.equals(first.containedFluids().raw(0))&&player.getMainHandItem().is(Items.LAVA_BUCKET),"occupied fluid slot rejects replacement without consuming held bucket");
        event=bucket(player,PRIMARY,new ItemStack(Items.BUCKET));need(event.getCancellationResult()==InteractionResult.SUCCESS&&!first.containedFluids().occupied(0)&&player.getMainHandItem().is(Items.WATER_BUCKET),"actual drain event restores original full bucket");
        var malformed=first.saveWithoutMetadata(level.registryAccess());var data=new CompoundTag();data.put("Side1",unknown());malformed.put(NativeContainedFluids.KEY,data);first.loadWithComponents(malformed,level.registryAccess());need(first.containedFluids().occupied(1)&&first.containedFluids().get(1).isEmpty(),"unresolved fluid/component remains occupied and inert");var roundtrip=first.saveWithoutMetadata(level.registryAccess());need(roundtrip.getCompound(NativeContainedFluids.KEY).getCompound("Side1").equals(unknown()),"unresolved compound survives losslessly");
        long revision=first.containedFluids().revision();for(int side:new int[]{-1,2})try{first.containedFluids().set(side,new FluidStack(Fluids.WATER,1000));throw new IllegalStateException("invalid side accepted");}catch(IllegalArgumentException expected){}need(first.containedFluids().revision()==revision,"invalid side must not mutate state");
        var legacy=new CompoundTag();legacy.putString("FluidName","water");legacy.putInt("Amount",1000);var legacyTag=new CompoundTag();legacyTag.putString("sample","legacy-opaque");legacyTag.putIntArray("values",new int[]{42,7});legacy.put("Tag",legacyTag);data.put("Side0",legacy);malformed.put(NativeContainedFluids.KEY,data);first.loadWithComponents(malformed,level.registryAccess());need(first.containedFluids().get(0).is(Fluids.WATER)&&first.containedFluids().get(0).get(DataComponents.CUSTOM_DATA).copyTag().equals(legacyTag)&&first.containedFluids().raw(0).equals(legacy),"legacy full fluid tag projected without rewriting its stored identity");
        ItemStack picked=first.getBlockState().getBlock().getCloneItemStack(first.getBlockState(),new BlockHitResult(Vec3.atCenterOf(PRIMARY),Direction.UP,PRIMARY,false),level,PRIMARY,player);need(picked.get(DataComponents.BLOCK_ENTITY_DATA).copyTag().getCompound(NativeContainedFluids.KEY).equals(data),"configured pick retains both opaque fluid slots");
        level.getChunkAt(PLACED);level.setBlock(PLACED,Blocks.WATER.defaultBlockState(),3);level.setBlock(PLACED.below(),Blocks.STONE.defaultBlockState(),3);player.setPos(PLACED.getX()+.5,80,5);player.getAttribute(Attributes.SCALE).setBaseValue(1);player.refreshDimensions();player.setItemInHand(InteractionHand.MAIN_HAND,picked);
        var placed=picked.useOn(new net.minecraft.world.item.context.UseOnContext(player,InteractionHand.MAIN_HAND,new BlockHitResult(Vec3.atCenterOf(PLACED.below()).add(0,.5,0),Direction.UP,PLACED.below(),false)));need(placed.consumesAction()&&level.getBlockEntity(PLACED) instanceof ProgrammableShapeEntity,"actual configured item placement into preexisting water");var restored=(ProgrammableShapeEntity)level.getBlockEntity(PLACED);need(restored.containedFluids().raw(0).equals(legacy)&&restored.containedFluids().raw(1).equals(unknown()),"placement capture cannot overwrite stored or unresolved occupied slots");level.setBlock(PLACED,Blocks.AIR.defaultBlockState(),3);
        // A plain placed slab captures the replaced fluid through the real entity-place event path.
        level.setBlock(PLACED,Blocks.LAVA.defaultBlockState(),3);player.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(ShapeRegistration.SLAB_ITEM.get()));placed=player.getMainHandItem().useOn(new net.minecraft.world.item.context.UseOnContext(player,InteractionHand.MAIN_HAND,new BlockHitResult(Vec3.atCenterOf(PLACED.below()).add(0,.5,0),Direction.UP,PLACED.below(),false)));need(placed.consumesAction()&&level.getBlockEntity(PLACED) instanceof ProgrammableShapeEntity,"actual plain slab placement in lava");restored=(ProgrammableShapeEntity)level.getBlockEntity(PLACED);need(restored.containedFluids().get(0).is(Fluids.LAVA)&&restored.containedFluids().get(0).getAmount()==1000,"placement snapshot preserves replaced lava");level.setBlock(PLACED,Blocks.AIR.defaultBlockState(),3);
        player.getAttribute(Attributes.SCALE).setBaseValue(.25);player.refreshDimensions();player.setItemInHand(InteractionHand.MAIN_HAND,ItemStack.EMPTY);first.containedFluids().set(0,new FluidStack(Fluids.WATER,1000));second.containedFluids().set(0,FluidStack.EMPTY);
        player.setPos(PRIMARY.getX()+.5,80.55,8.5);player.setAirSupply(300);player.setRemainingFireTicks(100);player.baseTick();need(player.isInWater()&&player.isEyeInFluid(FluidTags.WATER)&&player.getEyeInFluidType()==Fluids.WATER.getFluidType()&&player.getFluidHeight(FluidTags.WATER)>0,"native body/eye/legacy tag queries consume partial water cell");need(player.canStartSwimming(),"native canStartSwimming uses actual contained feet and eye fluid types");player.setSprinting(true);player.updateSwimming();need(player.isSwimming(),"ordinary native updateSwimming enters swimming");player.setSprinting(false);player.updateSwimming();need(!player.isSwimming(),"ordinary native updateSwimming exits swimming");
        need(player.getAirSupply()<300&&player.getRemainingFireTicks()<=0,"native drowning budget and extinguishing occur through ordinary baseTick");
        player.setPos(PRIMARY.getX()+.5,80.02,8.5);int air=player.getAirSupply();player.baseTick();need(!player.isInWater()&&!player.isEyeInFluid(FluidTags.WATER)&&player.getAirSupply()>air,"solid half and dry eye clear native immersion and recover air");
        player.setPos(PRIMARY.getX()+.5,80.55,8.5);player.setAirSupply(0);player.setHealth(20);player.invulnerableTime=0;for(int i=0;i<20;i++){player.tick();player.baseTick();}need(player.getHealth()<20,"ordinary native drowning damage after exhausted air budget");
        first.containedFluids().set(0,new FluidStack(Fluids.LAVA,1000));need(NativeFluidWorld.light(level,PRIMARY)==15&&first.getBlockState().getLightEmission(level,PRIMARY)==15,"auxiliary manager exposes lava light without BE reads");player.setHealth(20);player.invulnerableTime=0;player.baseTick();need(player.isInLava()&&player.isEyeInFluid(FluidTags.LAVA)&&player.getHealth()<20,"native lava body/eyes and actual damage");
        first.containedFluids().set(0,new FluidStack(Fluids.WATER,1000));second.containedFluids().set(0,new FluidStack(Fluids.LAVA,1000));player.setPos(PRIMARY.getX()+1,80.55,8.5);player.baseTick();need(player.getFluidTypeHeight(Fluids.WATER.getFluidType())>0&&player.getFluidTypeHeight(Fluids.LAVA.getFluidType())>0&&player.isEyeInFluid(FluidTags.LAVA),"two simultaneous fluid types retain separate body heights and half-open eye owner");
        first.containedFluids().set(0,FluidStack.EMPTY);second.containedFluids().set(0,FluidStack.EMPTY);player.baseTick();need(!player.isInWater()&&!player.isInLava()&&!player.isEyeInFluid(FluidTags.WATER)&&!player.isEyeInFluid(FluidTags.LAVA),"draining clears all stale immersion types");
        var tagged=new FluidStack(Fluids.WATER,1000);tagged.set(DataComponents.CUSTOM_DATA,CustomData.of(legacyTag.copy()));first.containedFluids().set(0,tagged);second.containedFluids().set(0,new FluidStack(Fluids.LAVA,1000));player.containerMenu=player.inventoryMenu;player.discard();
        System.out.println("[vandorlabs][modern] contained-fluids-write PASS real ServerPlayer water/air/drowning/lava lifecycle, two native fluid types, bucket exchange, placement capture, opaque components, legacy tags, configured items and auxiliary light");
    }
    public static void prepareRead(ServerLevel level){for(var pos:java.util.List.of(PRIMARY,SECOND)){level.getChunkAt(pos);need(level.getBlockEntity(pos) instanceof ProgrammableShapeEntity,"saved fluid host");}}
    public static void read(ServerLevel level){var first=(ProgrammableShapeEntity)level.getBlockEntity(PRIMARY);var second=(ProgrammableShapeEntity)level.getBlockEntity(SECOND);need(first.containedFluids().get(0).is(Fluids.WATER)&&first.containedFluids().get(0).get(DataComponents.CUSTOM_DATA).copyTag().getString("sample").equals("legacy-opaque")&&first.containedFluids().raw(1).equals(unknown()),"registry components and unknown occupied slot survive process restart");need(second.containedFluids().get(0).is(Fluids.LAVA)&&NativeFluidWorld.light(level,SECOND)==15,"deferred lava light restored");System.out.println("[vandorlabs][modern] contained-fluids-read PASS opaque/native/legacy fluid data and deferred auxiliary lava light across process restart");}
    public static void clientFixture(ServerLevel level){BOUNDARY_CYCLES.put(level,new BoundaryCycle());level.setBlock(DIAGONAL,Blocks.AIR.defaultBlockState(),3);for(var face:Direction.Plane.HORIZONTAL)level.setBlock(DIAGONAL.relative(face),Blocks.AIR.defaultBlockState(),3);for(var face:java.util.List.of(Direction.NORTH,Direction.SOUTH,Direction.EAST))level.setBlock(BOUNDARY_B.relative(face),Blocks.AIR.defaultBlockState(),3);for(ServerPlayer player:level.players())player.getAttribute(Attributes.SCALE).setBaseValue(1);CLIENT_LEVELS.put(level,new java.util.HashMap<>());for(int x=272;x<=286;x++)for(int z=1;z<=15;z++){var pos=new BlockPos(x,80,z);if(level.getBlockState(pos).getBlock() instanceof net.minecraft.world.level.block.LiquidBlock&&level.getFluidState(pos).is(FluidTags.WATER))level.setBlock(pos,Blocks.AIR.defaultBlockState(),3);}for(var pos:java.util.List.of(PRIMARY.west(2),PRIMARY.west().north(),PRIMARY.west().south()))level.setBlock(pos,Blocks.BARRIER.defaultBlockState(),3);for(int x=253;x<=300;x++)for(int z=-5;z<=15;z++)level.setBlock(new BlockPos(x,79,z),Blocks.SMOOTH_STONE.defaultBlockState(),3);var water=install(level,PRIMARY,ShapeRegistration.SLAB.get().defaultBlockState());water.containedFluids().set(0,new FluidStack(Fluids.WATER,1000));level.setBlock(PRIMARY.west(),Blocks.WATER.defaultBlockState(),3);var lava=install(level,new BlockPos(284,80,8),ShapeRegistration.SLAB.get().defaultBlockState().setValue(com.vandorlabs.neoforge.shape.ProgrammableSlabBlock.HALF,Half.TOP));lava.containedFluids().set(0,new FluidStack(Fluids.LAVA,1000));var stairs=install(level,new BlockPos(288,80,8),ShapeRegistration.STAIRS.get().defaultBlockState());stairs.containedFluids().set(0,new FluidStack(Fluids.WATER,1000));for(var pos:java.util.List.of(BOUNDARY_A,BOUNDARY_B)){var owner=install(level,pos,ShapeRegistration.SLAB.get().defaultBlockState());owner.containedFluids().set(0,new FluidStack(Fluids.WATER,1000));}}
}
