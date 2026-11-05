package com.vandorlabs.neoforge;
import com.vandorlabs.neoforge.fluid.NativeFluidRenderSnapshot;
import java.nio.*;
import net.neoforged.neoforge.client.extensions.IVertexConsumerExtension;
/** Exercise NeoForge's actual mesh merge while external light decreases; no world/client bootstrap. */
public final class NativeFluidLightChecks {
 private static void require(boolean value,String why){if(!value)throw new AssertionError(why);}
 public static void main(String[] args){IVertexConsumerExtension merge=new IVertexConsumerExtension(){};ByteBuffer vertex=ByteBuffer.allocate(32).order(ByteOrder.nativeOrder());int[] sky={15,7,0},block={12,4,0};
  vertex.putInt(24,15<<4);for(int i=0;i<3;i++)require((merge.applyBakedLighting((sky[i]<<20)|(block[i]<<4),vertex)&65535)==15<<4,"negative control must reproduce stale baked external light");
  vertex.putInt(24,NativeFluidRenderSnapshot.emissionLight(3));for(int i=0;i<3;i++){int result=merge.applyBakedLighting((sky[i]<<20)|(block[i]<<4),vertex);require((result>>>16)==sky[i]<<4,"current sky light must decrease");require((result&65535)==Math.max(3,block[i])<<4,"current block light decreases to self emission");}
  require(NativeFluidRenderSnapshot.emissionLight(-1)==0&&NativeFluidRenderSnapshot.emissionLight(16)==240,"emission bounds");System.out.println("Native fluid light PASS actual NeoForge merge: old external-light freeze reproduced, sky/block decrease while intrinsic emission remains");
 }
}
