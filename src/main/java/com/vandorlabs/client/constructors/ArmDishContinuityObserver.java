package com.vandorlabs.client.constructors;

import com.google.gson.Gson;
import com.vandorlabs.blocks.FrameMachineDesign;
import com.vandorlabs.gear.client.ArmCoreTransitionObserver;
import com.vandorlabs.tiles.TileEntityConstructor;
import com.vandorlabs.vehicle.VehicleWorld;
import java.io.*;
import java.lang.reflect.Field;
import java.nio.ByteBuffer;
import java.util.*;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.vertex.*;
import net.minecraft.util.EnumFacing;
import net.minecraftforge.fml.relauncher.*;

/** Read-only actual rotor-submission oracle; no authored/frozen cosmetic clock. */
@SideOnly(Side.CLIENT)
public final class ArmDishContinuityObserver implements ArmCoreTransitionObserver.SubmissionListener {
    private final Writer out;private final Field poses;
    private double originalPhase=Double.NaN;private String failure;
    private int nativeDraws,capturedDraws,restoredDraws;private boolean wasCaptured;
    private double lastYaw;
    public ArmDishContinuityObserver(File output)throws Exception{
        output.mkdirs();out=new BufferedWriter(new FileWriter(new File(output,"dish-continuity.jsonl")));
        poses=MachineVisualMotion.class.getDeclaredField("WORLDS");poses.setAccessible(true);
    }
    public void submitted(TileEntityConstructor tile,ByteBuffer actual,VertexFormat format,int count,int frame){
        if(!tile.core().design.equals("standalone_dish"))return;
        try{
            @SuppressWarnings("unchecked") Map<Object,? extends Map<Object,MachineVisualMotion.Pose>> worlds=(Map<Object,? extends Map<Object,MachineVisualMotion.Pose>>)poses.get(null);
            Map<Object,MachineVisualMotion.Pose> world=worlds.get(tile.getWorld());MachineVisualMotion.Pose pose=world==null?null:world.get(tile.getPos());
            if(pose==null||!pose.initialized)throw new IllegalStateException("Actual Dish draw did not sample its cosmetic pose");
            FrameMachineDesign spec=tile.core().specification();String folder=spec.state_models[tile.screenOn()?tile.activity():0].split("/")[0]+"/parts/";
            int start=UniversalConstructorModelCache.get(spec.design,folder+"static.obj").getQuadCount()*4;
            UniversalConstructorModelCache.Mesh rotor=UniversalConstructorModelCache.get(spec.design,folder+"rotor.obj");
            int rotorCount=rotor.getQuadCount()*4;if(start+rotorCount!=count)throw new IllegalStateException("Actual Dish BODY lost static/rotor boundary");
            BufferBuilder reference=new BufferBuilder(4096);reference.begin(7,DefaultVertexFormats.BLOCK);rotor.emitTransformed(reference,0,0,0,tile.facing(),spec.width,spec.depth,0,0,0,0,0,spec.motion_pivot[0],spec.motion_pivot[2]);reference.finishDrawing();
            ByteBuffer zero=reference.getByteBuffer();int offset=position(format),stride=format.getNextOffset(),rs=DefaultVertexFormats.BLOCK.getNextOffset(),ro=position(DefaultVertexFormats.BLOCK);
            int best=0;double longest=0,rx=0,rz=0;
            for(int i=1;i<rotorCount;i++){double x=zero.getFloat(i*rs+ro)-zero.getFloat(ro),z=zero.getFloat(i*rs+ro+8)-zero.getFloat(ro+8),length=x*x+z*z;if(length>longest){longest=length;best=i;rx=x;rz=z;}}
            if(longest<.1)throw new IllegalStateException("Rotor emitted landmark has no horizontal extent");
            int first=start*stride+offset,second=(start+best)*stride+offset;double ax=actual.getFloat(second)-actual.getFloat(first),az=actual.getFloat(second+8)-actual.getFloat(first+8);
            // This fixture keeps NORTH; whole-arm translation is in GL and cannot affect this local vector.
            if(tile.facing()!=EnumFacing.NORTH)throw new IllegalStateException("Dish fixture unexpectedly changed facing");
            double yaw=Math.atan2(rz*ax-rx*az,rx*ax+rz*az);lastYaw=yaw;
            boolean carried=tile.getWorld() instanceof VehicleWorld;
            if(!carried&&!wasCaptured){nativeDraws++;if(Double.isNaN(originalPhase))originalPhase=pose.phase;}else if(carried){capturedDraws++;wasCaptured=true;}else restoredDraws++;
            double expected=Math.toRadians(55)*Math.sin((pose.now-originalPhase)*Math.PI/80)*MachineVisualMotion.eased(pose.progress),error=Math.abs(yaw-expected);
            if(!Double.isNaN(originalPhase)&&error>.002&&failure==null)failure="Actual emitted Dish rotor reset at frame="+frame+" world="+tile.getWorld().getClass().getSimpleName()+" phase="+pose.phase+" originalPhase="+originalPhase+" yaw="+yaw+" continuousYaw="+expected+" error="+error;
            Map<String,Object> row=new LinkedHashMap<>();row.put("frame",frame);row.put("world",tile.getWorld().getClass().getSimpleName());row.put("worldIdentity",System.identityHashCode(tile.getWorld()));row.put("tileIdentity",System.identityHashCode(tile));row.put("pos",tile.getPos().toString());row.put("now",pose.now);row.put("phase",pose.phase);row.put("originalPhase",originalPhase);row.put("progress",pose.progress);row.put("yaw",yaw);row.put("continuousYaw",expected);row.put("error",error);row.put("staticVertices",start);row.put("rotorVertices",rotorCount);out.write(new Gson().toJson(row));out.write('\n');out.flush();
        }catch(ReflectiveOperationException|IOException problem){throw new IllegalStateException(problem);}
    }
    private static int position(VertexFormat format){for(int i=0;i<format.getElementCount();i++)if(format.getElement(i).getUsage()==VertexFormatElement.EnumUsage.POSITION)return format.getOffset(i);throw new IllegalStateException("No submitted position");}
    public boolean warm(){return nativeDraws>=8&&Math.abs(lastYaw)>.25;}
    public int captured(){return capturedDraws;}public int restored(){return restoredDraws;}
    public String failure(){return failure;}
    public void close()throws IOException{out.close();}
}
