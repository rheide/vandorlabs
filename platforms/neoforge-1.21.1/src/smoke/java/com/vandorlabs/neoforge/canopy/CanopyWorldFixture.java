package com.vandorlabs.neoforge.canopy;

import java.util.*;
import net.minecraft.core.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.*;
import net.minecraft.world.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;

/** Isolated placed authored gallery. Full footprints are installed before a client joins. */
@EventBusSubscriber(modid="vandorlabs")
public final class CanopyWorldFixture {
    static final String[] KINDS={"front_canopy_glass","regular_canopy_glass","canopy","long_canopy","angled_front_1x1","angled_front_1x2","large_front_canopy_glass","space_visor_2x1","space_visor_3x1","space_visor_5x1","space_visor_5x1_double_height"};
    static final BlockPos ROOT=new BlockPos(0,96,0),REGULAR=new BlockPos(6,94,0),MARKER=new BlockPos(0,90,-8);
    static final Map<String,NativeCanopyBlock> BLOCKS=new LinkedHashMap<>();static BlockEntityType<NativeCanopyEntity> TYPE;private static UUID actor;private static int stage,checks;
    static boolean enabled(){return Boolean.getBoolean("vandorlabs.canopyWorldCheck");}
    static BlockPos root(int index){return index==0?ROOT:index==1?REGULAR:new BlockPos(12+(index-2)%4*8,96,12+(index-2)/4*12);}
    static void need(boolean value,String why){checks++;if(!value)throw new AssertionError("Canopy placed world stage="+stage+" "+why);}
    public record Step(int value)implements CustomPacketPayload {static final Type<Step> TYPE=new Type<>(ResourceLocation.parse("vandorlabs:canopy_world_step"));public Type<? extends CustomPacketPayload>type(){return TYPE;}}
    @EventBusSubscriber(modid="vandorlabs",bus=EventBusSubscriber.Bus.MOD)
    public static final class Registration {
        @SubscribeEvent public static void register(net.neoforged.neoforge.registries.RegisterEvent event){if(!enabled())return;event.register(Registries.BLOCK,h->{for(String kind:KINDS){var block=new NativeCanopyBlock(kind,()->TYPE,BlockBehaviour.Properties.of().strength(1.5F,1.5F).sound(SoundType.GLASS));BLOCKS.put(kind,block);h.register(ResourceLocation.parse("vandorlabs:canopy_world_"+kind),block);}});event.register(Registries.BLOCK_ENTITY_TYPE,h->{TYPE=BlockEntityType.Builder.of((pos,state)->new NativeCanopyEntity(TYPE,pos,state,((NativeCanopyBlock)state.getBlock()).kind()),BLOCKS.values().toArray(Block[]::new)).build(null);h.register(ResourceLocation.parse("vandorlabs:canopy_world"),TYPE);});}
        @SubscribeEvent public static void packets(net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent event){if(!enabled())return;CanopyUsePayload.register(event);event.registrar("1").playToServer(Step.TYPE,new StreamCodec<RegistryFriendlyByteBuf,Step>(){public Step decode(RegistryFriendlyByteBuf bytes){if(bytes.readableBytes()!=1)throw new IllegalArgumentException("Canopy world step length");int value=bytes.readUnsignedByte();if(value>17)throw new IllegalArgumentException("Canopy world step");return new Step(value);}public void encode(RegistryFriendlyByteBuf bytes,Step value){bytes.writeByte(value.value);}},(packet,context)->context.enqueueWork(()->{if(context.player() instanceof ServerPlayer player)step(player,packet.value);}));}
    }
    @SubscribeEvent public static void setup(ServerStartedEvent event){if(!enabled())return;var world=event.getServer().overworld();world.setDayTime(6000);world.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false,event.getServer());world.setWeatherParameters(100000,0,false,false);
        for(int x=-8;x<=44;x++)for(int z=-10;z<=40;z++){var floor=new BlockPos(x,90,z);world.getChunkAt(floor);world.setBlock(floor,Blocks.WHITE_CONCRETE.defaultBlockState(),18);}
        for(int i=0;i<KINDS.length;i++){var pos=root(i);var state=BLOCKS.get(KINDS[i]).defaultBlockState();world.setBlock(pos,state,18);var owner=(NativeCanopyEntity)world.getBlockEntity(pos);var tag=new CompoundTag();tag.putLong("AnchorOffset",0);tag.putBoolean("CenteredPlacement",KINDS[i].startsWith("space_visor_")||com.vandorlabs.persistence.CanopySettingsData.supports(KINDS[i]));tag.putBoolean("HollowPlacement",com.vandorlabs.persistence.CanopySettingsData.supports(KINDS[i]));tag.putInt("CanopyTint",2);tag.putString("PrivateCanopyWorld","server-only");if(i==0){tag.putInt("CanopyMount",3);tag.putInt("CanopyDepth",80);}owner.loadWithComponents(tag,world.registryAccess());var cells=NativeCanopyFootprint.cells(owner.kind(),pos,owner.facing(),owner.settings(),owner.links());owner.configurationFootprint(cells);for(var cell:cells)if(!cell.equals(pos)){world.setBlock(cell,state,18);var helper=(NativeCanopyEntity)world.getBlockEntity(cell);need(helper!=null,"complete gallery helper");helper.helperOf(owner);helper.notifyChanged();}NativeCanopyLifecycle.prepare(owner);owner.notifyChanged();need(owner.available()&&owner.publication().draws()&&owner.publication().geometry()!=null,"prepared exact gallery owner="+KINDS[i]);}
        world.setBlock(MARKER,Blocks.NOTE_BLOCK.defaultBlockState(),18);System.out.println("[vandorlabs][canopy-world] gallery PASS checks="+checks);
    }
    @SubscribeEvent public static void login(PlayerEvent.PlayerLoggedInEvent event){if(!enabled()||!(event.getEntity() instanceof ServerPlayer player))return;actor=player.getUUID();player.setGameMode(GameType.CREATIVE);player.getInventory().clearContent();player.getInventory().selected=0;player.setItemInHand(InteractionHand.MAIN_HAND,ItemStack.EMPTY);player.setItemInHand(InteractionHand.OFF_HAND,ItemStack.EMPTY);player.getAbilities().mayfly=true;player.getAbilities().flying=true;player.onUpdateAbilities();camera(player,2.5,96,-5,new Vec3(1.5,94.5,1.5));marker(player,0);}
    private static void camera(ServerPlayer player,double x,double y,double z,Vec3 target){double dx=target.x-x,dy=target.y-y-player.getEyeHeight(),dz=target.z-z;player.connection.teleport(x,y,z,(float)-Math.toDegrees(Math.atan2(dx,dz)),(float)-Math.toDegrees(Math.atan2(dy,Math.sqrt(dx*dx+dz*dz))));player.connection.send(new net.minecraft.network.protocol.game.ClientboundSetCarriedItemPacket(0));player.inventoryMenu.broadcastChanges();}
    private static void marker(ServerPlayer player,int value){player.serverLevel().setBlock(MARKER,Blocks.NOTE_BLOCK.defaultBlockState().setValue(NoteBlock.NOTE,value),3);}
    private static void step(ServerPlayer player,int next){need(actor.equals(player.getUUID())&&next==stage+1,"ordered actual actor step");stage=next;if(next>=14&&next<=16){var moving=(NativeCanopyEntity)player.level().getBlockEntity(root(2));need(moving!=null&&moving.available(),"actual installed moving owner");if(next==15)need(moving.links().progress()==1,"server automatic opening endpoint");if(next==16)need(moving.links().progress()>0&&moving.links().progress()<1,"server actual closing reversal pose="+moving.links().progress());need(NativeCanopyControl.toggle(moving),"actual runtime target toggle");}if(next==17){var moving=(NativeCanopyEntity)player.level().getBlockEntity(root(2));need(moving.links().progress()==1,"server automatic reversed endpoint");}if(next==1)camera(player,2.5,94,-2.5,new Vec3(2.5,94.8,.09375));else if(next==2)camera(player,6.5,94,-3,new Vec3(6.5,94.5,.5));else if(next>=3&&next<=13){var pos=root(next-3);var owner=(NativeCanopyEntity)player.level().getBlockEntity(pos);var bounds=owner.publication().geometry().bounds();var target=new Vec3((bounds.minX+bounds.maxX)/2,(bounds.minY+bounds.maxY)/2,(bounds.minZ+bounds.maxZ)/2);camera(player,target.x,target.y-1,-7+pos.getZ(),target);}marker(player,next);}
    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent event){if(enabled()&&event.getEntity().getUUID().equals(actor)&&stage==17)System.out.println("[vandorlabs][canopy-world] server PASS checks="+checks);}
}
