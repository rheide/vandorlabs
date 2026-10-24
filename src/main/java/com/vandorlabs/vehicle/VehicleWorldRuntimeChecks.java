package com.vandorlabs.vehicle;

import com.vandorlabs.gear.GearTileEntity;
import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.init.Blocks;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import java.util.*;

/** Shared headless, integrated and physical-server regression; no parent-world mutations. */
public final class VehicleWorldRuntimeChecks {
    private VehicleWorldRuntimeChecks(){}
    public static final class ChunkObserver {
        int events;
        @SubscribeEvent public void attach(AttachCapabilitiesEvent<Chunk> event){
            if(event.getObject() instanceof Chunk && event.getObject().getWorld() instanceof VehicleWorld)events++;
        }
    }
    private static void require(boolean value,String message){if(!value)throw new IllegalStateException(message);}
    public static void run(World parent){
        ChunkObserver observer=new ChunkObserver();boolean live=parent instanceof net.minecraft.world.WorldServer;
        if(live)MinecraftForge.EVENT_BUS.register(observer);
        try{
            BlockPos p=new BlockPos(15,8,15);
            Block neighbor=new Block(Material.ROCK){
                @Override public void onNeighborChange(net.minecraft.world.IBlockAccess world,BlockPos at,BlockPos from){
                    throw new IllegalStateException("Carried tile invoked foreign comparator callback");
                }
            };
            VehicleStructure source=VehicleStructure.display(Arrays.asList(
                    new VehicleStructure.Cell(p,Blocks.STONE.getDefaultState(),null),
                    new VehicleStructure.Cell(p.east(),neighbor.getDefaultState(),null)));
            VehicleWorld view=new VehicleWorld(parent,source);
            require(view.isRemote,"Vehicle view must retain remote semantics");
            require(view.getChunkProvider().getLoadedChunk(0,0)==null,"Loaded lookup allocated a synthetic chunk");
            require(com.vandorlabs.canopy.CanopyInteractions.owners(view,new net.minecraft.util.math.AxisAlignedBB(p)).isEmpty(),"Chunk-only canopy query invented owners");
            require(!view.getCollisionBoxes(null,new net.minecraft.util.math.AxisAlignedBB(p)).isEmpty(),"Snapshot collision was lost during global collision events");
            Set<BlockPos> changed=new LinkedHashSet<>();view.controlUpdates=changed::add;
            GearTileEntity gear=new GearTileEntity();NBTTagCompound data=new NBTTagCompound();
            data.setInteger("x",p.getX());data.setInteger("y",p.getY());data.setInteger("z",p.getZ());
            data.setInteger("GearTrigger",1);data.setBoolean("GearTargetDeployed",true);
            gear.readFromNBT(data);gear.setWorld(view);view.tiles.put(p,gear);
            // This is the exact input -> target -> sync -> markDirty crash path.
            gear.input();require(!gear.target(),"Unpowered retract input was ignored");
            require(changed.contains(p),"Carried dirty state was not queued for persistence");
            NBTTagCompound saved=gear.writeToNBT(new NBTTagCompound());
            GearTileEntity restored=new GearTileEntity();restored.readFromNBT(saved);
            require(!restored.target(),"Retracted target did not survive NBT round trip");
            changed.clear();gear.target(true);require(gear.target()&&changed.contains(p),"Reverse input was not persisted");
            // All carried tile subclasses share this inherited dirty path.
            TileEntity replacement=new TileEntity(){};replacement.setWorld(view);replacement.setPos(p);
            changed.clear();view.tiles.put(p,replacement);gear.markDirty();
            require(changed.isEmpty(),"Replaced tile dirtied the current snapshot");
            replacement.markDirty();require(changed.contains(p),"Generic carried tile dirty state was lost");
            for(EnumFacing face:EnumFacing.VALUES)require(view.isSideSolid(p,face,false),"Snapshot stone face is not solid");
            require(!view.isSideSolid(p.up(),EnumFacing.DOWN,true),"Snapshot air used chunk fallback solidity");
            require(view.isSideSolid(new BlockPos(0,-1,0),EnumFacing.UP,true),"Invalid-position fallback changed");
            require(view.getBlockLightOpacity(p)==Blocks.STONE.getDefaultState().getLightOpacity(view,p),"Snapshot opacity changed");
            boolean loaded=parent.isBlockLoaded(p);
            for(net.minecraft.world.EnumSkyBlock type:net.minecraft.world.EnumSkyBlock.values()){
                int light=view.getLightFromNeighborsFor(type,p);
                require(light>=0&&light<=15,"Invalid sampled light level");
                require(view.getLightFor(type,p)==(loaded?parent.getLightFor(type,p):0),"Light reader bypassed loaded-parent sampling");
                view.setLightFor(type,p,15);
            }
            require(view.getLight(p)==(loaded?parent.getLight(p):0),"Direct light reader changed parent sampling");
            for(boolean neighbors:new boolean[]{false,true})require(view.getLight(p,neighbors)==(loaded?parent.getLight(p,neighbors):0),"Light reader lost neighbor flag");
            require(view.canSeeSky(p)==(loaded&&parent.canSeeSky(p)),"Sky reader changed parent sampling");
            require(observer.events==0,"Read-only vehicle operations created capability-bearing chunks: "+observer.events);
            try{
                java.lang.reflect.Field chunks=VehicleWorld.class.getDeclaredField("chunks");chunks.setAccessible(true);
                require(((Map<?,?>)chunks.get(view)).isEmpty(),"Read-only vehicle operations allocated synthetic chunks");
            }catch(ReflectiveOperationException error){throw new IllegalStateException(error);}
            System.out.println("[vandorlabs] vehicle-world-compatibility PASS input reversal NBT generic-dirty stale-tile comparator isolation solidity opacity zero chunk capability events");
        }finally{if(live)MinecraftForge.EVENT_BUS.unregister(observer);}
    }
}
