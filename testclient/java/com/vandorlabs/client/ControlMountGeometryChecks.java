package com.vandorlabs.client;

import com.vandorlabs.blocks.SignalControlMount;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.*;
import java.security.MessageDigest;

/** Frozen original Java-8 transform/bounds/wedge bits; includes fractional authored-style coordinates. */
public final class ControlMountGeometryChecks {
    private static final byte[] BYTES=new byte[8];
    private static void value(MessageDigest digest,double value){long bits=Double.doubleToRawLongBits(value);for(int i=0;i<8;i++)BYTES[7-i]=(byte)(bits>>>(8*i));digest.update(BYTES);}
    private static void point(MessageDigest digest,Vec3d p){value(digest,p.x);value(digest,p.y);value(digest,p.z);}
    private static void box(MessageDigest digest,AxisAlignedBB b){value(digest,b.minX);value(digest,b.minY);value(digest,b.minZ);value(digest,b.maxX);value(digest,b.maxY);value(digest,b.maxZ);}
    private static AxisAlignedBB support(EnumFacing face,int kind){
        double[] low={.3125,.11330098,.1875},high={.6875,.87565048,.8125},depth={.0625,.046875,.109375};double[] a={low[kind],low[kind],low[kind]},b={high[kind],high[kind],high[kind]};int axis=face.getAxis()==EnumFacing.Axis.X?0:face.getAxis()==EnumFacing.Axis.Y?1:2;
        a[axis]=face.getAxisDirection()==EnumFacing.AxisDirection.POSITIVE?0:1-depth[kind];b[axis]=a[axis]+depth[kind];return new AxisAlignedBB(a[0],a[1],a[2],b[0],b[1],b[2]);
    }
    public static void main(String[] ignored)throws Exception{
        MessageDigest digest=MessageDigest.getInstance("SHA-256"),reference=MessageDigest.getInstance("SHA-256");int cases=0,wedges=0;
        AxisAlignedBB body=new AxisAlignedBB(.11330098,.0625,.30394670,.88669902,.9375,.87565048);
        for(EnumFacing face:EnumFacing.values())for(int rotation=0;rotation<4;rotation++)for(int height=0;height<4;height++)for(int tilt=0;tilt<4;tilt++)for(int direction=0;direction<4;direction++)for(int kind=0;kind<3;kind++){
            AxisAlignedBB support=support(face,kind);SignalControlMount mount=new SignalControlMount(face,rotation,height,tilt,direction,support);com.vandorlabs.blocks.LegacyControlMountReference original=new com.vandorlabs.blocks.LegacyControlMountReference(face,rotation,height,tilt,direction,support);
            for(double x:new double[]{support.minX,support.maxX})for(double y:new double[]{support.minY,support.maxY})for(double z:new double[]{support.minZ,support.maxZ}){
                Vec3d p=new Vec3d(x,y,z);point(digest,mount.transform(p));point(digest,mount.project(p));value(digest,mount.depth(p));point(reference,original.transform(p));point(reference,original.project(p));value(reference,original.depth(p));
            }
            for(Vec3d p:new Vec3d[]{new Vec3d(-0.0,.11330098,-0.0),new Vec3d(.5,.5,.5),new Vec3d(.30394670,.87565048,.0625)}){point(digest,mount.transform(p));point(digest,mount.project(p));value(digest,mount.depth(p));point(reference,original.transform(p));point(reference,original.project(p));value(reference,original.depth(p));}
            box(digest,mount.bounds(support));box(digest,mount.bounds(body));box(reference,original.bounds(support));box(reference,original.bounds(body));java.util.List<AxisAlignedBB> parts=mount.wedgeBoxes(support);value(digest,parts.size());for(AxisAlignedBB part:parts){box(digest,part);wedges++;}java.util.List<AxisAlignedBB> old=original.wedgeBoxes(support);value(reference,old.size());for(AxisAlignedBB part:old)box(reference,part);cases++;
        }
        StringBuilder hash=new StringBuilder();for(byte part:digest.digest())hash.append(String.format("%02x",part&255));
        StringBuilder expected=new StringBuilder();for(byte part:reference.digest())expected.append(String.format("%02x",part&255));
        if(!expected.toString().equals(hash.toString()))throw new AssertionError("Original mount geometry changed: "+hash+" != "+expected);
        System.out.println("Control mount geometry bits PASS cases="+cases+" wedges="+wedges+" sha256="+hash);
    }
}
