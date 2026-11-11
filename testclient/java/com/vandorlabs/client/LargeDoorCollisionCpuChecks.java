package com.vandorlabs.client;

import com.vandorlabs.blocks.BlockDetailedDoor;
import com.vandorlabs.blocks.BlockLargeProgrammableDoor;
import com.vandorlabs.blocks.BlockVandorDoor;
import com.vandorlabs.tiles.TileEntityLargeProgrammableDoor;
import com.vandorlabs.tiles.TileEntitySpaceDoor;
import com.vandorlabs.tiles.MovedDoorInteractions;
import net.minecraft.block.state.IBlockState;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Compare the released per-query clipping algorithm with actual block collision dispatch. */
public final class LargeDoorCollisionCpuChecks {
    private static final com.sun.management.ThreadMXBean METER=
            (com.sun.management.ThreadMXBean)ManagementFactory.getThreadMXBean();
    private static volatile Object consumed;
    private static void need(boolean yes,String why){if(!yes)throw new AssertionError(why);}

    // Pinned e8cc78a1 BlockLargeProgrammableDoor.addCollisionBoxToList.
    private static void original(BlockLargeProgrammableDoor block,NonRenderingChecks.MemoryWorld world,
            BlockPos pos,AxisAlignedBB entity,List<AxisAlignedBB> result){
        TileEntityLargeProgrammableDoor root=block.root(world,pos);if(root==null)return;
        EnumFacing width=world.getBlockState(root.getPos()).getValue(BlockVandorDoor.FACING).rotateYCCW();
        int column=(pos.getX()-root.getPos().getX())*width.getFrontOffsetX()
                +(pos.getZ()-root.getPos().getZ())*width.getFrontOffsetZ();
        int row=pos.getY()-root.getPos().getY();
        double lo=column==0?Double.NEGATIVE_INFINITY:0,hi=column==2?Double.POSITIVE_INFINITY:1;
        double minX=Double.NEGATIVE_INFINITY,maxX=Double.POSITIVE_INFINITY;
        double minZ=Double.NEGATIVE_INFINITY,maxZ=Double.POSITIVE_INFINITY;
        if(width.getAxis()==EnumFacing.Axis.X){minX=pos.getX()+(width.getFrontOffsetX()>0?lo:1-hi);maxX=pos.getX()+(width.getFrontOffsetX()>0?hi:1-lo);}
        else{minZ=pos.getZ()+(width.getFrontOffsetZ()>0?lo:1-hi);maxZ=pos.getZ()+(width.getFrontOffsetZ()>0?hi:1-lo);}
        AxisAlignedBB cell=new AxisAlignedBB(minX,row==0?Double.NEGATIVE_INFINITY:pos.getY(),minZ,
                maxX,row==2?Double.POSITIVE_INFINITY:pos.getY()+1,maxZ);
        for(AxisAlignedBB box:block.geometry(world,pos)){
            if(!box.intersects(cell))continue;
            AxisAlignedBB clipped=box.intersect(cell);
            if(clipped.intersects(entity))result.add(clipped);
        }
    }

    private static final class Fixture {
        final NonRenderingChecks.MemoryWorld world=new NonRenderingChecks.MemoryWorld(true);
        final BlockLargeProgrammableDoor block=new BlockLargeProgrammableDoor("large_collision_cpu",
                new BlockDetailedDoor("large_collision_cpu_model",BlockVandorDoor.DoorMotion.SLIDING,
                        true,true,false,1,15,7,90,-90,-16,16));
        final TileEntityLargeProgrammableDoor root=new TileEntityLargeProgrammableDoor();
        final BlockPos[] cells=new BlockPos[9];
        IBlockState state;
        void place(BlockPos anchor,EnumFacing face,boolean open){
            world.clear();state=block.getDefaultState().withProperty(BlockVandorDoor.FACING,face)
                    .withProperty(BlockVandorDoor.OPEN,open);
            for(int col=0;col<3;col++)for(int row=0;row<3;row++){
                BlockPos at=anchor.offset(face.rotateYCCW(),col).up(row);cells[row*3+col]=at;
                TileEntityLargeProgrammableDoor tile=col==0&&row==0?root:new TileEntityLargeProgrammableDoor();
                world.states.put(at,state);world.tiles.put(at,tile);tile.setWorld(world);tile.setPos(at);tile.assign(anchor);
            }
        }
        void configure(boolean sliding,int direction,boolean framed,boolean hinges,int depth,int panels,int security){
            root.configure(23,1,framed,direction,false,sliding,hinges,0,panels!=0);
            root.setPlacementDepth(depth);root.setPanelMode(panels);root.configureSecurity(security,127);
        }
        void current(BlockPos pos,AxisAlignedBB entity,List<AxisAlignedBB> result){
            block.addCollisionBoxToList(state,world,pos,entity,result,null,false);
        }
    }

    private static void matrix(){
        Fixture f=new Fixture();List<AxisAlignedBB> expected=new ArrayList<>(),actual=new ArrayList<>();
        int cases=0,queries=0,maxRetained=0;
        for(BlockPos anchor:new BlockPos[]{new BlockPos(17,80,-20),new BlockPos(-29999983,240,29999983)})
        for(EnumFacing face:EnumFacing.HORIZONTALS)for(boolean sliding:new boolean[]{false,true})
        for(int direction=0;direction<(sliding?4:1);direction++)for(boolean frame:new boolean[]{false,true})
        for(boolean hinges:new boolean[]{false,true})for(int depth=0;depth<3;depth++)for(int panels=0;panels<3;panels++)
        for(int security:new int[]{0,1})for(boolean open:new boolean[]{false,true}){
            f.place(anchor,face,open);f.configure(sliding,direction,frame,hinges,depth,panels,security);
            AxisAlignedBB all=new AxisAlignedBB(anchor.getX()-8,anchor.getY()-8,anchor.getZ()-8,
                    anchor.getX()+9,anchor.getY()+9,anchor.getZ()+9);
            AxisAlignedBB small=new AxisAlignedBB(anchor.getX()+.2,anchor.getY()+.25,anchor.getZ()+.2,
                    anchor.getX()+.8,anchor.getY()+2.05,anchor.getZ()+.8);
            AxisAlignedBB distant=all.offset(100,100,100);
            int retained=0;
            for(int col=0;col<3;col++)for(int row=0;row<3;row++){
                BlockPos cell=f.cells[row*3+col];
                for(AxisAlignedBB query:new AxisAlignedBB[]{all,small,distant}){
                    expected.clear();actual.clear();original(f.block,f.world,cell,query,expected);f.current(cell,query,actual);
                    need(expected.equals(actual),"collision partition changed: case="+cases+" cell="+col+","+row);
                    queries++;
                }
                List<AxisAlignedBB> partition=f.root.collisionCellGeometry(face,open,col,row);
                need(partition==f.root.collisionCellGeometry(face,open,col,row),"warm partition not reused");
                retained+=partition.size();
                try{partition.clear();throw new AssertionError("mutable collision partition escaped");}
                catch(UnsupportedOperationException correct){/* immutable published geometry */}
            }
            maxRetained=Math.max(maxRetained,retained);cases++;
        }
        // The same tile and position can receive a replacement snapshot without setters.
        f.place(new BlockPos(17,80,-20),EnumFacing.SOUTH,false);f.configure(true,0,true,true,0,1,0);
        f.world.stateReads=0;need(f.root.anchorPos().equals(f.root.getPos()),"root changed its own anchor");
        need(f.world.stateReads==0,"zero-offset anchor queried the world");
        List<AxisAlignedBB> before=f.root.collisionCellGeometry(EnumFacing.SOUTH,false,0,0);
        NBTTagCompound saved=f.root.writeToNBT(new NBTTagCompound());saved.setBoolean("SpaceFramed",false);
        saved.setInteger("SpaceSlideDirection",3);f.root.readFromNBT(saved);
        need(before!=f.root.collisionCellGeometry(EnumFacing.SOUTH,false,0,0),"NBT replacement retained stale partitions");
        expected.clear();actual.clear();AxisAlignedBB query=new AxisAlignedBB(9,72,-28,26,89,-11);
        original(f.block,f.world,f.root.getPos(),query,expected);f.current(f.root.getPos(),query,actual);
        need(expected.equals(actual),"NBT replacement changed collision geometry");
        System.out.println("large-door-collision PASS configurations="+cases+" queries="+queries
                +" maximum-retained-partition-boxes="+maxRetained
                +" facings/motions/depths/hinges/panels/security/far-world/NBT/relocation/immutable");
    }

    private static void bounds(){
        TileEntityLargeProgrammableDoor tile=new TileEntityLargeProgrammableDoor();
        for(BlockPos at:new BlockPos[]{new BlockPos(17,80,-20),new BlockPos(-29999983,240,29999983)}){
            tile.setPos(at);AxisAlignedBB expected=new AxisAlignedBB(at.add(-4,-3,-4),at.add(5,7,5));
            need(expected.equals(tile.getRenderBoundingBox()),"large render bounds changed");
            need(tile.getRenderBoundingBox()==tile.getRenderBoundingBox(),"large render bounds not reused");
        }
        long started=METER.getThreadAllocatedBytes(Thread.currentThread().getId());
        for(int i=0;i<65536;i++)consumed=tile.getRenderBoundingBox();
        long bytes=METER.getThreadAllocatedBytes(Thread.currentThread().getId())-started;
        need(bytes<4096,"large render bounds still allocate per culling query");
        System.out.println("large-door-collision bounds PASS warmed-calls=65536 allocated-bytes="+bytes);
    }

    private static void subclasses(){
        final List<AxisAlignedBB> supplied=new ArrayList<>();
        BlockLargeProgrammableDoor block=new BlockLargeProgrammableDoor("large_collision_extension",
                new BlockDetailedDoor("large_collision_extension_model",BlockVandorDoor.DoorMotion.SLIDING,
                        true,true,false,1,15,7,90,-90,-16,16)){
            @Override public List<AxisAlignedBB> geometry(net.minecraft.world.IBlockAccess world,BlockPos pos){return supplied;}
        };
        NonRenderingChecks.MemoryWorld world=new NonRenderingChecks.MemoryWorld(true);
        TileEntityLargeProgrammableDoor root=new TileEntityLargeProgrammableDoor();
        BlockPos anchor=new BlockPos(17,80,-20);IBlockState state=block.getDefaultState().withProperty(BlockVandorDoor.FACING,EnumFacing.SOUTH);
        root.setWorld(world);root.setPos(anchor);root.assign(anchor);world.states.put(anchor,state);world.tiles.put(anchor,root);
        AxisAlignedBB query=new AxisAlignedBB(9,72,-28,26,89,-11);
        List<AxisAlignedBB> expected=new ArrayList<>(),actual=new ArrayList<>();
        for(int changed=0;changed<2;changed++){
            supplied.clear();supplied.add(new AxisAlignedBB(17.1,80.1+changed*.1,-19.9,17.9,80.5+changed*.1,-19.1));
            expected.clear();actual.clear();original(block,world,anchor,query,expected);
            block.addCollisionBoxToList(state,world,anchor,query,actual,null,false);
            need(!expected.isEmpty()&&expected.equals(actual),"block subclass geometry override bypassed or cached");
        }
        TileEntityLargeProgrammableDoor extension=new TileEntityLargeProgrammableDoor(){
            @Override public List<AxisAlignedBB> collisionGeometry(EnumFacing face,boolean open){return supplied;}
        };
        extension.setPos(anchor);
        List<AxisAlignedBB> before=extension.collisionCellGeometry(EnumFacing.SOUTH,false,0,0);
        supplied.clear();supplied.add(new AxisAlignedBB(17.1,80.1,-19.9,17.9,80.2,-19.1));
        need(!before.equals(extension.collisionCellGeometry(EnumFacing.SOUTH,false,0,0)),"mutable tile subclass geometry was cached");
        System.out.println("large-door-collision extensions PASS dynamic block and tile geometry overrides preserved");
    }

    private static final class CountingWorld extends NonRenderingChecks.MemoryWorld {
        int tileReads,loadedQueries;
        CountingWorld(){super(true);}
        @Override public net.minecraft.tileentity.TileEntity getTileEntity(BlockPos pos){tileReads++;return super.getTileEntity(pos);}
        @Override protected boolean isChunkLoaded(int x,int z,boolean empty){loadedQueries++;return super.isChunkLoaded(x,z,empty);}
        void resetCounts(){stateReads=tileReads=loadedQueries=0;}
    }
    private static List<TileEntitySpaceDoor> originalCandidates(CountingWorld world,AxisAlignedBB query){
        List<TileEntitySpaceDoor> result=new ArrayList<>();
        for(TileEntitySpaceDoor door:MovedDoorInteractions.INDEX.query(world,query.grow(5)))
            if(MovedDoorInteractions.isMoved(world,door.getPos())
                    &&(door instanceof TileEntityLargeProgrammableDoor?((TileEntityLargeProgrammableDoor)door).isAnchor()
                    :world.getBlockState(door.getPos()).getValue(BlockVandorDoor.HALF)==net.minecraft.block.BlockDoor.EnumDoorHalf.LOWER))
                result.add(door);
        return result;
    }
    @SuppressWarnings("unchecked") private static void candidates()throws Exception{
        CountingWorld world=new CountingWorld();BlockPos anchor=new BlockPos(15,80,8);
        BlockDetailedDoor model=new BlockDetailedDoor("large_candidates_model",BlockVandorDoor.DoorMotion.SLIDING,
                true,true,false,1,15,7,90,-90,-16,16);
        BlockLargeProgrammableDoor block=new BlockLargeProgrammableDoor("large_candidates",model);
        com.vandorlabs.blocks.BlockConfigurableSpaceDoor ordinary=new com.vandorlabs.blocks.BlockConfigurableSpaceDoor("ordinary_candidates",false,model);
        List<TileEntitySpaceDoor> installed=new ArrayList<>();
        java.lang.reflect.Method method=MovedDoorInteractions.class.getDeclaredMethod("doors",net.minecraft.world.World.class,AxisAlignedBB.class);
        method.setAccessible(true);int cases=0;
        try{
            for(int col=0;col<3;col++)for(int row=0;row<3;row++){
                BlockPos at=anchor.add(col,row,0);TileEntityLargeProgrammableDoor tile=new TileEntityLargeProgrammableDoor();
                tile.setWorld(world);tile.setPos(at);tile.assign(anchor);
                world.states.put(at,block.getDefaultState().withProperty(BlockVandorDoor.FACING,EnumFacing.SOUTH));
                world.tiles.put(at,tile);installed.add(tile);MovedDoorInteractions.INDEX.add(tile);
            }
            for(int half=0;half<2;half++){
                BlockPos at=anchor.add(-2,half,0);TileEntitySpaceDoor tile=new TileEntitySpaceDoor();tile.setWorld(world);tile.setPos(at);
                world.states.put(at,ordinary.getDefaultState().withProperty(BlockVandorDoor.OPEN,true)
                        .withProperty(BlockVandorDoor.HALF,half==0?net.minecraft.block.BlockDoor.EnumDoorHalf.LOWER:net.minecraft.block.BlockDoor.EnumDoorHalf.UPPER));
                world.tiles.put(at,tile);installed.add(tile);MovedDoorInteractions.INDEX.add(tile);
            }
            // A third-party isAnchor implementation may require loaded state.
            TileEntityLargeProgrammableDoor extension=new TileEntityLargeProgrammableDoor(){
                @Override public boolean isAnchor(){need(getWorld().isBlockLoaded(getPos()),"extension role queried before loaded-world validation");return super.isAnchor();}
            };
            BlockPos at=anchor.add(4,0,0);extension.setWorld(world);extension.setPos(at);extension.assign(at);
            world.states.put(at,block.getDefaultState());world.tiles.put(at,extension);installed.add(extension);MovedDoorInteractions.INDEX.add(extension);
            AxisAlignedBB query=new AxisAlignedBB(12,79,7,20,84,10);
            for(int mode=0;mode<4;mode++)for(boolean limited:new boolean[]{false,true}){
                world.chunkLimit=false;
                for(int i=0;i<9;i++){
                    TileEntityLargeProgrammableDoor tile=(TileEntityLargeProgrammableDoor)installed.get(i);
                    if(mode==1)tile.assign(tile.getPos());
                    else if(mode==2){NBTTagCompound tag=new NBTTagCompound();tag.setInteger("x",tile.getPos().getX());tag.setInteger("y",tile.getPos().getY());tag.setInteger("z",tile.getPos().getZ());tag.setBoolean("LargeDoorAssigned",false);tag.setInteger("SpaceDoorSchema",2);tile.readFromNBT(tag);}
                    else tile.assign(anchor);
                }
                world.chunkLimit=limited;world.resetCounts();List<TileEntitySpaceDoor> expected=originalCandidates(world,query);
                int oldStates=world.stateReads,oldTiles=world.tileReads,oldLoaded=world.loadedQueries;
                world.resetCounts();List<TileEntitySpaceDoor> actual=(List<TileEntitySpaceDoor>)method.invoke(null,world,query);
                need(expected.equals(actual),"candidate roots changed after assign/NBT or chunk boundary changes");
                need(actual.contains(installed.get(9))&&!actual.contains(installed.get(10)),"ordinary door half handling changed");
                if(mode==0&&!limited){
                    need(oldStates-world.stateReads==8&&oldTiles-world.tileReads==8,"large followers still incur state/identity reads");
                    need(world.loadedQueries<oldLoaded,"large followers still incur chunk checks");
                    System.out.println("large-door-collision candidates baseline-state-reads="+oldStates+" current-state-reads="+world.stateReads
                            +" baseline-tile-reads="+oldTiles+" current-tile-reads="+world.tileReads
                            +" baseline-loaded-queries="+oldLoaded+" current-loaded-queries="+world.loadedQueries);
                }
                cases++;
            }
        }finally{world.chunkLimit=false;for(TileEntitySpaceDoor tile:installed)MovedDoorInteractions.INDEX.remove(tile);}
        System.out.println("large-door-collision candidates PASS cases="+cases+" current assign/NBT roles, ordinary halves, chunk boundary and extension ordering");
    }

    private static void benchmark(boolean xSplit){
        Fixture f=new Fixture();BlockPos anchor=new BlockPos(17,80,-20);
        f.place(anchor,EnumFacing.SOUTH,true);f.configure(true,xSplit?3:0,true,true,0,1,0);
        AxisAlignedBB query=new AxisAlignedBB(9,72,-28,26,89,-11);List<AxisAlignedBB> result=new ArrayList<>();
        final int calls=2048;long[][] cpu=new long[2][15],alloc=new long[2][15],reads=new long[2][15];
        for(int batch=-40;batch<15;batch++)for(int order=0;order<2;order++){
            int mode=(batch+order)&1;f.world.stateReads=0;
            long memory=METER.getThreadAllocatedBytes(Thread.currentThread().getId()),start=METER.getCurrentThreadCpuTime();
            for(int i=0;i<calls;i++){
                result.clear();BlockPos cell=f.cells[i%9];
                if(mode==0)original(f.block,f.world,cell,query,result);else f.current(cell,query,result);
                consumed=result;
            }
            if(batch>=0){cpu[mode][batch]=METER.getCurrentThreadCpuTime()-start;
                alloc[mode][batch]=METER.getThreadAllocatedBytes(Thread.currentThread().getId())-memory;
                reads[mode][batch]=f.world.stateReads;}
        }
        for(int mode=0;mode<2;mode++){
            if(Boolean.getBoolean("vandorlabs.doorCpuTrace"))System.out.println("large-door-collision samples "+xSplit+" "+mode+" "+Arrays.toString(cpu[mode]));
            Arrays.sort(cpu[mode]);Arrays.sort(alloc[mode]);Arrays.sort(reads[mode]);
            System.out.println("large-door-collision benchmark "+(xSplit?"X-split":"sideways")+" "+(mode==0?"baseline":"current")
                    +" calls="+calls+" median-thread-ns="+cpu[mode][7]+" allocated-bytes="+alloc[mode][7]+" state-reads="+reads[mode][7]);
        }
        need(alloc[1][7]<alloc[0][7],"collision caching did not reduce allocation");
        need(reads[1][7]<reads[0][7],"collision dispatch still repeats root resolution");
    }

    public static void main(String[] args)throws Exception{
        net.minecraft.init.Bootstrap.register();com.vandorlabs.VandorLabs.proxy=new com.vandorlabs.CommonProxy();
        net.minecraftforge.fml.common.registry.GameRegistry.registerTileEntity(TileEntityLargeProgrammableDoor.class,
                new net.minecraft.util.ResourceLocation("vandorlabs","large_collision_cpu"));
        net.minecraftforge.fml.common.registry.GameRegistry.registerTileEntity(com.vandorlabs.tiles.TileEntityTokenSlot.class,
                new net.minecraft.util.ResourceLocation("vandorlabs","large_collision_cpu_reader"));
        METER.setThreadAllocatedMemoryEnabled(true);METER.setThreadCpuTimeEnabled(true);
        matrix();bounds();subclasses();candidates();benchmark(false);benchmark(true);
        System.out.println("large-door-collision PASS geometry/read/allocation checks; CPU microbenchmark, not game FPS");
    }
}
