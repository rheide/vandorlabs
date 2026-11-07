package com.vandorlabs.neoforge.ramp;

import com.vandorlabs.neoforge.copifier.NativeCopiedSettingsValidation;
import com.vandorlabs.neoforge.inventory.NativeInventoryItems;
import com.vandorlabs.neoforge.placement.NativeStationaryPlacement;
import com.vandorlabs.neoforge.redstone.NativeRedstoneChannels;
import java.util.Objects;
import net.minecraft.core.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.*;
import net.minecraft.server.level.*;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;

/** Private configured-item adapter. Runtime admission and publication must be supplied explicitly. */
public class NativeRampItem extends BlockItem {
    public static final String CONFIGURATION="RampSettings",COPIED="CopiedRampSettings",RECOVERY=NativeRampEntity.ITEM_RECOVERY;
    /** Freeze real actor/held-item permission and bounded plans; do not transfer source ownership yet. */
    @FunctionalInterface public interface Commands {PreparedCommand prepare(NativeRampEntity owner,ServerPlayer actor,NativeRampConfiguration.Plan plan,CompoundTag copied);}
    /** Invoked once after successful placement; never invoked when staging rolls back. */
    @FunctionalInterface public interface PreparedCommand {NativeRampReceipt placed();}
    private record Prepared(CompoundTag snapshot,NativeRampConfiguration.Plan plan,CompoundTag copied){}
    private final Commands commands;
    public NativeRampItem(NativeRampBlock block,Properties properties,Commands commands){super(block,properties);this.commands=Objects.requireNonNull(commands);}
    public static ItemStack pick(Item item,NativeRampEntity owner){
        var stack=new ItemStack(item);if(owner!=null){var root=new CompoundTag();root.put(owner.inert()?RECOVERY:CONFIGURATION,owner.inert()?owner.recoverySnapshot():owner.itemSettings());stack.set(DataComponents.CUSTOM_DATA,CustomData.of(root));}return stack;
    }
    private static Prepared prepare(NativeRampBlock block,BlockPos pos,BlockState state,ItemStack stack,HolderLookup.Provider registries){
        if(NativeInventoryItems.containedOwnership(stack))throw new IllegalArgumentException("Ramp item physical ownership");
        var root=stack.getOrDefault(DataComponents.CUSTOM_DATA,CustomData.EMPTY).copyTag();
        for(String key:new String[]{RECOVERY,NativeRampEntity.PLACEMENT_RECOVERY})if(root.contains(key))throw new IllegalArgumentException("Ramp recovery item");
        for(String key:new String[]{CONFIGURATION,COPIED,"BlockEntityTag"})if(root.contains(key)&&!root.contains(key,Tag.TAG_COMPOUND))throw new IllegalArgumentException("Malformed Ramp item "+key);
        var raw=stack.get(DataComponents.BLOCK_ENTITY_DATA);var input=raw!=null?raw.copyTag():root.getCompound("BlockEntityTag").copy();
        for(String key:new String[]{"id","x","y","z"})input.remove(key);
        var draft=block.entityType().create(pos,state);if(!(draft instanceof NativeRampEntity owner))throw new IllegalArgumentException("Ramp item factory");
        // Vanilla placement merges generic item data onto the fresh tile before loading it.
        // Loading an empty old-save tag directly would replace fresh defaults with legacy fallbacks.
        var merged=owner.saveWithoutMetadata(registries);merged.merge(input);
        owner.loadWithComponents(merged,registries);if(owner.inert())throw new IllegalArgumentException("Unsupported Ramp item owner");
        NativeRampConfiguration.Plan plan=null;var next=owner.settings();
        if(root.contains(CONFIGURATION)){plan=NativeRampConfiguration.placement(next,NativeRampBlock.scanFacing(state).get3DDataValue(),root.getCompound(CONFIGURATION),true,true,true);next=plan.result();}
        else if(NativeRampBlock.scanFacing(state).getAxis().isVertical()&&!next.hasDirection())next=new NativeRampSettings(next.startHalf(),next.endHalf(),next.treadPixels(),next.powerOn(),next.elevator(),next.extend(),next.speed(),next.matchTextures(),next.interpolation(),next.escalator(),next.moat(),next.verticalLayout(),next.travelAxis(),true,state.getValue(NativeRampBlock.FACING).get2DDataValue(),next.analogDeployment(),next.channels());
        var snapshot=owner.saveWithoutMetadata(registries);next.writeChoices(snapshot);snapshot.putBoolean("ChannelSignal",false);
        CompoundTag copied=null;if(root.contains(COPIED)){copied=root.getCompound(COPIED).copy();if(!NativeCopiedSettingsValidation.valid(copied))throw new IllegalArgumentException("Malformed copied Ramp settings");}
        return new Prepared(snapshot,plan,copied);
    }
    protected boolean stage(Level world,BlockPos pos,BlockState state){return world.setBlock(pos,state,Block.UPDATE_KNOWN_SHAPE);}
    protected boolean restore(Level world,BlockPos pos,BlockState state){return world.setBlock(pos,state,Block.UPDATE_KNOWN_SHAPE);}
    private static NativeRampEntity recovery(Level world,BlockPos pos,BlockState state,NativeRampBlock block,Prepared prepared){
        if(world.getBlockState(pos)!=state)return null;var owner=world.getBlockEntity(pos) instanceof NativeRampEntity tile?tile:null;
        if(owner==null){owner=new NativeRampEntity(block.entityType(),pos,state,block instanceof NativeRampSlabBlock);world.setBlockEntity(owner);if(world.getBlockEntity(pos)!=owner)return null;}
        owner.retainPlacementRecovery(prepared.snapshot);return owner;
    }
    @Override public InteractionResult place(BlockPlaceContext context){
        var world=context.getLevel();var player=context.getPlayer();var stack=context.getItemInHand();var pos=context.getClickedPos();var block=(NativeRampBlock)getBlock();
        if(player==null||stack.isEmpty()||!context.canPlace()||!world.hasChunkAt(pos)||world.isOutsideBuildHeight(pos)||!world.mayInteract(player,pos)||!player.mayUseItemAt(pos,context.getClickedFace(),stack)||NativeRampHeldOwners.blocked(world,pos))return InteractionResult.FAIL;
        if(world instanceof ServerLevel server&&(!server.getServer().isSameThread()||!NativeRedstoneChannels.open(server)))return InteractionResult.FAIL;
        var old=world.getBlockState(pos);if(old.hasBlockEntity()||world.getBlockEntity(pos)!=null||!old.canBeReplaced(context))return InteractionResult.FAIL;
        var proposed=block.getStateForPlacement(context);if(proposed==null||!canPlace(context,proposed))return InteractionResult.FAIL;
        final Prepared prepared;try{prepared=prepare(block,pos,proposed,stack,world.registryAccess());}catch(IllegalArgumentException invalid){return InteractionResult.FAIL;}
        final var state=proposed.setValue(NativeRampBlock.MODE,NativeRampSettings.read(prepared.snapshot).mode());
        if(world.isClientSide)return InteractionResult.SUCCESS;if(!(player instanceof ServerPlayer actor))return InteractionResult.FAIL;
        var placed=stack.copy();var action=new PreparedCommand[1];var installed=new NativeRampEntity[1];
        boolean committed=NativeStationaryPlacement.commit(world,pos,old,state,player,stack,new NativeStationaryPlacement.Hooks<NativeRampEntity>(){
            public boolean stage(){return NativeRampItem.this.stage(world,pos,state);}public boolean restore(){return NativeRampItem.this.restore(world,pos,old);}
            public NativeRampEntity owner(){return world.getBlockEntity(pos) instanceof NativeRampEntity owner?owner:null;}
            public void install(NativeRampEntity owner){owner.installItemSnapshot(prepared.snapshot,world.registryAccess());installed[0]=owner;action[0]=Objects.requireNonNull(commands.prepare(owner,actor,prepared.plan,prepared.copied==null?null:prepared.copied.copy()));}
            public NativeRampEntity retainRecovery(){return recovery(world,pos,state,block,prepared);}public String label(){return "Ramp";}
        });
        if(!committed)return InteractionResult.FAIL;
        world.markAndNotifyBlock(pos,world.getChunkAt(pos),old,state,Block.UPDATE_ALL,512);block.setPlacedBy(world,pos,state,player,placed);
        var sound=state.getSoundType(world,pos,player);world.playSound(null,pos,sound.getPlaceSound(),SoundSource.BLOCKS,(sound.getVolume()+1)/2,sound.getPitch()*.8F);world.gameEvent(player,GameEvent.BLOCK_PLACE,pos);net.minecraft.advancements.CriteriaTriggers.PLACED_BLOCK.trigger(actor,pos,placed);
        if(world.getBlockEntity(pos)==installed[0])Objects.requireNonNull(action[0].placed());
        return InteractionResult.CONSUME;
    }
}
