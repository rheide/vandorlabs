package com.vandorlabs.gear.client;

import com.vandorlabs.gear.*;
import com.vandorlabs.tiles.TileEntityConstructor;
import java.util.*;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;

/** Independent buffers inspect the actual complete arm and Core body emitters. */
@net.minecraftforge.fml.relauncher.SideOnly(net.minecraftforge.fml.relauncher.Side.CLIENT)
public final class ArmCoreGapRenderChecks {
    public static final class Sample {public final double[] core,sockets;public final double phase;Sample(double[] core,double[] sockets,double phase){this.core=core;this.sockets=sockets;this.phase=phase;}}
    private static double[] points(BufferBuilder buffer){int count=buffer.getVertexCount(),stride=DefaultVertexFormats.BLOCK.getNextOffset();double[] out=new double[count*3];java.nio.ByteBuffer bytes=buffer.getByteBuffer();for(int i=0;i<count;i++)for(int a=0;a<3;a++)out[i*3+a]=bytes.getFloat(i*stride+a*4);return out;}
    @SuppressWarnings("unchecked") public static Sample emit(GearTileEntity arm)throws Exception{
        if(arm.payload()==null||arm.payload().materialized()||arm.isLandingAssembly())throw new IllegalStateException("Complete captured bare-arm payload required");
        TileEntityConstructor machine=null;for(net.minecraft.tileentity.TileEntity tile:arm.payload().view(arm).tiles.values())if(tile instanceof TileEntityConstructor){if(machine!=null)throw new IllegalStateException("One saved Core owner required");machine=(TileEntityConstructor)tile;}
        if(machine==null||!machine.formed())throw new IllegalStateException("Saved formed Core required");
        java.lang.reflect.Field body=com.vandorlabs.client.constructors.TEConstructor.class.getDeclaredField("body");body.setAccessible(true);net.minecraftforge.client.model.animation.FastTESR<TileEntityConstructor> fast=(net.minecraftforge.client.model.animation.FastTESR<TileEntityConstructor>)body.get(new com.vandorlabs.client.constructors.TEConstructor());
        BufferBuilder core=new BufferBuilder(65536);core.begin(7,DefaultVertexFormats.BLOCK);fast.renderTileEntityFast(machine,machine.getPos().getX(),machine.getPos().getY(),machine.getPos().getZ(),1,-1,1,core);core.finishDrawing();double[] emittedCore=points(core);
        BufferBuilder complete=new BufferBuilder(65536);complete.begin(7,DefaultVertexFormats.BLOCK);new GearRenderer().renderUncached(arm,0,0,0,1,complete);complete.finishDrawing();double[] emittedArm=points(complete);
        java.lang.reflect.Field meshes=GearModels.class.getDeclaredField("MESHES");meshes.setAccessible(true);Object socket=((Map<?,?>)meshes.get(null)).get("socket");java.lang.reflect.Field quads=socket.getClass().getDeclaredField("quads");quads.setAccessible(true);int vertices=((List<?>)quads.get(socket)).size()*4,columns=arm.columns().size(),per=complete.getVertexCount()/columns;
        if(emittedCore.length<36||complete.getVertexCount()%columns!=0||per<vertices)throw new IllegalStateException("Actual bounded Core/socket records missing");double[] sockets=new double[columns*vertices*3];for(int i=0;i<columns;i++)System.arraycopy(emittedArm,((i+1)*per-vertices)*3,sockets,i*vertices*3,vertices*3);
        return new Sample(emittedCore,sockets,GearRenderer.renderedProgress(arm,1));
    }
    private ArmCoreGapRenderChecks(){}
}
