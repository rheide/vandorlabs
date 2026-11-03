package com.vandorlabs.persistence;

import java.util.ArrayDeque;
import net.minecraft.nbt.*;

/** Name-only migration: metadata, block properties, inventory contents and tile IDs stay intact. */
public final class RenamedBlocks {
    public static final String OLD="vandorlabs:programmable_diagonal_half_console",NEW="vandorlabs:programmable_diagonal_half_screen";
    private RenamedBlocks(){}
    public static String canonical(String id){return OLD.equals(id)?NEW:id;}
    public static NBTTagCompound stateTag(NBTTagCompound saved){if(!OLD.equals(saved.getString("Name")))return saved;NBTTagCompound copy=saved.copy();copy.setString("Name",NEW);return copy;}
    public static void migrateItems(NBTTagCompound saved){walk(saved,true,false);}
    public static NBTTagCompound itemData(NBTTagCompound saved){if(saved==null||!walk(saved,false,false))return saved;NBTTagCompound copy=saved.copy();migrateItems(copy);return copy;}
    public static NBTTagCompound tileData(NBTTagCompound saved){if(saved==null||!walk(saved,false,true))return saved;NBTTagCompound copy=saved.copy();walk(copy,true,true);return copy;}
    private static boolean walk(NBTTagCompound root,boolean change,boolean states){
        boolean found=false;ArrayDeque<NBTBase> pending=new ArrayDeque<>();pending.add(root);
        while(!pending.isEmpty()){
            NBTBase value=pending.removeLast();
            if(value instanceof NBTTagCompound){NBTTagCompound tag=(NBTTagCompound)value;
                if(!states&&tag.hasKey("Count",99)&&OLD.equals(tag.getString("id"))){if(!change)return true;tag.setString("id",NEW);found=true;}
                if(!states&&tag.hasKey("recipeBook",10)){NBTTagCompound book=tag.getCompoundTag("recipeBook");for(String key:new String[]{"recipes","toBeDisplayed"}){NBTTagList list=book.getTagList(key,8);for(int i=0;i<list.tagCount();i++)if(OLD.equals(list.getStringTagAt(i))){if(!change)return true;list.set(i,new NBTTagString(NEW));found=true;}}}
                if(states&&tag.hasKey("Schema",3)&&tag.hasKey("Palette",9)&&tag.hasKey("Blocks",9)){NBTTagList palette=tag.getTagList("Palette",10);for(int i=0;i<palette.tagCount();i++)if(OLD.equals(palette.getCompoundTagAt(i).getString("Name"))){if(!change)return true;palette.set(i,stateTag(palette.getCompoundTagAt(i)));found=true;}}
                if(states&&(tag.getString("id").equals("vandorlabs:programmable_ramp")||tag.getString("id").equals("vandorlabs:programmable_ramp_slab")||tag.getString("id").equals("vandorlabs:controlled_ramp"))){
                    for(String key:new String[]{"Original","Source"})if(tag.hasKey(key,10)&&OLD.equals(tag.getCompoundTag(key).getString("Name"))){if(!change)return true;tag.setTag(key,stateTag(tag.getCompoundTag(key)));found=true;}
                    for(String key:new String[]{"OriginalState","SourceState"})if(tag.hasKey(key,8)){String encoded=tag.getString(key);if(encoded.equals(OLD)||encoded.startsWith(OLD+"[")){if(!change)return true;net.minecraft.block.state.IBlockState state=LegacyBlockStates.decode(encoded);if(state==null)throw new IllegalArgumentException("Invalid renamed block state");tag.setString(key,LegacyBlockStates.encode(state));found=true;}}
                }
                for(String key:tag.getKeySet()){if(states&&key.equals("ForgeCaps"))continue;NBTBase child=tag.getTag(key);if(child instanceof NBTTagCompound||child instanceof NBTTagList)pending.add(child);}
            }else if(value instanceof NBTTagList){NBTTagList list=(NBTTagList)value;for(int i=0;i<list.tagCount();i++){NBTBase child=list.get(i);if(child instanceof NBTTagCompound||child instanceof NBTTagList)pending.add(child);}}
        }
        return found;
    }
}
