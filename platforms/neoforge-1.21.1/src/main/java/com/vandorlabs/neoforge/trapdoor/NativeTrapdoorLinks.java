package com.vandorlabs.neoforge.trapdoor;

import com.vandorlabs.persistence.LegacyBlockPosition;
import java.util.List;
import java.util.ArrayList;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.*;

/** Saved world relations. Released coordinates use a different packed-long layout from modern BlockPos. */
public record NativeTrapdoorLinks(BlockPos partner,BlockPos square,List<BlockPos> assembly,double hinge,double travel,
                                  Direction groupFacing,boolean reverse,boolean inverted,boolean channelSignal,
                                  boolean powerKnown,boolean lastPower) {
    private static final String NATIVE="NativeTrapdoorLinks";
    public NativeTrapdoorLinks {
        partner=partner==null?null:partner.immutable();square=square==null?null:square.immutable();
        assembly=List.copyOf(assembly);if(assembly.size()>64||groupFacing==null||groupFacing.getAxis().isVertical())throw new IllegalArgumentException("Trapdoor relations");
    }
    public static BlockPos releasedPosition(long value){return new BlockPos(LegacyBlockPosition.x(value),LegacyBlockPosition.y(value),LegacyBlockPosition.z(value));}
    public static long releasedPosition(BlockPos pos){return LegacyBlockPosition.pack(pos.getX(),pos.getY(),pos.getZ());}
    private static BlockPos position(CompoundTag tag,String key,boolean modern){return !tag.contains(key,Tag.TAG_LONG)?null:modern?BlockPos.of(tag.getLong(key)):releasedPosition(tag.getLong(key));}
    public static NativeTrapdoorLinks read(CompoundTag tag){
        if(tag.contains(NATIVE)&&(!tag.contains(NATIVE,Tag.TAG_INT)||tag.getInt(NATIVE)!=1))throw new IllegalArgumentException("Future trapdoor coordinate schema");boolean modern=tag.getInt(NATIVE)==1;var members=tag.getList("TrapdoorAssembly",Tag.TAG_LONG);List<BlockPos> cells=new ArrayList<>();
        if(members.size()<=64)for(Tag member:members){long packed=((LongTag)member).getAsLong();cells.add(modern?BlockPos.of(packed):releasedPosition(packed));}
        return new NativeTrapdoorLinks(position(tag,"TrapdoorPartner",modern),position(tag,"TrapdoorSquare",modern),cells,
            Math.max(-8,Math.min(8,tag.getDouble("TrapdoorHinge"))),Math.max(15D/16,Math.min(8,tag.getDouble("TrapdoorTravel"))),
            Direction.from2DDataValue(Math.abs(tag.getInt("DiagonalGroupFacing")%4)),tag.getBoolean("DiagonalReverse"),tag.getBoolean("DiagonalInverted"),
            tag.getBoolean("ChannelSignal"),tag.getBoolean("TrapdoorPowerKnown"),tag.getBoolean("TrapdoorLastPower"));
    }
    public void write(CompoundTag tag){
        tag.putInt(NATIVE,1);if(partner==null)tag.remove("TrapdoorPartner");else tag.putLong("TrapdoorPartner",partner.asLong());
        if(square==null)tag.remove("TrapdoorSquare");else tag.putLong("TrapdoorSquare",square.asLong());
        var members=new ListTag();for(BlockPos cell:assembly)members.add(LongTag.valueOf(cell.asLong()));tag.put("TrapdoorAssembly",members);
        tag.putDouble("TrapdoorHinge",hinge);tag.putDouble("TrapdoorTravel",travel);tag.putInt("DiagonalGroupFacing",groupFacing.get2DDataValue());
        tag.putBoolean("DiagonalReverse",reverse);tag.putBoolean("DiagonalInverted",inverted);tag.putBoolean("ChannelSignal",channelSignal);
        tag.putBoolean("TrapdoorPowerKnown",powerKnown);tag.putBoolean("TrapdoorLastPower",lastPower);
    }
}
