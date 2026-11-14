package com.vandorlabs.neoforge.gear;

import com.vandorlabs.neoforge.ModernFinishCatalog;
import com.vandorlabs.neoforge.ModernHousingTags;
import com.vandorlabs.neoforge.storage.NativeStorageSettings;
import com.vandorlabs.persistence.HousingSettings;
import com.vandorlabs.persistence.StairState;
import com.vandorlabs.surface.TextureJoinSettings;
import java.io.*;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.*;
import net.minecraft.world.level.block.SignBlock;
import net.minecraft.world.level.block.entity.SignText;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

/** Bounded public artwork and geometry. Authoritative tile snapshots never enter this protocol. */
public final class NativeGearPayloadView {
    public static final int MAX_BYTES=256*1024,MAX_CELLS=8192,MAX_BOXES=32768,MAX_RETAINED_BYTES=16*1024*1024;
    private static final Set<String> ROOT_KEYS=Set.of("Schema","Identity","CapturePhase","Materialized","Reference","States","Shapes","Projections","Cells");
    private static final Set<String> HOUSING_KEYS=Set.of(HousingSettings.MAIN,HousingSettings.VERSION,HousingSettings.SIDE,HousingSettings.LAYOUT,HousingSettings.TILE_SIDES,HousingSettings.FACES_ENABLED,HousingSettings.FACES,TextureJoinSettings.DIRECTION_TAG,TextureJoinSettings.PARITY_TAG,StairState.TREAD,StairState.ROLL);
    private static final Set<String> SIGN_KEYS=Set.of("messages","filtered_messages","color","has_glowing_text");
    public record Cell(BlockPos source,BlockState state,CompoundTag projection,List<AABB> boxes) {
        public Cell {
            source=Objects.requireNonNull(source).immutable();Objects.requireNonNull(state);projection=Objects.requireNonNull(projection).copy();boxes=List.copyOf(boxes);
            coordinate(source.getX());coordinate(source.getY());coordinate(source.getZ());if(state.isAir()||boxes.isEmpty()||boxes.size()>128)throw new IllegalArgumentException("Invalid arm view cell");
            for(var box:boxes)validateBox(box);
        }
        @Override public CompoundTag projection(){return projection.copy();}
    }
    private final List<Cell> cells;
    private final UUID identity;
    private final double phase;
    private final NativeGearPayload.Transform reference;
    private final boolean materialized;
    private final int retainedBytes;
    private NativeGearPayloadView(UUID identity,double phase,boolean materialized,NativeGearPayload.Transform reference,List<Cell> cells) {
        this.identity=Objects.requireNonNull(identity);if(identity.getMostSignificantBits()==0&&identity.getLeastSignificantBits()==0||!Double.isFinite(phase)||phase<0||phase>1||cells.isEmpty()||cells.size()>MAX_CELLS)throw new IllegalArgumentException("Invalid arm view envelope");
        this.reference=NativeGearPayload.Transform.read(Objects.requireNonNull(reference).write());this.phase=phase;this.materialized=materialized;this.cells=List.copyOf(cells);long weight=512;int boxes=0;Set<BlockPos> seen=new HashSet<>();
        for(var cell:cells){if(!seen.add(cell.source))throw new IllegalArgumentException("Duplicate arm view cell");boxes=Math.addExact(boxes,cell.boxes.size());weight=weight(weight,cell.boxes.size(),cell.projection.sizeInBytes());}
        if(boxes>MAX_BOXES)throw new IllegalArgumentException("Arm view expanded collision limit");retainedBytes=Math.toIntExact(weight);bounded(encode());
    }
    public List<Cell> cells(){return cells;}
    public double capturePhase(){return phase;}
    public NativeGearPayload.Transform reference(){return reference;}
    public boolean materialized(){return materialized;}
    public UUID identity(){return identity;}
    public int retainedBytes(){return retainedBytes;}
    public static NativeGearPayloadView from(NativeGearPayload payload,BlockPos root,HolderLookup.Provider registries) {
        Objects.requireNonNull(payload);Objects.requireNonNull(root);Objects.requireNonNull(registries);var result=new ArrayList<Cell>(payload.size());long budget=512;int count=0;
        for(int i=0;i<payload.size();i++){
            var original=payload.cells().get(i);long x=(long)original.pos.getX()-root.getX(),y=(long)original.pos.getY()-root.getY(),z=(long)original.pos.getZ()-root.getZ();coordinate(x);coordinate(y);coordinate(z);
            if(original.section)throw new IllegalArgumentException("Arm source sections are not public payload cells");
            CompoundTag raw=original.tile();var clean=projection(original.state,raw==null?new CompoundTag():raw,registries);var boxes=payload.shapes(i);count=Math.addExact(count,boxes.size());if(count>MAX_BOXES)throw new IllegalArgumentException("Arm view expanded collision limit");budget=weight(budget,boxes.size(),clean.sizeInBytes());result.add(new Cell(new BlockPos((int)x,(int)y,(int)z),original.state,clean,boxes));
        }
        return new NativeGearPayloadView(payload.id(),payload.capturePhase(),payload.materialized(),payload.reference(),result);
    }
    private static long weight(long before,int boxes,int projectionBytes){long result=Math.addExact(before,Math.addExact(512L+64L*boxes,4L*projectionBytes));if(result>MAX_RETAINED_BYTES)throw new IllegalArgumentException("Arm view retained memory limit");return result;}
    private static void coordinate(long value){if(value< -64||value>64)throw new IllegalArgumentException("Arm view coordinate limit");}
    private static void validateBox(AABB box){if(box==null)throw new IllegalArgumentException("Null arm view collision");for(double value:new double[]{box.minX,box.minY,box.minZ,box.maxX,box.maxY,box.maxZ})if(!Double.isFinite(value)||value< -1||value>2)throw new IllegalArgumentException("Arm view collision coordinate limit");if(box.minX>=box.maxX||box.minY>=box.maxY||box.minZ>=box.maxZ)throw new IllegalArgumentException("Empty arm view collision");}
    private static CompoundTag projection(BlockState state,CompoundTag raw,HolderLookup.Provider registries) {
        String name=BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();var result=new CompoundTag();
        boolean housing=name.equals("vandorlabs:programmable_block")||name.equals("vandorlabs:programmable_slab")||name.equals("vandorlabs:programmable_stairs");
        if(housing||name.equals("vandorlabs:programmable_storage")){
            var selected=new CompoundTag();for(String key:HOUSING_KEYS){Tag value=raw.get(key);if(value!=null)selected.put(key,value.copy());}
            var tags=new ModernHousingTags(selected);
            if(housing){HousingSettings.read(tags,0,true,ModernFinishCatalog::storedChoice,ModernFinishCatalog::validChoice).write(new ModernHousingTags(result));TextureJoinSettings.read(tags).write(new ModernHousingTags(result));}
            else NativeStorageSettings.read(selected).write(result);
            if(name.equals("vandorlabs:programmable_stairs"))StairState.read(tags).write(new ModernHousingTags(result));
        }else if(state.getBlock() instanceof SignBlock){
            for(String side:List.of("front_text","back_text")){
                CompoundTag face=raw.contains(side,Tag.TAG_COMPOUND)?raw.getCompound(side):new CompoundTag();
                SignText decoded=face.isEmpty()?new SignText():readSign(face,registries);var clean=new SignText().setColor(decoded.getColor()).setHasGlowingText(decoded.hasGlowingText());
                for(int line=0;line<4;line++)clean=clean.setMessage(line,publicText(decoded.getMessage(line,false)),publicText(decoded.getMessage(line,true)));
                Tag encoded=SignText.DIRECT_CODEC.encodeStart(registries.createSerializationContext(NbtOps.INSTANCE),clean).getOrThrow();if(!(encoded instanceof CompoundTag compound))throw new IllegalArgumentException("Sign projection envelope");result.put(side,compound);
            }
            if(raw.contains("is_waxed")&&(!raw.contains("is_waxed",Tag.TAG_BYTE)||raw.getByte("is_waxed")<0||raw.getByte("is_waxed")>1))throw new IllegalArgumentException("Sign wax projection flag");result.putBoolean("is_waxed",raw.getBoolean("is_waxed"));
        }
        return result;
    }
    private static SignText readSign(CompoundTag raw,HolderLookup.Provider registries){
        var face=new CompoundTag();for(String key:SIGN_KEYS){Tag value=raw.get(key);if(value!=null)face.put(key,value.copy());}
        for(String key:List.of("messages","filtered_messages")){
            if(!face.contains(key)&&key.equals("filtered_messages"))continue;
            if(!(face.get(key) instanceof ListTag lines)||lines.size()!=4||lines.getElementType()!=Tag.TAG_STRING)throw new IllegalArgumentException("Sign projection line schema");for(int i=0;i<4;i++)jsonDepth(lines.getString(i));
        }
        return SignText.DIRECT_CODEC.parse(registries.createSerializationContext(NbtOps.INSTANCE),face).getOrThrow();
    }
    private static void jsonDepth(String text){
        if(text.length()>8192)throw new IllegalArgumentException("Sign projection text limit");boolean quoted=false,escaped=false;int depth=0;
        for(int i=0;i<text.length();i++){char c=text.charAt(i);if(quoted){if(escaped)escaped=false;else if(c=='\\')escaped=true;else if(c=='\"')quoted=false;}else if(c=='\"')quoted=true;else if(c=='{'||c=='['){if(++depth>32)throw new IllegalArgumentException("Sign projection text depth");}else if(c=='}'||c==']'){if(--depth<0)throw new IllegalArgumentException("Sign projection text structure");}}if(quoted||depth!=0)throw new IllegalArgumentException("Sign projection text structure");
    }
    private static Component publicText(Component input){
        MutableComponent[] result={null};int[] budget={0,0};input.<Void>visit((style,text)->{if(text.isEmpty())return Optional.empty();budget[0]=Math.addExact(budget[0],text.length());if(++budget[1]>256||budget[0]>8192)throw new IllegalArgumentException("Sign public text limit");var piece=Component.literal(text).setStyle(style.withClickEvent(null).withHoverEvent(null).withInsertion(null));if(result[0]==null)result[0]=piece;else result[0].append(piece);return Optional.empty();},Style.EMPTY);return result[0]==null?Component.empty():result[0];
    }
    public CompoundTag write(){var tag=encode();bounded(tag);return tag;}
    private CompoundTag encode(){
        var tag=new CompoundTag();tag.putInt("Schema",1);tag.putUUID("Identity",identity);tag.putDouble("CapturePhase",phase);tag.putBoolean("Materialized",materialized);tag.put("Reference",reference.write());
        var states=new LinkedHashMap<BlockState,Integer>();var shapes=new LinkedHashMap<List<AABB>,Integer>();var projections=new LinkedHashMap<CompoundTag,Integer>();long[] packed=new long[cells.size()];
        for(int i=0;i<cells.size();i++){var cell=cells.get(i);int state=states.computeIfAbsent(cell.state,k->states.size()),shape=shapes.computeIfAbsent(cell.boxes,k->shapes.size()),projection=projections.computeIfAbsent(cell.projection,k->projections.size());packed[i]=(cell.source.getX()+64L)|((cell.source.getY()+64L)<<8)|((cell.source.getZ()+64L)<<16)|((long)state<<24)|((long)shape<<37)|((long)projection<<50);}
        var stateTags=new ListTag();for(var state:states.keySet())stateTags.add(NbtUtils.writeBlockState(state));tag.put("States",stateTags);
        var shapeTags=new ListTag();for(var boxes:shapes.keySet()){var group=new ListTag();for(var box:boxes){var list=new ListTag();for(double value:new double[]{box.minX,box.minY,box.minZ,box.maxX,box.maxY,box.maxZ})list.add(DoubleTag.valueOf(value));group.add(list);}shapeTags.add(group);}tag.put("Shapes",shapeTags);
        var projectionTags=new ListTag();for(var projection:projections.keySet())projectionTags.add(projection.copy());tag.put("Projections",projectionTags);tag.putLongArray("Cells",packed);return tag;
    }
    public static NativeGearPayloadView read(CompoundTag tag,HolderLookup.Provider registries){
        Objects.requireNonNull(tag);Objects.requireNonNull(registries);bounded(tag);
        if(!tag.getAllKeys().equals(ROOT_KEYS)||!tag.contains("Schema",Tag.TAG_INT)||tag.getInt("Schema")!=1||!tag.hasUUID("Identity")||!tag.contains("CapturePhase",Tag.TAG_DOUBLE)||!tag.contains("Materialized",Tag.TAG_BYTE)||tag.getByte("Materialized")<0||tag.getByte("Materialized")>1||!(tag.get("Cells") instanceof LongArrayTag array)||array.size()<1||array.size()>MAX_CELLS)throw new IllegalArgumentException("Malformed arm public view");
        var stateTags=palette(tag,"States",Tag.TAG_COMPOUND,array.size());var shapeTags=palette(tag,"Shapes",Tag.TAG_LIST,array.size());var projectionTags=palette(tag,"Projections",Tag.TAG_COMPOUND,array.size());
        var states=new ArrayList<BlockState>(stateTags.size());for(var raw:stateTags){var state=NativeGearSnapshot.decode(registries,(CompoundTag)raw);if(state.isAir())throw new IllegalArgumentException("Empty arm public state");states.add(state);}
        var shapes=new ArrayList<List<AABB>>(shapeTags.size());int paletteBoxes=0;
        for(var raw:shapeTags){var group=(ListTag)raw;if(group.isEmpty()||group.size()>128||group.getElementType()!=Tag.TAG_LIST)throw new IllegalArgumentException("Arm view shape schema");paletteBoxes=Math.addExact(paletteBoxes,group.size());if(paletteBoxes>MAX_BOXES)throw new IllegalArgumentException("Arm view shape palette limit");var boxes=new ArrayList<AABB>(group.size());for(var entry:group){var coordinates=(ListTag)entry;if(coordinates.size()!=6||coordinates.getElementType()!=Tag.TAG_DOUBLE)throw new IllegalArgumentException("Arm view cuboid schema");double[] c=new double[6];for(int i=0;i<6;i++)c[i]=coordinates.getDouble(i);if(!Double.isFinite(c[0])||!Double.isFinite(c[1])||!Double.isFinite(c[2])||!Double.isFinite(c[3])||!Double.isFinite(c[4])||!Double.isFinite(c[5])||c[0]>=c[3]||c[1]>=c[4]||c[2]>=c[5])throw new IllegalArgumentException("Invalid arm view cuboid");var box=new AABB(c[0],c[1],c[2],c[3],c[4],c[5]);validateBox(box);boxes.add(box);}shapes.add(List.copyOf(boxes));}
        var projections=new ArrayList<CompoundTag>(projectionTags.size());int[] projectionBytes=new int[projectionTags.size()];for(int i=0;i<projectionTags.size();i++){var projection=projectionTags.getCompound(i).copy();projections.add(projection);projectionBytes[i]=projection.sizeInBytes();}
        long[] packed=array.getAsLongArray();long budget=512;int expanded=0;
        for(long value:packed){int x=(int)(value&255)-64,y=(int)((value>>>8)&255)-64,z=(int)((value>>>16)&255)-64;coordinate(x);coordinate(y);coordinate(z);int state=(int)((value>>>24)&8191),shape=(int)((value>>>37)&8191),projection=(int)((value>>>50)&8191);if(value<0||state>=states.size()||shape>=shapes.size()||projection>=projections.size())throw new IllegalArgumentException("Arm view palette index");expanded=Math.addExact(expanded,shapes.get(shape).size());if(expanded>MAX_BOXES)throw new IllegalArgumentException("Arm view expanded collision limit");budget=weight(budget,shapes.get(shape).size(),projectionBytes[projection]);}
        var result=new ArrayList<Cell>(packed.length);Set<Long> checked=new HashSet<>();
        for(long value:packed){int state=(int)((value>>>24)&8191),shape=(int)((value>>>37)&8191),projection=(int)((value>>>50)&8191);long pair=((long)state<<13)|projection;if(checked.add(pair)&&!projection(states.get(state),projections.get(projection),registries).equals(projections.get(projection)))throw new IllegalArgumentException("Nonpublic or malformed arm appearance");result.add(new Cell(new BlockPos((int)(value&255)-64,(int)((value>>>8)&255)-64,(int)((value>>>16)&255)-64),states.get(state),projections.get(projection),shapes.get(shape)));}
        var view=new NativeGearPayloadView(tag.getUUID("Identity"),tag.getDouble("CapturePhase"),tag.getBoolean("Materialized"),NativeGearPayload.Transform.read(tag.get("Reference")),result);if(!view.encode().equals(tag))throw new IllegalArgumentException("Noncanonical arm view palettes");return view;
    }
    private static ListTag palette(CompoundTag root,String key,int type,int cells){if(!(root.get(key) instanceof ListTag list)||list.isEmpty()||list.size()>cells||list.getElementType()!=type)throw new IllegalArgumentException("Arm view palette schema");return list;}
    private static void bounded(CompoundTag tag){
        int bytes=tag.sizeInBytes();if(bytes<0||bytes>MAX_BYTES)throw new IllegalArgumentException("Arm public view byte limit");
        try{NbtIo.write(tag,new DataOutputStream(new OutputStream(){int count;private void add(int amount)throws IOException{if(amount<0||amount>MAX_BYTES-count)throw new IOException("Arm public view wire limit");count+=amount;}@Override public void write(int value)throws IOException{add(1);}@Override public void write(byte[] bytes,int offset,int length)throws IOException{add(length);}}));}catch(IOException failure){throw new IllegalArgumentException("Arm public view encoded byte limit",failure);}
    }
}
