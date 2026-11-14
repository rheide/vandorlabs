package com.vandorlabs.gear;

import net.minecraft.nbt.*;
import net.minecraft.util.*;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/** Relative source links and canonical mechanism coordinates rotate in their respective bases. */
public final class GearData {
    private GearData(){}
    private static BlockPos vector(BlockPos p,int turns){switch(Math.floorMod(turns,4)){case 1:return new BlockPos(-p.getZ(),p.getY(),p.getX());case 2:return new BlockPos(-p.getX(),p.getY(),-p.getZ());case 3:return new BlockPos(p.getZ(),p.getY(),-p.getX());default:return p;}}
    /** Unowned oriented endpoint feet use their own six-face frame rather than world-horizontal metadata. */
    public static com.vandorlabs.vehicle.VehicleStructure.Cell rotateLooseFoot(com.vandorlabs.vehicle.VehicleStructure.Cell cell,int turns){
        if(!ArmLooseGearData.admitted(cell))return null;int[] basis;switch(Math.floorMod(turns,4)){case 1:basis=new int[]{0,0,-1,0,1,0,1,0,0};break;case 2:basis=new int[]{-1,0,0,0,1,0,0,0,-1};break;case 3:basis=new int[]{0,0,1,0,1,0,-1,0,0};break;default:basis=new int[]{1,0,0,0,1,0,0,0,1};}
        return new com.vandorlabs.vehicle.VehicleStructure.Cell(cell.pos,ArmGridTiles.state(cell,basis),ArmGridTiles.data(cell,basis));
    }
    /** Bound Large Wheel parts rotate their footprint metadata in its saved plane. */
    public static net.minecraft.block.state.IBlockState rotateComponentState(com.vandorlabs.vehicle.VehicleStructure.Cell cell,int turns){
        if(!ArmLooseGearData.foot(cell.state)||!cell.hasTileKey(ArmFootComponentFrame.KEY))return null;
        if(!ArmFootComponentFrame.valid(cell.tileData()))throw new IllegalArgumentException("Unknown landing component frame");
        if(!ArmLooseGearData.largeWheel(cell.state)&&!cell.tileString(ArmFootComponentFrame.KEY).equals(cell.tileString("ArmMountDirection")))throw new IllegalArgumentException("Landing foot metadata frame differs from its mounting frame");
        EnumFacing frame=ArmFootComponentFrame.frame(cell),next=frame;for(int i=0;i<Math.floorMod(turns,4);i++)if(next.getAxis().isHorizontal())next=next.rotateY();
        BlockPos x=vector(new BlockPos(1,0,0),turns),y=vector(new BlockPos(0,1,0),turns),z=vector(new BlockPos(0,0,1),turns);int[] basis={x.getX(),y.getX(),z.getX(),x.getY(),y.getY(),z.getY(),x.getZ(),y.getZ(),z.getZ()};
        return cell.state.withProperty(GearBlock.FACING,ArmLooseGearData.localFacing(frame,next,cell.state.getValue(GearBlock.FACING),basis));
    }
    public static void rotate(NBTTagCompound tag,int turns){rotate(tag,turns,null);}
    public static void rotate(NBTTagCompound tag,int turns,World parent){
        EnumFacing sourceFrame=tag.hasKey(ArmFootComponentFrame.SOURCE_KEY,8)?EnumFacing.valueOf(tag.getString(ArmFootComponentFrame.SOURCE_KEY)):EnumFacing.DOWN;
        EnumFacing nextSourceFrame=sourceFrame;for(int i=0;i<Math.floorMod(turns,4);i++)if(nextSourceFrame.getAxis().isHorizontal())nextSourceFrame=nextSourceFrame.rotateY();
        BlockPos sourceNorth=ArmOrientation.inverse(nextSourceFrame,vector(ArmOrientation.vector(sourceFrame,new BlockPos(0,0,-1)),turns));int sourceTurns=sourceNorth.getX()==1?1:sourceNorth.getZ()==1?2:sourceNorth.getX()==-1?3:0;
        if(tag.hasKey(ArmFootComponentFrame.SOURCE_KEY)){if(!ArmFootComponentFrame.valid(tag))throw new IllegalArgumentException("Unknown source footprint frame");if(nextSourceFrame==EnumFacing.DOWN)tag.removeTag(ArmFootComponentFrame.SOURCE_KEY);else tag.setString(ArmFootComponentFrame.SOURCE_KEY,nextSourceFrame.name());}
        if(tag.hasKey(ArmFootComponentFrame.KEY)){
            if(!ArmFootComponentFrame.valid(tag))throw new IllegalArgumentException("Unknown landing component frame");
            EnumFacing frame=EnumFacing.valueOf(tag.getString(ArmFootComponentFrame.KEY));for(int i=0;i<Math.floorMod(turns,4);i++)if(frame.getAxis().isHorizontal())frame=frame.rotateY();tag.setString(ArmFootComponentFrame.KEY,frame.name());
        }
        EnumFacing mount=EnumFacing.DOWN;try{mount=EnumFacing.valueOf(tag.getString("ArmMountDirection"));}catch(IllegalArgumentException ignored){}
        EnumFacing nextMount=mount;if(mount.getAxis().isHorizontal())for(int i=0;i<Math.floorMod(turns,4);i++)nextMount=nextMount.rotateY();
        for(String key:new String[]{"GearOwnerOffset","TrackLeaderOffset"})if(tag.hasKey(key,4))tag.setLong(key,vector(BlockPos.fromLong(tag.getLong(key)),turns).toLong());
        NBTTagList trackMembers=tag.getTagList("TrackMembers",10);for(int i=0;i<trackMembers.tagCount();i++){NBTTagCompound entry=trackMembers.getCompoundTagAt(i);entry.setLong("Offset",vector(BlockPos.fromLong(entry.getLong("Offset")),turns).toLong());}
        for(String key:new String[]{"GearFeet","GearColumns"}){
            NBTTagList old=tag.getTagList(key,11),list=new NBTTagList();
            for(int i=0;i<old.tagCount();i++){int[] xyz=old.getIntArrayAt(i);if(xyz.length!=3)throw new IllegalArgumentException("Invalid modular arm topology");BlockPos p=new BlockPos(xyz[0],xyz[1],xyz[2]);p=key.equals("GearFeet")?ArmOrientation.inverse(nextMount,vector(ArmOrientation.vector(mount,p),turns)):vector(p,turns);list.appendTag(new NBTTagIntArray(new int[]{p.getX(),p.getY(),p.getZ()}));}
            if(tag.hasKey(key,9))tag.setTag(key,list);
        }
        if(tag.hasKey("GearTrackContact",11)){int[] xyz=tag.getIntArray("GearTrackContact");if(xyz.length!=3)throw new IllegalArgumentException("Invalid Track mounting contact");BlockPos contact=ArmOrientation.inverse(nextMount,vector(ArmOrientation.vector(mount,new BlockPos(xyz[0],xyz[1],xyz[2])),turns));tag.setIntArray("GearTrackContact",new int[]{contact.getX(),contact.getY(),contact.getZ()});}
        BlockPos north=ArmOrientation.inverse(nextMount,vector(ArmOrientation.vector(mount,new BlockPos(0,0,-1)),turns));int localTurns=north.getX()==1?1:north.getZ()==1?2:north.getX()==-1?3:0;
        if(tag.hasKey("ArmMountDirection",8))tag.setString("ArmMountDirection",nextMount.name());
        try{EnumFacing facing=EnumFacing.valueOf(tag.getString("GearDirection"));for(int i=0;i<localTurns;i++)facing=facing.rotateY();tag.setString("GearDirection",facing.name());}catch(IllegalArgumentException ignored){}
        if(sourceTurns!=localTurns&&!tag.hasKey("GearSourceFootFacing")&&tag.getBoolean("GearController")&&!"NONE".equals(tag.getString("GearKind"))&&tag.hasKey("GearFootFacing",3))tag.setInteger("GearSourceFootFacing",tag.getInteger("GearFootFacing"));
        if(tag.hasKey("GearSourceFootFacing",3)){EnumFacing foot=EnumFacing.getHorizontal(tag.getInteger("GearSourceFootFacing"));for(int i=0;i<sourceTurns;i++)foot=foot.rotateY();tag.setInteger("GearSourceFootFacing",foot.getHorizontalIndex());}
        if(tag.hasKey("ArmFootFacing",3)){EnumFacing foot=EnumFacing.getHorizontal(tag.getInteger("ArmFootFacing"));for(int i=0;i<localTurns;i++)foot=foot.rotateY();tag.setInteger("ArmFootFacing",foot.getHorizontalIndex());}
        if(tag.hasKey("GearFootFacing",3)){EnumFacing foot=EnumFacing.getHorizontal(tag.getInteger("GearFootFacing"));for(int i=0;i<localTurns;i++)foot=foot.rotateY();tag.setInteger("GearFootFacing",foot.getHorizontalIndex());}
        if(tag.hasKey(GearTaxiState.KEY,10))tag.setTag(GearTaxiState.KEY,GearTaxiState.rotate(tag.getCompoundTag(GearTaxiState.KEY),turns,parent));
        if(tag.hasKey("ArmStoredSections",10))tag.setTag("ArmStoredSections",ArmStoredSections.rotate(tag.getCompoundTag("ArmStoredSections"),turns,parent));
        if(tag.hasKey("ArmPayload",10)){
            if(parent==null)throw new IllegalArgumentException("Rotating an arm payload requires its read-only world view");
            tag.setTag("ArmPayload",ArmPayload.rotate(tag.getCompoundTag("ArmPayload"),turns,parent));
        }
        if(tag.hasKey("Appearance",10)){
            com.vandorlabs.gear.track.TrackAppearance old=com.vandorlabs.gear.track.TrackAppearance.read(tag.getCompoundTag("Appearance"));int side=((old.sideMask<<Math.floorMod(turns,4))|(old.sideMask>>(4-Math.floorMod(turns,4))))&15;
            tag.setTag("Appearance",new com.vandorlabs.gear.track.TrackAppearance(old.beltMask,old.mount,side,old.bridgeMask,((old.lowerSideMask<<Math.floorMod(turns,4))|(old.lowerSideMask>>(4-Math.floorMod(turns,4))))&15).write());
        }
    }
}
