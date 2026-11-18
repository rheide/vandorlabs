package com.vandorlabs.client;

import com.vandorlabs.blocks.*;
import com.vandorlabs.items.ModItems;
import com.vandorlabs.navigation.NavigationLayout;
import com.vandorlabs.redstone.ChannelList;
import com.vandorlabs.tiles.*;
import com.vandorlabs.vehicle.*;
import net.minecraft.block.state.IBlockState;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;

/** Literal 2.10.0 own-field preimages, including the exact comparison used by recovery. */
public final class Release211SavedDefaultsChecks {
    private static int checks;
    private static final BlockPos POS=new BlockPos(8,80,8);

    private static void need(boolean value,String message){
        checks++;
        if(!value)throw new AssertionError(message);
    }

    /** Values follow the 2.10.0 Source, TokenSlot and SecuredDisplay writers.
     * Do not seed these fixtures with the current writer or remove its new fields:
     * that would hide future changes to the previous release's save contract. */
    private static NBTTagCompound display210(String id){
        NBTTagCompound tag=new NBTTagCompound();
        tag.setString("id",id);
        tag.setInteger("x",POS.getX());tag.setInteger("y",POS.getY());tag.setInteger("z",POS.getZ());
        tag.setIntArray("RedstoneChannels",new int[0]);
        tag.setInteger("ControlMountVersion",2);
        tag.setInteger("BaseHeight",0);tag.setInteger("BaseTilt",0);tag.setInteger("TiltDirection",0);
        tag.setInteger("VandorDataVersion",2);tag.setInteger("RedstoneChannel",0);
        tag.setBoolean("LocalOn",false);tag.setBoolean("ChannelInitialized",false);tag.setInteger("MountRotation",0);
        tag.setIntArray("LatchedLevels",new int[0]);tag.setIntArray("LatchedChannels",new int[0]);
        tag.setInteger("TokenLogo",0);tag.setLong("TokenRejectedUntil",0);
        tag.setBoolean("ChipSlot",true);tag.setBoolean("AnyToken",true);tag.setInteger("AllowedTokens",127);
        tag.setInteger("TokenBehavior",0);tag.setLong("TokenPulseUntil",0);
        tag.setBoolean("TokenToggled",false);tag.setBoolean("TokenToggleApplied",false);
        tag.setLong("TokenInsertStarted",0);tag.setLong("TokenInsertionSequence",0);
        tag.setInteger("NavigationTexture",-1);tag.setBoolean("NavigationAnimated",true);
        tag.setInteger("NavigationSpeed",1);tag.setBoolean("NavigationRedstone",false);
        tag.setBoolean("NavigationSecurity",false);tag.setBoolean("NavigationScreenOn",false);
        return tag;
    }

    private static NBTTagCompound core210(){
        NBTTagCompound tag=display210("vandorlabs:constructor");
        tag.setInteger("ConstructorCoreX",0);tag.setInteger("ConstructorCoreZ",0);
        tag.setInteger("MachineActivity",1);tag.setIntArray("MachineSurfaceTextures",new int[0]);
        return tag;
    }

    private static NBTTagCompound table210(){
        NBTTagCompound tag=display210("vandorlabs:navigation_table");
        tag.setInteger("NavigationConnections",0);tag.setLong("NavigationOwner",0);
        tag.setBoolean("NavigationFormed",true);tag.setLong("NavigationOrigin",0);
        tag.setInteger("NavigationWidth",1);tag.setInteger("NavigationDepth",1);
        tag.setInteger("NavigationFacing",EnumFacing.NORTH.getHorizontalIndex());
        return tag;
    }

    private static TileEntity restored(NonRenderingChecks.MemoryWorld world,IBlockState state,NBTTagCompound saved){
        TileEntity tile=TileEntity.create(null,saved.copy());
        need(tile!=null,"Previous-release tile factory is still available");
        tile.setWorld(world);world.states.put(POS,state);world.tiles.put(POS,tile);
        return tile;
    }

    private static void exact(IBlockState state,NBTTagCompound saved,TileEntity restored,String description){
        NBTTagCompound after=restored.writeToNBT(new NBTTagCompound());
        need(saved.equals(after),description+" exact recovery receipt: expected="+saved+" actual="+after);
        VehicleStructure.Cell cell=new VehicleStructure.Cell(POS,state,saved);
        MovingBlockSupport.requireExactTileData(cell,restored);
        need(cell.tileData().equals(saved),description+" preflight leaves original snapshot untouched");
        // An unknown owned field still refuses transfer; compatibility does not waive data ownership.
        NBTTagCompound unknown=saved.copy();unknown.setInteger("UnknownOwnedPayload",73);
        boolean rejected=false;
        try{MovingBlockSupport.requireExactTileData(new VehicleStructure.Cell(POS,state,unknown),restored);}
        catch(IllegalArgumentException expected){rejected=true;}
        need(rejected,description+" unknown own data remains rejected");
    }

    private static void cores(){
        for(String design:new String[]{"synthesis_tower","assembly_press","enclosed_gantry","standalone_dish","standalone_antenna"}){
            BlockConstructorCore block=new BlockConstructorCore(FrameMachineDesign.get(design));
            IBlockState state=block.getDefaultState().withProperty(BlockConstructorCore.FORMED,true);
            NonRenderingChecks.MemoryWorld world=new NonRenderingChecks.MemoryWorld(false);
            NBTTagCompound old=core210();
            TileEntityConstructor tile=(TileEntityConstructor)restored(world,state,old);
            exact(state,old,tile,"2.10 "+design);
            need(!tile.processing()&&tile.synthesisInventory().getStackInSlot(0).isEmpty(),"Old Core gains no live work or inventory");
            tile.getUpdateTag();
            exact(state,old,tile,"2.10 "+design+" after public update projection");
            if(!tile.builder())continue;
            tile.synthesisInventory().setStackInSlot(2,new ItemStack(ModItems.BATTERY_MODULE));
            NBTTagCompound edited=tile.writeToNBT(new NBTTagCompound());
            need(edited.hasKey("Synthesis",10)&&edited.hasKey("SynthesisBatterySequences",10),"New physical battery ownership is persisted");
            need(!edited.hasKey("SynthesisActivation"),"Unedited activation default remains absent");
            exact(state,edited,restored(world,state,edited),"Edited "+design);
            tile=(TileEntityConstructor)restored(world,state,old);
            tile.configureSynthesisRedstone(2,ChannelList.EMPTY);
            need(tile.writeToNBT(new NBTTagCompound()).getInteger("SynthesisActivation")==2,"New redstone selection is persisted");
            tile.configureSynthesisRedstone(0,ChannelList.EMPTY);
            need(tile.writeToNBT(new NBTTagCompound()).hasKey("SynthesisActivation",3),"Explicitly reset activation stays saved");
            tile.readFromNBT(old);
            exact(state,old,tile,"Reused "+design+" clears newer runtime");
        }
    }

    private static void tables(){
        for(NavigationLayout.Kind kind:NavigationLayout.Kind.values()){
            BlockNavigationTable block=new BlockNavigationTable("navigation_table_"+kind.id,kind);
            IBlockState state=block.getDefaultState();
            NonRenderingChecks.MemoryWorld world=new NonRenderingChecks.MemoryWorld(false);
            NBTTagCompound old=table210();
            TileEntityNavigationTable tile=(TileEntityNavigationTable)restored(world,state,old);
            need(tile.navigationExplicit(),"Old selected artwork retains priority");
            exact(state,old,tile,"2.10 "+kind+" table");
            tile.navigationExplicitSelection();tile.getUpdateTag();
            exact(state,old,tile,"2.10 "+kind+" unchanged explicit selection");
            for(boolean selected:new boolean[]{false,true}){
                NBTTagCompound modern=old.copy();modern.setBoolean("NavigationExplicit",selected);
                exact(state,modern,restored(world,state,modern),"2.11 "+kind+" explicit="+selected);
            }
        }
    }

    public static void main(String[] args)throws Exception{
        java.lang.reflect.Field environment=net.minecraftforge.fml.relauncher.CoreModManager.class.getDeclaredField("deobfuscatedEnvironment");
        environment.setAccessible(true);environment.setBoolean(null,true);
        net.minecraft.init.Bootstrap.register();
        net.minecraftforge.fml.common.registry.GameRegistry.registerTileEntity(TileEntityConstructor.class,new ResourceLocation("vandorlabs:constructor"));
        net.minecraftforge.fml.common.registry.GameRegistry.registerTileEntity(TileEntityNavigationTable.class,new ResourceLocation("vandorlabs:navigation_table"));
        net.minecraftforge.fml.common.registry.ForgeRegistries.ITEMS.register(ModItems.BATTERY_MODULE);
        cores();tables();
        System.out.println("Release 2.11 saved defaults PASS checks="+checks+" literal 2.10 Core/table preimages, exact ownership, edited inventory, explicit selections, unknown-data rejection");
    }

    private Release211SavedDefaultsChecks(){}
}
