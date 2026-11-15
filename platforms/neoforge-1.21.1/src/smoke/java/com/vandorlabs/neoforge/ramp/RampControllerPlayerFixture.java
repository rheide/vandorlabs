package com.vandorlabs.neoforge.ramp;

import java.util.*;
import java.util.concurrent.*;
import net.minecraft.core.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.*;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.*;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.phys.*;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/** Connected ordinary UI/input path; no fixture action mutates controller/platform ownership. */
@EventBusSubscriber(modid="vandorlabs")
public final class RampControllerPlayerFixture {
 public static final BlockPos ROOT=new BlockPos(0,100,0),LEVER=ROOT.east(),ACK=ROOT.east(4);
 private static NativeRampBlock rootBlock;private static NativeRampPartBlock partBlock;
 public static BlockEntityType<NativeRampEntity> ROOT_TYPE;public static BlockEntityType<NativeRampPartEntity> PART_TYPE;
 public static MenuType<NativeRampMenu> MENU;
 private static ServerLevel world;private static ServerPlayer actor;private static NativeRampEntity owner;
 private static final Map<BlockPos,CompoundTag> originals=new LinkedHashMap<>();
 private static final CountDownLatch preparingEntered=new CountDownLatch(1),preparingRelease=new CountDownLatch(1),commitEntered=new CountDownLatch(1),commitRelease=new CountDownLatch(1);
 private static int stage,ticks,checks,stable;private static boolean cancelled,completed;private static double oldX,maximum;private static Vec3 from,to,lastFreePosition;
 private static long moveBefore,physicsBefore;
 public static void canonical(){if(NativeRampBindings.enabled()){rootBlock=NativeRampBindings.BLOCK;partBlock=NativeRampBindings.PART;ROOT_TYPE=NativeRampBindings.ENTITY;PART_TYPE=NativeRampBindings.PART_ENTITY;MENU=NativeRampBindings.MENU;}}
 public static void receive(NativeRampMotionPayload packet){client.accept(packet);}
 public static boolean enabled(){return "ramp-controller-player".equals(System.getProperty("vandorlabs.transmitterPhase"));}
 private static void need(boolean yes,String message){checks++;if(!yes)throw new AssertionError("Ramp player: "+message+" stage="+stage+" ticks="+ticks+" player="+(actor==null?"none":actor.position()+"/"+actor.getDeltaMovement())+" runtime="+(owner==null?"none":owner.controller().status()));}
 private static void signal(int value){world.setBlock(ACK,Blocks.NOTE_BLOCK.defaultBlockState().setValue(NoteBlock.NOTE,value),3);}
 private static NativeRampMenu menu(int window,NativeRampEntity tile,boolean server){var service=tile.controller();return new NativeRampMenu(MENU,window,tile,tile.scanFacing(),tile.horizontalFacing(),server?service::configure:null,server?service::status:()->new NativeRampMenu.Status("Ready",false,false,false,0));}
 private static final class RootBlock extends NativeRampBlock {
  RootBlock(){super(BlockBehaviour.Properties.of().strength(5),()->ROOT_TYPE,()->partBlock.defaultBlockState());}
  @Override protected InteractionResult useWithoutItem(BlockState state,Level level,BlockPos pos,Player player,BlockHitResult hit){if(!(level.getBlockEntity(pos) instanceof NativeRampEntity tile))return InteractionResult.FAIL;boolean admitted=com.vandorlabs.neoforge.NativeConfigurationAccess.canEditDialog(player,tile);if(!level.isClientSide)System.out.println("[vandorlabs][ramp-controller-player] root interaction admitted="+admitted+" held="+NativeRampHeldOwners.blocked(level,pos)+" player="+player.position()+" eye="+player.getEyePosition()+" hit="+hit.getLocation()+" face="+hit.getDirection()+" phase="+tile.controller().phase()+" status="+tile.controller().status());if(!admitted)return InteractionResult.FAIL;if(player instanceof ServerPlayer server)server.openMenu(new SimpleMenuProvider((window,inventory,ignored)->menu(window,tile,true),net.minecraft.network.chat.Component.literal("Ramp Controller")),buffer->buffer.writeBlockPos(pos));return InteractionResult.sidedSuccess(level.isClientSide);}
 }
 public record Probe(int step) implements CustomPacketPayload {
  public Probe{if(step<0||step>2)throw new IllegalArgumentException("Ramp probe step");}
  public static final Type<Probe> TYPE=new Type<>(ResourceLocation.parse("vandorlabs:ramp_controller_player_probe"));
  public Type<? extends CustomPacketPayload> type(){return TYPE;}
 }
 @EventBusSubscriber(modid="vandorlabs",bus=EventBusSubscriber.Bus.MOD)
 public static final class Registration {
  @SubscribeEvent public static void register(net.neoforged.neoforge.registries.RegisterEvent event){if(!enabled())return;
   event.register(Registries.BLOCK,h->{rootBlock=new RootBlock();partBlock=new NativeRampPartBlock(BlockBehaviour.Properties.of(),()->PART_TYPE);h.register(ResourceLocation.parse("vandorlabs:ramp_player_check"),rootBlock);h.register(ResourceLocation.parse("vandorlabs:ramp_player_check_part"),partBlock);});
   event.register(Registries.BLOCK_ENTITY_TYPE,h->{ROOT_TYPE=BlockEntityType.Builder.<NativeRampEntity>of((p,s)->new NativeRampEntity(ROOT_TYPE,p,s,false),rootBlock).build(null);PART_TYPE=BlockEntityType.Builder.<NativeRampPartEntity>of((p,s)->new NativeRampPartEntity(PART_TYPE,p,s),partBlock).build(null);h.register(ResourceLocation.parse("vandorlabs:ramp_player_check"),ROOT_TYPE);h.register(ResourceLocation.parse("vandorlabs:ramp_player_check_part"),PART_TYPE);});
   event.register(Registries.MENU,h->{MENU=net.neoforged.neoforge.common.extensions.IMenuTypeExtension.create((window,inventory,buffer)->menu(window,(NativeRampEntity)inventory.player.level().getBlockEntity(buffer.readBlockPos()),false));h.register(ResourceLocation.parse("vandorlabs:ramp_player_check"),MENU);});
  }
  @SubscribeEvent public static void payload(net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent event){if(!enabled())return;if(!NativeRampBindings.enabled())RampSettingsPayload.register(event);var registrar=event.registrar("1");if(!NativeRampBindings.enabled())registrar.playToClient(NativeRampMotionPayload.TYPE,NativeRampMotionPayload.CODEC,(packet,context)->context.enqueueWork(()->client.accept(packet)));registrar.playToServer(Probe.TYPE,new StreamCodec<RegistryFriendlyByteBuf,Probe>(){public Probe decode(RegistryFriendlyByteBuf buffer){if(buffer.readableBytes()!=1)throw new IllegalArgumentException("Ramp probe length");return new Probe(buffer.readUnsignedByte());}public void encode(RegistryFriendlyByteBuf buffer,Probe value){buffer.writeByte(value.step());}},(packet,context)->context.enqueueWork(()->{if(context.player() instanceof ServerPlayer player)probe(player,packet.step());}));}
 }
 private static java.util.function.Consumer<NativeRampMotionPayload> client=ignored->{};
 public static void client(java.util.function.Consumer<NativeRampMotionPayload> receiver){client=receiver;}
 @SubscribeEvent public static void start(net.neoforged.neoforge.event.server.ServerStartedEvent event){if(!enabled())return;world=event.getServer().overworld();if(NativeRampColdPlayerFixture.read()){canonical();NativeRampColdPlayerFixture.started(world);return;}for(int x=-1;x<=1;x++)for(int z=-1;z<=1;z++){world.getChunk(x,z);world.setChunkForced(x,z,true);}for(var p:BlockPos.betweenClosed(new BlockPos(-5,95,-5),new BlockPos(5,105,4)))world.setBlock(p,p.getY()==95?Blocks.STONE.defaultBlockState():Blocks.AIR.defaultBlockState(),18);
  canonical();world.setBlock(ROOT.below(),Blocks.STONE.defaultBlockState(),18);world.setBlock(LEVER.below(),Blocks.STONE.defaultBlockState(),18);world.setBlock(ACK.below(),Blocks.STONE.defaultBlockState(),18);world.setBlock(LEVER,Blocks.LEVER.defaultBlockState().setValue(LeverBlock.FACE,AttachFace.FLOOR),18);world.setBlock(ROOT,rootBlock.defaultBlockState(),18);owner=(NativeRampEntity)world.getBlockEntity(ROOT);
  var settings=new CompoundTag();NativeRampSettings.defaults().writeChoices(settings);settings.putInt("SpeedMode",2);settings.putInt("EndOffsetHalfSteps",-4);owner.loadWithComponents(settings,world.registryAccess());
  for(int x=-1;x<=1;x++)for(int z=-2;z<=-1;z++){var p=new BlockPos(x,100,z);world.setBlock(p,com.vandorlabs.neoforge.VandorLabsBootstrap.PROGRAMMABLE_SLAB.get().defaultBlockState(),18);var source=world.getBlockEntity(p);source.getPersistentData().putInt("PlayerSourceWitness",x*10+z);originals.put(p,source.saveWithFullMetadata(world.registryAccess()));}
  signal(0);NativeRampJournal.testFault=point->{if(point.equals("before-actor-intent-write")&&preparingEntered.getCount()>0){preparingEntered.countDown();await(preparingRelease);}else if(point.equals("before-vacated-write")&&commitEntered.getCount()>0){commitEntered.countDown();await(commitRelease);}};System.out.println("[vandorlabs][ramp-controller-player] actual server PID="+ProcessHandle.current().pid());
 }
 private static void await(CountDownLatch latch){try{if(!latch.await(30,TimeUnit.SECONDS))throw new IllegalStateException("Player fixture worker hold timeout");}catch(InterruptedException failure){Thread.currentThread().interrupt();throw new IllegalStateException(failure);}}
 @SubscribeEvent public static void login(PlayerEvent.PlayerLoggedInEvent event){if(!enabled()||!(event.getEntity() instanceof ServerPlayer player))return;actor=player;if(NativeRampColdPlayerFixture.read()){owner=(NativeRampEntity)world.getBlockEntity(ROOT);NativeRampColdPlayerFixture.login(world,player);return;}player.setGameMode(GameType.SURVIVAL);player.getInventory().clearContent();player.getInventory().selected=0;player.setItemInHand(InteractionHand.OFF_HAND,ItemStack.EMPTY);player.getInventory().setItem(8,new ItemStack(Items.DIAMOND,37));player.getAbilities().flying=false;player.onUpdateAbilities();player.teleportTo(world,.5,100.5,-1.5,0,0);player.setDeltaMovement(Vec3.ZERO);player.setOnGround(true);player.inventoryMenu.broadcastChanges();world.getServer().getPlayerList().saveAll();NativeRampColdPlayerFixture.prepared(world,player,originals);System.out.println("[vandorlabs][ramp-controller-player] setup PASS normal connected Survival/empty selected slot/resource inventory/native source");}
 @SubscribeEvent public static void tick(net.neoforged.neoforge.event.tick.ServerTickEvent.Post event){if(!enabled()||world==null||actor==null||completed)return;try{
  if(NativeRampColdPlayerFixture.read()){NativeRampColdPlayerFixture.tick(world,actor);return;}need(++ticks<2400,"timeout");var runtime=owner.controller();var operation=NativeRampJournal.atRoot(world,ROOT);
  if(stage==0&&preparingEntered.getCount()==0){need(operation!=null&&operation.phase()==NativeRampJournal.Phase.ACTOR_INTENT,"actual actor intent is pending");need(!NativeRampPlayerHold.blocked(actor),"preparing keeps player free");for(var p:originals.keySet())need(world.getBlockEntity(p)!=null&&world.getBlockEntity(p).saveWithFullMetadata(world.registryAccess()).equals(originals.get(p)),"preparing source exact/native");var intent=NativeRampJournal.startupActorIntent(operation);var participant=intent.participants().stream().filter(p->p.id().equals(actor.getUUID())).findFirst().orElseThrow();from=participant.from();to=participant.to();need(!from.equals(to),"nonzero Start has real initial player transfer");oldX=actor.getX();signal(1);stage=1;}
  if(stage==2){if(runtime.phase()!=NativeRampController.Phase.IDLE)return;need(NativeRampJournal.atRoot(world,ROOT)==null,"changed preparation retired without ownership transfer");for(var p:originals.keySet())need(world.getBlockEntity(p)!=null&&NativeRampJournal.sameSnapshot(originals.get(p),world.getBlockEntity(p).saveWithFullMetadata(world.registryAccess())),"cancel leaves exact source");need(actor.getY()==100.5&&!NativeRampPlayerHold.blocked(actor),"cancel leaves player free/native");var velocity=actor.getDeltaMovement();var position=actor.position();if(velocity.x*velocity.x+velocity.z*velocity.z>1e-12||!actor.onGround()||!position.equals(lastFreePosition)){lastFreePosition=position;stable=0;return;}if(++stable<5)return;cancelled=true;signal(2);stage=3;}
  if(stage==3&&commitEntered.getCount()==0){need(operation!=null&&operation.phase()==NativeRampJournal.Phase.COMMIT_PUBLICATION,"real vacated receipt is pending");need(NativeRampPlayerHold.blocked(actor),"physical commit fence held");var participant=NativeRampJournal.startupActorIntent(operation).participants().stream().filter(p->p.id().equals(actor.getUUID())).findFirst().orElseThrow();to=participant.to();need(actor.position().equals(to),"every player physical move precedes commit fence");NativeRampColdPlayerFixture.committed(world,actor,operation);moveBefore=NativeRampPlayerHold.diagnostics()[0];physicsBefore=NativeRampPlayerHold.diagnostics()[1];signal(3);stage=4;}
  if(stage==5){need(!runtime.status().error(),"committed actor activation failed");if(NativeRampPlayerHold.blocked(actor)||runtime.phase()!=NativeRampController.Phase.ACTIVE)return;need(actor.position().equals(to),"held physical endpoint retained until durable receipt");oldX=actor.getX();signal(4);stage=6;}
  if(stage==6){need(!runtime.status().error(),"released player failed");if(Math.abs(actor.getX()-oldX)<.04)return;need(!NativeRampPlayerHold.blocked(actor),"ordinary movement resumes after same-operation release");signal(5);stage=7;}
  if(stage==7){if(NativeRampColdPlayerFixture.moving(world,actor,owner,operation))return;need(!runtime.status().error(),"lever stroke refused");if(runtime.committedPose()<.25)return;need(runtime.committedPose()<1&&runtime.status().moving(),"real lever started an unfinished stroke");supported(operation);maximum=runtime.committedPose();signal(6);stage=8;}
  if(stage==8){need(!runtime.status().error(),"reverse refused");maximum=Math.max(maximum,runtime.committedPose());supported(operation);if(runtime.committedPose()!=0||runtime.status().moving())return;need(maximum<1&&maximum>.25,"ordinary second lever click reverses before endpoint");System.out.println("[vandorlabs][ramp-controller-player] stopped endpoint pose="+runtime.committedPose()+" revision="+runtime.status().revision()+" player="+actor.position()+" body="+actor.getBoundingBox());signal(7);stage=9;}
  if(stage==9){need(!runtime.status().error(),"native return refused");if(runtime.phase()!=NativeRampController.Phase.IDLE||owner.settings().startHalf()!=0)return;need(NativeRampJournal.atRoot(world,ROOT)==null,"native return retired journal");for(var p:originals.keySet())need(world.getBlockEntity(p)!=null&&NativeRampJournal.sameSnapshot(originals.get(p),world.getBlockEntity(p).saveWithFullMetadata(world.registryAccess())),"native return exact source NBT");need(Math.abs(actor.getY()-100.5)<1e-8,"player returns to native support");need(actor.getInventory().getItem(8).is(Items.DIAMOND)&&actor.getInventory().getItem(8).getCount()==37,"player resources never copied or replaced");signal(8);stage=10;}
 }catch(Throwable failure){preparingRelease.countDown();commitRelease.countDown();NativeRampJournal.testFault=null;System.err.println("[vandorlabs][ramp-controller-player] FAIL "+failure);failure.printStackTrace();world.getServer().halt(false);throw failure;}}
 private static void supported(NativeRampJournal.Operation operation){
  need(operation!=null,"moving operation exists");var pose=owner.controller().committedPose();var body=actor.getBoundingBox();var memo=NativeRampRiders.captureSupport(actor);
  need(memo!=null&&memo.level().get()==world&&world.getGameTime()-memo.tick()>=0&&world.getGameTime()-memo.tick()<=1&&Math.abs(memo.height()-body.minY)<1e-6,"authoritative current support memo pose="+pose+" memo="+memo+" body="+body);
  double nearest=Double.POSITIVE_INFINITY;AABB witness=null;
  for(var cell:operation.assembly().cells(pose,pose)){
   var chunk=world.getChunkSource().getChunkNow(cell.getX()>>4,cell.getZ()>>4);need(chunk!=null,"loaded current geometry cell");var raw=chunk.getBlockEntities().get(cell);
   need(raw instanceof NativeRampPartEntity part&&part.belongs(operation.id(),ROOT)&&part.publication()!=null,"installed exact-operation support owner");
   for(var box:((NativeRampPartEntity)raw).publication().worldBoxes())if(box.maxX>body.minX+1e-7&&box.minX<body.maxX-1e-7&&box.maxZ>body.minZ+1e-7&&box.minZ<body.maxZ-1e-7){double difference=Math.abs(body.minY-box.maxY);if(difference<nearest){nearest=difference;witness=box;}}
  }
  need(nearest<1e-6,"actual emitted underfoot witness pose="+pose+" feet="+body.minY+" delta="+nearest+" box="+witness);
 }
 private static void probe(ServerPlayer player,int step){need(enabled()&&player==actor&&player.serverLevel()==world,"current connected probe actor");switch(step){
  case 0->{need(stage==1&&Math.abs(actor.getX()-oldX)>.04&&!NativeRampPlayerHold.blocked(actor),"real C2S movement during preparation remains free");need(actor.getY()==100.5,"pending preparation still has native support");preparingRelease.countDown();stage=2;System.out.println("[vandorlabs][ramp-controller-player] preparing-free PASS");}
  case 1->{need(stage==4&&actor.position().equals(to),"commit fence preserves physical endpoint despite move packets");var stats=NativeRampPlayerHold.diagnostics();need(stats[0]-moveBefore>=2&&stats[1]-physicsBefore>=2,"both actual C2S movement and ServerPlayer physics were held");commitRelease.countDown();stage=5;System.out.println("[vandorlabs][ramp-controller-player] commit-fence PASS moves="+(stats[0]-moveBefore)+" physics="+(stats[1]-physicsBefore));}
  case 2->{need(stage==10&&cancelled,"client observed completed normal path");completed=true;signal(9);NativeRampJournal.testFault=null;System.out.println("[vandorlabs][ramp-controller-player] server PASS checks="+checks+" normalGUI/C2S/prepare-cancel/physical-fence/lever/reverse/native-return/resources");}
  default->throw new IllegalArgumentException();}}
 @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent event){if(enabled()&&event.getEntity()==actor)System.out.println("[vandorlabs][ramp-controller-player] logout "+(completed?"PASS":"INCOMPLETE"));}
 private RampControllerPlayerFixture(){}
}
