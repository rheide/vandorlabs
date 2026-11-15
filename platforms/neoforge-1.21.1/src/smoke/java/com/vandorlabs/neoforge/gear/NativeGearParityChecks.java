package com.vandorlabs.neoforge.gear;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import com.vandorlabs.neoforge.VandorLabsBootstrap;

/** Actual installed housing factory plus strict old-record comparisons, with original opaque fields retained. */
public final class NativeGearParityChecks {
    private static void need(boolean ok,String why){if(!ok)throw new AssertionError("Legacy join parity: "+why);}
    public static void run(ServerLevel level){
        var pos=new BlockPos(16,110,0);level.getChunkAt(pos);var state=VandorLabsBootstrap.PROGRAMMABLE_BLOCK.get().defaultBlockState();level.setBlock(pos,state,18);
        try{
            var tile=level.getBlockEntity(pos);var ramp=com.vandorlabs.neoforge.ramp.NativeRampJournal.class.getDeclaredMethod("sameSnapshot",CompoundTag.class,CompoundTag.class);ramp.setAccessible(true);
            for(int parity:new int[]{1,2}){
                var original=tile.saveWithFullMetadata(level.registryAccess());original.putInt("TextureJoinDirection",5);original.putInt("TextureJoinParity",parity);var before=original.copy();var cell=new NativeGearSnapshot.Cell(pos,state,original);var staged=NativeGearSnapshot.stage(level,cell,false);var actual=staged.saveWithFullMetadata(level.registryAccess());
                need(actual.getInt("TextureJoinDirection")==5&&actual.getInt("TextureJoinParity")==0,"actual tile stage keeps direction/normalizes All");need(original.equals(before)&&cell.tile().equals(before),"authoritative before images unchanged");
                var opaque=new CompoundTag();opaque.putString("owner","external");opaque.putInt("TextureJoinParity",parity);var left=original.copy();var right=actual.copy();left.put("ForgeCaps",opaque.copy());right.put("ForgeCaps",opaque.copy());
                need(NativeGearSnapshot.same(left,right)&&(boolean)ramp.invoke(null,left,right),"only known own field canonicalized by gear/ramp strict comparisons");
                right.getCompound("ForgeCaps").putString("owner","changed");need(!NativeGearSnapshot.same(left,right)&&!(boolean)ramp.invoke(null,left,right),"opaque capability changes still refused");
                left.putString("id","foreign:machine");right=left.copy();right.putInt("TextureJoinParity",0);need(!NativeGearSnapshot.same(left,right),"foreign own fields remain exact");
            }
            System.out.println("[vandorlabs][join-parity-migration] PASS actual housing staging/old gear-ramp comparisons/original preimages/opaque exactness");
        }catch(ReflectiveOperationException failure){throw new IllegalStateException(failure);}finally{level.removeBlock(pos,false);}
    }
    private NativeGearParityChecks(){}
}
