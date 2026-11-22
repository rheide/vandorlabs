package com.vandorlabs.canopy;
import net.minecraft.nbt.NBTTagCompound;
import com.vandorlabs.tiles.GlassTextures;
import com.vandorlabs.screen.VehicleInstrument;
import com.vandorlabs.render.TextureLayoutMode;
/** Stable settings shared by placed blocks, configured items and captured craft. */
public final class LargeCanopySettings {
    public int depth=16,scale=71,shade=0,tint=1,mount=0,motion=0,hinge=2,angle=1,material=-1;
    public boolean openable=false,redstone=false,slideForward=false,tileMaterial=false,activateOnPower=true;
    private int textureLayout;
    public int textureLayout(){return textureLayout==2?2:tileMaterial?1:0;}
    public void textureLayout(int mode){if(!com.vandorlabs.render.TextureLayoutMode.valid(mode))throw new IllegalArgumentException("Invalid canopy texture layout");textureLayout=mode;tileMaterial=mode==1;}
    private boolean separateSide;
    private int sideMaterial=-1,sideShade,sideTint=1,sideLayout;

    public static boolean supportsDisplays(String kind){
        return supports(kind)||kind.equals("canopy")||kind.equals("long_canopy")||kind.equals("regular_canopy_glass");
    }
    public boolean hasSideFinish(){return separateSide;}
    public void separateSide(){
        if(separateSide)return;
        separateSide=true;sideMaterial=VehicleInstrument.includes(material)?-1:material;
        sideShade=shade;sideTint=tint;sideLayout=textureLayout();
    }
    public int material(boolean side){return side&&separateSide?sideMaterial:material;}
    public int shade(boolean side){return side&&separateSide?sideShade:shade;}
    public int tint(boolean side){return side&&separateSide?sideTint:tint;}
    public int textureLayout(boolean side){return side&&separateSide?sideLayout:textureLayout();}
    public void sideFinish(int material,int shade,int tint,int layout){
        separateSide=true;sideMaterial=material;sideShade=shade;sideTint=tint;sideLayout=layout;
    }
    public boolean hasDisplay(){
        return VehicleInstrument.includes(material)||separateSide&&VehicleInstrument.includes(sideMaterial);
    }
    public boolean mayRenderTranslucent(){
        return !com.vandorlabs.tiles.GlassMaterials.opaque(material,tint)
                || separateSide&&!com.vandorlabs.tiles.GlassMaterials.opaque(sideMaterial,sideTint);
    }
    public static boolean validMaterial(int choice){
        return choice==-1||VehicleInstrument.includes(choice)||com.vandorlabs.tiles.ScreenHousingTextures.validChoice(choice);
    }
    public com.vandorlabs.redstone.ChannelList channels=com.vandorlabs.redstone.ChannelList.EMPTY;
    public static boolean supports(String kind){return kind.equals("front_canopy_glass") || kind.equals("large_front_canopy_glass");}
    public static boolean valid(int depth,int scale,int shade,int tint){return depth>=0 && depth<=80 && scale>=10 && scale<=100 && GlassTextures.valid(shade) && GlassTextures.validTint(tint);}
    public int length(){return Math.max(1,(depth+15)/16);}
    private KeyState keyState;
    /** Public settings may change directly; compare exact values before reusing a key. */
    public String key(){KeyState cached=keyState;if(cached==null||!cached.matches(this))keyState=cached=new KeyState(this);return cached.text;}
    private static int flags(LargeCanopySettings s){return (s.slideForward?1:0)|(s.openable?2:0)|(s.redstone?4:0)|(s.activateOnPower?8:0);}
    private static final class KeyState {
        final int depth,scale,shade,tint,mount,motion,hinge,angle,material,layout,flags,sideMaterial,sideShade,sideTint,sideLayout;
        final boolean separateSide;
        final com.vandorlabs.redstone.ChannelList channels;final String text;
        KeyState(LargeCanopySettings s){
            depth=s.depth;scale=s.scale;shade=s.shade;tint=s.tint;mount=s.mount;motion=s.motion;hinge=s.hinge;angle=s.angle;material=s.material;layout=s.textureLayout();flags=flags(s);channels=s.channels;
            separateSide=s.separateSide;sideMaterial=s.sideMaterial;sideShade=s.sideShade;sideTint=s.sideTint;sideLayout=s.sideLayout;
            text=depth+":"+scale+":"+shade+":"+tint+":"+mount+":"+motion+":"+hinge+":"+angle+":"+material+":"+layout+":"+((flags&1)!=0)+":"+((flags&2)!=0)+":"+((flags&4)!=0)+":"+((flags&8)!=0)+":"+channels
                    +(separateSide?":side:"+sideMaterial+":"+sideShade+":"+sideTint+":"+sideLayout:"");
        }
        boolean matches(LargeCanopySettings s){return depth==s.depth&&scale==s.scale&&shade==s.shade&&tint==s.tint&&mount==s.mount&&motion==s.motion&&hinge==s.hinge&&angle==s.angle&&material==s.material&&layout==s.textureLayout()&&flags==flags(s)&&java.util.Objects.equals(channels,s.channels)
                &&separateSide==s.separateSide&&sideMaterial==s.sideMaterial&&sideShade==s.sideShade&&sideTint==s.sideTint&&sideLayout==s.sideLayout;}
    }
    public void write(NBTTagCompound tag){tag.setInteger("CanopyMount",mount);tag.setInteger("CanopyMotion",motion);tag.setBoolean("CanopySlideForward",slideForward);tag.setBoolean("CanopyTileMaterial",tileMaterial);tag.setInteger("CanopyTextureLayout",textureLayout());tag.setInteger("CanopyHinge",hinge);tag.setInteger("CanopyAngle",angle);tag.setInteger("CanopyMaterial",material);tag.setBoolean("CanopyOpenable",openable);tag.setBoolean("CanopyRedstone",redstone);tag.setBoolean("CanopyActivateOnPower",activateOnPower);tag.setIntArray("CanopyChannels",channels.toArray());tag.setInteger("CanopyDepth",depth);tag.setInteger("CanopyScale",scale);tag.setInteger("CanopyShade",shade);tag.setInteger("CanopyTint",tint);
        if(separateSide){
            tag.setInteger("CanopySideMaterial",sideMaterial);
            tag.setInteger("CanopySideShade",sideShade);
            tag.setInteger("CanopySideTint",sideTint);
            tag.setInteger("CanopySideTextureLayout",sideLayout);
        }else{
            tag.removeTag("CanopySideMaterial");
            tag.removeTag("CanopySideShade");
            tag.removeTag("CanopySideTint");
            tag.removeTag("CanopySideTextureLayout");
        }
    }
    public void read(NBTTagCompound tag){mount=Math.max(0,Math.min(3,tag.getInteger("CanopyMount")));motion=Math.max(0,Math.min(1,tag.getInteger("CanopyMotion")));hinge=tag.hasKey("CanopyHinge")?Math.max(0,Math.min(3,tag.getInteger("CanopyHinge"))):2;angle=tag.hasKey("CanopyAngle")?Math.max(0,Math.min(3,tag.getInteger("CanopyAngle"))):1;material=tag.hasKey("CanopyMaterial")?tag.getInteger("CanopyMaterial"):-1;slideForward=tag.getBoolean("CanopySlideForward");tileMaterial=tag.getBoolean("CanopyTileMaterial");textureLayout=tag.hasKey("CanopyTextureLayout",3) && tag.getInteger("CanopyTextureLayout")==2?2:0;openable=tag.getBoolean("CanopyOpenable");redstone=tag.getBoolean("CanopyRedstone");activateOnPower=!tag.hasKey("CanopyActivateOnPower",1) || tag.getBoolean("CanopyActivateOnPower");channels=com.vandorlabs.redstone.ChannelData.read(tag,"CanopyChannels",com.vandorlabs.redstone.ChannelList.EMPTY);depth=tag.hasKey("CanopyDepth",3)?Math.max(0,Math.min(80,tag.getInteger("CanopyDepth"))):16;scale=tag.hasKey("CanopyScale",3)?Math.max(10,Math.min(100,tag.getInteger("CanopyScale"))):71;shade=GlassTextures.clamp(tag.getInteger("CanopyShade"));tint=tag.hasKey("CanopyTint",3)?GlassTextures.clampTint(tag.getInteger("CanopyTint")):1;
        separateSide=tag.hasKey("CanopySideMaterial",3);
        int savedSide=tag.getInteger("CanopySideMaterial");
        sideMaterial=separateSide&&validMaterial(savedSide)?savedSide:-1;
        sideShade=separateSide?GlassTextures.clamp(tag.getInteger("CanopySideShade")):0;
        sideTint=separateSide?GlassTextures.clampTint(tag.getInteger("CanopySideTint")):1;
        int savedLayout=tag.getInteger("CanopySideTextureLayout");
        sideLayout=separateSide&&TextureLayoutMode.valid(savedLayout)?savedLayout:0;
    }
}
