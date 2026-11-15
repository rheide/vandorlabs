package com.vandorlabs.neoforge.gear;

import java.lang.ref.WeakReference;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.*;

/** Exact armed transaction cells, including old container references retained by vanilla menus/handlers. */
@EventBusSubscriber(modid="vandorlabs")
public final class NativeGearHolds {
    private record Hold(WeakReference<ServerLevel> world,UUID id,Set<BlockPos> cells,List<WeakReference<Container>> containers){}
    private static volatile List<Hold> holds=List.of();private static final ThreadLocal<UUID> MUTATION=new ThreadLocal<>();
    private static final ThreadLocal<WriteAdmission> WRITE=new ThreadLocal<>();
    private static final class WriteAdmission {
        final ServerLevel world;final UUID operation;final BlockPos pos;final WriteAdmission previous;boolean consumed;
        WriteAdmission(ServerLevel world,UUID operation,BlockPos pos,WriteAdmission previous){this.world=world;this.operation=operation;this.pos=pos.immutable();this.previous=previous;}
    }
    /** Only the immediate publication call can consume this admission. Callbacks retain every hold. */
    static WriteScope writeOnce(ServerLevel world,BlockPos pos){
        if(!world.getServer().isSameThread())throw new IllegalStateException("Arm write requires server thread");
        var operation=MUTATION.get();boolean owns=false;for(var hold:holds)if(hold.world.get()==world&&hold.id.equals(operation)&&hold.cells.contains(pos)){owns=true;break;}
        if(!owns)throw new IllegalStateException("Arm write outside its exact held footprint");
        var previous=WRITE.get();if(previous!=null)throw new IllegalStateException("Nested arm publication admission");
        var admission=new WriteAdmission(world,operation,pos,previous);WRITE.set(admission);return new WriteScope(admission);
    }
    static final class WriteScope implements AutoCloseable {
        private final WriteAdmission admission;private boolean closed;
        WriteScope(WriteAdmission admission){this.admission=admission;}
        public void close(){if(closed)return;if(WRITE.get()!=admission)throw new IllegalStateException("Arm write scopes closed out of order");if(admission.previous==null)WRITE.remove();else WRITE.set(admission.previous);closed=true;}
    }
    private static boolean exactWrite(Level world,BlockPos pos){var admission=WRITE.get();return admission!=null&&admission.world==world&&admission.pos.equals(pos)&&admission.operation.equals(MUTATION.get());}
    static void freeze(ServerLevel world,UUID id,Collection<BlockPos> cells){if(!world.getServer().isSameThread()||cells.size()>8769)throw new IllegalStateException("Arm hold admission");var next=new ArrayList<Hold>();var containers=new ArrayList<WeakReference<Container>>();for(var hold:holds)if(hold.world.get()!=null){if(hold.id.equals(id)){containers.addAll(hold.containers);}else next.add(hold);}for(var pos:cells)if(NativeGearFormation.loaded(world,pos)&&world.getBlockEntity(pos) instanceof Container container){boolean seen=false;for(var ref:containers)seen|=ref.get()==container;if(!seen)containers.add(new WeakReference<>(container));}next.add(new Hold(new WeakReference<>(world),id,Set.copyOf(cells),List.copyOf(containers)));holds=List.copyOf(next);for(var actor:world.players())for(var slot:actor.containerMenu.slots)if(blocked(slot.container)){actor.closeContainer();break;}}
    static void release(UUID id){holds=holds.stream().filter(h->h.world.get()!=null&&!h.id.equals(id)).toList();}
    public static boolean active(Level level){if(!(level instanceof ServerLevel)||holds.isEmpty())return false;for(var hold:holds)if(hold.world.get()==level)return true;return false;}
    /** Exact read-only reservation observation, independent of internal write/break scopes. */
    public static boolean reserved(Level level,BlockPos pos){if(!(level instanceof ServerLevel))return false;for(var hold:holds)if(hold.world.get()==level&&hold.cells.contains(pos))return true;return false;}
    public static boolean blocked(Level level,BlockPos pos){return blockedForBreak(level,pos,null);}
    public static boolean blockedForBreak(Level level,BlockPos pos,UUID admitted){
        if(!(level instanceof ServerLevel)||holds.isEmpty())return false;
        for(var hold:holds)if(hold.world.get()==level&&!hold.id.equals(admitted)&&hold.cells.contains(pos)){
            var write=WRITE.get();if(exactWrite(level,pos)&&!write.consumed&&hold.id.equals(write.operation)){write.consumed=true;continue;}return true;
        }return false;
    }
    public static boolean blocked(Container container){
        if(holds.isEmpty())return false;
        // Inventory queries never consume a block-write token, including old handler references.
        if(container instanceof BlockEntity tile&&reserved(tile.getLevel(),tile.getBlockPos()))return true;
        for(var hold:holds)if(hold.world.get()!=null)for(var ref:hold.containers){var current=ref.get();if(current!=null&&(current==container||container instanceof CompoundContainer compound&&compound.contains(current)))return true;}return false;
    }
    static boolean mutating(UUID id){return id.equals(MUTATION.get());}
    static Scope mutate(UUID id){var previous=MUTATION.get();if(previous!=null&&!previous.equals(id))throw new IllegalStateException("Nested foreign arm mutation");MUTATION.set(id);return new Scope(previous);}
    static final class Scope implements AutoCloseable {private final UUID previous;Scope(UUID previous){this.previous=previous;}public void close(){if(previous==null)MUTATION.remove();else MUTATION.set(previous);}}
    public static boolean retainedOwner(Level level,BlockPos pos){if(!(level instanceof ServerLevel)||exactWrite(level,pos)||!NativeGearBindings.enabled())return false;var tile=NativeGearFormation.at(level,pos);if(tile==null)return false;var root=tile.controllerEntity();return tile.ownsCaptured()||root!=null&&root.hasStoredSections();}
    public static boolean replacementBlocked(Level level,BlockPos pos,BlockState next){return retainedOwner(level,pos)&&level.getBlockState(pos).getBlock()!=next.getBlock();}
    @SubscribeEvent public static void breaking(BlockEvent.BreakEvent event){if(event.getLevel() instanceof Level level&&retainedOwner(level,event.getPos())){event.setCanceled(true);event.getPlayer().displayClientMessage(net.minecraft.network.chat.Component.literal("Return the attachment to a native endpoint before removing this arm."),true);}}
    @SubscribeEvent public static void explosion(ExplosionEvent.Detonate event){if(NativeGearBindings.enabled())event.getAffectedBlocks().removeIf(pos->retainedOwner(event.getLevel(),pos));}
    @SubscribeEvent public static void unload(LevelEvent.Unload event){holds=holds.stream().filter(h->h.world.get()!=null&&h.world.get()!=event.getLevel()).toList();}
    private NativeGearHolds(){}
}
