package com.vandorlabs.gear;

import com.vandorlabs.coupler.*;
import com.vandorlabs.vehicle.*;
import java.util.*;
import net.minecraft.block.state.IBlockState;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.*;
import net.minecraft.util.math.*;

/** Actual released Coupler bounds/state mappings over every proper cardinal basis. */
public final class ArmGridTilesChecks {
    private static int checks,profiles,admitted,refused;
    private static void need(boolean v,String why){checks++;if(!v)throw new AssertionError("Grid Coupler tiles: "+why);}
    private static Vec3d vector(EnumFacing f){return new Vec3d(f.getFrontOffsetX(),f.getFrontOffsetY(),f.getFrontOffsetZ());}
    private static void profile(BlockCoupler block,IBlockState state,ArmGridMapping map){
        NBTTagCompound data=new NBTTagCompound();data.setString("id","vandorlabs:square_coupler");data.setInteger("x",1);data.setInteger("y",2);data.setInteger("z",3);data.setBoolean("CouplerArticulation",true);data.setString("UnknownFutureOwnField","preserved");NBTTagCompound cap=new NBTTagCompound();cap.setByteArray("Opaque",new byte[71]);data.setTag("ForgeCaps",cap);
        VehicleStructure.Cell original=new VehicleStructure.Cell(new BlockPos(1,2,3),state,data);IBlockState moved=ArmGridTiles.state(original,map.basis());boolean representable=!(block instanceof BlockCouplerSlab)||map.facing(EnumFacing.UP).getAxis()==EnumFacing.Axis.Y;
        need((moved!=null)==representable,"slab keeps its authored horizontal half-height restriction");profiles++;if(moved==null){refused++;return;}admitted++;
        AxisAlignedBB bounds=state.getBoundingBox(null,BlockPos.ORIGIN);double[] lo={Double.POSITIVE_INFINITY,Double.POSITIVE_INFINITY,Double.POSITIVE_INFINITY},hi={Double.NEGATIVE_INFINITY,Double.NEGATIVE_INFINITY,Double.NEGATIVE_INFINITY};int[] m=map.basis();BlockPos shift=map.cell(BlockPos.ORIGIN);
        for(int corner=0;corner<8;corner++){double x=(corner&1)==0?bounds.minX:bounds.maxX,y=(corner&2)==0?bounds.minY:bounds.maxY,z=(corner&4)==0?bounds.minZ:bounds.maxZ;double[] p={m[0]*x+m[1]*y+m[2]*z-shift.getX(),m[3]*x+m[4]*y+m[5]*z-shift.getY(),m[6]*x+m[7]*y+m[8]*z-shift.getZ()};for(int a=0;a<3;a++){lo[a]=Math.min(lo[a],p[a]);hi[a]=Math.max(hi[a],p[a]);}}
        AxisAlignedBB expected=moved.getBoundingBox(null,BlockPos.ORIGIN);need(lo[0]==expected.minX&&lo[1]==expected.minY&&lo[2]==expected.minZ&&hi[0]==expected.maxX&&hi[1]==expected.maxY&&hi[2]==expected.maxZ,"actual Source bounds agree including rotated slab half");need(moved.getValue(BlockCoupler.LOCKED)==state.getValue(BlockCoupler.LOCKED),"lock bit invariant");
        VehicleStructure source=VehicleStructure.display(Arrays.asList(original,new VehicleStructure.Cell(new BlockPos(3,2,4),state,data)));
        List<VehicleStructure.Cell> raw=new ArrayList<>();int x=Integer.MAX_VALUE,y=x,z=x;for(VehicleStructure.Cell c:source.cells){BlockPos at=map.cell(c.pos);x=Math.min(x,at.getX());y=Math.min(y,at.getY());z=Math.min(z,at.getZ());raw.add(new VehicleStructure.Cell(at,moved,null));}BlockPos origin=new BlockPos(x,y,z);List<VehicleStructure.Cell> cells=new ArrayList<>();for(int i=0;i<raw.size();i++){VehicleStructure.Cell c=raw.get(i),a=source.cells.get(i);BlockPos p=c.pos.subtract(origin);NBTTagCompound copy=a.tileData();BlockPos delta=p.subtract(a.pos);copy.setInteger("x",copy.getInteger("x")+delta.getX());copy.setInteger("y",copy.getInteger("y")+delta.getY());copy.setInteger("z",copy.getInteger("z")+delta.getZ());cells.add(new VehicleStructure.Cell(p,moved,copy));}
        VehicleStructure placed=VehicleStructure.display(cells);ArmGridTiles.validate(source,placed,m);need(source.cells.get(0).tileData().equals(data),"source bytes unchanged");Collections.reverse(cells);boolean rejected=false;try{ArmGridTiles.validate(source,VehicleStructure.display(cells),m);}catch(IllegalArgumentException expectedFailure){rejected=true;}need(rejected,"ordered cell identity cannot be permuted");
        NBTTagCompound unknown=data.copy();unknown.setString("id","foreign:future_tile");need(ArmGridTiles.state(new VehicleStructure.Cell(original.pos,state,unknown),m)==null,"foreign tile never treated as Coupler");
    }
    public static void main(String[] args){
        net.minecraft.init.Bootstrap.register();net.minecraftforge.fml.common.registry.GameRegistry.registerTileEntity(TileCoupler.class,new ResourceLocation("vandorlabs:square_coupler"));BlockCoupler full=new BlockCoupler(),slab=new BlockCouplerSlab();int bases=0;
        for(EnumFacing x:EnumFacing.values())for(EnumFacing y:EnumFacing.values()){Vec3d vx=vector(x),vy=vector(y);if(vx.dotProduct(vy)!=0)continue;ArmGridMapping map=ArmGridMapping.of(BlockPos.ORIGIN,Vec3d.ZERO,new Vec3d[]{vx,vy,vx.crossProduct(vy)});need(map!=null,"proper cardinal basis");bases++;for(int meta=0;meta<16;meta++){if((meta&7)<6)profile(full,full.getStateFromMeta(meta),map);profile(slab,slab.getStateFromMeta(meta),map);}}
        need(bases==24&&profiles==672&&admitted==416&&refused==256,"all proper bases/full metadata/slab metadata counts");System.out.println("Arm typed Coupler grid mapping PASS bases="+bases+" profiles="+profiles+" admitted="+admitted+" refused="+refused+" checks="+checks+" actual authored bounds/metadata/opaque data/order/unsupported tile refusal; publication separate");
    }
}
