package com.vandorlabs.client;

import com.vandorlabs.segmented.*;
import com.vandorlabs.tiles.*;
import com.vandorlabs.vehicle.*;
import java.lang.invoke.*;
import java.lang.management.ManagementFactory;
import java.lang.reflect.*;
import java.util.*;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.texture.*;
import net.minecraft.client.renderer.vertex.*;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;

/** Actual ordinary renderer parity and immutable preparation costs; no OpenGL or timing threshold. */
public final class SegmentedColumnCpuChecks {
    private static final BlockPos POS=new BlockPos(15,96,8);
    private static SegmentedDoorBlock BLOCK;
    private static final Object[] RENDERERS=new Object[2];
    private static final MethodHandle[] DRAW=new MethodHandle[2];
    private static int checks;
    private static Object sink;
    private static final class World extends NonRenderingChecks.MemoryWorld {
        int tileReads;
        World(){super(true);}
        @Override public TileEntity getTileEntity(BlockPos pos){tileReads++;return super.getTileEntity(pos);}
        void resetReads(){stateReads=tileReads=0;}
    }
    private static void need(boolean value,String message){checks++;if(!value)throw new AssertionError(message);}
    private static Field field(Class<?> type,String name)throws Exception{Field f=type.getDeclaredField(name);f.setAccessible(true);return f;}
    private static void bootstrap()throws Exception{
        net.minecraft.init.Bootstrap.register();BLOCK=new SegmentedDoorBlock();sun.misc.Unsafe unsafe=(sun.misc.Unsafe)field(sun.misc.Unsafe.class,"theUnsafe").get(null);
        Minecraft mc=(Minecraft)unsafe.allocateInstance(Minecraft.class);
        for(Field f:Minecraft.class.getDeclaredFields()){
            if(Modifier.isStatic(f.getModifiers())&&f.getType()==Minecraft.class){f.setAccessible(true);f.set(null,mc);}
            if(f.getType()==Thread.class){f.setAccessible(true);f.set(mc,Thread.currentThread());}
        }
        TextureAtlasSprite sprite=new TextureAtlasSprite("segmented_cpu_reference"){};sprite.setIconWidth(16);sprite.setIconHeight(16);sprite.initSprite(256,256,32,64,false);
        TextureMap atlas=new TextureMap(""){@Override public TextureAtlasSprite getAtlasSprite(String name){return sprite;}};
        for(Field f:Minecraft.class.getDeclaredFields())if(!Modifier.isStatic(f.getModifiers())&&f.getType()==TextureMap.class){f.setAccessible(true);f.set(mc,atlas);}
        need(mc.isCallingFromMinecraftThread(),"actual client-thread guard");
        RENDERERS[0]=new ReferenceSegmentedDoorRenderer();RENDERERS[1]=new com.vandorlabs.client.segmented.SegmentedDoorRenderer();
        for(int i=0;i<DRAW.length;i++){
            Method method=RENDERERS[i].getClass().getDeclaredMethod("cached",SegmentedDoorTile.class,double.class,double.class,double.class,BufferBuilder.class,double.class,int.class);method.setAccessible(true);
            DRAW[i]=MethodHandles.lookup().unreflect(method).asType(MethodType.methodType(void.class,Object.class,SegmentedDoorTile.class,double.class,double.class,double.class,BufferBuilder.class,double.class,int.class));
        }
    }
    private static SegmentedDoorTile tile(net.minecraft.world.World world,BlockPos pos,int height,int row)throws Exception{
        SegmentedDoorTile tile=new SegmentedDoorTile();tile.setWorld(world);tile.setPos(pos);tile.height=height;tile.topOffset=row;
        field(SegmentedDoorTile.class,"ready").setBoolean(tile,true);return tile;
    }
    private static SegmentedDoorTile[] column(World world,BlockPos pos,int height,EnumFacing facing)throws Exception{
        SegmentedDoorTile[] tiles=new SegmentedDoorTile[height];
        for(int row=0;row<height;row++){
            tiles[row]=tile(world,pos.down(row),height,row);world.tiles.put(tiles[row].getPos(),tiles[row]);
            world.states.put(tiles[row].getPos(),BLOCK.getDefaultState().withProperty(SegmentedDoorBlock.FACING,facing));
        }
        return tiles;
    }
    private static SegmentedDoorSettings settings(int profile){
        SegmentedDoorSettings s=SegmentedDoorSettings.DEFAULT;
        if(profile==1)return new SegmentedDoorSettings(0,false,0,0,false,0,false,s.glass);
        if(profile==2)return new SegmentedDoorSettings(0,false,1,0,true,2,true,s.glass);
        if(profile==3)return new SegmentedDoorSettings(1,false,2,0,false,0,false,s.glass);
        if(profile==4)return new SegmentedDoorSettings(2,true,1,0,false,0,false,s.glass);
        if(profile==5)return s.position(2);
        if(profile==6)return s.tread(8);
        return s;
    }
    private static void configure(SegmentedDoorTile[] tiles,int profile){
        for(SegmentedDoorTile tile:tiles){tile.settings=settings(profile);tile.texture=-1;tile.cellArtwork=SegmentedCellArtwork.INHERITED;tile.columns=3;tile.column=1;}
        if(profile==7)tiles[tiles.length-1].texture=0;
        SegmentedGateContexts.changed(tiles[0]);
    }
    private static void draw(int which,SegmentedDoorTile tile,BufferBuilder buffer,double progress,int pass,VertexFormat format)throws Throwable{
        buffer.begin(7,format);DRAW[which].invokeExact(RENDERERS[which],tile,2.25D,-1.5D,3.75D,buffer,progress,pass);buffer.finishDrawing();
    }
    private static int[] records(BufferBuilder buffer){int[] result=new int[buffer.getVertexCount()*buffer.getVertexFormat().getIntegerSize()];for(int i=0;i<result.length;i++)result[i]=buffer.getByteBuffer().getInt(i*4);return result;}
    private static int geometry()throws Throwable{
        int cases=0;BufferBuilder old=new BufferBuilder(131072),current=new BufferBuilder(131072);
        for(EnumFacing facing:EnumFacing.HORIZONTALS)for(int height:new int[]{1,3}){
            World world=new World();SegmentedDoorTile[] tiles=column(world,POS,height,facing);
            for(int profile=0;profile<8;profile++){
                configure(tiles,profile);SegmentedDoorTile root=tiles[0];SegmentedGateContexts.Prepared prepared=SegmentedGateContexts.prepareRender(root);
                need(prepared!=null&&!prepared.complex,"ordinary fixture");need(prepared.appearance().nativePanel==root.canBake(),"native eligibility parity");
                if(prepared.appearance().nativePanel)need(prepared.appearance().uniformArtwork==root.uniformArtwork(),"uniform artwork parity");
                for(VertexFormat format:new VertexFormat[]{DefaultVertexFormats.BLOCK,BlockSurfaceFormat.get()})for(double progress:new double[]{0,.25,1})for(int pass=0;pass<2;pass++){
                    draw(0,root,old,progress,pass,format);draw(1,root,current,progress,pass,format);
                    need(Arrays.equals(records(old),records(current)),"full emitted records face="+facing+" height="+height+" profile="+profile+" pose="+progress+" pass="+pass+" format="+format);
                    cases++;
                }
            }
            SegmentedGateContexts.unload(new net.minecraftforge.event.world.WorldEvent.Unload(world));
        }
        return cases;
    }
    private static void invalidation()throws Exception{
        World world=new World();SegmentedDoorTile[] rows=column(world,POS,3,EnumFacing.NORTH);SegmentedDoorTile root=rows[0];
        SegmentedGateContexts.Prepared before=SegmentedGateContexts.prepareRender(root);need(before.appearance().nativePanel,"initial native");
        rows[2].settings=settings(2);SegmentedGateContexts.changed(rows[2]);SegmentedGateContexts.Prepared after=SegmentedGateContexts.prepareRender(root);
        need(after!=before&&!after.appearance().nativePanel&&(after.flags&2)!=0,"lower row edit changes eligibility and pass");
        world.tiles.remove(rows[2].getPos());SegmentedGateContexts.changed(rows[2]);after=SegmentedGateContexts.prepareRender(root);
        need(after.appearance().nativePanel,"missing row uses unchanged live-renderer owner fallback");
        world.tiles.put(rows[2].getPos(),rows[2]);SegmentedGateContexts.changed(rows[2]);need(!SegmentedGateContexts.prepareRender(root).appearance().nativePanel,"restored member");
        SegmentedDoorTile replacement=tile(world,POS,3,0);world.tiles.put(POS,replacement);after=SegmentedGateContexts.prepareRender(replacement);
        need(after!=before,"same-position owner replacement");
        world.states.put(POS,BLOCK.getDefaultState().withProperty(SegmentedDoorBlock.FACING,EnumFacing.EAST));SegmentedGateContexts.changed(replacement);
        need(SegmentedGateContexts.prepareRender(replacement).appearance().face==EnumFacing.EAST.getIndex(),"facing notification");
        before=SegmentedGateContexts.prepareRender(replacement);GlassMaterials.changed();need(SegmentedGateContexts.prepareRender(replacement)==before,"unrelated screen/glass signal must not invalidate door appearance");
        before=SegmentedGateContexts.prepareRender(replacement);SegmentedGateContexts.clear();need(SegmentedGateContexts.prepareRender(replacement)!=before,"resource reload");
        SegmentedGateContexts.unload(new net.minecraftforge.event.world.WorldEvent.Unload(world));need(!worldCaches().containsKey(world),"unload releases world");
    }
    private static void joinedWindows()throws Exception{
        World world=new World();SegmentedDoorTile[] roots=new SegmentedDoorTile[4];
        for(int i=0;i<roots.length;i++){roots[i]=column(world,POS.east(i),1,EnumFacing.NORTH)[0];roots[i].column=i;roots[i].columns=4;roots[i].settings=settings(2);}
        SegmentedGateContexts.Prepared before=SegmentedGateContexts.prepareRender(roots[0]);need(before.appearance().window(0).width==4,"joined four-column window");
        roots[3].settings=SegmentedDoorSettings.DEFAULT;SegmentedGateContexts.changed(roots[3]);SegmentedGateContexts.Prepared after=SegmentedGateContexts.prepareRender(roots[0]);
        need(after!=before&&after.appearance().window(0).width==3,"distant member edit changes joined UV extent");
        world.chunkLimit=true;SegmentedGateContexts.changed(roots[1]);after=SegmentedGateContexts.prepareRender(roots[0]);need(after.appearance().window(0).width==1,"unloaded adjacent chunk excluded");
        world.chunkLimit=false;SegmentedGateContexts.changed(roots[1]);need(SegmentedGateContexts.prepareRender(roots[0]).appearance().window(0).width==3,"adjacent chunk load restores group");
    }
    @SuppressWarnings("unchecked") private static Map<net.minecraft.world.World,?> worldCaches()throws Exception{return (Map<net.minecraft.world.World,?>)field(SegmentedGateContexts.class,"WORLDS").get(null);}
    private static void carried()throws Exception{
        World parent=new World();List<VehicleWorld> views=new ArrayList<>();List<SegmentedDoorTile> roots=new ArrayList<>();List<SegmentedGateContexts.Prepared> prepared=new ArrayList<>();
        IBlockState state=BLOCK.getDefaultState();
        for(int i=0;i<8;i++){
            VehicleWorld world=new VehicleWorld(parent,VehicleStructure.display(Arrays.asList(new VehicleStructure.Cell(POS,state,null),new VehicleStructure.Cell(POS.down(),state,null))));views.add(world);
            SegmentedDoorTile root=tile(world,POS,2,0),bottom=tile(world,POS.down(),2,1);world.tiles.put(POS,root);world.tiles.put(POS.down(),bottom);roots.add(root);prepared.add(SegmentedGateContexts.prepareRender(root));
        }
        long builds=SegmentedGateContexts.builds();for(int i=0;i<64;i++)for(int j=0;j<views.size();j++)need(SegmentedGateContexts.prepareRender(roots.get(j))==prepared.get(j),"small carried worlds coexist");need(SegmentedGateContexts.builds()==builds,"small worlds thrashed preparation");
        VehicleWorld world=views.get(0);SegmentedDoorTile replacement=tile(world,POS.down(),2,1);replacement.settings=settings(2);world.tiles.put(POS.down(),replacement);world.tileRevision++;
        need(!SegmentedGateContexts.prepareRender(roots.get(0)).appearance().nativePanel,"carried subordinate tile revision");
        replacement.settings=SegmentedDoorSettings.DEFAULT;world.structure=VehicleStructure.display(Arrays.asList(new VehicleStructure.Cell(POS,state,null),new VehicleStructure.Cell(POS.down(),state,null)));
        need(SegmentedGateContexts.prepareRender(roots.get(0)).appearance().nativePanel,"carried structure replacement");
        beforeOriginMove(views.get(1),roots.get(1),prepared.get(1));
        for(VehicleWorld view:views)SegmentedGateContexts.unload(new net.minecraftforge.event.world.WorldEvent.Unload(view));
    }
    private static void beforeOriginMove(VehicleWorld world,SegmentedDoorTile root,SegmentedGateContexts.Prepared expected){world.origin=world.origin.add(10,0,0);need(SegmentedGateContexts.prepareRender(root)==expected,"moving physical origin preserves local geometry");}
    private static void bounds()throws Exception{
        SegmentedGateContexts.clear();List<World> retained=new ArrayList<>();
        for(int i=0;i<140;i++){World world=new World();retained.add(world);SegmentedDoorTile root=column(world,POS,1,EnumFacing.NORTH)[0];SegmentedGateContexts.prepareRender(root);need(worldCaches().size()<=128&&SegmentedGateContexts.retainedBytes()<=8*1024*1024,"world and byte bounds");}
        SegmentedGateContexts.clear();World world=new World();SegmentedDoorTile first=null;
        for(int i=0;i<1100;i++){SegmentedDoorTile root=column(world,new BlockPos(i*2,96,8),1,EnumFacing.NORTH)[0];if(first==null)first=root;SegmentedGateContexts.prepareRender(root);}
        world.resetReads();SegmentedGateContexts.prepareRender(first);need(world.tileReads>0,"column LRU bound");need(SegmentedGateContexts.retainedBytes()<=8*1024*1024,"single-world retained bound");
    }
    private static void passOnlyDensity()throws Exception{
        SegmentedGateContexts.clear();World world=new World();SegmentedDoorTile[] roots=new SegmentedDoorTile[1024];
        for(int i=0;i<roots.length;i++){roots[i]=column(world,new BlockPos(i*2,96,8),32,EnumFacing.NORTH)[0];roots[i].clientSuppressed=true;SegmentedGateContexts.Prepared prepared=SegmentedGateContexts.prepare(roots[i]);need(prepared.flags==0&&prepared.appearance()==null,"pass-only opaque root must remain compact");}
        long builds=SegmentedGateContexts.builds();world.resetReads();
        for(int round=0;round<4;round++)for(SegmentedDoorTile root:roots){SegmentedGateContexts.Prepared prepared=SegmentedGateContexts.prepare(root);need(prepared.flags==0&&prepared.appearance()==null,"pass-only query materialized ordinary descriptors");}
        need(SegmentedGateContexts.builds()==builds&&world.tileReads==0&&world.stateReads==0,"1024 maximum-height pass-only roots churned their cache");
        SegmentedGateContexts.Prepared compact=SegmentedGateContexts.prepare(roots[0]);need(SegmentedGateContexts.prepareRender(roots[0])==compact&&compact.appearance()!=null,"actual drawing lazily upgrades the same valid entry");
        world.resetReads();need(SegmentedGateContexts.prepareRender(roots[0])==compact&&world.tileReads==0,"lazy upgrade was not retained");
        System.out.println("segmented-pass-only PASS 1024 roots x32 rows, 4096 warm eligibility queries, stateReads=0 tileReads=0 descriptorBuilds=0");
    }
    private static void cost()throws Throwable{
        SegmentedGateContexts.clear();World world=new World();SegmentedDoorTile[] rows=column(world,POS,32,EnumFacing.NORTH);configure(rows,1);SegmentedDoorTile root=rows[0];
        BufferBuilder buffer=new BufferBuilder(131072);VertexFormat format=BlockSurfaceFormat.get();
        com.sun.management.ThreadMXBean meter=(com.sun.management.ThreadMXBean)ManagementFactory.getThreadMXBean();meter.setThreadAllocatedMemoryEnabled(true);long thread=Thread.currentThread().getId();
        for(int warm=0;warm<4000;warm++)for(int mode=0;mode<2;mode++)draw(mode,root,buffer,.25,0,format);
        long[] bytes=new long[2],cpu=new long[2];int[] tileReads=new int[2],stateReads=new int[2];
        for(int mode=0;mode<2;mode++){
            world.resetReads();long allocated=meter.getThreadAllocatedBytes(thread),started=meter.getCurrentThreadCpuTime();
            for(int i=0;i<4096;i++)draw(mode,root,buffer,.25,0,format);
            cpu[mode]=meter.getCurrentThreadCpuTime()-started;bytes[mode]=meter.getThreadAllocatedBytes(thread)-allocated;tileReads[mode]=world.tileReads;stateReads[mode]=world.stateReads;
        }
        need(tileReads[0]>=4096*32&&stateReads[0]>=4096,"baseline exercises full descriptor/member scan");need(tileReads[1]==0&&stateReads[1]==0,"warm ordinary columns query world");need(bytes[1]<bytes[0],"warm prepared renderer allocation reduction");sink=records(buffer);
        System.out.println("segmented-column-cpu 4096 warmed 32-row draws: tileReads="+Arrays.toString(tileReads)+" stateReads="+Arrays.toString(stateReads)+" allocatedBytes="+Arrays.toString(bytes)+" threadCpuNs="+Arrays.toString(cpu));
    }
    public static void main(String[] args)throws Throwable{
        bootstrap();int geometry=geometry();invalidation();joinedWindows();carried();bounds();passOnlyDensity();cost();
        System.out.println("segmented-column-cpu PASS checks="+checks+" fullRecordProfiles="+geometry+" member/facing/missing-row/joined-window/chunk/material/reload/carried revisions and bounded many-world reuse");
    }
    private SegmentedColumnCpuChecks(){}
}
