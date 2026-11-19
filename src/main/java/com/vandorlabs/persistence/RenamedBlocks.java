package com.vandorlabs.persistence;

import java.util.ArrayDeque;
import net.minecraft.nbt.*;

/** Name-only migration: metadata, block properties, inventory contents and tile IDs stay intact. */
public final class RenamedBlocks {
    public static final String OLD="vandorlabs:programmable_diagonal_half_console",NEW="vandorlabs:programmable_diagonal_half_screen";
    public static final String OLD_MATTER="vandorlabs:programmable_matter_block", PROGRAMMABLE_BLOCK="vandorlabs:programmable_block";
    private RenamedBlocks(){}
    public static net.minecraft.block.state.IBlockState matterState(){
        // The retired block's sole metadata value was zero, which the Forge chunk palette remaps to DOWN.
        return com.vandorlabs.blocks.ModBlocks.PROGRAMMABLE_BLOCK.getStateFromMeta(0);
    }
    public static NBTTagCompound matterTile(net.minecraft.util.math.BlockPos pos){
        net.minecraft.block.state.IBlockState state=matterState();
        net.minecraft.tileentity.TileEntity tile=state.getBlock().createTileEntity(null,state);
        tile.setPos(pos);
        return tile.writeToNBT(new NBTTagCompound());
    }
    public static String canonical(String id){return OLD_MATTER.equals(id)?PROGRAMMABLE_BLOCK:OLD.equals(id)?NEW:"vandorlabs:survey_dish_core".equals(id)?"vandorlabs:standalone_dish_core":"vandorlabs:sentinel_array_core".equals(id)?"vandorlabs:standalone_antenna_core":id;}
    public static NBTTagCompound stateTag(NBTTagCompound saved){
        if(OLD_MATTER.equals(saved.getString("Name"))){
            if(saved.hasKey("Properties")&&(!saved.hasKey("Properties",10)||!saved.getCompoundTag("Properties").hasNoTags()))
                throw new IllegalArgumentException("Unknown retired Matter Block properties");
            return NBTUtil.writeBlockState(new NBTTagCompound(),matterState());
        }
        if(canonical(saved.getString("Name")).equals(saved.getString("Name")))return saved;NBTTagCompound copy=saved.copy();copy.setString("Name",canonical(saved.getString("Name")));return copy;}
    public static void migrateItems(NBTTagCompound saved){walk(saved,true,false);}
    public static NBTTagCompound itemData(NBTTagCompound saved){if(saved==null||!walk(saved,false,false))return saved;NBTTagCompound copy=saved.copy();migrateItems(copy);return copy;}
    public static NBTTagCompound tileData(NBTTagCompound saved){if(saved==null||!walk(saved,false,true))return saved;NBTTagCompound copy=saved.copy();walk(copy,true,true);return copy;}
    private static boolean walk(NBTTagCompound root,boolean change,boolean states){
        boolean found=false;ArrayDeque<NBTBase> pending=new ArrayDeque<>();pending.add(root);
        while(!pending.isEmpty()){
            NBTBase value=pending.removeLast();
            if(value instanceof NBTTagCompound){NBTTagCompound tag=(NBTTagCompound)value;
                if(!states&&tag.hasKey("Count",99)&&!canonical(tag.getString("id")).equals(tag.getString("id"))){if(!change)return true;tag.setString("id",canonical(tag.getString("id")));found=true;}
                if(!states&&tag.hasKey("recipeBook",10)){NBTTagCompound book=tag.getCompoundTag("recipeBook");for(String key:new String[]{"recipes","toBeDisplayed"}){NBTTagList list=book.getTagList(key,8);for(int i=0;i<list.tagCount();i++)if(!canonical(list.getStringTagAt(i)).equals(list.getStringTagAt(i))){if(!change)return true;list.set(i,new NBTTagString(canonical(list.getStringTagAt(i))));found=true;}}}
                if(states&&tag.hasKey("Schema",3)&&tag.hasKey("Palette",9)&&tag.hasKey("Blocks",9)){
                    NBTTagList palette=tag.getTagList("Palette",10);
                    java.util.BitSet legacyFrames=new java.util.BitSet();
                    for(int i=0;i<palette.tagCount();i++){
                        NBTTagCompound state=palette.getCompoundTagAt(i);
                        String name=state.getString("Name");
                        if(!canonical(name).equals(name)){
                            if(!change)return true;
                            if(OLD_MATTER.equals(name))legacyFrames.set(i);
                            palette.set(i,stateTag(state));found=true;
                        }
                    }
                    if(!legacyFrames.isEmpty()){
                        NBTTagList cells=tag.getTagList("Blocks",10);
                        for(int i=0;i<cells.tagCount();i++){
                            NBTTagCompound cell=cells.getCompoundTagAt(i);int index=cell.getInteger("State");
                            if(index>=0&&legacyFrames.get(index)){
                                if(cell.hasKey("Tile"))throw new IllegalArgumentException("Unexpected retired Matter Block tile");
                                cell.setTag("Tile",matterTile(net.minecraft.util.math.BlockPos.fromLong(cell.getLong("Pos"))));
                            }
                        }
                    }
                }
                if(states&&(tag.getString("id").equals("vandorlabs:programmable_ramp")||tag.getString("id").equals("vandorlabs:programmable_ramp_slab")||tag.getString("id").equals("vandorlabs:controlled_ramp"))){
                    for(String key:new String[]{"Original","Source"})if(tag.hasKey(key,10)&&!canonical(tag.getCompoundTag(key).getString("Name")).equals(tag.getCompoundTag(key).getString("Name"))){if(!change)return true;tag.setTag(key,stateTag(tag.getCompoundTag(key)));found=true;}
                    for(String key:new String[]{"OriginalState","SourceState"})if(tag.hasKey(key,8)){String encoded=tag.getString(key);if(!canonical(encoded.split("\\[",2)[0]).equals(encoded.split("\\[",2)[0])){if(!change)return true;net.minecraft.block.state.IBlockState state=LegacyBlockStates.decode(encoded);if(state==null)throw new IllegalArgumentException("Invalid renamed block state");tag.setString(key,LegacyBlockStates.encode(state));found=true;}}
                }
                for(String key:tag.getKeySet()){if(key.equals("ForgeCaps"))continue;NBTBase child=tag.getTag(key);if(child instanceof NBTTagCompound||child instanceof NBTTagList)pending.add(child);}
            }else if(value instanceof NBTTagList){NBTTagList list=(NBTTagList)value;for(int i=0;i<list.tagCount();i++){NBTBase child=list.get(i);if(child instanceof NBTTagCompound||child instanceof NBTTagList)pending.add(child);}}
        }
        return found;
    }
}
