package com.vandorlabs.vehicle;

import com.vandorlabs.coupler.BlockCoupler;
import com.vandorlabs.gear.*;
import com.vandorlabs.physics.CouplerGraphConstraint;
import java.util.*;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/** Stable semantic body/contact identity. Not a content, collision-cache, authorization or ownership receipt.
 * The returned descriptor retains only primitive labels and immutable positions, never its capture inputs. */
public final class VehicleGeneralPoseLayout {
    public static final int VERSION=1;
    private static final int MAX_OWNERSHIP=VehicleStructure.MAX_BLOCKS+64*512;
    private static final Comparator<VehicleStructure.Cell> CELLS=(a,b)->a.pos.compareTo(b.pos);
    private final long token;
    private final BlockPos[] carriers;
    private final int[] canonical;
    private final int root;
    private VehicleGeneralPoseLayout(long token,BlockPos[] carriers,int[] canonical,int root){this.token=token;this.carriers=carriers;this.canonical=canonical;this.root=root;}
    public long token(){return token;}
    public int bodies(){return carriers.length;}
    public int rootBody(){return root;}
    /** The index is the supplied constraint frame's body index, not a persistent ordinal. */
    public BlockPos carrier(int frameBody){return carriers[frameBody];}
    public int canonicalBody(int index){return canonical[index];}
    public int bodyForCarrier(BlockPos carrier){for(int i=0;i<carriers.length;i++)if(carriers[i].equals(carrier))return i;return -1;}
    public long retainedBytes(){return 64L+44L*carriers.length;}
    public static VehicleGeneralPoseLayout capture(VehicleStructure source,VehicleWorld view,VehicleCouplerTopology topology,VehicleArmConstraintFrame frame){
        World physical=view;int depth=0;while(physical instanceof VehicleWorld){if(++depth>8)throw new IllegalArgumentException("Nested general layout bound");physical=((VehicleWorld)physical).parent;}
        if(physical==null||physical.isRemote||physical.getMinecraftServer()==null||!physical.getMinecraftServer().isCallingFromMinecraftThread())throw new IllegalStateException("General layout requires the physical server thread");
        return project(source,view,topology,frame);
    }
    /** CPU reference seam; does not establish world/thread/gameplay admission. */
    static VehicleGeneralPoseLayout project(VehicleStructure source,VehicleWorld view,VehicleCouplerTopology topology,VehicleArmConstraintFrame frame){
        if(source==null||view==null||view.structure!=source||topology==null||frame==null||source.cells.size()>VehicleStructure.MAX_BLOCKS||topology.sections.size()>CouplerGraphConstraint.MAX_BODIES||topology.links.size()>CouplerGraphConstraint.MAX_LINKS||frame.sections()!=topology.sections.size()||frame.graph().links()!=topology.links.size()||frame.arms()>CouplerGraphConstraint.MAX_ARMS)throw new IllegalArgumentException("General layout bounds/source changed");
        int count=frame.graph().bodies;BlockPos[] carriers=new BlockPos[count];
        for(int section=0;section<topology.sections.size();section++){
            int body=frame.sectionBody(section);if(body<0||body>=count)throw new IllegalArgumentException("General frame body disappeared");
            for(BlockPos at:topology.sections.get(section)){
                if(source.at(at)==null||!Integer.valueOf(section).equals(topology.section.get(at)))throw new IllegalArgumentException("General native section identity changed");
                if(carriers[body]==null||at.compareTo(carriers[body])<0)carriers[body]=at.toImmutable();
            }
        }
        Integer[] order=new Integer[count];for(int body=0;body<count;body++){
            BlockPos at=carriers[body];if(at==null||at.getX()<0||at.getX()>=64||at.getY()<0||at.getY()>=64||at.getZ()<0||at.getZ()>=64)throw new IllegalArgumentException("General body needs an owned native carrier");
            for(int previous=0;previous<body;previous++)if(at.equals(carriers[previous]))throw new IllegalArgumentException("Duplicate general body carrier");order[body]=body;
        }
        Arrays.sort(order,(a,b)->carriers[a].compareTo(carriers[b]));int[] canonical=new int[count];for(int i=0;i<count;i++)canonical[i]=order[i];
        if(frame.graph().root!=frame.sectionBody(topology.root))throw new IllegalArgumentException("General root body changed");
        Hash hash=new Hash();hash.number(0x474C5931);hash.number(VERSION);hash.number(count);hash.position(carriers[frame.graph().root]);hash.position(source.seat);hash.number(source.facing.ordinal());
        hash.real(source.bounds.minX);hash.real(source.bounds.minY);hash.real(source.bounds.minZ);hash.real(source.bounds.maxX);hash.real(source.bounds.maxY);hash.real(source.bounds.maxZ);
        for(int body:canonical)hash.position(carriers[body]);
        List<VehicleStructure.Cell> nativeCells=sorted(source.cells);hash.number(nativeCells.size());
        for(VehicleStructure.Cell cell:nativeCells){Integer section=topology.section.get(cell.pos);if(section==null)throw new IllegalArgumentException("General owned cell lost its body");hash.position(cell.pos);hash.position(carriers[frame.sectionBody(section)]);}
        List<BlockPos> roots=new ArrayList<>();long ownership=source.cells.size(),payloadCells=0;
        for(int i=0;i<frame.arms();i++){
            BlockPos at=frame.arm(i);GearTileEntity arm=owner(source,view,at);ArmPayload payload=arm.payload();payloadCells+=payload.blocks.cells.size();ownership+=arm.storedSectionCells().size();if(!payload.materialized())ownership+=payload.blocks.cells.size();
            if(payloadCells>MAX_OWNERSHIP||ownership>MAX_OWNERSHIP)throw new IllegalArgumentException("General retained ownership exceeds bounded descriptor work");
            if(roots.contains(at))throw new IllegalArgumentException("Duplicate general arm root");roots.add(at.toImmutable());
        }
        Collections.sort(roots);hash.number(roots.size());
        for(BlockPos at:roots){
            GearTileEntity arm=owner(source,view,at);ArmPayload payload=arm.payload();hash.position(at);hash.position(carriers[frame.sectionBody(topology.section.get(at))]);
            hash.text(source.at(at).state.getBlock().getRegistryName().toString());hash.number(((GearBlock)source.at(at).state.getBlock()).kind().ordinal());hash.number(arm.mode().ordinal());hash.number(arm.mountDirection().ordinal());hash.number(arm.direction().ordinal());hash.number(arm.armSize());hash.number(arm.assembly().columnHeight());hash.number(arm.assembly().kind().ordinal());hash.number(arm.footFacing().ordinal());hash.real(arm.footMountOffset());
            List<BlockPos> columns=new ArrayList<>(arm.columns());Collections.sort(columns);hash.number(columns.size());for(BlockPos column:columns)hash.position(column);hash.number(arm.assembly().feet().size());for(com.vandorlabs.gear.core.GearTopology.Cell foot:arm.assembly().feet()){hash.number(foot.x);hash.number(foot.y);hash.number(foot.z);}com.vandorlabs.gear.core.GearTopology.Cell contact=arm.assembly().anchorFoot();hash.number(contact.x);hash.number(contact.y);hash.number(contact.z);
            hash.real(arm.activeAngle());hash.real(arm.configuredEndAngle());hash.real(arm.physicalStartAngle());hash.real(arm.configuredActiveExtension());hash.number(arm.rotatePayload()?1:0);hash.number(arm.armRetractionStroke()?1:0);hash.number(arm.trackRetractionStroke()?1:0);hash.number(arm.isExtendedTrackStroke()?1:0);
            hash.position(payload.offset);hash.number(payload.materialized()?1:0);List<VehicleStructure.Cell> cells=sorted(payload.blocks.cells);hash.number(cells.size());
            // Installed aliases belong to immutable ownership metadata, not the evaluated current pose.
            Map<BlockPos,Integer> indexes=null;if(payload.materialized()){indexes=new HashMap<>();for(int i=0;i<payload.blocks.cells.size();i++)indexes.put(payload.blocks.cells.get(i).pos,i);}
            for(VehicleStructure.Cell cell:cells){hash.position(cell.pos);if(indexes!=null)hash.position(payload.placedCellPosition(arm,indexes.get(cell.pos)));}
            List<VehicleStructure.Cell> stored=sorted(arm.storedSectionCells());hash.number(stored.size());for(VehicleStructure.Cell cell:stored)hash.position(cell.pos);
        }
        List<Edge> edges=new ArrayList<>();for(VehicleCouplerTopology.Link link:topology.links){if(link.headA==null||link.headB==null||link.headA.released||link.headB.released)throw new IllegalArgumentException("General closed constraint lacks stable heads");edges.add(new Edge(link));}
        Collections.sort(edges,(a,b)->{int c=address(a.first.address,b.first.address);return c!=0?c:address(a.second.address,b.second.address);});hash.number(edges.size());
        Edge previous=null;for(Edge edge:edges){if(previous!=null&&address(previous.first.address,edge.first.address)==0&&address(previous.second.address,edge.second.address)==0)throw new IllegalArgumentException("Duplicate general closed pair");hash.number(edge.link.articulation?1:0);head(hash,edge.first,source,view,topology,frame,carriers);head(hash,edge.second,source,view,topology,frame,carriers);previous=edge;}
        return new VehicleGeneralPoseLayout(hash.value==0?1:hash.value,carriers,canonical,frame.graph().root);
    }
    private static GearTileEntity owner(VehicleStructure source,VehicleWorld view,BlockPos at){
        TileEntity raw=view.tiles.get(at);VehicleStructure.Cell cell=source.at(at);
        if(!(raw instanceof GearTileEntity)||raw.isInvalid()||raw.getWorld()!=view||cell==null||!(cell.state.getBlock() instanceof GearBlock))throw new IllegalArgumentException("General arm owner changed");
        GearTileEntity arm=(GearTileEntity)raw;if(!at.equals(arm.owner())||!arm.isOperational()||arm.assembly()==null||arm.payload()==null||arm.hasFailedArmSnapshot()||arm.hasTaxiData()||arm.payload().hasOwnershipJournal())throw new IllegalArgumentException("General arm ownership unavailable");return arm;
    }
    private static List<VehicleStructure.Cell> sorted(List<VehicleStructure.Cell> cells){List<VehicleStructure.Cell> copy=new ArrayList<>(cells);Collections.sort(copy,CELLS);return copy;}
    private static final class Edge {
        final VehicleCouplerTopology.Link link;final VehicleArmCouplers.Head first,second;
        Edge(VehicleCouplerTopology.Link link){this.link=link;boolean forward=address(link.headA.address,link.headB.address)<0;first=forward?link.headA:link.headB;second=forward?link.headB:link.headA;}
    }
    private static int address(VehicleArmCouplers.Address a,VehicleArmCouplers.Address b){
        if(a.arm==null&&b.arm!=null)return -1;if(a.arm!=null&&b.arm==null)return 1;int result=a.arm==null?0:a.arm.compareTo(b.arm);return result!=0?result:a.cell.compareTo(b.cell);
    }
    private static void head(Hash hash,VehicleArmCouplers.Head head,VehicleStructure source,VehicleWorld view,VehicleCouplerTopology topology,VehicleArmConstraintFrame frame,BlockPos[] carriers){
        Integer section=topology.section.get(head.carrier);if(section==null)throw new IllegalArgumentException("General head carrier changed");hash.position(carriers[frame.sectionBody(section)]);hash.number(head.address.arm==null?0:1);if(head.address.arm!=null)hash.position(head.address.arm);hash.position(head.address.cell);hash.number(head.nativeCell==null?0:1);if(head.nativeCell!=null)hash.position(head.nativeCell);hash.position(head.legacyCell);hash.number(head.legacyFacing.ordinal());hash.real(head.legacyHeight);hash.real(head.localX);hash.real(head.localY);hash.real(head.localZ);hash.number(head.articulated?1:0);
        VehicleStructure.Cell original=head.address.arm==null?source.at(head.address.cell):owner(source,view,head.address.arm).payload().blocks.at(head.address.cell);
        if(original==null||!(original.state.getBlock() instanceof BlockCoupler))throw new IllegalArgumentException("General original head disappeared");hash.text(original.state.getBlock().getRegistryName().toString());hash.number(original.state.getValue(BlockCoupler.FACING).ordinal());hash.real(BlockCoupler.couplingHeight(original.state));
    }
    private static final class Hash {
        long value=0xcbf29ce484222325L;
        void number(long number){for(int i=0;i<8;i++){value=(value^(number&255))*0x100000001b3L;number>>>=8;}}
        void position(BlockPos at){number(at.getX());number(at.getY());number(at.getZ());}
        void real(double value){if(!Double.isFinite(value))throw new IllegalArgumentException("Nonfinite general layout geometry");number(Double.doubleToLongBits(value==0?0:value));}
        void text(String text){number(text.length());for(int i=0;i<text.length();i++)number(text.charAt(i));}
    }
}
