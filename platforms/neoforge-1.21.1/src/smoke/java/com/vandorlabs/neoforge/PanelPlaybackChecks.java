package com.vandorlabs.neoforge;

import com.vandorlabs.neoforge.shape.PanelPlayback;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.*;

/** Actual native host publication and byte boundary checks against frozen legacy rules. */
final class PanelPlaybackChecks {
    private static int checks;
    private static void require(boolean value,String message){checks++;if(!value)throw new AssertionError("Panel playback: "+message);}
    private static void value(CompoundTag tag,String key,int kind,int value){switch(kind){case 1->tag.putByte(key,(byte)value);case 2->tag.putInt(key,value);case 3->tag.putShort(key,(short)value);case 4->tag.putLong(key,value);case 5->tag.putString(key,Integer.toString(value));case 6->tag.putBoolean(key,value!=0);}}
    private static int expected(int kind,int value){int byteValue=kind==6?(value==0?0:1):(byte)value;return (kind==1||kind==6)&&byteValue>=0&&byteValue<=2?byteValue:1;}
    static void run(HolderLookup.Provider registries){
        for(int kind=0;kind<7;kind++)for(int speedKind=0;speedKind<7;speedKind++)for(int input=-130;input<=130;input++){
            CompoundTag tag=new CompoundTag();value(tag,"GlassPlaybackMode",kind,input);value(tag,"GlassPlaybackSpeed",speedKind,2-input);tag.putString("FuturePanelPlayback","opaque");var original=tag.copy();var host=new WallHostChecks.Counting(true);host.loadWithComponents(tag,registries);int mode=expected(kind,input),speed=expected(speedKind,2-input);require(host.panelPlayback().mode()==mode&&host.panelPlayback().speed()==speed&&tag.equals(original),"Strict BYTE/native read, original unchanged");require(host.wallAppearance().playback()==host.panelPlayback(),"Complete immutable publication");var saved=host.saveWithoutMetadata(registries);boolean sparse=mode==1&&speed==1;require(saved.contains("GlassPlaybackMode")==!sparse&&saved.contains("GlassPlaybackSpeed")==!sparse,"Sparse default removal");if(!sparse)require(saved.contains("GlassPlaybackMode",Tag.TAG_BYTE)&&saved.contains("GlassPlaybackSpeed",Tag.TAG_BYTE)&&saved.getByte("GlassPlaybackMode")==mode&&saved.getByte("GlassPlaybackSpeed")==speed,"Both exact bytes");require(saved.getString("FuturePanelPlayback").equals("opaque"),"Opaque retained");host.reset();var stable=host.wallAppearance();require(host.configurePanelGlass(host.panelGlassSettings(),new PanelPlayback(mode,speed),host.wallSettings().joinPortholes,host.wallSettings().portholeShape)&&host.wallAppearance()==stable&&host.dirty==0&&host.models==0,"Equal playback is quiescent");
        }
        var host=new WallHostChecks.Counting(true);for(int mode=0;mode<3;mode++)for(int speed=0;speed<3;speed++){
            PanelPlayback next=new PanelPlayback(mode,speed);boolean changed=!host.panelPlayback().equals(next);host.reset();require(host.configurePanelGlass(host.panelGlassSettings(),next,true,0)&&host.dirty==(changed?1:0)&&host.models==(changed?1:0),"One atomic mixed publication");require(host.wallAppearance().playback()==host.panelPlayback(),"Playback visible before callback");var stable=host.wallAppearance();host.reset();require(!host.configurePanelGlass(host.panelGlassSettings(),null,false,1)&&host.wallAppearance()==stable&&host.dirty==0,"Invalid entire edit rejects");
            var data=net.minecraft.world.item.component.CustomData.of(host.saveWithId(registries));var stack=new net.minecraft.world.item.ItemStack(VandorLabsBootstrap.PROGRAMMABLE_ITEM.get());stack.set(net.minecraft.core.component.DataComponents.BLOCK_ENTITY_DATA,data);var encoded=(CompoundTag)stack.save(registries);var decoded=net.minecraft.world.item.ItemStack.parseOptional(registries,encoded);var loaded=new WallHostChecks.Counting(true);loaded.loadWithComponents(decoded.get(net.minecraft.core.component.DataComponents.BLOCK_ENTITY_DATA).copyTag(),registries);require(loaded.panelPlayback().equals(next),"Actual configured component codec retains pair");
        }
        System.out.println("[vandorlabs][panel-playback] PASS typedCases=12789 checks="+checks+" sparse fields immutable publication atomic no-op configured-component roundtrip; renderer acceptance separate");
    }
}
