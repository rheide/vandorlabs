package com.vandorlabs.neoforge.legacylanding;
import com.google.gson.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import net.minecraft.world.phys.AABB;
/** Immutable authored collision cuboids. Client resource packs do not redefine server collision. */
final class NativeLegacyAssets {
 private static final Map<NativeLegacyLandingKind,List<List<List<AABB>>>> ALL=load();
 static List<List<AABB>> groups(NativeLegacyLandingKind kind,int size){if(size<0||size>4)throw new IllegalArgumentException("Legacy model size");return ALL.get(kind).get(Math.min(3,size));}
 private static Map<NativeLegacyLandingKind,List<List<List<AABB>>>> load(){try(var stream=NativeLegacyAssets.class.getResourceAsStream("/assets/vandorlabs/data/native_legacy_landing_geometry.json")){if(stream==null)throw new IllegalStateException("Missing authored legacy geometry");var raw=stream.readNBytes(2*1024*1024+1);if(raw.length>2*1024*1024)throw new IllegalStateException("Legacy geometry limit");var root=JsonParser.parseString(new String(raw,StandardCharsets.UTF_8)).getAsJsonObject();var result=new EnumMap<NativeLegacyLandingKind,List<List<List<AABB>>>>(NativeLegacyLandingKind.class);for(var kind:NativeLegacyLandingKind.values()){var sizes=root.getAsJsonArray(kind.id);if(sizes.size()!=4)throw new IllegalStateException("Legacy size inventory");var rows=new ArrayList<List<List<AABB>>>();for(var size:sizes){var groups=size.getAsJsonArray();if(groups.size()!=3)throw new IllegalStateException("Legacy group inventory");var parts=new ArrayList<List<AABB>>();for(var group:groups){var entries=group.getAsJsonArray();if(entries.size()>256)throw new IllegalStateException("Legacy collider limit");var boxes=new ArrayList<AABB>();for(var entry:entries){var values=entry.getAsJsonArray();if(values.size()!=6)throw new IllegalStateException("Legacy collider dimensions");double[] v=new double[6];for(int i=0;i<6;i++){v[i]=values.get(i).getAsDouble();if(!Double.isFinite(v[i])||Math.abs(v[i])>8)throw new IllegalStateException("Legacy collider bounds");}if(v[0]>v[3]||v[1]>v[4]||v[2]>v[5])throw new IllegalStateException("Reversed legacy collider");boxes.add(new AABB(v[0],v[1],v[2],v[3],v[4],v[5]));}parts.add(List.copyOf(boxes));}rows.add(List.copyOf(parts));}result.put(kind,List.copyOf(rows));}return Map.copyOf(result);}catch(IOException failure){throw new IllegalStateException("Cannot read authored legacy geometry",failure);}}
 private NativeLegacyAssets(){}
}
