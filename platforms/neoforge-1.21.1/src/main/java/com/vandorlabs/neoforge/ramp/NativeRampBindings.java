package com.vandorlabs.neoforge.ramp;

import com.vandorlabs.neoforge.*;
import net.minecraft.core.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.registries.RegisterEvent;

/** Canonical runtime adapter; feature activation follows complete lifecycle acceptance. */
@EventBusSubscriber(modid="vandorlabs",bus=EventBusSubscriber.Bus.MOD)
public final class NativeRampBindings {
    public static NativeRampBlock BLOCK;
    public static NativeRampSlabBlock SLAB;
    public static NativeRampPartBlock PART;
    public static NativeRampItem ITEM,SLAB_ITEM;
    public static BlockEntityType<NativeRampEntity> ENTITY;
    public static BlockEntityType<NativeRampPartEntity> PART_ENTITY;
    public static MenuType<NativeRampMenu> MENU;
    private static java.util.function.Consumer<NativeRampMotionPayload> motion=ignored->{};
    public static void bindMotion(java.util.function.Consumer<NativeRampMotionPayload> receiver){motion=java.util.Objects.requireNonNull(receiver);}
    public static boolean enabled(){return Boolean.parseBoolean(System.getProperty("vandorlabs.ramps","false"));}
    private static ResourceLocation id(String path){return ResourceLocation.fromNamespaceAndPath("vandorlabs",path);}
    private static BlockBehaviour.Properties properties(){return BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(5,6).sound(SoundType.METAL).requiresCorrectToolForDrops().noOcclusion();}
    private static ItemInteractionResult itemUse(ItemStack stack,Level world,BlockPos pos,Player actor,InteractionHand hand){
        if(hand!=InteractionHand.MAIN_HAND)return ItemInteractionResult.SKIP_DEFAULT_BLOCK_INTERACTION;
        if(stack.getItem() instanceof NativeOwnsBlockInteraction)return ItemInteractionResult.SKIP_DEFAULT_BLOCK_INTERACTION;
        if(stack.is(VandorLabsBootstrap.CONFIGURIZER.get()))return open(actor,world,pos)?ItemInteractionResult.SUCCESS:ItemInteractionResult.FAIL;
        return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }
    private static final class ControllerBlock extends NativeRampBlock {
        ControllerBlock(){super(NativeRampBindings.properties(),()->ENTITY,()->PART.defaultBlockState());}
        @Override protected ItemInteractionResult useItemOn(ItemStack stack,BlockState state,Level world,BlockPos pos,Player actor,InteractionHand hand,BlockHitResult hit){return itemUse(stack,world,pos,actor,hand);}
        @Override protected InteractionResult useWithoutItem(BlockState state,Level world,BlockPos pos,Player actor,BlockHitResult hit){return actor.getMainHandItem().isEmpty()&&open(actor,world,pos)?InteractionResult.sidedSuccess(world.isClientSide):InteractionResult.PASS;}
    }
    private static final class ControllerSlab extends NativeRampSlabBlock {
        ControllerSlab(){super(NativeRampBindings.properties(),()->ENTITY,()->PART.defaultBlockState());}
        @Override protected ItemInteractionResult useItemOn(ItemStack stack,BlockState state,Level world,BlockPos pos,Player actor,InteractionHand hand,BlockHitResult hit){return itemUse(stack,world,pos,actor,hand);}
        @Override protected InteractionResult useWithoutItem(BlockState state,Level world,BlockPos pos,Player actor,BlockHitResult hit){return actor.getMainHandItem().isEmpty()&&open(actor,world,pos)?InteractionResult.sidedSuccess(world.isClientSide):InteractionResult.PASS;}
    }
    private static NativeRampItem.PreparedCommand prepare(NativeRampEntity owner,ServerPlayer actor,NativeRampConfiguration.Plan plan,net.minecraft.nbt.CompoundTag copied){
        if(owner.inert()||owner.controller()==null||plan!=null&&(!plan.valid()||plan.admission()==null||!plan.admission().accepted()))throw new IllegalArgumentException("Ramp placement authorization refused");
        var next=owner.settings();
        if(copied!=null){var choices=NativeRampConfiguration.copied(next,owner.horizontalFacing().get3DDataValue(),owner.scanFacing().get3DDataValue(),copied,true,true,true,false);if(!choices.valid()||choices.configurationRequested()&&(choices.admission()==null||!choices.admission().accepted()))throw new IllegalArgumentException("Invalid copied Ramp choices");next=choices.result();}
        if(!NativeRampAuthorization.grantInstalled(owner,actor))throw new IllegalArgumentException("Ramp placement authorization refused");
        var prepared=next;
        return ()->{var result=owner.controller().configure(actor,prepared);return new NativeRampReceipt(result.admitted(),result.choicesCommitted(),result.actionPending(),result.activationRefused(),result.status());};
    }
    private static NativeRampMenu menu(int window,NativeRampEntity tile,boolean server){var runtime=server?tile.controller():null;return new NativeRampMenu(MENU,window,tile,tile.scanFacing(),tile.horizontalFacing(),server?runtime::configure:null,server?runtime::status:()->new NativeRampMenu.Status("Ready",false,false,false,0));}
    @SubscribeEvent public static void register(RegisterEvent event){if(!enabled())return;
        event.register(Registries.BLOCK,h->{BLOCK=new ControllerBlock();SLAB=new ControllerSlab();PART=new NativeRampPartBlock(properties().strength(-1,3_600_000).dynamicShape(),()->PART_ENTITY);h.register(id("programmable_ramp"),BLOCK);h.register(id("programmable_ramp_slab"),SLAB);h.register(id("controlled_ramp"),PART);});
        event.register(Registries.ITEM,h->{ITEM=new NativeRampItem(BLOCK,new Item.Properties(),NativeRampBindings::prepare);SLAB_ITEM=new NativeRampItem(SLAB,new Item.Properties(),NativeRampBindings::prepare);h.register(id("programmable_ramp"),ITEM);h.register(id("programmable_ramp_slab"),SLAB_ITEM);});
        event.register(Registries.BLOCK_ENTITY_TYPE,h->{ENTITY=BlockEntityType.Builder.<NativeRampEntity>of((pos,state)->new NativeRampEntity(ENTITY,pos,state,state.getBlock() instanceof NativeRampSlabBlock),BLOCK,SLAB).build(null);PART_ENTITY=BlockEntityType.Builder.<NativeRampPartEntity>of((pos,state)->new NativeRampPartEntity(PART_ENTITY,pos,state),PART).build(null);h.register(id("programmable_ramp"),ENTITY);h.register(id("controlled_ramp"),PART_ENTITY);});
        event.register(Registries.MENU,h->{MENU=net.neoforged.neoforge.common.extensions.IMenuTypeExtension.create((window,inventory,buffer)->{var pos=buffer.readBlockPos();if(!inventory.player.level().hasChunkAt(pos)||!(inventory.player.level().getBlockEntity(pos) instanceof NativeRampEntity tile)||tile.inert())throw new IllegalArgumentException("Ramp editor owner unavailable");return menu(window,tile,false);});h.register(id("programmable_ramp"),MENU);});
    }
    @SubscribeEvent public static void payload(net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent event){if(!enabled())return;RampSettingsPayload.register(event);event.registrar("1").playToClient(NativeRampMotionPayload.TYPE,NativeRampMotionPayload.CODEC,(packet,context)->context.enqueueWork(()->motion.accept(packet)));}
    @SubscribeEvent public static void creative(net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent event){if(enabled()&&event.getTabKey().location().equals(id("vandorlabs"))){event.accept(ITEM);event.accept(SLAB_ITEM);}}
    public static boolean open(Player actor,Level world,BlockPos pos){if(!enabled()||MENU==null||!NativeConfigurationAccess.canEditDialog(actor,world,pos)||!(world.getBlockEntity(pos) instanceof NativeRampEntity tile)||tile.inert())return false;if(actor instanceof ServerPlayer player){if(!player.getServer().isSameThread()||tile.controller()==null)return false;player.openMenu(new SimpleMenuProvider((window,inventory,ignored)->menu(window,tile,true),Component.literal("Ramp Controller")),buffer->buffer.writeBlockPos(pos));}return true;}
    private NativeRampBindings(){}
}
