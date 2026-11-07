package com.vandorlabs.gear;

import com.vandorlabs.coupler.*;
import com.vandorlabs.vehicle.*;
import net.minecraft.block.BlockSlab;
import net.minecraft.block.state.IBlockState;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.*;

/** Narrow typed state mapping. Unknown attachment tiles are never interpreted or rotated. */
final class ArmGridTiles {
    private ArmGridTiles(){}
    static Vec3d[] axes(int[] m){
        if(m==null||m.length!=9)throw new IllegalArgumentException("Invalid attachment basis");
        Vec3d[] out={new Vec3d(m[0],m[3],m[6]),new Vec3d(m[1],m[4],m[7]),new Vec3d(m[2],m[5],m[8])};
        if(ArmGridMapping.of(BlockPos.ORIGIN,Vec3d.ZERO,out)==null)throw new IllegalArgumentException("Invalid attachment basis");return out;
    }
    static IBlockState state(VehicleStructure.Cell cell,int[] basis){return state(cell,cell.state,basis);}
    /** Same typed policy for inverse mapping; the tile still belongs to this exact block type. */
    static IBlockState state(VehicleStructure.Cell cell,IBlockState state,int[] basis){
        if(state.getBlock()!=cell.state.getBlock())return null;
        Vec3d[] axes=axes(basis);
        if(!(state.getBlock() instanceof BlockCoupler))return cell.hasTileData()||!state.isFullCube()?null:ArmPayload.rotateState(state,axes);
        if(state.getPropertyKeys().size()!=(state.getBlock() instanceof BlockCouplerSlab?3:2))return null;
        ResourceLocation id=TileEntity.getKey(TileCoupler.class);
        if(!cell.hasTileData()||id==null||!id.toString().equals(cell.tileString("id")))return null;
        EnumFacing face=state.getValue(BlockCoupler.FACING);Vec3d out=axes[face.getAxis()==EnumFacing.Axis.X?0:face.getAxis()==EnumFacing.Axis.Y?1:2].scale(face.getAxisDirection()==EnumFacing.AxisDirection.POSITIVE?1:-1);
        EnumFacing mapped=EnumFacing.getFacingFromVector((float)out.x,(float)out.y,(float)out.z);
        if(state.getBlock() instanceof BlockCouplerSlab){
            if(Math.abs(axes[1].y)!=1||!mapped.getAxis().isHorizontal())return null;
            if(axes[1].y<0)state=state.withProperty(BlockCouplerSlab.HALF,state.getValue(BlockCouplerSlab.HALF)==BlockSlab.EnumBlockHalf.TOP?BlockSlab.EnumBlockHalf.BOTTOM:BlockSlab.EnumBlockHalf.TOP);
        }
        state=state.withProperty(BlockCoupler.FACING,mapped);
        return state.getBlock().getStateFromMeta(state.getBlock().getMetaFromState(state))==state?state:null;
    }
    static void validate(VehicleStructure source,VehicleStructure placed,int[] basis){
        axes(basis);if(source.cells.isEmpty()||source.cells.size()!=placed.cells.size())throw new IllegalArgumentException("Invalid typed attachment size");
        BlockPos first=source.cells.get(0).pos,target=placed.cells.get(0).pos;
        for(int i=0;i<source.cells.size();i++){
            VehicleStructure.Cell a=source.cells.get(i),b=placed.cells.get(i);BlockPos d=a.pos.subtract(first);
            BlockPos expected=target.add(basis[0]*d.getX()+basis[1]*d.getY()+basis[2]*d.getZ(),basis[3]*d.getX()+basis[4]*d.getY()+basis[5]*d.getZ(),basis[6]*d.getX()+basis[7]*d.getY()+basis[8]*d.getZ());
            if(!expected.equals(b.pos)||state(a,basis)!=b.state||a.hasTileData()!=b.hasTileData())throw new IllegalArgumentException("Invalid ordered attachment state mapping");
            if(a.hasTileData()){
                NBTTagCompound data=a.tileData();BlockPos shift=b.pos.subtract(a.pos);data.setInteger("x",data.getInteger("x")+shift.getX());data.setInteger("y",data.getInteger("y")+shift.getY());data.setInteger("z",data.getInteger("z")+shift.getZ());
                if(!data.equals(b.tileData()))throw new IllegalArgumentException("Typed attachment altered original Coupler data");
            }
        }
    }
    static int[] yawBasis(int[] basis,int turns){
        int[] y;switch(Math.floorMod(turns,4)){case 1:y=new int[]{0,0,-1,0,1,0,1,0,0};break;case 2:y=new int[]{-1,0,0,0,1,0,0,0,-1};break;case 3:y=new int[]{0,0,1,0,1,0,-1,0,0};break;default:return basis.clone();}
        int[] result=new int[9];for(int r=0;r<3;r++)for(int c=0;c<3;c++)for(int a=0;a<3;a++)for(int b=0;b<3;b++)result[r*3+c]+=y[r*3+a]*basis[a*3+b]*y[c*3+b];return result;
    }
}
