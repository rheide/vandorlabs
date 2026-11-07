package com.vandorlabs.vehicle;

import java.util.List;
import net.minecraft.nbt.*;

/** Entity-owned generation and pending-frame bookkeeping; no world access or ownership publication. */
final class VehicleBodyPoseState {
    interface Receiver {boolean apply(VehicleBodyPoseFrame frame,boolean localDriver);}
    private VehicleBodyPoseFrame committed=VehicleBodyPoseFrame.NONE,pending=VehicleBodyPoseFrame.NONE;
    private long highestEpoch;
    private NBTBase retained;
    private boolean applying;
    static VehicleBodyPoseState retain(NBTBase raw){
        if(raw==null)return null;VehicleBodyPoseState state=new VehicleBodyPoseState();state.retained=raw.copy();return state;
    }
    void readRetained(){
        if(retained==null)return;
        if(!(retained instanceof NBTTagCompound))throw new IllegalArgumentException("Invalid retained general body envelope type");
        VehicleBodyPoseFrame parsed=VehicleBodyPoseFrame.readNBT((NBTTagCompound)retained);
        committed=parsed;pending=parsed.reset()?VehicleBodyPoseFrame.NONE:parsed;highestEpoch=parsed.epoch;
    }
    void write(NBTTagCompound tag){
        if(retained!=null)tag.setTag(VehicleBodyPoseFrame.NBT_KEY,retained.copy());
        else if(committed.present())tag.setTag(VehicleBodyPoseFrame.NBT_KEY,committed.writeNBT());
        else tag.removeTag(VehicleBodyPoseFrame.NBT_KEY);
    }
    VehicleBodyPoseFrame current(){return committed;}
    boolean ready(){return !pending.present();}
    boolean usesGeneral(){return committed.layout!=0||pending.layout!=0;}
    VehicleBodyPoseFrame commit(long layout,List<VehicleBodyPoseFrame.Body> bodies){
        if(layout==0&&bodies!=null&&bodies.isEmpty()&&!committed.present())return VehicleBodyPoseFrame.NONE;
        long epoch=committed.present()&&committed.layout==layout?committed.epoch:Math.addExact(committed.epoch,1L);
        VehicleBodyPoseFrame prepared=new VehicleBodyPoseFrame(epoch,layout,bodies);
        committed=prepared;pending=VehicleBodyPoseFrame.NONE;highestEpoch=epoch;retained=null;return prepared;
    }
    /** Called only after the enclosing STATE tick/sequence has been admitted. */
    boolean receive(VehicleBodyPoseFrame frame,Receiver receiver,boolean localDriver){
        if(frame==null||!frame.present()||frame.epoch<highestEpoch)return false;
        VehicleBodyPoseFrame known=pending.present()?pending:committed;
        if(frame.epoch==highestEpoch&&known.present()&&known.epoch==highestEpoch&&frame.layout!=known.layout)return false;
        highestEpoch=frame.epoch;pending=frame;return applyPending(receiver,localDriver);
    }
    boolean applyPending(Receiver receiver,boolean localDriver){
        if(applying||!pending.present())return false;
        if(receiver==null){if(!pending.reset())return false;committed=pending;pending=VehicleBodyPoseFrame.NONE;retained=null;return true;}
        VehicleBodyPoseFrame frame=pending;applying=true;
        try{
            if(!receiver.apply(frame,localDriver))return false;
            committed=frame;if(pending==frame)pending=VehicleBodyPoseFrame.NONE;retained=null;return true;
        }finally{applying=false;}
    }
}
