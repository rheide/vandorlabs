package com.vandorlabs.client;

import com.vandorlabs.blocks.*;
import com.vandorlabs.tiles.*;
import com.vandorlabs.vehicle.*;
import net.minecraft.block.Block;
import net.minecraft.block.BlockDoor;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.*;
import java.util.*;

/** Postmeasurement checks exercise the cache against fresh live-world collision collection. */
final class VehicleCollisionCacheRuntimeChecks {
    private static int checks;
    private static void require(boolean ok,String message){if(!ok)throw new IllegalStateException("vehicle collision cache: "+message);checks++;}
    private static void equal(VehicleCollision actual,VehicleCollision expected,String context){
        require(actual.boxes.equals(expected.boxes),context+" exact ordered boxes "+actual.boxes.size()+"/"+expected.boxes.size());
        require(actual.bounds.equals(expected.bounds),context+" local bounds");
        for(float yaw:new float[]{0,23,90,179.5F})require(actual.bounds(yaw).equals(expected.bounds(yaw)),context+" oriented bounds yaw="+yaw);
    }
    private static VehicleCollision check(VehicleCollisionCache cache,VehicleWorld view,String context){VehicleCollision result=cache.collision(view);equal(result,new VehicleCollision(view),context);require(cache.entries()<=VehicleCollisionCache.MAX_ENTRIES&&cache.boxes()<=VehicleCollisionCache.MAX_BOXES,context+" bounded entries and coordinate weight");return result;}
    private static VehicleStructure replace(VehicleStructure source,BlockPos pos,IBlockState state,NBTTagCompound data){List<VehicleStructure.Cell> cells=new ArrayList<>();boolean added=false;for(VehicleStructure.Cell c:source.cells){if(c.pos.equals(pos)){cells.add(new VehicleStructure.Cell(pos,state,data));added=true;}else cells.add(c);}if(!added)cells.add(new VehicleStructure.Cell(pos,state,data));return new VehicleStructure(cells,source.seat,source.facing,source.gearCount);}
    private static VehicleStructure small(IBlockState extra,BlockPos pos,NBTTagCompound data){return new VehicleStructure(Arrays.asList(new VehicleStructure.Cell(BlockPos.ORIGIN,ModBlocks.PILOT_SEAT.getDefaultState(),null),new VehicleStructure.Cell(pos,extra,data)),BlockPos.ORIGIN,EnumFacing.NORTH,1);}
    private static List<TileEntityCanopy> owners(VehicleWorld view){List<TileEntityCanopy> result=new ArrayList<>();for(TileEntity raw:view.tiles.values())if(raw instanceof TileEntityCanopy){TileEntityCanopy tile=(TileEntityCanopy)raw;if(tile.draws()&&tile.opening())result.add(tile);}return result;}
    private static void pose(VehicleWorld view,float value){for(TileEntityCanopy tile:owners(view)){tile.previous=tile.progress;tile.progress=value;tile.targetOpen=value>=tile.previous;}}
    private static void invalidation(VehicleCollisionCache cache,VehicleWorld view,Runnable change,String context){long misses=cache.misses();change.run();check(cache,view,context);require(cache.misses()>misses,context+" rejects prior geometry key");}
    private static void awaitWork(VehicleCollisionCache cache,VehicleWorld view)throws InterruptedException{long deadline=System.nanoTime()+5_000_000_000L;while(cache.warming()&&System.nanoTime()<deadline){Thread.sleep(1);cache.tick(view);}require(!cache.warming(),"bounded worker completes within five seconds");}
    static void run(EntityGroundVehicle saved)throws Exception{
        checks=0;List<VehicleStructure.Cell> safe=new ArrayList<>();for(VehicleStructure.Cell c:saved.structure.cells){net.minecraft.util.ResourceLocation id=c.state.getBlock().getRegistryName();if(id!=null&&(id.getResourceDomain().equals("minecraft")||id.getResourceDomain().equals("vandorlabs")))safe.add(c);}
        VehicleStructure snapshot=new VehicleStructure(safe,saved.structure.seat,saved.structure.facing,saved.structure.gearCount);
        VehicleWorld view=new VehicleWorld(saved.world,snapshot);VehicleCollisionCache cache=new VehicleCollisionCache();cache.reset(view,new VehicleCollision(view));
        require(!owners(view).isEmpty(),"actual saved craft supplies an opening canopy");
        VehicleWorld prewarmed=new VehicleWorld(saved.world,snapshot);VehicleCollisionCache prepared=new VehicleCollisionCache();prepared.reset(prewarmed,new VehicleCollision(prewarmed));require(owners(prewarmed).size()==1,"actual craft has one independently opening owner for all-pose prewarm");awaitWork(prepared,prewarmed);require(prepared.entries()>=17,"background preparation publishes all seventeen actual canopy poses");long preparedMisses=prepared.misses(),preparedHits=prepared.hits();
        for(int pass=0;pass<2;pass++)for(int i=0;i<=16;i++){int step=pass==0?i:16-i;pose(prewarmed,step/16F);check(prepared,prewarmed,"background-prepared "+(pass==0?"opening":"reversal")+" pose="+step);}require(prepared.misses()==preparedMisses&&prepared.hits()>=preparedHits+34,"published worker poses hit without any synchronous geometry miss across Open normalization and reversal");
        VehicleCollision first=check(cache,view,"actual saved baseline");long hits=cache.hits();VehicleCollision second=check(cache,view,"identical cache hit");require(cache.hits()>hits,"unchanged actual geometry hits cache");require(first!=second,"hit returns independent mutable physics wrapper");
        first.pose(57);require(second.bounds(0).equals(new VehicleCollision(view).bounds(0)),"wrapper pose state is isolated");
        for(int i=0;i<=16;i++){pose(view,i/16F);check(cache,view,"opening pose="+i);}
        hits=cache.hits();for(int i=16;i>=0;i--){pose(view,i/16F);check(cache,view,"reversal pose="+i);}require(cache.hits()>hits,"reversal reuses exact previously collected poses");
        long boundedMisses=cache.misses();for(int i=0;i<VehicleCollisionCache.MAX_ENTRIES+3;i++){pose(view,(i+.25F)/(VehicleCollisionCache.MAX_ENTRIES+3));check(cache,view,"bounded distinct pose="+i);}require(cache.misses()>boundedMisses&&cache.entries()<=VehicleCollisionCache.MAX_ENTRIES,"more distinct poses than entry limit are bounded");
        TileEntityCanopy canopy=owners(view).get(0);invalidation(cache,view,()->canopy.large.angle=(canopy.large.angle+1)%4,"same-state canopy geometry NBT");
        invalidation(cache,view,()->canopy.centeredPlacement=!canopy.centeredPlacement,"canopy placement geometry");
        invalidation(cache,view,()->canopy.anchor=new BlockPos(63,63,63),"canopy ownership");
        // A new install, including local-coordinate rebase, starts a distinct cache generation.
        VehicleWorld installed=new VehicleWorld(saved.world,snapshot);long generation=cache.generation();cache.reset(installed,new VehicleCollision(installed));require(cache.generation()>generation,"install resets geometry generation");check(cache,installed,"reinstalled actual craft");
        VehicleWorld isolated=new VehicleWorld(saved.world,snapshot);VehicleCollisionCache other=new VehicleCollisionCache();other.reset(isolated,new VehicleCollision(isolated));pose(installed,.25F);check(cache,installed,"first craft independent pose");pose(isolated,.75F);check(other,isolated,"second craft independent pose");check(cache,installed,"second craft cannot poison first");
        net.minecraft.world.WorldServer otherWorld=saved.world.getMinecraftServer().getWorld(-1);require(otherWorld!=null&&otherWorld!=saved.world,"second actual Minecraft dimension exists");VehicleWorld dimension=new VehicleWorld(otherWorld,snapshot);pose(dimension,.625F);VehicleCollisionCache dimensionCache=new VehicleCollisionCache();dimensionCache.reset(dimension,new VehicleCollision(dimension));check(dimensionCache,dimension,"independent actual dimension");check(cache,installed,"different parent world cannot poison first");
        // Real gear geometry depends on live extension and NBT size, without a blockstate change.
        VehicleWorld gearView=new VehicleWorld(saved.world,snapshot);VehicleCollisionCache gearCache=new VehicleCollisionCache();gearCache.reset(gearView,new VehicleCollision(gearView));TileEntityLandingGear gear=null;for(TileEntity tile:gearView.tiles.values())if(tile instanceof TileEntityLandingGear&&((TileEntityLandingGear)tile).isRoot()){gear=(TileEntityLandingGear)tile;break;}require(gear!=null,"actual saved gear root present");final TileEntityLandingGear gearRoot=gear;
        invalidation(gearCache,gearView,()->gearRoot.progress+=.5F,"live gear extension");
        invalidation(gearCache,gearView,()->{NBTTagCompound data=gearRoot.writeToNBT(new NBTTagCompound());data.setInteger("GearSize",(gearRoot.getSize()+1)%5);gearRoot.readFromNBT(data);},"same-state gear size NBT");
        VehicleStructure.Cell seat=snapshot.at(snapshot.seat);VehicleWorld seatView=new VehicleWorld(saved.world,snapshot);VehicleCollisionCache seatCache=new VehicleCollisionCache();seatCache.reset(seatView,new VehicleCollision(seatView));invalidation(seatCache,seatView,()->{NBTTagCompound data=seat.tileData();require(data!=null,"actual pilot seat has NBT");data.setInteger("PilotHeight",(data.getInteger("PilotHeight")+1)%4);seatView.update(replace(snapshot,seat.pos,seat.state,data));},"pilot height settings");
        BlockPos p=new BlockPos(2,0,0);VehicleWorld door=new VehicleWorld(saved.world,small(Blocks.IRON_DOOR.getDefaultState().withProperty(BlockDoor.OPEN,false),p,null));VehicleCollisionCache doorCache=new VehicleCollisionCache();doorCache.reset(door,new VehicleCollision(door));invalidation(doorCache,door,()->door.update(replace(door.structure,p,door.getBlockState(p).withProperty(BlockDoor.OPEN,true),null)),"door state install");
        // Frozen ramp geometry is keyed by its source/tread journal, and install resets it.
        TileEntityControlledRamp sourceRamp=new TileEntityControlledRamp();sourceRamp.setPos(p);sourceRamp.sourceY=0;sourceRamp.row=1;sourceRamp.length=3;sourceRamp.treadPixels=4;sourceRamp.setHalfOffsets(0,0);sourceRamp.origins.add(p);sourceRamp.vehicleFrozen=true;
        VehicleWorld ramp=new VehicleWorld(saved.world,small(ModBlocks.CONTROLLED_RAMP.getDefaultState(),p,sourceRamp.writeToNBT(new NBTTagCompound())));TileEntityControlledRamp rampTile=(TileEntityControlledRamp)ramp.getTileEntity(p);rampTile.move(true,1,ramp.getTotalWorldTime(),60,false);VehicleCollisionCache rampCache=new VehicleCollisionCache();rampCache.reset(ramp,new VehicleCollision(ramp));
        invalidation(rampCache,ramp,()->rampTile.setHalfOffsets(0,-2),"frozen ramp tread/source geometry NBT");
        VehicleStructure rampNext=replace(ramp.structure,p,Blocks.STONE_SLAB.getDefaultState(),null);ramp.update(rampNext);rampCache.reset(ramp,new VehicleCollision(ramp));check(rampCache,ramp,"ramp endpoint replacement install");
        // Unknown adapters can read arbitrary mutable state; they must always use fresh collection.
        final double[] height={.25};Block foreign=new Block(Material.ROCK){@Override public AxisAlignedBB getCollisionBoundingBox(IBlockState state,net.minecraft.world.IBlockAccess world,BlockPos at){return new AxisAlignedBB(0,0,0,1,height[0],1);}};
        VehicleWorld foreignRampView=new VehicleWorld(saved.world,small(ModBlocks.CONTROLLED_RAMP.getDefaultState(),p,sourceRamp.writeToNBT(new NBTTagCompound())));TileEntityControlledRamp foreignRamp=(TileEntityControlledRamp)foreignRampView.getTileEntity(p);foreignRamp.move(true,1,foreignRampView.getTotalWorldTime(),60,false);foreignRamp.source=foreign.getDefaultState();VehicleCollisionCache foreignRampCache=new VehicleCollisionCache();foreignRampCache.reset(foreignRampView,new VehicleCollision(foreignRampView));require(foreignRampCache.entries()==0,"frozen ramp foreign source is rejected before NBT serialization");long foreignRampHits=foreignRampCache.hits(),foreignRampMisses=foreignRampCache.misses();check(foreignRampCache,foreignRampView,"foreign source frozen ramp initial");height[0]=.625;check(foreignRampCache,foreignRampView,"foreign source frozen ramp changed mutable adapter");require(foreignRampCache.entries()==0&&foreignRampCache.hits()==foreignRampHits&&foreignRampCache.misses()>=foreignRampMisses+2,"foreign ramp source never reuses cached geometry");height[0]=.25;
        VehicleWorld dynamic=new VehicleWorld(saved.world,small(foreign.getDefaultState(),p,null));VehicleCollisionCache fallback=new VehicleCollisionCache();fallback.reset(dynamic,new VehicleCollision(dynamic));VehicleCollision old=check(fallback,dynamic,"foreign adapter initial");long oldHits=fallback.hits();height[0]=.875;VehicleCollision changed=check(fallback,dynamic,"foreign mutable collision");require(!old.boxes.equals(changed.boxes)&&fallback.hits()==oldHits,"foreign changing geometry bypasses cache");
        // Start real queued pure-coordinate work, then invalidate before it can publish.
        VehicleWorld queued=new VehicleWorld(saved.world,snapshot);VehicleCollisionCache async=new VehicleCollisionCache();async.reset(queued,new VehicleCollision(queued));async.tick(queued);require(async.warming(),"actual canopy prewarm has queued work before invalidation");long prior=async.generation();async.clear();require(async.generation()>prior,"clear invalidates outstanding generation");VehicleWorld replacement=new VehicleWorld(saved.world,small(Blocks.STONE.getDefaultState(),p,null));async.reset(replacement,new VehicleCollision(replacement));Thread.sleep(25);async.tick(replacement);awaitWork(async,replacement);check(async,replacement,"stale async completion cannot replace new install");
        cache.clear();prepared.clear();other.clear();dimensionCache.clear();gearCache.clear();seatCache.clear();doorCache.clear();rampCache.clear();foreignRampCache.clear();fallback.clear();async.clear();
        System.out.println("[vandorlabs][reprolab] vehicle-collision-cache PASS checks="+checks+" background-published-17-poses zero-miss-opening-and-reversal exact-fresh-parity independent-wrappers reversal ownership config-NBT gear seat-height door-state ramp-journal-and-install foreign-dynamic-and-frozen-ramp-source-fallback install-generation stale-worker-isolation bounded-entries-and-weight actual-dimension-isolation");
    }
}
