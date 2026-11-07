package com.vandorlabs.neoforge.screen;
import com.vandorlabs.redstone.ChannelList;
import com.vandorlabs.persistence.SignalControlData;
import com.vandorlabs.neoforge.redstone.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import java.util.*;
/** Loaded standalone row sources. Cosmetic rendering never polls or serializes these mutable authorities. */
@EventBusSubscriber(modid="vandorlabs")
public final class NativeScreenControlService {
 private static final Map<ServerLevel,Set<NativeScreenControlService>> ACTIVE=new IdentityHashMap<>();
 private static final Set<ServerLevel> CLOSED=Collections.newSetFromMap(new WeakHashMap<>());
 private final NativeScreenEntity owner;private final List<List<Row>> rows=new ArrayList<>(2);private final NativeScreenControls[] headers=new NativeScreenControls[2];private final Set<Row> registered=Collections.newSetFromMap(new IdentityHashMap<>());private boolean initialized,configuring,editing;private int pendingSlots;
 NativeScreenControlService(NativeScreenEntity owner){this.owner=owner;for(int slot=0;slot<2;slot++){headers[slot]=owner.controls(slot);rows.add(readRows(slot,headers[slot]));}}
 private List<Row> readRows(int slot,NativeScreenControls data){List<Row> result=new ArrayList<>();for(var row:data.rows())result.add(new Row(slot,row));return result;}
 private ServerLevel level(){if(!(owner.getLevel() instanceof ServerLevel world))return null;if(!world.getServer().isSameThread())throw new IllegalStateException("Screen controls require server thread");return CLOSED.contains(world)||!NativeRedstoneChannels.open(world)?null:world;}
 private boolean live(){return level()!=null&&owner.isChannelMemberActive();}
 void savedChanged(){boolean resume=initialized;List<Row> old=registeredRows();for(int slot=0;slot<2;slot++){headers[slot]=owner.controls(slot);rows.set(slot,readRows(slot,headers[slot]));}if(resume&&live())mutate(()->{registerCurrent();withdraw(old);});else{withdraw(old);initialized=false;}if(resume&&live())owner.reconcileControlOutput();arm();}
 void finishLoading(){if(!live())return;initialized=true;mutate(()->{registerCurrent();owner.reconcileControlOutput();});arm();}
 private void mutate(Runnable operation){ServerLevel world=level();if(world!=null)NativeRedstoneChannels.batch(world,operation);}
 private void registerCurrent(){for(int slot=0;slot<2;slot++)if(headers[slot].enabled())for(Row row:rows.get(slot)){registered.add(row);NativeRedstoneChannels.channelsChanged(row);}}
 private List<Row> registeredRows(){List<Row> result=new ArrayList<>();for(var side:rows)for(Row row:side)if(registered.contains(row))result.add(row);for(Row row:registered)if(!result.contains(row))result.add(row);return result;}
 // Source settles each removal before the next; preserve dormant feedback while network publication is batched.
 private void withdraw(Collection<Row> old){for(Row row:old){registered.remove(row);if(level()!=null){NativeRedstoneChannels.unregister(row);if(owner.isChannelMemberActive())for(Row remaining:registeredRows())refresh(remaining);}}}
 void stop(){initialized=false;disarm();withdraw(registeredRows());}
 private NativeScreenControls currentData(int slot){List<NativeScreenControls.Row> values=new ArrayList<>();for(Row row:rows.get(slot))values.add(row.snapshot());return new NativeScreenControls(headers[slot].enabled(),headers[slot].title(),values);}
 private void publish(int slot,boolean notify){if(configuring){pendingSlots|=1<<slot;return;}NativeScreenControls next=currentData(slot);headers[slot]=next;owner.installControlData(slot,next,notify);}
 boolean editing(){return editing;}
 private boolean valid(int slot,int index){return !editing&&live()&&slot>=0&&slot<2&&headers[slot].enabled()&&index>=0&&index<rows.get(slot).size();}
 private void refresh(Row row){int received=0;for(int c=0;c<row.channels.size();c++)received=Math.max(received,NativeRedstoneChannels.level(level(),owner.getBlockPos(),row.channels.get(c)));row.setChannelLevel(received);}
 boolean configure(int slot,NativeScreenRowEdit edit){return configure(slot,edit,true,false);}
 private boolean configure(int slot,NativeScreenRowEdit edit,boolean enabled,boolean copied){
  if(editing&&!copied||!live()||edit==null||slot<0||slot>1||!copied&&!owner.family().supportsSlot(slot)||edit.rows().size()>owner.family().standaloneControlRows(slot))return false;
  boolean nested=configuring;
  List<Row> oldRows=rows.get(slot),next=new ArrayList<>();
  for(int i=0;i<edit.rows().size();i++){
   var choice=edit.rows().get(i);Row old=i<oldRows.size()?oldRows.get(i):null;
   next.add(new Row(slot,new NativeScreenControls.Row(choice.label(),choice.channels(),old!=null&&old.slider,old!=null&&old.pulse,old==null?5:old.min,old==null?15:old.max,0,old==null?Map.of():old.levels,old==null?ChannelList.EMPTY:old.latched.intersect(choice.channels()),false,0)));
  }
  List<Row> previous=new ArrayList<>(oldRows.stream().filter(registered::contains).toList());
  editing=configuring=true;
  try{
   rows.set(slot,next);headers[slot]=new NativeScreenControls(enabled,edit.title(),List.of());initialized=true;
   mutate(()->{
    // Match Source's two stages: transfer by index and settle old types before applying new options.
    if(enabled)for(Row row:next){registered.add(row);NativeRedstoneChannels.channelsChanged(row);}
    withdraw(previous);if(enabled)for(Row row:next)refresh(row);
    for(int i=0;i<next.size();i++){
     Row row=next.get(i);NativeRedstoneChannels.unregister(row);var choice=edit.rows().get(i);
     row.pulse=choice.pulse();row.slider=!row.pulse&&choice.slider();
     if(row.pulse){row.latched=ChannelList.EMPTY;row.levels.clear();}else row.until=0;
     row.max=Math.max(3,Math.min(15,choice.max()));row.min=Math.max(1,Math.min(row.max-2,choice.min()==0?5:choice.min()));row.dirty=true;
     if(enabled)NativeRedstoneChannels.channelsChanged(row);refresh(row);
    }
   });
  }finally{
   configuring=nested;pendingSlots|=1<<slot;if(!nested){int publish=pendingSlots;pendingSlots=0;
   try{for(int i=0;i<2;i++)if((publish&(1<<i))!=0)publish(i,true);}finally{editing=false;arm();}}
  }
  return true;
 }
 void transmissionChanged(){transmissionChanged(false);}
 private void transmissionChanged(boolean copied){
  if(editing&&!copied||!live())throw new IllegalStateException("Screen transmission owner unavailable");boolean nested=configuring;
  editing=configuring=true;
  try{mutate(()->{withdraw(registeredRows());NativeRedstoneChannels.unregister(owner);NativeRedstoneChannels.channelsChanged(owner);initialized=true;registerCurrent();for(var side:rows)for(Row row:side)refresh(row);});}
  finally{configuring=nested;if(!nested){pendingSlots=0;try{publish(0,false);publish(1,false);owner.reconcileControlOutput();}finally{editing=false;arm();}}else pendingSlots|=3;}
 }
 /** Ordered Source copier phases, buffering every projection until both headers and the metadata tail are installed. */
 boolean applyCopied(com.vandorlabs.neoforge.copifier.NativeCopifierScreenEdit.Plan plan,Runnable scalar,Runnable footer,Runnable transmission,boolean changeTransmission){
  if(editing||!live())return false;editing=configuring=true;
  try{scalar.run();for(int slot=0;slot<2;slot++)if(slot==0?plan.surface0():plan.surface1()){
   boolean enabled=plan.scalar().controls().get(slot).enabled();var previous=headers[slot];if(previous.enabled()==enabled)continue;headers[slot]=new NativeScreenControls(enabled,previous.title(),previous.rows());
   if(enabled){for(Row row:rows.get(slot)){registered.add(row);NativeRedstoneChannels.channelsChanged(row);}for(Row row:rows.get(slot))refresh(row);}else withdraw(new ArrayList<>(rows.get(slot).stream().filter(registered::contains).toList()));pendingSlots|=1<<slot;
  }
  if(plan.rows()!=null)for(int slot=0;slot<2;slot++){var edit=plan.rows().get(slot);if(!configure(slot,edit.choices(),edit.enabled(),true))throw new IllegalStateException("Prevalidated Screen row copy refused");}
  footer.run();transmission.run();if(changeTransmission)transmissionChanged(true);
  return true;
  }finally{for(int slot=0;slot<2;slot++){var data=currentData(slot);headers[slot]=data;owner.stageCopiedControls(slot,data);}configuring=editing=false;pendingSlots=0;arm();}
 }
 boolean toggle(int slot,int index){if(!valid(slot,index))return false;Row row=rows.get(slot).get(index);if(row.channels.isEmpty()||row.slider)return false;if(row.pulse)return pulse(slot,index);boolean all=true;ServerLevel world=level();for(int i=0;i<row.channels.size();i++)if(NativeRedstoneChannels.level(world,owner.getBlockPos(),row.channels.get(i))==0){all=false;break;}NativeRedstoneChannels.latchLevelChanged(row,all?0:15);return true;}
 boolean select(int slot,int index,int segment){if(!valid(slot,index)||segment<0||segment>3)return false;Row row=rows.get(slot).get(index);if(!row.slider)return false;NativeRedstoneChannels.latchLevelChanged(row,SignalControlData.levelForStep(segment,row.min,row.max));return true;}
 boolean pulse(int slot,int index){if(!valid(slot,index))return false;Row row=rows.get(slot).get(index);if(!row.pulse||row.channels.isEmpty())return false;row.until=level().getGameTime()+20;row.dirty=true;mutate(()->{publish(slot,true);NativeRedstoneChannels.outputChanged(row);});arm();return true;}
 boolean setEnabled(int slot,boolean enabled){if(editing||!live()||slot<0||slot>1)return false;if(headers[slot].enabled()==enabled)return true;var old=headers[slot];headers[slot]=new NativeScreenControls(enabled,old.title(),old.rows());mutate(()->{publish(slot,false);if(enabled)registerCurrent();else withdraw(new ArrayList<>(rows.get(slot).stream().filter(registered::contains).toList()));publish(slot,true);});arm();return true;}
 boolean expire(long now){if(!live())return false;boolean[] pending={false};mutate(()->{for(int slot=0;slot<2;slot++)if(headers[slot].enabled())for(Row row:rows.get(slot))if(row.until>0){if(now>=row.until){row.until=0;row.dirty=true;publish(slot,true);NativeRedstoneChannels.outputChanged(row);}else pending[0]=true;}});return pending[0];}
 private boolean hasPulses(){for(int slot=0;slot<2;slot++)if(headers[slot].enabled())for(Row row:rows.get(slot))if(row.pulse&&row.until>0)return true;return false;}
 private void disarm(){if(owner.getLevel() instanceof ServerLevel world){Set<NativeScreenControlService> active=ACTIVE.get(world);if(active!=null){active.remove(this);if(active.isEmpty())ACTIVE.remove(world);}}}
 private void arm(){ServerLevel world=level();if(world!=null&&initialized&&owner.isChannelMemberActive()&&hasPulses())ACTIVE.computeIfAbsent(world,unused->Collections.newSetFromMap(new IdentityHashMap<>())).add(this);else disarm();}
 static int activeCount(ServerLevel world){Set<NativeScreenControlService> active=ACTIVE.get(world);return active==null?0:active.size();}
 @SubscribeEvent public static void tick(LevelTickEvent.Post event){if(!(event.getLevel() instanceof ServerLevel world))return;Set<NativeScreenControlService> services=ACTIVE.get(world);if(services==null||services.isEmpty())return;for(var service:new ArrayList<>(services))if(!service.initialized||!service.owner.isChannelMemberActive()||!service.expire(world.getGameTime()))service.disarm();}
 @SubscribeEvent public static void load(LevelEvent.Load event){if(event.getLevel() instanceof ServerLevel world)CLOSED.remove(world);}
 @SubscribeEvent public static void unload(LevelEvent.Unload event){if(event.getLevel() instanceof ServerLevel world){CLOSED.add(world);ACTIVE.remove(world);}}
 private final class Row implements NativeChannelLatch {
  NativeScreenControls.Row projection;boolean dirty;final int slot;final String label;final ChannelList channels;boolean slider,pulse;int min,max;final Map<Integer,Integer> levels;ChannelList latched;int value;boolean active;long until;
  Row(int slot,NativeScreenControls.Row saved){this.slot=slot;projection=saved;label=saved.label();channels=saved.channels();slider=saved.slider();pulse=saved.pulse();min=saved.min();max=saved.max();levels=new HashMap<>(saved.levels());latched=saved.latched();value=saved.level();active=saved.active();until=saved.pulseUntil();}
  NativeScreenControls.Row snapshot(){if(dirty){projection=new NativeScreenControls.Row(label,channels,slider,pulse,min,max,value,levels,latched,active,until);dirty=false;}return projection;}
  public BlockEntity channelBlockEntity(){return owner;}public ChannelList getRedstoneChannels(){return channels;}public ChannelList transmittedChannels(){return channels;}public int transmissionRange(){return owner.transmission().range();}
  public boolean isChannelMemberActive(){return owner.isChannelMemberActive()&&registered.contains(this)&&headers[slot].enabled();}
  public int localSignalLevel(int channel){return pulse?until>0&&owner.getLevel().getGameTime()<until?15:0:latchedLevel(channel);}
  public boolean isChannelLatch(){return !pulse;}public boolean latchOn(){return !channels.isEmpty()&&latched.containsAll(channels);}public ChannelList latchedChannels(){return latched;}public int latchedLevel(int channel){return levels.getOrDefault(channel,latched.contains(channel)?15:0);}
  public void applyLinkedLatch(boolean on){applyLinkedChannels(on?channels:ChannelList.EMPTY);}public void applyLinkedChannels(ChannelList next){if(!latched.equals(next)){latched=next;dirty=true;publish(slot,false);}}
  public void applyLinkedLevels(Map<Integer,Integer> next){ChannelList active=NativeChannelLatch.activeChannels(next);if(!levels.equals(next)||!latched.equals(active)){levels.clear();levels.putAll(next);latched=active;dirty=true;publish(slot,false);}}
  public void setChannelLevel(int level){if(!rows.get(slot).contains(this))return;boolean changed=false;if(slider&&value!=level){value=level;changed=true;}boolean all=!channels.isEmpty();ServerLevel world=NativeScreenControlService.this.level();if(world==null)return;for(int i=0;i<channels.size();i++)if(NativeRedstoneChannels.level(world,owner.getBlockPos(),channels.get(i))==0){all=false;break;}if(active!=all){active=all;changed=true;}if(changed){dirty=true;publish(slot,true);}}
 }
}
