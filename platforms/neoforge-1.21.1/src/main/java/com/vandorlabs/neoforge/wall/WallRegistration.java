package com.vandorlabs.neoforge.wall;

import com.vandorlabs.neoforge.VandorLabsModern;
import com.vandorlabs.ui.WallDialog;
import java.util.List;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.neoforge.registries.DeferredHolder;

public final class WallRegistration {
    public static final DeferredHolder<Block,WallBlock> WALL=block("programmable_wall",WallDialog.Profile.PLAIN);
    public static final DeferredHolder<Block,WallBlock> PORTHOLE=block("programmable_porthole_wall",WallDialog.Profile.PORTHOLE);
    public static final DeferredHolder<Block,WallBlock> FULL_PORTHOLE=block("programmable_porthole_block",WallDialog.Profile.FULL_PORTHOLE);
    public static final DeferredHolder<Item,BlockItem> WALL_ITEM=item("programmable_wall",WALL);
    public static final DeferredHolder<Item,BlockItem> PORTHOLE_ITEM=item("programmable_porthole_wall",PORTHOLE);
    public static final DeferredHolder<Item,BlockItem> FULL_PORTHOLE_ITEM=item("programmable_porthole_block",FULL_PORTHOLE);
    public static final DeferredHolder<BlockEntityType<?>,BlockEntityType<WallEntity>> ENTITY=VandorLabsModern.ENTITIES.register("programmable_wall",()->BlockEntityType.Builder.of(WallEntity::new,WALL.get(),PORTHOLE.get(),FULL_PORTHOLE.get()).build(null));
    private static DeferredHolder<Block,WallBlock> block(String id,WallDialog.Profile profile){return VandorLabsModern.BLOCKS.register(id,()->new WallBlock(profile,BlockBehaviour.Properties.of().mapColor(net.minecraft.world.level.material.MapColor.METAL).strength(5,6).sound(net.minecraft.world.level.block.SoundType.METAL).requiresCorrectToolForDrops().noOcclusion().dynamicShape().forceSolidOn().isSuffocating((s,l,p)->false).isViewBlocking((s,l,p)->false)));}
    private static DeferredHolder<Item,BlockItem> item(String id,DeferredHolder<Block,WallBlock> block){return VandorLabsModern.ITEMS.register(id,()->new BlockItem(block.get(),new Item.Properties()));}
    public static void initialize(){WallMenu.initialize();}
    public static List<ItemStack> creativeItems(){return List.of(new ItemStack(WALL_ITEM.get()),new ItemStack(PORTHOLE_ITEM.get()),new ItemStack(FULL_PORTHOLE_ITEM.get()));}
    private WallRegistration(){}
}
