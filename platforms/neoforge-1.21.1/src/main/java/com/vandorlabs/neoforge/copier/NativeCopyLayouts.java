package com.vandorlabs.neoforge.copier;

import java.util.*;
import com.vandorlabs.neoforge.surface.NativeSurfaceLayout;
import net.minecraft.core.*;
import net.minecraft.nbt.*;

/** Exact Source layout shape; unknown nested fields are retained by the controller's inert-load path. */
final class NativeCopyLayouts {
    static CompoundTag write(NativeSurfaceLayout layout){var tag=new CompoundTag();tag.putInt("Normal",layout.normal.get3DDataValue());var list=new ListTag();for(var pos:layout.cells)list.add(LongTag.valueOf(com.vandorlabs.persistence.LegacyBlockPosition.pack(pos.getX(),pos.getY(),pos.getZ())));tag.put("Cells",list);return tag;}
    static NativeSurfaceLayout read(CompoundTag tag,BlockPos root,boolean rectangle){if(!tag.getAllKeys().equals(Set.of("Normal","Cells"))||!tag.contains("Normal",Tag.TAG_INT)||tag.getInt("Normal")<0||tag.getInt("Normal")>5||!(tag.get("Cells") instanceof ListTag list)||list.isEmpty()||list.size()>1024||list.getElementType()!=Tag.TAG_LONG)throw new IllegalArgumentException("Saved Surface layout schema");var cells=new ArrayList<BlockPos>();for(Tag value:list){long packed=((LongTag)value).getAsLong();var pos=new BlockPos(com.vandorlabs.persistence.LegacyBlockPosition.x(packed),com.vandorlabs.persistence.LegacyBlockPosition.y(packed),com.vandorlabs.persistence.LegacyBlockPosition.z(packed));if(Math.abs((long)pos.getX()-root.getX())>64||Math.abs((long)pos.getY()-root.getY())>64||Math.abs((long)pos.getZ()-root.getZ())>64)throw new IllegalArgumentException("Saved Surface layout footprint");cells.add(pos);}return new NativeSurfaceLayout(cells,Direction.from3DDataValue(tag.getInt("Normal")),rectangle);}
    static UUID uuid(CompoundTag tag,String key,boolean required){if(tag.hasUUID(key))return tag.getUUID(key);if(tag.contains(key)||tag.contains(key+"Most")||tag.contains(key+"Least")){if(!tag.contains(key+"Most",Tag.TAG_LONG)||!tag.contains(key+"Least",Tag.TAG_LONG))throw new IllegalArgumentException("Saved controller UUID");return new UUID(tag.getLong(key+"Most"),tag.getLong(key+"Least"));}if(required)throw new IllegalArgumentException("Missing controller UUID");return null;}
    private NativeCopyLayouts(){}
}
