package com.vandorlabs.neoforge.light;

import com.vandorlabs.neoforge.VandorLabsBootstrap;
import com.vandorlabs.neoforge.screen.NativeScreenCatalog;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.neoforge.registries.DeferredHolder;

/** Inactive canonical definitions. Loading this class must precede deferred-register attachment. */
public final class NativeLightRegistrations {
    public static final String ID="programmable_light", FRAME_ID="programmable_light_frame", SLAB_ID="programmable_light_slab";
    public static final DeferredHolder<Block,NativeLightBlock> BLOCK=VandorLabsBootstrap.BLOCKS.register(ID,()->block(NativeLightBlock.Kind.FULL));
    public static final DeferredHolder<Block,NativeLightBlock> FRAME=VandorLabsBootstrap.BLOCKS.register(FRAME_ID,()->block(NativeLightBlock.Kind.FRAME));
    public static final DeferredHolder<Block,NativeLightBlock> SLAB=VandorLabsBootstrap.BLOCKS.register(SLAB_ID,()->block(NativeLightBlock.Kind.SLAB));
    public static final DeferredHolder<Item,NativeLightItem> ITEM=VandorLabsBootstrap.ITEMS.register(ID,()->new NativeLightItem(BLOCK.get(),new Item.Properties()));
    public static final DeferredHolder<Item,NativeLightItem> FRAME_ITEM=VandorLabsBootstrap.ITEMS.register(FRAME_ID,()->new NativeLightItem(FRAME.get(),new Item.Properties()));
    public static final DeferredHolder<Item,NativeLightItem> SLAB_ITEM=VandorLabsBootstrap.ITEMS.register(SLAB_ID,()->new NativeLightItem(SLAB.get(),new Item.Properties()));
    public static final DeferredHolder<BlockEntityType<?>,BlockEntityType<NativeLightEntity>> ENTITY=VandorLabsBootstrap.ENTITIES.register(ID,()->BlockEntityType.Builder.<NativeLightEntity>of((pos,state)->new NativeLightEntity(NativeLightRegistrations.ENTITY.get(),pos,state,NativeScreenCatalog.INSTANCE),BLOCK.get(),FRAME.get(),SLAB.get()).build(null));
    public static final DeferredHolder<MenuType<?>,MenuType<NativeLightMenu>> MENU=VandorLabsBootstrap.MENUS.register(ID,()->net.neoforged.neoforge.common.extensions.IMenuTypeExtension.create((id,inventory,buffer)->new NativeLightMenu(NativeLightRegistrations.MENU.get(),id,inventory,buffer.readBlockPos())));
    private static NativeLightBlock block(NativeLightBlock.Kind kind){
        var properties=BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(5,10).sound(SoundType.METAL).requiresCorrectToolForDrops().isRedstoneConductor((s,w,p)->false);
        return kind==NativeLightBlock.Kind.SLAB?new RegisteredSlab(properties):new Registered(kind,properties);
    }
    private static class Registered extends NativeLightBlock {
        Registered(Kind kind,Properties properties){super(kind,properties,()->ENTITY.get());}
        @Override protected MenuType<NativeLightMenu> menuType(){return MENU.get();}
        @Override public RenderShape getRenderShape(net.minecraft.world.level.block.state.BlockState state){return RenderShape.MODEL;}
    }
    private static final class RegisteredSlab extends Registered {
        RegisteredSlab(Properties properties){super(Kind.SLAB,properties);}
        @Override protected void createBlockStateDefinition(net.minecraft.world.level.block.state.StateDefinition.Builder<Block,net.minecraft.world.level.block.state.BlockState> builder){super.createBlockStateDefinition(builder);builder.add(HALF);}
    }
    private NativeLightRegistrations(){}
}
