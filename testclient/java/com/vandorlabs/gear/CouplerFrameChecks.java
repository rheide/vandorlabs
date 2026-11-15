package com.vandorlabs.gear;

import com.vandorlabs.coupler.*;
import com.vandorlabs.geometry.CardinalFrame;
import com.vandorlabs.vehicle.*;
import java.util.*;
import net.minecraft.block.state.IBlockState;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.*;

/** Narrow full-frame covariance, old receipt compatibility and strict own-field checks. */
final class CouplerFrameChecks {
    private static int checks;
    private static void need(boolean ok,String why){checks++;if(!ok)throw new AssertionError("Coupler frame: "+why);}
    private static void refused(Runnable action,String why){boolean refused=false;try{action.run();}catch(IllegalArgumentException expected){refused=true;}need(refused,why);}
    private static int[] inverse(int[] basis){int[] out=new int[9];for(int r=0;r<3;r++)for(int c=0;c<3;c++)out[r*3+c]=basis[c*3+r];return out;}
    private static Vec3d map(int[] m,Vec3d p){return new Vec3d(m[0]*p.x+m[1]*p.y+m[2]*p.z,m[3]*p.x+m[4]*p.y+m[5]*p.z,m[6]*p.x+m[7]*p.y+m[8]*p.z);}
    private static VehicleStructure one(IBlockState state,NBTTagCompound data){return VehicleStructure.display(Collections.singletonList(new VehicleStructure.Cell(BlockPos.ORIGIN,state,data)));}
    static void run(BlockCoupler block){
        NBTTagCompound raw=new NBTTagCompound();raw.setString("id","vandorlabs:square_coupler");raw.setUniqueId("CouplerControlIdentity",new UUID(88,99));raw.setInteger("x",0);raw.setInteger("y",0);raw.setInteger("z",0);
        NBTTagCompound caps=new NBTTagCompound();caps.setInteger(CouplerFrameData.KEY,999);caps.setByteArray("OriginalOpaque",new byte[]{-9,31,7});raw.setTag("ForgeCaps",caps);
        List<int[]> rotations=new ArrayList<>();
        for(EnumFacing facing:EnumFacing.values())for(EnumFacing up:EnumFacing.values())if(facing.getAxis()!=up.getAxis()){
            CardinalFrame frame=CardinalFrame.of(facing.getIndex(),up.getIndex());int[] m=new int[9];for(int r=0;r<3;r++)for(int c=0;c<3;c++)m[r*3+c]=frame.component(r,c);rotations.add(m);
        }
        int frames=0;
        for(EnumFacing facing:EnumFacing.values())for(EnumFacing up:EnumFacing.values())if(facing.getAxis()!=up.getAxis())for(boolean locked:new boolean[]{false,true}){
            NBTTagCompound original=raw.copy();if(up!=CouplerShape.defaultUp(facing))original.setInteger(CouplerFrameData.KEY,up.getIndex());IBlockState state=block.getDefaultState().withProperty(BlockCoupler.FACING,facing).withProperty(BlockCoupler.LOCKED,locked);VehicleStructure.Cell cell=new VehicleStructure.Cell(BlockPos.ORIGIN,state,original);
            for(int[] m:rotations){IBlockState next=ArmGridTiles.state(cell,m);NBTTagCompound mapped=ArmGridTiles.data(cell,m);VehicleStructure.Cell nativeCell=new VehicleStructure.Cell(BlockPos.ORIGIN,next,mapped);need(ArmGridTiles.state(nativeCell,inverse(m))==state&&ArmGridTiles.data(nativeCell,inverse(m)).equals(original),"all24 frames exact inverse/opaque capability bytes");
                AxisAlignedBB[] a=CouplerShape.getBoxes(facing,up,locked),b=CouplerShape.getBoxes(next.getValue(BlockCoupler.FACING),CouplerFrameData.up(next,mapped),locked);
                for(int i=0;i<a.length;i++){Vec3d lo=map(m,new Vec3d(a[i].minX-.5,a[i].minY-.5,a[i].minZ-.5)),hi=map(m,new Vec3d(a[i].maxX-.5,a[i].maxY-.5,a[i].maxZ-.5));AxisAlignedBB expected=new AxisAlignedBB(Math.min(lo.x,hi.x)+.5,Math.min(lo.y,hi.y)+.5,Math.min(lo.z,hi.z)+.5,Math.max(lo.x,hi.x)+.5,Math.max(lo.y,hi.y)+.5,Math.max(lo.z,hi.z)+.5);need(expected.equals(b[i]),"nine emitted collision cuboids transform rigidly");}
                need(mapped.getCompoundTag("ForgeCaps").equals(caps)&&cell.tileData().equals(original),"mapping never interprets nested opaque frame-looking fields");
            }frames++;
        }
        int[] m={1,0,0,0,0,-1,0,1,0};IBlockState state=block.getDefaultState().withProperty(BlockCoupler.FACING,EnumFacing.NORTH),placed=ArmGridTiles.state(new VehicleStructure.Cell(BlockPos.ORIGIN,state,raw),m);
        NBTTagCompound old=header(one(state,raw),one(placed,raw),m,false);need(ArmPayload.read(old).write().equals(old),"old GridTileBasis/PlacedBlocks raw snapshot roundtrip");
        NBTTagCompound framed=header(one(state,raw),one(placed,ArmGridTiles.data(new VehicleStructure.Cell(BlockPos.ORIGIN,state,raw),m)),m,true);need(ArmPayload.read(framed).write().equals(framed),"version1 exact raw snapshot roundtrip");
        for(NBTTagCompound tag:new NBTTagCompound[]{old,framed}){
            NBTTagCompound original=tag.copy(),controls=raw.copy();controls.setBoolean("CouplerInverted",true);ArmPayload payload=ArmPayload.read(tag);ArmPayload release=payload.releaseMaterializedCoupler(BlockPos.ORIGIN,true,controls);ArmPayload.read(release.write());need(tag.equals(original),"native release leaves original receipt untouched");
            NBTTagCompound captured=tag.copy();captured.setBoolean("Materialized",false);ArmPayload moving=ArmPayload.read(captured);ArmPayload edited=moving.withCouplerData(BlockPos.ORIGIN,controls);ArmPayload.read(edited.write());ArmPayload.read(moving.releaseCoupler(BlockPos.ORIGIN,true,controls).write());ArmPayload.read(moving.withCouplerAssociations(Collections.singletonMap(BlockPos.ORIGIN,CouplerAssociationData.chosenPatch(raw,new UUID(21,22),new UUID(23,24),CouplerAssociation.Boundary.NEW_ASSEMBLY,true,false))).write());need(captured.getCompoundTag("Blocks").equals(tag.getCompoundTag("Blocks")),"control/latch/association merges preserve both occurrence frames and opaque originals");
            NBTTagCompound changed=controls.copy();changed.getCompoundTag("ForgeCaps").setInteger("unexpected",1);refused(()->moving.withCouplerData(BlockPos.ORIGIN,changed),"control merge refuses opaque capability edits");
            TileCoupler tile=new TileCoupler();tile.readFromNBT(ArmGridTiles.data(new VehicleStructure.Cell(BlockPos.ORIGIN,state,raw),m));need(!tile.currentData(raw).hasKey(CouplerFrameData.KEY),"native control merge does not overwrite canonical geometry");
        }
        NBTTagCompound unstamped=framed.copy();unstamped.removeTag("CouplerFrameMapping");refused(()->ArmPayload.read(unstamped),"new frame cannot masquerade as old mapping");
        NBTTagCompound dropped=framed.copy();dropped.setTag("PlacedBlocks",one(placed,raw).write());refused(()->ArmPayload.read(dropped),"version1 cannot drop required frame");
        for(int malformed:new int[]{-1,6,EnumFacing.NORTH.getIndex(),EnumFacing.UP.getIndex()}){NBTTagCompound bad=raw.copy();bad.setInteger(CouplerFrameData.KEY,malformed);TileCoupler tile=new TileCoupler();tile.readFromNBT(bad);need(!tile.acceptFrame(state)&&tile.quarantined()&&tile.writeToNBT(new NBTTagCompound()).equals(bad),"malformed/parallel/redundant frame retained inert exactly");}
        need(frames==48,"24 proper frames both latch positions");System.out.println("Coupler frame PASS frames="+frames+" checks="+checks+" rigid9box/fullinverse/old+newreceipt/controls+lock/opaqueCaps/malformed");
    }
    private static NBTTagCompound header(VehicleStructure source,VehicleStructure placed,int[] basis,boolean framed){NBTTagCompound tag=new NBTTagCompound();tag.setTag("Blocks",source.write());tag.setTag("PlacedBlocks",placed.write());tag.setLong("Offset",0);tag.setLong("PlacedOffset",0);tag.setBoolean("Materialized",true);tag.setBoolean("GridAligned",true);tag.setInteger("GridTileMapping",1);tag.setIntArray("GridTileBasis",basis);if(framed)tag.setInteger("CouplerFrameMapping",1);return tag;}
}
