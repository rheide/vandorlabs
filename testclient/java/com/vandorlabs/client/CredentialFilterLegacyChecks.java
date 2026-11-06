package com.vandorlabs.client;
import com.vandorlabs.items.*;
import com.vandorlabs.tiles.TileEntityTokenSlot;
import java.security.MessageDigest;

/** Actual current metadata/filter labels, hit bounds and normalization, without rendering. */
public final class CredentialFilterLegacyChecks {
 public static void main(String[] args)throws Exception{
  net.minecraft.init.Bootstrap.register();MessageDigest digest=MessageDigest.getInstance("SHA-256");int cases=0;
  for(boolean chip:new boolean[]{false,true})for(int mask=-256;mask<=512;mask++){
   net.minecraft.item.ItemStack sample=CredentialFilterPicker.sample(chip,mask);String head=chip+":"+mask+":"+CredentialFilterPicker.label(chip,mask)+":"+(sample.isEmpty()?-1:sample.getMetadata())+":"+TileEntityTokenSlot.singleCompany(mask)+";";digest.update(head.getBytes(java.nio.charset.StandardCharsets.UTF_8));
   for(int button:new int[]{-1,0,1,2})for(int[] point:new int[][]{{10,20},{137,20},{138,20},{163,20},{164,20},{10,19},{10,37},{10,38},{9,20}}){int[] selected={Integer.MIN_VALUE};boolean hit=CredentialFilterPicker.click(null,point[0],point[1],button,10,20,chip,mask,value->selected[0]=value);String value=hit+":"+selected[0]+";";digest.update(value.getBytes(java.nio.charset.StandardCharsets.UTF_8));cases++;}
  }
  StringBuilder hash=new StringBuilder();for(byte b:digest.digest())hash.append(String.format("%02x",b&255));String expected="8e140e5e4b2217161b2d102b872f1444eb00b07fcf440c800ccea50e934818c5";if(!expected.isEmpty()&&!expected.equals(hash.toString()))throw new AssertionError("Actual credential filter changed "+hash);System.out.println("Actual credential filter PASS cases="+cases+" golden="+hash);
 }
}
