package com.vandorlabs.neoforge.ramp;

import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.*;

/** Exact save-time physical support evidence. No actor/inventory snapshot and no initial
 * From/To authority survives in this stamp. */
final class NativeRampSupportStamp {
 static final String KEY="NativeRampSupport";
 private static final Set<String> KEYS=Set.of("Schema","Actor","Type","Dimension","Operation","Owner","Root","Recipe","Revision","Pose","Distance","Position","Body");
 final UUID actor,operation,owner;final String type,dimension;final BlockPos root;final byte[] recipe;
 final long revision;final double pose,distance;final Vec3 position;final AABB body;
 NativeRampSupportStamp(UUID actor,String type,String dimension,UUID operation,UUID owner,BlockPos root,byte[] recipe,long revision,double pose,double distance,Vec3 position,AABB body){
  this.actor=Objects.requireNonNull(actor);this.operation=Objects.requireNonNull(operation);this.owner=Objects.requireNonNull(owner);this.type=resource(type);this.dimension=resource(dimension);this.root=Objects.requireNonNull(root).immutable();
  if(recipe.length!=32||revision<0||!Double.isFinite(pose)||pose<0||pose>1||!Double.isFinite(distance)||Math.abs(distance)>1e12)throw new IllegalArgumentException("Ramp support stamp bounds");this.recipe=recipe.clone();this.revision=revision;this.pose=pose;this.distance=distance;this.position=Objects.requireNonNull(position);this.body=Objects.requireNonNull(body);
  for(double value:new double[]{position.x,position.y,position.z,body.minX,body.minY,body.minZ,body.maxX,body.maxY,body.maxZ})if(!Double.isFinite(value)||Math.abs(value)>30000128)throw new IllegalArgumentException("Ramp support finite position/body");
  if(body.getXsize()<=0||body.getYsize()<=0||body.getZsize()<=0||body.getXsize()>64||body.getYsize()>64||body.getZsize()>64||body.minY!=position.y||Math.abs((body.minX+body.maxX)*.5-position.x)>1e-7||Math.abs((body.minZ+body.maxZ)*.5-position.z)>1e-7||Math.abs(position.x-root.getX())>128||Math.abs(position.y-root.getY())>128||Math.abs(position.z-root.getZ())>128)throw new IllegalArgumentException("Ramp support physical shape/domain");
 }
 CompoundTag write(){var tag=new CompoundTag();tag.putInt("Schema",1);tag.putUUID("Actor",actor);tag.putString("Type",type);tag.putString("Dimension",dimension);tag.putUUID("Operation",operation);tag.putUUID("Owner",owner);tag.putIntArray("Root",new int[]{root.getX(),root.getY(),root.getZ()});tag.putByteArray("Recipe",recipe.clone());tag.putLong("Revision",revision);tag.putDouble("Pose",pose);tag.putDouble("Distance",distance);tag.put("Position",numbers(position.x,position.y,position.z));tag.put("Body",numbers(body.minX,body.minY,body.minZ,body.maxX,body.maxY,body.maxZ));return tag;}
 static NativeRampSupportStamp read(CompoundTag tag){
  if(tag.sizeInBytes()>4096||!tag.getAllKeys().equals(KEYS)||!tag.contains("Schema",Tag.TAG_INT)||tag.getInt("Schema")!=1||!tag.hasUUID("Actor")||!tag.hasUUID("Operation")||!tag.hasUUID("Owner")||!tag.contains("Type",Tag.TAG_STRING)||!tag.contains("Dimension",Tag.TAG_STRING)||!tag.contains("Root",Tag.TAG_INT_ARRAY)||!tag.contains("Recipe",Tag.TAG_BYTE_ARRAY)||!tag.contains("Revision",Tag.TAG_LONG)||!tag.contains("Pose",Tag.TAG_DOUBLE)||!tag.contains("Distance",Tag.TAG_DOUBLE))throw new IllegalArgumentException("Unknown Ramp support stamp schema");
  var root=tag.getIntArray("Root");if(root.length!=3)throw new IllegalArgumentException("Ramp support root coordinate shape");var p=values(tag,"Position",3);var b=values(tag,"Body",6);if(b[3]<=b[0]||b[4]<=b[1]||b[5]<=b[2])throw new IllegalArgumentException("Reversed Ramp support body");var value=new NativeRampSupportStamp(tag.getUUID("Actor"),tag.getString("Type"),tag.getString("Dimension"),tag.getUUID("Operation"),tag.getUUID("Owner"),new BlockPos(root[0],root[1],root[2]),tag.getByteArray("Recipe"),tag.getLong("Revision"),tag.getDouble("Pose"),tag.getDouble("Distance"),new Vec3(p[0],p[1],p[2]),new AABB(b[0],b[1],b[2],b[3],b[4],b[5]));if(!value.write().equals(tag))throw new IllegalArgumentException("Noncanonical Ramp support stamp");return value;
 }
 private static String resource(String text){var id=text==null||text.length()>256?null:ResourceLocation.tryParse(text);if(id==null||!id.toString().equals(text))throw new IllegalArgumentException("Ramp support resource identity");return text;}
 private static ListTag numbers(double... values){var list=new ListTag();for(double value:values)list.add(DoubleTag.valueOf(value));return list;}
 private static double[] values(CompoundTag tag,String key,int count){if(!tag.contains(key,Tag.TAG_LIST))throw new IllegalArgumentException("Ramp support vector type");var list=tag.getList(key,Tag.TAG_DOUBLE);if(list.size()!=count||((ListTag)tag.get(key)).size()!=count)throw new IllegalArgumentException("Ramp support vector shape");var values=new double[count];for(int i=0;i<count;i++)values[i]=list.getDouble(i);return values;}
}
