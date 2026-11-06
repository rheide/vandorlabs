package com.vandorlabs.client;

import com.google.gson.*;
import com.vandorlabs.blocks.*;
import java.nio.file.*;
import net.minecraft.client.renderer.block.model.*;
import net.minecraft.item.*;
import net.minecraft.nbt.*;

/** Frozen output of actual private Forge item selector and actual GUI/hand wrapper, before extraction. */
public final class DoorItemLegacyChecks {
    public static void main(String[] args)throws Exception{
        net.minecraft.init.Bootstrap.register();
        Item ordinary=new Item().setRegistryName("vandorlabs","door_item_reference");BlockDetailedDoor model=new BlockDetailedDoor("door_item_reference_model",BlockVandorDoor.DoorMotion.SLIDING,true,true,false,1,15,7,90,-90,-16,16);Item large=new com.vandorlabs.items.ItemLargeProgrammableDoor(new BlockLargeProgrammableDoor("door_item_reference_large",model)).setRegistryName("vandorlabs","door_item_reference_large");net.minecraftforge.fml.common.registry.ForgeRegistries.ITEMS.register(ordinary);net.minecraftforge.fml.common.registry.ForgeRegistries.ITEMS.register(large);
        java.lang.reflect.Method selector=ModBlocks.class.getDeclaredMethod("doorItemModel",ItemStack.class);selector.setAccessible(true);JsonArray rows=new JsonArray();
        for(boolean big:new boolean[]{false,true}){
            for(int kind=0;kind<4;kind++){ItemStack stack=new ItemStack(big?large:ordinary);if(kind>0){setRoot(stack,kind);}row(rows,selector,stack,big);}
            for(int design=-2;design<32;design++)for(int detail=-2;detail<5;detail++)for(int bits=0;bits<8;bits++){
                ItemStack stack=new ItemStack(big?large:ordinary);NBTTagCompound data=new NBTTagCompound();data.setInteger("SpaceDesign",design);data.setInteger("SpaceDetail",detail);data.setBoolean("SpaceDoorSliding",(bits&1)!=0);data.setBoolean("SpaceFramed",(bits&2)!=0);data.setBoolean("SpaceDoorHinges",(bits&4)!=0);data.setInteger("DoorFaceTexture",bits%2==0?Integer.MAX_VALUE:7);NBTTagCompound root=new NBTTagCompound();root.setTag("SpaceDoorSettings",data);stack.setTagCompound(root);row(rows,selector,stack,big);
            }
            for(int type=0;type<6;type++){ItemStack stack=new ItemStack(big?large:ordinary);NBTTagCompound data=new NBTTagCompound();NBTBase value=type==0?new NBTTagByte((byte)-1):type==1?new NBTTagShort((short)29):type==2?new NBTTagLong(Long.MAX_VALUE):type==3?new NBTTagFloat(4.9F):type==4?new NBTTagDouble(6.9):new NBTTagString("29");data.setTag("SpaceDesign",value);data.setTag("SpaceDetail",value.copy());data.setTag("SpaceFramed",value.copy());data.setTag("SpaceDoorSliding",value.copy());data.setTag("SpaceDoorHinges",value.copy());NBTTagCompound root=new NBTTagCompound();root.setTag("SpaceDoorSettings",data);stack.setTagCompound(root);row(rows,selector,stack,big);}
        }
        JsonArray transforms=new JsonArray();for(boolean padded:new boolean[]{false,true}){ItemCameraTransforms t=new ScaledDoorItemModel(null,padded).getItemCameraTransforms();JsonObject row=new JsonObject();row.addProperty("padded",padded);JsonArray contexts=new JsonArray();for(ItemCameraTransforms.TransformType type:ItemCameraTransforms.TransformType.values()){writeTransform(contexts,type,t.getTransform(type));}row.add("contexts",contexts);transforms.add(row);}
        JsonObject result=new JsonObject();result.add("cases",rows);result.add("transforms",transforms);String body=new Gson().toJson(result);String hash=sha(body);String expected="1c2a5955a4b3b529b0cd1431086d3f66bc250fbb7104f9c32731118c7306325a";if(!expected.isEmpty()&&!hash.equals(expected))throw new AssertionError("Actual Door item oracle changed "+hash);result.addProperty("golden",hash);Path output=Paths.get(System.getProperty("vandorlabs.doorItemReference","build/test-artifacts/door-item-forge.json"));Files.createDirectories(output.toAbsolutePath().getParent());Files.write(output,new Gson().toJson(result).getBytes(java.nio.charset.StandardCharsets.UTF_8));System.out.println("Actual Door item selector/transforms PASS cases="+rows.size()+" golden="+hash);
    }
    private static void setRoot(ItemStack stack,int kind){NBTTagCompound root=new NBTTagCompound();root.setTag("SpaceDoorSettings",kind==1?new NBTTagCompound():kind==2?new NBTTagString("invalid"):new NBTTagInt(29));stack.setTagCompound(root);}
    private static void row(JsonArray rows,java.lang.reflect.Method selector,ItemStack stack,boolean large)throws Exception{JsonObject row=new JsonObject();row.addProperty("large",large);row.addProperty("input",stack.hasTagCompound()?stack.getTagCompound().toString():"{}");row.addProperty("model",selector.invoke(null,stack).toString());rows.add(row);}
    private static void writeTransform(JsonArray out,ItemCameraTransforms.TransformType type,ItemTransformVec3f t){JsonObject row=new JsonObject();row.addProperty("context",type.name());row.add("rotation",vec(t.rotation));row.add("translation",vec(t.translation));row.add("scale",vec(t.scale));out.add(row);}
    private static JsonArray vec(org.lwjgl.util.vector.Vector3f v){JsonArray a=new JsonArray();a.add(v.x);a.add(v.y);a.add(v.z);return a;}
    private static String sha(String text)throws Exception{StringBuilder out=new StringBuilder();for(byte b:java.security.MessageDigest.getInstance("SHA-256").digest(text.getBytes(java.nio.charset.StandardCharsets.UTF_8)))out.append(String.format("%02x",b&255));return out.toString();}
}
