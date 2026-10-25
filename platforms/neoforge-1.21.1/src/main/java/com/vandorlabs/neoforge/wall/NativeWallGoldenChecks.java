package com.vandorlabs.neoforge.wall;

import com.google.gson.*;
import com.vandorlabs.neoforge.ModernFinishCatalog;
import com.vandorlabs.neoforge.fluid.NativeFluidRenderSnapshot;
import com.vandorlabs.persistence.WallAppearance;
import com.vandorlabs.ui.WallDialog;
import java.nio.file.Files;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/** Independent old-renderer comparison inputs; geometry is emitted by the actual native model. */
public final class NativeWallGoldenChecks {
    private static final int[][] STYLES={{0,-1,0,-1,1,1},{150,2,1,150,0,0},{26,-1,2,0,2,2}};
    private record Case(int family,int facing,int depth,int shape,int style,String topology){String key(){return family+"/"+facing+"/"+depth+"/"+shape+"/"+style+"/"+topology;}}
    private NativeWallGoldenChecks(){}
    private static void need(boolean ok,String why){if(!ok)throw new IllegalStateException("Wall original parity: "+why);}
    public static void run(Minecraft mc){
        BlockPos base=new BlockPos(324,85,8);Map<BlockPos,BlockState> before=new LinkedHashMap<>();Map<BlockPos,CompoundTag> tiles=new HashMap<>();Set<BlockPos> active=new HashSet<>();List<Object> report=new ArrayList<>();List<Case> cases=new ArrayList<>();
        for(int f=0;f<3;f++)for(Direction d:Direction.Plane.HORIZONTAL)for(int depth=0;depth<3;depth++)for(int s=0;s<(f==0?1:4);s++)for(int style=0;style<3;style++)cases.add(new Case(f,d.get3DDataValue(),depth,s,style,"single"));
        for(int f=0;f<3;f++)for(Direction d:Direction.Plane.HORIZONTAL)for(int s=0;s<(f==0?1:4);s++)for(int style=0;style<3;style++)for(String t:f==0?new String[]{"L","T","cross"}:f==1?new String[]{"joined","hole","L","T","cross"}:new String[]{"joined","hole"})cases.add(new Case(f,d.get3DDataValue(),0,s,style,t));
        for(int x=-3;x<=3;x++)for(int y=-1;y<=3;y++)for(int z=-3;z<=3;z++){BlockPos p=base.offset(x,y,z);need(mc.level.getChunkSource().hasChunk(p.getX()>>4,p.getZ()>>4),"bounded actual loaded fixture");before.put(p,mc.level.getBlockState(p));var tile=mc.level.getBlockEntity(p);if(tile!=null)tiles.put(p,tile.saveWithFullMetadata(mc.level.registryAccess()));}
        try{
            for(var p:before.keySet())mc.level.setBlock(p,Blocks.AIR.defaultBlockState(),2);
            for(Case c:cases){
                for(var p:active)mc.level.setBlock(p,Blocks.AIR.defaultBlockState(),2);active.clear();
                var block=c.family==0?WallRegistration.WALL.get():c.family==1?WallRegistration.PORTHOLE.get():WallRegistration.FULL_PORTHOLE.get();Direction facing=Direction.from3DDataValue(c.facing),right=facing.getClockWise();var state=block.defaultBlockState().setValue(WallBlock.FACING,facing).setValue(WallBlock.DEPTH,c.depth);WallEntity owner=place(mc,base,state,c,true,active);
                if(c.topology.equals("joined")||c.topology.equals("hole")){int size=c.topology.equals("hole")?3:2;for(int y=0;y<size;y++)for(int x=0;x<size;x++)if((x!=0||y!=0)&&(!c.topology.equals("hole")||x!=1||y!=1))place(mc,base.relative(right,x).above(y),state,c,true,active);}
                else if(!c.topology.equals("single")){place(mc,base.relative(facing),state.setValue(WallBlock.FACING,right),c,false,active);if(!c.topology.equals("L"))place(mc,base.relative(facing.getOpposite()),state.setValue(WallBlock.FACING,right),c,false,active);if(!c.topology.equals("L"))place(mc,base.relative(right),state,c,false,active);if(c.topology.equals("cross"))place(mc,base.relative(right.getOpposite()),state,c,false,active);}
                for(var p:active)WallClientSnapshots.update(new NativeFluidRenderSnapshot.Update((WallEntity)mc.level.getBlockEntity(p)));WallClientSnapshots.tick(new ClientTickEvent.Post());
                var snapshot=owner.wallRenderSnapshot();need(!snapshot.opaque.isEmpty(),"actual publication "+c.key());var model=mc.getBlockRenderer().getBlockModel(state);var data=owner.getModelData().derive().with(WallModel.OWN_LIGHT,0).build();var emitted=model.getQuads(state,null,RandomSource.create(0),data,RenderType.cutoutMipped());need(emitted.size()==snapshot.opaque.size()*2,"actual two-sided emission");List<Object> quads=new ArrayList<>();
                for(int i=0;i<snapshot.opaque.size();i++){var face=snapshot.opaque.get(i);var q=emitted.get(i*2);int[] v=q.getVertices();List<Object> vertices=new ArrayList<>();String material=material(c,face,false);double[] window=window(c,material);var sprite=q.getSprite();for(int k=0;k<4;k++){int at=k*8,col=v[at+3],n=v[at+7];vertices.add(new double[]{Float.intBitsToFloat(v[at]),Float.intBitsToFloat(v[at+1]),Float.intBitsToFloat(v[at+2]),(Float.intBitsToFloat(v[at+4])-sprite.getU0())/(sprite.getU1()-sprite.getU0())/window[0],(Float.intBitsToFloat(v[at+5])-sprite.getV0())/(sprite.getV1()-sprite.getV0())/window[1],(byte)n/127.,(byte)(n>>8)/127.,(byte)(n>>16)/127.,col&255,col>>8&255,col>>16&255,col>>>24});}quads.add(Map.of("pass",0,"depthWrite",true,"material",material,"vertices",vertices));}
                for(var face:snapshot.glass){String material=material(c,face,true);double[] window=window(c,material);List<Object> vertices=new ArrayList<>();for(var p:face.vertices())vertices.add(new double[]{p.x(),p.y(),p.z(),p.u()/window[0],p.v()/window[1],face.nx(),face.ny(),face.nz(),face.argb()>>16&255,face.argb()>>8&255,face.argb()&255,(int)(face.opacity()*255F)});quads.add(Map.of("pass",1,"depthWrite",face.depthWrite(),"material",material,"vertices",vertices));}
                report.add(Map.of("key",c.key(),"quads",quads));
            }
            var output=mc.gameDirectory.toPath().resolve("screenshots/modern-wall-original-parity.json");Files.createDirectories(output.getParent());Files.writeString(output,new Gson().toJson(Map.of("format",1,"cases",report)));System.out.println("[vandorlabs][modern] wall-original-matrix PASS "+report.size()+" actual native publication/model cases; independent original-renderer comparison required");
        }catch(Exception e){throw new IllegalStateException("Wall native matrix",e);}finally{
            for(var e:before.entrySet()){mc.level.setBlock(e.getKey(),e.getValue(),2);var tile=mc.level.getBlockEntity(e.getKey());if(tile!=null&&tiles.containsKey(e.getKey()))tile.loadWithComponents(tiles.get(e.getKey()),mc.level.registryAccess());if(tile instanceof WallEntity wall)WallClientSnapshots.update(new NativeFluidRenderSnapshot.Update(wall));}WallClientSnapshots.tick(new ClientTickEvent.Post());
        }
    }
    private static WallEntity place(Minecraft mc,BlockPos p,BlockState state,Case c,boolean owner,Set<BlockPos> active){active.add(p);mc.level.setBlock(p,state,2);need(mc.level.getBlockEntity(p) instanceof WallEntity,"actual tile factory");WallEntity tile=(WallEntity)mc.level.getBlockEntity(p);int[] s=STYLES[c.style];need(tile.applyWall(new WallDialog.State(owner?s[0]:1,owner?s[1]:2,s[2],-1,new WallAppearance(2,s[4],s[3],true,s[5]!=1,true,true,c.shape,s[5]))),"apply exact matrix inputs");return tile;}
    private static String material(Case c,WallRenderSnapshot.Face face,boolean glass){int[] s=STYLES[c.style];if(glass)return s[3]<0?"stock:2:"+s[4]:Integer.toString(s[3]);for(int choice:new int[]{s[0],s[1]<0?26:s[1]})if(face.texture().toString().equals(ModernFinishCatalog.entry(choice).texture()))return Integer.toString(choice);if(face.texture().toString().equals("vandorlabs:block/programmable_glass/metal_side"))return "26";throw new IllegalStateException("Unselected actual material "+face.texture());}
    private static double[] window(Case c,String material){if(material.startsWith("stock:"))return new double[]{1,1};var entry=ModernFinishCatalog.entry(Integer.parseInt(material));return new double[]{entry.u(),entry.v()};}
}
