package com.vandorlabs.neoforge.gear;

import com.vandorlabs.gear.core.GearTopology;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.world.level.Level;

/** Loaded-only discovery; a missing boundary refuses the complete publication. */
public final class NativeGearFormation {
    public record Scan(BlockPos root,GearTopology.Assembly assembly,List<BlockPos> members,List<BlockPos> columns,String error,boolean pending){
        public boolean valid(){return assembly!=null;}
    }
    public static boolean loaded(Level world,BlockPos pos){return world!=null&&!world.isOutsideBuildHeight(pos)&&world.getChunkSource().getChunkNow(pos.getX()>>4,pos.getZ()>>4)!=null;}
    public static NativeGearEntity at(Level world,BlockPos pos){if(!loaded(world,pos))return null;var chunk=world.getChunkSource().getChunkNow(pos.getX()>>4,pos.getZ()>>4);return chunk.getBlockEntities().get(pos) instanceof NativeGearEntity tile&&!tile.isRemoved()?tile:null;}
    private static Scan refused(String why,boolean pending){return new Scan(null,null,List.of(),List.of(),why,pending);}
    private static boolean column(NativeGearEntity tile){return tile!=null&&(tile.definition().arm()||tile.definition().kind==NativeGearDefinition.Kind.MOUNT);}
    private static boolean compatibleTrack(NativeGearEntity first,NativeGearEntity next){return !(first instanceof com.vandorlabs.neoforge.gear.track.NativeTrackEntity a)||!(next instanceof com.vandorlabs.neoforge.gear.track.NativeTrackEntity b)||a.style()==b.style()&&a.getBlockState().getValue(NativeGearBlock.FACING)==b.getBlockState().getValue(NativeGearBlock.FACING);}
    public static Scan scan(NativeGearEntity requested){
        Level world=requested.getLevel();BlockPos top=requested.getBlockPos();var current=requested;
        if(!column(current)){
            var feetToCheck=new ArrayDeque<BlockPos>();var visited=new HashSet<BlockPos>();feetToCheck.add(top);BlockPos found=null;Direction face=current.mount();
            while(!feetToCheck.isEmpty()){var cell=feetToCheck.removeFirst();if(!visited.add(cell))continue;if(visited.size()>GearTopology.MAX_FEET)return refused("Landing group exceeds 64 feet",false);var foot=at(world,cell);if(foot==null||!foot.definition().foot()||foot.mount()!=face)continue;var above=cell.relative(face.getOpposite());if(!loaded(world,above))return refused("Load every mounting column",true);var strut=at(world,above);if(column(strut)&&strut.mount()==face){found=above;break;}for(var side:List.of(Direction.NORTH,Direction.SOUTH,Direction.EAST,Direction.WEST)){var next=cell.offset(NativeGearOrientation.vector(face,new BlockPos(side.getStepX(),0,side.getStepZ())));if(!loaded(world,next))return refused("Load the complete landing group boundary",true);var neighbor=at(world,next);if(neighbor!=null&&neighbor.definition().foot()&&compatibleTrack(requested,neighbor))feetToCheck.add(next);}}
            if(found==null)return refused("Complete the mounting struts first",false);top=found;current=at(world,top);
        }
        int upper=0;
        while(true){BlockPos next=top.relative(current.mount().getOpposite());if(!loaded(world,next))return refused("Load the complete mounting column",true);var above=at(world,next);if(!column(above)||above.mount()!=current.mount())break;if(++upper>=GearTopology.MAX_COLUMN_HEIGHT)return refused("A strut column may contain at most eight sections",false);top=next;current=above;}
        final var definition=current.definition();final Direction mount=current.mount();List<BlockPos> members=new ArrayList<>();BlockPos first=top;int height=0;
        while(true){if(!loaded(world,first))return refused("Load the complete arm and its boundary",true);var tile=at(world,first);if(!column(tile)||tile.mount()!=mount)break;if(tile.definition()!=definition)return refused("Do not mix arm types in a column",false);if(definition.kind==NativeGearDefinition.Kind.ROTATING&&tile.getBlockState().getValue(NativeGearBlock.FACING)!=current.getBlockState().getValue(NativeGearBlock.FACING))return refused("Align connected Rotating Arms",false);if(++height>GearTopology.MAX_COLUMN_HEIGHT)return refused("A strut column may contain at most eight sections",false);members.add(first.immutable());first=first.relative(mount);}
        var foot=at(world,first);
        if(foot==null||!foot.definition().foot()){
            var result=GearTopology.validate(height,GearTopology.FootKind.NONE,List.of(new GearTopology.Cell(0,-height,0)));
            if(!result.isValid())return refused(result.error(),false);try{var joined=NativeGearJoined.columns(current,height);var primary=joined.getFirst();var joinedMembers=new ArrayList<BlockPos>();for(var column:joined)for(int n=0;n<height;n++)joinedMembers.add(column.relative(mount,n));return new Scan(primary,result.assembly(),List.copyOf(joinedMembers),joined.stream().map(pos->pos.subtract(primary)).toList(),"",false);}catch(IllegalArgumentException unavailable){return refused(unavailable.getMessage(),false);}
        }
        if(foot.definition().kind==NativeGearDefinition.Kind.WHEEL&&foot.definition().size!=definition.size)return refused("Wheel and arm sizes must match",false);
        var footDefinition=foot.definition();var footFacing=foot.getBlockState().getValue(NativeGearBlock.FACING);var kind=footDefinition.footKind();Set<BlockPos> feet=new LinkedHashSet<>();ArrayDeque<BlockPos> queue=new ArrayDeque<>();queue.add(first);Set<BlockPos> columns=new LinkedHashSet<>();columns.add(top);Direction[] sides={Direction.NORTH,Direction.SOUTH,Direction.EAST,Direction.WEST};
        while(!queue.isEmpty()){
            BlockPos pos=queue.removeFirst();if(!feet.add(pos))continue;if(feet.size()>GearTopology.MAX_FEET)return refused("Landing group exceeds 64 feet",false);var tile=at(world,pos);if(tile==null||tile.definition()!=footDefinition)return refused("Use one landing-foot type",false);if(kind!=GearTopology.FootKind.PLATE&&tile.getBlockState().getValue(NativeGearBlock.FACING)!=footFacing)return refused("Align connected skis and wheels",false);if(!NativeGearComponents.intact(world,pos))return refused("Complete the Large Wheel footprint first",false);if(tile.mount()!=mount)return refused("Align the landing feet mounting face",false);
            BlockPos column=pos.relative(mount.getOpposite(),height);if(!loaded(world,column))return refused("Load every mounting column",true);var strut=at(world,column);if(column(at(world,pos.relative(mount.getOpposite())))){if(!column(strut)||strut.definition()!=definition||strut.mount()!=mount)return refused("Connected struts must have the same type and height",false);var below=at(world,pos.relative(mount.getOpposite()));if(below==null||below.definition()!=definition||below.mount()!=mount)return refused("Connected struts must meet the foot plane",false);columns.add(column);
            for(int step=0;step<height;step++){BlockPos member=column.relative(mount,step);var part=at(world,member);if(part==null||part.definition()!=definition||part.mount()!=mount)return refused("Connected struts must have the same type and height",!loaded(world,member));if(!members.contains(member))members.add(member.immutable());}if(column(at(world,column.relative(mount.getOpposite()))))return refused("Connected struts must have the same height",false);if(footDefinition==NativeGearDefinition.LARGE_WHEEL&&!NativeGearComponents.root(world,pos).equals(pos))return refused("Align each Large Wheel root beneath its Large arm",false);}
            if(!members.contains(pos))members.add(pos.immutable());
            for(Direction side:sides){BlockPos next=pos.offset(NativeGearOrientation.vector(mount,new BlockPos(side.getStepX(),0,side.getStepZ())));if(!loaded(world,next))return refused("Load the complete landing group boundary",true);var candidate=at(world,next);if(candidate!=null&&candidate.definition().foot()&&compatibleTrack(foot,candidate))queue.add(next);}
        }
        var orderedColumns=columns.stream().sorted().toList();var primary=orderedColumns.getFirst();List<GearTopology.Cell> offsets=new ArrayList<>();for(BlockPos pos:feet){var local=NativeGearOrientation.inverse(mount,pos.subtract(primary));offsets.add(new GearTopology.Cell(local.getX(),local.getY(),local.getZ()));}
        var result=GearTopology.validate(height,kind,offsets);if(!result.isValid())return refused(result.error(),false);var relative=orderedColumns.stream().map(pos->pos.subtract(primary)).toList();return new Scan(primary.immutable(),result.assembly(),List.copyOf(members),relative,"",false);
    }
    private NativeGearFormation(){}
}
