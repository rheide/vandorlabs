package com.vandorlabs.neoforge.transfer;

import java.lang.ref.WeakReference;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.LevelEvent;

/** Exact durable leases; does not create, load, advance or retire any operation journal. */
@EventBusSubscriber(modid="vandorlabs")
public final class NativeTransferHolds {
    public interface Authority {
        UUID operationId();
        /** A durable PREPARED record may reserve exact cells while ARMED is pending; no mutation is admitted. */
        default boolean reservationDurable(){return mutationArmed();}
        /** True only after the caller's immutable before-images are durable and mutation/recovery is armed. */
        boolean mutationArmed();
    }
    private static final int MAX_CELLS=65536,MAX_WORLD_CELLS=131072,MAX_TOTAL_CELLS=262144,MAX_WORLD_OPERATIONS=16;
    private static volatile List<Lease> held=List.of();
    private static final ThreadLocal<Admission> MUTATION=new ThreadLocal<>();
    private static final class Admission {final Lease lease;final BlockPos pos;final Admission previous;boolean consumed;Admission(Lease lease,BlockPos pos,Admission previous){this.lease=lease;this.pos=pos;this.previous=previous;}}
    public static final class Lease implements AutoCloseable {
        private final WeakReference<ServerLevel> world;
        private final Authority authority;
        private final Set<BlockPos> cells;
        private final List<WeakReference<Container>> containers;
        private boolean closed;
        private Lease(ServerLevel world,Authority authority,Set<BlockPos> cells,List<WeakReference<Container>> containers){this.world=new WeakReference<>(world);this.authority=authority;this.cells=cells;this.containers=containers;}
        public UUID operationId(){return authority.operationId();}
        public boolean contains(BlockPos pos){return !closed&&cells.contains(pos);}
        public boolean live(){return !closed&&world.get()!=null&&held.contains(this);}
        /** One explicit internal access/write. Reentrant callbacks cannot reuse the admission. */
        public Scope allowOnce(BlockPos pos){var level=world.get();thread(level);if(!live()||!authority.mutationArmed()||!cells.contains(pos))throw new IllegalStateException("Unarmed transfer mutation");var previous=MUTATION.get();if(previous!=null&&previous.lease!=this)throw new IllegalStateException("Nested foreign transfer mutation");var admission=new Admission(this,pos.immutable(),previous);MUTATION.set(admission);return new Scope(admission);}
        /** Exact physical slot mutation; automation and surrounding callbacks never gain inventory access. */
        public void writeSlot(Container inventory,int slot,ItemStack expectedBefore,ItemStack after){
            var level=world.get();thread(level);if(!live()||!authority.mutationArmed()||slot<0||slot>=inventory.getContainerSize()||inventory.getContainerSize()>8192)throw new IllegalStateException("Transfer slot admission");
            boolean registered=false;for(var reference:containers)if(reference.get()==inventory){registered=true;break;}
            if(!registered||!(inventory instanceof BlockEntity tile)||tile.getLevel()!=level||!cells.contains(tile.getBlockPos())||level.getChunkSource().getChunkNow(tile.getBlockPos().getX()>>4,tile.getBlockPos().getZ()>>4)==null||level.getBlockEntity(tile.getBlockPos())!=tile)throw new IllegalStateException("Transfer slot owner changed");
            if(!after.isEmpty()&&(after.getCount()<1||after.getCount()>Math.min(inventory.getMaxStackSize(),after.getMaxStackSize())))throw new IllegalArgumentException("Transfer slot count");
            if(!ItemStack.matches(inventory.getItem(slot),expectedBefore))throw new IllegalStateException("Transfer slot contents changed");
            var stable=after.copy();inventory.setItem(slot,stable.copy());
            if(level.getBlockEntity(tile.getBlockPos())!=tile||!ItemStack.matches(inventory.getItem(slot),stable))throw new IllegalStateException("Transfer slot callback changed ownership");inventory.setChanged();
        }
        @Override public void close(){var level=world.get();if(level!=null)thread(level);if(closed)return;closed=true;held=held.stream().filter(lease->lease!=this&&lease.world.get()!=null&&!lease.closed).toList();}
    }
    public static final class Scope implements AutoCloseable {
        private final Admission admission;private boolean closed;
        private Scope(Admission admission){this.admission=admission;}
        @Override public void close(){if(closed)return;if(MUTATION.get()!=admission)throw new IllegalStateException("Transfer mutation scopes closed out of order");if(admission.previous==null)MUTATION.remove();else MUTATION.set(admission.previous);closed=true;}
    }
    private static void thread(ServerLevel level){if(level==null||!level.getServer().isSameThread())throw new IllegalStateException("Transfer hold requires its server thread");}
    public static Lease freeze(ServerLevel level,Authority authority,Collection<BlockPos> positions){
        thread(level);Objects.requireNonNull(authority);Objects.requireNonNull(authority.operationId());if(!authority.reservationDurable()||positions.isEmpty()||positions.size()>MAX_CELLS)throw new IllegalArgumentException("Transfer hold admission");
        var cells=new HashSet<BlockPos>();for(var pos:positions)cells.add(pos.immutable());if(cells.size()!=positions.size())throw new IllegalArgumentException("Duplicate transfer hold cell");
        int worldCells=cells.size(),totalCells=cells.size(),operations=1;var next=new ArrayList<Lease>();for(var lease:held){var owner=lease.world.get();if(owner==null||lease.closed)continue;if(lease.authority==authority||lease.operationId().equals(authority.operationId()))throw new IllegalStateException("Transfer lease already installed");next.add(lease);totalCells=Math.addExact(totalCells,lease.cells.size());if(owner==level){operations++;worldCells=Math.addExact(worldCells,lease.cells.size());if(!Collections.disjoint(cells,lease.cells))throw new IllegalStateException("Overlapping transfer lease");}}
        if(operations>MAX_WORLD_OPERATIONS||worldCells>MAX_WORLD_CELLS||totalCells>MAX_TOTAL_CELLS)throw new IllegalStateException("Transfer hold capacity");
        var containers=new ArrayList<WeakReference<Container>>();for(var pos:cells)if(level.getChunkSource().getChunkNow(pos.getX()>>4,pos.getZ()>>4)!=null&&level.getBlockEntity(pos) instanceof Container inventory)containers.add(new WeakReference<>(inventory));
        var lease=new Lease(level,authority,Set.copyOf(cells),List.copyOf(containers));next.add(lease);held=List.copyOf(next);for(var actor:level.players())for(var slot:actor.containerMenu.slots)if(blocked(slot.container)){actor.closeContainer();break;}return lease;
    }
    public static boolean active(Level level){if(!(level instanceof ServerLevel)||held.isEmpty())return false;for(var lease:held)if(!lease.closed&&lease.world.get()==level)return true;return false;}
    public static boolean blocked(Level level,BlockPos pos){
        if(!(level instanceof ServerLevel)||held.isEmpty())return false;var admission=MUTATION.get();for(var lease:held)if(!lease.closed&&lease.world.get()==level&&lease.cells.contains(pos)){
            if(admission!=null&&!admission.consumed&&admission.lease==lease&&admission.pos.equals(pos)&&lease.authority.mutationArmed()){admission.consumed=true;continue;}return true;
        }return false;
    }
    /** Inventory automation remains frozen even while an internal block write is admitted. */
    public static boolean blocked(Container inventory){if(held.isEmpty())return false;for(var lease:held)if(!lease.closed&&lease.world.get()!=null){if(inventory instanceof BlockEntity tile&&tile.getLevel()==lease.world.get()&&lease.cells.contains(tile.getBlockPos()))return true;for(var reference:lease.containers){var owner=reference.get();if(owner!=null&&(owner==inventory||inventory instanceof CompoundContainer pair&&pair.contains(owner)))return true;}}return false;}
    @SubscribeEvent public static void unload(LevelEvent.Unload event){held=held.stream().filter(lease->{if(lease.world.get()==null||lease.world.get()==event.getLevel()){lease.closed=true;return false;}return true;}).toList();}
    private NativeTransferHolds(){}
}
