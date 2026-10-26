package com.vandorlabs.gear;
import java.io.*;
import net.minecraft.nbt.*;
/** Both compressed bytes and expanded recovery allocations must fit before publication. */
public final class ArmJournalLimitsChecks {
    public static void main(String[] args)throws Exception{
        NBTTagCompound ordinary=record(1024);byte[] bytes=ArmTransactions.encodeForRecovery(ordinary);
        try(DataInputStream in=new DataInputStream(new java.util.zip.GZIPInputStream(new ByteArrayInputStream(bytes)))){if(!ordinary.equals(CompressedStreamTools.read(in,new NBTSizeTracker(16L*1024*1024))))throw new AssertionError("Exact journal round trip");}
        net.minecraft.init.Bootstrap.register();
        ArmPayload.validateSyncBudget(com.vandorlabs.vehicle.VehicleStructure.display(java.util.Collections.singletonList(new com.vandorlabs.vehicle.VehicleStructure.Cell(net.minecraft.util.math.BlockPos.ORIGIN,net.minecraft.init.Blocks.STONE.getDefaultState(),null))).encode());
        NBTTagCompound large=record(32768);NBTTagCompound original=large.copy();boolean rejected=false;
        try{ArmTransactions.encodeForRecovery(large);}catch(IOException expected){rejected=true;}
        if(!rejected||!large.equals(original))throw new AssertionError("Expanded journal must be refused without modifying its source");
        ByteArrayOutputStream raw=new ByteArrayOutputStream();CompressedStreamTools.write(large,new DataOutputStream(raw));rejected=false;try{ArmPayload.validateSyncBudget(raw.toByteArray());}catch(IOException expected){rejected=true;}if(!rejected)throw new AssertionError("Oversized client update must be refused before detaching");
        System.out.println("arm-journal-limits PASS 1024-cell round trip 32768-cell highly compressible recovery limit before publication original retained");
    }
    private static NBTTagCompound record(int count){NBTTagCompound record=new NBTTagCompound(),tile=new NBTTagCompound();NBTTagList before=new NBTTagList(),blocks=new NBTTagList();for(int i=0;i<count;i++){NBTTagCompound row=new NBTTagCompound(),state=new NBTTagCompound(),props=new NBTTagCompound(),cell=new NBTTagCompound();props.setString("variant","stone");state.setString("Name","minecraft:stone");state.setTag("Properties",props);row.setLong("Pos",i);row.setTag("State",state);before.appendTag(row);cell.setLong("Pos",i);cell.setInteger("State",0);blocks.appendTag(cell);}tile.setTag("Blocks",blocks);NBTTagCompound root=new NBTTagCompound();root.setTag("Tile",tile);before.appendTag(root);record.setTag("Before",before);return record;}
}
