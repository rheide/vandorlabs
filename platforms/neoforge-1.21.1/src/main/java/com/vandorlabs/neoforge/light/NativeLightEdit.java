package com.vandorlabs.neoforge.light;
import com.vandorlabs.neoforge.ModernFinishCatalog;
import com.vandorlabs.persistence.SpaceDoorData;
import com.vandorlabs.render.TextureLayoutMode;
/** Source Light dialog choices. Manual and joined runtime are absent from the editor draft. */
public record NativeLightEdit(int faceTexture,int offFaceTexture,int level,boolean join,int housing,int trigger,boolean small,int layout,boolean signalBrightness,int offset) {
 public boolean valid(){return ModernFinishCatalog.validChoice(faceTexture)&&(offFaceTexture==-1||ModernFinishCatalog.validChoice(offFaceTexture))&&level>=0&&level<=15&&ModernFinishCatalog.validChoice(housing)&&SpaceDoorData.validTrigger(trigger)&&TextureLayoutMode.valid(layout)&&offset>=-15&&offset<=15;}
}
