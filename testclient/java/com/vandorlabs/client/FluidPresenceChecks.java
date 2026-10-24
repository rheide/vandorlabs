package com.vandorlabs.client;

import com.vandorlabs.blocks.*;
import net.minecraft.block.*;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.event.world.*;
import net.minecraftforge.fluids.*;
import java.util.*;

/** Legacy parity plus counted world reads; no GPU or FPS claims. */
final class FluidPresenceChecks {
    private static int checks;
    private static void require(boolean ok,String why){checks++;if(!ok)throw new AssertionError("fluid presence: "+why);}
    private static void put(NonRenderingChecks.MemoryWorld world,BlockPos pos,IBlockState state){IBlockState before=world.getBlockState(pos);world.setBlockState(pos,state,2);world.notifyBlockUpdate(pos,before,state,3);}
    static void run(){
        NonRenderingChecks.MemoryWorld world=new NonRenderingChecks.MemoryWorld(true);BlockPos pos=new BlockPos(8,80,8);
        BlockDetailedDoor paired=new BlockDetailedDoor("fluid_presence_model",BlockVandorDoor.DoorMotion.SLIDING,true,true,false,1,15,7,90,-90,-16,16);
        BlockConfigurableSpaceDoor door=new BlockConfigurableSpaceDoor("fluid_presence_door",false,paired);
        BlockProgrammableTrapdoor trapdoor=new BlockProgrammableTrapdoor();BlockProgrammableDiagonalTrapdoor diagonal=new BlockProgrammableDiagonalTrapdoor();BlockLargeProgrammableDoor large=new BlockLargeProgrammableDoor("fluid_presence_large",paired);
        int cases=0;
        for(Block block:new Block[]{door,large,trapdoor,diagonal})for(EnumFacing facing:EnumFacing.HORIZONTALS)for(boolean open:new boolean[]{false,true})for(int half=0;half<2;half++)for(int shape=0;shape<(block==diagonal?3:1);shape++){
            boolean isDoor=block instanceof BlockConfigurableSpaceDoor;
            IBlockState state=block.getDefaultState().withProperty(isDoor?BlockVandorDoor.FACING:BlockProgrammableTrapdoor.FACING,facing).withProperty(isDoor?BlockVandorDoor.OPEN:BlockProgrammableTrapdoor.OPEN,open);
            if(isDoor)state=state.withProperty(BlockVandorDoor.HALF,half==0?BlockDoor.EnumDoorHalf.LOWER:BlockDoor.EnumDoorHalf.UPPER);
            else state=state.withProperty(BlockProgrammableTrapdoor.HALF,half==0?BlockTrapDoor.DoorHalf.BOTTOM:BlockTrapDoor.DoorHalf.TOP);
            put(world,pos,state);if(isDoor)put(world,pos.down(),door.getDefaultState().withProperty(BlockVandorDoor.FACING,facing).withProperty(BlockVandorDoor.OPEN,open));
            TileEntity tile=world.getTileEntity(pos);
            if(block==diagonal){((com.vandorlabs.tiles.TileEntityProgrammableTrapdoor)tile).configure(0,shape,false,0,0);put(world,pos,state);}
            ContainedFluids stored=WaterloggedParts.data(tile);
            for(int mask=0;mask<64;mask++){
                for(EnumFacing face:EnumFacing.values()){
                    // Retain the root below upper doors: a door cell cannot also be native water.
                    IBlockState neighbor=isDoor&&half==1&&face==EnumFacing.DOWN?world.getBlockState(pos.down()):(mask&(1<<face.getIndex()))==0?Blocks.AIR.getDefaultState():Blocks.WATER.getDefaultState();
                    put(world,pos.offset(face),neighbor);
                }
                for(int fluid=0;fluid<4;fluid++){
                    stored.set(0,fluid==1?new FluidStack(FluidRegistry.WATER,1000):fluid==3?new FluidStack(FluidRegistry.LAVA,1000):null);stored.set(1,fluid==2?new FluidStack(FluidRegistry.WATER,1000):null);
                    boolean expected=WaterloggedParts.wet(world,pos);require(RenderFluidPresence.wet(tile)==expected,"legacy matrix "+block.getRegistryName()+facing+open+half+mask+fluid);require(RenderFluidPresence.wet(tile)==expected,"cached matrix");cases++;
                }
            }
        }
        RenderFluidPresence.unloaded(new WorldEvent.Unload(world));
        boundary(door);simulation(door);counts(door);capacity(door);
        System.out.println("Fluid render presence PASS cases="+cases+" assertions="+checks+" legacy parity regular/large doors trapdoors three diagonal profiles all facings halves open/closed 64 adjacency masks water/lava mutation chunk boundaries bounded cache unload");
    }
    private static void boundary(Block door){
        NonRenderingChecks.MemoryWorld world=new NonRenderingChecks.MemoryWorld(true);world.chunkLimit=true;BlockPos pos=new BlockPos(15,80,8);put(world,pos,door.getDefaultState().withProperty(BlockVandorDoor.FACING,EnumFacing.EAST));TileEntity tile=world.getTileEntity(pos);
        world.states.put(pos.east(),Blocks.WATER.getDefaultState());require(!RenderFluidPresence.wet(tile),"unloaded water is never queried");
        world.chunkLimit=false;net.minecraft.world.chunk.Chunk chunk=world.getChunkFromChunkCoords(1,0);RenderFluidPresence.chunkLoaded(new ChunkEvent.Load(chunk));require(RenderFluidPresence.wet(tile),"loading adjacent chunk invalidates dry mask");
        world.chunkLimit=true;RenderFluidPresence.chunkUnloaded(new ChunkEvent.Unload(chunk));require(!RenderFluidPresence.wet(tile),"unloading adjacent chunk invalidates wet mask");RenderFluidPresence.unloaded(new WorldEvent.Unload(world));
    }
    private static void simulation(Block door){
        NonRenderingChecks.MemoryWorld server=new NonRenderingChecks.MemoryWorld(false);BlockPos pos=new BlockPos(8,80,8);put(server,pos,door.getDefaultState());TileEntity tile=server.getTileEntity(pos);require(!RenderFluidPresence.wet(tile),"server dry fallback");server.states.put(pos.north(),Blocks.WATER.getDefaultState());require(RenderFluidPresence.wet(tile),"server reads remain live without cache callbacks");
        BlockPos local=new BlockPos(1,0,1);com.vandorlabs.vehicle.VehicleStructure.Cell cell=new com.vandorlabs.vehicle.VehicleStructure.Cell(local,door.getDefaultState(),null);
        com.vandorlabs.vehicle.VehicleWorld view=new com.vandorlabs.vehicle.VehicleWorld(server,com.vandorlabs.vehicle.VehicleStructure.display(Arrays.asList(cell,new com.vandorlabs.vehicle.VehicleStructure.Cell(local.north(),Blocks.WATER.getDefaultState(),null))));
        com.vandorlabs.tiles.TileEntitySpaceDoor carried=new com.vandorlabs.tiles.TileEntitySpaceDoor();carried.setWorld(view);carried.setPos(local);view.tiles.put(local,carried);
        require(RenderFluidPresence.wet(carried),"carried view reads native water");view.structure=com.vandorlabs.vehicle.VehicleStructure.display(Collections.singletonList(cell));require(!RenderFluidPresence.wet(carried),"carried snapshot replacement bypasses cache");
    }
    private static void counts(Block door){
        NonRenderingChecks.MemoryWorld world=new NonRenderingChecks.MemoryWorld(true);BlockPos pos=new BlockPos(8,80,8);
        Block wall=new BlockProgrammableWall("fluid_presence_wall",BlockProgrammableWall.Shape.PLAIN);Block diagonal=new BlockProgrammableWall("fluid_presence_diagonal",BlockProgrammableWall.Shape.DIAGONAL);
        for(int scenario=0;scenario<6;scenario++){
            put(world,pos,(scenario<2?door:scenario<4?wall:diagonal).getDefaultState());put(world,pos.north(),scenario==1?Blocks.WATER.getDefaultState():Blocks.AIR.getDefaultState());TileEntity tile=world.getTileEntity(pos);ContainedFluids stored=WaterloggedParts.data(tile);stored.set(0,scenario>=3&&scenario%2==1?new FluidStack(FluidRegistry.WATER,1000):null);
            for(int i=0;i<10000;i++){WaterloggedParts.wet(world,pos);RenderFluidPresence.wet(tile);}
            int[] reads=new int[2];long[][] nanos=new long[2][5];
            for(int sample=0;sample<5;sample++)for(int mode=0;mode<2;mode++){
                world.stateReads=0;long start=System.nanoTime();boolean answer=false;for(int i=0;i<5000;i++)answer=mode==0?WaterloggedParts.wet(world,pos):RenderFluidPresence.wet(tile);nanos[mode][sample]=System.nanoTime()-start;reads[mode]=world.stateReads;require(answer==WaterloggedParts.wet(world,pos),"benchmark answers");
            }
            require(reads[1]<reads[0],"reduce repeated world reads");Arrays.sort(nanos[0]);Arrays.sort(nanos[1]);
            System.out.println("Fluid render presence counts "+new String[]{"dry-door","adjacent-water-door","dry-wall","stored-water-wall","dry-diagonal-wall","stored-water-diagonal-wall"}[scenario]+" queries=5000 reads="+reads[0]+" -> "+reads[1]+" median-ns="+nanos[0][2]+" -> "+nanos[1][2]);
        }
        RenderFluidPresence.unloaded(new WorldEvent.Unload(world));
    }
    private static void capacity(Block door){
        NonRenderingChecks.MemoryWorld world=new NonRenderingChecks.MemoryWorld(true);
        for(int i=0;i<2100;i++){BlockPos at=new BlockPos(i*3,80,8);world.setBlockState(at,door.getDefaultState(),2);RenderFluidPresence.wet(world.getTileEntity(at));}
        try{
            java.lang.reflect.Field field=RenderFluidPresence.class.getDeclaredField("WORLDS");field.setAccessible(true);Map<?,?> worlds=(Map<?,?>)field.get(null);Object cache=worlds.get(world);java.lang.reflect.Field masks=cache.getClass().getDeclaredField("masks");masks.setAccessible(true);require(((Map<?,?>)masks.get(cache)).size()==2048,"bounded LRU");RenderFluidPresence.unloaded(new WorldEvent.Unload(world));require(!worlds.containsKey(world),"unload releases cache");
        }catch(ReflectiveOperationException error){throw new AssertionError(error);}
    }
}
