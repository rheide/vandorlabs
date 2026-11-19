package com.vandorlabs.neoforge.copier;

import com.vandorlabs.neoforge.inventory.NativeInventoryItems;
import com.vandorlabs.neoforge.transfer.NativeRawCell;
import java.util.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.properties.*;

/** Construction costs include private inventories. Filled carriers can never satisfy an empty block cost. */
public final class NativeCopyMaterials {
    private static final Set<String> OWNED_LISTS=Set.of("Items","PortItems","Inventory","inventory","Passengers","ArmPayload","NativeGearPayload","InsertedToken","NavigationChip","NavigationModule");
    private static final Set<String> OWNED_AMOUNTS=Set.of("Energy","PortEnergy","energy","energyStored","electricityStored","Amount","amount","BurnTime","Fuel");
    public static final class Need {
        private final ItemStack item;public final boolean exact;public final long count;
        private Need(ItemStack item,long count,boolean exact){this.item=item.copyWithCount(1);this.count=count;this.exact=exact;}
        public ItemStack item(){return item.copy();}
        public String description(){return item.getHoverName().getString()+" x "+count;}
    }
    public static List<Need> prepare(ServerLevel level,List<NativeRawCell> source,List<BlockEntity> staged,boolean creative){
        if(source.size()!=staged.size()||source.size()>32768)throw new IllegalArgumentException("Construction cost cell count");
        if(creative)return List.of();var needs=new ArrayList<Need>();var observerMatter=NativeCopyObservers.validate(source);
        for(int cell=0;cell<source.size();cell++){
            var saved=source.get(cell);var state=saved.state;var tile=staged.get(cell);var raw=saved.tile();
            if(raw!=null&&raw.contains("LootTable"))throw new IllegalArgumentException("Open loot containers before Survival copying");
            if(state.getBlock() instanceof com.vandorlabs.neoforge.navigation.observer.NativeObserverPartBlock){
                if(observerMatter.contains(saved.pos))add(needs,new ItemStack(com.vandorlabs.neoforge.navigation.observer.NativeObserverFormation.matter()),1,false);
                continue;
            }
            var item=state.getBlock().asItem();if(item==Items.AIR)throw new IllegalArgumentException("Block has no obtainable material item");
            int count=1;if((state.getBlock() instanceof DoorBlock||state.getBlock() instanceof DoublePlantBlock)&&state.getValue(BlockStateProperties.DOUBLE_BLOCK_HALF)==DoubleBlockHalf.UPPER)count=0;
            if(state.getBlock() instanceof BedBlock&&state.getValue(BlockStateProperties.BED_PART)==BedPart.HEAD)count=0;
            if(state.getBlock() instanceof SlabBlock&&state.getValue(BlockStateProperties.SLAB_TYPE)==SlabType.DOUBLE)count=2;
            if(state.getBlock() instanceof SnowLayerBlock)count=state.getValue(SnowLayerBlock.LAYERS);
            boolean carrier=tile instanceof com.vandorlabs.neoforge.reservoir.NativeBufferEntity buffer&&buffer.amount()>0||
                tile instanceof com.vandorlabs.neoforge.ports.NativeResourcePortEntity&&retained(raw,0,new int[]{0});
            var material=new ItemStack(item,count);
            if(carrier){
                var data=raw.copy();for(String key:List.of("x","y","z"))data.remove(key);material.set(DataComponents.BLOCK_ENTITY_DATA,CustomData.of(data));
                // This is exactly the configured native block item, including its resource type/components.
                if(count!=1)throw new IllegalArgumentException("Resource carrier material quantity");
                add(needs,material,1,true);continue;
            }
            if(raw!=null){for(String key:List.of("ForgeCaps","neoforge:attachments"))if(raw.contains(key)&&!(raw.get(key) instanceof CompoundTag data&&data.isEmpty()))throw new IllegalArgumentException("Unknown stored ownership needs an exact material adapter");}
            add(needs,material,count,false);
            if(tile instanceof com.vandorlabs.neoforge.navigation.NativeNavigationTableEntity table){
                var chip=table.storedChip();
                add(needs,chip,chip.getCount(),true);
                var module=table.storedModule();
                add(needs,module,module.getCount(),true);
            }
            if(tile instanceof com.vandorlabs.neoforge.rack.NativeRackEntity rack){for(var module:rack.ownedModuleDrops())add(needs,module,module.getCount(),true);}
            if(tile instanceof Container inventory){if(inventory.getContainerSize()>8192)throw new IllegalArgumentException("Construction inventory slot bound");for(int slot=0;slot<inventory.getContainerSize();slot++){var stack=inventory.getItem(slot);add(needs,stack,stack.getCount(),true);}}
            if(tile instanceof com.vandorlabs.neoforge.credentials.NativeCredentialReaderEntity reader){var token=reader.getItem(0);if(!(tile instanceof Container))add(needs,token,token.getCount(),true);}
        }
        needs.sort(Comparator.comparing(need->!need.exact));return List.copyOf(needs);
    }
    public static ListTag write(List<Need> needs,net.minecraft.core.HolderLookup.Provider registries){var list=new ListTag();for(var need:needs){var row=new CompoundTag();row.put("Item",need.item.save(registries));row.putLong("Count",need.count);row.putBoolean("Exact",need.exact);list.add(row);}return list;}
    public static List<Need> read(ListTag list,net.minecraft.core.HolderLookup.Provider registries){if(list.size()>32768||!list.isEmpty()&&list.getElementType()!=Tag.TAG_COMPOUND)throw new IllegalArgumentException("Construction cost list bound");var result=new ArrayList<Need>();var budget=new NativeInventoryItems.Budget();for(Tag value:list){var row=(CompoundTag)value;if(!row.getAllKeys().equals(Set.of("Item","Count","Exact"))||!row.contains("Item",Tag.TAG_COMPOUND)||!row.contains("Count",Tag.TAG_LONG)||row.getLong("Count")<1||row.getLong("Count")>Integer.MAX_VALUE||!row.contains("Exact",Tag.TAG_BYTE)||row.getByte("Exact")<0||row.getByte("Exact")>1)throw new IllegalArgumentException("Construction cost schema");var item=NativeInventoryItems.modern(row.getCompound("Item"),registries,budget);if(item.getCount()!=1)throw new IllegalArgumentException("Construction cost prototype count");result.add(new Need(item,row.getLong("Count"),row.getBoolean("Exact")));}return List.copyOf(result);}
    public static boolean same(List<Need> first,List<Need> second){if(first.size()!=second.size())return false;for(int i=0;i<first.size();i++){var a=first.get(i);var b=second.get(i);if(a.exact!=b.exact||a.count!=b.count||!ItemStack.isSameItemSameComponents(a.item,b.item))return false;}return true;}
    private static void add(List<Need> needs,ItemStack item,long count,boolean exact){
        if(count<0||count>Integer.MAX_VALUE)throw new IllegalArgumentException("Construction material quantity");if(item.isEmpty()||count==0)return;
        for(int i=0;i<needs.size();i++){var old=needs.get(i);if(old.exact==exact&&ItemStack.isSameItemSameComponents(old.item,item)){long combined=Math.addExact(old.count,count);if(combined>Integer.MAX_VALUE)throw new IllegalArgumentException("Construction material quantity");needs.set(i,new Need(item,combined,exact));return;}}
        if(needs.size()>=32768)throw new IllegalArgumentException("Distinct construction material bound");needs.add(new Need(item,count,exact));
    }
    public static boolean satisfies(Need need,ItemStack supplied){
        if(supplied.isEmpty()||!supplied.is(need.item.getItem()))return false;
        if(need.exact)return ItemStack.isSameItemSameComponents(need.item,supplied);
        if(NativeInventoryItems.containedOwnership(supplied))return false;
        String namespace=BuiltInRegistries.ITEM.getKey(supplied.getItem()).getNamespace();if(!namespace.equals("minecraft")&&!namespace.equals("vandorlabs"))return ItemStack.isSameItemSameComponents(need.item,supplied);
        var block=supplied.getOrDefault(DataComponents.BLOCK_ENTITY_DATA,CustomData.EMPTY).copyTag();var custom=supplied.getOrDefault(DataComponents.CUSTOM_DATA,CustomData.EMPTY).copyTag();
        return !retained(block,0,new int[]{0})&&!retained(custom,0,new int[]{0});
    }
    /** A conservative cost check, not a migration walk: opaque capabilities are never interpreted as free material. */
    private static boolean retained(Tag tag,int depth,int[] visited){
        if(tag==null)return false;if(depth>32||++visited[0]>32768)return true;
        if(tag instanceof CompoundTag data){for(String key:data.getAllKeys()){
            Tag value=data.get(key);
            if((key.equals("ForgeCaps")||key.equals("neoforge:attachments"))&&!(value instanceof CompoundTag empty&&empty.isEmpty()))return true;
            if(OWNED_LISTS.contains(key)&&value!=null&&(!(value instanceof ListTag list)||!list.isEmpty())&&(!(value instanceof CompoundTag compound)||!compound.isEmpty()))return true;
            if(OWNED_AMOUNTS.contains(key)&&value instanceof NumericTag number&&number.getAsDouble()>0)return true;
            if(retained(value,depth+1,visited))return true;
        }}else if(tag instanceof ListTag list)for(Tag child:list)if(retained(child,depth+1,visited))return true;return false;
    }
    private NativeCopyMaterials(){}
}
