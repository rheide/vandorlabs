package com.vandorlabs.client;
import com.vandorlabs.vehicle.VehicleNetwork;
import java.lang.reflect.*;
import java.util.*;
/** Header retention does not admit scope; no Minecraft/world instance is required. */
public final class GearMotionRetentionChecks {
 private static int checks;
 private static void need(boolean value,String why){checks++;if(!value)throw new AssertionError(why);}
 @SuppressWarnings("unchecked") public static void main(String[] args)throws Exception{
  UUID owner=new UUID(1,2),scope=new UUID(3,4),other=new UUID(5,6);
  Class<?> type=Class.forName("com.vandorlabs.client.VehicleClient$Incoming");Constructor<?> constructor=type.getDeclaredConstructor(VehicleNetwork.Packet.class);constructor.setAccessible(true);
  Field created=type.getDeclaredField("created");created.setAccessible(true);
  for(String lane:new String[]{"incoming","completed"}){
   Field field=VehicleClient.class.getDeclaredField(lane);field.setAccessible(true);Map<UUID,Object> map=(Map<UUID,Object>)field.get(null);
   VehicleNetwork.Packet p=new VehicleNetwork.Packet(VehicleNetwork.SNAPSHOT);p.token=owner;p.associationOwner=owner;p.associationGeneration=scope;p.total=8;
   Object pending=constructor.newInstance(p);map.put(owner,pending);
   need(VehicleClient.pendingSourceScope(owner,scope),lane+" matching header protects retention");
   need(!VehicleClient.pendingSourceScope(owner,other),"different scope not protected");
   need(!VehicleClient.pendingSourceScope(other,scope),"different owner not protected");
   p.associationOwner=other;need(!VehicleClient.pendingSourceScope(owner,scope),"explicit owner required");p.associationOwner=owner;
   created.setLong(pending,System.currentTimeMillis()-10001);need(!VehicleClient.pendingSourceScope(owner,scope),"expired header loses retention");
   map.remove(owner);need(!VehicleClient.pendingSourceScope(owner,scope),"removed header loses retention");
  }
  need(!VehicleClient.pendingSourceScope(owner,null),"legacy scope not protected by guessed header");
  System.out.println("Gear motion retention PASS checks="+checks+" validated header owner/generation/expiry/removal; retention only");
 }
}
