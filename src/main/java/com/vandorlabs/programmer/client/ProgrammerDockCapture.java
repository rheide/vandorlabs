package com.vandorlabs.programmer.client;
import com.google.gson.*;
import com.vandorlabs.programmer.ProgrammerTile;
import java.io.File;
import java.lang.reflect.Field;
import java.nio.FloatBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderItem;
import net.minecraft.client.renderer.block.model.ItemCameraTransforms;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumFacing;
/** Focused-client oracle: observe the actual released dock GL frame, then delegate the real item draw. */
@net.minecraftforge.fml.relauncher.SideOnly(net.minecraftforge.fml.relauncher.Side.CLIENT)
final class ProgrammerDockCapture implements AutoCloseable {
 private static ProgrammerDockCapture active;
 private final Minecraft mc;private final File output;private final RenderItem original;private final Field field;
 private final JsonArray cases=new JsonArray();private JsonObject profile;
 ProgrammerDockCapture(Minecraft mc,File output)throws Exception{this.mc=mc;this.output=output;original=mc.getRenderItem();Field selected=null;for(Field candidate:Minecraft.class.getDeclaredFields())if(candidate.getType()==RenderItem.class&&!java.lang.reflect.Modifier.isStatic(candidate.getModifiers())){if(selected!=null)throw new IllegalStateException("Ambiguous item renderer field");selected=candidate;}if(selected==null)throw new IllegalStateException("Missing item renderer field");field=selected;field.setAccessible(true);RenderItem probe=new RenderItem(mc.getTextureManager(),original.getItemModelMesher().getModelManager(),mc.getItemColors()){@Override public void renderItem(ItemStack stack,ItemCameraTransforms.TransformType context){if(profile!=null&&context==ItemCameraTransforms.TransformType.FIXED){if(profile.has("matrix"))throw new IllegalStateException("Multiple item draws in isolated dock profile");profile.add("matrix",matrix());cases.add(profile);}original.renderItem(stack,context);}};field.set(mc,probe);active=this;}
 static void select(EnumFacing facing,boolean chip,int slot){if(active==null)return;if(facing==null){active.profile=null;return;}JsonObject row=new JsonObject();row.addProperty("facing",facing.name());row.addProperty("chip",chip);row.addProperty("slot",slot);active.profile=row;}
 static void base(ProgrammerTile tile){if(active==null||active.profile==null)return;active.profile.add("base",matrix());JsonArray root=new JsonArray();root.add(tile.getPos().getX());root.add(tile.getPos().getY());root.add(tile.getPos().getZ());active.profile.add("root",root);}
 private static JsonArray matrix(){FloatBuffer buffer=org.lwjgl.BufferUtils.createFloatBuffer(16);org.lwjgl.opengl.GL11.glGetFloat(org.lwjgl.opengl.GL11.GL_MODELVIEW_MATRIX,buffer);JsonArray result=new JsonArray();for(int i=0;i<16;i++)result.add(Float.floatToRawIntBits(buffer.get(i)));return result;}
 @Override public void close()throws Exception{field.set(mc,original);active=null;if(cases.size()!=24)throw new IllegalStateException("Incomplete actual dock profiles: "+cases.size());String json=new Gson().toJson(cases);StringBuilder hash=new StringBuilder();for(byte b:java.security.MessageDigest.getInstance("SHA-256").digest(json.getBytes(StandardCharsets.UTF_8)))hash.append(String.format(Locale.ROOT,"%02x",b&255));JsonObject result=new JsonObject();result.addProperty("golden",hash.toString());result.add("cases",cases);Files.write(new File(output,"programmer-dock-gl-reference.json").toPath(),new Gson().toJson(result).getBytes(StandardCharsets.UTF_8));System.out.println("[vandorlabs][reprolab] programmer-dock-gl PASS actual-production-renderer profiles=24 golden="+hash);}
}
