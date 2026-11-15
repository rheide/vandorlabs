package com.vandorlabs.neoforge.ports;

import java.util.*;
import net.minecraft.core.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.*;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.context.*;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.*;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;

/** Same installed resource owners through decode refusal, item publication faults and recovery mining. */
@EventBusSubscriber(modid="vandorlabs")
public final class NativePortSavedDataChecks {
    private static int checks;private static BlockPos tracked;private static final List<ItemEntity> drops=new ArrayList<>();
    private static final Map<NativePortKind,NativePortItem> ROLLBACK_ITEMS=new EnumMap<>(NativePortKind.class),RETAIN_ITEMS=new EnumMap<>(NativePortKind.class);
    private static final class FaultItem extends NativePortItem {
        private final boolean rollback;
        FaultItem(NativePortBlock block,boolean rollback){super(block,new Item.Properties().stacksTo(1));this.rollback=rollback;}
        @Override public void registerBlocks(Map<net.minecraft.world.level.block.Block,Item> map,Item item){} // Fault tools never replace the canonical block item.
        @Override protected void install(NativeResourcePortEntity owner,CompoundTag data){super.install(owner,data);throw new IllegalStateException("intentional port install fault");}
        @Override protected boolean restore(Level world,BlockPos at,BlockState state){return rollback&&super.restore(world,at,state);}
    }
    @EventBusSubscriber(modid="vandorlabs",bus=EventBusSubscriber.Bus.MOD)
    public static final class FaultItems {
        @SubscribeEvent public static void register(net.neoforged.neoforge.registries.RegisterEvent event){if(!Boolean.getBoolean("vandorlabs.portRuntimeChecks"))return;event.register(net.minecraft.core.registries.Registries.ITEM,helper->{for(var kind:NativePortKind.values())for(boolean rollback:new boolean[]{true,false}){var item=new FaultItem(NativePortBindings.BLOCKS.get(kind),rollback);(rollback?ROLLBACK_ITEMS:RETAIN_ITEMS).put(kind,item);helper.register(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("vandorlabs","port_saved_fixture_"+kind.id+(rollback?"_rollback":"_retained")),item);}});}
    }
    private static void need(boolean yes,String why){checks++;if(!yes)throw new AssertionError("Port saved ownership: "+why);}
    @SubscribeEvent public static void joined(net.neoforged.neoforge.event.entity.EntityJoinLevelEvent event){if(tracked!=null&&!event.isCanceled()&&event.getEntity() instanceof ItemEntity item&&item.position().distanceToSqr(Vec3.atCenterOf(tracked))<4)drops.add(item);}
    private static final class Actor extends net.neoforged.neoforge.common.util.FakePlayer {Actor(ServerLevel level){super(level,new com.mojang.authlib.GameProfile(UUID.fromString("2bc2e695-317b-479c-9414-6590c63b546a"),"PortSavedOwner"));setGameMode(GameType.SURVIVAL);}}
    private static CompoundTag source(NativePortKind kind){var tag=new CompoundTag();switch(kind){case POWER->tag.putInt("PortEnergy",37);case FLUID->{var fluid=new CompoundTag();fluid.putString("FluidName","water");fluid.putInt("Amount",37);tag.put("PortFluid",fluid);}case MATTER->{var item=new ItemStack(Items.DIAMOND,37);var row=new CompoundTag();row.putByte("Slot",(byte)0);row.putString("id","minecraft:diamond");row.putByte("Count",(byte)37);row.putShort("Damage",(short)0);var rows=new ListTag();rows.add(row);tag.put("Items",rows);}}return tag;}
    private static NativeResourcePortEntity draft(NativePortKind kind,CompoundTag tag,ServerLevel level){var block=NativePortBindings.BLOCKS.get(kind);var owner=(NativeResourcePortEntity)block.newBlockEntity(BlockPos.ZERO,block.defaultBlockState());owner.loadWithComponents(tag,level.registryAccess());return owner;}
    private static void refused(NativePortKind kind,CompoundTag tag,ServerLevel level,String why){var before=tag.copy();var owner=draft(kind,tag,level);need(owner.inert(),why+" inert");need(owner.saveWithoutMetadata(level.registryAccess()).equals(before),why+" raw preserved");need(before.equals(tag),why+" source unchanged");}
    private static UseOnContext context(Actor actor,BlockPos pos){actor.setPos(pos.getX()+.5,pos.getY(),pos.getZ()-2);actor.setYRot(0);actor.setXRot(0);return new UseOnContext(actor,InteractionHand.MAIN_HAND,new BlockHitResult(Vec3.atCenterOf(pos.below()).add(0,.501,0),Direction.UP,pos.below(),false));}
    private static ItemStack carrier(NativePortKind kind,NativeResourcePortEntity owner,ServerLevel level){var stack=new ItemStack(NativePortBindings.BLOCKS.get(kind));stack.set(DataComponents.BLOCK_ENTITY_DATA,CustomData.of(owner.saveWithId(level.registryAccess())));return stack;}
    public static void run(ServerLevel level){
        com.vandorlabs.neoforge.furniture.NativeFurnitureItemChecks.run(level);
        var actor=new Actor(level);int n=0;
        for(var kind:NativePortKind.values()){
            var block=NativePortBindings.BLOCKS.get(kind);var canonical=draft(kind,source(kind),level);need(!canonical.inert(),"valid Source buffer "+kind);var saved=canonical.saveWithoutMetadata(level.registryAccess());
            var caps=saved.copy();var foreign=new CompoundTag();foreign.putInt("foreign:stored",37);caps.put("ForgeCaps",foreign);refused(kind,caps,level,"unknown Source capabilities "+kind);caps.putString("ForgeCaps","wrong type");refused(kind,caps,level,"Source capability type "+kind);caps.put("ForgeCaps",new CompoundTag());need(!draft(kind,caps,level).inert(),"empty Source capability envelope "+kind);
            var malformed=saved.copy();if(kind==NativePortKind.FLUID)malformed.getCompound("PortFluid").putInt("FutureVolume",31);else if(kind==NativePortKind.MATTER)malformed.getList("PortItems",Tag.TAG_COMPOUND).getCompound(0).getCompound("Item").putString("FutureOwner","retain");else malformed.put("Items",new ListTag());refused(kind,malformed,level,"unknown/mixed owner "+kind);
            malformed=saved.copy();malformed.remove("PortFaces");refused(kind,malformed,level,"partial policy "+kind);
            var pos=new BlockPos(18280+n++*8,120,18280);level.setChunkForced(pos.getX()>>4,pos.getZ()>>4,true);level.getChunkAt(pos);level.setBlock(pos.below(),Blocks.STONE.defaultBlockState(),3);level.removeBlock(pos,false);
            var stack=carrier(kind,canonical,level);actor.setItemInHand(InteractionHand.MAIN_HAND,stack);need(stack.useOn(context(actor,pos)).consumesAction()&&stack.isEmpty(),"ordinary configured placement consumes once "+kind);var installed=(NativeResourcePortEntity)level.getBlockEntity(pos);need(installed!=null&&!installed.inert(),"configured owner installed "+kind);var actual=installed.saveWithoutMetadata(level.registryAccess());for(String key:List.of("PortEnergy","PortFluid","PortItems"))need(Objects.equals(actual.get(key),saved.get(key)),"exact resource installed "+kind+"/"+key);level.removeBlock(pos,false);
            for(boolean rollback:new boolean[]{true,false}){
                var faulty=(rollback?ROLLBACK_ITEMS:RETAIN_ITEMS).get(kind);
                stack=carrier(kind,canonical,level);actor.setItemInHand(InteractionHand.MAIN_HAND,stack);boolean threw=false;
                try{faulty.place(new BlockPlaceContext(context(actor,pos)));}catch(IllegalStateException expected){threw=true;}
                need(threw,"injected late fault surfaced");
                if(rollback){need(level.getBlockState(pos).isAir()&&level.getBlockEntity(pos)==null&&stack.getCount()==1,"rollback returns sole owner to item "+kind);continue;}
                need(stack.isEmpty()&&level.getBlockEntity(pos) instanceof NativeResourcePortEntity owner&&owner.inert(),"failed rollback retains one inert physical owner "+kind);
                var retained=(NativeResourcePortEntity)level.getBlockEntity(pos);var retainedTag=retained.saveWithoutMetadata(level.registryAccess());need(retainedTag.getBoolean(NativeResourcePortEntity.RECOVERY),"retained recovery marker");
                for(String key:List.of("PortEnergy","PortFluid","PortItems"))need(Objects.equals(retainedTag.get(key),saved.get(key)),"failed rollback retains exact resource "+kind+"/"+key);
                actor.setGameMode(GameType.CREATIVE);tracked=pos;drops.clear();need(actor.gameMode.destroyBlock(pos),"actual Creative recovery mining "+kind);tracked=null;need(drops.size()==1&&drops.get(0).getItem().getCount()==1,"one inert recovery carrier, no resource loss "+kind);var recovery=drops.get(0).getItem().copy();drops.get(0).discard();actor.setGameMode(GameType.SURVIVAL);actor.setItemInHand(InteractionHand.MAIN_HAND,recovery);need(!recovery.useOn(context(actor,pos)).consumesAction()&&recovery.getCount()==1&&level.getBlockState(pos).isAir(),"inert recovery refuses reactivation/consumption "+kind);
            }
        }
        var rackState=com.vandorlabs.neoforge.rack.NativeRackBindings.BLOCK.defaultBlockState();var rackTag=new CompoundTag();var rackCaps=new CompoundTag();rackCaps.putInt("foreign:stored",37);rackTag.put("ForgeCaps",rackCaps);var rack=new com.vandorlabs.neoforge.rack.NativeRackEntity(com.vandorlabs.neoforge.rack.NativeRackBindings.ENTITY,BlockPos.ZERO,rackState);rack.loadWithComponents(rackTag,level.registryAccess());need(rack.inert()&&rack.ownedModuleDrops().isEmpty()&&rack.saveWithoutMetadata(level.registryAccess()).equals(rackTag),"unknown Source Rack capabilities retained inert");
        rackTag=new CompoundTag();var module=new CompoundTag();module.putString("id","vandorlabs:blank_module");module.putByte("Count",(byte)1);module.putShort("Damage",(short)0);module.putByte("Slot",(byte)0);module.put("ForgeCaps",rackCaps.copy());var modules=new ListTag();modules.add(module);rackTag.put("Items",modules);rack.loadWithComponents(rackTag,level.registryAccess());need(rack.inert()&&rack.ownedModuleDrops().isEmpty()&&rack.saveWithoutMetadata(level.registryAccess()).equals(rackTag),"unknown Source module-item capabilities retain whole inert Rack");
        System.out.println("[vandorlabs][port-saved-data] PASS checks="+checks+" Source/modern strict retained owners, real configured placement, late rollback/failure recovery, actual Creative carrier");
    }
}
