package com.vandorlabs.client;
final class NativeReceiptRollbackClientChecks {
 private static int ticks;private static boolean setup;
 static boolean tick(net.minecraft.client.Minecraft mc)throws Exception{if(++ticks<80)return false;if(!setup){mc.getIntegratedServer().addScheduledTask(()->com.vandorlabs.vehicle.VehicleNativeReceiptRollbackChecks.setup(mc.getIntegratedServer().getWorld(0),mc.getIntegratedServer().getPlayerList().getPlayers().get(0))).get();setup=true;return false;}boolean done=mc.getIntegratedServer().callFromMainThread(()->com.vandorlabs.vehicle.VehicleNativeReceiptRollbackChecks.tick(mc.getIntegratedServer().getPlayerList().getPlayers().get(0))).get();if(done)System.out.println("[vandorlabs][reprolab] utility26-focused PASS native-receipt-rollback-only");return done;}
}
