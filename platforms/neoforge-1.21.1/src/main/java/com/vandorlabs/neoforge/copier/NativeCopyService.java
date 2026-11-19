package com.vandorlabs.neoforge.copier;

import com.vandorlabs.neoforge.surface.*;
import com.vandorlabs.neoforge.transfer.*;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.nbt.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.*;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

/** Family-owned economy over durable cell publication. No client can provide a snapshot or a material receipt. */
@EventBusSubscriber(modid="vandorlabs")
public final class NativeCopyService {
    private static final NativeTransferSnapshot.Limits LIMITS=new NativeTransferSnapshot.Limits(16386,2*1024*1024,10*1024*1024,40*1024*1024);
    private record Pending(NativeCopyControllerEntity owner,java.lang.ref.WeakReference<ServerPlayer> player,NativeCopyPlan plan,NativeCellTransactions.Request request,long bytes){}
    private static final Map<ServerLevel,Map<BlockPos,Pending>> QUEUED=new IdentityHashMap<>();
    private static final Map<ServerLevel,LinkedHashSet<UUID>> VERIFIED=new IdentityHashMap<>();
    interface Fault {void afterCell(NativeCellTransactions.Header header,NativeRawCell target);}
    static Fault fault;
    private static final ResourceLocation ID=ResourceLocation.parse("vandorlabs:copy_controller");
    public static void register(){NativeCellTransactions.register(FAMILY);}
    static boolean busy(NativeCopyControllerEntity owner){if(!(owner.getLevel() instanceof ServerLevel world))return false;var queued=QUEUED.get(world);return queued!=null&&queued.containsKey(owner.getBlockPos())||NativeCellTransactions.busy(world,owner.getBlockPos(),owner.key());}
    static void submit(NativeCopyControllerEntity owner,ServerPlayer player){
        var world=player.serverLevel();if(busy(owner))throw new IllegalArgumentException("A copy is already queued");var plan=NativeCopyPlan.prepare(owner,player);var before=new ArrayList<NativeRawCell>();var after=new ArrayList<NativeRawCell>();if(plan.supply!=null){before.addAll(plan.supply.before());after.addAll(plan.supply.after());}before.addAll(plan.before.cells());after.addAll(plan.result.cells());
        var request=new NativeCellTransactions.Request(owner.getBlockPos(),owner.key(),new NativeTransferSnapshot(before,LIMITS),new NativeTransferSnapshot(after,LIMITS),plan.held,NativeCopyReceipt.write(owner,plan,world.registryAccess()));long bytes=Math.addExact(4096L+request.before().retainedBytes+request.after().retainedBytes+plan.costSource.retainedBytes+(plan.input==null?0:plan.input.retainedBytes),4L*request.recipe().sizeInBytes());long total=bytes;for(var waiting:QUEUED.values())for(var previous:waiting.values())total=Math.addExact(total,previous.bytes);var sameWorld=QUEUED.get(world);long worldBytes=bytes;if(sameWorld!=null)for(var previous:sameWorld.values())worldBytes=Math.addExact(worldBytes,previous.bytes);if(bytes>128L*1024*1024||worldBytes>128L*1024*1024||total>256L*1024*1024||sameWorld!=null&&sameWorld.size()>=16)throw new IllegalArgumentException("Queued Copy preparation memory bound");var waiting=new Pending(owner,new java.lang.ref.WeakReference<>(player),plan,request,bytes);QUEUED.computeIfAbsent(world,unused->new LinkedHashMap<>()).put(owner.getBlockPos(),waiting);owner.status("Preparing durable copy");owner.changed();try{admit(world,waiting);}catch(RuntimeException failure){var queued=QUEUED.get(world);if(queued!=null){queued.remove(owner.getBlockPos());if(queued.isEmpty())QUEUED.remove(world);}owner.failure(failure.getMessage());owner.changed();throw failure;}
    }
    private static void admit(ServerLevel world,Pending pending){
        var root=pending.owner;if(!root.installed()||!root.key().equals(pending.plan.owner))throw new IllegalArgumentException("Queued controller identity changed");var actor=pending.player.get();if(actor==null||actor.isRemoved())throw new IllegalArgumentException("The configuring player left before the copy was admitted");pending.plan.validate(root,actor);var operation=NativeCellTransactions.submit(world,FAMILY,actor,pending.request);if(operation!=null){var queued=QUEUED.get(world);if(queued!=null){queued.remove(root.getBlockPos());if(queued.isEmpty())QUEUED.remove(world);}root.status("Copying with durable recovery");root.changed();}
    }
    private static NativeCopyControllerEntity owner(ServerLevel world,NativeCellTransactions.Header header){
        if(world.getChunkSource().getChunkNow(header.root().getX()>>4,header.root().getZ()>>4)==null||!(world.getBlockEntity(header.root()) instanceof NativeCopyControllerEntity root)||!root.installed()||!root.key().equals(header.owner()))throw new IllegalArgumentException("Copy Controller identity changed");return root;
    }
    private static void permission(ServerLevel world,NativeCellTransactions.Header header,ServerPlayer actor,BlockPos pos){if(!world.mayInteract(actor,pos)||!actor.mayUseItemAt(pos,Direction.UP,actor.getMainHandItem())||com.vandorlabs.neoforge.ramp.NativeRampHeldOwners.blocked(world,pos)&&!NativeCellTransactions.reservedBy(world,header,pos))throw new IllegalArgumentException("Copy permission or foreign ownership refused");}
    private static boolean sameLayout(NativeSurfaceLayout first,NativeSurfaceLayout second){return first!=null&&second!=null&&first.normal==second.normal&&new HashSet<>(first.cells).equals(new HashSet<>(second.cells));}
    private static void validatePads(ServerLevel world,NativeCellTransactions.Header header,NativeCopyControllerEntity root,NativeCopyReceipt recipe,ServerPlayer actor){
        if(!sameLayout(root.output(),recipe.outputPad)||!recipe.settings.chip&&!sameLayout(root.input(),recipe.inputPad))throw new IllegalArgumentException("Copy pad layout changed");var right=root.front().getCounterClockWise();if(!recipe.outputPad.cells.contains(root.getBlockPos().relative(right))||recipe.inputPad!=null&&!recipe.inputPad.cells.contains(root.getBlockPos().relative(right.getOpposite())))throw new IllegalArgumentException("Copy pads no longer meet the correct controller sides");
        for(var layout:new NativeSurfaceLayout[]{recipe.inputPad,recipe.outputPad})if(layout!=null)for(var pos:layout.cells){permission(world,header,actor,pos);if(!(world.getBlockEntity(pos) instanceof NativeSurfaceEntity tile)||tile.inert()||!tile.claimedBy(root.getBlockPos(),root.key()))throw new IllegalArgumentException("Copy drawing claim changed");int side=(pos.getX()-root.getBlockPos().getX())*right.getStepX()+(pos.getZ()-root.getBlockPos().getZ())*right.getStepZ();if(layout==recipe.outputPad?side<=0:side>=0)throw new IllegalArgumentException("Copy pad crosses the controller divider");}
        if(recipe.supply!=null){recipe.supply.validateBacking(root.chest());for(var pos:recipe.supply.positions()){permission(world,header,actor,pos);if(!(world.getBlockEntity(pos) instanceof net.minecraft.world.level.block.entity.ChestBlockEntity chest)||!chest.canOpen(actor)||chest.getLootTable()!=null||net.minecraft.world.level.block.ChestBlock.getContainer((net.minecraft.world.level.block.ChestBlock)world.getBlockState(pos).getBlock(),world.getBlockState(pos),world,pos,false)==null)throw new IllegalArgumentException("Material chest authorization changed");}}
    }
    private static void validateEconomy(ServerLevel world,NativeCellTransactions.Header header,NativeCopyReceipt recipe){
        var checked=VERIFIED.computeIfAbsent(world,unused->new LinkedHashSet<>());if(checked.contains(header.operation()))return;if(checked.size()>=16)checked.remove(checked.iterator().next());var staged=new ArrayList<BlockEntity>();for(var cell:recipe.costSource.cells())staged.add(NativeCopyTiles.stage(world,cell));var computed=NativeCopyMaterials.prepare(world,recipe.costSource.cells(),staged,header.creative());if(!NativeCopyMaterials.same(computed,recipe.needs))throw new IllegalArgumentException("Copy receipt does not account for the actual source inventories and resources");checked.add(header.operation());
    }
    private static final NativeCellTransactions.Family<NativeCopyReceipt> FAMILY=new NativeCellTransactions.Family<>(){
        public ResourceLocation id(){return ID;}public NativeTransferSnapshot.Limits limits(){return LIMITS;}
        public NativeCopyReceipt readRecipe(HolderLookup.Provider registries,NativeCellTransactions.Header header,NativeTransferSnapshot before,NativeTransferSnapshot after,Set<BlockPos> held,CompoundTag raw){return NativeCopyReceipt.read(registries,header,before,after,held,raw);}
        public void authorize(ServerLevel world,NativeCellTransactions.Header header,ServerPlayer actor,NativeCopyReceipt recipe){
            var root=owner(world,header);if(!Objects.equals(root.actorId(),actor.getUUID())||!recipe.settings.equals(root.settings()))throw new IllegalArgumentException("Copy settings or authorizing player changed");permission(world,header,actor,root.getBlockPos());if(actor.distanceToSqr(root.getBlockPos().getCenter())>64)throw new IllegalArgumentException("Stay within reach while preparing a copy");validatePads(world,header,root,recipe,actor);
            NativeCopyNavigationTable.requireIsolated(world,recipe.tableInputBoundary);
            NativeCopyNavigationTable.requireIsolated(world,recipe.tableOutputBoundary);
            for(var pos:recipe.tableOutputBoundary)permission(world,header,actor,pos);
            NativeCopyScreens.requireIsolated(world,recipe.screenInputBoundary);
            NativeCopyScreens.requireIsolated(world,recipe.screenOutputBoundary);
            for(var pos:recipe.screenInputBoundary)permission(world,header,actor,pos);
            for(var pos:recipe.screenOutputBoundary)permission(world,header,actor,pos);
            recipe.targetVolume.requireLoaded(world);if(!world.getEntities(null,recipe.targetVolume.bounds()).isEmpty())throw new IllegalArgumentException("Output prism became occupied");for(var pos:recipe.targetVolume.cells())permission(world,header,actor,pos);
            if(recipe.source!=null){recipe.sourceVolume.requireLoaded(world);
                for(var cell:recipe.source.cells()){
                    if(cell.state.getBlock() instanceof com.vandorlabs.neoforge.navigation.observer.NativeObserverCoreBlock)NativeCopyObservers.requireIdle(world,cell.pos);
                    if(cell.state.getBlock() instanceof com.vandorlabs.neoforge.builders.NativeBuilderCoreBlock)NativeCopyBuilders.requireIdle(world,cell.pos);
                }if(!recipe.source.matches(world,NativeCopyTiles::same))throw new IllegalArgumentException("Input private fields or blocks changed");for(var pos:recipe.sourceVolume.cells())permission(world,header,actor,pos);}else{var mount=NativeCopyPlan.chipMount(world,root.getBlockPos());if(mount==null||!mount.getBlockPos().equals(recipe.chipMount)||!ItemStack.matches(mount.getItem(0),recipe.chip()))throw new IllegalArgumentException("Construction chip changed");}validateEconomy(world,header,recipe);
        }
        public BlockEntity stage(ServerLevel world,NativeCellTransactions.Header header,NativeRawCell cell,NativeCopyReceipt recipe){validateEconomy(world,header,recipe);return NativeCopyTiles.stage(world,cell);}
        public boolean same(CompoundTag first,CompoundTag second){return NativeCopyTiles.same(first,second);}
        public boolean intermediate(ServerLevel world,NativeCellTransactions.Header header,NativeRawCell before,NativeRawCell after,NativeCopyReceipt recipe){return recipe.supply!=null&&recipe.supply.contains(before.pos)&&recipe.supply.recognizes(world,before.pos);}
        public void publish(ServerLevel world,NativeCellTransactions.Header header,NativeRawCell before,NativeRawCell after,BlockEntity staged,NativeTransferHolds.Lease lease,NativeCopyReceipt recipe){
            if(recipe.supply!=null&&recipe.supply.contains(after.pos)){boolean restoring=recipe.supply.restoring(after);recipe.supply.publishCell(world,lease,after.pos,restoring);if(!restoring&&fault!=null&&(Boolean.getBoolean("vandorlabs.copyRuntimeChecks")||Boolean.getBoolean("vandorlabs.copyPlayerNavigationCheck")))fault.afterCell(header,after);return;}
            var old=world.getBlockEntity(after.pos);if(old instanceof com.vandorlabs.neoforge.rack.NativeRackEntity rack)rack.clearForTransfer();if(old instanceof Container container)container.clearContent();
            if(world.getBlockState(after.pos)!=after.state)try(var scope=lease.allowOnce(after.pos)){boolean observer=NativeCopyObservers.observer(before.state)||NativeCopyObservers.observer(after.state);
                boolean builder=NativeCopyBuilders.builder(before.state)||NativeCopyBuilders.builder(after.state);
                boolean replaced=observer?com.vandorlabs.neoforge.navigation.observer.NativeObserverProtection.set(world,after.pos,after.state,Block.UPDATE_KNOWN_SHAPE)
                    :builder?com.vandorlabs.neoforge.builders.NativeBuilderProtection.set(world,after.pos,after.state,Block.UPDATE_KNOWN_SHAPE)
                    :world.setBlock(after.pos,after.state,Block.UPDATE_KNOWN_SHAPE);
                if(!replaced)throw new IllegalStateException("Copy publication refused");}
            if(after.hasTile()){if(staged==null)throw new IllegalStateException("Prepared Copy tile missing");world.removeBlockEntity(after.pos);world.setBlockEntity(staged);staged.setChanged();}else if(world.getBlockEntity(after.pos)!=null)world.removeBlockEntity(after.pos);world.getChunkAt(after.pos).setUnsaved(true);
            if(!after.state.isAir()&&fault!=null&&(Boolean.getBoolean("vandorlabs.copyRuntimeChecks")||Boolean.getBoolean("vandorlabs.copyPlayerNavigationCheck")))fault.afterCell(header,after);
        }
        public void released(ServerLevel world,NativeCellTransactions.Header header,boolean rollback,String error,NativeCopyReceipt recipe){
            var checked=VERIFIED.get(world);
            if(checked!=null){checked.remove(header.operation());if(checked.isEmpty())VERIFIED.remove(world);}
            if(world.getBlockEntity(header.root()) instanceof NativeCopyControllerEntity root&&root.key().equals(header.owner())){
                if(rollback){
                    root.status("Copy rolled back");
                    root.failure(error.isEmpty()?"Copy cancelled safely":error);
                    root.changed();
                }else root.completed();
                NativeCopyUpdates.queue(root);
            }
            // Publication stages cells without client updates while the journal owns them.
            // Announce their final blocks and any tile packets after the durable lease retires.
            for(var cell:recipe.costSource.cells()){
                if(!world.hasChunkAt(cell.pos))continue;
                world.invalidateCapabilities(cell.pos);
                var state=world.getBlockState(cell.pos);
                if(world.getBlockEntity(cell.pos) instanceof com.vandorlabs.neoforge.builders.NativeBuilderEntity builder)
                    com.vandorlabs.neoforge.redstone.NativeRedstoneChannels.queueRegistration(builder);
                world.sendBlockUpdated(cell.pos,state,state,Block.UPDATE_CLIENTS);
            }
            for(var pos:recipe.targetVolume.cells()){
                for(Direction side:Direction.values())
                    if(world.getBlockEntity(pos.relative(side)) instanceof NativeSurfaceEntity surface)surface.schedule();
                if(world.getBlockEntity(pos) instanceof NativeSurfaceEntity surface)surface.schedule();
            }
        }
    };
    @SubscribeEvent public static void tick(LevelTickEvent.Post event){if(!(event.getLevel() instanceof ServerLevel world))return;var queued=QUEUED.get(world);if(queued==null)return;for(var pending:List.copyOf(queued.values()))try{admit(world,pending);}catch(IllegalArgumentException refusal){queued.remove(pending.owner.getBlockPos());if(pending.owner.installed()){pending.owner.failure(refusal.getMessage());pending.owner.changed();}}if(queued.isEmpty())QUEUED.remove(world);}
    @SubscribeEvent public static void unload(LevelEvent.Unload event){if(event.getLevel() instanceof ServerLevel world){QUEUED.remove(world);VERIFIED.remove(world);}}
    private NativeCopyService(){}
}
