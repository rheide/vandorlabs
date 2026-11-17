package com.vandorlabs.neoforge.appliances;
import net.minecraft.server.level.ServerPlayer;
/** Product creation belongs to the service; hardware never consumes inputs before a product is ready. */
public final class NativeApplianceHooks {
 @FunctionalInterface public interface Product {boolean activate(ServerPlayer player,NativeApplianceEntity appliance);}
 @FunctionalInterface public interface Display {void display(ServerPlayer player,NativeApplianceEntity appliance);}
 public static Product PRODUCT=(player,tile)->false;
 public static Display DISPLAY=(player,tile)->{};
 public static void display(ServerPlayer player,NativeApplianceEntity tile){DISPLAY.display(player,tile);}
 private NativeApplianceHooks(){}
}
