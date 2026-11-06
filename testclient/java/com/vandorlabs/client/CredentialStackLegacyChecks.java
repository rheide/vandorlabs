package com.vandorlabs.client;
import com.google.gson.*;
import com.vandorlabs.items.ModItems;
import com.vandorlabs.tiles.TileEntityTokenSlot;
import java.nio.file.*;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

/** Actual1.12 credential stack loading, including invalid metadata and opaque original payloads. */
public final class CredentialStackLegacyChecks {
 public static void main(String[] args)throws Exception{
  net.minecraft.init.Bootstrap.register();net.minecraftforge.fml.common.registry.ForgeRegistries.ITEMS.register(ModItems.ACCESS_CARD);net.minecraftforge.fml.common.registry.ForgeRegistries.ITEMS.register(ModItems.DATA_CHIP);JsonArray rows=new JsonArray();
  for(boolean chip:new boolean[]{false,true})for(int count:new int[]{-1,0,1,2,64})for(int damage:new int[]{-32768,-1,0,1,2,3,4,5,6,7,8,32767}){
   NBTTagCompound input=new NBTTagCompound();input.setString("id","vandorlabs:"+(chip?"data_chip":"access_card"));input.setByte("Count",(byte)count);input.setShort("Damage",(short)damage);input.setString("FutureStackRoot","opaque");NBTTagCompound data=new NBTTagCompound();data.setIntArray("FutureProgram",new int[]{1,-7,Integer.MAX_VALUE});NBTTagCompound display=new NBTTagCompound();display.setString("Name","Credential migration fixture");data.setTag("display",display);input.setTag("tag",data);NBTTagCompound caps=new NBTTagCompound();caps.setInteger("private",271828);input.setTag("ForgeCaps",caps);
   ItemStack stack=new ItemStack(input);TileEntityTokenSlot card=new TileEntityTokenSlot(false),reader=new TileEntityTokenSlot(true);JsonObject row=new JsonObject();row.addProperty("input",input.toString());row.addProperty("empty",stack.isEmpty());row.addProperty("company",stack.isEmpty()?-1:stack.getMetadata());row.addProperty("card",card.accepts(stack));row.addProperty("chip",reader.accepts(stack));row.addProperty("anyAuthorized",(chip?reader:card).authorized(stack));if(!stack.isEmpty())row.addProperty("name",stack.getDisplayName());rows.add(row);
  }
  String serialized=new Gson().toJson(rows);java.security.MessageDigest digest=java.security.MessageDigest.getInstance("SHA-256");StringBuilder hash=new StringBuilder();for(byte b:digest.digest(serialized.getBytes(java.nio.charset.StandardCharsets.UTF_8)))hash.append(String.format("%02x",b&255));String expected="16d1daf2c8cd8cf7a610f82783dd4cfa3cf49b127a3c537b24ff65542bb7ecde";if(!expected.isEmpty()&&!expected.equals(hash.toString()))throw new AssertionError("Actual credential stack changed "+hash);JsonObject root=new JsonObject();root.addProperty("schema",1);root.addProperty("golden",hash.toString());root.add("cases",rows);Path file=Paths.get(System.getProperty("vandorlabs.credentialStackReference","build/test-artifacts/credential-stack-forge.json"));Files.createDirectories(file.toAbsolutePath().getParent());try(java.io.Writer writer=Files.newBufferedWriter(file)){new Gson().toJson(root,writer);}System.out.println("Actual credential stacks PASS cases="+rows.size()+" golden="+hash);
 }
}
