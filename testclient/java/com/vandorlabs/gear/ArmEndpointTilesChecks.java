package com.vandorlabs.gear;

import com.vandorlabs.coupler.*;
import com.vandorlabs.vehicle.VehicleStructure;
import java.util.*;
import net.minecraft.block.BlockSlab;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.*;
import net.minecraft.util.math.*;

/** Independent cardinal facing/half oracle against the shared typed forward policy. */
public final class ArmEndpointTilesChecks {
    private static int checks,full,slab,refused;
    private static void need(boolean value,String why){checks++;if(!value)throw new AssertionError("Arm endpoint inverse: "+why);}
    private static int det(int[] m){return m[0]*(m[4]*m[8]-m[5]*m[7])-m[1]*(m[3]*m[8]-m[5]*m[6])+m[2]*(m[3]*m[7]-m[4]*m[6]);}
    private static EnumFacing inverse(EnumFacing face,int[] m){int x=face.getFrontOffsetX(),y=face.getFrontOffsetY(),z=face.getFrontOffsetZ();return EnumFacing.getFacingFromVector(m[0]*x+m[3]*y+m[6]*z,m[1]*x+m[4]*y+m[7]*z,m[2]*x+m[5]*y+m[8]*z);}
    public static void main(String[] args)throws Exception{
        net.minecraft.init.Bootstrap.register();net.minecraftforge.fml.common.registry.GameRegistry.registerTileEntity(TileCoupler.class,new ResourceLocation("vandorlabs:square_coupler"));BlockCoupler block=new BlockCoupler();BlockCouplerSlab half=new BlockCouplerSlab();net.minecraftforge.fml.common.registry.ForgeRegistries.BLOCKS.register(block);net.minecraftforge.fml.common.registry.ForgeRegistries.BLOCKS.register(half);
        NBTTagCompound raw=new NBTTagCompound();raw.setString("id","vandorlabs:square_coupler");raw.setUniqueId("CouplerControlIdentity",new UUID(123,456));raw.setString("FutureCouplerChoice","unchanged");NBTTagCompound caps=new NBTTagCompound();caps.setByteArray("Opaque",new byte[]{-3,7,19});raw.setTag("ForgeCaps",caps);int matrices=0;
        for(int a=0;a<3;a++)for(int b=0;b<3;b++)for(int c=0;c<3;c++)if(a!=b&&a!=c&&b!=c)for(int signs=0;signs<8;signs++){
            int[] m=new int[9];m[a*3]=(signs&1)==0?1:-1;m[b*3+1]=(signs&2)==0?1:-1;m[c*3+2]=(signs&4)==0?1:-1;if(det(m)!=1)continue;matrices++;Vec3d[] axes=ArmGridTiles.axes(m);ArmEndpointTiles policy=new ArmEndpointTiles(axes);ArmGridMapping forward=ArmGridMapping.of(BlockPos.ORIGIN,Vec3d.ZERO,axes);
            for(boolean locked:new boolean[]{false,true})for(EnumFacing facing:EnumFacing.values()){
                IBlockState nativeState=block.getDefaultState().withProperty(BlockCoupler.FACING,facing).withProperty(BlockCoupler.LOCKED,locked);VehicleStructure.Cell nativeCell=new VehicleStructure.Cell(BlockPos.ORIGIN,nativeState,raw);IBlockState rest=policy.constructionState(nativeCell);need(rest!=null&&rest.getValue(BlockCoupler.FACING)==inverse(facing,m)&&rest.getValue(BlockCoupler.LOCKED)==locked,"Full facing inverse follows transpose and retains lock");VehicleStructure.Cell local=new VehicleStructure.Cell(BlockPos.ORIGIN,rest,raw);need(forward.state(local)==nativeState,"Full inverse agrees with existing ArmGridMapping forward policy");need(nativeCell.tileData().equals(raw),"Unknown data/identity/caps untouched");full++;
            }
            for(boolean locked:new boolean[]{false,true})for(EnumFacing facing:EnumFacing.HORIZONTALS)for(BlockSlab.EnumBlockHalf height:BlockSlab.EnumBlockHalf.values()){
                IBlockState nativeState=half.getDefaultState().withProperty(BlockCoupler.FACING,facing).withProperty(BlockCoupler.LOCKED,locked).withProperty(BlockCouplerSlab.HALF,height);VehicleStructure.Cell nativeCell=new VehicleStructure.Cell(BlockPos.ORIGIN,nativeState,raw);IBlockState rest=policy.constructionState(nativeCell);boolean valid=Math.abs(m[4])==1&&inverse(facing,m).getAxis().isHorizontal();need((rest!=null)==valid,"Slab preserves authored horizontal half domain");if(valid){BlockSlab.EnumBlockHalf expected=m[4]<0?(height==BlockSlab.EnumBlockHalf.TOP?BlockSlab.EnumBlockHalf.BOTTOM:BlockSlab.EnumBlockHalf.TOP):height;need(rest.getValue(BlockCouplerSlab.HALF)==expected&&rest.getValue(BlockCoupler.FACING)==inverse(facing,m)&&rest.getValue(BlockCoupler.LOCKED)==locked,"Slab facing/half inverse retains exact fields");need(forward.state(new VehicleStructure.Cell(BlockPos.ORIGIN,rest,raw))==nativeState,"Slab inverse agrees with existing forward policy");slab++;}else refused++;
            }
            NBTTagCompound chest=new NBTTagCompound();chest.setString("id","minecraft:chest");boolean identity=!forward.rotates();need((policy.constructionState(new VehicleStructure.Cell(BlockPos.ORIGIN,Blocks.CHEST.getDefaultState(),chest))!=null)==identity,"Unknown tile rotations remain unavailable; identity translation stays supported");need((policy.constructionState(new VehicleStructure.Cell(BlockPos.ORIGIN,Blocks.STONE_SLAB.getDefaultState(),null))!=null)==identity,"Unproven nonfull shapes do not gain rotation support");NBTTagCompound foreign=raw.copy();foreign.setString("id","foreign:coupler");need((policy.constructionState(new VehicleStructure.Cell(BlockPos.ORIGIN,block.getDefaultState(),foreign))!=null)==identity,"Foreign typed identity cannot gain rotation support");
        }
        need(matrices==24&&full==288&&slab==128&&refused==256,"All proper cardinal bases and explicit slab refusal domain");
        System.out.println("Arm endpoint inverse PASS matrices="+matrices+" full="+full+" slab="+slab+" refused="+refused+" checks="+checks+" independent transpose/half oracle/shared forward policy/opaque metadata/unsupported tiles");
    }
}
