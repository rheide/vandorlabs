package com.vandorlabs.neoforge.gear;

import java.util.*;
import net.minecraft.core.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.game.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.*;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** Only supplies inventory/camera setup and checks authoritative results of real client interactions. */
@EventBusSubscriber(modid="vandorlabs")
public final class NativeGearStowedFootClientFixture {
    public static final BlockPos ROOT=new BlockPos(0,99,0),FOOT=ROOT.below(2),ACK=new BlockPos(8,96,4);
    private static UUID actorId,ownerId;private static int stage,checks,waiting;private static boolean failed;
    public static boolean enabled(){return Boolean.getBoolean("vandorlabs.gearStowedFootClient");}
    public record Mode(int operation) implements CustomPacketPayload {public static final Type<Mode> TYPE=new Type<>(ResourceLocation.parse("vandorlabs:gear_stowed_foot_fixture"));public Type<? extends CustomPacketPayload> type(){return TYPE;}}
    @EventBusSubscriber(modid="vandorlabs",bus=EventBusSubscriber.Bus.MOD)public static final class Packets {
        @SubscribeEvent public static void register(net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent event){if(enabled())event.registrar("1").playToServer(Mode.TYPE,new StreamCodec<RegistryFriendlyByteBuf,Mode>(){
            public Mode decode(RegistryFriendlyByteBuf b){if(b.readableBytes()!=1)throw new IllegalArgumentException();return new Mode(b.readUnsignedByte());}
            public void encode(RegistryFriendlyByteBuf b,Mode mode){b.writeByte(mode.operation);}
        },(mode,context)->context.enqueueWork(()->{if(context.player() instanceof ServerPlayer actor)try{apply(actor,mode.operation);}catch(Throwable failure){fail(actor.serverLevel().getServer(),failure);}}));}
    }
    private static void need(boolean ok,String why){checks++;if(!ok)throw new AssertionError("Native stopped Ski client server stage="+stage+": "+why);}
    private static NativeGearEntity root(ServerPlayer actor){var root=NativeGearFormation.at(actor.level(),ROOT);need(root!=null&&!root.inert()&&root.controller(),"healthy installed primary");if(ownerId!=null)need(ownerId.equals(root.identity()),"same original owner identity");return root;}
    private static void held(ServerPlayer actor,ItemStack stack){actor.getInventory().selected=0;actor.setItemInHand(InteractionHand.MAIN_HAND,stack);actor.setItemInHand(InteractionHand.OFF_HAND,ItemStack.EMPTY);actor.connection.send(new ClientboundSetCarriedItemPacket(0));actor.connection.send(new ClientboundContainerSetSlotPacket(-2,0,0,stack.copy()));actor.inventoryMenu.broadcastChanges();}
    private static void ack(ServerPlayer actor,int operation){actor.serverLevel().setBlock(ACK.east(operation),Blocks.STONE.defaultBlockState(),3);stage++;}
    @SubscribeEvent public static void login(PlayerEvent.PlayerLoggedInEvent event){if(!enabled()||!(event.getEntity() instanceof ServerPlayer actor))return;actorId=actor.getUUID();var world=actor.serverLevel();for(int x=-7;x<=14;x++)for(int z=-6;z<=6;z++){world.setBlock(new BlockPos(x,95,z),Blocks.STONE.defaultBlockState(),3);for(int y=96;y<=104;y++)world.setBlock(new BlockPos(x,y,z),Blocks.AIR.defaultBlockState(),3);}world.setBlock(ROOT.above(),Blocks.STONE.defaultBlockState(),3);actor.setGameMode(GameType.SURVIVAL);actor.getInventory().clearContent();var stack=new ItemStack(NativeGearBindings.ITEMS.get(NativeGearDefinition.ROTATING.id),2);var choices=NativeGearSettings.defaults().choices();choices.putBoolean("RotatePayload",true);choices.putInt("Direction",0);var data=new CompoundTag();data.put(NativeGearItem.SETTINGS,choices);stack.set(DataComponents.CUSTOM_DATA,CustomData.of(data));held(actor,stack);actor.connection.teleport(.5,96,-3,0,0);System.out.println("[vandorlabs][gear-stowed-foot-client] setup PASS");}
    private static void view(ServerPlayer actor,Direction face,boolean original){var owner=root(actor);var boxes=owner.interactionBoxes(0);var sources=owner.interactionSources();
        for(int i=0;i<boxes.size();i++){var part=NativeGearFormation.at(actor.level(),sources.get(i));if(part==null||part.definition()!=NativeGearDefinition.SKI||original&&!sources.get(i).equals(FOOT))continue;var box=boxes.get(i);var target=new Vec3(face==Direction.EAST?box.maxX:face==Direction.WEST?box.minX:(box.minX+box.maxX)/2,face==Direction.DOWN?box.minY:(box.minY+box.maxY)/2,(box.minZ+box.maxZ)/2);
            for(double tangent:original?new double[]{0,-1.5,1.5}:new double[]{1.5,-1.5,0}){var eye=new Vec3(target.x+3*face.getStepX()+(face.getAxis()==Direction.Axis.Y?tangent:0),96+actor.getEyeHeight(),target.z+(face.getAxis()==Direction.Axis.Y?0:tangent));var look=target.subtract(eye).normalize();float yaw=(float)Math.toDegrees(Math.atan2(-look.x,look.z)),pitch=(float)-Math.toDegrees(Math.asin(look.y));actor.setPos(eye.x,96,eye.z);actor.setYRot(yaw);actor.setXRot(pitch);var hit=NativeGearMechanismTrace.nearest(actor.level(),actor.getEyePosition(),actor.getEyePosition().add(actor.getViewVector(1).scale(actor.blockInteractionRange())),NativeGearEntity::progress);if(hit==null||hit.owner()!=owner||!hit.source().equals(FOOT)||hit.worldFace()!=face)continue;
                var vanilla=actor.level().clip(new net.minecraft.world.level.ClipContext(actor.getEyePosition(),actor.getEyePosition().add(actor.getViewVector(1).scale(actor.blockInteractionRange())),net.minecraft.world.level.ClipContext.Block.OUTLINE,net.minecraft.world.level.ClipContext.Fluid.NONE,actor));if(vanilla.getType()!=HitResult.Type.MISS&&actor.getEyePosition().distanceToSqr(vanilla.getLocation())+1e-8<hit.distanceSq()){var member=NativeGearFormation.at(actor.level(),vanilla.getBlockPos());if(member==null||member.controllerEntity()!=owner)continue;}actor.connection.teleport(actor.getX(),96,actor.getZ(),yaw,pitch);return;
            }
        }throw new AssertionError("No clear grounded current-pose Ski ray crouching="+actor.isCrouching());
    }
    private static void floorClearance(ServerPlayer actor,NativeGearEntity owner){
        String oldHit=null;
        outer:for(int step=1;step<=NativeGearEntity.DURATION;step++)for(var current:NativeGearGeometry.mechanismBoxes(owner,1-step/(double)NativeGearEntity.DURATION)){
            var old=current.move(0,-1,0).deflate(.0001);
            for(var at:BlockPos.betweenClosed(BlockPos.containing(old.minX,old.minY,old.minZ),BlockPos.containing(old.maxX,old.maxY,old.maxZ)))if(at.getY()==95&&actor.serverLevel().getBlockState(at).is(Blocks.STONE)&&old.intersects(new AABB(at))){oldHit=at+" bounds="+old;break outer;}
        }
        need(oldHit!=null,"original flush-floor placement is a real stroke obstruction");
        need(NativeGearTargets.clearPath(owner,1,0),"raised assembly has a clear whole stroke with floor and mounting support retained");
        System.out.println("[vandorlabs][gear-stowed-foot-client] floor-clearance PASS originalLowPlacement="+oldHit+" currentRootY="+ROOT.getY()+" retainedFloorY95");
    }
    private static void apply(ServerPlayer actor,int operation){need(actor.getUUID().equals(actorId)&&operation==stage&&waiting==0,"exact actor/operation "+operation);var root=root(actor);switch(operation){
        case 0->{ownerId=root.identity();need(root.assembly().columnHeight()==2&&actor.getMainHandItem().isEmpty(),"two real Survival arm placements");held(actor,new ItemStack(NativeGearBindings.ITEMS.get(NativeGearDefinition.SKI.id)));}
        case 1->{need(root.assembly().kind()==com.vandorlabs.gear.core.GearTopology.FootKind.SKI&&root.assembly().feet().size()==1&&root.members().contains(FOOT)&&root.progress()==1&&actor.getMainHandItem().isEmpty(),"real existing native Ski placement");floorClearance(actor,root);held(actor,ItemStack.EMPTY);}
        case 2->{need(root.progress()==0&&!root.target()&&!root.blocked(),"actual stopped target reached");held(actor,new ItemStack(NativeGearBindings.ITEMS.get(NativeGearDefinition.SKI.id),2));actor.connection.teleport(3.5,96,.5,90,0);}
        case 3->{waiting=1;return;}
        case 4->{need(root.progress()==0&&!root.target()&&root.assembly().feet().size()==2&&actor.getMainHandItem().getCount()==1,"actual input consumes one and stays stopped");for(var at:root.members()){var member=NativeGearFormation.at(actor.level(),at);need(member!=null&&member.mount()==Direction.DOWN&&member.controllerEntity()==root,"exact native member owner/frame");}waiting=2;System.out.println("[vandorlabs][gear-stowed-foot-client] append PASS checks="+checks+" actualShiftUse/oneItem/samePhase0/nativeOwner");return;}
        case 5->{need(root.progress()==0&&!root.blocked()&&root.assembly().feet().size()==3&&actor.getMainHandItem().isEmpty(),"actual ordinary placement C2S fallback consumes the remaining one Ski");held(actor,ItemStack.EMPTY);System.out.println("[vandorlabs][gear-stowed-foot-client] ordinary PASS realUseItemOnC2S/currentPosedServerRetrace/oneItem/samePhase0");var boxes=root.interactionBoxes(0);var sources=root.interactionSources();AABB shown=null;for(int i=0;i<boxes.size();i++){var part=NativeGearFormation.at(actor.level(),sources.get(i));if(part!=null&&part.definition()==NativeGearDefinition.SKI)shown=shown==null?boxes.get(i):shown.minmax(boxes.get(i));}var target=shown.getCenter();var eye=new Vec3(3,96+actor.getEyeHeight(),-2.5);var look=target.subtract(eye).normalize();actor.connection.teleport(3,96,-2.5,(float)Math.toDegrees(Math.atan2(-look.x,look.z)),(float)-Math.toDegrees(Math.asin(look.y)));}
        case 6->{need(root.progress()==1&&!root.blocked()&&root.assembly().feet().size()==3,"actual current-foot reversal");need(actor.serverLevel().getBlockState(ROOT.above()).is(Blocks.STONE),"mounting support remains");System.out.println("[vandorlabs][gear-stowed-foot-client] completed PASS checks="+checks);}
        default->throw new IllegalArgumentException("Stowed Ski client operation");}ack(actor,operation);
    }
    private static void fail(net.minecraft.server.MinecraftServer server,Throwable failure){failed=true;failure.printStackTrace();System.out.println("[vandorlabs][gear-stowed-foot-client] FAIL "+failure);server.halt(false);}
    @SubscribeEvent public static void tick(ServerTickEvent.Post event){if(!enabled()||waiting==0||failed)return;try{var actor=event.getServer().getPlayerList().getPlayer(actorId);if(actor==null)return;if(waiting==1){if(!actor.isShiftKeyDown()||!actor.isCrouching())return;need(root(actor).progress()==0,"stopped while waiting for real crouch");view(actor,Direction.EAST,true);waiting=0;ack(actor,3);System.out.println("[vandorlabs][gear-stowed-foot-client] crouch-aim PASS actualServerEyeHeight="+actor.getEyeHeight());}else{if(actor.isShiftKeyDown()||actor.isCrouching())return;view(actor,Direction.DOWN,true);waiting=0;ack(actor,4);}}catch(Throwable failure){fail(event.getServer(),failure);}}
    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent event){if(enabled()&&!failed&&event.getEntity().getUUID().equals(actorId)){need(stage==7,"complete client journey");System.out.println("[vandorlabs][gear-stowed-foot-client] logout PASS");}}
}
