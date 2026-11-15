package com.vandorlabs.client.constructors;

import com.vandorlabs.blocks.BlockConstructorCore;
import com.vandorlabs.gear.*;
import com.vandorlabs.tiles.TileEntityConstructor;
import com.vandorlabs.vehicle.*;
import java.lang.ref.WeakReference;
import java.util.*;
import net.minecraft.block.Block;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/** Client cosmetic ownership only: bounded logical machine poses, never snapshot or gameplay data. */
public final class CoreArmVisualIdentity {
    private static final Map<GearTileEntity,State> ARMS=new WeakHashMap<>();
    private static final Map<World,State> VIEWS=new WeakHashMap<>();
    private static final int MAX_ARMS=128,MAX_CORES=2048;
    private static final class Entry {final Block block;MachineVisualMotion.Pose pose;Entry(Block block,MachineVisualMotion.Pose pose){this.block=block;this.pose=pose;}}
    private static final class State {WeakReference<ArmPayload> header;final Map<BlockPos,Entry> cores=new HashMap<>();State(ArmPayload payload){header=new WeakReference<>(payload);}}
    private static void bound(){int count=0;for(State state:ARMS.values())count+=state.cores.size();Iterator<Map.Entry<GearTileEntity,State>> it=ARMS.entrySet().iterator();while((ARMS.size()>MAX_ARMS||count>MAX_CORES)&&it.hasNext()){State removed=it.next().getValue();count-=removed.cores.size();it.remove();for(Iterator<State> views=VIEWS.values().iterator();views.hasNext();)if(views.next()==removed)views.remove();}}
    public static void bind(GearTileEntity arm,ArmPayload payload,VehicleWorld view){
        State state=ARMS.get(arm);if(state==null||state.header.get()!=payload){state=new State(payload);ARMS.put(arm,state);}VIEWS.put(view,state);
        for(int i=0;i<payload.blocks.cells.size();i++){
            VehicleStructure.Cell cell=payload.blocks.cells.get(i);if(!(cell.state.getBlock() instanceof BlockConstructorCore)||!(view.tiles.get(cell.pos) instanceof TileEntityConstructor))continue;
            Entry entry=state.cores.get(cell.pos);if(entry==null||entry.block!=cell.state.getBlock()){entry=new Entry(cell.state.getBlock(),MachineVisualMotion.takeObserved(arm.getWorld(),payload.placedCellPosition(arm,i)));state.cores.put(cell.pos,entry);}
            MachineVisualMotion.releaseObserved(arm.getWorld(),payload.placedCellPosition(arm,i),entry.pose);
            MachineVisualMotion.installObserved(view,cell.pos,entry.pose);
        }bound();
    }
    /** Exact incoming header transition; unrelated/replaced roots cannot borrow another arm's record. */
    public static void changed(GearTileEntity arm,ArmPayload previous){
        State state=ARMS.get(arm);if(state==null)return;ArmPayload next=arm.payload();
        if(state.header.get()!=previous||next==null){ARMS.remove(arm);return;}
        Map<BlockPos,Entry> retained=new HashMap<>();
        for(int i=0;i<next.blocks.cells.size();i++){
            VehicleStructure.Cell cell=next.blocks.cells.get(i);Entry entry=state.cores.get(cell.pos);
            if(entry==null||entry.block!=cell.state.getBlock())continue;retained.put(cell.pos,entry);
            if(next.materialized())MachineVisualMotion.installObserved(arm.getWorld(),next.placedCellPosition(arm,i),entry.pose);
        }
        state.cores.clear();state.cores.putAll(retained);state.header=new WeakReference<>(next);
    }
    static MachineVisualMotion.Pose retained(TileEntityConstructor tile){State state=VIEWS.get(tile.getWorld());Entry entry=state==null?null:state.cores.get(tile.getPos());return entry==null?null:entry.pose;}
    static void observed(TileEntityConstructor tile,MachineVisualMotion.Pose pose){State state=VIEWS.get(tile.getWorld());Entry entry=state==null?null:state.cores.get(tile.getPos());if(entry!=null&&entry.pose==null)entry.pose=pose;}
    static void clear(World world){for(Iterator<Map.Entry<GearTileEntity,State>> it=ARMS.entrySet().iterator();it.hasNext();)if(it.next().getKey().getWorld()==world)it.remove();VIEWS.remove(world);}
    private CoreArmVisualIdentity(){}
}
