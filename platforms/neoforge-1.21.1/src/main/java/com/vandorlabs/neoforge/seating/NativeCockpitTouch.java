package com.vandorlabs.neoforge.seating;

import com.google.gson.JsonParser;
import com.vandorlabs.neoforge.redstone.NativeChannelMember;
import com.vandorlabs.neoforge.redstone.NativeRedstoneChannels;
import com.vandorlabs.neoforge.screen.NativeScreenControlService;
import com.vandorlabs.neoforge.screen.NativeScreenControls;
import com.vandorlabs.neoforge.screen.NativeScreenPickerPolicy;
import com.vandorlabs.neoforge.screen.NativeScreenRowEdit;
import com.vandorlabs.neoforge.screen.NativeYokeGeometry;
import com.vandorlabs.redstone.ChannelList;
import com.vandorlabs.redstone.TransmissionSettings;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/** Authored cockpit control surfaces, with an independent row bank and receiver per face. */
public final class NativeCockpitTouch implements NativeScreenControlService.Host {
    public static final int TOUCH=0,CENTER=1;
    private record Surface(String id,String label,boolean monitor) {}
    private static final Surface[] COCKPIT = {
        new Surface("touch_right_keyboard","Right arm keyboard",false),
        new Surface("screen_center","Center monitor",true),
        new Surface("screen_left","Left monitor",true),
        new Surface("screen_right","Right monitor",true),
        new Surface("touch_left_forward","Left touch panel",false),
        new Surface("touch_center_keyboard","Center keyboard",false),
        new Surface("touch_right_forward","Right touch panel",false)
    };
    private static final Surface CHAIR = new Surface("touch_right_single","Right touch panel",false);
    public static final int MAX_SURFACES = COCKPIT.length;
    private static final double APRON=10.5/16;
    private static final List<Map<Direction,NativeYokeGeometry.Face>> FACES=faces("cockpit",COCKPIT);
    private static final Map<Direction,NativeYokeGeometry.Face> CHAIR_FACES=faces("chair_siderests",CHAIR).getFirst();
    private final NativeChairEntity owner;
    private final NativeScreenControlService service;
    private final Surface[] surfaces;
    private final NativeScreenControls[] data;
    private final Receiver[] receivers;
    private final NativeCockpitSurfaceEdit[] settings;
    private boolean loaded;

    NativeCockpitTouch(NativeChairEntity owner){
        this.owner=owner;
        surfaces=owner.kind()==NativeSeatKind.COCKPIT?COCKPIT:new Surface[]{CHAIR};
        data=new NativeScreenControls[surfaces.length];
        receivers=new Receiver[surfaces.length];
        settings=new NativeCockpitSurfaceEdit[surfaces.length];
        for(int slot=0;slot<surfaces.length;slot++){
            settings[slot]=defaults(slot);
            receivers[slot]=new Receiver(slot);
            data[slot]=empty(slot);
        }
        service=new NativeScreenControlService(this);
    }

    private NativeCockpitSurfaceEdit defaults(int slot){
        int artwork=slot<=CENTER?NativeCockpitSurfaceEdit.REDSTONE:NativeCockpitSurfaceEdit.ORIGINAL;
        return new NativeCockpitSurfaceEdit(artwork,2,1,0,ChannelList.EMPTY);
    }

    private NativeScreenControls empty(int slot){return new NativeScreenControls(redstone(slot),"",List.of());}
    public NativeScreenControls controls(int slot){return data[slot];}
    public NativeCockpitSurfaceEdit settings(int slot){return settings[slot];}
    public boolean active(int slot){return receivers[slot].active;}
    public boolean monitor(int slot){return surfaces[slot].monitor;}
    public String label(int slot){return surfaces[slot].label;}
    private boolean redstone(int slot){return settings[slot].artwork()==NativeCockpitSurfaceEdit.REDSTONE;}
    @Override public int controlSlots(){return surfaces.length;}
    @Override public int rowCapacity(int slot){return supportsRowSlot(slot)?monitor(slot)?6:3:0;}
    @Override public boolean supportsRowSlot(int slot){return slot>=0&&slot<surfaces.length;}
    @Override public int rowTransmissionRange(){return TransmissionSettings.DEFAULT_RANGE;}
    @Override public BlockEntity channelBlockEntity(){return owner;}
    @Override public ChannelList getRedstoneChannels(){return ChannelList.EMPTY;}
    @Override public void setChannelLevel(int ignored){}
    @Override public boolean isChannelMemberActive(){return NativeScreenControlService.Host.super.isChannelMemberActive()&&!owner.inert();}

    @Override public void installControlData(int slot,NativeScreenControls next,boolean notify){
        data[slot]=next;
        owner.setChanged();
        if(notify) publish();
    }

    private void publish(){
        if(owner.getLevel() instanceof ServerLevel)
            owner.getLevel().sendBlockUpdated(owner.getBlockPos(),owner.getBlockState(),owner.getBlockState(),2);
    }

    void finishLoading(){
        if(!loaded&&owner.getLevel() instanceof ServerLevel&&isChannelMemberActive()){
            loaded=true;
            service.finishLoading();
            for(var receiver:receivers)NativeRedstoneChannels.queueRegistration(receiver);
        }
    }

    void stop(){
        loaded=false;
        service.stop();
        if(owner.getLevel() instanceof ServerLevel)
            for(var receiver:receivers)NativeRedstoneChannels.unregister(receiver);
    }

    private String rowKey(int slot){
        if(surfaces==COCKPIT&&slot<=CENTER) return slot==TOUCH?"NativeCockpitTouchRows":"NativeCockpitCenterRows";
        return "NativeSeatRows_"+surfaces[slot].id;
    }

    private String configKey(int slot){return "NativeSeatSurface_"+surfaces[slot].id;}

    private boolean validArtwork(int slot,int artwork){
        return artwork<0||NativeScreenPickerPolicy.selectable(artwork,!monitor(slot));
    }

    boolean read(CompoundTag tag,boolean fromClientUpdate){
        try{
            for(int slot=0;slot<surfaces.length;slot++){
                settings[slot]=defaults(slot);
                receivers[slot].active=true;
                String key=configKey(slot);
                if(tag.contains(key)){
                    if(!tag.contains(key,Tag.TAG_COMPOUND))return false;
                    var raw=tag.getCompound(key);
                    if(!raw.getAllKeys().equals(Set.of("Artwork","Playback","Speed","Activation","Channels","Active"))
                        ||!raw.contains("Channels",Tag.TAG_INT_ARRAY)||!raw.contains("Active",Tag.TAG_BYTE)
                        ||raw.getByte("Active")<0||raw.getByte("Active")>1)return false;
                    for(String field:List.of("Artwork","Playback","Speed","Activation"))if(!raw.contains(field,Tag.TAG_INT))return false;
                    var config=new NativeCockpitSurfaceEdit(raw.getInt("Artwork"),raw.getInt("Playback"),raw.getInt("Speed"),
                        raw.getInt("Activation"),ChannelList.of(raw.getIntArray("Channels")));
                    if(!validArtwork(slot,config.artwork()))return false;
                    settings[slot]=config;
                    receivers[slot].active=fromClientUpdate?raw.getBoolean("Active"):config.activation()!=1||config.channels().isEmpty();
                }
                key=rowKey(slot);
                if(!tag.contains(key)){data[slot]=empty(slot);continue;}
                if(!tag.contains(key,Tag.TAG_COMPOUND))return false;
                var raw=tag.getCompound(key);
                if(!(raw.get("Rows") instanceof net.minecraft.nbt.ListTag rows)
                    ||!rows.isEmpty()&&rows.getElementType()!=Tag.TAG_COMPOUND
                    ||rows.size()>rowCapacity(slot)||!NativeScreenControls.validConfiguration(raw))return false;
                var bank=NativeScreenControls.read(raw,rowCapacity(slot));
                data[slot]=new NativeScreenControls(redstone(slot),bank.title(),bank.rows());
            }
        }catch(IllegalArgumentException malformed){return false;}
        if(!fromClientUpdate)service.savedChanged();
        return true;
    }

    void write(CompoundTag tag,boolean item){
        for(int slot=0;slot<surfaces.length;slot++){
            var config=settings[slot];
            String key=configKey(slot);
            if(!config.equals(defaults(slot))||!active(slot)){
                var written=new CompoundTag();
                written.putInt("Artwork",config.artwork());
                written.putInt("Playback",config.playback());
                written.putInt("Speed",config.speed());
                written.putInt("Activation",config.activation());
                written.putIntArray("Channels",config.channels().toArray());
                written.putBoolean("Active",!item&&active(slot));
                tag.put(key,written);
            }else tag.remove(key);
            key=rowKey(slot);
            if((surfaces!=COCKPIT||slot>CENTER)&&data[slot].rows().isEmpty()&&data[slot].title().isEmpty()&&!tag.contains(key))
                continue;
            if(item)tag.put(key,data[slot].configuration());
            else{
                var saved=tag.getCompound(key).copy();
                data[slot].write(saved);
                tag.put(key,saved);
            }
        }
    }

    public boolean configure(int slot,NativeScreenRowEdit edit){
        finishLoading();
        return supportsRowSlot(slot)&&service.configure(slot,edit,redstone(slot));
    }
    public boolean configure(int slot,NativeCockpitSurfaceEdit edit){
        finishLoading();
        if(!supportsRowSlot(slot)||!loaded||!isChannelMemberActive()||!validArtwork(slot,edit.artwork()))return false;
        var previous=settings[slot];
        settings[slot]=edit;
        if(!service.setEnabled(slot,redstone(slot))){
            settings[slot]=previous;
            return false;
        }
        NativeRedstoneChannels.channelsChanged(receivers[slot]);
        owner.setChanged();
        publish();
        return true;
    }
    public boolean editing(){return service.editing();}
    boolean usesRow(int slot,int row){
        return redstone(slot)&&active(slot)&&settings[slot].playback()!=0&&row>=0&&row<data[slot].rows().size();
    }
    void use(int slot,int row,int segment){
        if(!usesRow(slot,row))return;
        finishLoading();
        if(data[slot].rows().get(row).slider())service.select(slot,row,segment);
        else service.toggle(slot,row);
    }

    private final class Receiver implements NativeChannelMember {
        final int slot;
        boolean active=true;
        Receiver(int slot){this.slot=slot;}
        @Override public BlockEntity channelBlockEntity(){return owner;}
        @Override public ChannelList getRedstoneChannels(){return settings[slot].channels();}
        @Override public boolean isChannelMemberActive(){return loaded&&NativeCockpitTouch.this.isChannelMemberActive();}
        @Override public void setChannelLevel(int signal){
            var config=settings[slot];
            boolean next=config.activation()==0||config.channels().isEmpty()||(signal>0)==(config.activation()==1);
            if(active!=next){
                active=next;
                publish();
            }
        }
    }

    public NativeYokeGeometry.Face surface(Direction facing,int slot,double lift){
        return surfaces==COCKPIT?face(facing,slot,lift):CHAIR_FACES.get(facing);
    }

    public static NativeYokeGeometry.Face face(Direction facing){return face(facing,TOUCH,0);}
    public static NativeYokeGeometry.Face face(Direction facing,int slot,double lift){
        var face=FACES.get(slot).get(facing);
        if(!COCKPIT[slot].monitor||lift==0)return face;
        var top=face.topLeft().add(0,-NativeCockpitLift.TRAVEL*lift,0);
        if(Math.max(top.y,top.y+face.right().y)<=APRON)return null;
        return new NativeYokeGeometry.Face(top,face.right(),face.down(),face.normal(),face.height());
    }

    public static int visiblePixels(NativeYokeGeometry.Face face,int slot){
        return !COCKPIT[slot].monitor?face.height():Math.min(face.height(),
            Math.max(0,(int)Math.floor(face.height()*(face.topLeft().y+Math.min(0,face.right().y)-APRON)/-face.down().y)));
    }

    public int visibleHeight(NativeYokeGeometry.Face face,int slot){return surfaces==COCKPIT?visiblePixels(face,slot):face.height();}

    record Pick(BlockHitResult hit,int slot,int row,int segment){}
    static Pick pick(BlockPos root,Direction facing,BlockPos clicked,Vec3 start,Vec3 end,int rows){
        return pick(root,clicked,start,end,face(facing),TOUCH,rows,false);
    }
    private static Pick pick(BlockPos root,BlockPos clicked,Vec3 start,Vec3 end,NativeYokeGeometry.Face face,
                             int slot,int rows,boolean monitor){
        if(face==null)return null;
        Vec3 origin=Vec3.atLowerCornerOf(root);
        var sample=face.sample(start.subtract(origin),end.subtract(origin),Math.max(1,rows));
        if(sample==null)return null;
        var point=start.lerp(end,sample.time());
        if(!BlockPos.containing(point).equals(clicked)||monitor&&point.y-root.getY()<APRON)return null;
        return new Pick(new BlockHitResult(point,Direction.UP,clicked,false),slot,rows==0?-1:sample.row(),sample.sliderStep());
    }

    Pick pick(BlockPos clicked,Vec3 start,Vec3 end){
        var root=owner.getBlockPos();
        var facing=owner.getBlockState().getValue(NativeSeatBlock.FACING);
        double lift=surfaces==COCKPIT?owner.liftPosition():0;
        Pick nearest=null;
        for(int slot=0;slot<surfaces.length;slot++){
            var hit=pick(root,clicked,start,end,surface(facing,slot,lift),slot,data[slot].rows().size(),monitor(slot));
            if(hit!=null&&(nearest==null||hit.hit().getLocation().distanceToSqr(start)<nearest.hit().getLocation().distanceToSqr(start)))
                nearest=hit;
        }
        return nearest;
    }

    private static List<Map<Direction,NativeYokeGeometry.Face>> faces(String design,Surface... surfaces){
        String path="/assets/vandorlabs/cockpits/"+design+"/surfaces.json";
        try(var stream=NativeCockpitTouch.class.getResourceAsStream(path)){
            if(stream==null)throw new IllegalStateException("Missing authored surfaces for "+design);
            var list=JsonParser.parseReader(new InputStreamReader(stream,StandardCharsets.UTF_8))
                .getAsJsonObject().getAsJsonArray("surfaces");
            var result=new java.util.ArrayList<Map<Direction,NativeYokeGeometry.Face>>();
            for(var spec:surfaces){
                var byFacing=new EnumMap<Direction,NativeYokeGeometry.Face>(Direction.class);
                for(var entry:list){
                    var face=entry.getAsJsonObject();
                    if(!face.get("id").getAsString().equals(spec.id))continue;
                    for(var direction:Direction.Plane.HORIZONTAL)byFacing.put(direction,NativeYokeGeometry.authoredFace(face,direction));
                    break;
                }
                if(byFacing.isEmpty())throw new IllegalStateException("Missing authored surface "+spec.id);
                result.add(Map.copyOf(byFacing));
            }
            return List.copyOf(result);
        }catch(java.io.IOException failure){throw new ExceptionInInitializerError(failure);}
    }
}
