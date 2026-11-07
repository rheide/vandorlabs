package com.vandorlabs.neoforge.canopy;

import com.vandorlabs.neoforge.redstone.*;
import com.vandorlabs.persistence.CanopySettingsData;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

/** Authoritative standalone controller. Close admission is mandatory and never defaults to READY. */
public final class NativeCanopyControl {
    private NativeCanopyControl(){}
    public enum Close {READY,PENDING,REFUSED}
    public interface Access extends NativeCanopyReservations.Access {Close prepareClose(NativeCanopyEntity owner);}
    static final class State {boolean review=true,retry=true,powerInitialized,channelsPending=true;void reset(){review=retry=channelsPending=true;powerInitialized=false;}}
    public static boolean opening(NativeCanopyEntity owner){return owner.kind().equals("canopy")||owner.kind().equals("long_canopy")||CanopySettingsData.supports(owner.kind())&&owner.settings().data().openable;}
    private static boolean real(NativeCanopyEntity owner){return owner.getLevel() instanceof ServerLevel server&&server.getServer().isSameThread()&&owner.available()&&NativeCanopyLifecycle.installed(owner);}
    public static void retryClose(NativeCanopyEntity host){if(real(host)){var owner=NativeCanopyLifecycle.owner(host);owner.control.retry=true;}}
    private static void geometryChanged(NativeCanopyEntity owner){owner.control.review=owner.control.retry=true;}
    public static boolean toggle(NativeCanopyEntity owner){if(!real(owner)||!owner.publication().draws()||!opening(owner))return false;owner.stageLinks(owner.links().withMotion(owner.links().progress(),!owner.links().open(),owner.links().waiting()));geometryChanged(owner);owner.notifyChanged();return true;}
    static int localPower(NativeCanopyEntity owner){if(!real(owner)||!owner.publication().draws()||!opening(owner)||!owner.settings().data().redstone)return 0;for(var member:NativeCanopyLifecycle.members(owner))for(var cell:NativeCanopyFootprint.cells(member.kind(),member.getBlockPos(),member.facing(),member.settings(),member.links()))if(NativeLoadedRedstonePower.level(owner.getLevel(),cell)>0)return 15;return 0;}
    public static void refreshPower(NativeCanopyEntity owner){
        if(!real(owner)||!owner.publication().draws()||!opening(owner)||!owner.settings().data().redstone)return;
        boolean powered=owner.channelPowered||localPower(owner)>0;var links=owner.links();boolean polarity=owner.settings().data().activateOnPower;
        if(links.powerKnown()&&links.lastPower()==powered&&links.lastPolarity()==polarity)return;
        boolean target=powered==polarity;owner.stageLinks(new NativeCanopyLinks(links.anchor(),links.pair(),links.joinedWidth(),links.centered(),links.hollow(),links.footprint(),links.released(),links.waiting(),target,links.progress(),true,powered,polarity));owner.setChanged();if(target!=links.open()){geometryChanged(owner);owner.notifyChanged();}
    }
    /** Source setChannels invalidates the power edge even when the list itself is unchanged. */
    static void settingsChanged(NativeCanopyEntity owner){
        if(!real(owner))return;var l=owner.links();owner.stageLinks(new NativeCanopyLinks(l.anchor(),l.pair(),l.joinedWidth(),l.centered(),l.hollow(),l.footprint(),l.released(),l.waiting(),l.open(),l.progress(),false,l.lastPower(),l.lastPolarity()));geometryChanged(owner);NativeRedstoneChannels.channelsChanged(owner);refreshPower(owner);
    }
    /** Call every member as a normal world tick would. The caller supplies actual ramp/journal admission. */
    public static void tick(NativeCanopyEntity owner,Access access){
        if(!real(owner))return;var control=owner.control;
        if(control.channelsPending){control.channelsPending=false;NativeRedstoneChannels.unregister(owner);NativeRedstoneChannels.channelsChanged(owner);}
        if(!control.powerInitialized&&owner.publication().draws()&&opening(owner)&&owner.settings().data().redstone){control.powerInitialized=true;refreshPower(owner);}
        if(!owner.publication().draws()||!opening(owner)){NativeCanopyLifecycle.prepare(owner);return;}
        var links=owner.links();if(links.open()&&links.waiting()){owner.stageLinks(links.withMotion(links.progress(),true,false));control.review=true;owner.notifyChanged();links=owner.links();}
        if(links.open()&&links.progress()==1&&control.review){control.review=false;NativeCanopyReservations.releaseOpen(owner,access);links=owner.links();}
        if(!links.open()&&control.retry){control.retry=false;boolean blocked=access.prepareClose(owner)!=Close.READY;links=owner.links();if(blocked&&links.progress()==0){owner.stageLinks(links.withMotion(1,false,true));NativeCanopyReservations.releaseOpen(owner,access);owner.notifyChanged();}else if(blocked!=links.waiting()){owner.stageLinks(links.withMotion(links.progress(),false,blocked));owner.notifyChanged();}links=owner.links();}
        if(links.waiting()){NativeCanopyLifecycle.prepare(owner);return;}
        float next=Math.max(0,Math.min(1,links.progress()+(links.open()?1:-1)/16F));if(next!=links.progress()){owner.stageLinks(links.withMotion(next,links.open(),false));owner.setChanged();if(next==1){control.review=false;NativeCanopyReservations.releaseOpen(owner,access);}if(next==0||next==1)owner.notifyChanged();}
        NativeCanopyLifecycle.prepare(owner);
    }
}
