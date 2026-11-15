package com.vandorlabs.gear;

import java.util.*;
import java.util.function.Function;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.model.*;
import net.minecraft.client.renderer.vertex.*;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.*;
import net.minecraftforge.fml.relauncher.*;

/** Compares the actual emitted texture landmarks across one real endpoint handoff. */
@SideOnly(Side.CLIENT)
final class CouplerFrameClientChecks {
    private static Map<String,Integer> expected;
    private static BlockPos destination;
    private static String before;
    private CouplerFrameClientChecks(){}
    static boolean capture(Minecraft mc,BlockPos source,BlockPos rootPos){
        if(!(mc.world.getTileEntity(source) instanceof com.vandorlabs.coupler.TileCoupler)||!(mc.world.getTileEntity(rootPos) instanceof GearTileEntity))return false;
        GearTileEntity arm=(GearTileEntity)mc.world.getTileEntity(rootPos);
        if(arm.progress()!=0||arm.assembly()==null)return false;
        ArmPayload.Endpoint first=ArmPayload.endpoint(arm,0),last=ArmPayload.endpoint(arm,1);if(first==null||last==null)throw new IllegalStateException("Coupler landmark endpoint unavailable");
        destination=last.placed(first.construction(source));before=mc.world.getBlockState(source).toString();
        Vec3d[] from=axes(first),to=axes(last);Vec3d center=new Vec3d(source).addVector(.5,.5,.5),target=new Vec3d(destination).addVector(.5,.5,.5);
        expected=vertices(mc,source,point->{Vec3d d=point.subtract(center);return target.add(to[0].scale(d.dotProduct(from[0]))).add(to[1].scale(d.dotProduct(from[1]))).add(to[2].scale(d.dotProduct(from[2])));});
        if(expected.size()<24)throw new IllegalStateException("Empty Coupler texture landmark control");
        System.out.println("[vandorlabs][reprolab] coupler-frame before source="+source+" destination="+destination+" state="+before+" vertices="+expected.values().stream().mapToInt(Integer::intValue).sum());
        return true;
    }
    static boolean verify(Minecraft mc,BlockPos rootPos){
        if(expected==null||!(mc.world.getTileEntity(rootPos) instanceof GearTileEntity)||!(mc.world.getTileEntity(destination) instanceof com.vandorlabs.coupler.TileCoupler))return false;
        GearTileEntity arm=(GearTileEntity)mc.world.getTileEntity(rootPos);
        if(arm.progress()!=1||arm.payload()==null||!arm.payload().materialized())return false;
        Map<String,Integer> actual=vertices(mc,destination,Function.identity());
        if(!expected.equals(actual)){
            Map<String,Integer> missing=new TreeMap<>(expected);for(Map.Entry<String,Integer> row:actual.entrySet())missing.computeIfPresent(row.getKey(),(key,count)->count.equals(row.getValue())?null:count-row.getValue());
            throw new IllegalStateException("Coupler rigid texture landmarks changed: before="+before+" after="+mc.world.getBlockState(destination)+" expected="+expected.size()+" actual="+actual.size()+" missing="+(missing.isEmpty()?"none":missing.entrySet().iterator().next()));
        }
        int bakes=com.vandorlabs.coupler.client.CouplerFrameModels.bakeCount();long bytes=com.vandorlabs.coupler.client.CouplerFrameModels.vertexBytes();
        if(bakes!=80||bytes<=0||bytes>4L*1024*1024)throw new IllegalStateException("Coupler frame cache is not bounded: bakes="+bakes+" bytes="+bytes);
        IBlockState state=mc.world.getBlockState(destination).getActualState(mc.world,destination);IBakedModel model=mc.getBlockRendererDispatcher().getModelForState(state);state=state.getBlock().getExtendedState(state,mc.world,destination);List<BakedQuad> reused=model.getQuads(state,null,0);
        for(int i=0;i<1000;i++)if(model.getQuads(state,null,0)!=reused)throw new IllegalStateException("Coupler frame quads rebuilt during repeated draws");
        if(com.vandorlabs.coupler.client.CouplerFrameModels.bakeCount()!=bakes)throw new IllegalStateException("Coupler frame rebaked during draw");
        System.out.println("[vandorlabs][reprolab] utility26-focused PASS coupler-frame actual-journaled-native-endpoint rigid-vertex/texture/UV landmarks="+expected.size()+" cachedModels="+bakes+" vertexBytes="+bytes+" drawReuses=1000");return true;
    }
    private static Map<String,Integer> vertices(Minecraft mc,BlockPos pos,Function<Vec3d,Vec3d> transform){
        IBlockState state=mc.world.getBlockState(pos).getActualState(mc.world,pos);IBakedModel model=mc.getBlockRendererDispatcher().getModelForState(state);state=state.getBlock().getExtendedState(state,mc.world,pos);
        List<BakedQuad> quads=new ArrayList<>(model.getQuads(state,null,0));for(EnumFacing side:EnumFacing.values())quads.addAll(model.getQuads(state,side,0));Map<String,Integer> result=new TreeMap<>();
        for(BakedQuad quad:quads){VertexFormat format=quad.getFormat();int position=-1,uv=-1;for(int i=0;i<format.getElementCount();i++){VertexFormatElement element=format.getElement(i);if(element.getUsage()==VertexFormatElement.EnumUsage.POSITION)position=format.getOffset(i)/4;if(element.getUsage()==VertexFormatElement.EnumUsage.UV&&element.getIndex()==0)uv=format.getOffset(i)/4;}
            if(position<0||uv<0)throw new IllegalStateException("Coupler quad format lacks position/UV");int[] data=quad.getVertexData();int stride=format.getIntegerSize();
            for(int v=0;v<4;v++){int at=v*stride;Vec3d p=transform.apply(new Vec3d(pos.getX()+Float.intBitsToFloat(data[at+position]),pos.getY()+Float.intBitsToFloat(data[at+position+1]),pos.getZ()+Float.intBitsToFloat(data[at+position+2])));String key=quantize(p.x)+","+quantize(p.y)+","+quantize(p.z)+"/"+quad.getSprite().getIconName()+"/"+quantize(quad.getSprite().getUnInterpolatedU(Float.intBitsToFloat(data[at+uv])))+","+quantize(quad.getSprite().getUnInterpolatedV(Float.intBitsToFloat(data[at+uv+1])));result.merge(key,1,Integer::sum);}
        }return result;
    }
    private static Vec3d[] axes(ArmPayload.Endpoint endpoint){Vec3d[] result=new Vec3d[3];EnumFacing[] unit={EnumFacing.EAST,EnumFacing.UP,EnumFacing.SOUTH};for(int i=0;i<3;i++){EnumFacing side=endpoint.mappedFacing(unit[i]);result[i]=new Vec3d(side.getFrontOffsetX(),side.getFrontOffsetY(),side.getFrontOffsetZ());}return result;}
    private static long quantize(double value){return Math.round(value*100000);}
}
