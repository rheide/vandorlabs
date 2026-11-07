package com.vandorlabs.neoforge;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Incremental native building slices; this target does not convert legacy worlds. */
@Mod("vandorlabs")
public final class VandorLabsBootstrap {
    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(Registries.BLOCK, "vandorlabs");
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(Registries.ITEM, "vandorlabs");
    public static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, "vandorlabs");
    // Legacy setResistance(10) yields effective explosion resistance 10 * 3 / 5.
    public static final DeferredHolder<Block, Block> MATTER_BLOCK = BLOCKS.register("programmable_matter_block",
        () -> new Block(BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(5, 6)
            .sound(SoundType.METAL).requiresCorrectToolForDrops()));
    public static final DeferredHolder<Item, BlockItem> MATTER_BLOCK_ITEM = ITEMS.register("programmable_matter_block",
        () -> new BlockItem(MATTER_BLOCK.get(), new Item.Properties()));
    public static final DeferredHolder<Item, Item> MATTER_INGOT = ITEMS.register("programmable_matter_ingot", () -> new Item(new Item.Properties()));
    public static final DeferredHolder<Item, Item> ALLOY_INGOT = ITEMS.register("industrial_alloy_ingot", () -> new Item(new Item.Properties()));
    public static final DeferredHolder<Item,Item> CONFIGURIZER=ITEMS.register("configurizer",()->new Item(new Item.Properties().stacksTo(1)){
        @Override public net.minecraft.world.InteractionResult useOn(net.minecraft.world.item.context.UseOnContext context){
            return context.getHand()==net.minecraft.world.InteractionHand.MAIN_HAND&&context.getPlayer()!=null&&(CubeConfiguration.open(context.getPlayer(),context.getLevel(),context.getClickedPos())||(context.getLevel().getBlockState(context.getClickedPos()).getBlock() instanceof com.vandorlabs.neoforge.screen.NativeScreenBlock screen&&screen.openConfiguration(context.getPlayer(),context.getLevel(),context.getClickedPos()))||com.vandorlabs.neoforge.door.DoorConfiguration.open(context.getPlayer(),context.getLevel(),context.getClickedPos())||(context.getLevel().getBlockState(context.getClickedPos()).getBlock() instanceof com.vandorlabs.neoforge.segmented.NativeSegmentedDoorBlock segmented&&segmented.openConfiguration(context.getPlayer(),context.getLevel(),context.getClickedPos()))||BinaryControlConfiguration.open(context.getPlayer(),context.getLevel(),context.getClickedPos())||WirelessConfiguration.open(context.getPlayer(),context.getLevel(),context.getClickedPos()))
                ?net.minecraft.world.InteractionResult.sidedSuccess(context.getLevel().isClientSide):net.minecraft.world.InteractionResult.PASS;
        }
    });
    public static final DeferredRegister<net.minecraft.world.level.block.entity.BlockEntityType<?>> ENTITIES=DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE,"vandorlabs");
    public static final DeferredRegister<net.minecraft.world.inventory.MenuType<?>> MENUS=DeferredRegister.create(Registries.MENU,"vandorlabs");
    public static final DeferredHolder<Block,ProgrammableBlock> PROGRAMMABLE_BLOCK=BLOCKS.register("programmable_block",
        ()->new ProgrammableBlock(BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(5,6).sound(SoundType.METAL).requiresCorrectToolForDrops()));
    public static final DeferredHolder<Item,BlockItem> PROGRAMMABLE_ITEM=ITEMS.register("programmable_block",()->new BlockItem(PROGRAMMABLE_BLOCK.get(),new Item.Properties()));
    public static final DeferredHolder<Block,com.vandorlabs.neoforge.shape.NativeProgrammableSlab> PROGRAMMABLE_SLAB=BLOCKS.register("programmable_slab",()->new com.vandorlabs.neoforge.shape.NativeProgrammableSlab(BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(5,6).sound(SoundType.METAL).requiresCorrectToolForDrops().dynamicShape()));
    public static final DeferredHolder<Block,com.vandorlabs.neoforge.shape.NativeProgrammableStairs> PROGRAMMABLE_STAIRS=BLOCKS.register("programmable_stairs",()->new com.vandorlabs.neoforge.shape.NativeProgrammableStairs(BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(5,6).sound(SoundType.METAL).requiresCorrectToolForDrops().dynamicShape()));
    public static final DeferredHolder<Item,BlockItem> PROGRAMMABLE_SLAB_ITEM=ITEMS.register("programmable_slab",()->new com.vandorlabs.neoforge.shape.NativeProgrammableSlabItem(PROGRAMMABLE_SLAB.get(),new Item.Properties()));
    public static final DeferredHolder<Item,BlockItem> PROGRAMMABLE_STAIRS_ITEM=ITEMS.register("programmable_stairs",()->new BlockItem(PROGRAMMABLE_STAIRS.get(),new Item.Properties()));
    public static final DeferredHolder<Block,com.vandorlabs.neoforge.shape.NativeFlatWallBlock> PROGRAMMABLE_WALL=BLOCKS.register("programmable_wall",()->new com.vandorlabs.neoforge.shape.NativeFlatWallBlock(BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(5,6).sound(SoundType.METAL).requiresCorrectToolForDrops().noOcclusion().dynamicShape(),false));
    public static final DeferredHolder<Item,BlockItem> PROGRAMMABLE_WALL_ITEM=ITEMS.register("programmable_wall",()->new BlockItem(PROGRAMMABLE_WALL.get(),new Item.Properties()));
    public static final DeferredHolder<Block,com.vandorlabs.neoforge.shape.NativeFlatWallBlock> PROGRAMMABLE_PORTHOLE_WALL=BLOCKS.register("programmable_porthole_wall",()->new com.vandorlabs.neoforge.shape.NativeFlatWallBlock(BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(5,6).sound(SoundType.METAL).requiresCorrectToolForDrops().noOcclusion().dynamicShape(),true));
    public static final DeferredHolder<Item,BlockItem> PROGRAMMABLE_PORTHOLE_WALL_ITEM=ITEMS.register("programmable_porthole_wall",()->new BlockItem(PROGRAMMABLE_PORTHOLE_WALL.get(),new Item.Properties()));
    public static final DeferredHolder<Block,com.vandorlabs.neoforge.shape.NativeDiagonalWallBlock> PROGRAMMABLE_DIAGONAL_WALL=BLOCKS.register("programmable_diagonal_wall",()->new com.vandorlabs.neoforge.shape.NativeDiagonalWallBlock(BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(5,6).sound(SoundType.METAL).requiresCorrectToolForDrops().noOcclusion().dynamicShape(),false));
    public static final DeferredHolder<Item,BlockItem> PROGRAMMABLE_DIAGONAL_WALL_ITEM=ITEMS.register("programmable_diagonal_wall",()->new com.vandorlabs.neoforge.shape.NativeDiagonalWallItem(PROGRAMMABLE_DIAGONAL_WALL.get(),new Item.Properties()));
    public static final DeferredHolder<Block,com.vandorlabs.neoforge.shape.NativeDiagonalWallBlock> PROGRAMMABLE_DIAGONAL_PORTHOLE=BLOCKS.register("programmable_diagonal_porthole",()->new com.vandorlabs.neoforge.shape.NativeDiagonalWallBlock(BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(5,6).sound(SoundType.METAL).requiresCorrectToolForDrops().noOcclusion().dynamicShape(),true));
    public static final DeferredHolder<Item,BlockItem> PROGRAMMABLE_DIAGONAL_PORTHOLE_ITEM=ITEMS.register("programmable_diagonal_porthole",()->new com.vandorlabs.neoforge.shape.NativeDiagonalWallItem(PROGRAMMABLE_DIAGONAL_PORTHOLE.get(),new Item.Properties()));
    public static final DeferredHolder<Block,com.vandorlabs.neoforge.shape.NativeDiagonalWallBlock> PROGRAMMABLE_DIAGONAL_GLASS=BLOCKS.register("programmable_diagonal_glass",()->new com.vandorlabs.neoforge.shape.NativeDiagonalWallBlock(BlockBehaviour.Properties.of().mapColor(MapColor.NONE).strength(.5F,1.2F).sound(SoundType.GLASS).noOcclusion().dynamicShape(),com.vandorlabs.neoforge.shape.NativePanelKind.GLASS));
    public static final DeferredHolder<Item,BlockItem> PROGRAMMABLE_DIAGONAL_GLASS_ITEM=ITEMS.register("programmable_diagonal_glass",()->new com.vandorlabs.neoforge.shape.NativeDiagonalWallItem(PROGRAMMABLE_DIAGONAL_GLASS.get(),new Item.Properties()));
    public static final DeferredHolder<Block,com.vandorlabs.neoforge.shape.NativeFlatWallBlock> PROGRAMMABLE_PORTHOLE_BLOCK=BLOCKS.register("programmable_porthole_block",()->new com.vandorlabs.neoforge.shape.NativeFlatWallBlock(BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(5,6).sound(SoundType.METAL).requiresCorrectToolForDrops().noOcclusion().dynamicShape(),true,true));
    public static final DeferredHolder<Item,BlockItem> PROGRAMMABLE_PORTHOLE_BLOCK_ITEM=ITEMS.register("programmable_porthole_block",()->new BlockItem(PROGRAMMABLE_PORTHOLE_BLOCK.get(),new Item.Properties()));
    public static final DeferredHolder<net.minecraft.world.level.block.entity.BlockEntityType<?>,net.minecraft.world.level.block.entity.BlockEntityType<net.minecraft.world.level.block.entity.BlockEntity>> PROGRAMMABLE_ENTITY=ENTITIES.register("programmable_viewscreen",
        ()->net.minecraft.world.level.block.entity.BlockEntityType.Builder.of(VandorLabsBootstrap::programmableEntity,PROGRAMMABLE_BLOCK.get(),PROGRAMMABLE_SLAB.get(),PROGRAMMABLE_STAIRS.get(),PROGRAMMABLE_WALL.get(),PROGRAMMABLE_PORTHOLE_WALL.get(),PROGRAMMABLE_DIAGONAL_WALL.get(),PROGRAMMABLE_DIAGONAL_PORTHOLE.get(),PROGRAMMABLE_DIAGONAL_GLASS.get(),PROGRAMMABLE_PORTHOLE_BLOCK.get()).build(null));
    public static final DeferredHolder<net.minecraft.world.inventory.MenuType<?>,net.minecraft.world.inventory.MenuType<ProgrammableMenu>> PROGRAMMABLE_MENU=MENUS.register("programmable_block",()->net.neoforged.neoforge.common.extensions.IMenuTypeExtension.create((id,inventory,buffer)->new ProgrammableMenu(id,inventory,buffer.readBlockPos())));
    public static final DeferredHolder<Block,NativeTriggerBlock> TRIGGER_BLOCK=BLOCKS.register("programmable_trigger_block",
        ()->new NativeTriggerBlock(BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(5,6).sound(SoundType.METAL).requiresCorrectToolForDrops()));
    public static final DeferredHolder<Item,BlockItem> TRIGGER_ITEM=ITEMS.register("programmable_trigger_block",()->new BlockItem(TRIGGER_BLOCK.get(),new Item.Properties()));
    public static final DeferredHolder<net.minecraft.world.level.block.entity.BlockEntityType<?>,net.minecraft.world.level.block.entity.BlockEntityType<NativeTriggerBlockEntity>> TRIGGER_ENTITY=ENTITIES.register("programmable_trigger_block",
        ()->net.minecraft.world.level.block.entity.BlockEntityType.Builder.of(NativeTriggerBlockEntity::new,TRIGGER_BLOCK.get()).build(null));
    public static final DeferredHolder<net.minecraft.world.inventory.MenuType<?>,net.minecraft.world.inventory.MenuType<TriggerMenu>> TRIGGER_MENU=MENUS.register("programmable_trigger",()->net.neoforged.neoforge.common.extensions.IMenuTypeExtension.create((id,inventory,buffer)->new TriggerMenu(id,inventory,buffer.readBlockPos())));
    public static final DeferredHolder<Item,com.vandorlabs.neoforge.redstone.NativeTransmitterItem> REDSTONE_TRANSMITTER=ITEMS.register("redstone_transmitter",com.vandorlabs.neoforge.redstone.NativeTransmitterItem::new);
    public static final DeferredHolder<net.minecraft.world.inventory.MenuType<?>,net.minecraft.world.inventory.MenuType<com.vandorlabs.neoforge.redstone.TransmitterMenu>> TRANSMITTER_MENU=MENUS.register("redstone_transmitter",()->net.neoforged.neoforge.common.extensions.IMenuTypeExtension.create((id,inventory,buffer)->new com.vandorlabs.neoforge.redstone.TransmitterMenu(id,inventory,buffer.readEnum(net.minecraft.world.InteractionHand.class))));
    public static final DeferredHolder<Block,NativeWirelessRedstoneBlock> REDSTONE_TRANSMITTER_BLOCK=BLOCKS.register("redstone_transmitter_block",()->new NativeWirelessRedstoneBlock(BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(5,6).sound(SoundType.METAL).requiresCorrectToolForDrops(),true));
    public static final DeferredHolder<Item,BlockItem> REDSTONE_TRANSMITTER_BLOCK_ITEM=ITEMS.register("redstone_transmitter_block",()->new BlockItem(REDSTONE_TRANSMITTER_BLOCK.get(),new Item.Properties()));
    public static final DeferredHolder<Block,NativeWirelessRedstoneBlock> REDSTONE_RECEIVER_BLOCK=BLOCKS.register("redstone_receiver_block",()->new NativeWirelessRedstoneBlock(BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(5,6).sound(SoundType.METAL).requiresCorrectToolForDrops(),false));
    public static final DeferredHolder<Item,BlockItem> REDSTONE_RECEIVER_BLOCK_ITEM=ITEMS.register("redstone_receiver_block",()->new BlockItem(REDSTONE_RECEIVER_BLOCK.get(),new Item.Properties()));
    public static final DeferredHolder<net.minecraft.world.level.block.entity.BlockEntityType<?>,net.minecraft.world.level.block.entity.BlockEntityType<NativeWirelessRedstoneBlockEntity>> WIRELESS_ENTITY=ENTITIES.register("wireless_redstone",()->net.minecraft.world.level.block.entity.BlockEntityType.Builder.of(NativeWirelessRedstoneBlockEntity::new,REDSTONE_TRANSMITTER_BLOCK.get(),REDSTONE_RECEIVER_BLOCK.get()).build(null));
    public static final DeferredHolder<net.minecraft.world.inventory.MenuType<?>,net.minecraft.world.inventory.MenuType<WirelessMenu>> WIRELESS_MENU=MENUS.register("wireless_redstone",()->net.neoforged.neoforge.common.extensions.IMenuTypeExtension.create((id,inventory,buffer)->new WirelessMenu(id,inventory,buffer.readBlockPos())));
    public static final DeferredHolder<Block,NativeMountedSwitchBlock> PUSH_BUTTON=BLOCKS.register("push_button",()->new NativeMountedSwitchBlock(BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(5,6).sound(SoundType.METAL).noOcclusion().dynamicShape().requiresCorrectToolForDrops().lightLevel(state->state.getValue(NativeMountedSwitchBlock.ON)?5:0),true));
    public static final DeferredHolder<Block,NativeMountedSwitchBlock> ROCKER_SWITCH=BLOCKS.register("rocker_switch",()->new NativeMountedSwitchBlock(BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(5,6).sound(SoundType.METAL).noOcclusion().dynamicShape().requiresCorrectToolForDrops().lightLevel(state->state.getValue(NativeMountedSwitchBlock.ON)?5:0),false));
    public static final DeferredHolder<Item,BlockItem> PUSH_BUTTON_ITEM=ITEMS.register("push_button",()->new BlockItem(PUSH_BUTTON.get(),new Item.Properties()));
    public static final DeferredHolder<Item,BlockItem> ROCKER_SWITCH_ITEM=ITEMS.register("rocker_switch",()->new BlockItem(ROCKER_SWITCH.get(),new Item.Properties()));
    public static final DeferredHolder<Block,NativePowerLeverBlock> INDUSTRIAL_POWER_LEVER=BLOCKS.register("industrial_power_lever",()->powerLever(false,1));
    public static final DeferredHolder<Block,NativePowerLeverBlock> COMPACT_POWER_LEVER=BLOCKS.register("compact_power_lever",()->powerLever(false,0));
    public static final DeferredHolder<Block,NativePowerLeverBlock> SMALL_POWER_LEVER=BLOCKS.register("small_power_lever",()->powerLever(true,0));
    public static final DeferredHolder<Block,NativePowerLeverBlock> LARGE_POWER_LEVER=BLOCKS.register("large_power_lever",()->powerLever(true,1));
    public static final DeferredHolder<Item,BlockItem> INDUSTRIAL_POWER_LEVER_ITEM=ITEMS.register("industrial_power_lever",()->new BlockItem(INDUSTRIAL_POWER_LEVER.get(),new Item.Properties()));
    public static final DeferredHolder<Item,BlockItem> COMPACT_POWER_LEVER_ITEM=ITEMS.register("compact_power_lever",()->new BlockItem(COMPACT_POWER_LEVER.get(),new Item.Properties()));
    public static final DeferredHolder<Item,BlockItem> SMALL_POWER_LEVER_ITEM=ITEMS.register("small_power_lever",()->new BlockItem(SMALL_POWER_LEVER.get(),new Item.Properties()));
    public static final DeferredHolder<Item,BlockItem> LARGE_POWER_LEVER_ITEM=ITEMS.register("large_power_lever",()->new BlockItem(LARGE_POWER_LEVER.get(),new Item.Properties()));
    private static NativePowerLeverBlock powerLever(boolean twin,int size){return new NativePowerLeverBlock(BlockBehaviour.Properties.of().mapColor(MapColor.NONE).strength(.5f,.5f).sound(SoundType.METAL).noOcclusion().dynamicShape().pushReaction(net.minecraft.world.level.material.PushReaction.DESTROY),twin,size);}
    public static final DeferredHolder<Block,NativeSignalControlBlock> THRUSTER_LEVER=BLOCKS.register("thruster_lever",()->signalControl(0,true));
    public static final DeferredHolder<Item,BlockItem> THRUSTER_LEVER_ITEM=ITEMS.register("thruster_lever",()->new BlockItem(THRUSTER_LEVER.get(),new Item.Properties()));
    public static final DeferredHolder<Block,NativeSignalControlBlock> AIRLINER_THROTTLE=BLOCKS.register("airliner_throttle",()->signalControl(1,true));
    public static final DeferredHolder<Item,BlockItem> AIRLINER_THROTTLE_ITEM=ITEMS.register("airliner_throttle",()->new BlockItem(AIRLINER_THROTTLE.get(),new Item.Properties()));
    public static final DeferredHolder<Block,NativeSignalControlBlock> FIGHTER_THROTTLE=BLOCKS.register("fighter_throttle",()->signalControl(2,true));
    public static final DeferredHolder<Item,BlockItem> FIGHTER_THROTTLE_ITEM=ITEMS.register("fighter_throttle",()->new BlockItem(FIGHTER_THROTTLE.get(),new Item.Properties()));
    public static final DeferredHolder<Block,NativeSignalControlBlock> WALL_SLIDER=BLOCKS.register("wall_slider",()->signalControl(0,false));
    public static final DeferredHolder<Item,BlockItem> WALL_SLIDER_ITEM=ITEMS.register("wall_slider",()->new BlockItem(WALL_SLIDER.get(),new Item.Properties()));
    private static NativeSignalControlBlock signalControl(int type,boolean selectable){return new NativeSignalControlBlock(BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(5,6).sound(SoundType.METAL).noOcclusion().dynamicShape().requiresCorrectToolForDrops(),type,selectable);}
    public static final DeferredHolder<net.minecraft.world.level.block.entity.BlockEntityType<?>,net.minecraft.world.level.block.entity.BlockEntityType<NativeSignalControlBlockEntity>> SIGNAL_CONTROL_ENTITY=ENTITIES.register("signal_control",()->net.minecraft.world.level.block.entity.BlockEntityType.Builder.of(NativeSignalControlBlockEntity::new,THRUSTER_LEVER.get(),AIRLINER_THROTTLE.get(),FIGHTER_THROTTLE.get(),WALL_SLIDER.get()).build(null));
    public static final DeferredHolder<net.minecraft.world.level.block.entity.BlockEntityType<?>,net.minecraft.world.level.block.entity.BlockEntityType<NativeBinaryControlBlockEntity>> BINARY_CONTROL_ENTITY=ENTITIES.register("redstone_channel",()->net.minecraft.world.level.block.entity.BlockEntityType.Builder.of(NativeBinaryControlBlockEntity::new,PUSH_BUTTON.get(),ROCKER_SWITCH.get(),INDUSTRIAL_POWER_LEVER.get(),COMPACT_POWER_LEVER.get(),SMALL_POWER_LEVER.get(),LARGE_POWER_LEVER.get()).build(null));
    public static final DeferredHolder<net.minecraft.world.inventory.MenuType<?>,net.minecraft.world.inventory.MenuType<BinaryControlMenu>> BINARY_CONTROL_MENU=MENUS.register("binary_control",()->net.neoforged.neoforge.common.extensions.IMenuTypeExtension.create((id,inventory,buffer)->new BinaryControlMenu(id,inventory,buffer.readBlockPos())));
    static {
        TABS.register("vandorlabs", () -> CreativeModeTab.builder().title(Component.translatable("itemGroup.vandorlabs"))
            .icon(() -> MATTER_BLOCK_ITEM.get().getDefaultInstance())
            .displayItems((parameters, output) -> {
                for(int tier=0;tier<4;tier++)output.accept(com.vandorlabs.neoforge.redstone.NativeTransmitterItem.variant(REDSTONE_TRANSMITTER.get(),tier));
                output.accept(REDSTONE_TRANSMITTER_BLOCK_ITEM.get());output.accept(REDSTONE_RECEIVER_BLOCK_ITEM.get());output.accept(THRUSTER_LEVER_ITEM.get());output.accept(WALL_SLIDER_ITEM.get());output.accept(INDUSTRIAL_POWER_LEVER_ITEM.get());output.accept(SMALL_POWER_LEVER_ITEM.get());output.accept(PUSH_BUTTON_ITEM.get());output.accept(ROCKER_SWITCH_ITEM.get());output.accept(PROGRAMMABLE_ITEM.get());output.accept(PROGRAMMABLE_SLAB_ITEM.get());output.accept(PROGRAMMABLE_STAIRS_ITEM.get());output.accept(PROGRAMMABLE_WALL_ITEM.get());output.accept(PROGRAMMABLE_PORTHOLE_WALL_ITEM.get());output.accept(PROGRAMMABLE_PORTHOLE_BLOCK_ITEM.get());output.accept(PROGRAMMABLE_DIAGONAL_WALL_ITEM.get());output.accept(PROGRAMMABLE_DIAGONAL_PORTHOLE_ITEM.get());output.accept(PROGRAMMABLE_DIAGONAL_GLASS_ITEM.get());output.accept(TRIGGER_ITEM.get());output.accept(com.vandorlabs.neoforge.door.NativeDoorRegistrations.ITEM.get());output.accept(com.vandorlabs.neoforge.door.NativeLargeDoorRegistrations.ITEM.get());output.accept(com.vandorlabs.neoforge.segmented.NativeSegmentedRegistrations.ITEM.get());output.accept(com.vandorlabs.neoforge.trapdoor.NativeTrapdoorRegistrations.ITEM.get());output.accept(com.vandorlabs.neoforge.trapdoor.NativeTrapdoorRegistrations.DIAGONAL_ITEM.get());output.accept(com.vandorlabs.neoforge.light.NativeLightRegistrations.ITEM.get());output.accept(com.vandorlabs.neoforge.light.NativeLightRegistrations.FRAME_ITEM.get());output.accept(com.vandorlabs.neoforge.light.NativeLightRegistrations.SLAB_ITEM.get());for(var engine:com.vandorlabs.neoforge.propulsion.NativePropulsionRegistrations.ITEMS.values())output.accept(engine.get());output.accept(CONFIGURIZER.get());output.accept(MATTER_INGOT.get()); output.accept(MATTER_BLOCK_ITEM.get()); output.accept(ALLOY_INGOT.get());for(var credential:com.vandorlabs.neoforge.credentials.NativeCredentialItems.creativeVariants())output.accept(credential);
            }).build());
    }
    private static net.minecraft.world.level.block.entity.BlockEntity programmableEntity(net.minecraft.core.BlockPos pos,net.minecraft.world.level.block.state.BlockState state){
        if(state.getBlock() instanceof com.vandorlabs.neoforge.shape.NativeDiagonalWallBlock wall)return new com.vandorlabs.neoforge.shape.NativeWallEntity(PROGRAMMABLE_ENTITY.get(),pos,state,wall.panelKind());
        if(state.getBlock() instanceof com.vandorlabs.neoforge.shape.NativeFlatWallBlock wall)return new com.vandorlabs.neoforge.shape.NativeWallEntity(PROGRAMMABLE_ENTITY.get(),pos,state,wall.isPorthole());
        if(state.getBlock() instanceof com.vandorlabs.neoforge.shape.NativeProgrammableSlab)return new com.vandorlabs.neoforge.shape.ProgrammableShapeEntity(PROGRAMMABLE_ENTITY.get(),pos,state,false);
        if(state.getBlock() instanceof com.vandorlabs.neoforge.shape.NativeProgrammableStairs)return new com.vandorlabs.neoforge.shape.ProgrammableShapeEntity(PROGRAMMABLE_ENTITY.get(),pos,state,true);
        return new ProgrammableBlockEntity(pos,state);
    }
    public VandorLabsBootstrap(IEventBus bus) {
        FilesystemMaterials.initialize();com.vandorlabs.neoforge.redstone.TransmitterCopyRegistration.register(bus);
        com.vandorlabs.neoforge.door.NativeDoorRegistrations.register(bus);
        com.vandorlabs.neoforge.door.NativeLargeDoorRegistrations.register();
        com.vandorlabs.neoforge.segmented.NativeSegmentedRegistrations.register(bus);
        com.vandorlabs.neoforge.trapdoor.NativeTrapdoorRegistrations.register(bus);
        com.vandorlabs.neoforge.light.NativeLightRegistrations.register(bus);
        com.vandorlabs.neoforge.propulsion.NativePropulsionRegistrations.register(bus);
        BLOCKS.register(bus); ITEMS.register(bus);com.vandorlabs.neoforge.credentials.NativeCredentialItems.register(bus); TABS.register(bus);ENTITIES.register(bus);MENUS.register(bus);bus.addListener(WirelessPayload::register);bus.addListener(WirelessTransmissionPayload::register);bus.addListener(SignalControlPayload::register);bus.addListener(PowerLeverPayload::register);bus.addListener(BinaryControlPayload::register);bus.addListener(BinaryTransmissionPayload::register);bus.addListener(FinishPayload::register);bus.addListener(com.vandorlabs.neoforge.shape.ShapeAppearancePayload::register);bus.addListener(com.vandorlabs.neoforge.shape.WallAppearancePayload::register);bus.addListener(com.vandorlabs.neoforge.shape.PortholeGlassPayload::register);bus.addListener(com.vandorlabs.neoforge.shape.DiagonalGeometryPayload::register);bus.addListener(TriggerPayload::register);bus.addListener(com.vandorlabs.neoforge.redstone.TransmitterPayload::register);bus.addListener(NativeMaterialRegistry::commonSetup);
    }
}
