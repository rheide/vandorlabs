package com.vandorlabs.items;

import java.util.*;
import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/** One bounded settings-only operation per player; inventories and ownership are never restored. */
@net.minecraftforge.fml.common.Mod.EventBusSubscriber(modid=com.vandorlabs.VandorLabs.MODID)
public final class DuplifierUndo {
    private static final class Entry {
        final Block block;final NBTTagCompound before;NBTTagCompound after;
        Entry(Block block,NBTTagCompound before){this.block=block;this.before=before.copy();}
    }
    private static final class Operation {
        final World world;final Map<BlockPos,Entry> entries=new LinkedHashMap<>();
        final long order=nextOrder++;long bytes;boolean complete=true;
        Operation(World world){this.world=world;}
    }
    private static final Map<EntityPlayer,Operation> history=new WeakHashMap<>(),pending=new WeakHashMap<>();
    private static final long PLAYER_BYTES=16L*1024*1024,TOTAL_BYTES=64L*1024*1024;
    private static long nextOrder;
    private DuplifierUndo(){}
    /** Conservative retained-tree estimate, including map entries, strings and array storage.
     * Keys may be shared by NBT copies; charging them again keeps this an upper estimate. */
    private static long bytes(net.minecraft.nbt.NBTBase tag){
        if(tag instanceof NBTTagCompound){NBTTagCompound compound=(NBTTagCompound)tag;long size=128;
            for(String key:compound.getKeySet())size+=112L+2L*key.length()+bytes(compound.getTag(key));return size;}
        if(tag instanceof net.minecraft.nbt.NBTTagList){net.minecraft.nbt.NBTTagList list=(net.minecraft.nbt.NBTTagList)tag;long size=80L+8L*list.tagCount();for(int i=0;i<list.tagCount();i++)size+=bytes(list.get(i));return size;}
        if(tag instanceof net.minecraft.nbt.NBTTagString)return 64L+2L*((net.minecraft.nbt.NBTTagString)tag).getString().length();
        if(tag instanceof net.minecraft.nbt.NBTTagIntArray)return 40L+4L*((net.minecraft.nbt.NBTTagIntArray)tag).getIntArray().length;
        if(tag instanceof net.minecraft.nbt.NBTTagByteArray)return 40L+((net.minecraft.nbt.NBTTagByteArray)tag).getByteArray().length;
        // Captured settings contain no long arrays; reject unsupported future types
        // by exhausting the budget rather than retaining an unmeasured payload.
        if(tag.getId()==12)return PLAYER_BYTES+1;
        return 32;
    }
    private static long totalBytes(){long total=0;for(Operation op:history.values())total+=op.bytes;for(Operation op:pending.values())total+=op.bytes;return total;}
    private static void discard(EntityPlayer player,Operation operation){operation.entries.clear();operation.bytes=0;operation.complete=false;history.remove(player);}
    private static boolean reserve(EntityPlayer player,Operation operation,long amount,long playerLimit,long totalLimit){
        if(!operation.complete)return false;
        Operation previous=history.get(player);
        if(previous!=null&&previous.bytes+operation.bytes+amount>playerLimit)history.remove(player);
        if(amount>playerLimit-operation.bytes){discard(player,operation);return false;}
        while(totalBytes()+amount>totalLimit){
            EntityPlayer oldest=null;long order=Long.MAX_VALUE;
            for(Map.Entry<EntityPlayer,Operation> entry:history.entrySet())if(entry.getValue().order<order){oldest=entry.getKey();order=entry.getValue().order;}
            if(oldest==null){discard(player,operation);return false;}history.remove(oldest);
        }
        operation.bytes+=amount;return true;
    }
    /** Focused checks use small budgets and real NBT without creating thousands of world tiles. */
    public static void runBudgetChecks(EntityPlayer player,World world){
        Operation oldHistory=history.remove(player),oldPending=pending.remove(player);
        try{
            Operation prior=new Operation(world);prior.bytes=512;history.put(player,prior);
            Operation draft=new Operation(world);pending.put(player,draft);
            if(!reserve(player,draft,256,4096,512)||history.containsKey(player)||draft.bytes!=256)throw new IllegalStateException("Undo total budget did not evict completed history");
            NBTTagCompound large=new NBTTagCompound();large.setIntArray("channels",new int[1024]);
            draft.entries.put(BlockPos.ORIGIN,new Entry(net.minecraft.init.Blocks.STONE,large));history.put(player,prior);
            if(reserve(player,draft,bytes(large),512,8192)||draft.complete||!draft.entries.isEmpty()||history.containsKey(player))throw new IllegalStateException("Undo budget retained an incomplete operation");
            if(reserve(player,draft,1,512,8192))throw new IllegalStateException("Undo budget resumed an incomplete draft");
            finish(player);if(history.containsKey(player)||pending.containsKey(player))throw new IllegalStateException("Undo budget published partial history");
            System.out.println("[vandorlabs][reprolab] duplifier-undo-budget PASS whole-history-eviction incomplete-draft-discard no-partial-undo player-MiB=16 total-MiB=64");
        }finally{history.remove(player);pending.remove(player);if(oldHistory!=null)history.put(player,oldHistory);if(oldPending!=null)pending.put(player,oldPending);}
    }
    // Values retain worlds, whose player lists can retain the weak keys. Explicit
    // lifecycle cleanup breaks that cycle and also releases abandoned drafts.
    @net.minecraftforge.fml.common.eventhandler.SubscribeEvent
    public static void worldUnloaded(net.minecraftforge.event.world.WorldEvent.Unload event){
        if(event.getWorld().isRemote)return;
        history.values().removeIf(operation->operation.world==event.getWorld());
        pending.values().removeIf(operation->operation.world==event.getWorld());
    }
    @net.minecraftforge.fml.common.eventhandler.SubscribeEvent
    public static void loggedOut(net.minecraftforge.fml.common.gameevent.PlayerEvent.PlayerLoggedOutEvent event){
        if(event.player.world.isRemote)return;
        history.remove(event.player);pending.remove(event.player);
    }
    private static NBTTagCompound capture(World world,BlockPos pos){
        NBTTagCompound settings=ProgrammableSettings.capture(world,pos);net.minecraft.tileentity.TileEntity tile=world.getTileEntity(pos);
        if(settings!=null&&settings.hasKey(ProgrammableSettings.FACE_TEXTURES,10)&&tile instanceof com.vandorlabs.tiles.TileEntityAnimatedScreenSelector)
            settings.getCompoundTag(ProgrammableSettings.FACE_TEXTURES).setIntArray("choices",((com.vandorlabs.tiles.TileEntityAnimatedScreenSelector)tile).getFaceTextures().choices());
        return settings;
    }
    public static void begin(EntityPlayer player,World world){if(player!=null&&!world.isRemote)pending.put(player,new Operation(world));}
    public static void before(EntityPlayer player,World world,BlockPos pos){
        Operation operation=pending.get(player);if(operation==null||!operation.complete||operation.world!=world||operation.entries.containsKey(pos)||operation.entries.size()>=DuplifierConnectedApply.MAX_CELLS)return;
        NBTTagCompound settings=capture(world,pos);
        if(settings!=null&&reserve(player,operation,160+bytes(settings),PLAYER_BYTES,TOTAL_BYTES))operation.entries.put(pos.toImmutable(),new Entry(world.getBlockState(pos).getBlock(),settings));
    }
    public static void finish(EntityPlayer player){
        Operation operation=pending.get(player);if(operation==null)return;
        if(!operation.complete){pending.remove(player);player.sendStatusMessage(new net.minecraft.util.text.TextComponentString("Duplifier applied settings; undo unavailable because the memory limit was exceeded"),true);return;}
        Iterator<Map.Entry<BlockPos,Entry>> iterator=operation.entries.entrySet().iterator();
        while(iterator.hasNext()){
            Map.Entry<BlockPos,Entry> value=iterator.next();value.getValue().after=capture(operation.world,value.getKey());
            if(value.getValue().after==null||value.getValue().before.equals(value.getValue().after)){operation.bytes-=160+bytes(value.getValue().before);iterator.remove();}
            else if(!reserve(player,operation,bytes(value.getValue().after),PLAYER_BYTES,TOTAL_BYTES)){finish(player);return;}
        }
        pending.remove(player);if(!operation.entries.isEmpty())history.put(player,operation);
    }
    public static boolean undo(EntityPlayer player){
        Operation operation=history.get(player);if(operation==null||operation.world!=player.world||player.isSpectator())return false;
        // Preflight every target before changing any settings; never load chunks or overwrite later edits.
        for(Map.Entry<BlockPos,Entry> value:operation.entries.entrySet()){
            BlockPos pos=value.getKey();Entry entry=value.getValue();
            if(!operation.world.isBlockLoaded(pos)||!operation.world.isBlockModifiable(player,pos)
                    ||!player.canPlayerEdit(pos,EnumFacing.UP,player.getHeldItemMainhand())
                    ||operation.world.getBlockState(pos).getBlock()!=entry.block
                    ||!entry.after.equals(capture(operation.world,pos)))return false;
        }
        Iterator<Map.Entry<BlockPos,Entry>> iterator=operation.entries.entrySet().iterator();
        while(iterator.hasNext()){
            Map.Entry<BlockPos,Entry> value=iterator.next();
            if(!ProgrammableSettings.apply(operation.world,value.getKey(),value.getValue().before,player))return false;
            iterator.remove();
        }
        history.remove(player);return true;
    }
}
