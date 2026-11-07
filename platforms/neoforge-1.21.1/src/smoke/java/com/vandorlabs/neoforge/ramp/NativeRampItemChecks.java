package com.vandorlabs.neoforge.ramp;

import com.vandorlabs.neoforge.placement.NativeStationaryPlacement;
import com.vandorlabs.redstone.ChannelList;
import net.minecraft.core.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.*;
import net.minecraft.world.*;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.phys.*;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStartedEvent;

/** Private actual item transactions and failure ownership; the injected action is not Ramp gameplay. */
@EventBusSubscriber(modid="vandorlabs")
public final class NativeRampItemChecks {
    private static final BlockPos BASE=new BlockPos(26400,90,26400),SAVE=BASE.north(32),INERT=SAVE.east(4);
    private static final NativeRampBlock[] BLOCKS=new NativeRampBlock[2];private static final FaultItem[] ITEMS=new FaultItem[2];
    private static BlockEntityType<NativeRampEntity> TYPE;private static int checks,fault,prepares,actions,drops;private static BlockPos faultPos,dropPos;private static ItemStack dropped=ItemStack.EMPTY;private static boolean inspect;
    private static String phase(){return System.getProperty("vandorlabs.transmitterPhase","");}private static boolean enabled(){return phase().startsWith("ramp-item-");}
    private static void need(boolean yes,String why){checks++;if(!yes)throw new AssertionError("Ramp item: "+why);}
    private static ResourceLocation id(int i){return ResourceLocation.fromNamespaceAndPath("vandorlabs","ramp_item_fixture_"+i);}
    private static NativeRampItem.PreparedCommand prepare(NativeRampEntity owner,ServerPlayer actor,NativeRampConfiguration.Plan plan,CompoundTag copied){
        prepares++;need(NativeStationaryPlacement.mutating(owner.getLevel(),owner.getBlockPos()),"admission inside guarded installed phase");need(owner.getLevel().getBlockEntity(owner.getBlockPos())==owner,"installed admission owner");
        var held=actor.getMainHandItem();int count=held.getCount();need(owner.getBlockState().getValue(NativeRampBlock.MODE)==owner.settings().mode(),"staged model mode matches installed choices before observers");need(count>0&&count<=2,"permission sees original pre-consumption held stack");need(NativeRampAuthorization.grantInstalled(owner,actor)&&actor.getUUID().equals(NativeRampAuthorization.actorId(owner)),"placement authority granted before consumption without dialog permission");
        if(inspect){need(plan!=null&&plan.admission().accepted()&&owner.settings().channels().equals(ChannelList.of(73102)),"outer RampSettings applied after generic settings");need(copied!=null&&copied.getInt("redstone_channel")==73103,"separate actor-authorized Copifier marker");copied.putInt("redstone_channel",1);need(held.get(DataComponents.CUSTOM_DATA).copyTag().getCompound(NativeRampItem.COPIED).getInt("redstone_channel")==73103,"callback cannot mutate original marker");}
        if(fault==7||fault==10)throw new IllegalStateException("fixture preparation refused");if(fault==8)return null;
        return ()->{actions++;need(!NativeStationaryPlacement.mutating(owner.getLevel(),owner.getBlockPos()),"action only after committed staging");need(held.getCount()==count-(actor.isCreative()?0:1),"action follows consumption");return new NativeRampReceipt(true,true,false,true,new NativeRampMenu.Status("Runtime intentionally absent in item fixture",true,false,false,1));};
    }
    private static final class FaultItem extends NativeRampItem {
        FaultItem(NativeRampBlock block){super(block,new Item.Properties(),NativeRampItemChecks::prepare);}
        @Override protected boolean stage(Level world,BlockPos pos,BlockState state){if(fault==1)return false;boolean changed=super.stage(world,pos,state);if(fault==3)throw new IllegalStateException("fixture after staged Ramp");return fault==2||fault==4||fault==6?false:changed;}
        @Override protected boolean restore(Level world,BlockPos pos,BlockState state){return fault==4||fault==6||fault==10?false:super.restore(world,pos,state);}
    }
    @EventBusSubscriber(modid="vandorlabs",bus=EventBusSubscriber.Bus.MOD)
    public static final class Registration {
        @SubscribeEvent public static void register(net.neoforged.neoforge.registries.RegisterEvent event){if(!enabled())return;
            event.register(Registries.BLOCK,h->{
                BLOCKS[0]=new NativeRampBlock(BlockBehaviour.Properties.of().strength(2.5F).requiresCorrectToolForDrops(),()->TYPE){@Override public BlockEntity newBlockEntity(BlockPos p,BlockState s){return (fault==5||fault==6)&&p.equals(faultPos)?null:super.newBlockEntity(p,s);}};
                BLOCKS[1]=new NativeRampSlabBlock(BlockBehaviour.Properties.of().strength(2.5F).requiresCorrectToolForDrops(),()->TYPE);
                for(int i=0;i<2;i++)h.register(id(i),BLOCKS[i]);
            });
            event.register(Registries.ITEM,h->{for(int i=0;i<2;i++){ITEMS[i]=new FaultItem(BLOCKS[i]);h.register(id(i),ITEMS[i]);}});
            event.register(Registries.BLOCK_ENTITY_TYPE,h->{TYPE=BlockEntityType.Builder.<NativeRampEntity>of((p,s)->new NativeRampEntity(TYPE,p,s,s.getBlock() instanceof NativeRampSlabBlock),BLOCKS).build(null);h.register(ResourceLocation.parse("vandorlabs:ramp_item_fixture"),TYPE);});
        }
    }
    @SubscribeEvent public static void dropped(net.neoforged.neoforge.event.entity.EntityJoinLevelEvent event){if(enabled()&&dropPos!=null&&event.getEntity() instanceof ItemEntity item&&item.position().distanceToSqr(Vec3.atCenterOf(dropPos))<16&&item.getItem().getItem() instanceof NativeRampItem){drops+=item.getItem().getCount();dropped=item.getItem().copy();}}
    private static NativeRampEntity at(ServerLevel world,BlockPos pos){return world.getBlockEntity(pos) instanceof NativeRampEntity owner?owner:null;}
    private static void clear(ServerLevel world,BlockPos pos){fault=0;inspect=false;world.getChunkAt(pos);for(int x=-1;x<=1;x++)for(int y=-1;y<=1;y++)for(int z=-1;z<=1;z++)world.setBlock(pos.offset(x,y,z),Blocks.AIR.defaultBlockState(),18);world.setBlock(pos.below(),Blocks.STONE.defaultBlockState(),18);for(var item:world.getEntitiesOfClass(ItemEntity.class,new AABB(pos).inflate(4)))item.discard();}
    private static ItemStack stack(int family,CompoundTag root){var result=new ItemStack(ITEMS[family],2);result.set(DataComponents.CUSTOM_DATA,CustomData.of(root));return result;}
    private static InteractionResult place(ServerLevel world,ServerPlayer actor,BlockPos pos,ItemStack stack){actor.setPos(pos.getX()+.5,pos.getY()+1,pos.getZ()-2);actor.setItemInHand(InteractionHand.MAIN_HAND,stack);return stack.useOn(new UseOnContext(actor,InteractionHand.MAIN_HAND,new BlockHitResult(Vec3.atBottomCenterOf(pos),Direction.UP,pos.below(),false)));}
    private static CompoundTag choices(){return NativeRampSettings.defaults().itemChoices(Direction.EAST.get3DDataValue());}
    private static void write(ServerLevel world)throws Exception {
        var actor=new net.neoforged.neoforge.common.util.FakePlayer(world,new com.mojang.authlib.GameProfile(new java.util.UUID(0,290711),"RampItem"));actor.setGameMode(GameType.SURVIVAL);
        for(int family=0;family<2;family++)for(int pitch:new int[]{-80,0,80})for(int yaw=0;yaw<4;yaw++){
            clear(world,BASE);actor.setXRot(pitch);actor.setYRot(yaw*90);prepares=actions=0;var root=new CompoundTag();var config=choices();config.putInt("Channel",73102);config.putIntArray("RedstoneChannels",new int[]{73102});root.put(NativeRampItem.CONFIGURATION,config);var generic=new CompoundTag();generic.putIntArray("RedstoneChannels",new int[]{73101});generic.putString("PrivateRampMarker","opaque:item");generic.putByteArray("OpaqueRampPayload",new byte[65536]);root.put("BlockEntityTag",generic);var copied=new CompoundTag();copied.putInt("redstone_channel",73103);root.put(NativeRampItem.COPIED,copied);var held=stack(family,root);inspect=true;
            need(place(world,actor,BASE,held).consumesAction()&&held.getCount()==1&&prepares==1&&actions==1,"configured placement exactly once");inspect=false;
            var owner=at(world,BASE);need(owner!=null&&!owner.inert()&&owner.slab()==(family==1),"correct actual owner");need(owner.saveWithoutMetadata(world.registryAccess()).getByteArray("OpaqueRampPayload").length==65536,"generic opaque metadata retained");var picked=NativeRampItem.pick(ITEMS[family],owner).get(DataComponents.CUSTOM_DATA).copyTag();need(picked.getCompound(NativeRampItem.CONFIGURATION).getInt("Channel")==73102&&!picked.toString().contains("opaque:item")&&!picked.contains("BlockEntityTag"),"configured pick excludes generic/private authority");
        }
        actor.setXRot(0);actor.setYRot(0);clear(world,BASE);var fresh=stack(0,new CompoundTag());need(place(world,actor,BASE,fresh).consumesAction()&&at(world,BASE).settings().equals(NativeRampSettings.defaults()),"plain item preserves fresh defaults actual="+at(world,BASE).settings());
        var moatRoot=new CompoundTag();var moatChoices=choices();moatChoices.putBoolean("MoatMode",true);moatRoot.put(NativeRampItem.CONFIGURATION,moatChoices);clear(world,BASE);need(place(world,actor,BASE,stack(0,moatRoot)).consumesAction()&&world.getBlockState(BASE).getValue(NativeRampBlock.MODE)==6,"configured mode staged with full choices");
        actor.setXRot(-80);actor.setYRot(90);clear(world,BASE);var plain=stack(0,new CompoundTag());need(place(world,actor,BASE,plain).consumesAction()&&at(world,BASE).settings().hasDirection()&&at(world,BASE).settings().resolvedDirection(2)==world.getBlockState(BASE).getValue(NativeRampBlock.FACING).get3DDataValue(),"plain vertical scan preserves horizontal travel");
        for(int mode:new int[]{1,2,3,4,5,6,7,8,10}){clear(world,BASE);fault=mode;faultPos=BASE;prepares=actions=0;var held=stack(0,new CompoundTag());boolean threw=false;try{place(world,actor,BASE,held);}catch(RuntimeException expected){threw=true;}finally{fault=0;}
            need(actions==0,"failed staging never publishes action mode="+mode);if(mode==4||mode==6||mode==10)need(threw&&held.getCount()==1&&at(world,BASE)!=null&&at(world,BASE).inert()&&NativeRampAuthorization.actorId(at(world,BASE))==null&&at(world,BASE).recoverySnapshot().getBoolean(NativeRampEntity.PLACEMENT_RECOVERY),"failed rollback retains single consumed owner mode="+mode);else need(held.getCount()==2&&world.getBlockState(BASE).isAir(),"failed staging restores cell and item mode="+mode);
        }
        for(String bad:new String[]{"{Sources:[{PrivateOwner:1b}]}","{NativeRampSchema:99}","{NativeRampPlacementRecovery:1b}","{ForgeCaps:{UnknownOwner:1b}}"}){clear(world,BASE);var root=new CompoundTag();root.put("BlockEntityTag",TagParser.parseTag(bad));var held=stack(0,root);prepares=actions=0;need(!place(world,actor,BASE,held).consumesAction()&&held.getCount()==2&&world.getBlockState(BASE).isAir()&&prepares==0&&actions==0,"unsupported ownership refused before mutation "+bad);}
        for(boolean creative:new boolean[]{false,true})for(boolean correct:new boolean[]{false,true}){clear(world,BASE);world.setBlock(BASE,BLOCKS[0].defaultBlockState(),18);var raw=TagParser.parseTag("{NativeRampSchema:99,PrivateRampMarker:'sealed'}");at(world,BASE).loadWithComponents(raw,world.registryAccess());actor.setGameMode(creative?GameType.CREATIVE:GameType.SURVIVAL);actor.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(correct?Items.STONE_PICKAXE:Items.WOODEN_PICKAXE));dropPos=BASE;drops=0;need(actor.gameMode.destroyBlock(BASE)&&drops==1,"single recovery drop creative="+creative+" correct="+correct);need(dropped.get(DataComponents.CUSTOM_DATA).copyTag().getCompound(NativeRampItem.RECOVERY).equals(raw),"exact mined recovery");dropPos=null;var held=dropped.copyWithCount(2);need(!place(world,actor,BASE,held).consumesAction()&&held.getCount()==2,"recovery is not ordinary placement");}
        clear(world,BASE);actor.setGameMode(GameType.ADVENTURE);var adventure=stack(0,new CompoundTag()).copyWithCount(1);adventure.set(DataComponents.CAN_PLACE_ON,new net.minecraft.world.item.AdventureModePredicate(java.util.List.of(net.minecraft.advancements.critereon.BlockPredicate.Builder.block().of(Blocks.STONE,Blocks.AIR).build()),false));need(place(world,actor,BASE,adventure).consumesAction()&&adventure.isEmpty()&&actor.getUUID().equals(NativeRampAuthorization.actorId(at(world,BASE))),"last Adventure item grants installed authority before consumption");need(!NativeRampAuthorization.grantInstalled(at(world,BASE),actor),"placement authority cannot be replayed outside staging");
        actor.setGameMode(GameType.SURVIVAL);for(int family=0;family<2;family++)for(boolean correct:new boolean[]{false,true}){clear(world,BASE);need(place(world,actor,BASE,stack(family,new CompoundTag())).consumesAction(),"healthy harvesting fixture");actor.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(correct?Items.STONE_PICKAXE:Items.WOODEN_PICKAXE));dropPos=BASE;drops=0;need(actor.gameMode.destroyBlock(BASE)&&drops==(correct?1:0),"healthy ordinary tool admission family="+family);if(correct)need(!dropped.has(DataComponents.CUSTOM_DATA)&&dropped.getItem()==ITEMS[family],"healthy harvest remains unconfigured");dropPos=null;}
        NativeRampCraftingChecks.run(world,ITEMS);
        actor.setGameMode(GameType.SURVIVAL);clear(world,SAVE);var held=stack(1,new CompoundTag());need(place(world,actor,SAVE,held).consumesAction(),"saved healthy item owner");at(world,SAVE).getPersistentData().putString("SavedItemMarker","retained");clear(world,INERT);world.setBlock(INERT,BLOCKS[0].defaultBlockState(),18);at(world,INERT).loadWithComponents(TagParser.parseTag("{NativeRampPlacementRecovery:1b,PrivateRampMarker:'restart'}"),world.registryAccess());for(var pos:new BlockPos[]{SAVE,INERT})world.setChunkForced(pos.getX()>>4,pos.getZ()>>4,true);
    }
    private static void read(ServerLevel world){var owner=at(world,SAVE);need(owner!=null&&!owner.inert()&&owner.slab()&&owner.getPersistentData().getString("SavedItemMarker").equals("retained"),"healthy item owner survives restart");var failed=at(world,INERT);need(failed!=null&&failed.inert()&&failed.recoverySnapshot().getString("PrivateRampMarker").equals("restart"),"sealed placement owner survives restart");}
    @SubscribeEvent public static void started(ServerStartedEvent event)throws Exception {if(!enabled())return;boolean read=phase().endsWith("read");if(read)read(event.getServer().overworld());else write(event.getServer().overworld());event.getServer().saveEverything(true,true,true);System.out.println("[vandorlabs][ramp-item] "+(read?"read":"write")+" PASS checks="+checks+" private placement only; runtime action remains injected");event.getServer().halt(false);}
}
