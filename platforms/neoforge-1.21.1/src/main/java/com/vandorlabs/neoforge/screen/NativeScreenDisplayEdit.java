package com.vandorlabs.neoforge.screen;
import com.vandorlabs.screen.ScreenFamily;
import com.vandorlabs.persistence.ScreenData;
import com.vandorlabs.redstone.ChannelList;
/** Bounded selector choices; owned items, runtime power and opaque NBT are never accepted from the editor. */
public record NativeScreenDisplayEdit(String screen,boolean enabled,boolean inverted,int playback,boolean framed,int speed,String input,String secondary,boolean small,int housing,ChannelList channels) {
 public interface Catalog extends NativeScreenSettings.Catalog {boolean validScreen(String value);String canonicalScreen(String value);String canonicalInput(String value);}
 public NativeScreenDisplayEdit{if(!name(screen)||!name(input)||!name(secondary)||playback<0||playback>2||speed<0||speed>2||housing<0||channels==null)throw new IllegalArgumentException("Screen selector choices");}
 private static boolean name(String value){return value!=null&&value.length()<=256;}
 public NativeScreenSettings apply(ScreenFamily family,NativeScreenSettings old,Catalog catalog){
  if(family==null||!catalog.validScreen(screen)||!catalog.validInput(input)||!catalog.validInput(secondary)||!catalog.validMaterial(housing))return null;
  var data=old.scalar();String nextScreen=java.util.Objects.equals(catalog.canonicalScreen(data.selectedScreen),catalog.canonicalScreen(screen))?data.selectedScreen:screen,nextInput=java.util.Objects.equals(catalog.canonicalInput(data.inputPanel),catalog.canonicalInput(input))?data.inputPanel:input,nextSecondary=java.util.Objects.equals(catalog.canonicalInput(data.secondaryInputPanel),catalog.canonicalInput(secondary))?data.secondaryInputPanel:secondary;
  int main=catalog.material(housing);boolean nextSmall=family.keyboard?small:data.smallInput;NativeScreenActivation activation=old.activation();if(activation.data().mode>=0){var next=activation.data().withMode(enabled?Math.max(1,activation.data().mode):0);if(next!=activation.data())activation=new NativeScreenActivation(next);}
  if(nextScreen.equals(data.selectedScreen)&&enabled==data.redstoneEnabled&&inverted==old.inverted()&&playback==data.displayMode&&framed==data.framed&&speed==data.animationSpeedIndex&&nextInput.equals(data.inputPanel)&&nextSecondary.equals(data.secondaryInputPanel)&&nextSmall==data.smallInput&&main==data.housingTexture&&main==old.housing().mainTexture&&channels.equals(old.channels())&&activation==old.activation())return old;
  ScreenData scalar=new ScreenData(nextScreen,enabled,playback,framed,speed,nextInput,nextSecondary,data.wallPosition,nextSmall,channels.first(),data.channelSignal,main);
  return new NativeScreenSettings(scalar,old.housing().withMainTexture(main),activation,channels,old.channelLevel(),inverted,old.primarySurface(),old.secondarySurface(),old.ceilingMounted(),old.ceilingPosition(),old.surfaceLayout(),old.surfaceTileSides());
 }
}
