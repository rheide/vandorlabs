package com.vandorlabs.gear;

import com.vandorlabs.gear.core.*;
import com.vandorlabs.vehicle.*;
import java.lang.reflect.*;
import java.util.*;
import net.minecraft.init.Blocks;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.*;

/** Independent eight-corner comparison against the actual released payload transform. */
public final class ArmGridMappingChecks {
    private static long checks;private static int profiles,aligned,intermediate,fractional,extenderIntermediate,rotatingIntermediate;
    private static final class Probe extends GearTileEntity {EnumFacing mount;int size;boolean swing;@Override public EnumFacing mountDirection(){return mount;}@Override public int armSize(){return size;}@Override public boolean isSwingArmController(){return swing;}}
    private static void need(boolean value,String why){checks++;if(!value)throw new AssertionError("Arm grid: "+why);}
    private static void set(GearTileEntity arm,String name,Object value)throws Exception{Field field=GearTileEntity.class.getDeclaredField(name);field.setAccessible(true);field.set(arm,value);}
    public static void main(String[] args)throws Exception{
        net.minecraft.init.Bootstrap.register();Constructor<ArmPayload> constructor=ArmPayload.class.getDeclaredConstructor(VehicleStructure.class,BlockPos.class,boolean.class,BlockPos.class);constructor.setAccessible(true);Field grid=ArmPayload.class.getDeclaredField("gridAligned");grid.setAccessible(true);
        List<BlockPos> cells=Arrays.asList(BlockPos.ORIGIN,new BlockPos(1,0,2),new BlockPos(-2,1,-1),new BlockPos(3,-2,1));VehicleStructure blocks=VehicleStructure.display(Collections.singletonList(new VehicleStructure.Cell(BlockPos.ORIGIN,Blocks.STONE.getDefaultState(),null)));
        for(EnumFacing mount:EnumFacing.values())for(GearMotion.Mode mode:GearMotion.Mode.values())for(GearMotion.Direction direction:GearMotion.Direction.values())for(int height:new int[]{1,3,8})for(int size:new int[]{1,2})for(double angle:new double[]{45,90,180}){
            Probe arm=new Probe();arm.setPos(new BlockPos(11,97,-17));arm.mount=mount;arm.size=size;arm.swing=mode!=GearMotion.Mode.EXTEND;set(arm,"assembly",GearTopology.validate(height,GearTopology.FootKind.NONE,Collections.singleton(new GearTopology.Cell(0,-height,0))).assembly());set(arm,"mode",mode);set(arm,"direction",direction);set(arm,"activeAngle",angle);set(arm,"rotatePayload",mode==GearMotion.Mode.ROTATE);
            ArmPayload payload=constructor.newInstance(blocks,new BlockPos(2,-height,1),false,BlockPos.ORIGIN);grid.set(payload,true);
            for(double progress:new double[]{0,.125,.25,.375,.5,.625,.75,.875,1}){set(arm,"progress",progress);net.minecraft.nbt.NBTTagCompound before=payload.write();double oldProgress=arm.progress();ArmGridMapping mapped=payload.gridMapping(arm,progress);boolean expected=true;List<BlockPos> minima=new ArrayList<>();
                for(BlockPos cell:cells){double x=Double.POSITIVE_INFINITY,y=x,z=x,X=Double.NEGATIVE_INFINITY,Y=X,Z=X;for(int corner=0;corner<8;corner++){Vec3d p=payload.transform(arm,progress,cell.getX()+(corner&1),cell.getY()+((corner>>1)&1),cell.getZ()+((corner>>2)&1)).addVector(arm.getPos().getX(),arm.getPos().getY(),arm.getPos().getZ());x=Math.min(x,p.x);y=Math.min(y,p.y);z=Math.min(z,p.z);X=Math.max(X,p.x);Y=Math.max(Y,p.y);Z=Math.max(Z,p.z);}expected&=Math.abs(X-x-1)<1e-8&&Math.abs(Y-y-1)<1e-8&&Math.abs(Z-z-1)<1e-8&&Math.abs(x-Math.rint(x))<1e-8&&Math.abs(y-Math.rint(y))<1e-8&&Math.abs(z-Math.rint(z))<1e-8;minima.add(new BlockPos(Math.rint(x),Math.rint(y),Math.rint(z)));}
                need((mapped!=null)==expected,"actual complete transformed unit-cubes profile="+profiles+" mount="+mount+" mode="+mode+" progress="+progress);if(mapped!=null){aligned++;if(progress>0&&progress<1){intermediate++;if(mode==GearMotion.Mode.EXTEND)extenderIntermediate++;if(mode==GearMotion.Mode.ROTATE)rotatingIntermediate++;}for(int i=0;i<cells.size();i++)need(mapped.cell(cells.get(i)).equals(minima.get(i)),"exact cell minimum");for(EnumFacing face:EnumFacing.values()){Vec3d zero=payload.transform(arm,progress,0,0,0),point=payload.transform(arm,progress,face.getFrontOffsetX(),face.getFrontOffsetY(),face.getFrontOffsetZ()).subtract(zero);need(mapped.facing(face)==EnumFacing.getFacingFromVector((float)point.x,(float)point.y,(float)point.z),"mounted face follows same Source basis");}}else fractional++;
                need(payload.write().equals(before)&&arm.progress()==oldProgress,"read-only query preserves payload snapshot and arm progress");profiles++;
            }
            need(payload.gridMapping(arm,-.01)==null&&payload.gridMapping(arm,1.01)==null&&payload.gridMapping(arm,Double.NaN)==null,"illegal/nonfinite progress refuses");
        }
        Vec3d x=new Vec3d(1,0,0),y=new Vec3d(0,1,0),z=new Vec3d(0,0,1);need(ArmGridMapping.of(BlockPos.ORIGIN,Vec3d.ZERO,new Vec3d[]{x,y,z})!=null,"identity admitted");need(ArmGridMapping.of(BlockPos.ORIGIN,Vec3d.ZERO,new Vec3d[]{x.scale(-1),y,z})==null,"left-handed reflection refused");need(ArmGridMapping.of(BlockPos.ORIGIN,Vec3d.ZERO,new Vec3d[]{x,x,z})==null,"duplicate axes refused");need(ArmGridMapping.of(BlockPos.ORIGIN,new Vec3d(.25,0,0),new Vec3d[]{x,y,z})==null,"fractional translation refused");
        need(aligned>0&&intermediate>0&&extenderIntermediate>0&&rotatingIntermediate>0&&fractional>0,"genuine integral intermediate and fractional controls");System.out.println("Arm grid mapping actual Source PASS profiles="+profiles+" aligned="+aligned+" intermediate="+intermediate+" extenderIntermediate="+extenderIntermediate+" rotatingIntermediate="+rotatingIntermediate+" refused="+fractional+" checks="+checks+" actual unit-corners/faces all6mounts sizes1/2 modes immutable snapshots; world publication separate");
    }
}
