package com.vandorlabs.gear;

import com.vandorlabs.copier.CopyWorld;
import com.vandorlabs.vehicle.TransferCheckpoint;
import net.minecraft.block.state.IBlockState;
import net.minecraft.nbt.*;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.*;
import net.minecraft.world.chunk.Chunk;
import net.minecraftforge.event.world.*;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.*;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;

/** Write-ahead ownership for arm/world transfers. Disk work never reads a live world. */
@Mod.EventBusSubscriber(modid="vandorlabs")
public final class ArmTransactions {
    private static final ThreadPoolExecutor IO=new ThreadPoolExecutor(1,1,30,TimeUnit.SECONDS,new ArrayBlockingQueue<Runnable>(32),r->{Thread t=new Thread(r,"Vandor arm journals");t.setDaemon(true);return t;},new ThreadPoolExecutor.AbortPolicy());
    private static final Map<WorldServer,Map<BlockPos,Hold>> HOLDS=new IdentityHashMap<>();
    private static final Map<WorldServer,Future<List<Hold>>> LOADING=new IdentityHashMap<>();
    private static final Set<WorldServer> LOADED=Collections.newSetFromMap(new IdentityHashMap<>());
    static java.util.function.Consumer<String> testFault;
    private static void fault(String stage){if(testFault!=null&&System.getProperty("vandorlabs.reprolab")!=null)testFault.accept(stage);}
    private static final ThreadLocal<GearTileEntity> ACTOR=new ThreadLocal<>();
    private static final ThreadLocal<Boolean> MATRIX=ThreadLocal.withInitial(()->false);
    static AutoCloseable matrix(){if(System.getProperty("vandorlabs.reprolab")==null)throw new IllegalStateException("Test fixture only");boolean old=MATRIX.get();MATRIX.set(true);return ()->MATRIX.set(old);}
    static final class Scope implements AutoCloseable {private final GearTileEntity before;Scope(GearTileEntity arm){before=ACTOR.get();ACTOR.set(arm);}@Override public void close(){if(before==null)ACTOR.remove();else ACTOR.set(before);}}
    static Scope authorizing(GearTileEntity arm){return new Scope(arm);}
    private static final class Hold {
        final WorldServer world;final Path file;final NBTTagCompound record;final BlockPos root;final List<BlockPos> cells=new ArrayList<>();
        Future<?> io;TransferCheckpoint checkpoint;boolean durable,applied,recovering,retiring,harvestPublished;int preparedTicks;String error;
        Hold(WorldServer world,Path file,NBTTagCompound record,boolean recovering){this.world=world;this.file=file;this.record=record;this.recovering=recovering;
            if(record.getInteger("Schema")!=1)throw new IllegalArgumentException("Unknown arm journal schema");root=BlockPos.fromLong(record.getLong("Root"));NBTTagList list=record.getTagList("Before",10);
            if(list.tagCount()<1||list.tagCount()>ArmPayload.MAX_BLOCKS+1)throw new IllegalArgumentException("Invalid arm journal size");Set<BlockPos> unique=new HashSet<>();
            for(int n=0;n<list.tagCount();n++){BlockPos pos=BlockPos.fromLong(list.getCompoundTagAt(n).getLong("Pos"));if(!unique.add(pos)||Math.abs((long)pos.getX()-root.getX())>128||Math.abs((long)pos.getY()-root.getY())>128||Math.abs((long)pos.getZ()-root.getZ())>128)throw new IllegalArgumentException("Invalid arm journal cells");cells.add(pos);}
            if(!unique.contains(root))throw new IllegalArgumentException("Arm journal has no controller");
        }
        boolean loaded(){for(NBTTagCompound item:ArmPayloadHarvest.items(record)){NBTTagList p=item.getTagList("Pos",6);if(!world.isBlockLoaded(new BlockPos(p.getDoubleAt(0),p.getDoubleAt(1),p.getDoubleAt(2)),false))return false;}for(BlockPos pos:cells)if(!world.isBlockLoaded(pos,false))return false;return true;}
        Set<Chunk> chunks(){Set<Chunk> chunks=new LinkedHashSet<>();for(BlockPos pos:cells)chunks.add(world.getChunkFromBlockCoords(pos));for(NBTTagCompound item:ArmPayloadHarvest.items(record)){NBTTagList p=item.getTagList("Pos",6);chunks.add(world.getChunkFromBlockCoords(new BlockPos(p.getDoubleAt(0),p.getDoubleAt(1),p.getDoubleAt(2))));}return chunks;}
    }
    private static Path directory(WorldServer world){return world.getSaveHandler().getWorldDirectory().toPath().resolve("data/vandorlabs-arms-"+world.provider.getDimension());}
    private static final class PreparationRejected extends IOException {PreparationRejected(Throwable cause){super("Attachment recovery data is too large; source retained",cause);}}
    static byte[] encodeForRecovery(NBTTagCompound record)throws IOException{
        try{ByteArrayOutputStream bytes=new ByteArrayOutputStream();CompressedStreamTools.writeCompressed(record,bytes);if(bytes.size()>8*1024*1024)throw new IOException("Compressed journal limit");byte[] encoded=bytes.toByteArray();try(DataInputStream input=new DataInputStream(new java.util.zip.GZIPInputStream(new ByteArrayInputStream(encoded)))){long budget=16L*1024*1024;if("harvest".equals(record.getString("Operation"))&&!record.getBoolean("HarvestCommitted"))budget-=1024;CompressedStreamTools.read(input,new NBTSizeTracker(budget));}return encoded;}
        catch(IOException|RuntimeException rejected){throw new PreparationRejected(rejected);}
    }
    private static boolean rejectPreparation(Hold hold,Exception failure){
        Throwable cause=failure instanceof ExecutionException?failure.getCause():failure;if(!(cause instanceof PreparationRejected)||hold.durable||hold.applied||hold.recovering)return false;
        Map<BlockPos,Hold> holds=HOLDS.get(hold.world);if(holds!=null)holds.remove(hold.root,hold);
        for(BlockPos at:hold.cells)if(!at.equals(hold.root))CopyWorld.publish(hold.world,at);
        TileEntity tile=hold.world.getTileEntity(hold.root);if(tile instanceof GearTileEntity)((GearTileEntity)tile).preparationRejected(cause.getMessage());
        return true;
    }
    private static void write(Path file,NBTTagCompound record)throws IOException{
        byte[] encoded=encodeForRecovery(record);
        Files.createDirectories(file.getParent());Path temp=file.resolveSibling(file.getFileName()+".tmp");
        try(FileOutputStream output=new FileOutputStream(temp.toFile())){output.write(encoded);output.getFD().sync();}
        Files.move(temp,file,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);
        com.vandorlabs.persistence.JournalFiles.syncDirectory(file.getParent());
    }
    private static void beginLoad(WorldServer world){
        if(LOADED.contains(world)||LOADING.containsKey(world))return;
        Path directory=directory(world);
        try{LOADING.put(world,IO.submit(()->{List<Hold> result=new ArrayList<>();if(Files.isDirectory(directory))try(DirectoryStream<Path> files=Files.newDirectoryStream(directory,"*.nbt")){for(Path file:files){if(result.size()>=256||Files.size(file)>8*1024*1024)throw new IOException("Arm recovery journal limit exceeded");try(InputStream input=Files.newInputStream(file);DataInputStream data=new DataInputStream(new java.util.zip.GZIPInputStream(input))){result.add(new Hold(world,file,CompressedStreamTools.read(data,new NBTSizeTracker(16L*1024*1024)),true));}}}return result;}));}catch(RejectedExecutionException busy){/* Retry without allowing a transfer to start. */}
    }
    private static boolean load(WorldServer world){
        if(LOADED.contains(world))return true;beginLoad(world);Future<List<Hold>> future=LOADING.get(world);if(future==null||!future.isDone())return false;
        try{Map<BlockPos,Hold> holds=HOLDS.computeIfAbsent(world,w->new LinkedHashMap<>());for(Hold hold:future.get()){if(holds.putIfAbsent(hold.root,hold)!=null)throw new IOException("Overlapping arm recovery controllers");}LOADED.add(world);LOADING.remove(world);return true;}
        catch(Exception failure){return false;}
    }
    public static boolean locked(World world,BlockPos pos){
        if(MATRIX.get()||!(world instanceof WorldServer))return false;WorldServer server=(WorldServer)world;if(!load(server))return true;
        GearTileEntity actor=ACTOR.get();Map<BlockPos,Hold> holds=HOLDS.get(server);if(holds!=null)for(Hold hold:holds.values())if(hold.cells.contains(pos)&&(actor==null||actor.getWorld()!=world||!actor.getPos().equals(hold.root)))return true;return false;
    }
    /** Matrix fixtures exercise mutation/rollback separately from real disk/restart checks. */
    static void prepareHarvest(GearTileEntity arm,NBTTagCompound extra){try(Scope scope=authorizing(arm)){ready(arm,"harvest",Collections.emptyList(),extra);}}
    static boolean ready(GearTileEntity arm,String operation,Collection<BlockPos> changed){return ready(arm,operation,changed,null);}
    private static boolean ready(GearTileEntity arm,String operation,Collection<BlockPos> changed,NBTTagCompound extra){
        if(MATRIX.get()||!(arm.getWorld() instanceof WorldServer))return true;WorldServer world=(WorldServer)arm.getWorld();
        if(world.disableLevelSaving)throw new IllegalArgumentException("Enable world saving before moving an arm attachment");if(!load(world))return false;
        Map<BlockPos,Hold> holds=HOLDS.computeIfAbsent(world,w->new LinkedHashMap<>());Hold hold=holds.get(arm.getPos());
        if(hold!=null){if(hold.recovering||hold.applied||!hold.record.getString("Operation").equals(operation))return false;if(hold.error!=null)throw new IllegalArgumentException(hold.error);if(hold.durable){fault("prepared");return true;}if(hold.io==null||!hold.io.isDone())return false;try{hold.io.get();hold.io=null;hold.durable=true;fault("prepared");return true;}catch(Exception failure){if(rejectPreparation(hold,failure))throw new IllegalArgumentException("Attachment recovery data is too large; source retained",failure);hold.error="Arm journal could not be saved; source retained";throw new IllegalArgumentException(hold.error,failure);}}
        if(holds.size()>=8)return false;
        Set<BlockPos> positions=new LinkedHashSet<>(changed);positions.add(arm.getPos());NBTTagList before=new NBTTagList();
        for(BlockPos pos:positions){if(!world.isBlockLoaded(pos,false)||locked(world,pos))throw new IllegalArgumentException("Arm transfer cells are unavailable");NBTTagCompound cell=new NBTTagCompound();cell.setLong("Pos",pos.toLong());cell.setTag("State",NBTUtil.writeBlockState(new NBTTagCompound(),world.getBlockState(pos)));TileEntity tile=world.getTileEntity(pos);if(tile!=null)cell.setTag("Tile",tile.writeToNBT(new NBTTagCompound()));before.appendTag(cell);}
        NBTTagCompound record=new NBTTagCompound();record.setInteger("Schema",1);record.setLong("Root",arm.getPos().toLong());record.setString("Operation",operation);java.util.UUID authorized=com.vandorlabs.tiles.WorldActionAuthorization.actorId(arm);if(authorized!=null)record.setUniqueId("AuthorizationActor",authorized);record.setTag("Before",before);if(extra!=null)record.merge(extra);
        Hold created=new Hold(world,directory(world).resolve(UUID.randomUUID()+".nbt"),record,false);
        try{created.io=IO.submit(()->{write(created.file,record);return null;});holds.put(created.root,created);for(BlockPos pos:created.cells)if(!pos.equals(created.root))freeze(created,pos);}catch(RejectedExecutionException busy){return false;}return false;
    }
    private static void freeze(Hold hold,BlockPos pos){
        // Preparing native endpoint publication must not replace the arm's own
        // inert member tiles with proxies: discovery would see a broken column
        // and the controller could never finish the prepared transfer. Their
        // positions remain locked; once applied, the new payload tiles freeze.
        TileEntity raw=hold.world.getTileEntity(pos);
        if(!hold.applied&&!hold.recovering&&hold.record.getString("Operation").equals("restore")&&raw instanceof GearTileEntity&&hold.root.equals(((GearTileEntity)raw).owner()))return;
        CopyWorld.freeze(hold.world,pos);
    }
    static void applied(GearTileEntity arm){if(MATRIX.get()||!(arm.getWorld() instanceof WorldServer))return;Map<BlockPos,Hold> holds=HOLDS.get(arm.getWorld());Hold hold=holds==null?null:holds.get(arm.getPos());if(hold!=null){hold.applied=true;for(BlockPos pos:hold.cells)if(!pos.equals(hold.root))freeze(hold,pos);fault(hold.record.getString("Operation"));}}
    static void failed(GearTileEntity arm){if(MATRIX.get())return;Map<BlockPos,Hold> holds=HOLDS.get(arm.getWorld());Hold hold=holds==null?null:holds.get(arm.getPos());if(hold!=null){hold.recovering=true;hold.applied=false;}}
    public static boolean blocked(GearTileEntity arm){if(MATRIX.get()||!(arm.getWorld() instanceof WorldServer))return false;WorldServer world=(WorldServer)arm.getWorld();if(!load(world))return true;Map<BlockPos,Hold> holds=HOLDS.get(world);Hold hold=holds==null?null:holds.get(arm.getPos());return hold!=null&&(hold.applied||hold.recovering||hold.retiring||hold.record.getString("Operation").equals("harvest"));}
    private static void recover(Hold hold)throws Exception{
        if(hold.record.getBoolean("HarvestCommitted")){hold.applied=true;hold.recovering=false;return;}
        ArmPayloadHarvest.rollback(hold.world,hold.record);
        NBTTagList before=hold.record.getTagList("Before",10);List<TileEntity> prepared=new ArrayList<>();List<IBlockState> states=new ArrayList<>();
        for(int n=0;n<before.tagCount();n++){NBTTagCompound cell=before.getCompoundTagAt(n);IBlockState state=NBTUtil.readBlockState(cell.getCompoundTag("State"));if(!NBTUtil.writeBlockState(new NBTTagCompound(),state).equals(cell.getCompoundTag("State")))throw new IllegalArgumentException("Recovery block is unavailable");states.add(state);TileEntity tile=cell.hasKey("Tile",10)?TileEntity.create(null,cell.getCompoundTag("Tile")):null;if(cell.hasKey("Tile",10)&&(tile==null||!tile.writeToNBT(new NBTTagCompound()).equals(cell.getCompoundTag("Tile"))))throw new IllegalArgumentException("Recovery tile cannot round-trip");prepared.add(tile);}
        try(AutoCloseable atomic=com.vandorlabs.vehicle.VehicleTransfer.atomic(hold.world)){
            for(int n=0;n<before.tagCount();n++){BlockPos pos=hold.cells.get(n);hold.world.removeTileEntity(pos);if(!hold.world.setBlockState(pos,states.get(n),2)&&hold.world.getBlockState(pos)!=states.get(n))throw new IllegalStateException("Arm recovery block refused");TileEntity tile=prepared.get(n);if(tile!=null){hold.world.removeTileEntity(pos);tile.setWorld(hold.world);tile.setPos(pos);tile.validate();hold.world.setTileEntity(pos,tile);tile.markDirty();}if(!pos.equals(hold.root))freeze(hold,pos);}
        }
        if(hold.record.hasUniqueId("AuthorizationActor"))com.vandorlabs.tiles.WorldActionAuthorization.grant(hold.world.getTileEntity(hold.root),hold.record.getUniqueId("AuthorizationActor"));
        hold.applied=true;hold.recovering=false;
    }
    private static void step(Hold hold)throws Exception{
        if(hold.error!=null)return;if(!hold.harvestPublished)ArmPayloadHarvest.hold(hold.world,hold.record);for(BlockPos pos:hold.cells)if(!pos.equals(hold.root)&&hold.world.isBlockLoaded(pos,false))freeze(hold,pos);if(!hold.loaded())return;if(hold.io!=null){if(!hold.io.isDone())return;try{hold.io.get();}catch(Exception failure){if(rejectPreparation(hold,failure))return;throw failure;}hold.io=null;hold.durable=true;if(hold.retiring){for(BlockPos pos:hold.cells)if(!pos.equals(hold.root))CopyWorld.publish(hold.world,pos);HOLDS.get(hold.world).remove(hold.root);return;}}
        if(!hold.applied&&!hold.recovering&&hold.durable&&hold.record.getString("Operation").equals("harvest")){try{fault("harvest-prepared");ArmPayloadHarvest.apply(hold.world,hold.root,hold.record);hold.applied=true;fault("harvest");}catch(Exception denied){hold.recovering=true;}}
        if(!hold.applied&&!hold.recovering&&hold.durable&&++hold.preparedTicks>=200)hold.recovering=true;
        if(hold.recovering)recover(hold);if(!hold.applied)return;
        if(hold.record.getBoolean("HarvestCommitted")&&!hold.harvestPublished){fault("harvest-committed");ArmPayloadHarvest.publish(hold.world,hold.record);hold.harvestPublished=true;}
        if(hold.checkpoint==null)hold.checkpoint=new TransferCheckpoint(hold.world,hold.chunks());if(!hold.checkpoint.step())return;hold.checkpoint=null;
        if(hold.record.getString("Operation").equals("harvest")&&!hold.record.getBoolean("HarvestCommitted")){hold.record.setBoolean("HarvestCommitted",true);NBTTagCompound stable=hold.record.copy();hold.io=IO.submit(()->{write(hold.file,stable);return null;});return;}
        try{hold.io=IO.submit(()->{Files.deleteIfExists(hold.file);com.vandorlabs.persistence.JournalFiles.syncDirectory(hold.file.getParent());return null;});hold.retiring=true;}catch(RejectedExecutionException busy){/* Keep locked until journal retirement can be scheduled. */}
    }
    @SubscribeEvent public static void tick(TickEvent.WorldTickEvent event){if(event.phase!=TickEvent.Phase.START||!(event.world instanceof WorldServer)||MATRIX.get())return;WorldServer world=(WorldServer)event.world;if(!load(world))return;Map<BlockPos,Hold> holds=HOLDS.get(world);if(holds!=null)for(Hold hold:new ArrayList<>(holds.values()))try{step(hold);}catch(Exception|LinkageError failure){hold.error="Arm recovery retained: "+failure.getMessage();com.vandorlabs.VandorLabs.logger.error(hold.error,failure);}}
    @SubscribeEvent public static void loaded(WorldEvent.Load event){if(event.getWorld() instanceof WorldServer){WorldServer world=(WorldServer)event.getWorld();beginLoad(world);try{Future<List<Hold>> future=LOADING.get(world);if(future!=null)future.get();load(world);}catch(Exception failure){com.vandorlabs.VandorLabs.logger.error("Arm recovery journals unavailable; transfers remain locked",failure);}}}
    @SubscribeEvent public static void chunk(ChunkEvent.Load event){Map<BlockPos,Hold> holds=HOLDS.get(event.getWorld());if(holds!=null)for(Hold hold:holds.values())for(BlockPos pos:hold.cells)if(!pos.equals(hold.root)&&(pos.getX()>>4)==event.getChunk().x&&(pos.getZ()>>4)==event.getChunk().z)freeze(hold,pos);}
    @SubscribeEvent public static void unloaded(WorldEvent.Unload event){Map<BlockPos,Hold> holds=HOLDS.remove(event.getWorld());if(holds!=null)for(Hold hold:holds.values())if(hold.checkpoint!=null)hold.checkpoint.close();LOADING.remove(event.getWorld());LOADED.remove(event.getWorld());}
    @SubscribeEvent(priority=EventPriority.HIGHEST) public static void breaking(BlockEvent.BreakEvent event){if(locked(event.getWorld(),event.getPos()))event.setCanceled(true);}
    @SubscribeEvent(priority=EventPriority.HIGHEST) public static void placing(BlockEvent.PlaceEvent event){if(locked(event.getWorld(),event.getPos()))event.setCanceled(true);}
    @SubscribeEvent(priority=EventPriority.HIGHEST) public static void use(PlayerInteractEvent.RightClickBlock event){if(locked(event.getWorld(),event.getPos())){event.setCanceled(true);event.setCancellationResult(net.minecraft.util.EnumActionResult.SUCCESS);}}
    private ArmTransactions(){}
}
