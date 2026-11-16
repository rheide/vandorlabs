package com.vandorlabs.neoforge.ramp;

import com.vandorlabs.ramp.RampLifecycle;
import java.util.Objects;
import java.util.function.Supplier;
import net.minecraft.server.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.network.PacketDistributor;

/** Owning-thread controller adapter. Geometry, ownership transfer and physical riders remain
 * in their prepared services. A pending disk operation only schedules another bounded poll. */
public final class NativeRampController {
    public enum Phase { IDLE, PREPARING, PUBLISHING, ACTIVE, RETURNING, RESTORING, HELD }
    private final NativeRampEntity owner;
    private final Supplier<BlockState> parts;
    private NativeRampJournal.Operation operation;
    private NativeRampFramePublisher.Frame frame;
    private NativeRampFrameCommand.Notices notices=new NativeRampFrameCommand.Notices(java.util.Set.of(),"");
    private RampLifecycle.Timeline timeline=new RampLifecycle.Timeline(0,0,0,0,0,0,60,false);
    private NativeRampSettings queuedChoices;
    private Phase phase=Phase.IDLE;
    private double requested,lastSignalTarget;
    private boolean signalKnown,error,waitingCanopy,resumed,stopPending;
    private String stoppedError="";
    private long revision;
    private NativeRampBeltRiders.State beltState;
    private boolean beltRequested,nearby,beltWaiting,controlsStoppedPublished;
    private String text="Retracted";
    private static final NativeRampRiders.Sync SYNC=PacketDistributor::sendToPlayer;

    NativeRampController(NativeRampEntity owner,Supplier<BlockState> parts){this.owner=Objects.requireNonNull(owner);this.parts=Objects.requireNonNull(parts);}
    public Phase phase(){return phase;}
    public boolean attached(){return operation!=null;}
    public double committedPose(){return timeline.observed;}
    public double committedBeltDistance(){return frame==null?0:frame.belt().observed();}
    NativeRampRuntime runtimeSnapshot(){
        if(!(owner.getLevel() instanceof ServerLevel)||operation==null||frame==null)return owner.loadedRuntime();if(!NativeRampJournal.startupActiveReceipt(operation)||phase==Phase.RESTORING)return null;
        var state=new NativeRampActiveState(operation.id(),NativeRampJournal.recipeIdentity(operation),frame.revision(),timeline,frame.cells(),phase==Phase.RETURNING,frame.belt());return new NativeRampRuntime(owner.ownerIdentity(),net.minecraft.core.registries.BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(owner.getType()),owner.getBlockState(),state,queuedChoices,requested,lastSignalTarget,signalKnown,phase==Phase.HELD,error,text);
    }
    void adopt(NativeRampJournal.Operation operation,NativeRampRuntime saved,NativeRampFramePublisher.Frame installedFrame){
        var world=level();if(!NativeRampRecoveryAdmission.active(world)||!installed(world)||this.operation!=null||!saved.frame.operation.equals(operation.id()))throw new IllegalStateException("Ramp controller adoption authority changed");this.operation=operation;frame=installedFrame;beltState=new NativeRampBeltRiders.State(operation.id());NativeRampActivity.watch(this);var old=saved.frame.timeline;timeline=new RampLifecycle.Timeline(Math.addExact(old.sequence,1),world.getGameTime(),world.getGameTime(),old.observed,old.target,old.observed,old.duration,old.moving&&old.observed!=old.target);revision=installedFrame.revision();queuedChoices=saved.queued;requested=saved.requested;lastSignalTarget=saved.signal;signalKnown=saved.signalKnown;beltRequested=owner.settings().escalator()!=0&&saved.signal>0;phase=saved.frame.returning?Phase.RETURNING:Phase.ACTIVE;text=saved.status;error=saved.error;waitingCanopy=false;stopPending=false;resumed=true;owner.adoptedRuntime();
    }
    public NativeRampMenu.Status status(){return new NativeRampMenu.Status(text,error,phase==Phase.PREPARING||phase==Phase.PUBLISHING||phase==Phase.RESTORING||waitingCanopy||stopPending,timeline.moving,revision);}
    private ServerLevel level(){if(!(owner.getLevel() instanceof ServerLevel server)||!server.getServer().isSameThread())throw new IllegalStateException("Ramp controller requires server thread");return server;}
    private boolean installed(ServerLevel level){var p=owner.getBlockPos();var chunk=level.getChunkSource().getChunkNow(p.getX()>>4,p.getZ()>>4);return !owner.inert()&&!owner.isRemoved()&&chunk!=null&&chunk.getBlockEntities().get(p)==owner;}
    private void changed(){owner.runtimeChanged(false);}
    private void status(String next,boolean failed){String message=next==null?"Ramp operation failed":next.substring(0,Math.min(256,next.length()));boolean notify=!text.equals(message)||error!=failed;text=message;error=failed;owner.runtimeChanged(notify);}
    private void schedule(){var world=level();if(installed(world)&&work()!=0)world.scheduleTick(owner.getBlockPos(),owner.getBlockState().getBlock(),1);}
    private int work(){return RampLifecycle.work(false,phase==Phase.PREPARING||phase==Phase.PUBLISHING||phase==Phase.RETURNING||phase==Phase.RESTORING||stopPending,timeline.moving,beltRunning(),waitingCanopy);}
    private NativeRampReceipt receipt(boolean admitted,boolean choices,boolean refused){return new NativeRampReceipt(admitted,choices,status().pending(),refused,status());}

    /** All UI/item/copy adapters use the same admitted choices and controller command path. */
    public NativeRampMenu.Outcome configure(ServerPlayer actor,NativeRampSettings choices){
        var world=level();if(!installed(world)||!com.vandorlabs.neoforge.NativeConfigurationAccess.canEditDialog(actor,owner))return receipt(false,false,true).outcome(owner.settings());
        if(operation!=null&&!Objects.equals(NativeRampAuthorization.actorId(owner),actor.getUUID()))return receipt(false,false,true).outcome(owner.settings());
        if(!NativeRampAuthorization.authorize(owner,actor))return receipt(false,false,true).outcome(owner.settings());
        var plan=NativeRampConfiguration.placement(owner.settings(),owner.scanFacing().get3DDataValue(),choices.itemChoices(owner.horizontalFacing().get3DDataValue()),true,true,true);
        if(!plan.valid()||plan.admission()==null||!plan.admission().accepted())return receipt(false,false,true).outcome(owner.settings());
        var admitted=plan.result().withAnalogDeployment(choices.analogDeployment());
        if(operation!=null&&!admitted.equals(owner.settings())){
            queuedChoices=admitted;requested=0;if(frame==null||phase==Phase.PUBLISHING){if(phase==Phase.PREPARING)phase=Phase.RESTORING;schedule();}else beginReturn();
            return receipt(true,false,error).outcome(owner.settings());
        }
        owner.commitRuntimeChoices(admitted);signalKnown=false;evaluateInput(true);
        return receipt(true,true,error).outcome(owner.settings());
    }

    /** Called only by the loaded/deferred channel adapter, never directly from tile onLoad. */
    void resumed(){var world=level();if(!installed(world))return;var pending=NativeRampJournal.atRoot(world,owner.getBlockPos());if(pending!=null&&pending!=operation){operation=pending;phase=Phase.RESTORING;status("Recovering original platform",false);schedule();return;}boolean initial=!resumed;resumed=true;evaluateInput(initial);refreshActivity();schedule();}
    void inputChanged(){if(installed(level()))evaluateInput(false);}
    private void evaluateInput(boolean force){
        var choices=owner.settings();double next=RampLifecycle.signalTarget(choices.analogDeployment(),choices.escalator()!=0,choices.powerOn(),owner.visual().physicalPower(),owner.visual().receivedPower());
        if(!force&&signalKnown&&Double.compare(next,lastSignalTarget)==0)return;signalKnown=true;lastSignalTarget=next;beltRequested=choices.escalator()!=0&&next>0;
        requestPose(choices.escalator()==0?next:1);
        refreshActivity();
    }
    public NativeRampReceipt requestPose(double target){
        var world=level();if(!Double.isFinite(target)||target<0||target>1||!installed(world)||phase==Phase.HELD)return receipt(false,false,true);
        if(NativeRampAuthorization.actorId(owner)==null){status("Reconfigure to authorize automation",true);return receipt(false,false,true);}
        requested=target;waitingCanopy=false;stopPending=false;
        if(operation==null){
            if(target==0&&owner.settings().startHalf()==0){phase=Phase.IDLE;status("Retracted",false);return receipt(true,false,false);}
            prepare();
        }else if(phase==Phase.ACTIVE||phase==Phase.RETURNING){
            if(target==0&&owner.settings().startHalf()==0)beginReturn();else startTimeline(target);
        }
        schedule();return receipt(true,false,error);
    }
    private void prepare(){
        var world=level();var actor=NativeRampAuthorization.permissionActor(owner);var selection=NativeRampCapture.select(owner,actor);
        if(!selection.accepted()){status(selection.error(),true);return;}
        var clearance=NativeRampClearance.inspect(owner,actor,selection.plan(),requested>0);
        if(clearance.status()==NativeRampClearance.Status.PENDING){waitingCanopy=true;status(clearance.error(),false);return;}
        if(clearance.status()!=NativeRampClearance.Status.READY){status(clearance.error(),true);return;}
        var state=parts.get();if(!(state.getBlock() instanceof NativeRampPartBlock))throw new IllegalStateException("Ramp generated-part factory is unavailable");
        state=state.setValue(NativeRampPartBlock.FACING,selection.plan().facing());
        int duration=RampLifecycle.duration(selection.plan().length(),owner.settings().startHalf(),owner.settings().endHalf(),owner.settings().speed());
        var motion=new NativeRampPartData.Motion(0,world.getGameTime(),0,0,duration,false,0);
        var choices=owner.settings();var belt=choices.escalator()==0?NativeRampPartData.Belt.NONE:new NativeRampPartData.Belt(choices.escalator(),choices.speed(),0,world.getGameTime(),false,0);
        var started=NativeRampJournal.prepareAssembly(owner,actor,selection.plan(),state,motion,belt);
        operation=started.operation();if(operation!=null){beltState=new NativeRampBeltRiders.State(operation.id());NativeRampActivity.watch(this);}if(started.status()==NativeRampJournal.Status.REFUSED){status(started.error(),true);return;}
        phase=Phase.PREPARING;status("Preparing platform",false);
    }
    private void startTimeline(double target){
        var world=level();var settings=owner.settings();int duration=RampLifecycle.duration(operation.assembly().footprint().length(),settings.startHalf(),settings.endHalf(),settings.speed());
        stopPending=false;timeline=timeline.request(target,world.getGameTime(),duration);phase=Phase.ACTIVE;revision++;
        status(timeline.moving?(target>timeline.observed?"Deploying":"Retracting"):(target>0?"Deployed":"Retracted"),false);schedule();
    }
    private void beginReturn(){
        if(operation==null)return;stopPending=deployed()&&frame.belt().running();phase=Phase.RETURNING;
        var world=level();timeline=deployed()?timeline.stopped(world.getGameTime()):timeline.request(0,world.getGameTime(),timeline.duration);revision++;
        status("Returning platform",false);schedule();
    }
    public void scheduledTick(){
        var world=level();if(!installed(world)||phase==Phase.HELD)return;
        try{
            if(waitingCanopy&&operation==null){waitingCanopy=false;prepare();}
            if(phase==Phase.PREPARING&&operation==null){prepare();schedule();return;}
            if(operation==null)return;
            if(frame!=null&&(phase==Phase.ACTIVE||phase==Phase.RETURNING))notices=NativeRampFrameCommand.retry(world,operation,notices,frame.cells());
            if(world.noSave&&frame!=null&&(phase==Phase.ACTIVE||phase==Phase.RETURNING)){if(!error&&timeline.moving)timeline=timeline.request(timeline.target,world.getGameTime(),timeline.duration);stoppedError=error?text:"Saving is disabled";stopPending=true;status(stoppedError,error);publishStopped();if(deployed())beltWaiting=true;schedule();return;}
            if(phase==Phase.RESTORING){restore();schedule();return;}
            if(phase==Phase.PREPARING||phase==Phase.PUBLISHING){publish();schedule();return;}
            if(phase==Phase.ACTIVE||phase==Phase.RETURNING){
                if(stopPending){publishStopped();schedule();return;}
                if(phase==Phase.RETURNING&&!timeline.moving&&(timeline.observed==0||deployed())){alignOriginal();schedule();return;}
                if(timeline.moving&&timeline.lastTick!=world.getGameTime())advance();
                else if(beltRunning())advanceBelt();
            }
            schedule();
        }catch(RuntimeException failure){phase=Phase.HELD;timeline=timeline.stopped(world.getGameTime());status(failure.getMessage(),true);}
    }
    private void publish(){
        var world=level();var actor=NativeRampAuthorization.permissionActor(owner);var result=NativeRampJournal.poll(world,operation);
        if(result==NativeRampJournal.Status.REFUSED){phase=Phase.HELD;status(operation.error(),true);return;}
        if(result==NativeRampJournal.Status.PENDING)return;
        if(operation.phase()==NativeRampJournal.Phase.PREPARED){NativeRampJournal.arm(world,operation,actor);return;}
        if(operation.phase()==NativeRampJournal.Phase.ARMED){
            var assembly=operation.assembly();var riders=NativeRampRiderPlan.prepare(world,assembly.footprint(),assembly.original().getBlock() instanceof StairBlock,0,0,true,false);
            if(riders==null){phase=Phase.RESTORING;status("Platform capture stopped: a rider is obstructed",true);return;}
            var applied=NativeRampJournal.applyAssembly(world,operation,actor,riders,SYNC);
            if(applied==NativeRampJournal.Status.REFUSED){phase=operation.requiresActorAlignment()?Phase.HELD:Phase.RESTORING;status(operation.error(),true);return;}
            phase=Phase.PUBLISHING;status("Publishing platform",false);return;
        }
        if(operation.phase()==NativeRampJournal.Phase.APPLIED){
            if(frame==null){var assembly=operation.assembly();int duration=RampLifecycle.duration(assembly.footprint().length(),owner.settings().startHalf(),owner.settings().endHalf(),owner.settings().speed());
            timeline=new RampLifecycle.Timeline(0,world.getGameTime(),world.getGameTime(),0,0,0,duration,false);
            var choices=owner.settings();var belt=choices.escalator()==0?NativeRampPartData.Belt.NONE:new NativeRampPartData.Belt(choices.escalator(),choices.speed(),0,world.getGameTime(),false,0);
            frame=new NativeRampFramePublisher.Frame(assembly.cells(0,0),motion(timeline),belt,0);}
            var active=NativeRampJournal.activate(world,operation);if(active!=NativeRampJournal.Status.READY){if(active==NativeRampJournal.Status.REFUSED){phase=Phase.HELD;status(operation.error(),true);}return;}
            notices=NativeRampJournal.retryInitialNotices(world,operation);if(requested==0&&(owner.settings().startHalf()==0||queuedChoices!=null))beginReturn();else startTimeline(requested);
        }
    }
    private static NativeRampPartData.Motion motion(RampLifecycle.Timeline value){return new NativeRampPartData.Motion(value.sequence,value.started,value.from,value.target,value.duration,value.moving,value.observed);}
    private NativeRampFramePublisher.Frame frozen(){var m=frame.motion();double p=timeline.observed;return new NativeRampFramePublisher.Frame(frame.cells(),new NativeRampPartData.Motion(m.sequence(),m.started(),p,p,m.duration(),false,p),stoppedBelt(),frame.revision());}
    private void advance(){
        var world=level();var proposed=timeline.committed(world.getGameTime(),timeline.sample(world.getGameTime(),0));
        // A stopped endpoint is a new physical publication, even when its control sequence is unchanged.
        // Clients otherwise retain the first moving observation as their collision shape.
        long nextRevision=timeline.moving&&!proposed.moving?Math.addExact(revision,1):revision;
        var candidate=new NativeRampFramePublisher.Frame(operation.assembly().cells(proposed.observed,proposed.sample(world.getGameTime()+1,0)),motion(proposed),stoppedBelt(),nextRevision);
        var result=NativeRampFrameCommand.advance(world,operation,NativeRampAuthorization.permissionActor(owner),frozen(),candidate,Math.addExact(nextRevision,1),SYNC);
        notices=result.notices();
        if(result.outcome()==NativeRampFrameCommand.Outcome.INSTALLED){controlsStoppedPublished=false;timeline=proposed;frame=result.frame();revision=frame.revision();if(timeline.moving&&!error&&text.equals("Saving is disabled"))status(phase==Phase.RETURNING?"Returning platform":"Moving platform",false);if(!timeline.moving){status(timeline.observed>0?"Deployed":"Retracted",false);if(timeline.observed==0&&(owner.settings().startHalf()==0||queuedChoices!=null))phase=Phase.RETURNING;else refreshActivity();}}
        else if(result.outcome()==NativeRampFrameCommand.Outcome.PENDING){timeline=timeline.request(timeline.target,world.getGameTime(),timeline.duration);status(result.error(),false);}
        else{if(result.frame()!=null){frame=result.frame();revision=frame.revision();}timeline=timeline.stopped(world.getGameTime());if(result.outcome()==NativeRampFrameCommand.Outcome.HELD_FAILURE)phase=Phase.HELD;else{stoppedError=result.error();stopPending=true;}status(result.error(),true);if(stopPending)publishStopped();}
        changed();
    }
    /** Freeze client control at the last installed physical frame after a refused step.
     * This transports no actors and never substitutes a new ownership scope. */
    private void publishStopped(){
        if(controlsStoppedPublished&&frame.motion().observed()==timeline.observed){stopPending=false;return;}
        var world=level();double pose=timeline.observed;long nextRevision=Math.addExact(Math.max(revision,frame.revision()),1);
        var stopped=new NativeRampPartData.Motion(timeline.sequence,world.getGameTime(),pose,pose,timeline.duration,false,pose);
        var candidate=new NativeRampFramePublisher.Frame(operation.assembly().cells(pose,pose),stopped,stoppedBelt(),nextRevision);
        var result=NativeRampFrameCommand.advance(world,operation,NativeRampAuthorization.permissionActor(owner),frozen(),candidate,Math.addExact(nextRevision,1),SYNC);
        notices=result.notices();
        if(result.outcome()==NativeRampFrameCommand.Outcome.INSTALLED){controlsStoppedPublished=true;frame=result.frame();revision=frame.revision();stopPending=false;status(stoppedError,error);}
        else if(result.outcome()!=NativeRampFrameCommand.Outcome.PENDING){if(result.frame()!=null){frame=result.frame();revision=frame.revision();}stopPending=false;phase=Phase.HELD;status(stoppedError+"; stopped publication: "+result.error(),true);}
    }
    private void alignOriginal(){
        var world=level();var stationary=new NativeRampPartData.Motion(timeline.sequence+1,world.getGameTime(),0,0,timeline.duration,false,0);
        var candidate=new NativeRampFramePublisher.Frame(operation.assembly().originalAlignment().cells(0,0),stationary,NativeRampPartData.Belt.NONE,++revision);
        var result=NativeRampFrameCommand.alignOriginal(world,operation,NativeRampAuthorization.permissionActor(owner),frozen(),candidate,revision+1,SYNC,beltState);
        if(result.outcome()==NativeRampFrameCommand.Outcome.INSTALLED){frame=result.frame();notices=result.notices();phase=Phase.RESTORING;status("Restoring original blocks",false);}
        else if(result.outcome()!=NativeRampFrameCommand.Outcome.PENDING){phase=Phase.HELD;status(result.error(),true);}
    }
    private boolean deployed(){return frame!=null&&com.vandorlabs.ramp.RampBeltPolicy.deployed(frame.belt().mode(),timeline.moving,timeline.observed);}
    private boolean beltRunning(){return phase==Phase.ACTIVE&&!error&&!beltWaiting&&beltRequested&&nearby&&deployed();}
    private NativeRampPartData.Belt stoppedBelt(){var b=frame.belt();if(b.mode()==0)return NativeRampPartData.Belt.NONE;return new NativeRampPartData.Belt(b.mode(),b.speed(),b.observed(),level().getGameTime(),false,b.observed());}
    /** The registry polls once per second, including sleeping belts; no idle per-tick scan. */
    boolean belongs(net.minecraft.world.level.Level world){return owner.getLevel()==world;}
    boolean refreshActivity(net.minecraft.server.MinecraftServer server){return owner.getLevel() instanceof ServerLevel world&&world.getServer()==server&&refreshActivity();}
    boolean refreshActivity(){
        if(!(owner.getLevel() instanceof ServerLevel world)||!installed(world)||operation==null||owner.settings().escalator()==0)return false;
        boolean was=beltRunning(),retainExitRadius=beltWaiting&&nearby&&beltRequested&&!error;beltWaiting=false;double radius=was||retainExitRadius?40:32;nearby=false;
        if(deployed()&&phase==Phase.ACTIVE){var spec=operation.assembly().footprint();for(var player:world.players()){
            if(!player.isAlive()||player.isSpectator())continue;
            for(var origin:spec.origins()){
                var source=origin.position();double along=spec.verticalLayout()?source.getY()-spec.sourceY():source.getX()*spec.facing().getStepX()+source.getZ()*spec.facing().getStepZ()-spec.minimumAlong();
                double offset=spec.end()*along/Math.max(1,spec.length()-1);
                double x=source.getX()+.5+(spec.verticalLayout()?spec.facing().getStepX()*offset:0),y=source.getY()+(spec.verticalLayout()?.5:spec.high()+offset),z=source.getZ()+.5+(spec.verticalLayout()?spec.facing().getStepZ()*offset:0);
                if(player.distanceToSqr(x,y,z)<=radius*radius){nearby=true;break;}
            }if(nearby)break;
        }}
        if(was!=beltRunning()||deployed()&&!timeline.moving&&!error){String next=beltRunning()?"Escalator running":beltRequested?"Escalator sleeping (no nearby player)":"Escalator stopped";if(!next.equals(text))status(next,false);if(!beltRunning()&&frame.belt().running()){stoppedError=text;stopPending=true;}schedule();}
        return true;
    }
    private void advanceBelt(){
        var world=level();if(frame.belt().running()&&frame.belt().tick()==world.getGameTime())return;
        var result=NativeRampFrameCommand.advanceBelt(world,operation,NativeRampAuthorization.permissionActor(owner),frozen(),frame.belt().running()?revision:Math.addExact(revision,1),SYNC,beltState);notices=result.notices();
        if(result.outcome()==NativeRampFrameCommand.Outcome.INSTALLED){controlsStoppedPublished=false;frame=result.frame();revision=frame.revision();if(!error&&text.equals("Saving is disabled"))status("Escalator running",false);changed();}
        else if(result.outcome()==NativeRampFrameCommand.Outcome.PENDING){beltWaiting=true;stoppedError=result.error();stopPending=true;status(stoppedError,false);publishStopped();}
        else{if(result.frame()!=null){frame=result.frame();revision=frame.revision();}stoppedError=result.error();stopPending=true;status(stoppedError,true);publishStopped();}
    }
    private void restore(){
        var result=NativeRampJournal.recover(level(),operation);
        if(result==NativeRampJournal.Status.DONE){operation=null;frame=null;if(beltState!=null)beltState.reset();beltState=null;NativeRampActivity.unwatch(this);phase=Phase.IDLE;timeline=new RampLifecycle.Timeline(timeline.sequence,level().getGameTime(),level().getGameTime(),0,0,0,timeline.duration,false);status("Retracted",false);if(queuedChoices!=null){var next=queuedChoices;queuedChoices=null;owner.commitRuntimeChoices(next);signalKnown=false;evaluateInput(true);}}
        else if(result==NativeRampJournal.Status.REFUSED){phase=Phase.HELD;status(operation.error(),true);}
    }
}
