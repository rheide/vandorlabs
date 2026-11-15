package com.vandorlabs.client;

import com.vandorlabs.ports.*;
import net.minecraft.init.*;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.*;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fluids.*;
import net.minecraftforge.fml.common.registry.GameRegistry;

/** Saved owners must not become empty when a provider or inventory row cannot be decoded. */
public final class ResourcePortSavedDataChecks {
    private static int checks;
    private static void need(boolean value,String why){checks++;if(!value)throw new AssertionError(why);}
    private static ResourcePortTile fresh(int kind){return kind==0?new PowerPortTile():kind==1?new FluidPortTile():new MatterPortTile();}
    private static NBTTagCompound raw(int kind){
        NBTTagCompound tag=fresh(kind).writeToNBT(new NBTTagCompound());tag.setString("AuditOpaque","preserve own extension");
        NBTTagCompound caps=new NBTTagCompound();caps.setString("absent:provider","retain exactly");tag.setTag("ForgeCaps",caps);
        if(kind==0)tag.setInteger("PortEnergy",37);
        else if(kind==1){FluidStack fluid=new FluidStack(FluidRegistry.WATER,37);fluid.tag=new NBTTagCompound();fluid.tag.setString("Batch","opaque fluid metadata");tag.setTag("PortFluid",fluid.writeToNBT(new NBTTagCompound()));}
        else{NBTTagCompound row=new ItemStack(Items.DIAMOND,37).writeToNBT(new NBTTagCompound());row.setByte("Slot",(byte)0);NBTTagList rows=new NBTTagList();rows.appendTag(row);tag.setTag("Items",rows);}
        return tag;
    }
    private static void refused(int kind,NBTTagCompound tag,String why){
        NBTTagCompound original=tag.copy();ResourcePortTile tile=fresh(kind);tile.readFromNBT(tag);
        need(tile.inert(),why+": inert");need(tag.equals(original),why+": input unchanged");
        need(tile.writeToNBT(new NBTTagCompound()).equals(original),why+": complete original retained");
        need(!tile.hasCapability(net.minecraftforge.energy.CapabilityEnergy.ENERGY,null),why+": no active fallback capability");
    }
    public static void main(String[] args){
        Bootstrap.register();
        GameRegistry.registerTileEntity(PowerPortTile.class,new ResourceLocation("vandorlabs:power_port"));
        GameRegistry.registerTileEntity(FluidPortTile.class,new ResourceLocation("vandorlabs:fluid_port"));
        GameRegistry.registerTileEntity(MatterPortTile.class,new ResourceLocation("vandorlabs:matter_port"));
        for(int kind=0;kind<3;kind++){
            NBTTagCompound source=raw(kind),original=source.copy();ResourcePortTile tile=fresh(kind);tile.readFromNBT(source);
            need(!tile.inert(),"valid owner accepted kind="+kind);need(source.equals(original),"valid source unmodified");
            need(tile.writeToNBT(new NBTTagCompound()).equals(original),"exact resource/capability save kind="+kind);
            NBTTagCompound mixed=source.copy();if(kind==1)mixed.setInteger("PortEnergy",7);else mixed.setTag("PortFluid",raw(1).getTag("PortFluid").copy());refused(kind,mixed,"mixed resource owners kind="+kind);
        }
        NBTTagCompound fluid=raw(1);fluid.getCompoundTag("PortFluid").setString("FluidName","missing:removed_fluid");refused(1,fluid,"absent fluid provider");
        fluid=raw(1);fluid.setString("PortFluid","wrong type");refused(1,fluid,"fluid tag type");
        fluid=raw(1);fluid.getCompoundTag("PortFluid").setInteger("FutureVolume",23);refused(1,fluid,"unknown fluid owner field");
        fluid=raw(1);fluid.getCompoundTag("PortFluid").setLong("Amount",37);refused(1,fluid,"fluid amount type");
        fluid=raw(1);fluid.getCompoundTag("PortFluid").setInteger("Amount",0);refused(1,fluid,"nonpositive fluid amount");
        NBTTagCompound item=raw(2);item.getTagList("Items",10).getCompoundTagAt(0).setString("id","missing:removed_item");refused(2,item,"absent item provider");
        item=raw(2);item.getTagList("Items",10).appendTag(item.getTagList("Items",10).getCompoundTagAt(0).copy());refused(2,item,"duplicate item slot");
        item=raw(2);item.getTagList("Items",10).getCompoundTagAt(0).setByte("Slot",(byte)9);refused(2,item,"out-of-range item slot");
        item=raw(2);item.getTagList("Items",10).getCompoundTagAt(0).setInteger("Slot",0);refused(2,item,"item slot type");
        item=raw(2);item.getTagList("Items",10).getCompoundTagAt(0).setByte("Count",(byte)0);refused(2,item,"empty saved item owner");
        item=raw(2);item.getTagList("Items",10).getCompoundTagAt(0).setString("FutureItemOwner","retain");refused(2,item,"unknown item owner field");
        item=raw(2);item.setString("Items","wrong type");refused(2,item,"inventory tag type");
        System.out.println("Resource port saved-data PASS checks="+checks+" valid exact resources/opaque caps, missing providers/duplicates/types/mixed owners retained inert");
    }
}
