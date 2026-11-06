package com.vandorlabs.neoforge.light;
import com.vandorlabs.light.*;
import com.vandorlabs.tiles.HousingFinishCatalog;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.properties.Half;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
/** Owning-thread preparation. Published values retain no world, tile, graph or member collection. */
@EventBusSubscriber(modid="vandorlabs")
public final class NativeLightRenderService {
 private static final Direction[] SIDES=Direction.values();
 private static final ThreadLocal<Map<Level,LinkedHashSet<BlockPos>>> PENDING=ThreadLocal.withInitial(WeakHashMap::new);
 private static final ThreadLocal<Set<Level>> CLOSED=ThreadLocal.withInitial(()->Collections.newSetFromMap(new WeakHashMap<>()));
 private NativeLightRenderService(){}
 public static void queue(NativeLightEntity owner){if(owner.getLevel()!=null)queue(owner.getLevel(),owner.getBlockPos());}
 /** Local only: no chunk, neighbor or tile lookup. */
 public static void queue(Level world,BlockPos pos){if(world!=null&&!CLOSED.get().contains(world))PENDING.get().computeIfAbsent(world,key->new LinkedHashSet<>()).add(pos.immutable());}
 private static boolean installed(Level world,NativeLightEntity owner){return NativeLightService.loaded(world,owner.getBlockPos())&&world.getBlockEntity(owner.getBlockPos())==owner&&!owner.isRemoved()&&owner.getBlockState().getBlock() instanceof NativeLightBlock;}
 private static Direction right(Direction facing){return facing.getAxis().isHorizontal()?facing.getClockWise():Direction.EAST;}
 private static Direction up(Direction facing){return facing==Direction.UP?Direction.SOUTH:facing==Direction.DOWN?Direction.NORTH:Direction.UP;}
 private static int axis(BlockPos pos,Direction direction){return pos.getX()*direction.getStepX()+pos.getY()*direction.getStepY()+pos.getZ()*direction.getStepZ();}
 private static final class Graph implements ProgrammableLightJoining.Graph<BlockPos> {
  final Level world;final Direction[] neighbors;final Map<BlockPos,NativeLightEntity> owners=new HashMap<>();final Map<BlockPos,ProgrammableLightJoining.Node> nodes=new HashMap<>();final Set<BlockPos> absent=new HashSet<>();
  Graph(Level world,Direction facing){this.world=world;var right=right(facing);var up=up(facing);neighbors=new Direction[]{right,right.getOpposite(),up,up.getOpposite()};}
  public BlockPos neighbor(BlockPos pos,int ordinal){return pos.relative(neighbors[ordinal]);}public boolean loaded(BlockPos pos){return NativeLightService.loaded(world,pos);}
  public ProgrammableLightJoining.Node node(BlockPos pos){var known=nodes.get(pos);if(known!=null||absent.contains(pos))return known;if(!loaded(pos))throw new IllegalStateException("Cold Light render metadata request");if(!(world.getBlockState(pos).getBlock() instanceof NativeLightBlock block)||!(world.getBlockEntity(pos) instanceof NativeLightEntity owner)||!installed(world,owner)){absent.add(pos.immutable());return null;}var visual=owner.visual();var data=visual.settings().light();int face=data.faceTexture<0?HousingFinishCatalog.legacyCount()+data.texture:data.faceTexture;var state=owner.getBlockState();var value=new ProgrammableLightJoining.Node(block.joiningIdentity(),visual.facing().get3DDataValue(),visual.upper()?1:0,face,block.kind()==NativeLightBlock.Kind.SLAB,owner.available(),data.join,visual.small(),visual.on());owners.put(pos.immutable(),owner);nodes.put(pos.immutable(),value);return value;}
 }
 private static int visible(Level world,NativeLightEntity owner){if(owner.visual().small())return 63;var state=owner.getBlockState();var facing=state.getValue(NativeLightBlock.FACING);var right=right(facing);var up=up(facing);int loaded=0,same=0,small=0,half=0;for(var local:SIDES){Direction side=switch(local){case NORTH->facing;case SOUTH->facing.getOpposite();case EAST->right;case WEST->right.getOpposite();case UP->up;case DOWN->up.getOpposite();};var pos=owner.getBlockPos().relative(side);if(!NativeLightService.loaded(world,pos))continue;int bit=1<<local.get3DDataValue();loaded|=bit;var neighbor=world.getBlockState(pos);if(neighbor.getBlock()==state.getBlock()){same|=bit;if(world.getBlockEntity(pos) instanceof NativeLightEntity tile&&tile.visual().small())small|=bit;if(owner.kind()==NativeLightBlock.Kind.SLAB&&neighbor.getValue(NativeLightBlock.HALF)!=state.getValue(NativeLightBlock.HALF))half|=bit;}}return ProgrammableLightArtwork.visibleFaces(false,loaded,same,small,half);}
 /** Installs every changed atomic publication before its caller notifies observers. */
 static List<NativeLightEntity> prepare(Level world){if(CLOSED.get().contains(world))return List.of();if(world instanceof ServerLevel server&&!server.getServer().isSameThread())throw new IllegalStateException("Light projection off owning server thread");var pending=PENDING.get().remove(world);if(pending==null)return List.of();var seeds=new LinkedHashSet<BlockPos>();for(var pos:pending){seeds.add(pos);for(var side:SIDES)seeds.add(pos.relative(side));}var visited=new HashSet<BlockPos>();var projections=new LinkedHashMap<NativeLightEntity,NativeLightRenderProjection>();for(var seed:seeds){if(visited.contains(seed)||!NativeLightService.loaded(world,seed)||!(world.getBlockEntity(seed) instanceof NativeLightEntity owner)||!installed(world,owner))continue;if(!owner.available()){visited.add(seed);projections.put(owner,NativeLightRenderProjection.SINGLE);continue;}var facing=owner.visual().facing();var graph=new Graph(world,facing);var node=graph.node(seed);var members=ProgrammableLightJoining.members(seed,node,graph,true);visited.addAll(members);var right=right(facing);var up=up(facing);var regions=ProgrammableLightArtwork.regions(members,pos->axis(pos,right),pos->axis(pos,up));for(var pos:members){var member=graph.owners.get(pos);var region=regions.get(pos);projections.put(member,new NativeLightRenderProjection(visible(world,member),region.left(axis(pos,right)),region.top(axis(pos,up)),region.right(axis(pos,right)),region.bottom(axis(pos,up))));}}
  var changed=new ArrayList<NativeLightEntity>();for(var entry:projections.entrySet())if(installed(world,entry.getKey())&&entry.getKey().publishRenderProjection(entry.getValue()))changed.add(entry.getKey());return changed;
 }
 static void notifyPrepared(List<NativeLightEntity> changed,Set<NativeLightEntity> gameplay){for(var owner:changed)if(!gameplay.contains(owner))owner.notifyRenderProjection();}
 public static void flush(Level world){notifyPrepared(prepare(world),Set.of());}
 @SubscribeEvent public static void tick(LevelTickEvent.Post event){if(PENDING.get().containsKey(event.getLevel()))flush(event.getLevel());}
 @SubscribeEvent public static void load(LevelEvent.Load event){if(event.getLevel() instanceof Level world)CLOSED.get().remove(world);}
 @SubscribeEvent public static void unload(LevelEvent.Unload event){if(event.getLevel() instanceof Level world){CLOSED.get().add(world);PENDING.get().remove(world);}}
}
