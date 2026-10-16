package com.vandorlabs.client;

import com.vandorlabs.blocks.BlockCanopy;
import com.vandorlabs.canopy.*;
import com.vandorlabs.tiles.TileEntityCanopy;
import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.*;
import net.minecraft.world.World;
import net.minecraftforge.event.world.GetCollisionBoxesEvent;
import java.io.*;
import java.util.*;
import java.util.concurrent.Future;

/** Exact ordered collision/picking parity using the real loaded-world collision event path. */
final class CanopyCollisionQueryRuntimeChecks {
    private static final BlockPos ROOT=new BlockPos(1740,101,430);
    private static Future<?> pending;private static boolean done;
    private static void require(boolean ok,String text){if(!ok)throw new IllegalStateException("canopy collision query: "+text);}
    static boolean tick(net.minecraft.client.Minecraft mc,File output)throws Exception{
        if(done)return true;
        if(pending==null){pending=mc.getIntegratedServer().addScheduledTask(()->{try{run(mc.getIntegratedServer().getPlayerList().getPlayerByUUID(mc.player.getUniqueID()),output);}catch(Exception e){throw new IllegalStateException(e);}});return false;}
        if(!pending.isDone())return false;pending.get();done=true;return true;
    }
    private static void ordered(List<AxisAlignedBB> expected,List<AxisAlignedBB> actual,String label){require(expected.size()==actual.size(),label+" count "+expected.size()+" != "+actual.size());for(int i=0;i<expected.size();i++)require(expected.get(i)==actual.get(i),label+" identity/order index="+i);}
    private static List<AxisAlignedBB> brute(World world,AxisAlignedBB region){List<AxisAlignedBB> result=new ArrayList<>();for(TileEntityCanopy owner:CanopyInteractions.owners(world,region))for(AxisAlignedBB box:owner.boxes())if(box.intersects(region))result.add(box);return result;}
    private static List<AxisAlignedBB> event(World world,AxisAlignedBB region){List<AxisAlignedBB> result=new ArrayList<>();new CanopyInteractions().collision(new GetCollisionBoxesEvent(world,null,region,result));return result;}
    private static List<AxisAlignedBB> regions(AxisAlignedBB bounds,int count){
        List<AxisAlignedBB> result=new ArrayList<>();Random random=new Random(820146);
        for(int i=0;i<count;i++){double x=bounds.minX+random.nextDouble()*(bounds.maxX-bounds.minX),y=bounds.minY+random.nextDouble()*(bounds.maxY-bounds.minY),z=bounds.minZ+random.nextDouble()*(bounds.maxZ-bounds.minZ);if(i%4==0)x=bounds.maxX+2;result.add(new AxisAlignedBB(x-.035,y-.035,z-.035,x+.035,y+.035,z+.035));}
        result.add(bounds.grow(1));result.add(new AxisAlignedBB(bounds.maxX,bounds.minY,bounds.minZ,bounds.maxX+.1,bounds.maxY,bounds.maxZ));return result;
    }
    private static void picking(TileEntityCanopy owner){
        List<CanopyMesh.Face> faces=owner.faces();int checked=0;
        for(int i=0;i<faces.size();i+=Math.max(1,faces.size()/12)){
            Vec3d center=Vec3d.ZERO;for(Vec3d v:faces.get(i).vertices)center=center.add(v);center=CanopyMesh.rotate(center.scale(1D/faces.get(i).vertices.length),owner.facing()).add(new Vec3d(owner.getPos()));
            for(Vec3d delta:new Vec3d[]{new Vec3d(4,0,0),new Vec3d(0,4,0),new Vec3d(0,0,4)}){
                Vec3d start=center.subtract(delta),end=center.add(delta);RayTraceResult expected=CanopyTraceRuntimeChecks.reference(faces,"posed",owner.width(),0,owner.facing(),owner.getPos(),start,end),actual=owner.trace(start,end);
                require((expected==null)==(actual==null),"ray broadphase preserves hit/miss");if(expected!=null)require(expected.sideHit==actual.sideHit&&expected.hitVec.squareDistanceTo(actual.hitVec)<1e-18,"ray broadphase preserves exact closest surface");checked++;
            }
        }
        require(checked>20,"real mesh ray probes available");
        CanopyTraceRuntimeChecks.allocations(faces,owner.width(),owner.facing(),owner.getPos());
        require(owner.trace(new Vec3d(ROOT.add(30,0,0)),new Vec3d(ROOT.add(31,0,0)))==null,"far parallel ray misses");
    }
    private static void boundaries(){
        AxisAlignedBB duplicate=new AxisAlignedBB(0,0,0,1,1,1);List<AxisAlignedBB> source=Arrays.asList(duplicate,new AxisAlignedBB(-1,-1,-1,0,0,0),duplicate,new AxisAlignedBB(1,1,1,2,2,2),new AxisAlignedBB(0,0,0,12,12,.01));CanopyCollisionIndex index=new CanopyCollisionIndex(source);
        for(AxisAlignedBB query:Arrays.asList(new AxisAlignedBB(-2,-2,-2,3,3,3),new AxisAlignedBB(1,0,0,2,1,1),new AxisAlignedBB(.99,.99,.99,1.01,1.01,1.01),new AxisAlignedBB(4,4,4,5,5,5))){List<AxisAlignedBB> expected=new ArrayList<>(),actual=new ArrayList<>();for(AxisAlignedBB box:source)if(box.intersects(query))expected.add(box);index.addIntersecting(query,actual);ordered(expected,actual,"duplicate/touching query");require(index.intersects(query)==!expected.isEmpty(),"existence query boundary parity");}
    }
    private static void oversizedGrid(){
        List<AxisAlignedBB> source=Arrays.asList(new AxisAlignedBB(-100000000,-100000000,-100000000,100000000,100000000,100000000),new AxisAlignedBB(-.1,-.1,-.1,.1,.1,.1));CanopyCollisionIndex index=new CanopyCollisionIndex(source);List<AxisAlignedBB> expected=new ArrayList<>(),actual=new ArrayList<>();AxisAlignedBB query=new AxisAlignedBB(-.01,-.01,-.01,.01,.01,.01);for(AxisAlignedBB b:source)if(b.intersects(query))expected.add(b);index.addIntersecting(query,actual);ordered(expected,actual,"bounded oversized-grid fallback");
    }
    private static void nonblockingPreparation(TileEntityCanopy owner)throws Exception{
        java.lang.reflect.Field field=CanopyReservations.class.getDeclaredField("WORKER");field.setAccessible(true);
        java.util.concurrent.ThreadPoolExecutor worker=(java.util.concurrent.ThreadPoolExecutor)field.get(null);
        java.util.concurrent.CountDownLatch entered=new java.util.concurrent.CountDownLatch(1),release=new java.util.concurrent.CountDownLatch(1);
        Future<?> blocker=worker.submit(()->{entered.countDown();try{release.await();}catch(InterruptedException e){Thread.currentThread().interrupt();}});
        java.lang.reflect.Method ready=CanopyReservations.class.getDeclaredMethod("readySweep",TileEntityCanopy.class);ready.setAccessible(true);
        try{
            require(entered.await(15,java.util.concurrent.TimeUnit.SECONDS),"worker test started");
            require(ready.invoke(null,owner)==null,"cold preparation returns while worker is occupied");
            long start=System.nanoTime();for(int i=0;i<40;i++)require(ready.invoke(null,owner)==null,"pending preparation never waits for worker");
            require(System.nanoTime()-start<100_000_000L,"pending closure checks stay bounded");
        }finally{release.countDown();}
        blocker.get();CanopyPreparationRuntimeChecks.await(owner);require(ready.invoke(null,owner)!=null,"completed worker result accepted");
        System.out.println("[vandorlabs][reprolab] canopy-closure-worker PASS occupied-worker nonblocking-polls completed-result");
    }
    private static Object collisionIndex(TileEntityCanopy owner)throws Exception{
        java.lang.reflect.Method method=TileEntityCanopy.class.getDeclaredMethod("collisionIndex");method.setAccessible(true);return method.invoke(owner);
    }
    private static void awaitIndex(TileEntityCanopy owner)throws Exception{
        long deadline=System.nanoTime()+15_000_000_000L;
        while(collisionIndex(owner)==null){require(System.nanoTime()<deadline,"collision index worker completed");Thread.sleep(2);}
    }
    private static void nonblockingIndex(TileEntityCanopy owner)throws Exception{
        java.lang.reflect.Field field=TileEntityCanopy.class.getDeclaredField("INDEX_WORKER");field.setAccessible(true);
        java.util.concurrent.ThreadPoolExecutor worker=(java.util.concurrent.ThreadPoolExecutor)field.get(null);
        java.util.concurrent.CountDownLatch entered=new java.util.concurrent.CountDownLatch(1),release=new java.util.concurrent.CountDownLatch(1);
        Future<?> blocker=worker.submit(()->{entered.countDown();try{release.await();}catch(InterruptedException e){Thread.currentThread().interrupt();}});
        try{
            require(entered.await(15,java.util.concurrent.TimeUnit.SECONDS),"index worker test started");
            List<AxisAlignedBB> boxes=owner.boxes();require(collisionIndex(owner)==null,"cold index does not wait for occupied worker");
            for(AxisAlignedBB query:regions(owner.selectionBounds(),8)){
                List<AxisAlignedBB> expected=new ArrayList<>(),actual=new ArrayList<>();for(AxisAlignedBB box:boxes)if(box.intersects(query))expected.add(box);
                owner.addCollisionBoxes(query,actual);ordered(expected,actual,"pending index exact fallback");require(collisionIndex(owner)==null,"fallback leaves occupied worker pending");
            }
        }finally{release.countDown();}
        blocker.get();awaitIndex(owner);
        ordered(brute(owner.getWorld(),owner.selectionBounds()),event(owner.getWorld(),owner.selectionBounds()),"published index exact parity");
        System.out.println("[vandorlabs][reprolab] canopy-collision-index-worker PASS occupied-worker exact-fallback ordered-identity completed-result");
    }
    private static void sweepQueries(TileEntityCanopy owner){
        float progress=owner.progress;owner.progress=1;List<AxisAlignedBB> boxes=CanopyReservations.closingSweep(owner);owner.progress=progress;
        CanopyCollisionIndex index=new CanopyCollisionIndex(boxes);List<AxisAlignedBB> probes=regions(index.bounds(),128);boolean[] expected=new boolean[probes.size()];
        long begin=System.nanoTime();for(int i=0;i<probes.size();i++)for(AxisAlignedBB swept:boxes)if(swept.intersects(probes.get(i).shrink(1e-7))){expected[i]=true;break;}long brute=System.nanoTime()-begin;
        List<AxisAlignedBB> shrunk=new ArrayList<>();for(AxisAlignedBB query:probes)shrunk.add(query.shrink(1e-7));
        for(int warm=0;warm<4;warm++)for(int i=0;i<shrunk.size();i++)require(index.intersects(shrunk.get(i))==expected[i],"closure sweep exact parity including touching margins");
        begin=System.nanoTime();for(int repeat=0;repeat<4;repeat++)for(int i=0;i<shrunk.size();i++)require(index.intersects(shrunk.get(i))==expected[i],"warm closure index parity");long indexed=System.nanoTime()-begin;
        System.out.println("[vandorlabs][reprolab] canopy-closure-index PASS sweep-boxes="+boxes.size()+" queries="+probes.size()+" indexed-us="+indexed/(4D*probes.size()*1000)+" nested-reference-us="+brute/(probes.size()*1000D)+" exact-touching-margins cached-index");
    }
    private static void clear(World world){TileEntityCanopy tile=TileEntityCanopy.at(world,ROOT);if(tile!=null){List<TileEntityCanopy> members=new ArrayList<>(tile.reservationMembers());for(TileEntityCanopy member:members)if(world.getBlockState(member.getPos()).getBlock() instanceof BlockCanopy)world.setBlockToAir(member.getPos());}for(BlockPos pos:BlockPos.getAllInBox(ROOT.add(-10,-4,-10),ROOT.add(10,10,10)))if(world.getBlockState(pos).getBlock() instanceof BlockCanopy)world.setBlockToAir(pos);}
    private static TileEntityCanopy fixture(World world,String kind,int width,EnumFacing facing){
        clear(world);BlockCanopy block=(BlockCanopy)Block.getBlockFromName("vandorlabs:"+kind);
        for(int i=0;i<(block.opening()?width:1);i++){BlockPos pos=ROOT.offset(facing.rotateY(),i);world.setBlockState(pos,block.getDefaultState().withProperty(BlockCanopy.FACING,facing),2);block.onBlockPlacedBy(world,pos,world.getBlockState(pos),null,new ItemStack(block));}
        TileEntityCanopy tile=TileEntityCanopy.at(world,ROOT).owner();require(tile.width()==width,"fixture joined width "+kind+" width="+tile.width());return tile;
    }
    static void run(EntityPlayerMP actor,File output)throws Exception{
        World world=actor.world;for(BlockPos at:BlockPos.getAllInBox(ROOT.add(-12,-4,-12),ROOT.add(12,12,12)))world.getChunkFromBlockCoords(at);boundaries();oversizedGrid();long exactTests=0,rawTests=0;int cases=0,queries=0;
        try(PrintWriter csv=new PrintWriter(new File(output,"canopy-collision-query-benchmark.csv"))){csv.println("kind,width,facing,pose,boxes,queries,index_box_tests,brute_box_tests,raw_build_ms,cold_event_ms,event_us,brute_us");
            for(String kind:new String[]{"canopy","long_canopy","front_canopy_glass","large_front_canopy_glass"})for(EnumFacing facing:EnumFacing.HORIZONTALS){
                int width=kind.equals("large_front_canopy_glass")?5:3;TileEntityCanopy owner=fixture(world,kind,width,facing);owner.large.openable=true;owner.large.motion=1;owner.large.hinge=facing.getHorizontalIndex();owner.large.mount=facing.getHorizontalIndex();owner.large.depth=kind.contains("front")?24:16;owner.progress=owner.previous=.4375F;
                // Includes collision rails extending beyond the visible glass bounds.
                List<AxisAlignedBB> probes=regions(owner.selectionBounds(),48);for(AxisAlignedBB region:probes){ordered(brute(world,region),event(world,region),kind+" "+facing);queries++;}picking(owner);
                NBTTagCompound saved=owner.writeToNBT(new NBTTagCompound());owner.readFromNBT(saved);owner.progress=owner.previous=1;owner.large.mount=(owner.large.mount+1)%4;owner.large.depth=kind.contains("front")?48:16;for(AxisAlignedBB region:regions(owner.selectionBounds(),16)){ordered(brute(world,region),event(world,region),"NBT/pose/settings invalidation "+kind);queries++;}owner.removing=true;require(owner.selectionBounds().equals(new AxisAlignedBB(owner.getPos())),"non-drawing owner discards indexed bounds");owner.removing=false;ordered(brute(world,owner.selectionBounds().grow(1)),event(world,owner.selectionBounds().grow(1)),"owner restoration invalidates empty index");cases++;
            }
            TileEntityCanopy owner=fixture(world,"long_canopy",9,EnumFacing.NORTH);owner.large.motion=1;owner.large.angle=2;nonblockingIndex(owner);nonblockingPreparation(owner);sweepQueries(owner);
            for(int poseIndex=0;poseIndex<6;poseIndex++){
                float pose=(7+poseIndex)/16F;owner.progress=owner.previous=pose;
                long rawStart=System.nanoTime();List<AxisAlignedBB> boxes=owner.boxes();long rawNanos=System.nanoTime()-rawStart;
                long coldStart=System.nanoTime();event(world,boxes.get(boxes.size()/2).grow(.001));long coldNanos=System.nanoTime()-coldStart;
                List<AxisAlignedBB> probes=regions(owner.selectionBounds(),256);
                // Contact-heavy particle probes complement random interior/near/outside probes.
                for(int i=1;i<256;i+=4){AxisAlignedBB contact=boxes.get(i*7919%boxes.size());double x=(contact.minX+contact.maxX)*.5,y=(contact.minY+contact.maxY)*.5,z=(contact.minZ+contact.maxZ)*.5;probes.set(i,new AxisAlignedBB(x-.035,y-.035,z-.035,x+.035,y+.035,z+.035));}
                CanopyCollisionIndex existence=new CanopyCollisionIndex(boxes);awaitIndex(owner);
                for(AxisAlignedBB q:probes){List<AxisAlignedBB> expected=brute(world,q);ordered(expected,event(world,q),"long9 particle contact pose="+pose);require(existence.intersects(q)==!expected.isEmpty(),"long9 existence parity");}
                long start=System.nanoTime();for(int repeat=0;repeat<4;repeat++)for(int i=0;i<256;i++)event(world,probes.get(i));long eventNanos=System.nanoTime()-start;
                start=System.nanoTime();for(int repeat=0;repeat<4;repeat++)for(int i=0;i<256;i++)brute(world,probes.get(i));long bruteNanos=System.nanoTime()-start;
                long checked=0,raw=0;for(int i=0;i<256;i++){List<AxisAlignedBB> matches=new ArrayList<>();checked+=owner.addCollisionBoxes(probes.get(i),matches);raw+=boxes.size();}
                require(checked*4<raw,"particle-sized queries inspect less than one quarter of raw boxes: "+checked+"/"+raw);exactTests+=checked;rawTests+=raw;
                csv.printf(java.util.Locale.ROOT,"long_canopy,9,NORTH,%.4f,%d,%d,%d,%d,%.3f,%.3f,%.3f,%.3f%n",pose,boxes.size(),256,checked,raw,rawNanos/1e6,coldNanos/1e6,eventNanos/(4D*256*1000),bruteNanos/(4D*256*1000));csv.flush();
            }
        }finally{clear(world);}
        System.out.println("[vandorlabs][reprolab] canopy-collision-query PASS cases="+cases+" queries="+queries+" exact-ordered-identity collision-event particles NBT-pose-settings-cardinal-invalidation picking-parity inspected="+exactTests+"/"+rawTests);
    }
    private CanopyCollisionQueryRuntimeChecks(){}
}
