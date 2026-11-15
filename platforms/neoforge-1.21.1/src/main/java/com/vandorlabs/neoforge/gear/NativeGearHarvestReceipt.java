package com.vandorlabs.neoforge.gear;

import java.util.*;
import net.minecraft.core.*;
import net.minecraft.nbt.*;
import net.minecraft.server.level.*;
import net.minecraft.world.Container;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.Vec3;

/** Immutable, bounded loot custody for one captured cell. Never accepts a client-provided loot list. */
final class NativeGearHarvestReceipt {
    private static final int MAX_DROPS=256,MAX_BYTES=256*1024;
    record Drop(UUID identity,ItemStack stack,Vec3 point) {
        Drop {Objects.requireNonNull(identity);stack=stack.copy();Objects.requireNonNull(point);if(stack.isEmpty()||stack.getCount()>stack.getMaxStackSize())throw new IllegalArgumentException("Invalid arm harvest stack");}
        @Override public ItemStack stack(){return stack.copy();}
    }
    final UUID operation,actor,payload;
    private final byte[] cellDigest;
    final int cell;
    final boolean creative;
    private final ItemStack tool;
    private final List<Drop> drops;private final Map<UUID,Drop> dropsById;
    private NativeGearHarvestReceipt(UUID operation,UUID actor,UUID payload,byte[] cellDigest,int cell,boolean creative,ItemStack tool,List<Drop> drops){
        this.operation=Objects.requireNonNull(operation);this.actor=Objects.requireNonNull(actor);this.payload=Objects.requireNonNull(payload);if(cellDigest==null||cellDigest.length!=32)throw new IllegalArgumentException("Arm harvest cell fingerprint");this.cellDigest=cellDigest.clone();if(cell<0||cell>=8192||drops.size()>MAX_DROPS)throw new IllegalArgumentException("Arm harvest receipt bound");this.cell=cell;this.creative=creative;this.tool=tool.copy();this.drops=List.copyOf(drops);var byId=new HashMap<UUID,Drop>();for(var drop:drops)if(byId.put(drop.identity,drop)!=null)throw new IllegalArgumentException("Duplicate arm harvest drop");dropsById=Map.copyOf(byId);
    }
    static NativeGearHarvestReceipt prepare(NativeGearEntity owner,ServerPlayer actor,int cell){
        if(!(owner.getLevel() instanceof ServerLevel level)||!level.getServer().isSameThread()||actor.level()!=level||owner.payload()==null||owner.payload().materialized()||cell<0||cell>=owner.payload().size())throw new IllegalArgumentException("Invalid captured block harvest");
        var selected=owner.payload().cells().get(cell);var payloadIdentity=owner.payload().id();var fingerprint=digest(selected);var staged=NativeGearSnapshot.stage(level,selected,false);var tool=actor.getMainHandItem().copy();var stacks=new ArrayList<ItemStack>();
        if(!actor.isCreative()&&actor.hasCorrectToolForDrops(selected.state)&&level.getGameRules().getBoolean(net.minecraft.world.level.GameRules.RULE_DOBLOCKDROPS))stacks.addAll(Block.getDrops(selected.state,level,selected.pos,staged,actor,tool));
        // Ordinary block loot omits container contents; native Storage drops appearance only as well.
        if(staged instanceof com.vandorlabs.neoforge.rack.NativeRackEntity rack)stacks.addAll(rack.ownedModuleDrops());
        if(staged instanceof Container container)for(int slot=0;slot<container.getContainerSize();slot++)if(!container.getItem(slot).isEmpty())stacks.add(container.getItem(slot).copy());
        var offset=selected.pos.subtract(owner.getBlockPos());var transform=owner.payload().transform(owner,owner.progress());var point=transform.point(offset.getX()+.5,offset.getY()+.5,offset.getZ()+.5).add(Vec3.atLowerCornerOf(owner.getBlockPos()));position(owner.getBlockPos(),point);
        var drops=new ArrayList<Drop>();for(var stack:stacks){if(stack.isEmpty())continue;int remaining=stack.getCount();while(remaining>0){if(drops.size()>=MAX_DROPS)throw new IllegalArgumentException("Too many arm harvest drops");int count=Math.min(remaining,stack.getMaxStackSize());if(count<=0)throw new IllegalArgumentException("Invalid arm harvest stack limit");var split=stack.copyWithCount(count);drops.add(new Drop(UUID.randomUUID(),split,point));remaining-=count;}}
        var receipt=new NativeGearHarvestReceipt(UUID.randomUUID(),actor.getUUID(),payloadIdentity,fingerprint,cell,actor.isCreative(),tool,drops);receipt.write(level.registryAccess());return receipt;
    }
    private static void position(BlockPos root,Vec3 point){for(double value:new double[]{point.x-root.getX(),point.y-root.getY(),point.z-root.getZ()})if(!Double.isFinite(value)||Math.abs(value)>128)throw new IllegalArgumentException("Arm harvest drop position bound");}
    boolean sourceMatches(NativeGearPayload source){return source!=null&&!source.materialized()&&payload.equals(source.id())&&cell<source.size()&&java.security.MessageDigest.isEqual(cellDigest,digest(source.cells().get(cell)));}
    private static byte[] digest(NativeGearSnapshot.Cell cell){try{var digest=java.security.MessageDigest.getInstance("SHA-256");try(var stream=new java.io.DataOutputStream(new java.security.DigestOutputStream(java.io.OutputStream.nullOutputStream(),digest))){NbtIo.write((CompoundTag)canonical(cell.write()),stream);}return digest.digest();}catch(java.io.IOException|java.security.NoSuchAlgorithmException impossible){throw new IllegalStateException("Arm harvest fingerprint",impossible);}}
    private static Tag canonical(Tag tag){if(tag instanceof CompoundTag compound){var sorted=new java.util.TreeMap<String,Tag>();for(String key:compound.getAllKeys())sorted.put(key,canonical(compound.get(key)));return new CompoundTag(sorted){};}if(tag instanceof ListTag list){var result=new ListTag();for(var entry:list)result.add(canonical(entry));return result;}return tag.copy();}
    boolean actorMatches(ServerPlayer player){return player!=null&&player.isAlive()&&player.getUUID().equals(actor)&&player.isCreative()==creative&&ItemStack.matches(player.getMainHandItem(),tool);}
    void finishTool(ServerPlayer player,net.minecraft.world.level.block.state.BlockState state,BlockPos source){if(!creative&&actorMatches(player)){player.causeFoodExhaustion(.005F);player.getMainHandItem().mineBlock(player.serverLevel(),state,source,player);}}
    List<Drop> drops(){return drops;}
    List<net.minecraft.world.level.ChunkPos> chunks(BlockPos root){var chunks=new LinkedHashSet<net.minecraft.world.level.ChunkPos>();chunks.add(new net.minecraft.world.level.ChunkPos(root));for(var drop:drops)chunks.add(new net.minecraft.world.level.ChunkPos(BlockPos.containing(drop.point)));return List.copyOf(chunks);}
    ItemEntity spawn(ServerLevel level,BlockPos root,Drop drop){var item=new ItemEntity(level,drop.point.x,drop.point.y,drop.point.z,drop.stack.copy());item.setUUID(drop.identity);NativeGearDropEscrow.prepare(item,operation,root);return item;}
    boolean matches(ItemEntity item,BlockPos root,Drop drop){return item!=null&&!item.isRemoved()&&item.getUUID().equals(drop.identity)&&ItemStack.matches(item.getItem(),drop.stack)&&item.position().distanceToSqr(drop.point)<1e-12&&operation.equals(NativeGearDropEscrow.operation(item))&&item.getPersistentData().getCompound(NativeGearDropEscrow.KEY).getLong("Root")==root.asLong();}
    /** Vanilla may omit an actor whose save callback throws. Never commit an incomplete drop record. */
    void requireRecord(HolderLookup.Provider registries,BlockPos root,net.minecraft.world.level.ChunkPos chunk,CompoundTag record,boolean present){
        var rows=record==null?new ListTag():record.getList("Entities",Tag.TAG_COMPOUND);var found=new HashMap<UUID,CompoundTag>();
        for(var raw:rows){var row=(CompoundTag)raw;if(row.hasUUID("UUID")){var id=row.getUUID("UUID");if(dropsById.containsKey(id)&&found.put(id,row)!=null)throw new IllegalStateException("Native harvest actor record is duplicated");}}
        for(var drop:drops){var row=found.get(drop.identity);boolean expected=present&&chunk.equals(new net.minecraft.world.level.ChunkPos(BlockPos.containing(drop.point)));if((row!=null)!=expected)throw new IllegalStateException("Native harvest actor record is missing or unexpectedly present");if(row==null)continue;
            var pos=row.getList("Pos",Tag.TAG_DOUBLE);var escrow=row.getCompound("NeoForgeData").getCompound(NativeGearDropEscrow.KEY);
            if(!row.getString("id").equals("minecraft:item")||pos.size()!=3||pos.getDouble(0)!=drop.point.x||pos.getDouble(1)!=drop.point.y||pos.getDouble(2)!=drop.point.z||!row.getCompound("Item").equals(drop.stack.save(registries))||!escrow.getAllKeys().equals(Set.of("Operation","Root"))||!escrow.hasUUID("Operation")||!operation.equals(escrow.getUUID("Operation"))||!escrow.contains("Root",Tag.TAG_LONG)||escrow.getLong("Root")!=root.asLong())throw new IllegalStateException("Native harvest actor record changed");
        }
    }
    CompoundTag write(HolderLookup.Provider registries){
        var tag=new CompoundTag();tag.putInt("Schema",1);tag.putUUID("Operation",operation);tag.putUUID("Actor",actor);tag.putUUID("Payload",payload);tag.putByteArray("CellDigest",cellDigest);tag.putInt("Cell",cell);tag.putBoolean("Creative",creative);tag.put("Tool",tool.saveOptional(registries));var list=new ListTag();
        for(var drop:drops){var item=new CompoundTag();item.putUUID("Identity",drop.identity);item.put("Item",drop.stack.save(registries));var pos=new ListTag();pos.add(DoubleTag.valueOf(drop.point.x));pos.add(DoubleTag.valueOf(drop.point.y));pos.add(DoubleTag.valueOf(drop.point.z));item.put("Position",pos);list.add(item);}tag.put("Drops",list);if(tag.sizeInBytes()>MAX_BYTES)throw new IllegalArgumentException("Arm harvest receipt byte bound");return tag;
    }
    static NativeGearHarvestReceipt read(HolderLookup.Provider registries,BlockPos root,CompoundTag tag){
        if(tag.sizeInBytes()>MAX_BYTES||!tag.getAllKeys().equals(Set.of("Schema","Operation","Actor","Payload","CellDigest","Cell","Creative","Tool","Drops"))||!tag.contains("Schema",Tag.TAG_INT)||tag.getInt("Schema")!=1||!tag.hasUUID("Operation")||!tag.hasUUID("Actor")||!tag.hasUUID("Payload")||!tag.contains("CellDigest",Tag.TAG_BYTE_ARRAY)||tag.getByteArray("CellDigest").length!=32||!tag.contains("Cell",Tag.TAG_INT)||!tag.contains("Creative",Tag.TAG_BYTE)||tag.getByte("Creative")<0||tag.getByte("Creative")>1||!tag.contains("Tool",Tag.TAG_COMPOUND)||!(tag.get("Drops") instanceof ListTag list)||list.size()>MAX_DROPS||!list.isEmpty()&&list.getElementType()!=Tag.TAG_COMPOUND)throw new IllegalArgumentException("Malformed arm harvest receipt");
        var tool=stack(registries,tag.getCompound("Tool"),true);var drops=new ArrayList<Drop>();for(var raw:list){var entry=(CompoundTag)raw;if(!entry.getAllKeys().equals(Set.of("Identity","Item","Position"))||!entry.hasUUID("Identity")||!entry.contains("Item",Tag.TAG_COMPOUND)||!(entry.get("Position") instanceof ListTag pos)||pos.size()!=3||pos.getElementType()!=Tag.TAG_DOUBLE)throw new IllegalArgumentException("Malformed arm harvest drop");var point=new Vec3(pos.getDouble(0),pos.getDouble(1),pos.getDouble(2));position(root,point);drops.add(new Drop(entry.getUUID("Identity"),stack(registries,entry.getCompound("Item"),false),point));}
        var receipt=new NativeGearHarvestReceipt(tag.getUUID("Operation"),tag.getUUID("Actor"),tag.getUUID("Payload"),tag.getByteArray("CellDigest"),tag.getInt("Cell"),tag.getBoolean("Creative"),tool,drops);if(!receipt.write(registries).equals(tag))throw new IllegalArgumentException("Noncanonical arm harvest receipt");return receipt;
    }
    private static ItemStack stack(HolderLookup.Provider registries,CompoundTag tag,boolean empty){if(tag.isEmpty()&&empty)return ItemStack.EMPTY;var stack=ItemStack.parse(registries,tag).orElseThrow(()->new IllegalArgumentException("Unknown arm harvest item"));if(stack.isEmpty()||stack.getCount()>stack.getMaxStackSize()||!stack.save(registries).equals(tag))throw new IllegalArgumentException("Malformed arm harvest item");return stack;}
}
