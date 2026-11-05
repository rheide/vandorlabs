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
            return context.getHand()==net.minecraft.world.InteractionHand.MAIN_HAND&&context.getPlayer()!=null&&CubeConfiguration.open(context.getPlayer(),context.getLevel(),context.getClickedPos())
                ?net.minecraft.world.InteractionResult.sidedSuccess(context.getLevel().isClientSide):net.minecraft.world.InteractionResult.PASS;
        }
    });
    public static final DeferredRegister<net.minecraft.world.level.block.entity.BlockEntityType<?>> ENTITIES=DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE,"vandorlabs");
    public static final DeferredRegister<net.minecraft.world.inventory.MenuType<?>> MENUS=DeferredRegister.create(Registries.MENU,"vandorlabs");
    public static final DeferredHolder<Block,ProgrammableBlock> PROGRAMMABLE_BLOCK=BLOCKS.register("programmable_block",
        ()->new ProgrammableBlock(BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(5,6).sound(SoundType.METAL).requiresCorrectToolForDrops()));
    public static final DeferredHolder<Item,BlockItem> PROGRAMMABLE_ITEM=ITEMS.register("programmable_block",()->new BlockItem(PROGRAMMABLE_BLOCK.get(),new Item.Properties()));
    public static final DeferredHolder<net.minecraft.world.level.block.entity.BlockEntityType<?>,net.minecraft.world.level.block.entity.BlockEntityType<ProgrammableBlockEntity>> PROGRAMMABLE_ENTITY=ENTITIES.register("programmable_viewscreen",
        ()->net.minecraft.world.level.block.entity.BlockEntityType.Builder.of(ProgrammableBlockEntity::new,PROGRAMMABLE_BLOCK.get()).build(null));
    public static final DeferredHolder<net.minecraft.world.inventory.MenuType<?>,net.minecraft.world.inventory.MenuType<ProgrammableMenu>> PROGRAMMABLE_MENU=MENUS.register("programmable_block",()->net.neoforged.neoforge.common.extensions.IMenuTypeExtension.create((id,inventory,buffer)->new ProgrammableMenu(id,inventory,buffer.readBlockPos())));
    static {
        TABS.register("vandorlabs", () -> CreativeModeTab.builder().title(Component.translatable("itemGroup.vandorlabs"))
            .icon(() -> MATTER_BLOCK_ITEM.get().getDefaultInstance())
            .displayItems((parameters, output) -> {
                output.accept(PROGRAMMABLE_ITEM.get());output.accept(CONFIGURIZER.get());output.accept(MATTER_INGOT.get()); output.accept(MATTER_BLOCK_ITEM.get()); output.accept(ALLOY_INGOT.get());
            }).build());
    }
    public VandorLabsBootstrap(IEventBus bus) {
        FilesystemMaterials.initialize();
        BLOCKS.register(bus); ITEMS.register(bus); TABS.register(bus);ENTITIES.register(bus);MENUS.register(bus);bus.addListener(FinishPayload::register);
    }
}
