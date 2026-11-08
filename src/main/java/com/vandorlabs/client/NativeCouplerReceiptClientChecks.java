package com.vandorlabs.client;
final class NativeCouplerReceiptClientChecks {
 private static int ticks;
 static boolean tick(net.minecraft.client.Minecraft mc)throws Exception{if(++ticks<80)return false;mc.getIntegratedServer().addScheduledTask(()->{try{com.vandorlabs.vehicle.VehicleArmCouplerNativeReceiptChecks.run(mc.getIntegratedServer().getWorld(0));}catch(Exception e){throw new IllegalStateException(e);}}).get();System.out.println("[vandorlabs][reprolab] utility26-focused PASS native-coupler-receipt-only");return true;}
}
