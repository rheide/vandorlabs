package com.vandorlabs.neoforge.client;

import com.vandorlabs.neoforge.*;
import com.vandorlabs.ui.ProgrammableBlockDialog;
import com.vandorlabs.ui.ProgrammableDialogLayout;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/** Native emitted geometry, authored UV orientation, immutable snapshots and real settings edits. */
public final class JoinedCubeClientChecks {
    private static int stage,started,matrixIndex,mode;
    private static long stablePublications;
    private static com.vandorlabs.render.HousingFacePlane original;
    private static void require(boolean value,String message){if(!value)throw new IllegalStateException("Modern client: joined cubes "+message);}
    private static ProgrammableBlockEntity tile(Minecraft mc,BlockPos at){return mc.level.getBlockEntity(at) instanceof ProgrammableBlockEntity t?t:null;}
    private static Object field(Object o,String name){try{var f=o.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(o);}catch(ReflectiveOperationException e){throw new IllegalStateException(e);}}
    private static boolean neighborSplit(Minecraft mc){var p=tile(mc,JoinedCubeRuntimeChecks.display(0).east()).joinedFaces().face(Direction.NORTH);return p==null||p.size(0)*p.size(1)*p.size(2)<6;}
    private static boolean six(ProgrammableBlockEntity tile){var p=tile.joinedFaces().face(Direction.NORTH);return p!=null&&p.size(0)*p.size(1)*p.size(2)==6;}
    private static void move(Minecraft mc,double x,double y,double z){mc.player.setPos(x,y,z);mc.player.connection.send(new ServerboundMovePlayerPacket.Pos(x,y,z,true));}
    private static void view(Minecraft mc,BlockPos origin){Vec3 target=new Vec3(origin.getX()+1,origin.getY()+1.5,origin.getZ());Vec3 delta=target.subtract(mc.player.getEyePosition());float yaw=(float)Math.toDegrees(Math.atan2(delta.z,delta.x))-90,pitch=(float)-Math.toDegrees(Math.atan2(delta.y,Math.hypot(delta.x,delta.z)));mc.player.setYRot(yaw);mc.player.setXRot(pitch);mc.player.connection.send(new ServerboundMovePlayerPacket.PosRot(mc.player.getX(),mc.player.getY(),mc.player.getZ(),yaw,pitch,true));}
    private static void select(Minecraft mc,int choice){
        var gui=mc.screen;var panel=(ProgrammableDialogLayout)field(gui,"panel");var state=(ProgrammableBlockDialog.State)field(gui,"state");var picker=(NativeMaterialPicker)field(gui,"picker");picker.setSelected(choice);int row=picker.rows().indexOf(choice)-picker.scroll();require(row>=0&&row<ProgrammableBlockDialog.listRows(panel,state),"actual material row not visible");gui.mouseClicked(panel.listX+12,ProgrammableBlockDialog.listY(panel,state)+row*22+11,0);
    }
    private static void layout(Minecraft mc,int button){var gui=mc.screen;var panel=(ProgrammableDialogLayout)field(gui,"panel");var state=(ProgrammableBlockDialog.State)field(gui,"state");var b=ProgrammableBlockDialog.controls(panel,state).stream().filter(c->c.bounds.id==ProgrammableBlockDialog.TEXTURE_LAYOUT).findFirst().orElseThrow().bounds;gui.mouseClicked(b.x+3,b.y+3,button);}
    private static void matrix(Minecraft mc,Direction face,Direction facing,int layout,BlockPos origin){
        Map<String,float[]> seams=new HashMap<>();double imageArea=0,totalArea=0,minU=2,minV=2,maxU=-1,maxV=-1;
        for(int row=0;row<3;row++)for(int column=0;column<2;column++){
            var at=JoinedCubeRuntimeChecks.cell(origin,face,column,row);var tile=tile(mc,at);require(tile!=null,"matrix fixture not installed");JoinedCubeSnapshots.sample(mc.level,at);
            var plane=tile.joinedFaces().face(face);if(layout!=1)require(plane!=null&&plane.size(0)*plane.size(1)*plane.size(2)==6,"main-thread plane discovery mismatch");
            var state=tile.getBlockState();var model=mc.getBlockRenderer().getBlockModel(state);var quads=model.getQuads(state,face,RandomSource.create(0),tile.getModelData(),null);
            require(!quads.isEmpty(),"native model emitted no face");
            for(var quad:quads){
                int[] data=quad.getVertices();int stride=data.length/4;require(stride>=8,"native vertex payload truncated");
                double ax=Float.intBitsToFloat(data[stride])-Float.intBitsToFloat(data[0]),ay=Float.intBitsToFloat(data[stride+1])-Float.intBitsToFloat(data[1]),az=Float.intBitsToFloat(data[stride+2])-Float.intBitsToFloat(data[2]);
                double bx=Float.intBitsToFloat(data[stride*3])-Float.intBitsToFloat(data[0]),by=Float.intBitsToFloat(data[stride*3+1])-Float.intBitsToFloat(data[1]),bz=Float.intBitsToFloat(data[stride*3+2])-Float.intBitsToFloat(data[2]);
                double area=Math.sqrt(Math.pow(ay*bz-az*by,2)+Math.pow(az*bx-ax*bz,2)+Math.pow(ax*by-ay*bx,2));totalArea+=area;
                if(quad.getSprite().contents().name().toString().equals("minecraft:block/black_concrete"))continue;imageArea+=area;
                for(int vertex=0;vertex<4;vertex++){
                    int off=vertex*stride;float u=quad.getSprite().getUOffset(Float.intBitsToFloat(data[off+4])),v=quad.getSprite().getVOffset(Float.intBitsToFloat(data[off+5]));
                    minU=Math.min(minU,u);minV=Math.min(minV,v);maxU=Math.max(maxU,u);maxV=Math.max(maxV,v);
                    String key=(at.getX()+Float.intBitsToFloat(data[off]))+":"+(at.getY()+Float.intBitsToFloat(data[off+1]))+":"+(at.getZ()+Float.intBitsToFloat(data[off+2]));
                    if(layout!=1){float[] previous=seams.put(key,new float[]{u,v});if(previous!=null)require(Math.abs(previous[0]-u)<.0001&&Math.abs(previous[1]-v)<.0001,"actual UV seam "+face+facing+layout);}
                    orientation(data,off,at,face,facing,layout==1?new com.vandorlabs.render.HousingFacePlane(new int[3],new int[]{1,1,1}):plane,layout==1?0:layout,u,v);
                }
            }
        }
        require(Math.abs(totalArea-6)<.0001,"Fit regions leave a physical hole/overlap");
        require(Math.abs(minU)<.0001&&Math.abs(minV)<.0001&&Math.abs(maxU-1)<.0001&&Math.abs(maxV-1)<.0001,"full artwork not preserved");
        if(layout==2)require(Math.abs(imageArea-4)<.0001,"Fit square artwork does not cover 2x2 plus opaque 2x3 margins");
    }
    private static void orientation(int[] data,int off,BlockPos at,Direction face,Direction facing,com.vandorlabs.render.HousingFacePlane plane,int layout,double u,double v){
        int turns=switch(facing){case EAST,UP,DOWN->3;case SOUTH->2;case WEST->1;default->0;};
        Direction local=face;if(face.getAxis()!=Direction.Axis.Y)for(int i=0;i<turns;i++)local=local.getClockWise();
        // Independent legacy authored U/V vectors, before horizontal rotation.
        int[] ub=switch(local){case DOWN,UP,SOUTH->new int[]{1,0,0};case NORTH->new int[]{-1,0,0};case WEST->new int[]{0,0,1};case EAST->new int[]{0,0,-1};};
        int[] vb=local==Direction.UP?new int[]{0,0,1}:local==Direction.DOWN?new int[]{0,0,-1}:new int[]{0,-1,0};
        for(int[] vector:new int[][]{ub,vb})for(int i=0;i<turns;i++){int x=vector[0];vector[0]=vector[2];vector[2]=-x;}
        int ua=ub[0]!=0?0:ub[1]!=0?1:2,va=vb[0]!=0?0:vb[1]!=0?1:2;
        double eu=(Float.intBitsToFloat(data[off+ua])-plane.min(ua))/plane.size(ua),ev=(Float.intBitsToFloat(data[off+va])-plane.min(va))/plane.size(va);
        if(ub[ua]<0)eu=1-eu;if(vb[va]<0)ev=1-ev;
        if(layout==2){double aspect=plane.size(ua)/(double)plane.size(va);eu=.5+(eu-.5)/Math.min(1,1/aspect);ev=.5+(ev-.5)/Math.min(1,aspect);}
        require(Math.abs(eu-u)<.0002&&Math.abs(ev-v)<.0002,"legacy UV orientation differs "+face+facing+layout+" expected="+eu+","+ev+" actual="+u+","+v);
    }
    private static final class WorkerRegion implements net.minecraft.world.level.BlockAndTintGetter {
        private final net.minecraft.world.level.BlockAndTintGetter region;private final java.util.concurrent.atomic.AtomicInteger reads;
        WorkerRegion(net.minecraft.world.level.BlockAndTintGetter region,java.util.concurrent.atomic.AtomicInteger reads){this.region=region;this.reads=reads;}
        @Override public int getBrightness(net.minecraft.world.level.LightLayer layer,BlockPos pos){reads.incrementAndGet();return region.getBrightness(layer,pos);}
        @Override public net.minecraft.world.level.block.state.BlockState getBlockState(BlockPos pos){return region.getBlockState(pos);}
        @Override public net.minecraft.world.level.material.FluidState getFluidState(BlockPos pos){return region.getFluidState(pos);}
        @Override public net.minecraft.world.level.block.entity.BlockEntity getBlockEntity(BlockPos pos){throw new AssertionError("Worker queried live block entity");}
        @Override public net.minecraft.world.level.lighting.LevelLightEngine getLightEngine(){throw new AssertionError("Worker bypassed captured-region brightness");}
        @Override public int getBlockTint(BlockPos pos,net.minecraft.world.level.ColorResolver color){return region.getBlockTint(pos,color);}
        @Override public float getShade(Direction face,boolean shade){return region.getShade(face,shade);}
        @Override public int getHeight(){return region.getHeight();}
        @Override public int getMinBuildHeight(){return region.getMinBuildHeight();}
    }
    public static boolean tick(Minecraft mc,int tick){
        if(stage==0){mc.player.closeContainer();mc.player.setShiftKeyDown(false);mc.player.connection.send(new ServerboundPlayerCommandPacket(mc.player,ServerboundPlayerCommandPacket.Action.RELEASE_SHIFT_KEY));mc.player.getAbilities().flying=false;mc.player.onUpdateAbilities();mc.options.guiScale().set(2);mc.resizeDisplay();started=tick;stage=-1;}
        require(tick-started<1800,"timed out stage "+stage+" matrix="+matrixIndex+" position="+mc.player.position());
        if(stage==-1){Vec3 target=new Vec3(64.5,80,-3);Vec3 delta=target.subtract(mc.player.position());if(delta.length()>4){if(tick%5==0){var next=mc.player.position().add(delta.normalize().scale(4));move(mc,next.x,next.y,next.z);}return false;}move(mc,target.x,target.y,target.z);stage=1;started=tick;return false;}
        if(stage==1){Vec3 target=new Vec3(80.5,80,40);Vec3 delta=target.subtract(mc.player.position());if(delta.length()>4){if(tick%5==0){var next=mc.player.position().add(delta.normalize().scale(4));move(mc,next.x,next.y,next.z);}return false;}move(mc,target.x,target.y,target.z);stage=2;started=tick;return false;}
        if(stage==2){if(tick-started<30)return false;for(int budget=0;budget<6&&matrixIndex<108;budget++,matrixIndex++){int index=matrixIndex,mode=index%3,dir=index/3%6,face=index/18;BlockPos at=JoinedCubeRuntimeChecks.matrix(index);for(int r=0;r<3;r++)for(int c=0;c<2;c++)if(tile(mc,JoinedCubeRuntimeChecks.cell(at,Direction.from3DDataValue(face),c,r))==null)return false;matrix(mc,Direction.from3DDataValue(face),Direction.from3DDataValue(dir),mode,at);}if(matrixIndex<108)return false;
            var probe=tile(mc,JoinedCubeRuntimeChecks.display(0));var probeState=probe.getBlockState();var probeModel=mc.getBlockRenderer().getBlockModel(probeState);var immutable=probe.getModelData();
            var probePos=probe.getBlockPos();var region=new net.minecraft.client.renderer.chunk.RenderRegionCache().createRegion(mc.level,net.minecraft.core.SectionPos.of(probePos),false);require(region!=null,"captured worker region");
            var reads=new java.util.concurrent.atomic.AtomicInteger();var guarded=new WorkerRegion(region,reads);
            var originalSettings=immutable.get(ProgrammableBlockEntity.SETTINGS);var originalJoined=immutable.get(ProgrammableBlockEntity.JOINED);var originalLight=immutable.get(NativeHousingLighting.LIGHT);
            java.util.concurrent.CompletableFuture.runAsync(()->{var mapped=probeModel.getModelData(guarded,probePos,probeState,immutable);
                require(mapped.get(ProgrammableBlockEntity.SETTINGS)==originalSettings&&mapped.get(ProgrammableBlockEntity.JOINED)==originalJoined,"worker replaced immutable settings/joined data");
                require(immutable.get(NativeHousingLighting.LIGHT)==originalLight,"worker mutated supplied model data");require(mapped.get(NativeHousingLighting.LIGHT)!=null&&reads.get()==12,"worker must derive bounded light from captured region");
                boolean rejected=false;try{JoinedCubeSnapshots.sample(mc.level,probePos);}catch(IllegalStateException expected){rejected=true;}require(rejected,"worker could enter world sampler");}).join();
            var copied=probe.joinedFaces().copy();copied[2]=null;require(probe.joinedFaces().face(Direction.NORTH)!=null,"snapshot exposes mutable array");System.out.println("[vandorlabs][modern] joined-cube-matrix PASS 108 installed six-face/six-facing/layout UV, orientation and Fit-margin cases");stage=21;started=tick;return false;}
        if(stage==21){Vec3 target=new Vec3(96.5,80,40);Vec3 delta=target.subtract(mc.player.position());if(delta.length()>4){if(tick%5==0){var next=mc.player.position().add(delta.normalize().scale(4));move(mc,next.x,next.y,next.z);}return false;}move(mc,target.x,target.y,target.z);stage=22;started=tick;return false;}
        if(stage==22){Vec3 target=new Vec3(96.5,80,3);Vec3 delta=target.subtract(mc.player.position());if(delta.length()>4){if(tick%5==0){var next=mc.player.position().add(delta.normalize().scale(4));move(mc,next.x,next.y,next.z);}return false;}move(mc,target.x,target.y,target.z);stage=3;started=tick;return false;}
        if(stage==3){BlockPos origin=JoinedCubeRuntimeChecks.display(0);var delta=new Vec3(origin.getX()+1,80,3).subtract(mc.player.position());if(delta.length()>4){if(tick%5==0){var next=mc.player.position().add(delta.normalize().scale(4));move(mc,next.x,next.y,next.z);}return false;}move(mc,origin.getX()+1,80,3);mc.player.getAbilities().flying=false;mc.player.onUpdateAbilities();view(mc,origin);stage=4;started=tick;return false;}
        if(stage==4){if(tick-started<15)return false;ModernClientRuntimeChecks.capture("modern-joined-"+mode+".png");stage=5;return false;}
        if(stage==5){if(!ModernClientRuntimeChecks.captured("modern-joined-"+mode+".png"))return false;if(++mode<3){stage=31;started=tick;return false;}stage=6;started=tick;return false;}
        if(stage==31){BlockPos origin=JoinedCubeRuntimeChecks.display(mode);var delta=new Vec3(origin.getX()+1,80,3).subtract(mc.player.position());if(delta.length()>4){if(tick%5==0){var next=mc.player.position().add(delta.normalize().scale(4));move(mc,next.x,next.y,next.z);}return false;}move(mc,origin.getX()+1,80,3);mc.player.getAbilities().flying=false;mc.player.onUpdateAbilities();view(mc,origin);stage=4;started=tick;return false;}
        if(stage==6){var origin=JoinedCubeRuntimeChecks.display(0);var delta=new Vec3(origin.getX()+.5,80,4).subtract(mc.player.position());if(delta.length()>4){if(tick%5==0){var next=mc.player.position().add(delta.normalize().scale(4));move(mc,next.x,next.y,next.z);}return false;}move(mc,origin.getX()+.5,80,4);original=tile(mc,origin).joinedFaces().face(Direction.NORTH);require(original!=null&&original.size(0)*original.size(1)*original.size(2)==6,"visible model snapshot did not settle");mc.player.setShiftKeyDown(true);mc.player.connection.send(new ServerboundPlayerCommandPacket(mc.player,ServerboundPlayerCommandPacket.Action.PRESS_SHIFT_KEY));mc.gameMode.useItemOn(mc.player,InteractionHand.MAIN_HAND,new BlockHitResult(Vec3.atCenterOf(origin).add(0,0,-.5),Direction.NORTH,origin,false));stage=7;started=tick;return false;}
        if(stage==7){if(mc.screen==null)return false;select(mc,0);stage=8;started=tick;return false;}
        if(stage==8){var root=tile(mc,JoinedCubeRuntimeChecks.display(0));if(root.settings().mainTexture!=0||root.joinedFaces().face(Direction.NORTH)!=null||!neighborSplit(mc))return false;require(original.size(0)*original.size(1)*original.size(2)==6,"published immutable plane mutated");select(mc,JoinedCubeRuntimeChecks.material());stage=9;started=tick;return false;}
        if(stage==9){if(!six(tile(mc,JoinedCubeRuntimeChecks.display(0))))return false;layout(mc,0);stage=10;started=tick;return false;}
        if(stage==10){var root=tile(mc,JoinedCubeRuntimeChecks.display(0));if(root.settings().layout!=1||root.joinedFaces().face(Direction.NORTH)!=null||!neighborSplit(mc))return false;layout(mc,1);stage=11;started=tick;return false;}
        if(stage==11){if(!six(tile(mc,JoinedCubeRuntimeChecks.display(0))))return false;mc.player.closeContainer();mc.player.setShiftKeyDown(false);mc.player.connection.send(new ServerboundPlayerCommandPacket(mc.player,ServerboundPlayerCommandPacket.Action.RELEASE_SHIFT_KEY));stablePublications=JoinedCubeSnapshots.publications();stage=12;started=tick;return false;}
        if(stage==12){if(tick-started<40)return false;require(JoinedCubeSnapshots.publications()-stablePublications<100,"snapshot remeshing did not converge");System.out.println("[vandorlabs][modern] joined-cube-client PASS 108 actual six-face/six-facing/layout cases, continuous authored UVs, Fit opaque margins, real menu material/layout splits and immutable convergent snapshots");stage=13;return true;}
        return stage==13;
    }
    private JoinedCubeClientChecks(){}
}
