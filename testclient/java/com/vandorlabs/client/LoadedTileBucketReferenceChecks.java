package com.vandorlabs.client;

import com.vandorlabs.world.LoadedTileIndex;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.util.Random;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;

/** Actual released bucket address and strict tile-overlap policies, without world access. */
public final class LoadedTileBucketReferenceChecks {
    public static void main(String[] args)throws Exception{
        net.minecraft.init.Bootstrap.register();Method key=LoadedTileIndex.class.getDeclaredMethod("key",int.class,int.class,int.class),inside=LoadedTileIndex.class.getDeclaredMethod("inside",TileEntity.class,AxisAlignedBB.class);key.setAccessible(true);inside.setAccessible(true);TileEntity tile=new TileEntity(){};Random random=new Random(0x4255434b455453L);MessageDigest digest=MessageDigest.getInstance("SHA-256");int cases=10000;
        for(int i=0;i<cases;i++){
            int kx=random.nextInt(),ky=random.nextInt(),kz=random.nextInt();long address=(Long)key.invoke(null,kx,ky,kz);digest.update(ByteBuffer.allocate(8).putLong(address).array());
            int x=random.nextInt(60000001)-30000000,y=random.nextInt(512)-128,z=random.nextInt(60000001)-30000000;tile.setPos(new BlockPos(x,y,z));
            double ax=x+random.nextDouble()*4-2,ay=y+random.nextDouble()*4-2,az=z+random.nextDouble()*4-2;AxisAlignedBB bounds=new AxisAlignedBB(ax,ay,az,ax+random.nextDouble()*3,ay+random.nextDouble()*3,az+random.nextDouble()*3);digest.update((byte)((Boolean)inside.invoke(null,tile,bounds)?1:0));
        }
        StringBuilder hash=new StringBuilder();for(byte b:digest.digest())hash.append(String.format("%02x",b&255));String expected=System.getProperty("vandorlabs.spatialBucketGolden","1305a57bc0301e85625e928f16d8a8014c1cf35217c10bf8f34a8b3170df1680");if(!expected.isEmpty()&&!expected.equals(hash.toString()))throw new AssertionError("Released tile bucket geometry changed "+hash);
        System.out.println("Loaded tile bucket actual reference PASS cases="+cases+" SHA256="+hash);
    }
}
