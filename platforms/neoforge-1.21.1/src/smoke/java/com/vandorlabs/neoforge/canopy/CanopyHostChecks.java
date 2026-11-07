package com.vandorlabs.neoforge.canopy;

import java.util.*;
import net.minecraft.core.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.phys.*;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStartedEvent;

/** Actual private registered hosts; no public IDs, ownership transfers or collision-index activation. */
@EventBusSubscriber(modid="vandorlabs")
public final class CanopyHostChecks {
    private static final String[] KINDS={"angled_front_1x1","angled_front_1x2","canopy","long_canopy","regular_canopy_glass","front_canopy_glass","large_front_canopy_glass","space_visor_2x1","space_visor_3x1","space_visor_5x1","space_visor_5x1_double_height"};
    private static final Map<String,NativeCanopyBlock> BLOCKS=new LinkedHashMap<>();private static BlockEntityType<NativeCanopyEntity> TYPE;
    private static final BlockPos BASE=new BlockPos(8,240,8),SAVED=new BlockPos(8,240,24);private static int checks;
    private static String phase(){return System.getProperty("vandorlabs.transmitterPhase","");}
    private static boolean enabled(){return phase().startsWith("canopy-host-");}
    private static void need(boolean yes,String text){checks++;if(!yes)throw new AssertionError("Canopy host "+text);}
    @EventBusSubscriber(modid="vandorlabs",bus=EventBusSubscriber.Bus.MOD)
    public static final class Registration {
        @SubscribeEvent public static void register(net.neoforged.neoforge.registries.RegisterEvent event){if(!enabled())return;
            event.register(Registries.BLOCK,h->{for(String kind:KINDS){var block=new NativeCanopyBlock(kind,()->TYPE,BlockBehaviour.Properties.of().strength(1.5F,1.5F).sound(SoundType.GLASS));BLOCKS.put(kind,block);h.register(ResourceLocation.parse("vandorlabs:canopy_host_"+kind),block);}});
            event.register(Registries.BLOCK_ENTITY_TYPE,h->{TYPE=BlockEntityType.Builder.of((pos,state)->new NativeCanopyEntity(TYPE,pos,state,((NativeCanopyBlock)state.getBlock()).kind()),BLOCKS.values().toArray(Block[]::new)).build(null);h.register(ResourceLocation.parse("vandorlabs:canopy_host_fixture"),TYPE);});
        }
    }
    private static NativeCanopyEntity put(ServerLevel world,BlockPos pos,String kind,Direction facing,CompoundTag tag){world.getChunkAt(pos);world.setBlock(pos,BLOCKS.get(kind).defaultBlockState().setValue(NativeCanopyBlock.FACING,facing),18);var host=(NativeCanopyEntity)world.getBlockEntity(pos);host.loadWithComponents(tag,world.registryAccess());return host;}
    private static volatile Object sink;
    private static void queries(ServerLevel world,NativeCanopyEntity host){
        var state=host.getBlockState();var pos=host.getBlockPos();var geometry=host.publication().geometry();var output=new ArrayList<AABB>(geometry.boxes().size());var query=geometry.bounds();
        var bean=(com.sun.management.ThreadMXBean)java.lang.management.ManagementFactory.getThreadMXBean();bean.setThreadAllocatedMemoryEnabled(true);long thread=Thread.currentThread().threadId();
        for(int i=0;i<3000;i++){sink=state.getShape(world,pos);sink=state.getCollisionShape(world,pos);output.clear();geometry.appendIntersecting(query,output);}
        long before=bean.getThreadAllocatedBytes(thread);for(int i=0;i<1000;i++){sink=state.getShape(world,pos);sink=state.getCollisionShape(world,pos);output.clear();geometry.appendIntersecting(query,output);}long bytes=bean.getThreadAllocatedBytes(thread)-before;
        need(bytes==0,"actual host warmed outline/local-empty-collision/prepared-external queries allocated="+bytes);need(output.size()==geometry.boxes().size(),"external box references");need(state.getShape(world,pos)==geometry.outline(),"outline identity");need(state.getShape(world,pos).toAabbs().size()==1,"dense collision does not become hover shape");
        var immutable=new net.minecraft.world.level.BlockGetter(){public BlockEntity getBlockEntity(BlockPos p){if(!p.equals(pos))throw new AssertionError("Worker canopy discovery");return host;}public net.minecraft.world.level.block.state.BlockState getBlockState(BlockPos p){throw new AssertionError("Worker state discovery");}public net.minecraft.world.level.material.FluidState getFluidState(BlockPos p){throw new AssertionError("Worker fluid discovery");}public int getHeight(){return 384;}public int getMinBuildHeight(){return -64;}};
        var from=new Vec3(pos.getX()+.5,pos.getY()+.5,pos.getZ()-2);var to=from.add(0,0,5);var expected=host.publication().geometry().trace(new com.vandorlabs.canopy.CanopyPoint(from.x,from.y,from.z),new com.vandorlabs.canopy.CanopyPoint(to.x,to.y,to.z));
        var hit=java.util.concurrent.CompletableFuture.supplyAsync(()->immutable.clipWithInteractionOverride(from,to,pos,geometry.outline(),state)).join();need(expected==null?hit==null:hit!=null&&hit.getLocation().equals(new Vec3(expected.x,expected.y,expected.z)),"actual registered authored mixin worker clip without discovery");
        for(int i=0;i<2000;i++)sink=immutable.clipWithInteractionOverride(from,to,pos,geometry.outline(),state);before=bean.getThreadAllocatedBytes(thread);for(int i=0;i<1000;i++)sink=immutable.clipWithInteractionOverride(from,to,pos,geometry.outline(),state);long clipBytes=bean.getThreadAllocatedBytes(thread)-before;
        System.out.println("[vandorlabs][canopy-host] actual queries=1000 outlineCollisionAllocated="+bytes+" clipAllocated="+clipBytes+" boxes="+output.size());
    }
    private static void write(ServerLevel world){
        for(String kind:KINDS)for(Direction facing:Direction.Plane.HORIZONTAL){var host=put(world,BASE,kind,facing,new CompoundTag());need(!host.inert()&&!host.publication().resolved()&&host.publication().geometry()==null,"decode is local, no dense prep "+kind);NativeCanopyLifecycle.prepare(host);need(host.publication().resolved()&&host.publication().geometry()!=null,"deferred installed preparation "+kind);need(host.settings().data().depth==16&&!host.links().centered(),"old absence defaults "+kind);need(host.saveWithoutMetadata(world.registryAccess()).getInt("CanopyWidth")==NativeCanopyModels.builtin().dimensions(kind).width(),"source dimensions "+kind);}
        var host=put(world,BASE,"angled_front_1x1",Direction.NORTH,new CompoundTag());NativeCanopyLifecycle.prepare(host);queries(world,host);
        var tag=new CompoundTag();tag.putInt("CanopyShade",2);tag.putBoolean("CenteredPlacement",true);tag.putString("ForeignCanopyData","opaque-kept");host=put(world,SAVED,"canopy",Direction.WEST,tag);need(!host.inert(),"valid saved owner");host.setChanged();
        var invalid=new CompoundTag();invalid.putString("NeoForgeData","invalid-envelope");invalid.putIntArray("opaque",new int[]{7,3,9});host=put(world,BASE,"canopy",Direction.NORTH,invalid);need(host.inert(),"invalid platform owner inert");var fallback=BLOCKS.get("canopy").traceAuthored(host.getBlockState(),world,BASE,new Vec3(8.5,240.5,6),new Vec3(8.5,240.5,10));need(fallback!=null&&fallback.getBlockPos().equals(BASE),"inert ownership remains selectable");need(host.saveWithoutMetadata(world.registryAccess()).equals(invalid),"exact failed snapshot retained");
        var edited=new CompoundTag();edited.putInt("CanopyShade",1);host.loadWithComponents(edited,world.registryAccess());need(!host.inert(),"valid owner can replace failed decode");need(!host.saveWithoutMetadata(world.registryAccess()).contains("opaque"),"successful edits retire stale failure bytes");
        var cold=new BlockPos(7200,240,7200);int loaded=world.getChunkSource().getLoadedChunksCount();need(NativeCanopyLifecycle.at(world,cold)==null&&world.getChunkSource().getLoadedChunksCount()==loaded,"loaded-only cold lookup");
    }
    private static void read(ServerLevel world){world.getChunkAt(SAVED);var host=(NativeCanopyEntity)world.getBlockEntity(SAVED);need(host!=null&&!host.inert()&&host.kind().equals("canopy"),"separate-process factory and owner");need(host.settings().data().shade==2&&host.links().centered()&&host.facing()==Direction.WEST,"saved absence-sensitive placement and facing");need(host.saveWithoutMetadata(world.registryAccess()).getString("ForeignCanopyData").equals("opaque-kept"),"opaque valid extra retained");NativeCanopyLifecycle.prepare(host);need(host.publication().resolved()&&host.publication().geometry()!=null,"restart preparation");}
    @SubscribeEvent public static void started(ServerStartedEvent event){if(!enabled())return;try{var world=event.getServer().overworld();if(phase().endsWith("write"))write(world);else read(world);System.out.println("[vandorlabs][canopy-host] "+(phase().endsWith("write")?"write":"read")+" PASS checks="+checks);}catch(Throwable failure){failure.printStackTrace();System.out.println("[vandorlabs][canopy-host] FAIL "+failure);}finally{event.getServer().halt(false);}}
}
