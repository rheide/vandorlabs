package com.vandorlabs.neoforge.gear.track;

import com.vandorlabs.gear.core.GearTopology;
import com.vandorlabs.gear.track.core.*;
import com.vandorlabs.neoforge.gear.*;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.nbt.*;

/** Immutable loaded-foot publication: rendering and collision never rediscover world topology. */
public final class NativeTrackDescriptor {
    public record Foot(GearTopology.Cell cell,int appearance){}
    private final int style;
    private final List<Foot> feet;
    public NativeTrackDescriptor(int style,List<Foot> feet){if(style<0||style>2||feet.isEmpty()||feet.size()>64)throw new IllegalArgumentException("Invalid Track descriptor");var seen=new HashSet<GearTopology.Cell>();for(var foot:feet){if(foot==null||!seen.add(foot.cell())||Math.abs((long)foot.cell().x)>64||Math.abs((long)foot.cell().y)>8||Math.abs((long)foot.cell().z)>64)throw new IllegalArgumentException("Track footprint bounds/duplicates");var state=TrackVisualState.strict(foot.appearance());if(state.mount==TrackVisualState.Mount.SOCKET)throw new IllegalArgumentException("Duplicate arm socket");}this.style=style;this.feet=List.copyOf(feet);}
    public TrackTopology.Style style(){return TrackTopology.Style.values()[style];}
    public List<Foot> feet(){return feet;}
    public CompoundTag write(){var tag=new CompoundTag();tag.putInt("Schema",1);tag.putInt("Style",style);var list=new ListTag();for(var foot:feet){var cell=foot.cell();list.add(new IntArrayTag(new int[]{cell.x,cell.y,cell.z,foot.appearance()}));}tag.put("Feet",list);return tag;}
    public static NativeTrackDescriptor read(CompoundTag tag){if(!tag.contains("Schema",Tag.TAG_INT)||tag.getInt("Schema")!=1||!tag.contains("Style",Tag.TAG_INT)||!tag.contains("Feet",Tag.TAG_LIST)||tag.sizeInBytes()>8192)throw new IllegalArgumentException("Malformed Track descriptor");var list=(ListTag)tag.get("Feet");if(list.size()>64||list.getElementType()!=Tag.TAG_INT_ARRAY)throw new IllegalArgumentException("Track descriptor list");var feet=new ArrayList<Foot>();for(var raw:list){var cell=((IntArrayTag)raw).getAsIntArray();if(cell.length!=4)throw new IllegalArgumentException("Track descriptor cell");feet.add(new Foot(new GearTopology.Cell(cell[0],cell[1],cell[2]),cell[3]));}return new NativeTrackDescriptor(tag.getInt("Style"),feet);}
    public static NativeTrackDescriptor capture(NativeGearEntity owner,GearTopology.Assembly assembly){if(assembly==null||assembly.kind()!=GearTopology.FootKind.TRACK)return null;var feet=new ArrayList<Foot>();int style=-1;boolean rebuilt=false;for(var cell:assembly.feet()){var pos=owner.getBlockPos().offset(NativeGearOrientation.vector(owner.mount(),new BlockPos(cell.x,cell.y,cell.z)));if(!(NativeGearFormation.at(owner.getLevel(),pos) instanceof NativeTrackEntity tile))throw new IllegalArgumentException("Load every Track member");if(!rebuilt){NativeTrackRuntime.rebuild(tile);rebuilt=true;}if(!tile.complete())throw new IllegalArgumentException("Complete the Track topology");if(style<0)style=tile.style().ordinal();else if(style!=tile.style().ordinal())throw new IllegalArgumentException("Use one Track style");feet.add(new Foot(cell,tile.appearance().onArm().key()));}return new NativeTrackDescriptor(style,feet);}
}
