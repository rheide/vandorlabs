package com.vandorlabs.screen;
/** Canonical placed-screen roles and legacy metadata, frozen against actual block classes. */
public enum ScreenFamily {
 SCREEN("programmable_viewscreen",false,false,false,false,true),
 CONSOLE("programmable_console",false,false,false,true,true),
 HALF_CONSOLE("programmable_half_console",false,false,true,true,false),
 HALF_INPUT("programmable_half_input",true,true,true,false,false),
 INPUT("programmable_input",true,true,false,false,true),
 DIAGONAL("programmable_diagonal_screen",true,false,false,false,true),
 HALF_DIAGONAL("programmable_diagonal_half_screen",false,false,true,false,false);
 private static final int[] HORIZONTAL_FACES={3,4,2,5};
 public final String id;public final boolean horizontal,keyboard,halfPrimary,secondary,displayArtwork;
 ScreenFamily(String id,boolean horizontal,boolean keyboard,boolean halfPrimary,boolean secondary,boolean displayArtwork){this.id=id;this.horizontal=horizontal;this.keyboard=keyboard;this.halfPrimary=halfPrimary;this.secondary=secondary;this.displayArtwork=displayArtwork;}
 public int activationSlot(){return this==HALF_CONSOLE?1:0;}
 public boolean supportsSlot(int slot){return slot==0||slot==1&&secondary;}
 public boolean half(int slot){return halfPrimary||this==CONSOLE&&slot==1;}
 /** Standalone controls only; cockpit surfaces independently use six/three rows. */
 public int standaloneControlRows(int slot){return half(slot)?4:8;}
 public int stateCount(){return keyboard?16:this==DIAGONAL?8:this==HALF_DIAGONAL?12:6;}
 public State decode(int metadata){int facing=horizontal?HORIZONTAL_FACES[metadata&3]:this==HALF_DIAGONAL?(metadata&7)<6?metadata&7:2:(metadata&7)%6;return new State(facing,keyboard&&(metadata&4)!=0,(keyboard||this==HALF_DIAGONAL)&&(metadata&8)!=0,this==DIAGONAL&&(metadata&4)!=0);}
 public int encode(State state){int facing=horizontal?state.facing==3?0:state.facing==4?1:state.facing==2?2:3:state.facing;return facing|(keyboard&&state.keyboard?4:0)|((keyboard||this==HALF_DIAGONAL)&&state.upper?8:0)|(this==DIAGONAL&&state.inverted?4:0);}
 public static ScreenFamily fromId(String id){for(ScreenFamily family:values())if(family.id.equals(id))return family;return null;}
 public static final class State {public final int facing;public final boolean keyboard,upper,inverted;public State(int facing,boolean keyboard,boolean upper,boolean inverted){if(facing<0||facing>5)throw new IllegalArgumentException("Invalid screen face");this.facing=facing;this.keyboard=keyboard;this.upper=upper;this.inverted=inverted;}}
}
