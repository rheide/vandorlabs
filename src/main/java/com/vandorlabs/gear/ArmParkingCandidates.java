package com.vandorlabs.gear;

import com.vandorlabs.gear.core.*;
import com.vandorlabs.vehicle.VehicleStructure;
import java.util.*;
import net.minecraft.util.math.*;

/** Exact-grid candidate enumeration. A candidate is not collision/publication authorization.
 * Identity tile envelopes defer deeper factory validation to the final chosen candidate. */
public final class ArmParkingCandidates {
    public enum Status { COMPLETE, INCOMPLETE, UNSUPPORTED, BOUNDED_REFUSAL }
    /** Integer cell-index map: a local cell maps to (x,y,z)+basis*local.
     * The origin is the minimum corner of the transformed local origin unit cell, not a vertex origin. */
    public static final class Candidate {
        public final double phase;public final int x,y,z;
        public final boolean tileValidationDeferred,storedSectionValidationDeferred;
        private final int[] basis;
        Candidate(double phase,ArmGridMapping mapping,boolean deferred,boolean sections){this.phase=phase;tileValidationDeferred=deferred;storedSectionValidationDeferred=sections;BlockPos zero=mapping.cell(BlockPos.ORIGIN);x=zero.getX();y=zero.getY();z=zero.getZ();basis=mapping.basis();}
        public int basis(int row,int column){if(row<0||row>2||column<0||column>2)throw new IndexOutOfBoundsException();return basis[row*3+column];}
        private boolean same(Candidate other){return x==other.x&&y==other.y&&z==other.z&&Arrays.equals(basis,other.basis);}
    }
    public static final class Result {
        public final Status status;public final List<Candidate> candidates;
        public final int testedPhases,transformQueries,stateChecks,typedStateQueries,storedSectionCount;
        Result(Status status,List<Candidate> candidates,int phases,int transforms,int checks,int typed,int sections){this.status=status;this.candidates=Collections.unmodifiableList(new ArrayList<>(candidates));testedPhases=phases;transformQueries=transforms;stateChecks=checks;typedStateQueries=typed;storedSectionCount=sections;}
        public boolean completeForDistinctGridMappings(){return status==Status.COMPLETE;}
        public boolean storedSectionValidationDeferred(){return storedSectionCount>0;}
        public boolean tileValidationDeferred(){for(Candidate candidate:candidates)if(candidate.tileValidationDeferred)return true;return false;}
    }
    private static final class Admission {final boolean allowed,deferred;Admission(boolean allowed,boolean deferred){this.allowed=allowed;this.deferred=deferred;}}
    private static Result refuse(Status status){return new Result(status,Collections.emptyList(),0,0,0,0,0);}
    /** Main-thread loaded exact owner only. Never rebuilds gear or creates a payload view. */
    public static Result enumerate(GearTileEntity arm){
        if(arm==null||arm.getWorld()==null)return refuse(Status.INCOMPLETE);
        net.minecraft.world.World world=arm.getWorld(),physical=world;int depth=0;
        while(physical instanceof com.vandorlabs.vehicle.VehicleWorld){if(++depth>8)return refuse(Status.INCOMPLETE);physical=((com.vandorlabs.vehicle.VehicleWorld)physical).parent;}
        if(physical==null||physical.isRemote||physical.getMinecraftServer()==null||!physical.getMinecraftServer().isCallingFromMinecraftThread()||cachedTile(world,arm.getPos())!=arm||arm.owner()==null||!arm.owner().equals(arm.getPos()))return refuse(Status.INCOMPLETE);
        if(!validStoredSections(arm))return refuse(Status.INCOMPLETE);
        if(arm.members().isEmpty()||arm.members().size()>ArmPayload.MAX_BLOCKS)return refuse(Status.INCOMPLETE);
        for(BlockPos member:arm.members()){
            if(arm.storedSections!=null&&arm.storedSections.at(member.subtract(arm.getPos()))!=null)continue;
            net.minecraft.tileentity.TileEntity tile=cachedTile(world,member);
            if(!(tile instanceof GearTileEntity)||tile.getWorld()!=world||!arm.getPos().equals(((GearTileEntity)tile).owner()))return refuse(Status.INCOMPLETE);
        }
        return inspect(arm);
    }
    private static boolean anyKey(VehicleStructure.Cell cell,String key){for(int type=1;type<=12;type++)if(cell.hasTileKey(key,type))return true;return false;}
    /** Existing displaced section ownership, checked without ArmStoredSections.read or a tile factory. */
    private static boolean validStoredSections(GearTileEntity arm){
        if(!arm.hasStoredSections())return true;
        if(arm.storedSections==null||arm.mode()!=GearMotion.Mode.EXTEND||arm.isSwingArmController())return false;
        List<VehicleStructure.Cell> cells=arm.storedSectionCells();if(cells.isEmpty()||cells.size()>512)return false;
        net.minecraft.util.ResourceLocation id=net.minecraft.tileentity.TileEntity.getKey(GearTileEntity.class);if(id==null)return false;
        Set<BlockPos> seen=new HashSet<>(),members=arm.members();List<BlockPos> columns=arm.columns();int size=arm.armSize();String gearId=id.toString();
        for(VehicleStructure.Cell cell:cells){
            BlockPos at=cell.pos;
            if(arm.storedSections.at(at)!=cell||at.equals(BlockPos.ORIGIN)||!seen.add(at)||Math.abs((long)at.getX())>80||Math.abs((long)at.getY())>80||Math.abs((long)at.getZ())>80||!members.contains(arm.getPos().add(at))||columns.contains(at)
                    ||!(cell.state.getBlock() instanceof GearBlock)||((GearBlock)cell.state.getBlock()).kind()!=GearBlock.Kind.EXTENDER||((GearBlock)cell.state.getBlock()).sizeClass()!=size
                    ||!cell.hasTileData()||!gearId.equals(cell.tileString("id"))||cell.tileBoolean("GearController")||!cell.hasTileKey("GearOwnerOffset",4)||cell.tileLong("GearOwnerOffset")!=BlockPos.ORIGIN.subtract(at).toLong()
                    ||!cell.hasTileKey("x",99)||!cell.hasTileKey("y",99)||!cell.hasTileKey("z",99)||cell.tileInteger("x")!=at.getX()||cell.tileInteger("y")!=at.getY()||cell.tileInteger("z")!=at.getZ()
                    ||anyKey(cell,"ArmPayload")||anyKey(cell,"ArmStoredSections")||anyKey(cell,GearTaxiState.KEY))return false;
        }
        return true;
    }
    private static net.minecraft.tileentity.TileEntity cachedTile(net.minecraft.world.World world,BlockPos pos){
        net.minecraft.tileentity.TileEntity tile;
        if(world instanceof com.vandorlabs.vehicle.VehicleWorld)tile=((com.vandorlabs.vehicle.VehicleWorld)world).tiles.get(pos);
        else{
            if(world.getChunkProvider()==null)return null;
            net.minecraft.world.chunk.Chunk chunk=world.getChunkProvider().getLoadedChunk(pos.getX()>>4,pos.getZ()>>4);
            tile=chunk==null?null:chunk.getTileEntityMap().get(pos);
        }
        return tile==null||tile.isInvalid()?null:tile;
    }
    /** Package seam for actual-transform CPU oracles, with no fake world/capability construction. */
    static Result inspect(GearTileEntity arm){
        ArmPayload payload=arm.payload();
        if(!arm.isOperational()||arm.assembly()==null||payload==null||payload.hasOwnershipJournal()||arm.hasTaxiData()||arm.recoveryDropped()||arm.assembly().kind()!=GearTopology.FootKind.NONE)return refuse(Status.INCOMPLETE);
        if(!validStoredSections(arm))return refuse(Status.INCOMPLETE);
        int sections=arm.storedSectionCells().size();
        GearMotion.Mode mode=arm.mode();if(mode!=GearMotion.Mode.EXTEND&&mode!=GearMotion.Mode.ROTATE&&mode!=GearMotion.Mode.ROTATE_LEVEL||mode==GearMotion.Mode.EXTEND&&arm.isSwingArmController()||mode!=GearMotion.Mode.EXTEND&&!arm.isSwingArmController())return refuse(Status.UNSUPPORTED);
        if(payload.blocks.cells.isEmpty())return refuse(Status.INCOMPLETE);
        if(payload.blocks.cells.size()>ArmPayload.MAX_BLOCKS)return refuse(Status.BOUNDED_REFUSAL);
        // Identity tile envelopes remain untouched and require chosen-candidate factory validation.
        // Missing own tile data cannot describe a complete owner.
        for(VehicleStructure.Cell cell:payload.blocks.cells)if(cell.state.getBlock().hasTileEntity(cell.state)&&!cell.hasTileData())return refuse(Status.UNSUPPORTED);
        int transforms=0,checks=0,tested=0,typed=0;List<Candidate> result=new ArrayList<>();Map<Integer,Admission> admissible=new HashMap<>();
        try{
            double[] phases;
            if(mode==GearMotion.Mode.EXTEND){Vec3d a=payload.transform(arm,0,0,0,0),b=payload.transform(arm,1,0,0,0);transforms+=2;phases=ArmParkingPhases.linear(vector(a),vector(b));}
            else{
                double a=arm.pose(0).angleDegrees(),b=arm.pose(1).angleDegrees();
                if(mode==GearMotion.Mode.ROTATE)phases=ArmParkingPhases.rotating(a,b);
                else{
                    Vec3d[] samples=new Vec3d[3];int i=0;
                    for(double angle:new double[]{0,90,180}){GearMotion.Pose pose=GearMotion.pose(mode,arm.direction(),arm.assembly().columnHeight(),1,0,0,arm.footMountOffset(),angle,angle);samples[i++]=payload.rawTransform(arm,pose,0,0,0);transforms++;}
                    double[] center=new double[3],cosine=new double[3],sine=new double[3];double[] zero=vector(samples[0]),quarter=vector(samples[1]),half=vector(samples[2]);
                    for(i=0;i<3;i++){center[i]=(zero[i]+half[i])*.5;cosine[i]=(zero[i]-half[i])*.5;sine[i]=quarter[i]-center[i];}
                    phases=ArmParkingPhases.leveling(center,cosine,sine,a,b);
                }
            }
            for(double phase:phases){tested++;ArmGridMapping mapping=payload.gridMapping(arm,phase);transforms+=4;if(mapping==null)continue;int[] basis=mapping.basis();int key=0;for(int v:basis)key=key*3+v+1;Admission admission=admissible.get(key);
                if(admission==null){
                    boolean allowed=true,deferred=false;IdentityHashMap<net.minecraft.block.state.IBlockState,Boolean> tileFree=null;
                    for(VehicleStructure.Cell cell:payload.blocks.cells){
                        checks++;boolean ownsTile=cell.hasTileData();
                        if(mapping.rotates()){
                            Boolean valid=null;
                            if(!ownsTile){if(tileFree==null)tileFree=new IdentityHashMap<>();valid=tileFree.get(cell.state);}
                            if(valid==null){typed++;valid=ArmGridTiles.state(cell,basis)!=null;if(!ownsTile)tileFree.put(cell.state,valid);}
                            if(!valid){allowed=false;break;}
                        }else if(ownsTile)deferred=true;
                    }
                    admission=new Admission(allowed,deferred);admissible.put(key,admission);
                }
                if(!admission.allowed)continue;Candidate candidate=new Candidate(phase,mapping,admission.deferred,sections>0);boolean duplicate=false;for(Candidate previous:result)if(candidate.same(previous)){duplicate=true;break;}if(!duplicate)result.add(candidate);
            }
            return new Result(Status.COMPLETE,result,tested,transforms,checks,typed,sections);
        }catch(IllegalArgumentException|ArithmeticException bounded){return new Result(Status.BOUNDED_REFUSAL,Collections.emptyList(),tested,transforms,checks,typed,sections);}
    }
    private static double[] vector(Vec3d v){return new double[]{v.x,v.y,v.z};}
    private ArmParkingCandidates(){}
}
