package com.vandorlabs.client;

import com.vandorlabs.blocks.*;
import com.vandorlabs.tiles.*;
import com.vandorlabs.render.ScreenHousingMesh;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import java.lang.management.ManagementFactory;
import java.util.*;

/** Real selector predicates and exact mesh preparation; no graphics context required. */
public final class SelectorCpuChecks {
    private static volatile Object consumed;
    private static final com.sun.management.ThreadMXBean METER=(com.sun.management.ThreadMXBean)ManagementFactory.getThreadMXBean();
    private static void need(boolean value,String reason){if(!value)throw new AssertionError(reason);}
    private static long bytes(){return METER.getThreadAllocatedBytes(Thread.currentThread().getId());}
    /** Frozen released predicate, before either CPU eligibility reorder. */
    private static boolean before(TileEntityAnimatedScreenSelector tile,int pass){
        if(tile.fluids.any() && pass==1)return true;
        if(tile.getWorld()!=null && PanelJoins.flatBar(tile.getWorld().getBlockState(tile.getPos()),tile.getWorld(),tile.getPos())!=null)return pass==0||pass==1;
        Block block=tile.getBlockType();
        if(block instanceof BlockProgrammableDiagonalGlass)return pass==0||pass==1;
        if(tile.getWorld()!=null && tile.getMainTextureLayout()==0 && DiagonalWallState.baked(block,tile.getPos(),tile.getBlockMetadata(),tile.isDiagonalHalfHeight(),tile.isDiagonalFullWidth()))return false;
        if(block!=null && (block.getClass()==BlockProgrammableBlock.class || block instanceof BlockProgrammableStairs || block instanceof BlockProgrammableSlab))return false;
        if(block instanceof BlockProgrammableWall && ((BlockProgrammableWall)block).isPortholeShape())return pass==0||pass==1;
        return pass==0;
    }
    private static boolean fastBefore(TileEntityAnimatedScreenSelector tile){
        if(tile.getWorld()==null)return false;Block block=tile.getWorld().getBlockState(tile.getPos()).getBlock();
        if(block instanceof BlockProgrammableWall){BlockProgrammableWall wall=(BlockProgrammableWall)block;return wall.isDiagonalShape()&&!wall.isPortholeShape()&&!(block instanceof BlockProgrammableDiagonalGlass)&&!tile.fluids.any();}
        return block instanceof BlockProgrammableTrigger || block.getClass()==BlockProgrammableLight.class&&!tile.isSmallInput();
    }
    private static TileEntityAnimatedScreenSelector put(NonRenderingChecks.MemoryWorld world,BlockPos pos,IBlockState state,int profile){
        TileEntityAnimatedScreenSelector tile=new TileEntityAnimatedScreenSelector();NBTTagCompound tag=new NBTTagCompound();
        tag.setBoolean("Waterlogged",(profile&1)!=0);tag.setBoolean("SmallInput",(profile&2)!=0);
        tag.setBoolean("DiagonalFullWidth",(profile&4)!=0);tag.setBoolean("DiagonalHalfHeight",(profile&8)!=0);
        tag.setInteger("MainTextureLayout",(profile>>>4)%3);tile.readFromNBT(tag);tile.setWorld(world);tile.setPos(pos);
        world.states.put(pos,state);world.tiles.put(pos,tile);tile.getBlockType();tile.getBlockMetadata();return tile;
    }
    private static void eligibility(){
        NonRenderingChecks.MemoryWorld world=new NonRenderingChecks.MemoryWorld(true);int cases=0;long oldReads=0,newReads=0;
        for(Block block:ModBlocks.BLOCKS)if(block instanceof BlockAnimatedScreenSelector || block instanceof BlockProgrammableStairs){
            for(IBlockState state:block.getBlockState().getValidStates())for(int profile:new int[]{0,1,2,4,8,12,16,32})for(int edge:new int[]{-1,0,15,16}){
                world.clear();TileEntityAnimatedScreenSelector tile=put(world,new BlockPos(edge,80,edge),state,profile);
                for(int pass=-1;pass<=2;pass++){
                    world.stateReads=0;boolean old=before(tile,pass);oldReads+=world.stateReads;
                    world.stateReads=0;boolean current=tile.shouldRenderInPass(pass);newReads+=world.stateReads;
                    need(old==current,"selector pass changed: "+block.getRegistryName()+" state="+state+" profile="+profile+" pass="+pass);
                    if(!(block instanceof BlockProgrammableWall))need(world.stateReads==0,"screen/input queried world in pass eligibility");cases++;
                }
                world.stateReads=0;boolean fast=fastBefore(tile);int previous=world.stateReads;
                world.stateReads=0;need(fast==tile.hasFastRenderer(),"fast renderer classification changed");
                need(previous==1 && world.stateReads==0,"fast renderer still fetched block state");
            }
        }
        need(cases>1000 && oldReads>newReads,"selector eligibility fixture did not exercise costs");
        System.out.println("selector-cpu eligibility PASS cases="+cases+" before-state-reads="+oldReads+" after-state-reads="+newReads+" fast-renderer reads1->0");
        // A block metadata/identity notification invalidates the vanilla tile cache.
        BlockPos pos=new BlockPos(8,80,8);TileEntityAnimatedScreenSelector tile=put(world,pos,ModBlocks.ANIMATED_SCREEN_SELECTOR.getDefaultState(),0);
        for(Block replacement:new Block[]{ModBlocks.PROGRAMMABLE_DIAGONAL_WALL,ModBlocks.PROGRAMMABLE_TRIGGER_BLOCK,ModBlocks.PROGRAMMABLE_LIGHT,ModBlocks.ANIMATED_SCREEN_SELECTOR}){
            world.states.put(pos,replacement.getDefaultState());tile.updateContainingBlockInfo();
            need(before(tile,0)==tile.shouldRenderInPass(0)&&before(tile,1)==tile.shouldRenderInPass(1),"block replacement kept stale pass classification");
            need(fastBefore(tile)==tile.hasFastRenderer(),"block replacement kept stale fast classification");
        }
    }
    private static void bars(){
        NonRenderingChecks.MemoryWorld world=new NonRenderingChecks.MemoryWorld(true);BlockProgrammableWall wall=(BlockProgrammableWall)ModBlocks.PROGRAMMABLE_WALL;
        int cases=0,bars=0;BlockPos pos=new BlockPos(8,80,8);
        for(EnumFacing facing:EnumFacing.HORIZONTALS)for(int depth=0;depth<3;depth++)for(int front=0;front<7;front++)for(int back=0;back<7;back++)for(int straight=0;straight<4;straight++){
            world.clear();IBlockState state=wall.getDefaultState().withProperty(BlockProgrammableWall.FACING,facing).withProperty(BlockProgrammableWall.DEPTH,depth);
            TileEntityAnimatedScreenSelector tile=put(world,pos,state,0);
            for(int end=0;end<2;end++){int variant=end==0?front:back;if(variant>0)put(world,pos.offset(end==0?facing:facing.getOpposite()),wall.getDefaultState().withProperty(BlockProgrammableWall.FACING,variant<=3?facing.rotateY():facing.rotateYCCW()).withProperty(BlockProgrammableWall.DEPTH,(variant-1)%3),0);}
            for(int side=0;side<2;side++)if((straight&(1<<side))!=0)put(world,pos.offset(side==0?facing.rotateYCCW():facing.rotateY()),state,0);
            if(PanelJoins.flatBar(state,world,pos)!=null)bars++;
            for(int pass=0;pass<2;pass++){
                boolean old=before(tile,pass);world.stateReads=0;need(old==tile.shouldRenderInPass(pass),"flat T-bar pass changed");
                if(pass==0)need(world.stateReads==0,"opaque flat panel unnecessarily discovered neighbours");cases++;
            }
        }
        need(bars>0,"T-bar fixture missed its positive case");
        System.out.println("selector-cpu flat panels PASS cases="+cases+" aligned-bars="+bars+" pass0 no topology scan, pass1 topology preserved");
    }
    private static void same(ScreenHousingMesh source,SelectorHousingMeshes.Mesh current){same(source.quads,current.quads);same(source.triangles,current.triangles);}
    private static void same(ScreenHousingMesh.Face[] a,ScreenHousingMesh.Face[] b){
        need(a.length==b.length,"housing primitive count changed");
        for(int face=0;face<a.length;face++){
            need(a[face].nx==b[face].nx&&a[face].ny==b[face].ny&&a[face].nz==b[face].nz&&a[face].vertices.length==b[face].vertices.length,"housing normals/winding changed");
            for(int vertex=0;vertex<a[face].vertices.length;vertex++){
                ScreenHousingMesh.Vertex x=a[face].vertices[vertex],y=b[face].vertices[vertex];
                need(x.x==y.x&&x.y==y.y&&x.z==y.z&&x.u==y.u&&x.v==y.v,"housing vertex/UV changed");
            }
        }
    }
    private static void meshes(){
        int cases=0;
        for(int layout=0;layout<3;layout++)for(double aspect:new double[]{.5,1,2,4,16}){
            for(int shape=0;shape<32;shape++){
                double x0=(shape&1)*2,y0=(shape&2)*.5,z0=(shape&4)*.5,x1=16-(shape&1),y1=16-(shape&8),z1=16-(shape&16)*.5;
                same(ScreenHousingMesh.box(x0,y0,z0,x1,y1,z1).textureLayout(layout,aspect),SelectorHousingMeshes.box(x0,y0,z0,x1,y1,z1,layout,aspect));cases++;
            }
            for(ScreenHousingMesh base:new ScreenHousingMesh[]{ScreenHousingMesh.cube(),ScreenHousingMesh.console(),ScreenHousingMesh.halfConsole(),ScreenHousingMesh.diagonal(false),ScreenHousingMesh.diagonal(true),ScreenHousingMesh.slab(false,true),ScreenHousingMesh.slab(true,true)}){
                same(base.textureLayout(layout,aspect),SelectorHousingMeshes.layout(base,layout,aspect));cases++;
            }
            for(int visible=0;visible<64;visible++){same(ScreenHousingMesh.cube().boxFaces(visible).textureLayout(layout,aspect),SelectorHousingMeshes.cubeFaces(visible,layout,aspect));cases++;}
        }
        SelectorHousingMeshes.Mesh first=SelectorHousingMeshes.box(0,0,0,16,16,16,1,1);
        need(first==SelectorHousingMeshes.box(-0.0,0,0,16,16,16,1,1),"signed-zero box did not reuse geometry");
        for(int i=0;i<2500;i++)SelectorHousingMeshes.box(i*.001,0,0,16,16,16,i%3,1+(i%7)*.125);
        need(SelectorHousingMeshes.entries()<2500&&SelectorHousingMeshes.retainedBytes()<=SelectorHousingMeshes.MAX_BYTES,"numeric housing cache exceeded byte budget");
        SelectorHousingMeshes.Mesh rebuilt=SelectorHousingMeshes.box(0,0,0,16,16,16,1,1);need(rebuilt!=first,"old mesh was not evicted");
        same(ScreenHousingMesh.box(0,0,0,16,16,16).textureLayout(1,1),rebuilt);
        SelectorHousingMeshes.clear();need(SelectorHousingMeshes.retainedBytes()==0&&SelectorHousingMeshes.entries()==0,"unload/reload retained geometry");
        need(SelectorHousingMeshes.box(0,0,0,16,16,16,1,1)!=rebuilt,"thread-local hit survived clear");
        System.out.println("selector-cpu mesh PASS exact position/UV/normal/winding cases="+cases+" masks/layout/aspect/box/console/diagonal/slab byte-budget and reload");
    }
    private static void cost(){
        final int calls=8192;long[][] cpu=new long[2][11],alloc=new long[2][11];
        for(int batch=-20;batch<11;batch++)for(int order=0;order<2;order++){
            int algorithm=(batch+order)&1;long memory=bytes(),start=METER.getCurrentThreadCpuTime();
            for(int i=0;i<calls;i++){double x=(i&15)*.125;int layout=i%3;double aspect=(i&1)==0?1:2;
                consumed=algorithm==0?ScreenHousingMesh.box(x,0,0,16,16,16).textureLayout(layout,aspect):SelectorHousingMeshes.box(x,0,0,16,16,16,layout,aspect);
            }
            long elapsed=METER.getCurrentThreadCpuTime()-start,allocated=bytes()-memory;
            if(batch>=0){cpu[algorithm][batch]=elapsed;alloc[algorithm][batch]=allocated;}
        }
        for(int algorithm=0;algorithm<2;algorithm++){Arrays.sort(cpu[algorithm]);Arrays.sort(alloc[algorithm]);}
        need(alloc[1][5]==0&&alloc[0][5]>0,"warm numeric housing lookups still allocated");
        System.out.println("selector-cpu housing preparation calls="+calls+" CPU-median-ns="+cpu[0][5]+"->"+cpu[1][5]+" allocated-median-bytes="+alloc[0][5]+"->"+alloc[1][5]);
    }
    public static void main(String[] args)throws Exception{
        net.minecraft.init.Bootstrap.register();com.vandorlabs.VandorLabs.proxy=new com.vandorlabs.CommonProxy();
        METER.setThreadAllocatedMemoryEnabled(true);METER.setThreadCpuTimeEnabled(true);
        if(args.length>0&&args[0].equals("routing")){SelectorTextureRoutingChecks.run();return;}
        eligibility();bars();meshes();cost();SelectorTextureRoutingChecks.run();
        System.out.println("selector-cpu PASS actual eligibility and prepared geometry; CPU measurements do not establish hardware FPS or shader acceptance");
    }
}
