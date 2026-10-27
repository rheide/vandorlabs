package com.vandorlabs.vehicle;

import java.util.*;
import net.minecraft.block.properties.IProperty;
import net.minecraft.block.state.IBlockState;
import net.minecraft.nbt.*;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.*;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/** Reviewed building/decorative families from the installed optional mods; no network machines. */
public final class BuildingBlockCompat {
    private static final Set<String> BUILDING=new HashSet<>(Arrays.asList(
            "cofh.thermalfoundation.block.BlockGlass",
            "cofh.thermalfoundation.block.BlockGlassAlloy",
            "cofh.thermalfoundation.block.BlockOre",
            "cofh.thermalfoundation.block.BlockOreFluid",
            "cofh.thermalfoundation.block.BlockRockwool",
            "cofh.thermalfoundation.block.BlockStorage",
            "cofh.thermalfoundation.block.BlockStorageAlloy",
            "cofh.thermalfoundation.block.BlockStorageResource",
            "micdoodle8.mods.galacticraft.core.blocks.BlockBasic",
            "micdoodle8.mods.galacticraft.core.blocks.BlockBasicMoon",
            "micdoodle8.mods.galacticraft.core.blocks.BlockCheese",
            "micdoodle8.mods.galacticraft.core.blocks.BlockConcealedRedstone",
            "micdoodle8.mods.galacticraft.core.blocks.BlockConcealedRepeater",
            "micdoodle8.mods.galacticraft.core.blocks.BlockDoubleSlabGC",
            "micdoodle8.mods.galacticraft.core.blocks.BlockGlowstoneTorch",
            "micdoodle8.mods.galacticraft.core.blocks.BlockGrating",
            "micdoodle8.mods.galacticraft.core.blocks.BlockSlabGC",
            "micdoodle8.mods.galacticraft.core.blocks.BlockSpaceGlass",
            "micdoodle8.mods.galacticraft.core.blocks.BlockStairsGC",
            "micdoodle8.mods.galacticraft.core.blocks.BlockUnlitTorch",
            "micdoodle8.mods.galacticraft.core.blocks.BlockWallGC",
            "micdoodle8.mods.galacticraft.planets.asteroids.blocks.BlockBasicAsteroids",
            "micdoodle8.mods.galacticraft.planets.asteroids.blocks.BlockIceAsteroids",
            "micdoodle8.mods.galacticraft.planets.mars.blocks.BlockBasicMars",
            "micdoodle8.mods.galacticraft.planets.venus.blocks.BlockBasicVenus",
            "micdoodle8.mods.galacticraft.planets.venus.blocks.BlockScorchedRock",
            "zmaster587.advancedRocketry.block.BlockAdvancedBipropellantRocketMotor",
            "zmaster587.advancedRocketry.block.BlockAdvancedRocketMotor",
            "zmaster587.advancedRocketry.block.BlockBipropellantRocketMotor",
            "zmaster587.advancedRocketry.block.BlockCharcoalLog",
            "zmaster587.advancedRocketry.block.BlockCrystal",
            "zmaster587.advancedRocketry.block.BlockIntake",
            "zmaster587.advancedRocketry.block.BlockLens",
            "zmaster587.advancedRocketry.block.BlockLinkedHorizontalTexture",
            "zmaster587.advancedRocketry.block.BlockMiningDrill",
            "zmaster587.advancedRocketry.block.BlockNuclearCore",
            "zmaster587.advancedRocketry.block.BlockNuclearRocketMotor",
            "zmaster587.advancedRocketry.block.BlockRegolith",
            "zmaster587.advancedRocketry.block.BlockRocketMotor",
            "zmaster587.advancedRocketry.block.BlockSeat",
            "zmaster587.advancedRocketry.block.BlockThermiteTorch",
            "zmaster587.advancedRocketry.block.BlockTorchUnlit",
            "zmaster587.advancedRocketry.block.plant.BlockLightwoodLeaves",
            "zmaster587.advancedRocketry.block.plant.BlockLightwoodPlanks",
            "zmaster587.advancedRocketry.block.plant.BlockLightwoodWood",
            "zmaster587.libVulpes.block.BlockAlphaTexture",
            "zmaster587.libVulpes.block.BlockFullyRotatable",
            "zmaster587.libVulpes.block.BlockMetalBlock",
            "zmaster587.libVulpes.block.BlockMotor",
            "zmaster587.libVulpes.block.BlockOre",
            "zmaster587.libVulpes.block.multiblock.BlockMultiBlockComponentVisible",
            "zmaster587.libVulpes.block.multiblock.BlockMultiBlockComponentVisibleAlphaTexture"));
    private static final Set<String> FURNITURE=new HashSet<>(Arrays.asList(
            "com.mrcrayfish.furniture.blocks.BlockBarStool",
            "com.mrcrayfish.furniture.blocks.BlockBasin",
            "com.mrcrayfish.furniture.blocks.BlockBath",
            "com.mrcrayfish.furniture.blocks.BlockBedsideCabinet",
            "com.mrcrayfish.furniture.blocks.BlockBench",
            "com.mrcrayfish.furniture.blocks.BlockBin",
            "com.mrcrayfish.furniture.blocks.BlockBirdBath",
            "com.mrcrayfish.furniture.blocks.BlockBlender",
            "com.mrcrayfish.furniture.blocks.BlockBlinds",
            "com.mrcrayfish.furniture.blocks.BlockCabinet",
            "com.mrcrayfish.furniture.blocks.BlockCabinetKitchen",
            "com.mrcrayfish.furniture.blocks.BlockCandle",
            "com.mrcrayfish.furniture.blocks.BlockCastleNetting",
            "com.mrcrayfish.furniture.blocks.BlockCeilingFan",
            "com.mrcrayfish.furniture.blocks.BlockCeilingLight",
            "com.mrcrayfish.furniture.blocks.BlockChair",
            "com.mrcrayfish.furniture.blocks.BlockChimney",
            "com.mrcrayfish.furniture.blocks.BlockChoppingBoard",
            "com.mrcrayfish.furniture.blocks.BlockCoffeeTable",
            "com.mrcrayfish.furniture.blocks.BlockComputer",
            "com.mrcrayfish.furniture.blocks.BlockCookieJar",
            "com.mrcrayfish.furniture.blocks.BlockCouchJeb",
            "com.mrcrayfish.furniture.blocks.BlockCouchNormal",
            "com.mrcrayfish.furniture.blocks.BlockCounter",
            "com.mrcrayfish.furniture.blocks.BlockCounterDrawer",
            "com.mrcrayfish.furniture.blocks.BlockCounterSink",
            "com.mrcrayfish.furniture.blocks.BlockCrate",
            "com.mrcrayfish.furniture.blocks.BlockCup",
            "com.mrcrayfish.furniture.blocks.BlockCurtainsClosed",
            "com.mrcrayfish.furniture.blocks.BlockCurtainsOpen",
            "com.mrcrayfish.furniture.blocks.BlockDesk",
            "com.mrcrayfish.furniture.blocks.BlockDeskCabinet",
            "com.mrcrayfish.furniture.blocks.BlockDigitalClock",
            "com.mrcrayfish.furniture.blocks.BlockDishwasher",
            "com.mrcrayfish.furniture.blocks.BlockDivingboard",
            "com.mrcrayfish.furniture.blocks.BlockDoorBell",
            "com.mrcrayfish.furniture.blocks.BlockDoorMat",
            "com.mrcrayfish.furniture.blocks.BlockElectricFence",
            "com.mrcrayfish.furniture.blocks.BlockEsky",
            "com.mrcrayfish.furniture.blocks.BlockFairyLight",
            "com.mrcrayfish.furniture.blocks.BlockFireAlarm",
            "com.mrcrayfish.furniture.blocks.BlockFirePitOff",
            "com.mrcrayfish.furniture.blocks.BlockFirePitOn",
            "com.mrcrayfish.furniture.blocks.BlockFreezer",
            "com.mrcrayfish.furniture.blocks.BlockFridge",
            "com.mrcrayfish.furniture.blocks.BlockGrandChair",
            "com.mrcrayfish.furniture.blocks.BlockGrill",
            "com.mrcrayfish.furniture.blocks.BlockHedge",
            "com.mrcrayfish.furniture.blocks.BlockInflatableCastle",
            "com.mrcrayfish.furniture.blocks.BlockLamp",
            "com.mrcrayfish.furniture.blocks.BlockLampOn",
            "com.mrcrayfish.furniture.blocks.BlockLightSwitch",
            "com.mrcrayfish.furniture.blocks.BlockMailBox",
            "com.mrcrayfish.furniture.blocks.BlockMantelPiece",
            "com.mrcrayfish.furniture.blocks.BlockMicrowave",
            "com.mrcrayfish.furniture.blocks.BlockMirror",
            "com.mrcrayfish.furniture.blocks.BlockModernBed",
            "com.mrcrayfish.furniture.blocks.BlockModernCouch",
            "com.mrcrayfish.furniture.blocks.BlockModernLight",
            "com.mrcrayfish.furniture.blocks.BlockModernSlidingDoor",
            "com.mrcrayfish.furniture.blocks.BlockModernStair",
            "com.mrcrayfish.furniture.blocks.BlockModernTV",
            "com.mrcrayfish.furniture.blocks.BlockModernTable",
            "com.mrcrayfish.furniture.blocks.BlockModernWindow",
            "com.mrcrayfish.furniture.blocks.BlockOutdoorTable",
            "com.mrcrayfish.furniture.blocks.BlockOven",
            "com.mrcrayfish.furniture.blocks.BlockParkBench",
            "com.mrcrayfish.furniture.blocks.BlockPhotoFrame",
            "com.mrcrayfish.furniture.blocks.BlockPlate",
            "com.mrcrayfish.furniture.blocks.BlockPresent",
            "com.mrcrayfish.furniture.blocks.BlockPrinter",
            "com.mrcrayfish.furniture.blocks.BlockRangeHood",
            "com.mrcrayfish.furniture.blocks.BlockShower",
            "com.mrcrayfish.furniture.blocks.BlockShowerHeadOff",
            "com.mrcrayfish.furniture.blocks.BlockShowerHeadOn",
            "com.mrcrayfish.furniture.blocks.BlockStereo",
            "com.mrcrayfish.furniture.blocks.BlockStonePath",
            "com.mrcrayfish.furniture.blocks.BlockTV",
            "com.mrcrayfish.furniture.blocks.BlockTVStand",
            "com.mrcrayfish.furniture.blocks.BlockTable",
            "com.mrcrayfish.furniture.blocks.BlockTap",
            "com.mrcrayfish.furniture.blocks.BlockToaster",
            "com.mrcrayfish.furniture.blocks.BlockToilet",
            "com.mrcrayfish.furniture.blocks.BlockTrampoline",
            "com.mrcrayfish.furniture.blocks.BlockTree",
            "com.mrcrayfish.furniture.blocks.BlockUpgradedFence",
            "com.mrcrayfish.furniture.blocks.BlockUpgradedGate",
            "com.mrcrayfish.furniture.blocks.BlockWallCabinet",
            "com.mrcrayfish.furniture.blocks.BlockWashingMachine",
            "com.mrcrayfish.furniture.blocks.BlockWhiteFence",
            "com.mrcrayfish.furniture.blocks.BlockWhiteGate",
            "com.mrcrayfish.furniture.blocks.BlockWreath"));
    private static final Set<String> FURNITURE_TILES=new HashSet<>(Arrays.asList(
            "com.mrcrayfish.furniture.tileentity.TileEntityBath",
            "com.mrcrayfish.furniture.tileentity.TileEntityBedsideCabinet",
            "com.mrcrayfish.furniture.tileentity.TileEntityBin",
            "com.mrcrayfish.furniture.tileentity.TileEntityBlender",
            "com.mrcrayfish.furniture.tileentity.TileEntityCabinet",
            "com.mrcrayfish.furniture.tileentity.TileEntityCabinetKitchen",
            "com.mrcrayfish.furniture.tileentity.TileEntityCeilingFan",
            "com.mrcrayfish.furniture.tileentity.TileEntityChoppingBoard",
            "com.mrcrayfish.furniture.tileentity.TileEntityCoffeeTable",
            "com.mrcrayfish.furniture.tileentity.TileEntityColoured",
            "com.mrcrayfish.furniture.tileentity.TileEntityComputer",
            "com.mrcrayfish.furniture.tileentity.TileEntityCookieJar",
            "com.mrcrayfish.furniture.tileentity.TileEntityCrate",
            "com.mrcrayfish.furniture.tileentity.TileEntityCup",
            "com.mrcrayfish.furniture.tileentity.TileEntityDeskCabinet",
            "com.mrcrayfish.furniture.tileentity.TileEntityDigitalClock",
            "com.mrcrayfish.furniture.tileentity.TileEntityDishwasher",
            "com.mrcrayfish.furniture.tileentity.TileEntityDoorMat",
            "com.mrcrayfish.furniture.tileentity.TileEntityEsky",
            "com.mrcrayfish.furniture.tileentity.TileEntityFreezer",
            "com.mrcrayfish.furniture.tileentity.TileEntityFridge",
            "com.mrcrayfish.furniture.tileentity.TileEntityGrill",
            "com.mrcrayfish.furniture.tileentity.TileEntityKitchenCounter",
            "com.mrcrayfish.furniture.tileentity.TileEntityKitchenCounterDrawer",
            "com.mrcrayfish.furniture.tileentity.TileEntityLightSwitch",
            "com.mrcrayfish.furniture.tileentity.TileEntityMailBox",
            "com.mrcrayfish.furniture.tileentity.TileEntityMicrowave",
            "com.mrcrayfish.furniture.tileentity.TileEntityMirror",
            "com.mrcrayfish.furniture.tileentity.TileEntityModernSlidingDoor",
            "com.mrcrayfish.furniture.tileentity.TileEntityOven",
            "com.mrcrayfish.furniture.tileentity.TileEntityPhotoFrame",
            "com.mrcrayfish.furniture.tileentity.TileEntityPlate",
            "com.mrcrayfish.furniture.tileentity.TileEntityPresent",
            "com.mrcrayfish.furniture.tileentity.TileEntityPrinter",
            "com.mrcrayfish.furniture.tileentity.TileEntityShowerHead",
            "com.mrcrayfish.furniture.tileentity.TileEntityStereo",
            "com.mrcrayfish.furniture.tileentity.TileEntityTV",
            "com.mrcrayfish.furniture.tileentity.TileEntityTVStand",
            "com.mrcrayfish.furniture.tileentity.TileEntityToaster",
            "com.mrcrayfish.furniture.tileentity.TileEntityTree",
            "com.mrcrayfish.furniture.tileentity.TileEntityWallCabinet",
            "com.mrcrayfish.furniture.tileentity.TileEntityWashingMachine"));

    public static boolean supported(VehicleStructure.Cell cell,TileEntity tile){
        String block=cell.state.getBlock().getClass().getName();
        if(tile==null){
            if(cell.hasTileData()||cell.state.getBlock().hasTileEntity(cell.state))return false;
            if(block.equals("micdoodle8.mods.galacticraft.core.blocks.BlockGrating")&&!cell.state.getBlock().getRegistryName().getResourcePath().equals("grating"))return false;
            return BUILDING.contains(block)||FURNITURE.contains(block);
        }
        String type=tile.getClass().getName();
        if(type.equals("micdoodle8.mods.galacticraft.core.tile.TileEntityScreen"))return true;
        if(!FURNITURE.contains(block)||!FURNITURE_TILES.contains(type))return false;
        // Configured switches contain absolute links to external live blocks.
        return !type.endsWith("TileEntityLightSwitch")||cell.tileData().getTagList("lights",4).tagCount()==0;
    }
    @SuppressWarnings({"rawtypes","unchecked"})
    public static IBlockState rotate(IBlockState original,Rotation rotation){
        String type=original.getBlock().getClass().getName();
        if(!BUILDING.contains(type)&&!FURNITURE.contains(type)&&!type.equals("micdoodle8.mods.galacticraft.core.blocks.BlockScreen"))return original.withRotation(rotation);
        IBlockState result=original.withRotation(rotation);
        for(IProperty property:original.getPropertyKeys()){
            Object before=original.getValue(property),after=before;
            if(before instanceof EnumFacing)after=rotation.rotate((EnumFacing)before);
            else if(before instanceof EnumFacing.Axis&&(rotation.ordinal()&1)!=0){EnumFacing.Axis axis=(EnumFacing.Axis)before;after=axis==EnumFacing.Axis.X?EnumFacing.Axis.Z:axis==EnumFacing.Axis.Z?EnumFacing.Axis.X:axis;}
            else if((rotation.ordinal()&1)!=0&&property.getName().equals("axis")){String next=before.toString().equalsIgnoreCase("x")?"z":before.toString().equalsIgnoreCase("z")?"x":"";for(Object value:property.getAllowedValues())if(value.toString().equalsIgnoreCase(next))after=value;}
            if(after!=before&&property.getAllowedValues().contains(after))result=result.withProperty(property,(Comparable)after);
        }
        // These are native neighbour-derived decorations, rather than orientation.
        // Local furniture types/left/right screen connections remain relative to facing.
        return result;
    }
    public static void rotateTile(IBlockState state,NBTTagCompound data,Rotation rotation){
        if(state.getBlock().getClass().getName().equals("com.mrcrayfish.furniture.blocks.BlockPlate")&&data.hasKey("Rotation",3))
            data.setInteger("Rotation",(data.getInteger("Rotation")+rotation.ordinal())&3);
    }
    /** The native doormat reader maps an absent message to the empty string. */
    public static NBTTagCompound canonical(IBlockState state,NBTTagCompound data){
        if(state.getBlock().getClass().getName().equals("com.mrcrayfish.furniture.blocks.BlockDoorMat")&&!data.hasKey("message"))data.setString("message","");
        return data;
    }
    public static boolean furniture(TileEntity tile){return tile!=null&&FURNITURE_TILES.contains(tile.getClass().getName());}
    /** TV dimensions are supplied by the native block constructor, not written to NBT. */
    public static TileEntity createTile(VehicleStructure.Cell cell,World world,NBTTagCompound data){
        String type=cell.state.getBlock().getClass().getName();
        if(!type.equals("com.mrcrayfish.furniture.blocks.BlockTV")&&!type.equals("com.mrcrayfish.furniture.blocks.BlockModernTV"))return TileEntity.create(world,data);
        TileEntity tile=cell.state.getBlock().createTileEntity(world,cell.state);
        if(tile!=null){tile.setWorld(world);tile.readFromNBT(data);}
        return tile;
    }
    private static Object property(IBlockState state,String key){for(IProperty<?> p:state.getPropertyKeys())if(p.getName().equals(key))return state.getValue(p);return null;}
    private static EnumFacing facing(IBlockState state){Object face=property(state,"facing");return face instanceof EnumFacing?(EnumFacing)face:EnumFacing.NORTH;}
    private static void require(boolean ok,String message){if(!ok)throw new IllegalArgumentException(message);}
    public static Set<BlockPos> required(World world,BlockPos at){
        IBlockState state=world.getBlockState(at);String type=state.getBlock().getClass().getName(),name=state.getBlock().getRegistryName().getResourcePath();
        Set<BlockPos> result=new LinkedHashSet<>();BlockPos other=null;String partner=null;
        if(type.equals("com.mrcrayfish.furniture.blocks.BlockBath")||type.equals("com.mrcrayfish.furniture.blocks.BlockModernBed")){
            boolean bottom=name.endsWith("bottom");other=at.offset(bottom?facing(state):facing(state).getOpposite());partner=name.replace(bottom?"bottom":"top",bottom?"top":"bottom");
        }else if(type.equals("com.mrcrayfish.furniture.blocks.BlockFreezer")){other=at.up();partner="fridge";}
        else if(type.equals("com.mrcrayfish.furniture.blocks.BlockFridge")){other=at.down();partner="freezer";}
        else if(type.equals("com.mrcrayfish.furniture.blocks.BlockModernSlidingDoor")){
            boolean top=Boolean.TRUE.equals(property(state,"top"));other=top?at.down():at.up();partner=name;
            require(!Objects.equals(property(state,"top"),property(world.getBlockState(other),"top")),"Include both furniture door sections");
        }
        if(other!=null){
            require(world.isBlockLoaded(other)&&world.getBlockState(other).getBlock().getRegistryName().toString().equals("cfm:"+partner)&&facing(world.getBlockState(other))==facing(state),"Include the complete furniture assembly");
            result.add(at);result.add(other);
        }
        TileEntity tile=world.getTileEntity(at);
        if(tile!=null&&tile.getClass().getName().equals("micdoodle8.mods.galacticraft.core.tile.TileEntityScreen")){
            NBTTagCompound data=tile.writeToNBT(new NBTTagCompound());int left=data.getInteger("connectionsLeft"),right=data.getInteger("connectionsRight"),up=data.getInteger("connectionsUp"),down=data.getInteger("connectionsDown");
            require(left>=0&&right>=0&&up>=0&&down>=0&&left+right<=8&&up+down<=8,"Invalid Galacticraft screen dimensions");
            EnumFacing face=facing(state);require(face.getAxis().isHorizontal()||left+right+up+down==0,"Only wall-mounted Galacticraft screens can form a joined display");
            EnumFacing side=face.getAxis().isHorizontal()?face.rotateYCCW():EnumFacing.WEST;
            for(int x=-left;x<=right;x++)for(int y=-down;y<=up;y++){
                BlockPos p=at.offset(side,-x).up(y);require(world.isBlockLoaded(p)&&world.getBlockState(p).getBlock()==state.getBlock()&&facing(world.getBlockState(p))==face,"Include the complete Galacticraft screen rectangle");
                TileEntity member=world.getTileEntity(p);require(member!=null&&member.getClass().getName().equals(tile.getClass().getName()),"Missing Galacticraft screen data");
                NBTTagCompound saved=member.writeToNBT(new NBTTagCompound());
                require(saved.getInteger("type")==data.getInteger("type")&&saved.getInteger("connectionsLeft")==left+x&&saved.getInteger("connectionsRight")==right-x&&saved.getInteger("connectionsUp")==up-y&&saved.getInteger("connectionsDown")==down+y,"Galacticraft screen group is changing; retry after it settles");
                result.add(p);
            }
        }
        return result;
    }
    private BuildingBlockCompat(){}
}
