package com.vandorlabs.neoforge.gear;

import com.vandorlabs.gear.core.ArmHinge;
import com.vandorlabs.gear.core.ArmResource;
import com.vandorlabs.gear.core.GearMotion;
import com.vandorlabs.redstone.ChannelList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;

/** Immutable Source-compatible choices; ownership and live phase never enter a configured item. */
public record NativeGearSettings(int trigger,int direction,ChannelList channels,boolean legacyAnyChannels,
        boolean pushBlocks,boolean rotatePayload,ArmHinge.Mode hingeMode,boolean join,int joinRange,
        boolean carryLoad,int loadHeight,double startAngle,double activeAngle,double activeExtension,int footFacing,int kinkRaw,int landingAnglesVersion,ArmResource resourceTransfer) {
    public NativeGearSettings(int trigger,int direction,ChannelList channels,boolean legacyAnyChannels,boolean pushBlocks,boolean rotatePayload,ArmHinge.Mode hingeMode,boolean join,int joinRange,boolean carryLoad,int loadHeight,double startAngle,double activeAngle,double activeExtension,int footFacing,int kinkRaw,int landingAnglesVersion){
        this(trigger,direction,channels,legacyAnyChannels,pushBlocks,rotatePayload,hingeMode,join,joinRange,carryLoad,loadHeight,startAngle,activeAngle,activeExtension,footFacing,kinkRaw,landingAnglesVersion,ArmResource.DISABLED);
    }
    /** Existing callers and old items remain on the exact legacy landing-angle policy. */
    public NativeGearSettings(int trigger,int direction,ChannelList channels,boolean legacyAnyChannels,boolean pushBlocks,boolean rotatePayload,ArmHinge.Mode hingeMode,boolean join,int joinRange,boolean carryLoad,int loadHeight,double startAngle,double activeAngle,double activeExtension,int footFacing,int kinkRaw){
        this(trigger,direction,channels,legacyAnyChannels,pushBlocks,rotatePayload,hingeMode,join,joinRange,carryLoad,loadHeight,startAngle,activeAngle,activeExtension,footFacing,kinkRaw,-1);
    }
    /** Existing constructors retain the old center-only motion used by saved fixtures and descriptors. */
    public NativeGearSettings(int trigger,int direction,ChannelList channels,boolean legacyAnyChannels,boolean pushBlocks,boolean rotatePayload,boolean middleHinge,boolean join,int joinRange,boolean carryLoad,int loadHeight,double startAngle,double activeAngle,double activeExtension,int footFacing){
        this(trigger,direction,channels,legacyAnyChannels,pushBlocks,rotatePayload,middleHinge?ArmHinge.Mode.LEGACY_CENTER:ArmHinge.Mode.SINGLE,join,joinRange,carryLoad,loadHeight,startAngle,activeAngle,activeExtension,footFacing,-1);
    }
    public NativeGearSettings {
        if(resourceTransfer==null||landingAnglesVersion< -1||landingAnglesVersion>1||trigger<0||trigger>2||direction<0||direction>3||channels==null||hingeMode==null||kinkRaw< -1||kinkRaw>3||joinRange<1||joinRange>32||loadHeight<1||loadHeight>32||footFacing<0||footFacing>3
                ||!Double.isFinite(startAngle)||startAngle< -180||startAngle>180||Math.rint(startAngle/15)*15!=startAngle
                ||!Double.isFinite(activeAngle)||activeAngle< -180||activeAngle>180||Math.rint(activeAngle/15)*15!=activeAngle
                ||!Double.isFinite(activeExtension)||activeExtension< -8||activeExtension>8)throw new IllegalArgumentException("Invalid arm settings");
    }
    public boolean middleHinge(){return hingeMode!=ArmHinge.Mode.SINGLE;}
    public int kinkDirection(){return kinkRaw<0?direction:kinkRaw;}
    public static NativeGearSettings defaults(){return new NativeGearSettings(0,0,ChannelList.EMPTY,false,false,false,false,false,32,false,1,0,90,1,2);}
    public NativeGearSettings withFootFacing(int facing){return new NativeGearSettings(trigger,direction,channels,legacyAnyChannels,pushBlocks,rotatePayload,hingeMode,join,joinRange,carryLoad,loadHeight,startAngle,activeAngle,activeExtension,facing,kinkRaw,landingAnglesVersion,resourceTransfer);}
    public NativeGearSettings withHingeMode(ArmHinge.Mode mode){return new NativeGearSettings(trigger,direction,channels,legacyAnyChannels,pushBlocks,rotatePayload,mode,join,joinRange,carryLoad,loadHeight,startAngle,activeAngle,activeExtension,footFacing,kinkRaw,landingAnglesVersion,resourceTransfer);}
    public com.vandorlabs.gear.core.ArmLandingAngles angles(){return new com.vandorlabs.gear.core.ArmLandingAngles(landingAnglesVersion,startAngle,activeAngle);}
    public NativeGearSettings withAngles(com.vandorlabs.gear.core.ArmLandingAngles angles){return new NativeGearSettings(trigger,direction,channels,legacyAnyChannels,pushBlocks,rotatePayload,hingeMode,join,joinRange,carryLoad,loadHeight,angles.rawStart,angles.rawEnd,activeExtension,footFacing,kinkRaw,angles.version,resourceTransfer);}
    /** Only a new explicit angle edit/copy adopts the physical legacy baseline. Other edits preserve ignored saved raw values. */
    public NativeGearSettings retainLegacyAngles(NativeGearSettings previous,boolean landing){return landing&&landingAnglesVersion!=1?withAngles(previous.angles()):this;}
    public NativeGearSettings copied(boolean landing){return withAngles(angles().copied(landing));}
    private static int integer(CompoundTag tag,String key,int fallback){return tag.contains(key,Tag.TAG_INT)?tag.getInt(key):fallback;}
    private static double number(CompoundTag tag,String key,double fallback){return tag.contains(key,Tag.TAG_DOUBLE)?tag.getDouble(key):fallback;}
    private static void typed(CompoundTag tag,int type,String...keys){for(String key:keys)if(tag.contains(key)&&!tag.contains(key,type))throw new IllegalArgumentException("Malformed arm setting "+key);}
    private static ArmResource readResource(CompoundTag tag,String key){return ArmResource.read(tag.contains(key),tag.contains(key,Tag.TAG_INT),tag.getInt(key));}
    public static NativeGearSettings readChoices(CompoundTag tag){
        typed(tag,Tag.TAG_INT,"Trigger","Direction","JoinRange","LoadHeight","FootFacing","HingeMode","KinkDirection","LandingAnglesVersion");typed(tag,Tag.TAG_STRING,"Channels");typed(tag,Tag.TAG_BYTE,"LegacyAnyChannels","PushBlocks","RotatePayload","MiddleHinge","JoinArms","CarryLoad","Deployed");typed(tag,Tag.TAG_DOUBLE,"StartAngle","ActiveAngle","ActiveExtension");
        if(tag.getString("Channels").length()>ChannelList.MAX_TEXT_LENGTH)throw new IllegalArgumentException("Arm channel text bound");var channels=ChannelList.parse(tag.getString("Channels"));if(channels==null)throw new IllegalArgumentException("Invalid arm channels");
        int kink=tag.contains("KinkDirection")?tag.getInt("KinkDirection"):-1;if(tag.contains("KinkDirection")&&kink<0)throw new IllegalArgumentException("Unknown arm kink direction");
        var hinge=ArmHinge.readChoice(tag.getBoolean("MiddleHinge"),tag.contains("HingeMode")?tag.getInt("HingeMode"):null);
        return new NativeGearSettings(integer(tag,"Trigger",0),integer(tag,"Direction",0),channels,tag.getBoolean("LegacyAnyChannels"),tag.getBoolean("PushBlocks"),tag.getBoolean("RotatePayload"),hinge,tag.getBoolean("JoinArms"),integer(tag,"JoinRange",32),tag.getBoolean("CarryLoad"),integer(tag,"LoadHeight",1),number(tag,"StartAngle",0),number(tag,"ActiveAngle",90),number(tag,"ActiveExtension",1),integer(tag,"FootFacing",2),kink,com.vandorlabs.gear.core.ArmLandingAngles.version(tag.contains("LandingAnglesVersion"),tag.contains("LandingAnglesVersion",Tag.TAG_INT),tag.getInt("LandingAnglesVersion")),readResource(tag,ArmResource.CHOICE));
    }
    public static NativeGearSettings readSaved(CompoundTag tag){
        typed(tag,Tag.TAG_INT,"GearTrigger","ArmJoinRange","ArmLoadHeight","ArmFootFacing","GearFootFacing","ArmHingeMode","ArmLandingAnglesVersion");typed(tag,Tag.TAG_STRING,"GearDirection","GearChannels","ArmKinkDirection");typed(tag,Tag.TAG_BYTE,"GearLegacyAnyChannels","ArmPushBlocks","ArmRotatePayload","ArmMiddleHinge","ArmJoinEnabled","ArmCarryLoad");typed(tag,Tag.TAG_DOUBLE,"ArmStartAngle","ArmActiveAngle","ArmActiveExtension");
        var choices=new CompoundTag();choices.putInt(ArmResource.CHOICE,readResource(tag,ArmResource.SAVED).ordinal());if(tag.contains("ArmLandingAnglesVersion"))choices.putInt("LandingAnglesVersion",tag.getInt("ArmLandingAnglesVersion"));choices.putInt("Trigger",integer(tag,"GearTrigger",0));String name=tag.getString("GearDirection");int direction=0;if(!name.isEmpty())try{direction=GearMotion.Direction.valueOf(name).ordinal();}catch(IllegalArgumentException invalid){throw new IllegalArgumentException("Unknown arm direction",invalid);}
        choices.putInt("Direction",direction);choices.putString("Channels",tag.getString("GearChannels"));choices.putBoolean("LegacyAnyChannels",tag.getBoolean("GearLegacyAnyChannels"));choices.putBoolean("PushBlocks",tag.getBoolean("ArmPushBlocks"));choices.putBoolean("RotatePayload",tag.getBoolean("ArmRotatePayload"));choices.putBoolean("MiddleHinge",tag.getBoolean("ArmMiddleHinge"));
        choices.putInt("HingeMode",ArmHinge.readSaved(tag.getBoolean("ArmMiddleHinge"),tag.contains("ArmHingeMode")?tag.getInt("ArmHingeMode"):null).ordinal());
        if(tag.contains("ArmKinkDirection"))choices.putInt("KinkDirection",GearMotion.Direction.valueOf(tag.getString("ArmKinkDirection")).ordinal());
        choices.putBoolean("JoinArms",tag.getBoolean("ArmJoinEnabled"));choices.putInt("JoinRange",integer(tag,"ArmJoinRange",32));choices.putBoolean("CarryLoad",tag.getBoolean("ArmCarryLoad"));choices.putInt("LoadHeight",integer(tag,"ArmLoadHeight",1));choices.putDouble("StartAngle",number(tag,"ArmStartAngle",0));choices.putDouble("ActiveAngle",number(tag,"ArmActiveAngle",90));choices.putDouble("ActiveExtension",number(tag,"ArmActiveExtension",1));choices.putInt("FootFacing",integer(tag,"ArmFootFacing",integer(tag,"GearFootFacing",2)));return readChoices(choices);
    }
    public CompoundTag choices(){
        var tag=new CompoundTag();if(resourceTransfer!=ArmResource.DISABLED)tag.putInt(ArmResource.CHOICE,resourceTransfer.ordinal());if(landingAnglesVersion>=0)tag.putInt("LandingAnglesVersion",landingAnglesVersion);tag.putInt("Trigger",trigger);tag.putInt("Direction",direction);tag.putString("Channels",channels.toString());tag.putBoolean("LegacyAnyChannels",legacyAnyChannels);tag.putBoolean("PushBlocks",pushBlocks);tag.putBoolean("RotatePayload",rotatePayload);tag.putBoolean("MiddleHinge",middleHinge());tag.putInt("HingeMode",hingeMode.ordinal());tag.putInt("KinkDirection",kinkDirection());tag.putBoolean("JoinArms",join);tag.putInt("JoinRange",joinRange);tag.putBoolean("CarryLoad",carryLoad);tag.putInt("LoadHeight",loadHeight);tag.putDouble("StartAngle",startAngle);tag.putDouble("ActiveAngle",activeAngle);tag.putDouble("ActiveExtension",activeExtension);tag.putInt("FootFacing",footFacing);return tag;
    }
    public CompoundTag editableChoices(){var tag=choices();if(kinkRaw<0)tag.remove("KinkDirection");return tag;}
    public void writeSaved(CompoundTag tag){
        if(resourceTransfer!=ArmResource.DISABLED)tag.putInt(ArmResource.SAVED,resourceTransfer.ordinal());else tag.remove(ArmResource.SAVED);
        if(landingAnglesVersion>=0)tag.putInt("ArmLandingAnglesVersion",landingAnglesVersion);else tag.remove("ArmLandingAnglesVersion");
        tag.putInt("GearTrigger",trigger);tag.putString("GearDirection",GearMotion.Direction.values()[direction].name());tag.putString("GearChannels",channels.toString());tag.putBoolean("GearLegacyAnyChannels",legacyAnyChannels);tag.putBoolean("ArmPushBlocks",pushBlocks);tag.putBoolean("ArmRotatePayload",rotatePayload);tag.putBoolean("ArmMiddleHinge",middleHinge());
        if(hingeMode==ArmHinge.Mode.DOUBLE)tag.putInt("ArmHingeMode",hingeMode.ordinal());else tag.remove("ArmHingeMode");
        if(kinkRaw>=0)tag.putString("ArmKinkDirection",GearMotion.Direction.values()[kinkRaw].name());else tag.remove("ArmKinkDirection");
        tag.putBoolean("ArmJoinEnabled",join);tag.putInt("ArmJoinRange",joinRange);tag.putBoolean("ArmCarryLoad",carryLoad);tag.putInt("ArmLoadHeight",loadHeight);tag.putDouble("ArmStartAngle",startAngle);tag.putDouble("ArmActiveAngle",activeAngle);tag.putDouble("ArmActiveExtension",activeExtension);tag.putInt("ArmFootFacing",footFacing);
    }
    /** Compare known optional additions without rewriting retained journal preimages or unknown keys. */
    static boolean sameRetainedChoices(CompoundTag a,CompoundTag b){if(a.equals(b))return true;try{var left=a.copy();var right=b.copy();expandRetained(left);expandRetained(right);return left.equals(right);}catch(IllegalArgumentException malformed){return false;}}
    private static void expandRetained(CompoundTag tag){typed(tag,Tag.TAG_BYTE,"MiddleHinge");typed(tag,Tag.TAG_INT,"Direction","HingeMode","KinkDirection","LandingAnglesVersion");com.vandorlabs.gear.core.ArmLandingAngles.version(tag.contains("LandingAnglesVersion"),tag.contains("LandingAnglesVersion",Tag.TAG_INT),tag.getInt("LandingAnglesVersion"));if(!tag.contains("LandingAnglesVersion"))tag.putInt("LandingAnglesVersion",0);var hinge=ArmHinge.readSaved(tag.getBoolean("MiddleHinge"),tag.contains("HingeMode")?tag.getInt("HingeMode"):null);tag.putInt("HingeMode",hinge.ordinal());if(!tag.contains("KinkDirection"))tag.putInt("KinkDirection",integer(tag,"Direction",0));}
}
