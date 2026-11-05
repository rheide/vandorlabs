package com.vandorlabs.client;

import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.security.MessageDigest;
import net.minecraft.util.math.AxisAlignedBB;

/** Freeze the actual offset/intersection contract before removing temporary boxes. */
public final class FluidClipMathChecks {
    private static final String GOLDEN="70e1b56eec6fa910efa16c498855ab1d2b764b251c5e90ff157b11ce9c8761e3";
    private static final AxisAlignedBB UNIT=new AxisAlignedBB(0,0,0,1,1,1);
    private static double[] coordinates(AxisAlignedBB b){return new double[]{b.minX,b.minY,b.minZ,b.maxX,b.maxY,b.maxZ};}
    public static void main(String[] args)throws Exception{
        Method method=com.vandorlabs.blocks.WaterloggedParts.class.getDeclaredMethod("localIntersection",AxisAlignedBB.class,double.class,double.class,double.class);method.setAccessible(true);
        double[] values={-3,-1,-Double.MIN_VALUE,-0.0,0.0,Double.MIN_VALUE,.25,.5,Math.nextDown(1D),1,Math.nextUp(1D),3};
        double[] offsets={-2147483648D,-100,-1,-.25,-0.0,0.0,.25,1,100,2147483647D};MessageDigest digest=MessageDigest.getInstance("SHA-256");ByteBuffer bits=ByteBuffer.allocate(8);int count=0;
        for(double a:values)for(double b:values)for(double offset:offsets){AxisAlignedBB input=new AxisAlignedBB(a,b,-a,b,a,-b);double x=offset,y=-offset,z=offset*.5;AxisAlignedBB old=input.offset(x,y,z).intersect(UNIT);double[] expected=coordinates(old);for(double value:expected){bits.clear();bits.putLong(Double.doubleToRawLongBits(value));digest.update(bits.array());}
            if(method!=null){AxisAlignedBB actual=(AxisAlignedBB)method.invoke(null,input,x,y,z);double[] result=coordinates(actual);for(int i=0;i<6;i++)if(Double.doubleToRawLongBits(expected[i])!=Double.doubleToRawLongBits(result[i]))throw new AssertionError("fluid clip raw bits case="+count+" axis="+i);}count++;
        }
        if(method!=null){int[] calls={0,0};AxisAlignedBB custom=new AxisAlignedBB(-.25,0,0,.75,1,1){@Override public AxisAlignedBB offset(double x,double y,double z){calls[0]++;return new AxisAlignedBB(minX+x+.125,minY+y,minZ+z,maxX+x+.125,maxY+y,maxZ+z){@Override public AxisAlignedBB intersect(AxisAlignedBB other){calls[1]++;return super.intersect(other).offset(.0625,0,0);}};}};double[] expected=coordinates(custom.offset(0,0,0).intersect(UNIT)),actual=coordinates((AxisAlignedBB)method.invoke(null,custom,0D,0D,0D));for(int i=0;i<6;i++)if(Double.doubleToRawLongBits(expected[i])!=Double.doubleToRawLongBits(actual[i]))throw new AssertionError("custom box dispatch changed");if(calls[0]!=2||calls[1]!=2)throw new AssertionError("custom offset/intersection overrides bypassed");}
        StringBuilder hash=new StringBuilder();for(byte b:digest.digest())hash.append(String.format("%02x",b&255));if(!GOLDEN.equals(hash.toString()))throw new AssertionError("actual clip baseline changed "+hash);System.out.println("Fluid clipping PASS cases="+count+" golden="+hash+" boundary/outside/inverted/signed-zero raw coordinates verified="+(method!=null));
    }
}
