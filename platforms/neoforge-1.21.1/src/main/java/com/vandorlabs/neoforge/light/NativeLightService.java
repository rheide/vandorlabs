package com.vandorlabs.neoforge.light;

import com.vandorlabs.light.*;
import com.vandorlabs.tiles.HousingFinishCatalog;
import com.vandorlabs.neoforge.redstone.*;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.properties.Half;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

/** Deferred, identity-checked loaded Light groups. No world or tile is retained by a published projection. */
@EventBusSubscriber(modid="vandorlabs")
public final class NativeLightService {
    private static final Direction[] DIRECTIONS=Direction.values();
    private static final class Pending {final Set<NativeLightEntity> owners=Collections.newSetFromMap(new IdentityHashMap<>());final LinkedHashSet<BlockPos> positions=new LinkedHashSet<>();NativeLightEntity coordinator;}
    private static final Map<ServerLevel,Pending> PENDING=new IdentityHashMap<>();
    private static final Set<ServerLevel> CLOSED=Collections.newSetFromMap(new WeakHashMap<>()),APPLYING=Collections.newSetFromMap(new IdentityHashMap<>());
    private NativeLightService(){}
    private static boolean open(ServerLevel world){return !CLOSED.contains(world)&&NativeRedstoneChannels.open(world);}
    static boolean loaded(Level world,BlockPos pos){return world!=null&&!world.isOutsideBuildHeight(pos)&&world.getChunkSource().getChunkNow(pos.getX()>>4,pos.getZ()>>4)!=null;}
    public static boolean installed(NativeLightEntity owner){var world=owner.getLevel();return world instanceof ServerLevel server&&open(server)&&loaded(world,owner.getBlockPos())&&world.getBlockEntity(owner.getBlockPos())==owner&&world.getBlockState(owner.getBlockPos()).getBlock() instanceof NativeLightBlock;}
    /** Local enqueue only, safe from onLoad. */
    public static void queue(NativeLightEntity owner){if(owner.getLevel() instanceof ServerLevel world&&open(world))PENDING.computeIfAbsent(world,key->new Pending()).owners.add(owner);}
    public static void queue(Level level,BlockPos pos){if(level instanceof ServerLevel world&&open(world))PENDING.computeIfAbsent(world,key->new Pending()).positions.add(pos.immutable());}
    static void changedChannel(NativeLightEntity owner){if(!(owner.getLevel() instanceof ServerLevel world)||!open(world))return;queue(owner);var pending=PENDING.get(world);if(NativeSignalUpdateBatch.isActive()){if(pending.coordinator==null){pending.coordinator=owner;NativeSignalUpdateBatch.afterSignals(owner,()->flush(world));}}else flush(world);}
    public static void refresh(NativeLightEntity owner){if(!(owner.getLevel() instanceof ServerLevel world)||!open(world))return;queue(owner);flush(world);}
    static void retired(NativeLightEntity owner){if(!(owner.getLevel() instanceof ServerLevel world)||!open(world))return;queue(world,owner.getBlockPos());if(loaded(world,owner.getBlockPos())&&(!(world.getBlockEntity(owner.getBlockPos()) instanceof NativeLightEntity replacement)||replacement==owner)){var manager=world.getAuxLightManager(owner.getBlockPos());if(manager!=null&&manager.getLightAt(owner.getBlockPos())!=0)manager.removeLightAt(owner.getBlockPos());}}
    private static final class Graph implements ProgrammableLightJoining.Graph<BlockPos> {
        final ServerLevel world;final Direction[] neighbors;final Map<BlockPos,NativeLightEntity> owners=new HashMap<>();final Map<BlockPos,ProgrammableLightJoining.Node> nodes=new HashMap<>();final Set<BlockPos> absent=new HashSet<>();
        Graph(ServerLevel world,Direction facing){this.world=world;Direction right=facing.getAxis().isHorizontal()?facing.getClockWise():Direction.EAST,up=facing==Direction.UP?Direction.SOUTH:facing==Direction.DOWN?Direction.NORTH:Direction.UP;neighbors=new Direction[]{right,right.getOpposite(),up,up.getOpposite()};}
        public BlockPos neighbor(BlockPos pos,int ordinal){return pos.relative(neighbors[ordinal]);}
        public boolean loaded(BlockPos pos){return NativeLightService.loaded(world,pos);}
        public ProgrammableLightJoining.Node node(BlockPos pos){var known=nodes.get(pos);if(known!=null||absent.contains(pos))return known;if(!loaded(pos))throw new IllegalStateException("Cold Light metadata request");if(!(world.getBlockState(pos).getBlock() instanceof NativeLightBlock block)||!(world.getBlockEntity(pos) instanceof NativeLightEntity owner)||!installed(owner)){absent.add(pos.immutable());return null;}var state=world.getBlockState(pos);var data=owner.settings().light();int face=data.faceTexture<0?HousingFinishCatalog.legacyCount()+data.texture:data.faceTexture;var value=new ProgrammableLightJoining.Node(block.joiningIdentity(),state.getValue(NativeLightBlock.FACING).get3DDataValue(),block.kind()==NativeLightBlock.Kind.SLAB&&state.getValue(NativeLightBlock.HALF)==Half.TOP?1:0,face,block.kind()==NativeLightBlock.Kind.SLAB,owner.available(),data.join,owner.small(),owner.visual().on());owners.put(pos.immutable(),owner);nodes.put(pos.immutable(),value);return value;}
    }
    private static List<BlockPos> members(Graph graph,NativeLightEntity owner,boolean matchOn){var start=owner.getBlockPos();var node=graph.node(start);return node==null?List.of():ProgrammableLightJoining.members(start,node,graph,matchOn);}
    public static List<BlockPos> members(NativeLightEntity owner,boolean matchOn){if(!installed(owner)||!owner.available())return List.of();var world=(ServerLevel)owner.getLevel();return members(new Graph(world,owner.getBlockState().getValue(NativeLightBlock.FACING)),owner,matchOn);}
    static boolean manual(NativeLightEntity owner){if(!installed(owner)||!owner.available()||!ProgrammableLightPolicy.manualToggleAllowed(owner.settings().light().trigger))return false;refresh(owner);boolean next=!owner.visual().on();var graph=new Graph((ServerLevel)owner.getLevel(),owner.getBlockState().getValue(NativeLightBlock.FACING));var cells=members(graph,owner,true);for(var pos:cells){var member=graph.owners.get(pos);if(member!=null)member.stageManual(next);}for(var pos:cells){var member=graph.owners.get(pos);if(member!=null)queue(member);}flush((ServerLevel)owner.getLevel());return true;}
    public static void flush(ServerLevel world){if(!open(world)||!APPLYING.add(world))return;try{NativeRedstoneChannels.flush(world);Pending pending=PENDING.remove(world);if(pending==null)return;LinkedHashSet<BlockPos> touched=new LinkedHashSet<>(pending.positions);for(var owner:pending.owners)if(installed(owner))touched.add(owner.getBlockPos());LinkedHashSet<BlockPos> seeds=new LinkedHashSet<>();for(var pos:touched){seeds.add(pos);for(var side:DIRECTIONS)seeds.add(pos.relative(side));}Set<BlockPos> visited=new HashSet<>();Map<NativeLightEntity,NativeLightEntity.Stage> stages=new LinkedHashMap<>();for(var seed:seeds){if(visited.contains(seed)||!loaded(world,seed)||!(world.getBlockEntity(seed) instanceof NativeLightEntity owner)||!installed(owner))continue;if(!owner.available()){visited.add(seed);NativeRedstoneChannels.unregister(owner);stages.put(owner,owner.stage(0,ProgrammableLightJoining.settle(List.of())));continue;}var graph=new Graph(world,owner.getBlockState().getValue(NativeLightBlock.FACING));var cells=members(graph,owner,false);visited.addAll(cells);List<ProgrammableLightJoining.Input> inputs=new ArrayList<>(cells.size());Map<NativeLightEntity,Integer> physical=new LinkedHashMap<>();for(var pos:cells){var member=graph.owners.get(pos);if(member==null)continue;int local=NativeLoadedRedstonePower.level(world,pos);physical.put(member,local);inputs.add(new ProgrammableLightJoining.Input(member.settings().light().signalBrightness,Math.max(member.inheritedSettings().channelLevel(),local),member.inheritedSettings().scalar().channelSignal||local>0));}var settlement=ProgrammableLightJoining.settle(inputs);for(var entry:physical.entrySet())stages.put(entry.getKey(),entry.getKey().stage(entry.getValue(),settlement));}
        for(var entry:stages.entrySet())if(installed(entry.getKey()))entry.getKey().install(entry.getValue());
        for(var owner:stages.keySet())if(installed(owner))owner.publishLight();
        for(var entry:stages.entrySet())if(installed(entry.getKey()))entry.getKey().notifyStage(entry.getValue());
    }finally{APPLYING.remove(world);}}
    @SubscribeEvent public static void tick(LevelTickEvent.Post event){if(event.getLevel() instanceof ServerLevel world&&PENDING.containsKey(world))flush(world);}
    @SubscribeEvent public static void load(LevelEvent.Load event){if(event.getLevel() instanceof ServerLevel world)CLOSED.remove(world);}
    @SubscribeEvent public static void unload(LevelEvent.Unload event){if(event.getLevel() instanceof ServerLevel world){CLOSED.add(world);PENDING.remove(world);APPLYING.remove(world);}}
}
