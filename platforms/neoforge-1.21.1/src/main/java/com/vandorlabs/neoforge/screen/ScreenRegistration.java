package com.vandorlabs.neoforge.screen;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** The programmable viewscreen adapter has isolated registries and runtime state. */
public final class ScreenRegistration {
    private static final DeferredRegister<Block> BLOCKS=DeferredRegister.create(Registries.BLOCK,"vandorlabs");
    private static final DeferredRegister<Item> ITEMS=DeferredRegister.create(Registries.ITEM,"vandorlabs");
    private static final DeferredRegister<BlockEntityType<?>> TILES=DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE,"vandorlabs");
    private static final DeferredRegister<MenuType<?>> MENUS=DeferredRegister.create(Registries.MENU,"vandorlabs");
    public static final DeferredHolder<Block,ProgrammableScreenBlock> BLOCK=BLOCKS.register("programmable_viewscreen",()->new ProgrammableScreenBlock(BlockBehaviour.Properties.of().mapColor(net.minecraft.world.level.material.MapColor.METAL).strength(5,6).sound(net.minecraft.world.level.block.SoundType.METAL).requiresCorrectToolForDrops()));
    public static final DeferredHolder<Item,BlockItem> ITEM=ITEMS.register("programmable_viewscreen",()->new BlockItem(BLOCK.get(),new Item.Properties()));
    public static final DeferredHolder<Block,ScreenFamilyBlocks.Console> CONSOLE=BLOCKS.register("programmable_console",()->new ScreenFamilyBlocks.Console(properties()));
    public static final DeferredHolder<Block,ScreenFamilyBlocks.HalfConsole> HALF_CONSOLE=BLOCKS.register("programmable_half_console",()->new ScreenFamilyBlocks.HalfConsole(properties()));
    public static final DeferredHolder<Block,ScreenFamilyBlocks.FullInput> FULL_INPUT=BLOCKS.register("programmable_input",()->new ScreenFamilyBlocks.FullInput(properties()));
    public static final DeferredHolder<Block,ScreenFamilyBlocks.HalfInput> HALF_INPUT=BLOCKS.register("programmable_half_input",()->new ScreenFamilyBlocks.HalfInput(properties()));
    public static final DeferredHolder<Block,ScreenFamilyBlocks.Diagonal> DIAGONAL=BLOCKS.register("programmable_diagonal_screen",()->new ScreenFamilyBlocks.Diagonal(properties()));
    public static final DeferredHolder<Block,ScreenFamilyBlocks.DiagonalHalf> DIAGONAL_HALF=BLOCKS.register("programmable_diagonal_half_console",()->new ScreenFamilyBlocks.DiagonalHalf(properties()));
    private static final java.util.List<DeferredHolder<Block,? extends ProgrammableScreenBlock>> FAMILIES=java.util.List.of(BLOCK,CONSOLE,HALF_CONSOLE,FULL_INPUT,HALF_INPUT,DIAGONAL,DIAGONAL_HALF);
    static {for(var family:FAMILIES)if(family!=BLOCK)ITEMS.register(family.getId().getPath(),()->new ScreenBlockItem(family.get(),new Item.Properties()));}
    private static BlockBehaviour.Properties properties(){return BlockBehaviour.Properties.of().mapColor(net.minecraft.world.level.material.MapColor.METAL).strength(5,6).sound(net.minecraft.world.level.block.SoundType.METAL).requiresCorrectToolForDrops().noOcclusion().dynamicShape().forceSolidOn();}
    public static java.util.List<net.minecraft.world.item.ItemStack> creativeItems(){return FAMILIES.stream().map(family->new net.minecraft.world.item.ItemStack(family.get())).toList();}
    public static boolean isScreen(Block block){for(var family:FAMILIES)if(family.get()==block)return true;return false;}
    public static final DeferredHolder<BlockEntityType<?>,BlockEntityType<ProgrammableScreenEntity>> TILE=TILES.register("programmable_screen",()->BlockEntityType.Builder.of(ProgrammableScreenEntity::new,FAMILIES.stream().map(DeferredHolder::get).toArray(Block[]::new)).build(null));
    public static final DeferredHolder<MenuType<?>,MenuType<ScreenMenu>> MENU=MENUS.register("screen_selector",()->IMenuTypeExtension.create((id,inventory,data)->new ScreenMenu(id,inventory,data.readBlockPos(),data.readBoolean(),data.readUnsignedByte())));
    private ScreenRegistration(){}
    public static void install(IEventBus bus){BLOCKS.register(bus);ITEMS.register(bus);TILES.register(bus);MENUS.register(bus);bus.addListener(ScreenPayload::register);bus.addListener(RedstoneConfigurationPayload::register);}
}
