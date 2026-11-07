package com.vandorlabs.neoforge.screen;

import com.vandorlabs.neoforge.credentials.NativeCredentialItems;
import com.vandorlabs.screen.ScreenFamily;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerLevel;

/** Hidden physical ownership persists privately with a separate selected-surface acceptance path. */
final class ScreenStoredChipChecks {
    private static int checks;
    private static final BlockPos BASE=new BlockPos(5100,100,5100);
    private static void need(boolean value,String why){checks++;if(!value)throw new AssertionError("Stored Screen chip: "+why);}
    private static CompoundTag original(){var root=new CompoundTag();root.putString("ForeignRoot","private-root");var reader=new CompoundTag();reader.putBoolean("AnyToken",false);reader.putBoolean("ChipSlot",false);reader.putInt("AllowedTokens",2);reader.putInt("TokenBehavior",3);reader.putLong("TokenPulseUntil",1234);reader.putUUID("TokenReturnTo",new java.util.UUID(12,34));var item=new CompoundTag();item.putString("id","vandorlabs:data_chip");item.putByte("Count",(byte)64);item.putShort("Damage",(short)2);item.putString("FutureStackRoot","private-token-root");var tag=new CompoundTag();tag.putString("ChipType","journal");tag.putString("JournalTitle","Saved chip");var pages=new ListTag();pages.add(StringTag.valueOf("Saved private journal"));tag.put("JournalPages",pages);tag.putString("ForeignTag","private-token-tag");item.put("tag",tag);var caps=new CompoundTag();caps.putString("foreign:chip","opaque-capability");item.put("ForgeCaps",caps);reader.put("InsertedToken",item);root.put("ScreenChipReader",reader);return root;}
    private static void verify(NativeScreenEntity tile,ServerLevel level){
        need(tile!=null&&!tile.inert(),"known hidden owner remains usable");var saved=tile.saveWithoutMetadata(level.registryAccess());need(saved.getString("ForeignRoot").equals("private-root"),"private Screen fields retained");var reader=saved.getCompound("ScreenChipReader");need(reader.getBoolean("AnyToken")&&reader.getBoolean("ChipSlot")&&reader.getInt("AllowedTokens")==127&&reader.getInt("TokenBehavior")==0&&!reader.contains("TokenReturnToMost")&&!reader.contains("TokenReturnToLeast"),"Source embedded passive policy");var stack=NativeCredentialItems.readStack(reader.getCompound("InsertedToken"),level.registryAccess());need(stack.getCount()==1&&NativeCredentialItems.company(stack)==2,"exactly one current owned chip");need(NativeCredentialItems.legacyCredentialData(stack).equals(original().getCompound("ScreenChipReader").getCompound("InsertedToken")),"complete original item/tag/ForgeCaps retained");need(!tile.getUpdateTag(level.registryAccess()).contains("ScreenChipReader")&&!tile.getUpdateTag(level.registryAccess()).toString().contains("private-token"),"physical ownership excluded from client projection");
    }
    static void run(ServerLevel level,java.util.Map<ScreenFamily,NativeScreenBlock> blocks,boolean read){
        for(ScreenFamily family:ScreenFamily.values()){var pos=BASE.east(family.ordinal()*3);level.getChunkAt(pos);
            if(!read){level.setBlock(pos,blocks.get(family).defaultBlockState(),2);var tile=(NativeScreenEntity)level.getBlockEntity(pos);var raw=original();var input=raw.copy();tile.loadWithComponents(raw,level.registryAccess());need(raw.equals(input),"load input defensive");verify(tile,level);
                var choices=NativeScreenItemSettings.choices(tile,level.registryAccess());var prepared=NativeScreenItemSettings.prepare(tile.getType(),pos,tile.getBlockState(),family,choices,level.registryAccess());prepared.snapshot().getCompound("ScreenChipReader").remove("InsertedToken");var copy=new NativeScreenEntity(tile.getType(),pos,tile.getBlockState(),family,NativeScreenCatalog.INSTANCE);NativeScreenItemSettings.install(copy,prepared);verify(copy,level);
                for(int failure=0;failure<3;failure++){var bad=original();if(failure==0)bad.getCompound("ScreenChipReader").putString("FutureOwner","opaque");else if(failure==1)bad.getCompound("ScreenChipReader").getCompound("InsertedToken").putString("id","future:unresolved_chip");else bad.putInt("PrimarySurfaceTexture",-3);var before=bad.copy();copy.loadWithComponents(bad,level.registryAccess());need(copy.inert()&&copy.saveWithoutMetadata(level.registryAccess()).equals(before),"unsupported reader/surface remains one original recovery owner");need(NativeScreenItemSettings.choices(copy,level.registryAccess()).contains(NativeScreenItemSettings.RECOVERY),"unsupported owner uses recovery item envelope");}
            }else verify((NativeScreenEntity)level.getBlockEntity(pos),level);
        }
        System.out.println("[vandorlabs][screen-stored-chip] "+(read?"read":"write")+" PASS checks="+checks+" seven roles/one physical item/private projection/prepared item/recovery");
    }
}
