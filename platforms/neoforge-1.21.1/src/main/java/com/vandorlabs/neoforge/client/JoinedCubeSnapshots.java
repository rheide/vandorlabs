package com.vandorlabs.neoforge.client;

import com.vandorlabs.client.JoinedHousingPolicy;
import com.vandorlabs.neoforge.JoinedCubeFaces;
import com.vandorlabs.neoforge.ProgrammableBlock;
import com.vandorlabs.neoforge.ProgrammableBlockEntity;
import com.vandorlabs.neoforge.ProgrammableModel;
import com.vandorlabs.render.HousingFacePlane;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/** Section workers enqueue positions only; bounded loaded-world sampling runs on the client thread. */
@EventBusSubscriber(modid="vandorlabs",value=Dist.CLIENT)
public final class JoinedCubeSnapshots {
    private record Request(ClientLevel world,BlockPos pos){}
    private static final Map<BlockPos,Request> PENDING=new LinkedHashMap<>();
    private record Signature(int main,int layout,boolean enabled,int f0,int f1,int f2,int f3,int f4,int f5,Direction facing,int visible){}
    private static final Map<ProgrammableBlockEntity,Signature> SEEN=new java.util.WeakHashMap<>();
    private static ClientLevel lastWorld,queueWorld;
    private static final HousingFacePlane UNIT=new HousingFacePlane(new int[3],new int[]{1,1,1});
    private static long published,scans;
    public static synchronized void request(BlockPos pos){
        ClientLevel level=Minecraft.getInstance().level;
        if(level!=queueWorld){PENDING.clear();queueWorld=level;}
        if(level!=null&&PENDING.size()<4096)PENDING.put(pos.immutable(),new Request(level,pos.immutable()));
    }
    public static long publications(){return published;}
    public static long scans(){return scans;}
    private static synchronized Request take(){var it=PENDING.entrySet().iterator();if(!it.hasNext())return null;var result=it.next().getValue();it.remove();return result;}
    @SubscribeEvent public static void tick(ClientTickEvent.Post event){
        var mc=Minecraft.getInstance();if(!mc.isSameThread())throw new IllegalStateException("Joined world sampling outside client thread");
        if(lastWorld!=mc.level){SEEN.clear();lastWorld=mc.level;}
        if(mc.level==null){synchronized(JoinedCubeSnapshots.class){PENDING.clear();queueWorld=null;}return;}
        for(int budget=0;budget<4;budget++){var request=take();if(request==null)return;if(request.world==mc.level)sample(request.world,request.pos);}
    }
    private static int material(ProgrammableBlockEntity tile,Direction face){var s=tile.settings();int local=ProgrammableModel.localFace(face,tile.getBlockState().getValue(ProgrammableBlock.FACING));return s.facesEnabled&&s.face(local)>=0?s.face(local):s.mainTexture;}
    private static JoinedHousingPolicy.Surface surface(ClientLevel world,BlockPos pos,Direction face){
        if(!world.hasChunkAt(pos)||!(world.getBlockEntity(pos) instanceof ProgrammableBlockEntity tile))return null;
        var state=world.getBlockState(pos);if(!state.is(com.vandorlabs.neoforge.VandorLabsModern.PROGRAMMABLE_BLOCK.get()))return null;
        BlockPos neighbor=pos.relative(face);
        boolean exposed=!world.hasChunkAt(neighbor)||!world.getBlockState(neighbor).isSolidRender(world,neighbor)||!world.getBlockState(neighbor).isCollisionShapeFullBlock(world,neighbor);
        return new JoinedHousingPolicy.Surface(true,exposed,state.getValue(ProgrammableBlock.FACING).get3DDataValue(),tile.settings().layout,material(tile,face));
    }
    /** Main-thread entry also used by opt-in real-world renderer contracts. */
    public static void sample(ClientLevel world,BlockPos pos){
        if(!Minecraft.getInstance().isSameThread())throw new IllegalStateException("Joined snapshot world read on worker");
        if(!world.hasChunkAt(pos)||!world.getBlockState(pos).is(com.vandorlabs.neoforge.VandorLabsModern.PROGRAMMABLE_BLOCK.get())||!(world.getBlockEntity(pos) instanceof ProgrammableBlockEntity root))return;
        int visible=0;for(Direction face:Direction.values()){var sampled=surface(world,pos,face);if(sampled!=null&&sampled.exposed)visible|=1<<face.get3DDataValue();}
        var s=root.settings();Signature signature=new Signature(s.mainTexture,s.layout,s.facesEnabled,s.face(0),s.face(1),s.face(2),s.face(3),s.face(4),s.face(5),root.getBlockState().getValue(ProgrammableBlock.FACING),visible);
        Signature before=SEEN.put(root,signature);
        if(before!=null&&!before.equals(signature))Minecraft.getInstance().levelRenderer.setBlocksDirty(pos.getX()-16,pos.getY()-16,pos.getZ()-16,pos.getX()+16,pos.getY()+16,pos.getZ()+16);
        if(SEEN.size()>8192)SEEN.remove(SEEN.keySet().iterator().next());
        Map<BlockPos,HousingFacePlane[]> next=new LinkedHashMap<>();next.put(pos,new HousingFacePlane[6]);
        if(JoinedHousingPolicy.joined(root.settings().layout))for(Direction face:Direction.values()){
            var origin=surface(world,pos,face);if(origin==null||!origin.exposed)continue;scans++;
            var found=JoinedHousingPolicy.discover(new JoinedHousingPolicy.Cell(pos.getX(),pos.getY(),pos.getZ()),face.get3DDataValue(),cell->JoinedHousingPolicy.matches(origin,surface(world,new BlockPos(cell.x,cell.y,cell.z),face)));
            for(var entry:found.planes.entrySet()){
                var cell=entry.getKey();BlockPos at=new BlockPos(cell.x,cell.y,cell.z);
                HousingFacePlane[] planes=next.computeIfAbsent(at,key->((ProgrammableBlockEntity)world.getBlockEntity(key)).joinedFaces().copy());
                var plane=entry.getValue();planes[face.get3DDataValue()]=plane;
            }
        }
        for(var entry:next.entrySet())if(world.hasChunkAt(entry.getKey())&&world.getBlockEntity(entry.getKey()) instanceof ProgrammableBlockEntity tile)
            {var planes=entry.getValue();boolean joined=false;
                for(var plane:planes)if(plane!=null&&plane.size(0)*plane.size(1)*plane.size(2)>1)joined=true;
                if(joined)for(Direction face:Direction.values())if(planes[face.get3DDataValue()]==null){var sampled=surface(world,entry.getKey(),face);if(sampled!=null&&sampled.exposed)planes[face.get3DDataValue()]=UNIT;}
                if(tile.publishJoined(joined?new JoinedCubeFaces(planes):JoinedCubeFaces.NONE))published++;
            }
    }
    private JoinedCubeSnapshots(){}
}
