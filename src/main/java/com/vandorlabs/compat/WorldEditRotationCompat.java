package com.vandorlabs.compat;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.vandorlabs.VandorLabs;
import com.vandorlabs.blocks.BlockPropulsionLight;
import com.vandorlabs.blocks.BlockTrianglePropulsionLight;
import com.vandorlabs.blocks.BlockVandorDoor;
import net.minecraft.block.Block;
import net.minecraft.util.EnumFacing;
import net.minecraftforge.fml.common.Loader;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.LinkedHashMap;
import java.util.HashMap;
import java.util.Map;

/** Supplies WorldEdit 6's metadata transformer with directions for our mod blocks.
 * WorldEdit keeps a vanilla-only registry and never calls Block.withRotation(). */
public final class WorldEditRotationCompat {
    private WorldEditRotationCompat() { }
    private static boolean installed;

    public static void install() {
        if (installed || !Loader.isModLoaded("worldedit")) return;
        try {
            register();
            installed=true;
        } catch (ReflectiveOperationException | RuntimeException e) {
            VandorLabs.logger.error("Could not register Vandor Labs orientations with WorldEdit",e);
        }
    }

    @SuppressWarnings("unchecked")
    private static void register() throws ReflectiveOperationException {
        Class<?> registryClass=Class.forName("com.sk89q.worldedit.world.registry.BundledBlockData");
        Class<?> entryClass=Class.forName("com.sk89q.worldedit.world.registry.BundledBlockData$BlockEntry");
        Class<?> vectorClass=Class.forName("com.sk89q.worldedit.Vector");
        Class<?> adapterClass=Class.forName("com.sk89q.worldedit.util.gson.VectorAdapter");
        Gson gson=new GsonBuilder().registerTypeAdapter(vectorClass,adapterClass.newInstance()).create();
        Object registry=registryClass.getMethod("getInstance").invoke(null);
        Field legacyField=field(registryClass,"legacyMap"),idField=field(registryClass,"idMap");
        Map<Integer,Object> legacy=(Map<Integer,Object>)legacyField.get(registry);
        Map<String,Object> ids=(Map<String,Object>)idField.get(registry);
        Map<String,int[]> triangles=triangleFamilies();
        int count=0;
        for (Block block:Block.REGISTRY) {
            if (!(block instanceof BlockVandorDoor) && !(block instanceof BlockPropulsionLight)) continue;
            String id=block.getRegistryName().toString();
            int numericId=Block.getIdFromBlock(block);
            Object entry=gson.fromJson(entryJson(id,numericId,block instanceof BlockVandorDoor),entryClass);
            Method post=entryClass.getDeclaredMethod("postDeserialization"); post.setAccessible(true); post.invoke(entry);
            if (block instanceof BlockVandorDoor) guardUpperDoor(entry,entryClass);
            if (block instanceof BlockTrianglePropulsionLight) {
                String family=triangleFamily(block.getRegistryName().getResourcePath());
                addTriangleCorner(entry,entryClass,vectorClass,triangles.get(family));
            }
            legacy.put(numericId,entry);
            ids.put(id,entry);
            count++;
        }
        VandorLabs.logger.info("Registered {} door and propulsion orientations with WorldEdit",count);
    }

    private static JsonObject entryJson(String id,int numericId,boolean door) {
        JsonObject entry=new JsonObject();
        entry.addProperty("id",id); entry.addProperty("legacyId",numericId);
        JsonObject state=new JsonObject(); state.addProperty("dataMask",door?3:7);
        JsonObject values=new JsonObject();
        for (EnumFacing facing:EnumFacing.values()) {
            if (door && !facing.getAxis().isHorizontal()) continue;
            JsonObject value=new JsonObject();
            value.addProperty("data",door?facing.getHorizontalIndex():facing.getIndex());
            JsonArray direction=new JsonArray();
            direction.add(facing.getFrontOffsetX()); direction.add(facing.getFrontOffsetY());
            direction.add(facing.getFrontOffsetZ());
            value.add("direction",direction); values.add(facing.getName(),value);
        }
        state.add("values",values);
        JsonObject states=new JsonObject(); states.add("facing",state); entry.add("states",states);
        return entry;
    }

    /** The upper door meta bits are hinge/power, not direction. */
    @SuppressWarnings("unchecked")
    private static void guardUpperDoor(Object entry,Class<?> entryClass) throws ReflectiveOperationException {
        Field statesField=field(entryClass,"states");
        Map<String,Object> states=(Map<String,Object>)statesField.get(entry);
        Object facing=states.get("facing");
        Class<?> stateClass=Class.forName("com.sk89q.worldedit.world.registry.State");
        Class<?> baseClass=Class.forName("com.sk89q.worldedit.blocks.BaseBlock");
        Method getData=baseClass.getMethod("getData");
        Object guarded=Proxy.newProxyInstance(stateClass.getClassLoader(),new Class<?>[]{stateClass},(proxy,method,args)->{
            if (method.getName().equals("getValue") && (((Integer)getData.invoke(args[0]))&8)!=0) return null;
            if (method.getDeclaringClass()==Object.class) return method.invoke(facing,args);
            return method.invoke(facing,args);
        });
        Map<String,Object> replacement=new LinkedHashMap<>(); replacement.put("facing",guarded);
        statesField.set(entry,replacement);
    }

    private static String triangleFamily(String id) {
        for (String suffix:new String[]{"_bottom_right","_top_right","_top_left"})
            if (id.endsWith(suffix)) return id.substring(0,id.length()-suffix.length());
        return id;
    }
    private static int corner(String id) {
        if (id.endsWith("_bottom_right")) return 1;
        if (id.endsWith("_top_right")) return 2;
        if (id.endsWith("_top_left")) return 3;
        return 0;
    }
    private static Map<String,int[]> triangleFamilies() {
        Map<String,int[]> result=new HashMap<>();
        for (Block block:Block.REGISTRY) if (block instanceof BlockTrianglePropulsionLight) {
            String id=block.getRegistryName().getResourcePath();
            int[] family=result.computeIfAbsent(triangleFamily(id),ignored->new int[4]);
            family[corner(id)]=Block.getIdFromBlock(block);
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    private static void addTriangleCorner(Object entry,Class<?> entryClass,Class<?> vectorClass,int[] family)
            throws ReflectiveOperationException {
        if (family==null) throw new IllegalStateException("Triangle family is incomplete");
        for (int id:family) if (id==0) throw new IllegalStateException("Triangle variant is missing");
        Class<?> stateClass=Class.forName("com.sk89q.worldedit.world.registry.State");
        Class<?> valueClass=Class.forName("com.sk89q.worldedit.world.registry.StateValue");
        Class<?> baseClass=Class.forName("com.sk89q.worldedit.blocks.BaseBlock");
        Method getId=baseClass.getMethod("getId"),getData=baseClass.getMethod("getData"),setId=baseClass.getMethod("setId",int.class);
        Constructor<?> vector=vectorClass.getConstructor(int.class,int.class,int.class);
        Map<String,Object> values=new LinkedHashMap<>();
        Object[][] byFacing=new Object[6][4];
        for (EnumFacing face:EnumFacing.values()) for (int corner=0;corner<4;corner++) {
            final int choice=corner;
            int[] offset=cornerVector(face,corner);
            Object direction=vector.newInstance(offset[0],offset[1],offset[2]);
            Object value=Proxy.newProxyInstance(valueClass.getClassLoader(),new Class<?>[]{valueClass},(proxy,method,args)->{
                switch (method.getName()) {
                    case "getDirection": return direction;
                    case "set": setId.invoke(args[0],family[choice]); return true;
                    case "isSet": return ((Integer)getId.invoke(args[0]))==family[choice];
                    case "toString": return face+" corner "+choice;
                    case "hashCode": return System.identityHashCode(proxy);
                    case "equals": return proxy==args[0];
                    default: throw new UnsupportedOperationException(method.getName());
                }
            });
            byFacing[face.getIndex()][corner]=value;
            values.put(face.getName()+corner,value);
        }
        Object state=Proxy.newProxyInstance(stateClass.getClassLoader(),new Class<?>[]{stateClass},(proxy,method,args)->{
            switch (method.getName()) {
                case "hasDirection": return true;
                case "valueMap": return values;
                case "getValue":
                    int id=(Integer)getId.invoke(args[0]);
                    int found=-1; for (int n=0;n<4;n++) if (family[n]==id) found=n;
                    if (found<0) return null;
                    int index=((Integer)getData.invoke(args[0]))&7;
                    return index<6?byFacing[index][found]:null;
                case "toString": return "triangle corner";
                case "hashCode": return System.identityHashCode(proxy);
                case "equals": return proxy==args[0];
                default: throw new UnsupportedOperationException(method.getName());
            }
        });
        Field statesField=field(entryClass,"states");
        Map<String,Object> existing=(Map<String,Object>)statesField.get(entry);
        Map<String,Object> replacement=new LinkedHashMap<>();
        replacement.put("corner",state); replacement.putAll(existing);
        statesField.set(entry,replacement);
    }

    private static int[] cornerVector(EnumFacing face,int corner) {
        int right=corner==1 || corner==2?1:-1;
        int up=corner>=2?1:-1;
        switch (face) {
            case NORTH: return new int[]{right,up,-10};
            case SOUTH: return new int[]{-right,up,10};
            case EAST: return new int[]{10,up,right};
            case WEST: return new int[]{-10,up,-right};
            case UP: return new int[]{right,10,up};
            case DOWN: return new int[]{right,-10,-up};
            default: throw new AssertionError(face);
        }
    }

    private static Field field(Class<?> owner,String name) throws ReflectiveOperationException {
        Field field=owner.getDeclaredField(name); field.setAccessible(true); return field;
    }
}
