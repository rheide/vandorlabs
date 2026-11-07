package com.vandorlabs.neoforge.copifier;

import com.vandorlabs.neoforge.*;
import com.vandorlabs.neoforge.door.*;
import com.vandorlabs.persistence.*;
import com.vandorlabs.redstone.ChannelList;
import net.minecraft.nbt.*;

/** Ordinary Door clipboard policy; reader inventories, timers and physical fluids stay outside the plan. */
public final class NativeCopifierDoorEdit {
 public record State(NativeDoorSettings settings,ChannelList channels){}
 public record Plan(State state,boolean applicable,boolean resetSecurity){}
 private static final String[] BODY={"redstone_trigger","door_design","door_detail","framed","door_slide_direction","door_middle","door_sliding","door_hinges","door_panel","door_depth"};
 private static int number(CompoundTag tag,String key,int previous){return tag.contains(key,Tag.TAG_INT)?tag.getInt(key):previous;}
 private static boolean flag(CompoundTag tag,String key,boolean previous){return tag.contains(key,Tag.TAG_BYTE)?tag.getBoolean(key):previous;}
 private static SpaceDoorData panel(SpaceDoorData d,boolean panel){return new SpaceDoorData(d.design,d.detail,d.framed,d.direction,d.middle,d.sliding,d.hinges,d.trigger,panel);}
 public static State read(String id,CompoundTag saved){
  var s=NativeDoorSettings.read(saved);if(!saved.contains("SpaceDoorSchema")){var d=s.door();boolean sliding=!id.equals(NativeDoorSettings.LEGACY_ROTATING_ID);d=new SpaceDoorData(d.design,d.detail,d.framed,d.direction,sliding||d.middle,sliding,d.hinges,d.trigger,d.panel);s=new NativeDoorSettings(d,s.faceTexture(),s.rawLayout(),s.tileTexture(),sliding?0:s.depth(),s.rawPanelMode(),s.securityMode(),s.securityAllowed(),s.frames(),s.glass());}
  ChannelList channels=ChannelList.of(Math.max(0,saved.getInt("RedstoneChannel")));if(saved.contains("RedstoneChannels",Tag.TAG_INT_ARRAY))try{channels=ChannelList.of(saved.getIntArray("RedstoneChannels"));}catch(IllegalArgumentException malformed){/* Source scalar fallback. */}return new State(s,channels);
 }
 public static CompoundTag capture(State state){
  var s=state.settings();var d=s.door();var out=new CompoundTag();out.putInt("redstone_trigger",d.trigger);out.putBoolean("door_tile_texture",s.tileTexture());out.putInt("door_texture_layout",s.textureLayout());out.putInt("door_design",d.design);out.putInt("door_face_texture",s.faceTexture());out.putInt("door_detail",d.detail);out.putBoolean("framed",d.framed);out.putInt("door_slide_direction",d.direction);out.putBoolean("door_middle",d.middle);out.putBoolean("door_sliding",d.sliding);out.putBoolean("door_hinges",d.hinges);out.putInt("door_panel",s.panelMode());out.putInt("door_depth",s.depth());
  var security=new CompoundTag();security.putInt("Mode",s.securityMode());security.putInt("Allowed",s.securityAllowed());out.put("door_security",security);if(d.design==15||d.design==16||d.design==29){var glass=new CompoundTag();s.glass().write(glass);out.put("DoorGlassAppearance",glass);}var frame=new CompoundTag();s.frames().write(new ModernHousingTags(frame));out.put("FrameAppearance",frame);if(state.channels().size()<=1)out.putInt("redstone_channel",state.channels().first());else out.putIntArray("redstone_channel",state.channels().toArray());return out;
 }
 public static Plan prepare(State current,boolean large,CompoundTag values){
  if(!NativeCopiedSettingsValidation.valid(values))throw new IllegalArgumentException("Malformed copied Door settings");
  var s=current.settings();var d=s.door();int face=s.faceTexture(),layout=s.rawLayout(),depth=s.depth(),rawPanel=s.rawPanelMode(),mode=s.securityMode(),allowed=s.securityAllowed();boolean tile=s.tileTexture(),applicable=false,reset=false;var frames=s.frames();var glass=s.glass();var channels=current.channels();
  if(values.contains("DoorGlassAppearance",Tag.TAG_COMPOUND)){glass=NativeDoorGlass.read(values.getCompound("DoorGlassAppearance"));applicable=true;}
  if(values.contains("door_tile_texture",Tag.TAG_BYTE)){boolean next=values.getBoolean("door_tile_texture");if(next!=tile){tile=next;layout=-1;}applicable=true;}
  if(values.contains("door_texture_layout",Tag.TAG_INT)){int next=values.getInt("door_texture_layout");if(next!=(layout<0?tile?1:0:layout)){layout=next;tile=next==1;}applicable=true;}
  if(values.contains("door_face_texture",Tag.TAG_INT)){int next=values.getInt("door_face_texture");face=next<0?-1:ModernFinishCatalog.storedChoice(next);applicable=true;}
  if(values.contains("door_security",Tag.TAG_COMPOUND)){var security=values.getCompound("door_security");int next=security.getInt("Mode"),company=NativeDoorSettings.singleCompany(security.getInt("Allowed"));reset=mode!=next||allowed!=company;mode=Math.max(0,Math.min(4,next));allowed=company;if(mode>0&&!d.panel){rawPanel=1;d=panel(d,true);}applicable=true;}
  boolean body=false;for(String key:BODY)if(values.contains(key)){body=true;break;}
  if(body){
   int design=number(values,"door_design",d.design),detail=number(values,"door_detail",d.detail),direction=number(values,"door_slide_direction",d.direction),trigger=number(values,"redstone_trigger",d.trigger);if(!SpaceDoorData.validTrigger(trigger))trigger=d.trigger;
   boolean middle=flag(values,"door_middle",d.middle),visible=values.contains("door_panel",Tag.TAG_INT)?values.getInt("door_panel")>0:flag(values,"door_panel",d.panel);
   if(design>=0&&design<30&&(large||design<21||design==29)&&detail>=0&&detail<3&&direction>=0&&direction<4&&(large||direction!=3)){
    if(middle!=d.middle)depth=middle?0:1;d=new SpaceDoorData(design,detail,flag(values,"framed",d.framed),direction,middle,flag(values,"door_sliding",d.sliding),flag(values,"door_hinges",d.hinges),trigger,visible);
   }
   if(values.contains("door_depth",Tag.TAG_INT)){int next=values.getInt("door_depth");if(next>=0&&next<=2){depth=next;if(depth!=0&&rawPanel==2)rawPanel=1;d=new SpaceDoorData(d.design,d.detail,d.framed,d.direction,depth==0,d.sliding,d.hinges,d.trigger,d.panel);}}
   if(values.contains("door_panel",Tag.TAG_INT)){rawPanel=Math.max(0,Math.min(depth==0?2:1,values.getInt("door_panel")));d=panel(d,rawPanel>0);}applicable=true;
  }
  if(values.contains("redstone_channel")){if(values.contains("redstone_channel",Tag.TAG_INT_ARRAY))try{channels=ChannelList.of(values.getIntArray("redstone_channel"));}catch(IllegalArgumentException malformed){/* Source retains current selection. */}else if(values.contains("redstone_channel",Tag.TAG_INT))channels=ChannelList.of(Math.max(0,values.getInt("redstone_channel")));applicable=true;}
  if(values.contains("FrameAppearance",Tag.TAG_COMPOUND)){frames=FrameSettings.read(new ModernHousingTags(values.getCompound("FrameAppearance")),NativeDoorSettings::frame,NativeDoorSettings::surface,ModernFinishCatalog::validChoice);applicable=true;}
  return new Plan(new State(new NativeDoorSettings(d,face,layout,tile,depth,rawPanel,mode,allowed,frames,glass),channels),applicable,reset);
 }
 private NativeCopifierDoorEdit(){}
}
