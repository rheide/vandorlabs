package com.vandorlabs.neoforge.builders;

import com.vandorlabs.machines.CoreLayout;
import com.vandorlabs.neoforge.transfer.NativeRawCell;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;

/** Formed production frames whose complete runtime and slot ownership is empty. */
public final class NativeBuilderTransfer {
    private static final Set<String> CORE=Set.of("id","x","y","z","BuilderKey","ConstructorCoreX","ConstructorCoreZ",
        "Activation","Channels","Inventory","BatterySequences","Synthesis");
    private static final Set<String> WORK=Set.of("Selected","Building","Work","Active","Connected","Mode");

    public static Set<BlockPos> validate(List<NativeRawCell> cells){
        var roots=new ArrayList<NativeRawCell>();
        var parts=new HashMap<BlockPos,NativeRawCell>();
        for(var cell:cells){
            if(cell.state.getBlock() instanceof NativeBuilderCoreBlock)roots.add(cell);
            if(cell.state.getBlock() instanceof NativeBuilderPartBlock)parts.put(cell.pos,cell);
        }
        var matter=new HashSet<BlockPos>();
        var keys=new HashSet<UUID>();
        for(var root:roots){
            var design=((NativeBuilderCoreBlock)root.state.getBlock()).design;
            if(!root.state.getValue(NativeBuilderCoreBlock.FORMED)||design.drive()!=null)
                throw new IllegalArgumentException("Capture a complete formed production builder");
            var raw=root.tile();
            requireIdleData(raw);
            var owner=raw.getUUID("BuilderKey");
            if(!keys.add(owner))throw new IllegalArgumentException("Duplicate builder owner");
            var layout=new CoreLayout(design.width(),design.height(),design.depth(),
                root.state.getValue(NativeBuilderCoreBlock.FACING).get3DDataValue(),
                raw.getInt("ConstructorCoreX"),raw.getInt("ConstructorCoreZ"));
            if(!layout.canonicalCorner())throw new IllegalArgumentException("Unknown builder frame corner");
            for(int x=0;x<design.width();x++)for(int y=0;y<design.height();y++)for(int z=0;z<design.depth();z++){
                var pos=root.pos.offset(layout.x(x,z),y,layout.z(x,z));
                if(pos.equals(root.pos))continue;
                var member=parts.remove(pos);
                if(member==null||!member.hasTile())throw new IllegalArgumentException("Capture the entire builder frame");
                NativeBuilderTransactions.requirePart(member.tile(),root.pos.subtract(pos),owner,true);
                if(layout.frame(x,y,z))matter.add(pos);
            }
        }
        if(!parts.isEmpty())throw new IllegalArgumentException("Capture each builder part with its own frame core");
        return matter;
    }

    public static void requireIdleData(CompoundTag raw){
        if(raw==null||!raw.hasUUID("BuilderKey")||!raw.contains("ConstructorCoreX",Tag.TAG_INT)
            ||!raw.contains("ConstructorCoreZ",Tag.TAG_INT)||!raw.contains("Inventory",Tag.TAG_COMPOUND)
            ||!raw.contains("Synthesis",Tag.TAG_COMPOUND)||!CORE.containsAll(NativeBuilderData.normalizedSnapshot(raw).getAllKeys()))
            throw new IllegalArgumentException("Builder data contains unsupported ownership");
        var inventory=raw.getCompound("Inventory");
        var work=raw.getCompound("Synthesis");
        if(!inventory.getAllKeys().equals(Set.of("Items"))||!(inventory.get("Items") instanceof ListTag items)||!items.isEmpty()
            ||!WORK.containsAll(work.getAllKeys())||work.getBoolean("Active")||work.getBoolean("Connected")||work.getInt("Work")!=0)
            throw new IllegalArgumentException("Empty and disconnect the idle builder before transferring");
    }

    public static boolean builder(BlockState state){
        return state.getBlock() instanceof NativeBuilderCoreBlock||state.getBlock() instanceof NativeBuilderPartBlock;
    }

    public static void requireIdle(ServerLevel world,BlockPos pos){
        if(!(world.getBlockEntity(pos) instanceof NativeBuilderEntity root)||root.inert()||root.pendingFormation()
            ||root.processing()||!root.isEmpty()||root.connected())
            throw new IllegalArgumentException("Empty and disconnect the idle builder before transferring");
    }

    private NativeBuilderTransfer(){}
}
