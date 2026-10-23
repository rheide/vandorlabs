package com.vandorlabs.tiles;

import com.vandorlabs.cockpit.CockpitData;
import com.vandorlabs.redstone.*;
import com.vandorlabs.vehicle.VehicleWorld;
import net.minecraft.nbt.*;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.*;
import java.util.*;

/** One persistent controller owns every display and independent touch/throttle output. */
public class TileEntityCockpit extends TileEntityVehicleController {
    private String design="cockpit";
    private int sideTexture=-1;
    private int throttleCount=2;
    public int throttleCount(){return throttleCount;}
    public String modelId(){return design.equals("cockpit")&&throttleCount==1?"cockpit_single":design;}
    public void setThrottleCount(int count){if(!design.equals("cockpit")||count<1||count>2||count==throttleCount)return;throttleCount=count;if(ordinary())for(Control c:controls)RedstoneChannels.inputChanged(c);changed();}

    public final com.vandorlabs.cockpit.CockpitActuator screenLift=new com.vandorlabs.cockpit.CockpitActuator(),seatSlide=new com.vandorlabs.cockpit.CockpitActuator();
    private long screenButtonUntil,seatButtonUntil;

    private boolean vehicleMotionWasMoving;
    public boolean motionCompleted(){boolean moving=screenLift.moving(world.getTotalWorldTime())||seatSlide.moving(world.getTotalWorldTime());boolean completed=vehicleMotionWasMoving&&!moving;vehicleMotionWasMoving=moving;return completed;}
    public String seatBlockReason="";
    private double mechanism(boolean screens,float partial){
        if(!design.equals("cockpit")||world==null)return 0;
        if(controlClient()&&com.vandorlabs.VandorLabs.proxy!=null)return com.vandorlabs.VandorLabs.proxy.cockpitMotion(this,screens,partial);
        return (screens?screenLift:seatSlide).sample(world.getTotalWorldTime()+partial);
    }
    public double screens(float partial){return mechanism(true,partial);}
    public double seatSlide(float partial){return mechanism(false,partial);}
    public Vec3d seatLocation(){return seatLocation(0);}
    public Vec3d seatLocation(float partial){return data().seat==null?null:data().seat.addVector(0,0,seatSlide(partial));}
    public double visualDetent(String id,float partial){
        if(id.equals("screen_lift_button")||id.equals("seat_slide_button")){double remaining=(id.equals("screen_lift_button")?screenButtonUntil:seatButtonUntil)-(world.getTotalWorldTime()+partial);return remaining>0?Math.min(1,remaining/4)*3:0;}
        if(id.equals("seat_assembly"))return seatSlide(partial)*3;
        if(id.equals("screen_bank"))return screens(partial)*3;
        Control c=control(id);return c==null?0:c.isThrottle()?c.step():c.level/5D;
    }
    public AxisAlignedBB posedBox(AxisAlignedBB box){int part=data().moving.getOrDefault(box,0);return part==1?box.offset(0,0,seatSlide(0)):part==2?box.offset(0,-.75*screens(0),0):box;}
    private boolean controlClient(){return world==null||(world instanceof VehicleWorld?((VehicleWorld)world).parent.isRemote:world.isRemote);}
    public boolean toggleSeat(net.minecraft.entity.player.EntityPlayer actor){
        seatBlockReason="";if(!design.equals("cockpit")||controlClient())return false;
        if(!com.vandorlabs.cockpit.CockpitReservations.clear(this,actor,seatSlide.target()))return false;
        NBTTagCompound before=writeToNBT(new NBTTagCompound());
        seatSlide.toggle(world.getTotalWorldTime());seatButtonUntil=world.getTotalWorldTime()+8;
        try{com.vandorlabs.cockpit.CockpitReservations.claim(this);if(!(world instanceof VehicleWorld)||((VehicleWorld)world).craft.view.getTileEntity(pos)==this)changed();return true;}catch(IllegalArgumentException blocked){seatBlockReason=blocked.getMessage();readFromNBT(before);changed();if(actor!=null)actor.sendStatusMessage(new net.minecraft.util.text.TextComponentString(blocked.getMessage()),true);return false;}
    }
    public void toggleScreens(){if(design.equals("cockpit")&&!controlClient()){screenLift.toggle(world.getTotalWorldTime());screenButtonUntil=world.getTotalWorldTime()+8;if(world instanceof VehicleWorld)com.vandorlabs.vehicle.VehicleCockpits.refreshGeometry(((VehicleWorld)world).craft);changed();}}

    public int sideTexture(){return sideTexture;}
    public void setSideTexture(int value){
        if(value< -1 || value>=ScreenHousingTextures.IDS.length)return;
        sideTexture=value;changed();
    }
    public final List<Display> displays=new ArrayList<>();
    public final List<Control> controls=new ArrayList<>();
    public TileEntityCockpit(){initializeControls();}
    public TileEntityCockpit(String design){this();this.design=design;prepare();}
    public String design(){return design;}
    public CockpitData data(){return CockpitData.get(modelId(),getHeight());}
    private EnumFacing lastFacing=EnumFacing.NORTH;
    /** Removal and replacement can precede the renderer's final queued bounds query. */
    public boolean hasLiveBlock(){
        if(world==null || isInvalid() || !world.isBlockLoaded(pos) || world.getTileEntity(pos)!=this)return false;
        net.minecraft.block.Block block=world.getBlockState(pos).getBlock();
        return block instanceof com.vandorlabs.blocks.BlockCockpit && design.equals(((com.vandorlabs.blocks.BlockCockpit)block).design)
            || block instanceof com.vandorlabs.blocks.BlockCockpitPeripheral && design.equals(((com.vandorlabs.blocks.BlockCockpitPeripheral)block).design);
    }
    public EnumFacing facing(){
        if(hasLiveBlock())lastFacing=world.getBlockState(pos).getValue(com.vandorlabs.blocks.BlockVandorDirectional.FACING);
        return lastFacing;
    }
    public double seatHeight(){return data().riderY+.725;}
    private void prepare(){
        if(!displays.isEmpty())return;
        for(CockpitData.Surface surface:data().surfaces){
            Display display=new Display(surface.id,surface.role);
            display.texture=defaultTexture(surface);
            displays.add(display);
            for(int row=0;row<3;row++){Control c=new Control(surface.id+"/"+row);controls.add(c);}
        }
        for(String part:CockpitData.get(design,getHeight()).controls){Control c=new Control(part);c.kind=1;controls.add(c);}
    }
    private int defaultTexture(CockpitData.Surface surface){
        if("touch".equals(surface.role))return ScreenHousingTextures.screenIndex("console_inputs/"+(surface.id.equals("touch_left_forward")?"console_controls_navigation_controls_static":surface.id.equals("touch_right_forward")?"console_controls_sensor_controls_static":surface.id.equals("touch_center_keyboard")?"extra_bare_personal_terminal_static":"console_controls_communications_keyboard_static"));
        return design.equals("yoke_screen")||surface.id.equals("screen_center")?TileEntityAnimatedScreenSelector.VEHICLE_SURFACE:ScreenHousingTextures.screenIndex(surface.id.equals("screen_left")?"bridge_bare_pilot_static":"bridge_bare_engineering_static");
    }
    public boolean configureRows(int index,NBTTagList rows){
        if(index<0||index>=displays.size()||rows.tagCount()>3)return false;
        for(int i=0;i<rows.tagCount();i++){NBTTagCompound row=rows.getCompoundTagAt(i);int kind=row.getInteger("Kind"),min=row.getInteger("Min"),max=row.getInteger("Max");if(row.getString("Label").length()>24||kind<0||kind>2||min<0||min>13||max<min+2||max>15)return false;ChannelData.read(row,0);}
        Display d=displays.get(index);d.rows=rows.tagCount();
        for(int i=0;i<3;i++){Control c=control(d.id+"/"+i);NBTTagCompound row=i<d.rows?rows.getCompoundTagAt(i):new NBTTagCompound();c.kind=row.getInteger("Kind");c.label=row.getString("Label");c.min=i<d.rows?row.getInteger("Min"):5;c.max=i<d.rows?row.getInteger("Max"):15;c.level=0;c.until=0;c.setRedstoneChannels(i<d.rows?ChannelData.read(row,0):ChannelList.EMPTY);if(ordinary())RedstoneChannels.inputChanged(c);}
        d.texture=TileEntityAnimatedScreenSelector.REDSTONE_SURFACE;changed();return true;
    }
    public Control control(String id){prepare();for(Control c:controls)if(c.id.equals(id))return c;return null;}
    public Display display(String id){prepare();for(Display d:displays)if(d.id.equals(id))return d;return null;}
    public void changed(){markDirty();if(world!=null)world.notifyBlockUpdate(pos,world.getBlockState(pos),world.getBlockState(pos),3);}
    private boolean ordinary(){return world!=null && !world.isRemote && !(world instanceof VehicleWorld);}
    @Override public void onLoad(){super.onLoad();prepare();if(ordinary())DeferredTileLoad.schedule(this,()->{for(Control c:controls)RedstoneChannels.register(c);});}
    @Override public void invalidate(){if(ordinary())for(Control c:controls)RedstoneChannels.unregister(c);super.invalidate();}
    @Override public void onChunkUnload(){if(ordinary())for(Control c:controls)RedstoneChannels.unregister(c);super.onChunkUnload();}
    public boolean expire(long now){boolean changed=false;for(Control c:controls)if(c.until>0 && now>=c.until){c.until=0;c.level=0;if(ordinary())RedstoneChannels.inputChanged(c);changed=true;}if(changed)changed();return changed;}
    public void updatePower(){boolean dirty=false;for(Display d:displays){boolean active=d.gate==0 || (RedstoneChannels.level(world,d.gate)>0)!=d.inverted;if(active!=d.active){d.active=active;dirty=true;}}if(dirty)changed();}
    @Override public void update(){prepare();if(world==null || world.isRemote)return;expire(world.getTotalWorldTime());updatePower();if(design.equals("cockpit")){com.vandorlabs.cockpit.CockpitReservations.tick(this);}if(data().seat!=null)super.update();}
    public boolean use(Vec3d start,Vec3d end){return use(start,end,null);}
    public boolean use(Vec3d start,Vec3d end,net.minecraft.entity.player.EntityPlayer actor){
        prepare();start=CockpitData.unrotate(start,facing());end=CockpitData.unrotate(end,facing());
        if(design.equals("cockpit")){
            Vec3d buttonStart=start.addVector(0,0,-seatSlide(0)),buttonEnd=end.addVector(0,0,-seatSlide(0));
            if(new AxisAlignedBB(26.35/16,10.45/16,1.3/16,28.65/16,11.0/16,2.8/16).calculateIntercept(buttonStart,buttonEnd)!=null){toggleScreens();return true;}
            if(new AxisAlignedBB(2.8/16,10.45/16,9.6/16,5.1/16,11.0/16,11.1/16).calculateIntercept(buttonStart,buttonEnd)!=null){toggleSeat(actor);return true;}
        }
        CockpitData.Surface nearest=null;double[] uv=null;
        for(CockpitData.Surface surface:data().surfaces)if(display(surface.id).active && ("touch".equals(surface.role)&&display(surface.id).texture>=0||display(surface.id).texture<=TileEntityAnimatedScreenSelector.REDSTONE_SURFACE)){double offset="screen".equals(surface.role)?-.75*screens(0):0;double slide=design.equals("cockpit")&&surface.id.equals("touch_right_keyboard")?seatSlide(0):0;double[] hit=surface.hit(start.addVector(0,-offset,-slide),end.addVector(0,-offset,-slide));if(hit!=null&&"screen".equals(surface.role)&&surface.origin.y+surface.u.y*hit[0]+surface.v.y*hit[1]+offset<10.5/16)hit=null;if(hit!=null && (uv==null || hit[2]<uv[2])){nearest=surface;uv=hit;}}
        ControlHit lever=controlHitLocal(start,end);
        if(lever!=null&&(uv==null||lever.distance<start.squareDistanceTo(start.add(end.subtract(start).scale(uv[2]))))){Control c=control(lever.id);c.activate(c.nextColumn());return true;}
        if(nearest!=null){if(display(nearest.id).texture==TileEntityAnimatedScreenSelector.VEHICLE_SURFACE){int x=(int)(uv[0]*128),y=(int)((1-uv[1])*("touch".equals(nearest.role)?64:128)),top="touch".equals(nearest.role)?0:24;int action=y>=top&&y<top+10?0:y>=top+12&&y<top+22?1+Math.min(2,Math.max(0,(x-6)/39)):y>=top+36&&y<top+46&&x<48?4:-1;if(action>=0)com.vandorlabs.vehicle.VehicleStatus.activate(world,pos,actor,action);return true;}com.vandorlabs.cockpit.CockpitScreenLayout layout=new com.vandorlabs.cockpit.CockpitScreenLayout(nearest,display(nearest.id).rows);if(layout.rows==0)return true;Control c=control(nearest.id+"/"+layout.row(uv[1]));c.activate(c.kind==1?layout.sliderColumn(uv[0]):uv[0]);return true;}
        return false;
    }
    /** Cosmetic dialog navigation uses the same posed screen planes and grip rays as interaction. */
    public int configurationPage(Vec3d start,Vec3d end){
        prepare();start=CockpitData.unrotate(start,facing());end=CockpitData.unrotate(end,facing());
        int base=(data().seat==null?0:1)+1,index=-1;double distance=Double.POSITIVE_INFINITY;
        for(int i=0;i<data().surfaces.size();i++){
            CockpitData.Surface surface=data().surfaces.get(i);double lift="screen".equals(surface.role)?-.75*screens(0):0,slide=design.equals("cockpit")&&surface.id.equals("touch_right_keyboard")?seatSlide(0):0;
            double[] hit=surface.hit(start.addVector(0,-lift,-slide),end.addVector(0,-lift,-slide));
            if(hit!=null&&("screen".equals(surface.role)&&surface.origin.y+surface.u.y*hit[0]+surface.v.y*hit[1]+lift<10.5/16))continue;
            if(hit!=null){double d=start.squareDistanceTo(start.add(end.subtract(start).scale(hit[2])));if(d<distance){distance=d;index=i;}}
        }
        ControlHit grip=controlHitLocal(start,end);
        if(grip!=null&&grip.distance<distance){for(int i=0;i<controls.size();i++)if(controls.get(i).id.equals(grip.id))return base+displays.size()+i;}
        return index<0?0:base+index;
    }
    private static final class ControlHit {String id;Vec3d point;double distance;ControlHit(String id,Vec3d point,double distance){this.id=id;this.point=point;this.distance=distance;}}
    private ControlHit controlHitLocal(Vec3d start,Vec3d end){
        ControlHit nearest=null;
        for(Map.Entry<String,Vec3d> part:data().pivots.entrySet()){
            boolean throttle=part.getKey().startsWith("throttle_");
            Vec3d p=part.getValue().addVector(0,0,seatSlide(0)-(throttle?0:control(part.getKey()).level/5D/16)),a=start.subtract(p),b=end.subtract(p);
            double angle=throttle?Math.toRadians(22.5-control(part.getKey()).step()*22.5):0,c=Math.cos(angle),s=Math.sin(angle);
            if(throttle){a=new Vec3d(a.x,a.y*c+a.z*s,a.z*c-a.y*s);b=new Vec3d(b.x,b.y*c+b.z*s,b.z*c-b.y*s);}
            AxisAlignedBB bounds=throttle?new AxisAlignedBB(-.075,-.025,-.078,.075,.335,.078):new AxisAlignedBB(-.04,-.03,-.047,.04,.15,.047);RayTraceResult hit=bounds.calculateIntercept(a,b);
            if(hit!=null){Vec3d v=hit.hitVec;Vec3d point=(throttle?new Vec3d(v.x,v.y*c-v.z*s,v.y*s+v.z*c):v).add(p);double distance=start.squareDistanceTo(point);if(nearest==null||distance<nearest.distance)nearest=new ControlHit(part.getKey(),point,distance);}
        }
        return nearest;
    }
    /** Actual grip targeting is shared with activation, including from a seated eye. */
    public RayTraceResult controlRay(Vec3d start,Vec3d end){ControlHit hit=controlHitLocal(CockpitData.unrotate(start,facing()),CockpitData.unrotate(end,facing()));return hit==null?null:new RayTraceResult(CockpitData.rotate(hit.point,facing()),EnumFacing.UP,pos);}
    @Override public NBTTagCompound writeToNBT(NBTTagCompound tag){
        super.writeToNBT(tag);screenLift.write(tag,"CockpitScreenLift");seatSlide.write(tag,"CockpitSeatSlide");tag.setLong("CockpitScreenButton",screenButtonUntil);tag.setLong("CockpitSeatButton",seatButtonUntil);tag.setString("CockpitDesign",design);tag.setInteger("CockpitThrottles",throttleCount);tag.setInteger("CockpitSideTexture",sideTexture);prepare();
        NBTTagList screens=new NBTTagList();for(Display d:displays)screens.appendTag(d.write());tag.setTag("CockpitDisplays",screens);
        NBTTagList outputs=new NBTTagList();for(Control c:controls)outputs.appendTag(c.write());tag.setTag("CockpitControls",outputs);return tag;
    }
    @Override public void readFromNBT(NBTTagCompound tag){
        if(ordinary())for(Control c:controls)RedstoneChannels.unregister(c);
        super.readFromNBT(tag);design=tag.hasKey("CockpitDesign",8)?tag.getString("CockpitDesign"):design;CockpitData.get(design);
        screenLift.read(tag,"CockpitScreenLift");seatSlide.read(tag,"CockpitSeatSlide");screenButtonUntil=tag.getLong("CockpitScreenButton");seatButtonUntil=tag.getLong("CockpitSeatButton");
        sideTexture=tag.hasKey("CockpitSideTexture",3)?tag.getInteger("CockpitSideTexture"):-1;
        if(sideTexture< -1 || sideTexture>=ScreenHousingTextures.IDS.length)sideTexture=-1;
        throttleCount=tag.hasKey("CockpitThrottles",3)&&tag.getInteger("CockpitThrottles")==1?1:2;
        displays.clear();controls.clear();prepare();
        NBTTagList screens=tag.getTagList("CockpitDisplays",10);for(int i=0;i<Math.min(32,screens.tagCount());i++){NBTTagCompound saved=screens.getCompoundTagAt(i);Display d=display(saved.getString("Id"));if(d!=null)d.read(saved);}
        NBTTagList outputs=tag.getTagList("CockpitControls",10);for(int i=0;i<Math.min(64,outputs.tagCount());i++){NBTTagCompound saved=outputs.getCompoundTagAt(i);Control c=control(saved.getString("Id"));if(c!=null)c.read(saved);}
        initializeControls();if(ordinary() && world.getTileEntity(pos)==this)for(Control c:controls)RedstoneChannels.register(c);
    }
    @Override public AxisAlignedBB getRenderBoundingBox(){return hasLiveBlock()?CockpitData.rotate(data().bounds,facing()).offset(pos).grow(.05):new AxisAlignedBB(pos);}
    public final class Control implements RedstoneChannelMember {
        public final String id;
        public String label="";
        public ChannelList channels=ChannelList.EMPTY;
        public int kind,level,min=5,max=15;
        public long until;
        Control(String id){this.id=id;}
        public TileEntity channelTile(){return TileEntityCockpit.this;}
        public int getRedstoneChannel(){return channels.first();}
        public ChannelList getRedstoneChannels(){return channels;}
        public void setRedstoneChannel(int channel){setRedstoneChannels(ChannelList.of(channel));}
        public void setRedstoneChannels(ChannelList next){ChannelList before=channels;channels=next;if(ordinary())RedstoneChannels.channelChanged(this,before);changed();}
        public boolean active(){return !id.equals("throttle_right")||throttleCount==2;}
        public boolean hasLocalRedstoneSignal(){return active()&&level>0;}
        public int localSignalLevel(int channel){return active()?level:0;}
        public void setChannelSignal(boolean ignored){}
        public boolean isThrottle(){return id.startsWith("throttle_");}
        public int step(){return com.vandorlabs.tiles.TileEntitySignalControl.stepForLevel(level,min,max);}
        public int sliderLevel(int segment){if(isThrottle())return TileEntitySignalControl.levelForStep(segment,min,max);return segment==0?0:min+(max-min)*(Math.max(1,Math.min(3,segment))-1)/2;}
        public double nextColumn(){int segment=0;for(int i=1;i<4;i++)if(Math.abs(sliderLevel(i)-level)<Math.abs(sliderLevel(segment)-level))segment=i;return ((segment+1)%4)/3D;}
        public void activate(double column){
            level=(kind==1||isThrottle())?sliderLevel(Math.max(0,Math.min(3,(int)Math.round(column*3)))):kind==2?15:level==0?15:0;
            until=kind==2?world.getTotalWorldTime()+20:0;if(ordinary())RedstoneChannels.inputChanged(this);changed();
        }
        public NBTTagCompound write(){NBTTagCompound tag=new NBTTagCompound();tag.setString("Id",id);tag.setString("Label",label);ChannelData.write(tag,channels);tag.setInteger("Min",min);tag.setInteger("Max",max);tag.setInteger("Kind",kind);tag.setInteger("Level",level);tag.setLong("Until",until);return tag;}
        public void read(NBTTagCompound tag){label=tag.getString("Label");if(label.length()>32)label=label.substring(0,32);channels=ChannelData.read(tag,0);min=tag.hasKey("Min",3)?Math.max(0,Math.min(13,tag.getInteger("Min"))):5;max=tag.hasKey("Max",3)?Math.max(min+2,Math.min(15,tag.getInteger("Max"))):15;kind=Math.max(0,Math.min(2,tag.getInteger("Kind")));level=Math.max(0,Math.min(15,tag.getInteger("Level")));until=Math.max(0,tag.getLong("Until"));if(isThrottle()){kind=1;until=0;if(!TileEntitySignalControl.validLimits(min,max)){min=5;max=15;}}}
    }
    public static final class Display {
        public final String id,role;
        public int texture=-1,speed=1,gate,rows=3;
        public boolean animated=true,inverted,active=true;
        Display(String id,String role){this.id=id;this.role=role;}
        public NBTTagCompound write(){NBTTagCompound tag=new NBTTagCompound();tag.setString("Id",id);tag.setInteger("Texture",texture);tag.setInteger("Rows",rows);tag.setInteger("Speed",speed);tag.setInteger("Gate",gate);tag.setBoolean("Animated",animated);tag.setBoolean("Inverted",inverted);tag.setBoolean("Active",active);return tag;}
        public void read(NBTTagCompound tag){rows=tag.hasKey("Rows",3)?Math.max(0,Math.min(3,tag.getInteger("Rows"))):3;texture=tag.getInteger("Texture");if(texture<TileEntityAnimatedScreenSelector.VEHICLE_SURFACE || texture>=ScreenHousingTextures.IDS.length)texture=-1;speed=com.vandorlabs.animation.ScreenBehavior.clampSpeedIndex(tag.hasKey("Speed",3)?tag.getInteger("Speed"):1);gate=Math.max(0,Math.min(65535,tag.getInteger("Gate")));animated=tag.getBoolean("Animated");inverted=tag.getBoolean("Inverted");active=gate==0 || tag.getBoolean("Active");}
    }
}
