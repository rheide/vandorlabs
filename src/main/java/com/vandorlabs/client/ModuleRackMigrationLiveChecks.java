package com.vandorlabs.client;

import com.vandorlabs.blocks.*;
import com.vandorlabs.rack.*;
import com.vandorlabs.redstone.ChannelList;
import com.vandorlabs.tiles.*;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.*;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldServer;
import net.minecraft.world.storage.WorldSavedData;
import java.io.*;
import java.util.UUID;

/** One old physical controller saved without the new hook, then reopened by the hook candidate. */
@net.minecraftforge.fml.relauncher.SideOnly(net.minecraftforge.fml.relauncher.Side.CLIENT)
final class ModuleRackMigrationLiveChecks {
    private static final String NAME="vandorlabs_rack_upgrade_fixture";
    private static final BlockPos AT=new BlockPos(88,110,88);
    private static boolean done,catalogChecked;
    private static int wait,checks;
    private static void need(boolean value,String reason){checks++;if(!value)throw new IllegalStateException("Rack saved upgrade age="+wait+": "+reason);}
    private static Record record(WorldServer world){return (Record)world.getPerWorldStorage().getOrLoadData(Record.class,NAME);}
    static void write(Minecraft mc,File directory)throws Exception{
        ModuleRackLiveChecks.server(mc,()->{
            try{
                EntityPlayerMP actor=ModuleRackLiveChecks.player(mc);WorldServer world=actor.getServerWorld();
                world.setBlockState(AT,ModBlocks.AUTOPILOT.getDefaultState().withProperty(BlockVandorDirectional.FACING,EnumFacing.WEST),3);
                TileEntityAutopilot tile=(TileEntityAutopilot)world.getTileEntity(AT);
                tile.setRedstoneChannels(ChannelList.of(21991,21992));tile.trigger=1;tile.marker="minecraft:wool";tile.markerMeta=14;
                tile.setEnabled(true);tile.departureAt=Long.MAX_VALUE;tile.departureSeat=AT.south();tile.departureOwner=actor.getUniqueID();tile.departureMode=2;tile.departureHeight=7;tile.departureDirect=false;tile.departureWaypoint=AT.down(2);
                NBTTagCompound raw=tile.writeToNBT(new NBTTagCompound()),caps=new NBTTagCompound();caps.setString("BlockState","minecraft:air opaque previous-layout data");caps.setIntArray("OwnerValues",new int[]{3,7,37});raw.setTag("ForgeCaps",caps);
                NBTTagCompound future=new NBTTagCompound();future.setString("FutureNavigationSetting","preserve without interpretation");raw.setTag("ForeignAutopilotData",future);
                tile.readFromNBT(raw);WorldActionAuthorization.authorize(tile,actor);tile.changed();DeferredTileLoad.flush(world);
                need(world.getTileEntity(AT).getClass()==TileEntityAutopilot.class,"write candidate has no automatic conversion hook");
                Record saved=new Record(NAME);saved.before=tile.writeToNBT(new NBTTagCompound());saved.actor=WorldActionAuthorization.actorId(tile);
                need(actor.getUniqueID().equals(saved.actor),"old-source authorization exists before save");world.getPerWorldStorage().setData(NAME,saved);saved.markDirty();
                CompressedStreamTools.writeCompressed(saved.before,new FileOutputStream(new File(directory,"module_rack_before.nbt")));
                world.getMinecraftServer().saveAllWorlds(false);
                System.out.println("[vandorlabs][reprolab] module-rack PASS migration-write checks="+checks+" exactOldBlock/originalRawCaps/nav/owner/prehookSavedWorld");
            }catch(IOException failure){throw new IllegalStateException(failure);}
        });
    }
    static boolean read(Minecraft mc,File directory)throws Exception{
        if(done)return true;
        if(!catalogChecked){catalog();catalogChecked=true;}
        if(++wait%80==0)System.out.println("[vandorlabs][reprolab] Rack saved upgrade waiting age="+wait+" mode="+mc.playerController.getCurrentGameType()+" held="+mc.player.getHeldItemMainhand()+" position="+mc.player.getPositionVector());
        need(wait<240,"bounded new-load migration");
        final boolean[] ready={false};
        ModuleRackLiveChecks.server(mc,()->{
            try{
                EntityPlayerMP player=ModuleRackLiveChecks.player(mc);WorldServer world=player.getServerWorld();Record original=record(world);
                need(original!=null&&original.actor!=null&&original.before.getString("id").equals("vandorlabs:autopilot"),"same owned old-source world and original raw preimage");
                if(!world.isBlockLoaded(AT,false)||!(world.getTileEntity(AT) instanceof TileEntityModuleRack))return;
                TileEntityModuleRack rack=(TileEntityModuleRack)world.getTileEntity(AT);NBTTagCompound current=rack.writeToNBT(new NBTTagCompound());
                need(world.getBlockState(AT).getBlock() instanceof BlockModuleRack&&world.getBlockState(AT).getValue(BlockModuleRack.FACING)==EnumFacing.WEST,"normal new-version chunk load converts actual block and facing");
                need(rack.autopilotCount()==1&&rack.getOccupiedMask()==1&&rack.getStackInSlot(0).getCount()==1,"one usable Rack with one physical module");
                need(current.getCompoundTag(LegacyAutopilotMigration.BACKUP).equals(original.before),"complete original old-source raw backup survives actual restart");
                need(current.getCompoundTag("ForgeCaps").equals(original.before.getCompoundTag("ForgeCaps"))&&current.getCompoundTag("ForeignAutopilotData").equals(original.before.getCompoundTag("ForeignAutopilotData")),"opaque old Caps/unknown own fields retain exact values");
                need(rack.enabled()&&rack.getRedstoneChannels().equals(ChannelList.of(21991,21992))&&rack.marker.equals("minecraft:wool")&&rack.markerMeta==14
                        &&rack.departureAt==Long.MAX_VALUE&&rack.departureSeat.equals(AT.south())&&rack.departureOwner.equals(original.actor)
                        &&rack.departureMode==2&&rack.departureHeight==7&&!rack.departureDirect&&rack.departureWaypoint.equals(AT.down(2)),"old receiving/activation/navigation/departure state remains live in module");
                need(original.actor.equals(WorldActionAuthorization.actorId(rack)),"trusted old chunk receipt rebinds to new physical host after restart");
                need(world.getEntitiesWithinAABB(net.minecraft.entity.item.EntityItem.class,new net.minecraft.util.math.AxisAlignedBB(87,109,87,90,113,90)).isEmpty(),"conversion creates no extra item owner/drop");
                need(!rack.getUpdateTag().hasKey("Items")&&!rack.getUpdateTag().hasKey("ForgeCaps")&&!rack.getUpdateTag().hasKey(LegacyAutopilotMigration.BACKUP),"new-load public appearance excludes saved private owners");
                CompressedStreamTools.writeCompressed(current,new FileOutputStream(new File(directory,"module_rack_after.nbt")));world.getMinecraftServer().saveAllWorlds(false);ready[0]=true;
            }catch(IOException failure){throw new IllegalStateException(failure);}
        });
        if(!ready[0])return false;done=true;
        System.out.println("[vandorlabs][reprolab] module-rack PASS migration-read checks="+checks+" actualNewChunkLoad/exactOriginalRawCaps/nav/owner/facing/oneModule/noDrops/privateAppearance");return true;
    }
    private static void catalog()throws Exception {
        need(net.minecraftforge.fml.common.Loader.isModLoaded("jei"),"actual JEI fixture installed for changed catalog entries");
        Object registry=Class.forName("mezz.jei.Internal").getMethod("getIngredientRegistry").invoke(null);
        java.util.Collection<?> ingredients=(java.util.Collection<?>)registry.getClass().getMethod("getAllIngredients",Class.class).invoke(registry,net.minecraft.item.ItemStack.class);
        net.minecraft.item.Item[] items={net.minecraft.item.Item.getItemFromBlock(net.minecraft.block.Block.getBlockFromName("vandorlabs:module_rack")),com.vandorlabs.items.ModItems.AUTOPILOT_MODULE,net.minecraft.item.Item.getItemFromBlock(ModBlocks.AUTOPILOT)};
        for(int index=0;index<items.length;index++) {
            net.minecraft.item.Item item=items[index];int expected=index==2?0:1;
            need(!new net.minecraft.item.ItemStack(item).isEmpty(),"compatibility item ID still registered "+index);
            for(net.minecraft.creativetab.CreativeTabs tab:new net.minecraft.creativetab.CreativeTabs[]{com.vandorlabs.VandorLabs.VANDOR_LABS_TAB,net.minecraft.creativetab.CreativeTabs.SEARCH}) {
                net.minecraft.util.NonNullList<net.minecraft.item.ItemStack> entries=net.minecraft.util.NonNullList.create();item.getSubItems(tab,entries);need(entries.size()==expected,"exact mod/search enumeration "+item.getRegistryName());
            }
            need(item.getCreativeTabs().length==expected,"JEI uncategorized fallback remains disabled for old block");
            long found=ingredients.stream().filter(value->value instanceof net.minecraft.item.ItemStack&&((net.minecraft.item.ItemStack)value).getItem()==item).count();
            need(found==expected,"actual JEI registry enumeration "+item.getRegistryName()+" count="+found);
        }
        net.minecraft.item.crafting.IRecipe recipe=net.minecraft.item.crafting.CraftingManager.REGISTRY.getObject(new net.minecraft.util.ResourceLocation("vandorlabs:autopilot"));
        need(recipe!=null&&recipe.getRecipeOutput().getItem()==com.vandorlabs.items.ModItems.AUTOPILOT_MODULE,"existing recipe identity now produces the replacement module");
        System.out.println("[vandorlabs][reprolab] module-rack PASS catalog modTab/search/actualJEI Rack+moduleVisible oldAutopilotHidden ID+recipeRetained");
    }
    public static final class Record extends WorldSavedData {
        NBTTagCompound before=new NBTTagCompound();UUID actor;
        public Record(String name){super(name);}
        @Override public void readFromNBT(NBTTagCompound data){before=data.getCompoundTag("Before").copy();actor=data.hasUniqueId("Actor")?data.getUniqueId("Actor"):null;}
        @Override public NBTTagCompound writeToNBT(NBTTagCompound data){data.setTag("Before",before.copy());if(actor!=null)data.setUniqueId("Actor",actor);return data;}
    }
}
