package com.vandorlabs.neoforge.door;

import com.vandorlabs.neoforge.ModernFinishCatalog;
import com.vandorlabs.persistence.SpaceDoorData;
import com.vandorlabs.render.TextureLayoutMode;

/** Validated immediate editor values; frame/glazing drafts use their independent routes. */
public record NativeDoorEdit(int design,int detail,boolean framed,int direction,int depth,boolean sliding,
                             boolean hinges,int trigger,boolean panel,int panelMode,int securityMode,int allowed,int faceTexture,int layout) {
    public boolean valid(){return design>=0&&design<30&&detail>=0&&detail<3&&direction>=0&&direction<4&&depth>=0&&depth<3&&SpaceDoorData.validTrigger(trigger)&&panelMode>=0&&panelMode<3&&(panelMode!=2||depth==0)&&securityMode>=0&&securityMode<5&&allowed>=0&&allowed<=127&&(faceTexture==-1||ModernFinishCatalog.validChoice(faceTexture))&&TextureLayoutMode.valid(layout);}
    public boolean accepts(boolean large){return valid()&&(large||direction!=3&&(design<21||design==29));}
    public NativeDoorSettings apply(NativeDoorSettings previous){
        int mode=securityMode>0&&panelMode==0?1:panelMode;boolean visible=mode>0;
        return new NativeDoorSettings(new SpaceDoorData(design,detail,framed,direction,depth==0,sliding,hinges,trigger,visible),faceTexture<0?-1:ModernFinishCatalog.storedChoice(faceTexture),layout,layout==1,depth,mode,securityMode,NativeDoorSettings.singleCompany(allowed),previous.frames(),previous.glass());
    }
}
