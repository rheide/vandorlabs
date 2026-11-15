package com.vandorlabs.neoforge.transfer;

import java.lang.reflect.InvocationTargetException;
import java.nio.file.Path;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.nbt.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.*;

/** Narrow maintenance authority proof piggybacked on the existing asynchronous player fixture. */
final class NativeCellMaintenanceChecks {
    private static final BlockPos ROOT=new BlockPos(24,110,8),SOURCE=ROOT.east(),TARGET=ROOT.east(2);
    private static final UUID OWNER=new UUID(0,0x7191);
    private static final NativeTransferSnapshot.Limits LIMITS=new NativeTransferSnapshot.Limits(3,256*1024,1024*1024,8*1024*1024);
    private static ServerLevel world;private static NativeCellTransactions.Operation<Boolean> operation;
    private static NativeCellTransactions.Request request;private static int checks,authorizations,publications;private static boolean reloaded;
    private static void need(boolean condition,String why){checks++;if(!condition)throw new AssertionError("Maintenance authority: "+why);}
    private static void rejects(Runnable action,String why){try{action.run();}catch(IllegalArgumentException expected){checks++;return;}throw new AssertionError("Maintenance admitted "+why);}
    private static final class Family implements NativeCellTransactions.MaintenanceFamily<Boolean> {
        private final ResourceLocation id;Family(String name){id=ResourceLocation.parse("vandorlabs:"+name);}
        public ResourceLocation id(){return id;}public NativeTransferSnapshot.Limits limits(){return LIMITS;}
        public Boolean readRecipe(HolderLookup.Provider registries,NativeCellTransactions.Header h,NativeTransferSnapshot before,NativeTransferSnapshot after,Set<BlockPos> held,CompoundTag raw){
            if(!h.root().equals(ROOT)||!h.owner().equals(OWNER)||!h.actor().equals(NativeCellTransactions.MAINTENANCE_ACTOR)||h.creative()||!raw.getAllKeys().equals(Set.of("Schema"))||!raw.contains("Schema",Tag.TAG_INT)||raw.getInt("Schema")!=1||!held.equals(Set.of(ROOT,SOURCE,TARGET))||before.cells().size()!=3||after.cells().size()!=3)throw new IllegalArgumentException("Maintenance recipe identity");
            if(!before.cells().get(0).pos.equals(ROOT)||!before.cells().get(0).identical(after.cells().get(0))||!before.cells().get(0).state.is(Blocks.CHEST)||!before.cells().get(1).pos.equals(SOURCE)||!before.cells().get(1).state.is(Blocks.GOLD_BLOCK)||!after.cells().get(1).state.isAir()||!before.cells().get(2).pos.equals(TARGET)||!before.cells().get(2).state.isAir()||!after.cells().get(2).state.is(Blocks.GOLD_BLOCK))throw new IllegalArgumentException("Maintenance exact recipe");return true;
        }
        public void authorizeMaintenance(ServerLevel level,NativeCellTransactions.Header h,Boolean recipe){authorizations++;if(!(level.getBlockEntity(ROOT) instanceof ChestBlockEntity root)||!root.getPersistentData().hasUUID("FixtureOwner")||!root.getPersistentData().getUUID("FixtureOwner").equals(h.owner()))throw new IllegalArgumentException("Maintenance physical root owner changed");}
        public BlockEntity stage(ServerLevel level,NativeCellTransactions.Header h,NativeRawCell cell,Boolean recipe){return cell.stage(level,tile->tile.getClass()==ChestBlockEntity.class,CompoundTag::equals);}
        public boolean same(CompoundTag a,CompoundTag b){return Objects.equals(a,b);}
        public void publish(ServerLevel level,NativeCellTransactions.Header h,NativeRawCell before,NativeRawCell after,BlockEntity staged,NativeTransferHolds.Lease lease,Boolean recipe){try(var scope=lease.allowOnce(after.pos)){if(!level.setBlock(after.pos,after.state,Block.UPDATE_KNOWN_SHAPE))throw new IllegalStateException("Maintenance publication refused");}publications++;}
    }
    private static final Family FAMILY=new Family("maintenance_core_fixture"),UNREGISTERED=new Family("maintenance_unregistered_fixture");
    static void register(){rejects(()->NativeCellTransactions.register(FAMILY),"ordinary registration of maintenance family");NativeCellTransactions.registerMaintenance(FAMILY);}
    static void begin(ServerLevel level,ServerPlayer actor){
        world=level;world.setChunkForced(ROOT.getX()>>4,ROOT.getZ()>>4,true);world.getChunkAt(ROOT);world.setBlock(ROOT,Blocks.CHEST.defaultBlockState(),3);world.setBlock(SOURCE,Blocks.GOLD_BLOCK.defaultBlockState(),3);world.setBlock(TARGET,Blocks.AIR.defaultBlockState(),3);var root=(ChestBlockEntity)world.getBlockEntity(ROOT);root.getPersistentData().putUUID("FixtureOwner",OWNER);root.setChanged();var cell=capture(ROOT);
        var before=new NativeTransferSnapshot(List.of(cell,capture(SOURCE),capture(TARGET)),LIMITS);var after=new NativeTransferSnapshot(List.of(cell,new NativeRawCell(SOURCE,Blocks.AIR.defaultBlockState(),null,256*1024),new NativeRawCell(TARGET,Blocks.GOLD_BLOCK.defaultBlockState(),null,256*1024)),LIMITS);var recipe=new CompoundTag();recipe.putInt("Schema",1);request=new NativeCellTransactions.Request(ROOT,OWNER,before,after,List.of(ROOT,SOURCE,TARGET),recipe);
        rejects(()->NativeCellTransactions.submitMaintenance(world,UNREGISTERED,request),"unregistered maintenance family");rejects(()->NativeCellTransactions.submit(world,FAMILY,actor,request),"player route into maintenance family");rejects(()->NativeCellTransactions.submitMaintenance(world,FAMILY,new NativeCellTransactions.Request(ROOT,NativeCellTransactions.MAINTENANCE_ACTOR,before,after,request.held(),recipe)),"zero owner");root.getPersistentData().putUUID("FixtureOwner",new UUID(0,42));rejects(()->NativeCellTransactions.submitMaintenance(world,FAMILY,request),"foreign physical root");root.getPersistentData().putUUID("FixtureOwner",OWNER);root.setChanged();
        NativeCellTransactions.fault=(point,header,cellIndex)->{if(header.family().equals(FAMILY.id())&&point.equals("armed-durable"))records();};
    }
    static boolean tick(){if(operation==null){operation=NativeCellTransactions.submitMaintenance(world,FAMILY,request);if(operation==null)return false;need(operation.header.actor().equals(NativeCellTransactions.MAINTENANCE_ACTOR)&&!operation.header.creative(),"explicit zero actor is noncreative");}if(operation.status()==NativeCellTransactions.Status.FAILED)throw new AssertionError(operation.error());if(operation.status()!=NativeCellTransactions.Status.DONE)return false;need(operation.error().isEmpty()&&reloaded,"real maintenance completed after durable record reload");need(authorizations>=4&&publications==2,"authority checked across asynchronous boundaries");need(world.getBlockState(SOURCE).isAir()&&world.getBlockState(TARGET).is(Blocks.GOLD_BLOCK),"exactly one destination gold owner");need(!NativeTransferHolds.active(world)&&!NativeCellTransactions.busy(world,ROOT,OWNER),"durable completion releases reservation");return true;}
    private static void records(){try{
        var fileField=operation.getClass().getDeclaredField("file");fileField.setAccessible(true);var file=(Path)fileField.get(operation);var raw=NativeTransferJournalFiles.read(file);need(raw.getString("Stage").equals("ARMED")&&raw.getBoolean("Maintenance"),"actual durable ARMED maintenance marker");read(file,raw);need(true,"valid durable maintenance record reloads");
        var bad=raw.copy();bad.remove("Maintenance");rejectRecord(file,bad,"missing marker");bad=raw.copy();bad.putInt("Maintenance",1);rejectRecord(file,bad,"wrong marker type");bad=raw.copy();bad.putBoolean("Maintenance",false);rejectRecord(file,bad,"false marker");bad=raw.copy();bad.putUUID("Actor",new UUID(0,42));rejectRecord(file,bad,"player actor on maintenance");bad=raw.copy();bad.putBoolean("Creative",true);rejectRecord(file,bad,"creative maintenance");bad=raw.copy();bad.putUUID("Owner",NativeCellTransactions.MAINTENANCE_ACTOR);rejectRecord(file,bad,"zero owner record");bad=raw.copy();bad.putString("Family","vandorlabs:cell_core_fixture");rejectRecord(file,bad,"ordinary family authority escalation");need(NativeTransferJournalFiles.read(file).equals(raw),"negative parsing preserves authoritative journal bytes");reloaded=true;
    }catch(ReflectiveOperationException|java.io.IOException failure){throw new IllegalStateException(failure);}}
    private static void read(Path path,CompoundTag raw)throws ReflectiveOperationException{var method=NativeCellTransactions.class.getDeclaredMethod("read",ServerLevel.class,NativeTransferJournalFiles.Saved.class);method.setAccessible(true);try{method.invoke(null,world,new NativeTransferJournalFiles.Saved(path,raw));}catch(InvocationTargetException failure){if(failure.getCause() instanceof IllegalArgumentException refused)throw refused;throw failure;}}
    private static void rejectRecord(Path file,CompoundTag raw,String why)throws ReflectiveOperationException{try{read(file,raw);}catch(IllegalArgumentException expected){checks++;return;}throw new AssertionError("Malformed maintenance record admitted: "+why);}
    private static NativeRawCell capture(BlockPos pos){var tile=world.getBlockEntity(pos);return new NativeRawCell(pos,world.getBlockState(pos),tile==null?null:tile.saveWithFullMetadata(world.registryAccess()),256*1024);}
    static int checks(){return checks;}
    private NativeCellMaintenanceChecks(){}
}
