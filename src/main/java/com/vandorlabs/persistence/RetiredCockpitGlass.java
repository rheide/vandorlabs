package com.vandorlabs.persistence;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.nbt.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.datafix.*;
import net.minecraftforge.common.util.ModFixs;
import net.minecraftforge.fml.common.FMLCommonHandler;

/** One-time native chunk/item migration, plus lazy readers for portable saved designs. */
public final class RetiredCockpitGlass {
    public static final String[] IDS={"clear_cockpit_glass","pale_cyan_cockpit_glass","smoked_cockpit_glass"};
    private static final String[] NAMES={"vandorlabs:clear_cockpit_glass","vandorlabs:pale_cyan_cockpit_glass","vandorlabs:smoked_cockpit_glass"};
    private static volatile Map<Integer,Integer> oldIds=Collections.emptyMap();
    private static Path journal;
    private RetiredCockpitGlass(){}
    public static int variant(String name){for(int i=0;i<IDS.length;i++)if(NAMES[i].equals(name))return i;return -1;}
    public static IBlockState state(int variant){if(variant<0||variant>2)throw new IllegalArgumentException("Invalid retired glass variant");return variant==0?Blocks.GLASS.getDefaultState():Blocks.STAINED_GLASS.getStateFromMeta(variant==1?9:7);}
    public static void install(){
        ModFixs fixes=FMLCommonHandler.instance().getDataFixer().init("vandorlabs",2);
        for(FixTypes type:new FixTypes[]{FixTypes.CHUNK,FixTypes.PLAYER,FixTypes.ENTITY,FixTypes.BLOCK_ENTITY,FixTypes.ITEM_INSTANCE})fixes.registerFix(type,new IFixableData(){
            public int getFixVersion(){return 1;}
            public NBTTagCompound fixTagCompound(NBTTagCompound tag){if(type==FixTypes.CHUNK)migrateChunk(tag,oldIds);migrateItems(tag);return tag;}
        });
        for(FixTypes type:new FixTypes[]{FixTypes.CHUNK,FixTypes.PLAYER,FixTypes.ENTITY,FixTypes.BLOCK_ENTITY,FixTypes.ITEM_INSTANCE})fixes.registerFix(type,new IFixableData(){
            public int getFixVersion(){return 2;}
            public NBTTagCompound fixTagCompound(NBTTagCompound tag){RenamedBlocks.migrateItems(tag);return tag;}
        });
    }
    /** Runs before world registry injection. Keep the original IDs even after level.dat drops retired names. */
    public static synchronized void begin(MinecraftServer server){
        journal=server.getActiveAnvilConverter().getFile(server.getFolderName(),"data/vandorlabs-retired-glass.dat").toPath();
        Map<Integer,Integer> loaded=new HashMap<>();
        if(Files.exists(journal))try{
            if(Files.size(journal)>65536)throw new IOException("Retired glass migration table is too large");
            NBTTagCompound data;try(DataInputStream input=new DataInputStream(new java.util.zip.GZIPInputStream(Files.newInputStream(journal)))){data=CompressedStreamTools.read(input,new NBTSizeTracker(65536));}
            NBTTagList entries=data.getTagList("Mappings",10);if(entries.tagCount()>64)throw new IOException("Too many retired glass mappings");
            for(int i=0;i<entries.tagCount();i++){NBTTagCompound entry=entries.getCompoundTagAt(i);int id=entry.getInteger("Id"),variant=entry.getInteger("Variant");if(id<0||id>65535||variant<0||variant>2||loaded.put(id,variant)!=null)throw new IOException("Invalid retired glass migration mapping");}
        }catch(IOException error){throw new IllegalStateException("Cannot read retired glass migration table",error);}
        oldIds=Collections.unmodifiableMap(loaded);
    }
    public static synchronized void remember(int id,int variant){
        if(!FMLCommonHandler.instance().getEffectiveSide().isServer())return;
        if(journal==null)throw new IllegalStateException("Retired glass migration context is unavailable");
        Integer previous=oldIds.get(id);if(previous!=null){if(previous!=variant)throw new IllegalStateException("Conflicting retired glass ID");return;}
        Map<Integer,Integer> next=new TreeMap<>(oldIds);next.put(id,variant);NBTTagList entries=new NBTTagList();
        for(Map.Entry<Integer,Integer> entry:next.entrySet()){NBTTagCompound value=new NBTTagCompound();value.setInteger("Id",entry.getKey());value.setInteger("Variant",entry.getValue());entries.appendTag(value);}NBTTagCompound data=new NBTTagCompound();data.setTag("Mappings",entries);
        try{Files.createDirectories(journal.getParent());JournalFiles.syncDirectory(journal.getParent().getParent());Path temporary=Files.createTempFile(journal.getParent(),".retired-glass-",".tmp");try{
            ByteArrayOutputStream bytes=new ByteArrayOutputStream();CompressedStreamTools.writeCompressed(data,bytes);try(FileOutputStream output=new FileOutputStream(temporary.toFile())){output.write(bytes.toByteArray());output.getFD().sync();}
            Files.move(temporary,journal,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);JournalFiles.syncDirectory(journal.getParent());
        }finally{Files.deleteIfExists(temporary);}}catch(IOException error){throw new IllegalStateException("Cannot durably retain retired glass IDs",error);}
        oldIds=Collections.unmodifiableMap(next);
    }
    /** Before Anvil/NEID resolves numerical block IDs. No world or chunk access from the I/O thread. */
    public static void migrateChunk(NBTTagCompound root,Map<Integer,Integer> ids){
        if(ids.isEmpty())return;NBTTagList sections=root.getCompoundTag("Level").getTagList("Sections",10);
        for(int section=0;section<sections.tagCount();section++){
            NBTTagCompound data=sections.getCompoundTagAt(section);byte[] blocks=data.getByteArray("Blocks"),meta=data.getByteArray("Data"),add=data.getByteArray("Add"),add2=data.getByteArray("Add2");
            if(blocks.length!=4096||meta.length!=2048||add.length!=0&&add.length!=2048||add2.length!=0&&add2.length!=2048)throw new IllegalArgumentException("Invalid legacy glass chunk section");
            boolean changed=false;
            for(int i=0;i<4096;i++){
                int id=(blocks[i]&255)|(nibble(add,i)<<8)|(nibble(add2,i)<<12);Integer variant=ids.get(id);if(variant==null)continue;
                IBlockState replacement=state(variant);int target=Block.getIdFromBlock(replacement.getBlock());if(target<0||target>65535)throw new IllegalStateException("Unsupported vanilla glass block ID");
                blocks[i]=(byte)target;if(add.length==0&&(target&0xf00)!=0)add=new byte[2048];if(add2.length==0&&(target&0xf000)!=0)add2=new byte[2048];
                putNibble(add,i,target>>8&15);putNibble(add2,i,target>>12&15);putNibble(meta,i,replacement.getBlock().getMetaFromState(replacement));changed=true;
            }
            if(changed){data.setByteArray("Blocks",blocks);data.setByteArray("Data",meta);if(add.length!=0)data.setByteArray("Add",add);if(add2.length!=0)data.setByteArray("Add2",add2);}
        }
    }
    private static int nibble(byte[] values,int at){return values.length==0?0:(values[at>>1]>>((at&1)*4))&15;}
    private static void putNibble(byte[] values,int at,int value){if(values.length==0)return;int shift=(at&1)*4;values[at>>1]=(byte)((values[at>>1]&~(15<<shift))|value<<shift);}
    /** Narrow item-stack recognition preserves custom names, counts, tags and opaque capabilities. */
    public static void migrateItems(NBTTagCompound root){walkItems(root,true);}
    private static boolean walkItems(NBTTagCompound root,boolean change){
        ArrayDeque<NBTBase> pending=new ArrayDeque<>();pending.add(root);
        while(!pending.isEmpty()){
            NBTBase value=pending.removeLast();if(value instanceof NBTTagCompound){NBTTagCompound tag=(NBTTagCompound)value;
                int variant=tag.hasKey("id",8)&&tag.hasKey("Count",99)?variant(tag.getString("id")):-1;
                if(variant>=0){if(!change)return true;IBlockState state=state(variant);tag.setString("id",state.getBlock().getRegistryName().toString());tag.setShort("Damage",(short)state.getBlock().getMetaFromState(state));}
                for(String key:tag.getKeySet()){NBTBase child=tag.getTag(key);if(child instanceof NBTTagCompound||child instanceof NBTTagList)pending.add(child);}
            }else if(value instanceof NBTTagList){NBTTagList list=(NBTTagList)value;for(int i=0;i<list.tagCount();i++){NBTBase child=list.get(i);if(child instanceof NBTTagCompound||child instanceof NBTTagList)pending.add(child);}}
        }
        return false;
    }
    public static NBTTagCompound stateTag(NBTTagCompound saved){
        int variant=variant(saved.getString("Name"));if(variant<0)return RenamedBlocks.stateTag(saved);
        if(saved.hasKey("Properties")&&!saved.hasKey("Properties",10)||!saved.getCompoundTag("Properties").hasNoTags()||saved.getKeySet().stream().anyMatch(key->!key.equals("Name")&&!key.equals("Properties")))throw new IllegalArgumentException("Unknown retired glass properties");
        return NBTUtil.writeBlockState(new NBTTagCompound(),state(variant));
    }
    public static IBlockState readState(NBTTagCompound saved){return NBTUtil.readBlockState(stateTag(saved));}
    /** Normalize owned ramp state fields and nested arm snapshots before lossless tile round trips. */
    public static NBTTagCompound tileData(NBTTagCompound saved){
        NBTTagCompound result=RenamedBlocks.tileData(itemData(saved));if(result==null||!result.getString("id").startsWith("vandorlabs:")||!(result.hasKey("ArmPayload",10)||result.hasKey("Original",10)||result.hasKey("Source",10)||result.hasKey("OriginalState",8)||result.hasKey("SourceState",8)))return result;
        if(!walkStates(result,false))return result;if(result==saved)result=saved.copy();walkStates(result,true);return result;
    }
    private static boolean walkStates(NBTTagCompound root,boolean change){
        ArrayDeque<NBTBase> pending=new ArrayDeque<>();pending.add(root);while(!pending.isEmpty()){
            NBTBase value=pending.removeLast();if(value instanceof NBTTagCompound){NBTTagCompound tag=(NBTTagCompound)value;
                if(tag.hasKey("Palette",9)&&tag.hasKey("Blocks",9)&&tag.hasKey("Schema",3)&&tag.hasKey("DisplayOnly",1)){NBTTagList palette=tag.getTagList("Palette",10);for(int i=0;i<palette.tagCount();i++)if(variant(palette.getCompoundTagAt(i).getString("Name"))>=0){if(!change)return true;palette.set(i,stateTag(palette.getCompoundTagAt(i)));}}
                if(tag.getString("id").equals("vandorlabs:programmable_ramp")||tag.getString("id").equals("vandorlabs:controlled_ramp")){
                    for(String key:new String[]{"Original","Source"})if(tag.hasKey(key,10)&&variant(tag.getCompoundTag(key).getString("Name"))>=0){if(!change)return true;tag.setTag(key,stateTag(tag.getCompoundTag(key)));}
                    for(String key:new String[]{"OriginalState","SourceState"})if(tag.hasKey(key,8)){String encoded=tag.getString(key);int old=-1;for(int i=0;i<NAMES.length;i++)if(encoded.equals(NAMES[i])||encoded.startsWith(NAMES[i])&&encoded.length()>NAMES[i].length()&&encoded.charAt(NAMES[i].length())=='['){old=i;break;}if(old>=0){BlockStateReference reference=BlockStateReference.parse(encoded);if(!reference.properties.isEmpty())throw new IllegalArgumentException("Unknown retired ramp glass properties");if(!change)return true;tag.setString(key,LegacyBlockStates.encode(state(old)));}}
                }
                // Capability state belongs to its provider, not our saved-state schema.
                for(String key:tag.getKeySet()){if(key.equals("ForgeCaps"))continue;NBTBase child=tag.getTag(key);if(child instanceof NBTTagCompound||child instanceof NBTTagList)pending.add(child);}
            }else if(value instanceof NBTTagList){NBTTagList list=(NBTTagList)value;for(int i=0;i<list.tagCount();i++){NBTBase child=list.get(i);if(child instanceof NBTTagCompound||child instanceof NBTTagList)pending.add(child);}}
        }return false;
    }
    public static NBTTagCompound itemData(NBTTagCompound saved){saved=RenamedBlocks.itemData(saved);if(saved==null||!walkItems(saved,false))return saved;NBTTagCompound copy=saved.copy();migrateItems(copy);return copy;}
}
