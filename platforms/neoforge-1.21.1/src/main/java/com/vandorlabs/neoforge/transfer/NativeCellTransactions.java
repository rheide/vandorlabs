package com.vandorlabs.neoforge.transfer;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.BooleanSupplier;
import net.minecraft.core.*;
import net.minecraft.nbt.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** Durable cell publication, never a generic permission or a client-supplied snapshot endpoint. */
@EventBusSubscriber(modid="vandorlabs")
public final class NativeCellTransactions {
    public enum Status {PENDING,DONE,FAILED}
    private enum Phase {PREPARING,ARMING,INSTALLING,CHECKPOINT,COMMITTING,RETIRING,RECOVERING,RECONCILING,DONE,FAILED}
    public record Header(UUID operation,ResourceLocation family,BlockPos root,UUID owner,UUID actor,boolean creative){
        public Header {Objects.requireNonNull(operation);Objects.requireNonNull(family);root=root.immutable();Objects.requireNonNull(owner);Objects.requireNonNull(actor);}
    }
    public record Request(BlockPos root,UUID owner,NativeTransferSnapshot before,NativeTransferSnapshot after,Collection<BlockPos> held,CompoundTag recipe){
        public Request {root=root.immutable();Objects.requireNonNull(owner);Objects.requireNonNull(before);Objects.requireNonNull(after);held=held.stream().map(BlockPos::immutable).toList();recipe=recipe.copy();}
        @Override public CompoundTag recipe(){return recipe.copy();}
    }
    public interface Family<R> {
        ResourceLocation id();NativeTransferSnapshot.Limits limits();
        /** Decode an immutable, world-free typed recipe once. Never accept arbitrary unknown fields. */
        R readRecipe(HolderLookup.Provider registries,Header header,NativeTransferSnapshot before,NativeTransferSnapshot after,Set<BlockPos> held,CompoundTag encoded);
        /** Repeated after both disk boundaries, immediately before live mutation. */
        void authorize(ServerLevel world,Header header,ServerPlayer actor,R recipe);
        BlockEntity stage(ServerLevel world,Header header,NativeRawCell cell,R recipe);
        boolean same(CompoundTag expected,CompoundTag actual);
        /** Must use the supplied exact lease; one-use admissions do not cover callbacks or other cells. */
        void publish(ServerLevel world,Header header,NativeRawCell before,NativeRawCell after,BlockEntity staged,NativeTransferHolds.Lease lease,R recipe);
        default boolean intermediate(ServerLevel world,Header header,NativeRawCell before,NativeRawCell after,R recipe){return false;}
        /** Separate family-owned actor/entity receipt. No cell engine authority is granted to unrelated journals. */
        default void installAdditional(ServerLevel world,Header header,R recipe){}
        /** Pending family-owned actor loads suspend publication/recovery, never reject it. */
        default boolean readyAdditional(ServerLevel world,Header header,boolean rollback,R recipe){return true;}
        default void beforeRollbackAdditional(ServerLevel world,Header header,R recipe){}
        default void rollbackAdditional(ServerLevel world,Header header,R recipe){}
        default boolean additionalMatches(ServerLevel world,Header header,boolean rollback,R recipe){return true;}
        default boolean checkpointAdditional(ServerLevel world,Header header,boolean rollback,R recipe){return true;}
        default void released(ServerLevel world,Header header,boolean rollback,String error,R recipe){}
    }
    /** Explicit server-maintenance authority; never registered on a player or C2S route. */
    public interface MaintenanceFamily<R> extends Family<R> {
        void authorizeMaintenance(ServerLevel world,Header header,R recipe);
        @Override default void authorize(ServerLevel world,Header header,ServerPlayer actor,R recipe){throw new IllegalArgumentException("Maintenance family has no player admission");}
    }
    public static final UUID MAINTENANCE_ACTOR=new UUID(0,0);
    public static final class Operation<R> implements NativeTransferHolds.Authority {
        public final Header header;private final Family<R> family;private final Path file;
        private NativeTransferSnapshot before,after;private Set<BlockPos> held;
        private R recipe;private CompoundTag diskRecord;
        private final long weight;private final int decodedBytes;private Phase phase;private Future<?> disk;private ServerPlayer actor;
        private NativeTransferHolds.Lease lease;private NativeTransferCheckpoint checkpoint;
        private boolean prepared,armed,mutated,rollback,retirementSubmitted;private CompoundTag nextRecord;private String error="";
        private Operation(Header header,Family<R> family,Path file,NativeTransferSnapshot before,NativeTransferSnapshot after,Set<BlockPos> held,R recipe,CompoundTag record,Phase phase){this.header=header;this.family=family;this.file=file;this.before=before;this.after=after;this.held=held;this.recipe=Objects.requireNonNull(recipe);diskRecord=record.copy();this.phase=phase;int recordBytes=record.sizeInBytes();decodedBytes=recoveryBytes(record,recordBytes);weight=Math.addExact(4096L+before.retainedBytes+after.retainedBytes,4L*recordBytes+96L*held.size());}
        public Status status(){return phase==Phase.DONE?Status.DONE:phase==Phase.FAILED?Status.FAILED:Status.PENDING;}
        public String error(){return error;}
        @Override public UUID operationId(){return header.operation;}
        @Override public boolean reservationDurable(){return prepared&&phase!=Phase.DONE;}
        @Override public boolean mutationArmed(){return armed&&phase!=Phase.DONE;}
        public boolean protects(BlockPos pos){return prepared&&lease!=null&&lease.live()&&held.contains(pos);}
    }
    private static final int MAX_OPERATIONS=16,MAX_HELD=65536,MAX_ROOT_DISTANCE=96,MAX_RECIPE_BYTES=8*1024*1024;
    private static final long WORLD_BYTES=128L*1024*1024,GLOBAL_BYTES=256L*1024*1024;
    private static final Map<ResourceLocation,Family<?>> FAMILIES=new ConcurrentHashMap<>();
    private static final Map<ResourceLocation,MaintenanceFamily<?>> MAINTENANCE=new ConcurrentHashMap<>();
    private static final Map<ServerLevel,State> WORLDS=new IdentityHashMap<>();private static long retained;
    private static final class State {Future<List<NativeTransferJournalFiles.Saved>> loading;boolean ready;String error="";long bytes,decodedBytes;final Map<UUID,Operation<?>> operations=new LinkedHashMap<>();}
    public static synchronized void register(Family<?> family){Objects.requireNonNull(family);if(family instanceof MaintenanceFamily<?>)throw new IllegalArgumentException("Use explicit maintenance registration");registerFamily(family);}
    public static synchronized void registerMaintenance(MaintenanceFamily<?> family){Objects.requireNonNull(family);var previous=FAMILIES.get(family.id());if(previous!=null&&previous!=family)throw new IllegalStateException("Duplicate cell journal family");if(!family.id().getNamespace().equals("vandorlabs"))throw new IllegalStateException("Foreign cell journal family");var prior=MAINTENANCE.putIfAbsent(family.id(),family);if(prior!=null&&prior!=family)throw new IllegalStateException("Duplicate maintenance family");registerFamily(family);}
    private static void registerFamily(Family<?> family){if(!family.id().getNamespace().equals("vandorlabs"))throw new IllegalStateException("Foreign cell journal family");var previous=FAMILIES.putIfAbsent(family.id(),family);if(previous!=null&&previous!=family)throw new IllegalStateException("Duplicate cell journal family");}
    private static boolean maintenance(Family<?> family){return family instanceof MaintenanceFamily<?> && MAINTENANCE.get(family.id())==family;}

    private static Path directory(ServerLevel world){return world.getServer().getWorldPath(LevelResource.ROOT).resolve("vandorlabs/cell-journals").resolve(HexFormat.of().formatHex(world.dimension().location().toString().getBytes(java.nio.charset.StandardCharsets.UTF_8)));}
    private static void thread(ServerLevel world){if(!world.getServer().isSameThread())throw new IllegalStateException("Cell transaction requires its server thread");}
    public static <R> Operation<R> submit(ServerLevel world,Family<R> family,ServerPlayer actor,Request request){
        thread(world);if(family instanceof MaintenanceFamily<?>||actor==null||actor.serverLevel()!=world||!actor.isAlive()||actor.isSpectator()||MAINTENANCE_ACTOR.equals(actor.getUUID()))throw new IllegalArgumentException("Transfer actor unavailable");
        return prepare(world,family,actor,request,new Header(UUID.randomUUID(),family.id(),request.root,request.owner,actor.getUUID(),actor.isCreative()));
    }
    public static <R> Operation<R> submitMaintenance(ServerLevel world,MaintenanceFamily<R> family,Request request){
        thread(world);if(!maintenance(family)||MAINTENANCE_ACTOR.equals(request.owner))throw new IllegalArgumentException("Maintenance owner/family unavailable");
        return prepare(world,family,null,request,new Header(UUID.randomUUID(),family.id(),request.root,request.owner,MAINTENANCE_ACTOR,false));
    }
    private static <R> Operation<R> prepare(ServerLevel world,Family<R> family,ServerPlayer actor,Request request,Header header){
        if(FAMILIES.get(family.id())!=family)throw new IllegalArgumentException("Unregistered cell journal family");var state=state(world);if(!state.ready||!state.error.isEmpty()||world.noSave||state.operations.size()>=MAX_OPERATIONS)return null;
        if(request.held.isEmpty()||request.held.size()>MAX_HELD||request.recipe.sizeInBytes()>MAX_RECIPE_BYTES)throw new IllegalArgumentException("Cell journal request bounds");
        // Only a small metadata envelope is allocated while capacity is pending; snapshot sizes are immutable.
        var envelope=recordEnvelope(world,header,request.held,request.recipe);
        int emptyBytes=new CompoundTag().sizeInBytes();envelope.put("Before",new CompoundTag());envelope.put("After",new CompoundTag());
        long decoded=(long)envelope.sizeInBytes()-2L*emptyBytes+request.before.decodedBytes+request.after.decodedBytes;
        long reserved=decoded+2L*("ROLLED_BACK".length()-"PREPARED".length());
        if(reserved>NativeTransferJournalFiles.MAX_NBT_BYTES)throw new IllegalArgumentException("Cell journal record byte bound");
        if(state.decodedBytes+reserved>NativeTransferJournalFiles.MAX_NBT_BYTES)return null;
        var cells=footprint(header,request.before,request.after,request.held);
        R recipe=Objects.requireNonNull(family.readRecipe(world.registryAccess(),header,request.before,request.after,cells,request.recipe()));authorize(world,family,header,actor,recipe);if(!matches(world,family,request.before))throw new IllegalArgumentException("Cell transfer source changed before preparation");
        for(var pos:cells)if(com.vandorlabs.neoforge.ramp.NativeRampHeldOwners.blocked(world,pos))throw new IllegalArgumentException("Another transfer owns a required cell");
        var record=record(world,header,request.before,request.after,cells,request.recipe());var op=new Operation<R>(header,family,directory(world).resolve(header.operation+".nbt"),request.before,request.after,cells,recipe,record,Phase.PREPARING);if(op.decodedBytes!=reserved)throw new IllegalStateException("Cell journal recovery size estimate changed");op.actor=actor;if(state.decodedBytes+op.decodedBytes>NativeTransferJournalFiles.MAX_NBT_BYTES)return null;admit(state,op);try{op.disk=NativeTransferJournalFiles.write(op.file,null,record);}catch(RejectedExecutionException busy){op.disk=null;}catch(IOException failure){op.error=message(failure);op.rollback=true;remove(world,state,op);throw new IllegalStateException("Cannot prepare transfer journal",failure);}return op;
    }
    public static boolean busy(ServerLevel world,BlockPos root,UUID owner){thread(world);var state=WORLDS.get(world);if(state==null)return false;for(var op:state.operations.values())if(op.header.root.equals(root)&&op.header.owner.equals(owner))return true;return false;}
    /** Read-only authority observation for this exact family callback, never a mutation admission. */
    public static boolean reservedBy(ServerLevel world,Header header,BlockPos position){
        thread(world);var state=WORLDS.get(world);if(state==null)return false;var operation=state.operations.get(header.operation());return operation!=null&&operation.header==header&&operation.reservationDurable()&&operation.lease!=null&&operation.lease.live()&&operation.lease.contains(position)&&!com.vandorlabs.neoforge.ramp.NativeRampHeldOwners.foreignToCellReservation(world,position);
    }
    /** Configuration-only session observation; no slots, packet operation or mutation is admitted. */
    public static Header sessionHeader(ServerLevel world,BlockPos root,UUID owner,ServerPlayer actor){
        thread(world);if(actor==null||actor.serverLevel()!=world||!actor.isAlive()||actor.isRemoved()||actor.isSpectator())return null;var state=WORLDS.get(world);if(state==null)return null;for(var operation:state.operations.values()){var header=operation.header;if(operation.status()==Status.PENDING&&header.root.equals(root)&&header.owner.equals(owner)&&header.actor.equals(actor.getUUID())&&header.creative==actor.isCreative()&&reservedBy(world,header,root))return header;}return null;
    }
    public enum EscrowState {PENDING,CONFLICT,ABSENT}
    /** ABSENT means only that a successful startup scan has no matching operation. The family must
     * validate its persisted actor marker and its own terrain/entity checkpoint protocol before release. */
    public static <R> EscrowState escrowState(ServerLevel world,Family<R> family,UUID operation,BlockPos root,UUID owner,UUID actor,java.util.function.Predicate<R> receiptMatches){
        thread(world);if(FAMILIES.get(family.id())!=family)throw new IllegalArgumentException("Unregistered escrow family");var current=state(world);if(!current.error.isEmpty())return EscrowState.CONFLICT;if(!current.ready)return EscrowState.PENDING;var found=current.operations.get(operation);if(found==null)return EscrowState.ABSENT;
        boolean identity=found.family==family&&found.header.family.equals(family.id())&&found.header.root.equals(root)&&found.header.owner.equals(owner)&&found.header.actor.equals(actor);return classifyEscrow(true,false,true,identity,identity&&exactEscrow(found,family,Objects.requireNonNull(receiptMatches)));
    }
    @SuppressWarnings("unchecked")
    private static <R> boolean exactEscrow(Operation<?> operation,Family<R> family,java.util.function.Predicate<R> receiptMatches){return operation.family==family&&operation.recipe!=null&&receiptMatches.test((R)operation.recipe);}
    private static EscrowState classifyEscrow(boolean ready,boolean error,boolean present,boolean identity,boolean receipt){return error?EscrowState.CONFLICT:!ready?EscrowState.PENDING:!present?EscrowState.ABSENT:identity&&receipt?EscrowState.PENDING:EscrowState.CONFLICT;}
    private static Set<BlockPos> footprint(Header header,NativeTransferSnapshot before,NativeTransferSnapshot after,Collection<BlockPos> positions){
        var a=new HashSet<BlockPos>();var b=new HashSet<BlockPos>();for(var cell:before.cells())a.add(cell.pos);for(var cell:after.cells())b.add(cell.pos);if(!a.equals(b))throw new IllegalArgumentException("Cell journal before/after coverage differs");for(int i=0;i<before.cells().size();i++)if(!before.cells().get(i).pos.equals(after.cells().get(i).pos))throw new IllegalArgumentException("Cell journal before/after order differs");
        if(positions.isEmpty()||positions.size()>MAX_HELD)throw new IllegalArgumentException("Cell journal held position bound");var result=new LinkedHashSet<BlockPos>();for(var pos:positions){if(Math.abs((long)pos.getX()-header.root.getX())>MAX_ROOT_DISTANCE||Math.abs((long)pos.getY()-header.root.getY())>MAX_ROOT_DISTANCE||Math.abs((long)pos.getZ()-header.root.getZ())>MAX_ROOT_DISTANCE||!result.add(pos.immutable()))throw new IllegalArgumentException("Cell journal duplicate/out-of-bounds held position");}if(!result.containsAll(a)||!result.contains(header.root))throw new IllegalArgumentException("Cell journal publication outside exact held footprint");return Set.copyOf(result);
    }
    private static int recoveryBytes(CompoundTag record,int bytes){return Math.addExact(bytes,2*Math.max(0,"ROLLED_BACK".length()-record.getString("Stage").length()));}
    private static CompoundTag recordEnvelope(ServerLevel world,Header header,Collection<BlockPos> held,CompoundTag recipe){
        if(recipe.sizeInBytes()>MAX_RECIPE_BYTES)throw new IllegalArgumentException("Cell journal recipe byte bound");var result=new CompoundTag();result.putInt("NativeCellJournalSchema",1);result.putString("Family",header.family.toString());result.putString("Dimension",world.dimension().location().toString());result.putUUID("Operation",header.operation);result.putUUID("Owner",header.owner);result.putUUID("Actor",header.actor);result.putBoolean("Creative",header.creative);if(MAINTENANCE_ACTOR.equals(header.actor))result.putBoolean("Maintenance",true);result.putLong("Root",header.root.asLong());result.putString("Stage","PREPARED");result.put("Recipe",recipe.copy());long[] positions=held.stream().sorted().mapToLong(BlockPos::asLong).toArray();result.putLongArray("Held",positions);return result;
    }
    private static CompoundTag record(ServerLevel world,Header header,NativeTransferSnapshot before,NativeTransferSnapshot after,Set<BlockPos> held,CompoundTag recipe){
        var result=recordEnvelope(world,header,held,recipe);result.put("Before",before.write());result.put("After",after.write());if(result.sizeInBytes()>NativeTransferJournalFiles.MAX_NBT_BYTES)throw new IllegalArgumentException("Cell journal record byte bound");return result;
    }
    private static void admit(State state,Operation<?> op){
        if(state.operations.size()>=MAX_OPERATIONS||state.bytes+op.weight>WORLD_BYTES||retained+op.weight>GLOBAL_BYTES)throw new IllegalArgumentException("Cell journal retained memory bound");for(var old:state.operations.values())if(old.header.root.equals(op.header.root)||!Collections.disjoint(old.held,op.held))throw new IllegalArgumentException("Overlapping cell journals");if(state.operations.putIfAbsent(op.header.operation,op)!=null)throw new IllegalArgumentException("Duplicate cell journal identity");state.bytes+=op.weight;state.decodedBytes+=op.decodedBytes;retained+=op.weight;
    }
    private static State state(ServerLevel world){thread(world);var state=WORLDS.computeIfAbsent(world,ignored->new State());if(state.ready||!state.error.isEmpty())return state;try{if(state.loading==null){state.loading=NativeTransferJournalFiles.load(directory(world));return state;}if(!state.loading.isDone())return state;var loaded=state.loading.get();for(var saved:loaded){var op=read(world,saved);admit(state,op);op.prepared=true;op.armed=!op.diskRecord.getString("Stage").equals("PREPARED");if(op.armed)op.lease=NativeTransferHolds.freeze(world,op,op.held);}state.loading=null;state.ready=true;}catch(RejectedExecutionException busy){state.loading=null;}catch(Exception failure){state.error=message(failure);}return state;}
    private static final Set<String> RECORD_FIELDS=Set.of("NativeCellJournalSchema","Family","Dimension","Operation","Owner","Actor","Creative","Root","Stage","Before","After","Recipe","Held");
    private static final Set<String> MAINTENANCE_FIELDS;static {var fields=new HashSet<>(RECORD_FIELDS);fields.add("Maintenance");MAINTENANCE_FIELDS=Set.copyOf(fields);}
    private static Operation<?> read(ServerLevel world,NativeTransferJournalFiles.Saved saved){
        var raw=saved.record();boolean maintenance=raw.contains("Maintenance");if(maintenance&&(!raw.contains("Maintenance",Tag.TAG_BYTE)||raw.getByte("Maintenance")!=1))throw new IllegalArgumentException("Malformed maintenance marker");if(!raw.getAllKeys().equals(maintenance?MAINTENANCE_FIELDS:RECORD_FIELDS)||!raw.contains("NativeCellJournalSchema",Tag.TAG_INT)||raw.getInt("NativeCellJournalSchema")!=1||!raw.contains("Family",Tag.TAG_STRING)||!raw.contains("Dimension",Tag.TAG_STRING)||!raw.getString("Dimension").equals(world.dimension().location().toString())||!raw.hasUUID("Operation")||!raw.hasUUID("Owner")||!raw.hasUUID("Actor")||!raw.contains("Root",Tag.TAG_LONG)||!raw.contains("Creative",Tag.TAG_BYTE)||raw.getByte("Creative")<0||raw.getByte("Creative")>1||!raw.contains("Stage",Tag.TAG_STRING)||!Set.of("PREPARED","ARMED","APPLIED","ROLLED_BACK").contains(raw.getString("Stage"))||!raw.contains("Before",Tag.TAG_COMPOUND)||!raw.contains("After",Tag.TAG_COMPOUND)||!raw.contains("Recipe",Tag.TAG_COMPOUND)||!raw.contains("Held",Tag.TAG_LONG_ARRAY)||raw.getLongArray("Held").length>MAX_HELD||raw.getCompound("Recipe").sizeInBytes()>MAX_RECIPE_BYTES)throw new IllegalArgumentException("Malformed cell journal record");
        var id=ResourceLocation.tryParse(raw.getString("Family"));var family=id==null?null:FAMILIES.get(id);if(family==null)throw new IllegalArgumentException("Cell journal family is unavailable; original file retained");var root=BlockPos.of(raw.getLong("Root"));if(world.isOutsideBuildHeight(root)||!world.getWorldBorder().isWithinBounds(root))throw new IllegalArgumentException("Cell journal controller outside the world");
        var header=new Header(raw.getUUID("Operation"),id,root,raw.getUUID("Owner"),raw.getUUID("Actor"),raw.getBoolean("Creative"));if(maintenance!=maintenance(family)||maintenance!=MAINTENANCE_ACTOR.equals(header.actor)||maintenance&&(header.creative||MAINTENANCE_ACTOR.equals(header.owner)))throw new IllegalArgumentException("Cell journal authority marker mismatch");if(!saved.path().getFileName().toString().equals(header.operation+".nbt"))throw new IllegalArgumentException("Cell journal file identity mismatch");
        return readFamily(world,saved,raw,family,header);
    }
    private static <R> Operation<R> readFamily(ServerLevel world,NativeTransferJournalFiles.Saved saved,CompoundTag raw,Family<R> family,Header header){
        var before=NativeTransferSnapshot.read(world.registryAccess(),raw.getCompound("Before"),family.limits(),palette->Map.of());var after=NativeTransferSnapshot.read(world.registryAccess(),raw.getCompound("After"),family.limits(),palette->Map.of());var positions=new ArrayList<BlockPos>();for(long pos:raw.getLongArray("Held"))positions.add(BlockPos.of(pos));var held=footprint(header,before,after,positions);R recipe=Objects.requireNonNull(family.readRecipe(world.registryAccess(),header,before,after,held,raw.getCompound("Recipe")));
        var phase=raw.getString("Stage").equals("PREPARED")?Phase.RETIRING:raw.getString("Stage").equals("ARMED")?Phase.RECOVERING:Phase.RETIRING;var result=new Operation<R>(header,family,saved.path(),before,after,held,recipe,raw,phase);result.rollback=raw.getString("Stage").equals("ROLLED_BACK");result.mutated=!raw.getString("Stage").equals("PREPARED");return result;
    }
    private static boolean loaded(ServerLevel world,Operation<?> op){for(var pos:op.held)if(world.isOutsideBuildHeight(pos)||!world.getWorldBorder().isWithinBounds(pos)||world.getChunkSource().getChunkNow(pos.getX()>>4,pos.getZ()>>4)==null)return false;return true;}
    private static boolean matches(ServerLevel world,Family<?> family,NativeTransferSnapshot snapshot){return snapshot.matches(world,family::same);}
    private static <R> boolean outcome(ServerLevel world,Operation<R> op){return matches(world,op.family,op.rollback?op.before:op.after)&&op.family.additionalMatches(world,op.header,op.rollback,op.recipe);}
    private static void ensureReservation(ServerLevel world,Operation<?> op){if(op.lease!=null)return;if(!op.prepared)throw new IllegalStateException("Cell reservation before durable preparation");for(var pos:op.held)if(com.vandorlabs.neoforge.ramp.NativeRampHeldOwners.blocked(world,pos))throw new IllegalStateException("Another transfer reserved a required cell");op.lease=NativeTransferHolds.freeze(world,op,op.held);}
    private static <R> void authorize(ServerLevel world,Family<R> family,Header header,ServerPlayer actor,R recipe){
        if(maintenance(family)){if(actor!=null||!MAINTENANCE_ACTOR.equals(header.actor)||header.creative||MAINTENANCE_ACTOR.equals(header.owner))throw new IllegalArgumentException("Maintenance identity changed");((MaintenanceFamily<R>)family).authorizeMaintenance(world,header,recipe);}
        else {if(MAINTENANCE_ACTOR.equals(header.actor)||actor==null||actor.serverLevel()!=world||!actor.isAlive()||actor.isSpectator()||actor.isCreative()!=header.creative||!actor.getUUID().equals(header.actor))throw new IllegalArgumentException("Transfer actor changed before mutation");family.authorize(world,header,actor,recipe);}
    }
    private static <R> void revalidate(ServerLevel world,Operation<R> op){authorize(world,op.family,op.header,op.actor,op.recipe);if(!matches(world,op.family,op.before))throw new IllegalArgumentException("Transfer before-images changed before mutation");}
    private static void writeStage(Operation<?> op,String stage)throws IOException{var next=op.diskRecord.copy();next.putString("Stage",stage);op.disk=NativeTransferJournalFiles.write(op.file,op.diskRecord,next);op.nextRecord=next;}
    private static boolean completedDisk(Operation<?> op)throws Exception{if(op.disk==null||!op.disk.isDone())return false;op.disk.get();op.disk=null;if(op.nextRecord!=null){op.diskRecord=op.nextRecord;op.nextRecord=null;}return true;}
    private static <R> void install(ServerLevel world,Operation<R> op){
        if(!op.armed||op.lease==null||!loaded(world,op))throw new IllegalStateException("Unarmed or unloaded cell publication");revalidate(world,op);var staged=new ArrayList<BlockEntity>();for(var cell:op.after.cells())staged.add(op.family.stage(world,op.header,cell,op.recipe));revalidate(world,op);
        op.mutated=true;for(int i=0;i<op.after.cells().size();i++){if(op.before.cells().get(i).identical(op.after.cells().get(i)))continue;if(!matchesCell(world,op.family,op.before.cells().get(i)))throw new IllegalStateException("A later transfer cell changed during publication");op.family.publish(world,op.header,op.before.cells().get(i),op.after.cells().get(i),staged.get(i),op.lease,op.recipe);fault("after-cell",op,i);}
        op.family.installAdditional(world,op.header,op.recipe);if(!matches(world,op.family,op.after)||op.family.readyAdditional(world,op.header,false,op.recipe)&&!op.family.additionalMatches(world,op.header,false,op.recipe))throw new IllegalStateException("Transfer publication changed during installation");op.checkpoint=new NativeTransferCheckpoint(op.held);op.phase=Phase.CHECKPOINT;fault("after-install",op,-1);
    }
    private static <R> void rollback(ServerLevel world,Operation<R> op){
        if(!loaded(world,op)||world.noSave)return;ensureReservation(world,op);op.armed=true;
        for(int i=0;i<op.before.cells().size();i++){var first=op.before.cells().get(i);var second=op.after.cells().get(i);if(!matchesCell(world,op.family,first)&&!matchesCell(world,op.family,second)&&!op.family.intermediate(world,op.header,first,second,op.recipe))throw new IllegalStateException("Recovery encountered another cell owner at "+first.pos);}
        var staged=new ArrayList<BlockEntity>();for(var cell:op.before.cells())staged.add(op.family.stage(world,op.header,cell,op.recipe));op.rollback=true;op.family.beforeRollbackAdditional(world,op.header,op.recipe);
        for(int i=0;i<op.before.cells().size();i++){var cell=op.before.cells().get(i);if(matchesCell(world,op.family,cell))continue;if(!matchesCell(world,op.family,op.after.cells().get(i))&&!op.family.intermediate(world,op.header,cell,op.after.cells().get(i),op.recipe))throw new IllegalStateException("A later recovery cell changed during publication");op.family.publish(world,op.header,op.after.cells().get(i),cell,staged.get(i),op.lease,op.recipe);fault("after-rollback-cell",op,i);}
        op.family.rollbackAdditional(world,op.header,op.recipe);
        if(!matches(world,op.family,op.before)||op.family.readyAdditional(world,op.header,true,op.recipe)&&!op.family.additionalMatches(world,op.header,true,op.recipe))throw new IllegalStateException("Recovery did not preserve the before-images");op.checkpoint=new NativeTransferCheckpoint(op.held);op.phase=Phase.CHECKPOINT;
    }
    private static boolean matchesCell(ServerLevel world,Family<?> family,NativeRawCell cell){if(world.getBlockState(cell.pos)!=cell.state)return false;var tile=world.getBlockEntity(cell.pos);return cell.hasTile()?tile!=null&&family.same(cell.tile(),tile.saveWithFullMetadata(world.registryAccess())):tile==null;}
    private static <R> void poll(ServerLevel world,State state,Operation<R> op){
        if(op.phase==Phase.DONE||op.phase==Phase.FAILED)return;
        try{
            if(op.phase==Phase.RECONCILING){if(op.disk==null||!op.disk.isDone())return;var actual=(CompoundTag)op.disk.get();op.disk=null;op.nextRecord=null;if(actual==null){remove(world,state,op);return;}op.diskRecord=actual;op.prepared=true;String stage=actual.getString("Stage");if(op.mutated&&stage.equals("PREPARED"))throw new IllegalStateException("Mutated transfer lost its durable ARMED intent");if(!op.mutated){op.rollback=true;op.phase=Phase.RETIRING;}else if(stage.equals("ARMED")){op.phase=Phase.RECOVERING;}else{op.rollback=stage.equals("ROLLED_BACK");op.phase=Phase.RETIRING;}}
            if(op.phase==Phase.PREPARING){if(op.disk==null){try{op.disk=NativeTransferJournalFiles.write(op.file,null,op.diskRecord);}catch(RejectedExecutionException busy){return;}}if(!completedDisk(op))return;op.prepared=true;revalidate(world,op);ensureReservation(world,op);fault("prepared-durable",op,-1);op.phase=Phase.ARMING;}
            if(op.phase==Phase.ARMING){if(world.noSave||!loaded(world,op))return;if(op.disk==null){try{writeStage(op,"ARMED");}catch(RejectedExecutionException busy){return;}return;}if(!completedDisk(op))return;op.armed=true;op.phase=Phase.INSTALLING;fault("armed-durable",op,-1);}
            if(op.phase==Phase.INSTALLING){if(world.noSave||!loaded(world,op)||!op.family.readyAdditional(world,op.header,false,op.recipe))return;install(world,op);return;}
            if(op.phase==Phase.RECOVERING){if(world.noSave||!loaded(world,op)||!op.family.readyAdditional(world,op.header,true,op.recipe))return;rollback(world,op);return;}
            if(op.phase==Phase.CHECKPOINT){if(world.noSave||!loaded(world,op)||!op.family.readyAdditional(world,op.header,op.rollback,op.recipe))return;if(!op.family.additionalMatches(world,op.header,op.rollback,op.recipe))throw new IllegalStateException("Additional ownership changed before checkpoint");if(!op.checkpoint.poll(world,()->matches(world,op.family,op.rollback?op.before:op.after)))return;if(!op.family.readyAdditional(world,op.header,op.rollback,op.recipe))return;if(!op.family.additionalMatches(world,op.header,op.rollback,op.recipe))throw new IllegalStateException("Additional ownership changed after checkpoint");if(!op.family.checkpointAdditional(world,op.header,op.rollback,op.recipe))return;fault("checkpoint-durable",op,-1);op.phase=Phase.COMMITTING;}
            if(op.phase==Phase.COMMITTING){if(world.noSave||!loaded(world,op)||!op.family.readyAdditional(world,op.header,op.rollback,op.recipe))return;if(!outcome(world,op))throw new IllegalStateException("Ownership changed before journal completion");if(op.disk==null){try{writeStage(op,op.rollback?"ROLLED_BACK":"APPLIED");}catch(RejectedExecutionException busy){return;}return;}if(!completedDisk(op))return;op.phase=Phase.RETIRING;fault("committed-durable",op,-1);}
            if(op.phase==Phase.RETIRING){if(world.noSave)return;if(op.mutated){if(!loaded(world,op)||!op.family.readyAdditional(world,op.header,op.rollback,op.recipe))return;if(!outcome(world,op))throw new IllegalStateException("Committed transfer ownership changed before retirement");}if(op.disk==null){try{op.disk=NativeTransferJournalFiles.delete(op.file,op.diskRecord);op.retirementSubmitted=true;}catch(RejectedExecutionException busy){return;}return;}if(!completedDisk(op))return;remove(world,state,op);fault("retired-durable",op,-1);}
        }catch(Throwable failure){
            if(failure instanceof ThreadDeath||failure instanceof VirtualMachineError)throw (Error)failure;
            if(op.phase==Phase.DONE){System.err.println("[vandorlabs] Completed cell transfer observer failed: "+message(failure));return;}
            op.error=message(failure);op.actor=null;
            if(op.disk!=null&&!op.disk.isDone()){op.phase=Phase.FAILED;return;}
            // Any I/O failure retains the immutable journal and its reservation; no guessed cleanup or refund.
            if(failure instanceof IOException||failure instanceof ExecutionException||failure instanceof CompletionException){op.phase=Phase.FAILED;return;}
            op.disk=null;op.nextRecord=null;if(op.mutated){op.phase=Phase.RECOVERING;op.checkpoint=null;}else{op.rollback=true;op.phase=Phase.RETIRING;}
        }
    }
    private static <R> void remove(ServerLevel world,State state,Operation<R> op){
        if(op.lease!=null)op.lease.close();state.operations.remove(op.header.operation);state.bytes-=op.weight;state.decodedBytes-=op.decodedBytes;retained-=op.weight;op.phase=Phase.DONE;op.actor=null;op.checkpoint=null;op.disk=null;try{op.family.released(world,op.header,op.rollback,op.error,op.recipe);}catch(RuntimeException notification){System.err.println("[vandorlabs] Completed cell transfer notification failed: "+message(notification));}finally{op.before=null;op.after=null;op.held=Set.of();op.recipe=null;op.diskRecord=new CompoundTag();}
    }
    /** Explicit retry only; an interrupted disk operation is never restarted concurrently. */
    public static boolean retry(ServerLevel world,Operation<?> op){thread(world);var state=WORLDS.get(world);if(state==null||state.operations.get(op.header.operation)!=op||op.phase!=Phase.FAILED||op.disk!=null&&!op.disk.isDone())return false;try{op.disk=NativeTransferJournalFiles.reconcile(op.file,op.diskRecord,op.retirementSubmitted||!op.prepared&&!op.mutated);op.nextRecord=null;op.phase=Phase.RECONCILING;return true;}catch(RejectedExecutionException busy){return false;}}
    private static String message(Throwable failure){while((failure instanceof ExecutionException||failure instanceof CompletionException)&&failure.getCause()!=null)failure=failure.getCause();String value=failure.getMessage();if(value==null)value=failure.getClass().getSimpleName();return value.length()>512?value.substring(0,512):value;}
    interface Fault {void check(String stage,Header header,int cell);}
    static Fault fault;
    private static void fault(String stage,Operation<?> op,int cell){if(fault!=null&&Boolean.getBoolean("vandorlabs.cellJournalChecks"))fault.check(stage,op.header,cell);}
    @SubscribeEvent public static void tick(ServerTickEvent.Post event){if(FAMILIES.isEmpty())return;for(var world:event.getServer().getAllLevels()){var state=state(world);if(!state.ready||!state.error.isEmpty()||state.operations.isEmpty())continue;for(var op:List.copyOf(state.operations.values()))poll(world,state,op);}}
    @SubscribeEvent public static void startup(ServerStartingEvent event){
        var root=event.getServer().getWorldPath(LevelResource.ROOT).resolve("vandorlabs/cell-journals");
        try{try{if(!Files.readAttributes(root,java.nio.file.attribute.BasicFileAttributes.class).isDirectory())throw new IOException("Cell journal root is not a directory");}catch(NoSuchFileException absent){return;}long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(30);for(var world:event.getServer().getAllLevels())while(true){var state=state(world);if(!state.error.isEmpty())throw new IOException(state.error);if(state.ready){for(var op:List.copyOf(state.operations.values())){for(var pos:op.held)world.getChunk(pos.getX()>>4,pos.getZ()>>4);poll(world,state,op);if(op.phase==Phase.FAILED)throw new IOException(op.error);}if(state.operations.isEmpty())break;}if(System.nanoTime()-deadline>=0)throw new IOException("Cell journal recovery did not finish before gameplay");Thread.sleep(1);}}
        catch(Exception failure){if(failure instanceof InterruptedException)Thread.currentThread().interrupt();throw new IllegalStateException("Cell transfer recovery retained its original journals before gameplay",failure);}
    }
    @SubscribeEvent public static void unload(LevelEvent.Unload event){if(!(event.getLevel() instanceof ServerLevel world))return;thread(world);var state=WORLDS.remove(world);if(state==null)return;for(var op:state.operations.values()){if(op.lease!=null)op.lease.close();retained-=op.weight;op.actor=null;op.before=op.after=null;op.recipe=null;op.held=Set.of();op.diskRecord=new CompoundTag();op.phase=Phase.FAILED;op.error="World unloaded; immutable journal retained";}state.operations.clear();}
    private NativeCellTransactions(){}
}
