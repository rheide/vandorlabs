package com.vandorlabs.neoforge.seating;
import com.vandorlabs.neoforge.NativeConfigurationAccess;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.registries.RegisterEvent;
/** Canonical stationary seat families; Vehicle/Taxi controls are deliberately outside this adapter. */
@EventBusSubscriber(modid="vandorlabs",bus=EventBusSubscriber.Bus.MOD)
public final class NativeSeatBindings {
    public static final Map<NativeSeatKind,NativeSeatBlock> BLOCKS=new EnumMap<>(NativeSeatKind.class);
    public static final Map<NativeSeatKind,NativeSeatItem> ITEMS=new EnumMap<>(NativeSeatKind.class);
    public static final Map<NativeSeatKind,BlockEntityType<NativeChairEntity>> ENTITIES=new EnumMap<>(NativeSeatKind.class);
    public static BlockEntityType<NativeChairEntity> type(net.minecraft.world.level.block.state.BlockState state){return ENTITIES.get(((NativeSeatBlock)state.getBlock()).kind);}
    public static EntityType<NativeChairMount> MOUNT;public static MenuType<NativeSeatMenu> MENU;
    public static boolean enabled(){return Boolean.parseBoolean(System.getProperty("vandorlabs.seating","true"));}
    private static ResourceLocation id(String path){return ResourceLocation.fromNamespaceAndPath("vandorlabs",path);}
    @SubscribeEvent public static void register(RegisterEvent event){if(!enabled())return;
        event.register(Registries.BLOCK,h->{for(var kind:NativeSeatKind.values()){var properties=BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(5,6).sound(SoundType.METAL).requiresCorrectToolForDrops().noOcclusion().dynamicShape();var block=switch(kind){case ADVANCED->new NativeAdvancedChairBlock(properties);case COCKPIT->new NativeCockpitBlock(properties);default->new NativeSeatBlock(kind,properties);};BLOCKS.put(kind,block);h.register(id(kind.id),block);}});
        event.register(Registries.ITEM,h->{for(var kind:NativeSeatKind.values()){var item=new NativeSeatItem(BLOCKS.get(kind),new Item.Properties());ITEMS.put(kind,item);h.register(id(kind.id),item);}});
        event.register(Registries.BLOCK_ENTITY_TYPE,h->{for(var root:new NativeSeatKind[]{NativeSeatKind.CHAIR,NativeSeatKind.LUXURY,NativeSeatKind.PILOT,NativeSeatKind.ADVANCED,NativeSeatKind.COCKPIT}){var kinds=root==NativeSeatKind.LUXURY?List.of(NativeSeatKind.LUXURY,NativeSeatKind.MILITARY):List.of(root);var type=BlockEntityType.Builder.of(NativeChairEntity::new,kinds.stream().map(BLOCKS::get).toArray(Block[]::new)).build(null);for(var kind:kinds)ENTITIES.put(kind,type);h.register(id(root==NativeSeatKind.LUXURY?"connected_seat":root.id),type);}});
        event.register(Registries.ENTITY_TYPE,h->{MOUNT=EntityType.Builder.<NativeChairMount>of(NativeChairMount::new,MobCategory.MISC).sized(.01F,.01F).clientTrackingRange(8).updateInterval(10).noSummon().build("vandorlabs:chair_seat");h.register(id("chair_seat"),MOUNT);});
        event.register(Registries.MENU,h->{MENU=net.neoforged.neoforge.common.extensions.IMenuTypeExtension.create((window,inventory,buffer)->new NativeSeatMenu(MENU,window,inventory,buffer.readBlockPos(),buffer.isReadable()?buffer.readUnsignedByte():NativeChairArmrest.LEFT));h.register(id("programmable_chair"),MENU);});
    }
    @SubscribeEvent public static void packets(net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent event){NativeSeatPayload.register(event);NativeChairArmrestPayload.register(event);}
    @SubscribeEvent public static void creative(net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent event){if(enabled()&&event.getTabKey().location().equals(id("vandorlabs")))for(var item:ITEMS.values())event.accept(item);}
    public static boolean open(Player actor,Level world,BlockPos pos){
        if(!enabled()||MENU==null||!NativeSeatBlock.loaded(world,pos))return false;
        var owner=NativeSeatBlock.owner(world,pos);
        if(owner==null||!NativeConfigurationAccess.canEditDialog(actor,owner)
            ||owner.kind()==NativeSeatKind.ADVANCED&&!((NativeSeatBlock)owner.getBlockState().getBlock()).complete(world,owner.getBlockPos()))
            return false;
        if(owner.cockpitTouch()!=null){
            var start=actor.getEyePosition();
            var end=start.add(actor.getViewVector(1).scale(Math.min(8,actor.blockInteractionRange())));
            if(owner.kind()==NativeSeatKind.COCKPIT){
                var grip=NativeCockpitThrottle.pick(owner,owner.getBlockState().getValue(NativeSeatBlock.FACING),pos,start,end);
                if(grip!=null)return openThrottle(actor,owner,pos,grip);
            }
            boolean advancedGrip=owner.kind()==NativeSeatKind.ADVANCED&&NativeAdvancedChairGeometry.grip(
                owner,owner.getBlockState().getValue(NativeSeatBlock.FACING),pos,start,end)!=null;
            var panel=advancedGrip?null:owner.cockpitTouch().pick(pos,start,end);
            if(panel!=null)return openSurface(actor,owner,pos,panel);
            if(owner.kind()==NativeSeatKind.COCKPIT)return openSurface(actor,owner,pos,null);
        }
        if(actor instanceof ServerPlayer player){
            if(!player.getServer().isSameThread())return false;
            player.openMenu(new SimpleMenuProvider((window,inventory,ignored)->new NativeSeatMenu(MENU,window,owner),
                owner.getBlockState().getBlock().getName()),buffer->buffer.writeBlockPos(owner.getBlockPos()));
        }
        return true;
    }

    private static boolean openThrottle(Player actor,NativeChairEntity owner,BlockPos pos,NativeCockpitThrottle.Hit grip){
        var world=owner.getLevel();
        if(!NativeConfigurationAccess.canConfigure(actor)
            ||!((NativeSeatBlock)owner.getBlockState().getBlock()).complete(world,owner.getBlockPos())
            ||!world.mayInteract(actor,pos)||!actor.mayUseItemAt(pos,grip.ray().getDirection(),actor.getMainHandItem()))return false;
        if(actor instanceof ServerPlayer player){
            if(!player.getServer().isSameThread()||!NativeSeatBlock.matchesAuthoredHit(world,actor,pos,grip.ray()))return false;
            player.connection.send(owner.getUpdatePacket());
            player.openMenu(new SimpleMenuProvider((window,inventory,ignored)->new NativeSeatMenu(MENU,window,owner,grip.index()),
                net.minecraft.network.chat.Component.literal("Cockpit Throttles")),
                buffer->{buffer.writeBlockPos(owner.getBlockPos());buffer.writeByte(grip.index());});
        }
        return true;
    }

    static boolean openSurface(Player actor,NativeChairEntity owner,BlockPos pos,NativeCockpitTouch.Pick panel){
        var world=owner.getLevel();
        if(!NativeConfigurationAccess.canEditDialog(actor,owner)
            ||!((NativeSeatBlock)owner.getBlockState().getBlock()).complete(world,owner.getBlockPos())
            ||!world.mayInteract(actor,pos)||!actor.mayUseItemAt(pos,net.minecraft.core.Direction.UP,actor.getMainHandItem()))return false;
        int slot=panel==null?0:panel.slot();
        if(actor instanceof ServerPlayer player){
            if(!player.getServer().isSameThread()||panel!=null&&!NativeSeatBlock.matchesAuthoredHit(world,actor,pos,panel.hit()))return false;
            var menu=com.vandorlabs.neoforge.screen.NativeScreenRegistrations.MENU.get();
            player.connection.send(owner.getUpdatePacket());
            var title=owner.kind()==NativeSeatKind.COCKPIT
                ?net.minecraft.network.chat.Component.translatable("block.vandorlabs.cockpit.displays"):owner.getBlockState().getBlock().getName();
            player.openMenu(new SimpleMenuProvider((id,inv,ignored)->new com.vandorlabs.neoforge.screen.NativeScreenMenu(menu,id,owner,slot),
                title),buffer->{buffer.writeBlockPos(owner.getBlockPos());buffer.writeByte(slot);});
        }
        return true;
    }
    private NativeSeatBindings(){}
}
