package com.vandorlabs.client;
import com.google.gson.*;
import com.vandorlabs.items.ModItems;
import com.vandorlabs.tiles.TileEntityTokenSlot;
import java.nio.file.*;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

/** Actual current reader scalar/state NBT policy; native consumes these captured expectations. */
public final class CredentialReaderLegacyChecks {
 private static final class World extends NonRenderingChecks.MemoryWorld {World(){super(false);}@Override public long getTotalWorldTime(){return 100;}}
 public static void main(String[] args)throws Exception{
  net.minecraft.init.Bootstrap.register();net.minecraftforge.fml.common.registry.ForgeRegistries.ITEMS.register(ModItems.ACCESS_CARD);net.minecraftforge.fml.common.registry.ForgeRegistries.ITEMS.register(ModItems.DATA_CHIP);net.minecraftforge.fml.common.registry.GameRegistry.registerTileEntity(TileEntityTokenSlot.class,new net.minecraft.util.ResourceLocation("vandorlabs","credential_reader_fixture"));JsonArray rows=new JsonArray();int index=0;
  for(boolean chip:new boolean[]{false,true})for(int behavior:new int[]{-1,0,1,2,3,4})for(int allowed:new int[]{0,2,3,127})for(int company:new int[]{-1,0,1,6,7})for(boolean toggled:new boolean[]{false,true})for(long deadline:new long[]{0,99,100,101})for(boolean returning:new boolean[]{false,true}){
   NBTTagCompound input=new NBTTagCompound();input.setBoolean("ChipSlot",chip);input.setBoolean("AnyToken",false);input.setInteger("AllowedTokens",allowed);input.setInteger("TokenBehavior",behavior);input.setBoolean("TokenToggled",toggled);input.setLong("TokenPulseUntil",deadline);input.setInteger("TokenLogo",index%9-1);input.setLong("TokenInsertStarted",80);input.setLong("TokenInsertionSequence",17);if(returning)input.setUniqueId("TokenReturnTo",java.util.UUID.fromString("46ed10fb-c563-4c59-9f2a-e45a228f6781"));if(company>=0)input.setTag("InsertedToken",new ItemStack(chip?ModItems.DATA_CHIP:ModItems.ACCESS_CARD,2,company).writeToNBT(new NBTTagCompound()));input.setString("FutureReader","opaque");TileEntityTokenSlot tile=new TileEntityTokenSlot();tile.readFromNBT(input);tile.setWorld(new World());tile.setPos(new net.minecraft.util.math.BlockPos(0,80,0));NBTTagCompound saved=tile.writeToNBT(new NBTTagCompound());
   JsonObject row=new JsonObject();row.addProperty("input",input.toString());row.addProperty("chip",tile.chip());row.addProperty("any",tile.any);row.addProperty("allowed",tile.allowed);row.addProperty("behavior",tile.behavior);row.addProperty("logo",tile.logoDesign());row.addProperty("savedLogo",saved.getInteger("TokenLogo"));row.addProperty("output",tile.getOutputLevel());row.addProperty("empty",tile.isEmpty());row.addProperty("returning",tile.returning());row.addProperty("company",tile.isEmpty()?-1:tile.getStackInSlot(0).getMetadata());row.addProperty("count",tile.getStackInSlot(0).getCount());rows.add(row);index++;
  }
  String serialized=new Gson().toJson(rows);java.security.MessageDigest digest=java.security.MessageDigest.getInstance("SHA-256");StringBuilder hash=new StringBuilder();for(byte b:digest.digest(serialized.getBytes(java.nio.charset.StandardCharsets.UTF_8)))hash.append(String.format("%02x",b&255));String expected="c7269ee705ec594c1c5d62410c290e571f445f827579009d9ae88b4e108a1c78";if(!expected.isEmpty()&&!expected.equals(hash.toString()))throw new AssertionError("Actual reader policy changed "+hash);JsonObject root=new JsonObject();root.addProperty("schema",1);root.addProperty("now",100);root.addProperty("golden",hash.toString());root.add("cases",rows);Path file=Paths.get(System.getProperty("vandorlabs.credentialReaderReference","build/test-artifacts/credential-reader-forge.json"));Files.createDirectories(file.toAbsolutePath().getParent());try(java.io.Writer writer=Files.newBufferedWriter(file)){new Gson().toJson(root,writer);}System.out.println("Actual credential reader policy PASS cases="+rows.size()+" golden="+hash);
 }
}
