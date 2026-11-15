package com.vandorlabs.neoforge.transfer;

import com.vandorlabs.neoforge.ModernFinishCatalog;
import com.vandorlabs.neoforge.ModernHousingTags;
import com.vandorlabs.neoforge.storage.NativeStorageSettings;
import com.vandorlabs.persistence.HousingSettings;
import com.vandorlabs.persistence.StairState;
import com.vandorlabs.surface.TextureJoinSettings;
import java.util.*;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.*;
import net.minecraft.world.level.block.SignBlock;
import net.minecraft.world.level.block.entity.SignText;
import net.minecraft.world.level.block.state.BlockState;

/** Strict allowlisted public appearance, shared by moving block renderers; never includes physical contents. */
public final class NativeSnapshotAppearance {
    private static final Set<String> HOUSING_KEYS=Set.of(HousingSettings.MAIN,HousingSettings.VERSION,HousingSettings.SIDE,HousingSettings.LAYOUT,HousingSettings.TILE_SIDES,HousingSettings.FACES_ENABLED,HousingSettings.FACES,TextureJoinSettings.DIRECTION_TAG,TextureJoinSettings.PARITY_TAG,StairState.TREAD,StairState.ROLL);
    private static final Set<String> SIGN_KEYS=Set.of("messages","filtered_messages","color","has_glowing_text");
    public static CompoundTag project(BlockState state,CompoundTag raw,HolderLookup.Provider registries) {
        String name=BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();var result=new CompoundTag();
        boolean housing=name.equals("vandorlabs:programmable_block")||name.equals("vandorlabs:programmable_slab")||name.equals("vandorlabs:programmable_stairs");
        if(housing||name.equals("vandorlabs:programmable_storage")){
            var selected=new CompoundTag();for(String key:HOUSING_KEYS){Tag value=raw.get(key);if(value!=null)selected.put(key,value.copy());}
            var tags=new ModernHousingTags(selected);
            if(housing){HousingSettings.read(tags,0,true,ModernFinishCatalog::storedChoice,ModernFinishCatalog::validChoice).write(new ModernHousingTags(result));TextureJoinSettings.read(tags).write(new ModernHousingTags(result));}
            else NativeStorageSettings.read(selected).write(result);
            if(name.equals("vandorlabs:programmable_stairs"))StairState.read(tags).write(new ModernHousingTags(result));
        }else if(name.equals("vandorlabs:module_rack")){
            return com.vandorlabs.neoforge.rack.NativeRackData.appearance(raw);
        }else if(name.equals("vandorlabs:turntable_surface")){
            return com.vandorlabs.neoforge.surface.NativeSurfaceAppearance.project(raw);
        }else if(state.getBlock() instanceof SignBlock){
            for(String side:List.of("front_text","back_text")){
                CompoundTag face=raw.contains(side,Tag.TAG_COMPOUND)?raw.getCompound(side):new CompoundTag();
                SignText decoded=face.isEmpty()?new SignText():readSign(face,registries);var clean=new SignText().setColor(decoded.getColor()).setHasGlowingText(decoded.hasGlowingText());
                for(int line=0;line<4;line++)clean=clean.setMessage(line,publicText(decoded.getMessage(line,false)),publicText(decoded.getMessage(line,true)));
                Tag encoded=SignText.DIRECT_CODEC.encodeStart(registries.createSerializationContext(NbtOps.INSTANCE),clean).getOrThrow();if(!(encoded instanceof CompoundTag compound))throw new IllegalArgumentException("Sign projection envelope");result.put(side,compound);
            }
            if(raw.contains("is_waxed")&&(!raw.contains("is_waxed",Tag.TAG_BYTE)||raw.getByte("is_waxed")<0||raw.getByte("is_waxed")>1))throw new IllegalArgumentException("Sign wax projection flag");result.putBoolean("is_waxed",raw.getBoolean("is_waxed"));
        }
        return result;
    }
    private static SignText readSign(CompoundTag raw,HolderLookup.Provider registries){
        var face=new CompoundTag();for(String key:SIGN_KEYS){Tag value=raw.get(key);if(value!=null)face.put(key,value.copy());}
        for(String key:List.of("messages","filtered_messages")){
            if(!face.contains(key)&&key.equals("filtered_messages"))continue;
            if(!(face.get(key) instanceof ListTag lines)||lines.size()!=4||lines.getElementType()!=Tag.TAG_STRING)throw new IllegalArgumentException("Sign projection line schema");for(int i=0;i<4;i++)jsonDepth(lines.getString(i));
        }
        return SignText.DIRECT_CODEC.parse(registries.createSerializationContext(NbtOps.INSTANCE),face).getOrThrow();
    }
    private static void jsonDepth(String text){
        if(text.length()>8192)throw new IllegalArgumentException("Sign projection text limit");boolean quoted=false,escaped=false;int depth=0;
        for(int i=0;i<text.length();i++){char c=text.charAt(i);if(quoted){if(escaped)escaped=false;else if(c=='\\')escaped=true;else if(c=='\"')quoted=false;}else if(c=='\"')quoted=true;else if(c=='{'||c=='['){if(++depth>32)throw new IllegalArgumentException("Sign projection text depth");}else if(c=='}'||c==']'){if(--depth<0)throw new IllegalArgumentException("Sign projection text structure");}}if(quoted||depth!=0)throw new IllegalArgumentException("Sign projection text structure");
    }
    private static Component publicText(Component input){
        MutableComponent[] result={null};int[] budget={0,0};input.<Void>visit((style,text)->{if(text.isEmpty())return Optional.empty();budget[0]=Math.addExact(budget[0],text.length());if(++budget[1]>256||budget[0]>8192)throw new IllegalArgumentException("Sign public text limit");var piece=Component.literal(text).setStyle(style.withClickEvent(null).withHoverEvent(null).withInsertion(null));if(result[0]==null)result[0]=piece;else result[0].append(piece);return Optional.empty();},Style.EMPTY);return result[0]==null?Component.empty():result[0];
    }
    public static boolean canonical(BlockState state,CompoundTag projection,HolderLookup.Provider registries){return project(state,projection,registries).equals(projection);}
    private NativeSnapshotAppearance(){}
}
