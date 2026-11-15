package com.vandorlabs.client;

import com.vandorlabs.blocks.BlockModuleRack;
import com.vandorlabs.items.ModItems;
import com.vandorlabs.tiles.TileEntityModuleRack;
import net.minecraft.item.*;
import net.minecraft.nbt.*;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.*;
import net.minecraftforge.fml.common.registry.*;

/** A failed Rack decode owns the complete raw inventory, never a truncated usable subset. */
public final class ModuleRackSavedDataChecks {
    private static int checks;
    private static BlockModuleRack block;
    private static void need(boolean value,String why){checks++;if(!value)throw new AssertionError(why);}
    private static NBTTagCompound raw(){TileEntityModuleRack rack=new TileEntityModuleRack();need(rack.insertModule(0,new ItemStack(ModItems.BLANK_MODULE),false),"prepare one module");return rack.writeToNBT(new NBTTagCompound());}
    private static void refused(NBTTagCompound raw,String why){
        NBTTagCompound original=raw.copy();TileEntityModuleRack rack=new TileEntityModuleRack();rack.readFromNBT(raw);
        need(rack.writeToNBT(new NBTTagCompound()).equals(original),why+": exact original inventory retained");
        need(rack.getOccupiedMask()==0&&!rack.enabled()&&rack.drainModules().isEmpty(),why+": no live partial owners");
        ItemStack held=new ItemStack(ModItems.BLANK_MODULE);need(!rack.insertModule(1,held,false)&&held.getCount()==1&&rack.removeModule(0).isEmpty(),why+": no inventory mutation");
        need(raw.equals(original),why+": caller preimage unchanged");
        NonRenderingChecks.MemoryWorld world=new NonRenderingChecks.MemoryWorld(false);BlockPos at=new BlockPos(0,80,0);
        world.setBlockState(at,block.getDefaultState(),3);TileEntityModuleRack installed=(TileEntityModuleRack)world.getTileEntity(at);installed.readFromNBT(raw);
        ItemStack carrier=block.getPickBlock(block.getDefaultState(),new RayTraceResult(new Vec3d(.5,80.5,.5),net.minecraft.util.EnumFacing.UP,at),world,at,null);
        NBTTagCompound item=carrier.getSubCompound("BlockEntityTag"),expected=original.copy();for(String key:new String[]{"id","x","y","z"})expected.removeTag(key);
        need(item!=null&&item.equals(expected),why+": one exact recovery carrier");
        block.breakBlock(world,at,block.getDefaultState());need(world.loadedEntityList.isEmpty(),why+": no loose partial module drops");
    }
    public static void main(String[] args){
        net.minecraft.init.Bootstrap.register();ForgeRegistries.ITEMS.register(ModItems.BLANK_MODULE);ForgeRegistries.ITEMS.register(ModItems.AUTOPILOT_MODULE);
        block=new BlockModuleRack();ForgeRegistries.BLOCKS.register(block);ForgeRegistries.ITEMS.register(new ItemBlock(block).setRegistryName(block.getRegistryName()));
        GameRegistry.registerTileEntity(TileEntityModuleRack.class,new ResourceLocation("vandorlabs:module_rack"));
        NBTTagCompound valid=raw();valid.setString("FutureRackField","retain own extension");NBTTagCompound caps=new NBTTagCompound();caps.setString("absent:module_provider","retain exactly");valid.setTag("ForgeCaps",caps);
        TileEntityModuleRack rack=new TileEntityModuleRack();rack.readFromNBT(valid);need(rack.getOccupiedMask()==1,"valid module loaded");need(rack.writeToNBT(new NBTTagCompound()).equals(valid),"valid unknown root fields/capabilities retained");
        NBTTagCompound value=raw();value.getTagList("Items",10).appendTag(value.getTagList("Items",10).getCompoundTagAt(0).copy());refused(value,"duplicate slot");
        value=raw();value.getTagList("Items",10).getCompoundTagAt(0).setString("id","absent:module");refused(value,"unknown module provider");
        value=raw();value.getTagList("Items",10).getCompoundTagAt(0).setByte("Count",(byte)2);refused(value,"multiple owners in one slot");
        value=raw();value.getTagList("Items",10).getCompoundTagAt(0).setByte("Slot",(byte)4);refused(value,"out-of-range slot");
        value=raw();value.getTagList("Items",10).getCompoundTagAt(0).setInteger("Slot",0);refused(value,"wrong slot type");
        value=raw();value.setString("Items","wrong type");refused(value,"wrong inventory type");
        value=raw();value.setLong("Revision",-1);refused(value,"negative sequence");
        value=raw();value.setInteger("RackHousing",-2);refused(value,"invalid housing choice");
        value=raw();value.getTagList("Items",10).getCompoundTagAt(0).setString("FutureOwner","retain");refused(value,"unknown item owner field");
        System.out.println("Module Rack saved-data PASS checks="+checks+" strict staged slots/items, no partial owners, exact inert recovery item, opaque root/capability preservation");
    }
}
