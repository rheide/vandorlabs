package com.vandorlabs.gear;

import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

/** Read-only integer affine geometry; does not authorize tile/state rotation or publication. */
public final class ArmGridMapping {
    private final BlockPos corner;
    private final int[] axes;
    private ArmGridMapping(BlockPos corner,int[] axes){this.corner=corner.toImmutable();this.axes=axes;}
    static ArmGridMapping resolve(ArmPayload payload,GearTileEntity arm,double progress){
        if(payload==null||arm==null||arm.assembly()==null||!Double.isFinite(progress)||progress<0||progress>1)return null;
        Vec3d zero=payload.transform(arm,progress,0,0,0);
        Vec3d[] columns={payload.transform(arm,progress,1,0,0).subtract(zero),payload.transform(arm,progress,0,1,0).subtract(zero),payload.transform(arm,progress,0,0,1).subtract(zero)};
        return of(arm.getPos(),zero,columns);
    }
    static ArmGridMapping of(BlockPos root,Vec3d zero,Vec3d[] columns){
        double[] origin={zero.x,zero.y,zero.z};for(double value:origin)if(!Double.isFinite(value)||Math.abs(value)>1000000||Math.abs(value-Math.rint(value))>1e-8)return null;
        if(columns.length!=3)return null;int[] m=new int[9];
        for(int c=0;c<3;c++){double[] values={columns[c].x,columns[c].y,columns[c].z};int length=0;for(int row=0;row<3;row++){double value=values[row];if(!Double.isFinite(value)||Math.abs(value)>1+1e-8||Math.abs(value-Math.rint(value))>1e-8)return null;m[row*3+c]=(int)Math.rint(value);length+=Math.abs(m[row*3+c]);}if(length!=1)return null;}
        for(int a=0;a<3;a++)for(int b=a+1;b<3;b++)if(m[a]*m[b]+m[3+a]*m[3+b]+m[6+a]*m[6+b]!=0)return null;
        int determinant=m[0]*(m[4]*m[8]-m[5]*m[7])-m[1]*(m[3]*m[8]-m[5]*m[6])+m[2]*(m[3]*m[7]-m[4]*m[6]);if(determinant!=1)return null;
        return new ArmGridMapping(new BlockPos(Math.addExact(root.getX(),(int)Math.rint(zero.x)),Math.addExact(root.getY(),(int)Math.rint(zero.y)),Math.addExact(root.getZ(),(int)Math.rint(zero.z))),m);
    }
    /** Minimum world/local-carrier corner of a complete transformed unit cell. */
    public BlockPos cell(BlockPos local){return new BlockPos(coordinate(0,corner.getX(),local),coordinate(1,corner.getY(),local),coordinate(2,corner.getZ(),local));}
    private int coordinate(int row,int origin,BlockPos p){int at=row*3;int result=Math.addExact(origin,Math.addExact(Math.multiplyExact(axes[at],p.getX()),Math.addExact(Math.multiplyExact(axes[at+1],p.getY()),Math.multiplyExact(axes[at+2],p.getZ()))));return Math.addExact(result,Math.min(0,axes[at])+Math.min(0,axes[at+1])+Math.min(0,axes[at+2]));}
    public EnumFacing facing(EnumFacing local){int x=local.getFrontOffsetX(),y=local.getFrontOffsetY(),z=local.getFrontOffsetZ();return EnumFacing.getFacingFromVector(axes[0]*x+axes[1]*y+axes[2]*z,axes[3]*x+axes[4]*y+axes[5]*z,axes[6]*x+axes[7]*y+axes[8]*z);}
    public boolean rotates(){return axes[0]!=1||axes[4]!=1||axes[8]!=1;}
}
