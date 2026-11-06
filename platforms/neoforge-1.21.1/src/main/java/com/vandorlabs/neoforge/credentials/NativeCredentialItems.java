package com.vandorlabs.neoforge.credentials;

import com.vandorlabs.credentials.CredentialCatalog;
import java.util.*;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.*;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.*;

/** Two canonical item IDs. Company data and original legacy payloads are independent. */
public final class NativeCredentialItems {
    public static final String LEGACY_KEY="VandorLegacyCredential";
    private static final DeferredRegister.Items ITEMS=DeferredRegister.createItems("vandorlabs");
    public static final DeferredItem<NativeCredentialItem> ACCESS_CARD=ITEMS.register("access_card",()->new NativeCredentialItem(false));
    public static final DeferredItem<NativeCredentialItem> DATA_CHIP=ITEMS.register("data_chip",()->new NativeCredentialItem(true));
    private NativeCredentialItems(){}
    public static void register(IEventBus bus){ITEMS.register(bus);}
    public static ItemStack accessCard(int company){return variant(false,company);}
    public static ItemStack dataChip(int company){return variant(true,company);}
    private static ItemStack variant(boolean chip,int company){if(!CredentialCatalog.validCompany(company))throw new IllegalArgumentException("Credential company");var result=new ItemStack(chip?DATA_CHIP.get():ACCESS_CARD.get());result.set(DataComponents.CUSTOM_MODEL_DATA,new CustomModelData(company));return result;}
    public static List<ItemStack> creativeVariants(){var result=new ArrayList<ItemStack>(14);for(boolean chip:new boolean[]{false,true})for(int company=0;company<CredentialCatalog.COMPANY_COUNT;company++)result.add(variant(chip,company));return List.copyOf(result);}
    /** Visual names/models may fall back; this raw value never converts an invalid credential to company0. */
    public static int company(ItemStack stack){if(stack.isEmpty())return -1;var value=stack.get(DataComponents.CUSTOM_MODEL_DATA);return value==null?0:value.value();}
    public static boolean accepts(ItemStack stack,boolean chip){return !stack.isEmpty()&&stack.is(chip?DATA_CHIP.get():ACCESS_CARD.get())&&CredentialCatalog.validCompany(company(stack));}
    public static boolean authorized(ItemStack stack,boolean chip,int allowed){return accepts(stack,chip)&&CredentialCatalog.authorized(company(stack),allowed);}
    /** Defensive complete original1.12 stack, including tag/ForgeCaps and unknown root fields. */
    public static CompoundTag legacyCredentialData(ItemStack stack){return stack.getOrDefault(DataComponents.CUSTOM_DATA,CustomData.EMPTY).copyTag().getCompound(LEGACY_KEY);}
    /** Specific credential decoder. Unknown/invalid data returns EMPTY so the owning reader retains its raw snapshot. */
    public static ItemStack readStack(CompoundTag raw,HolderLookup.Provider registries){
        if(!raw.contains("Count")&&!raw.contains("Damage")&&raw.contains("count",Tag.TAG_ANY_NUMERIC))return ItemStack.CODEC.parse(registries.createSerializationContext(NbtOps.INSTANCE),raw.copy()).result().orElse(ItemStack.EMPTY);
        if(!raw.contains("id",Tag.TAG_STRING)||raw.getByte("Count")<=0)return ItemStack.EMPTY;
        String id=raw.getString("id");boolean chip=id.equals("vandorlabs:data_chip");if(!chip&&!id.equals("vandorlabs:access_card"))return ItemStack.EMPTY;
        // Actual1.12 ItemStack(NBT) clamps negative SHORT Damage before credential validation.
        int company=Math.max(0,raw.getShort("Damage"));var stack=new ItemStack(chip?DATA_CHIP.get():ACCESS_CARD.get(),raw.getByte("Count"));stack.set(DataComponents.CUSTOM_MODEL_DATA,new CustomModelData(company));
        var data=new CompoundTag();data.put(LEGACY_KEY,raw.copy());stack.set(DataComponents.CUSTOM_DATA,CustomData.of(data));
        var display=raw.getCompound("tag").getCompound("display");if(display.contains("Name",Tag.TAG_STRING))stack.set(DataComponents.CUSTOM_NAME,Component.literal(display.getString("Name")));
        return stack;
    }
    /** Event/ownership boundary copy; never used by output or model hot paths. */
    public static ItemStack ownedCopy(ItemStack stack){
        if(stack.isEmpty())return ItemStack.EMPTY;var result=stack.copy();for(var component:stack.getComponents())copyMutableData(result,component);
        var name=stack.get(DataComponents.CUSTOM_NAME);if(name!=null)result.set(DataComponents.CUSTOM_NAME,name.copy());
        var itemName=stack.get(DataComponents.ITEM_NAME);if(itemName!=null)result.set(DataComponents.ITEM_NAME,itemName.copy());
        var lore=stack.get(DataComponents.LORE);if(lore!=null)result.set(DataComponents.LORE,new ItemLore(lore.lines().stream().map(value->(Component)value.copy()).toList()));
        return result;
    }
    @SuppressWarnings("unchecked") // Runtime type proves that this registered value is CustomData.
    private static <T> void copyMutableData(ItemStack target,net.minecraft.core.component.TypedDataComponent<T> component){if(component.value() instanceof CustomData data)target.set(component.type(),(T)CustomData.of(data.copyTag()));}
    public static CompoundTag writeStack(ItemStack stack,HolderLookup.Provider registries){if(stack.isEmpty())return new CompoundTag();var result=ItemStack.CODEC.encodeStart(registries.createSerializationContext(NbtOps.INSTANCE),stack).getOrThrow();if(!(result instanceof CompoundTag compound))throw new IllegalStateException("Credential stack encoding");return compound.copy();}
}
