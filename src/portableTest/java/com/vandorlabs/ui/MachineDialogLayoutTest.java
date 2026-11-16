package com.vandorlabs.ui;

import com.vandorlabs.ports.ResourcePortSettings;

public final class MachineDialogLayoutTest {
    public static void main(String[] args){int cases=0;
        for(int width:new int[]{320,427,640,960})for(int height:new int[]{240,360,480})for(boolean active:new boolean[]{false,true})for(int mode=0;mode<3;mode++){
            ProgrammableDialogLayout panel=new ProgrammableDialogLayout(width,height);for(SurfaceDialog.Control control:SurfaceDialog.controls(panel,active,mode)){check(control.x>=panel.left&&control.y>=panel.top&&control.x+control.width<=panel.left+panel.width&&control.y+control.height<=panel.top+panel.height,"Surface control fit");if(control.id==SurfaceDialog.ACTIVE)check(control.enabled!=active,"Source active tab ID90 and selected state");if(control.id==SurfaceDialog.INACTIVE)check(control.enabled==active,"Source inactive tab ID91 and selected state");}check(SurfaceDialog.cycleLayout(mode,-1)==Math.floorMod(mode-1,3),"reverse layout order");cases++;
        }
        ResourcePortSettings settings=ResourcePortSettings.placed(2);
        for(int page=0;page<3;page++)for(int mode=0;mode<3;mode++)for(boolean battery:new boolean[]{false,true})for(boolean block:new boolean[]{false,true}){
            java.util.Set<Integer> nativeIds=new java.util.HashSet<>();for(ResourcePortDialog.Control control:ResourcePortDialog.controls(0,0,page,settings,mode,battery,block)){check(control.x>=0&&control.y>=0&&control.x+control.width<=308&&control.y+control.height<=280,"Port Source bounds");check(nativeIds.add(ResourcePortDialog.nativeId(control.id)),"Native legacy ID mapping unique");if(control.tab)check(control.enabled!=(control.id==100+page),"Port selected tab");}cases++;
        }
        check(DialogStyle.SEGMENTED.logoHeaderHeight==26&&DialogStyle.BUFFER.titleX==16&&DialogStyle.SIGNAL.headerHeight==24,"family Source shell differences");check(DialogStyle.PROGRAMMER.logoHeaderHeight==0&&DialogStyle.COPY.logoHeaderHeight==0,"retain Source absent-logo families");
        System.out.println("MachineDialogLayoutTest PASS cases="+cases+" Source Surface controls/options/reverse and Port dimensions/tabs/labels/legacyNativeIDs");
    }
    private static void check(boolean value,String text){if(!value)throw new AssertionError(text);}
}
