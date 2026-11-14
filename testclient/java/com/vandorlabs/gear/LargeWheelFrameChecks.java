package com.vandorlabs.gear;

import com.vandorlabs.vehicle.VehicleStructure;
import java.util.*;
import net.minecraft.block.state.IBlockState;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.*;
import net.minecraft.world.*;
import net.minecraft.world.biome.Biome;

/** Metadata, physical footprint and opaque-data covariance for all proper grid rotations. */
public final class LargeWheelFrameChecks {
    private static int checks,profiles;
    private static void need(boolean value,String why){checks++;if(!value)throw new AssertionError("Large Wheel frame: "+why);}
    private static BlockPos rotate(BlockPos p,int[] b){return new BlockPos(b[0]*p.getX()+b[1]*p.getY()+b[2]*p.getZ(),b[3]*p.getX()+b[4]*p.getY()+b[5]*p.getZ(),b[6]*p.getX()+b[7]*p.getY()+b[8]*p.getZ());}
    private static Vec3d vector(EnumFacing f){return new Vec3d(f.getFrontOffsetX(),f.getFrontOffsetY(),f.getFrontOffsetZ());}
    private static final class Access implements IBlockAccess {
        final Map<BlockPos,TileEntity> tiles=new HashMap<>();final Map<BlockPos,IBlockState> states=new HashMap<>();
        public TileEntity getTileEntity(BlockPos p){return tiles.get(p);}public int getCombinedLight(BlockPos p,int minimum){return 0;}
        public IBlockState getBlockState(BlockPos p){return states.getOrDefault(p,net.minecraft.init.Blocks.AIR.getDefaultState());}
        public boolean isAirBlock(BlockPos p){return !states.containsKey(p);}public Biome getBiome(BlockPos p){return net.minecraft.init.Biomes.PLAINS;}
        public int getStrongPower(BlockPos p,EnumFacing f){return 0;}public WorldType getWorldType(){return WorldType.DEFAULT;}
        public boolean isSideSolid(BlockPos p,EnumFacing f,boolean fallback){return false;}
    }
    public static void main(String[] args){
        net.minecraft.init.Bootstrap.register();net.minecraftforge.fml.common.registry.GameRegistry.registerTileEntity(GearTileEntity.class,new ResourceLocation("vandorlabs:modular_gear"));
        LargeGearBlock block=new LargeGearBlock("frame_test_large_wheel",GearBlock.Kind.WHEEL);net.minecraftforge.fml.common.registry.ForgeRegistries.BLOCKS.register(block);
        GearTileEntity seed=new GearTileEntity();seed.setPos(BlockPos.ORIGIN);NBTTagCompound base=seed.writeToNBT(new NBTTagCompound());
        NBTTagCompound caps=new NBTTagCompound();caps.setByteArray("Opaque",new byte[]{-3,4,99});base.setTag("ForgeCaps",caps);
        for(EnumFacing frame:EnumFacing.values())for(EnumFacing oldMount:EnumFacing.values())for(EnumFacing facing:EnumFacing.HORIZONTALS){
            Access access=new Access();BlockPos root=new BlockPos(-13,97,31);
            for(int part=0;part<4;part++){
                BlockPos position=root.add(ArmFootComponentFrame.offset(frame,facing,part&1,part>>1));
                NBTTagCompound raw=base.copy();raw.setString("ArmMountDirection",oldMount.name());ArmFootComponentFrame.write(raw,frame);
                GearTileEntity tile=new GearTileEntity();tile.setPos(position);tile.readFromNBT(raw);access.tiles.put(position,tile);IBlockState state=block.getDefaultState().withProperty(GearBlock.FACING,facing).withProperty(LargeGearBlock.PART,part);access.states.put(position,state);
                need(block.componentRoot(access,position,state).equals(root),"all helpers resolve one physical root independently of legacy arm mount");
                need(tile.componentFrame()==frame,"saved component frame loaded");
                NBTTagCompound saved=tile.writeToNBT(new NBTTagCompound());need(saved.hasKey(ArmFootComponentFrame.KEY)==(frame!=EnumFacing.DOWN),"default footprint does not add an upgrade field");
                for(int turns=1;turns<4;turns++){
                    NBTTagCompound owned=raw.copy();owned.setLong("GearOwnerOffset",new BlockPos(1,2,-3).toLong());owned.setInteger("GearSourceFootFacing",facing.getHorizontalIndex());if(frame!=EnumFacing.DOWN)owned.setString(ArmFootComponentFrame.SOURCE_KEY,frame.name());
                    VehicleStructure.Cell ownedCell=new VehicleStructure.Cell(BlockPos.ORIGIN,state,owned);IBlockState nextState=GearData.rotateComponentState(ownedCell,turns);NBTTagCompound rotated=owned.copy();GearData.rotate(rotated,turns);EnumFacing nextFrame=rotated.hasKey(ArmFootComponentFrame.KEY,8)?EnumFacing.valueOf(rotated.getString(ArmFootComponentFrame.KEY)):EnumFacing.DOWN;
                    if(nextState==null){EnumFacing next=facing;for(int i=0;i<turns;i++)next=next.rotateY();nextState=state.withProperty(GearBlock.FACING,next);}
                    for(int x=0;x<2;x++)for(int z=0;z<2;z++){BlockPos before=ArmFootComponentFrame.offset(frame,facing,x,z),after=ArmFootComponentFrame.offset(nextFrame,nextState.getValue(GearBlock.FACING),x,z);for(int i=0;i<turns;i++)before=new BlockPos(-before.getZ(),before.getY(),before.getX());need(before.equals(after),"bound vehicle rotation preserves all physical helper offsets");}
                    need(rotated.getInteger("GearSourceFootFacing")==nextState.getValue(GearBlock.FACING).getHorizontalIndex(),"source-facing cache uses its physical component frame");GearData.rotate(rotated,4-turns);need(rotated.equals(owned),"bound rotation exact original NBT inverse");
                }
                VehicleStructure.Cell source=new VehicleStructure.Cell(BlockPos.ORIGIN,state,raw);need(ArmLooseGearData.admitted(source),"unowned typed component admitted");
                for(EnumFacing x:EnumFacing.values())for(EnumFacing y:EnumFacing.values()){
                    Vec3d vx=vector(x),vy=vector(y);if(vx.dotProduct(vy)!=0)continue;
                    int[] basis=ArmGridMapping.of(BlockPos.ORIGIN,Vec3d.ZERO,new Vec3d[]{vx,vy,vx.crossProduct(vy)}).basis();
                    IBlockState mapped=ArmGridTiles.state(source,basis);NBTTagCompound data=ArmGridTiles.data(source,basis);VehicleStructure.Cell target=new VehicleStructure.Cell(BlockPos.ORIGIN,mapped,data);
                    need(data.getCompoundTag("ForgeCaps").equals(caps),"opaque capability retained");
                    need(ArmGridTiles.state(target,ArmLooseGearData.inverse(basis))==state&&ArmGridTiles.data(target,ArmLooseGearData.inverse(basis)).equals(raw),"exact metadata and original data inverse");
                    EnumFacing nextFrame=ArmFootComponentFrame.frame(target),nextFacing=mapped.getValue(GearBlock.FACING);
                    for(int dx=0;dx<2;dx++)for(int dz=0;dz<2;dz++)need(rotate(ArmFootComponentFrame.offset(frame,facing,dx,dz),basis).equals(ArmFootComponentFrame.offset(nextFrame,nextFacing,dx,dz)),"physical companion centers rotate with the rendered frame");
                    profiles++;
                }
            }
        }
        NBTTagCompound malformed=base.copy();malformed.setString(ArmFootComponentFrame.KEY,"UNKNOWN");GearTileEntity failed=new GearTileEntity();failed.readFromNBT(malformed);need(failed.writeToNBT(new NBTTagCompound()).equals(malformed),"unknown frame remains inert and retains original snapshot");
        try(ArmFootComponentFrame.Placement first=ArmFootComponentFrame.placing(EnumFacing.EAST,EnumFacing.WEST)){need(ArmFootComponentFrame.placement()==EnumFacing.EAST,"posed placement scope");need(ArmFootComponentFrame.placementFacing()==EnumFacing.WEST,"scoped local facing");try(ArmFootComponentFrame.Placement nested=ArmFootComponentFrame.placing(EnumFacing.UP)){need(ArmFootComponentFrame.placement()==EnumFacing.UP,"nested placement");}need(ArmFootComponentFrame.placement()==EnumFacing.EAST,"placement restored");need(ArmFootComponentFrame.placementFacing()==EnumFacing.WEST,"nested facing restored");}need(!ArmFootComponentFrame.posedPlacement(),"placement cleared");
        need(profiles==13824,"all rotations frames legacy mounts facings and helper indices");System.out.println("Large Wheel component frame PASS profiles="+profiles+" checks="+checks+" exact helpers, opaque data, old absent defaults, unknown-frame retention");
    }
    private LargeWheelFrameChecks(){}
}
