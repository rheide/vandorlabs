package com.vandorlabs.neoforge.screen;

import com.vandorlabs.render.ScreenFamilyPolicy;
import com.vandorlabs.redstone.ChannelList;
import com.vandorlabs.ui.ScreenSelectorDialog;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.common.util.FakePlayerFactory;

/** Actual registered blocks, settings sessions, saved two-surface options and native collision. */
public final class ScreenFamilyRuntimeChecks {
    private ScreenFamilyRuntimeChecks(){}
    private static void require(boolean result,String message){if(!result)throw new IllegalStateException("Modern screen family: "+message);}
    private static List<ProgrammableScreenBlock> blocks(){return List.of(ScreenRegistration.BLOCK.get(),ScreenRegistration.DIAGONAL.get(),ScreenRegistration.CONSOLE.get(),ScreenRegistration.HALF_CONSOLE.get(),ScreenRegistration.FULL_INPUT.get(),ScreenRegistration.HALF_INPUT.get(),ScreenRegistration.DIAGONAL_HALF.get());}
    private static BlockPos pos(int index){return new BlockPos(40+index*4,80,0);}
    private static String firstInput(){return InputArtwork.ENTRIES.get(0).bare();}
    private static String secondInput(){return InputArtwork.ENTRIES.get(1).framed();}
    private static ListTag rows(String label){ListTag rows=new ListTag();CompoundTag row=new CompoundTag();row.putString("Label",label);row.putIntArray("RedstoneChannels",new int[]{101});row.putBoolean("Slider",false);row.putBoolean("Pulse",false);row.putInt("Min",5);row.putInt("Max",15);rows.add(row);return rows;}
    private static boolean canHoldFluid(ServerLevel level,BlockPos pos,net.minecraft.world.level.material.Fluid fluid){
        try{var method=net.minecraft.world.level.material.FlowingFluid.class.getDeclaredMethod("canHoldFluid",net.minecraft.world.level.BlockGetter.class,BlockPos.class,net.minecraft.world.level.block.state.BlockState.class,net.minecraft.world.level.material.Fluid.class);method.setAccessible(true);return (boolean)method.invoke(fluid,level,pos,level.getBlockState(pos),fluid);}catch(ReflectiveOperationException error){throw new IllegalStateException("Cannot exercise native fluid admission",error);}
    }
    private static double volume(net.minecraft.world.phys.shapes.VoxelShape shape){double result=0;for(var b:shape.toAabbs())result+=(b.maxX-b.minX)*(b.maxY-b.minY)*(b.maxZ-b.minZ);return result;}
    private static void artworkContract(){
        require(ScreenArtwork.ENTRIES.size()==58,"canonical artwork pair count");
        for(var entry:ScreenArtwork.ENTRIES){require(ScreenArtwork.active(entry.bare(),true).equals(entry.bare()),"raw bare identity survives frame flag");require(ScreenArtwork.active(entry.framed(),false).equals(entry.framed()),"raw framed identity survives frame flag");require(ScreenArtwork.activeHasFrame(entry.bare(),true)==entry.bareHasFrame(),"bare off-art metadata");require(ScreenArtwork.activeHasFrame(entry.framed(),false)==entry.framedHasFrame(),"framed off-art metadata");}
        require(ScreenArtwork.activeHasFrame("engineering_screen",false),"authored engineering border survives false flag");require(!ScreenArtwork.activeHasFrame("console_biology",true),"bare authored console survives true flag");require(InputArtwork.valid(InputArtwork.DEFAULT),"canonical input default");
    }
    public static void run(ServerLevel level){
        artworkContract();
        ScreenFamilyGeometryChecks.run(level);
        var player=FakePlayerFactory.getMinecraft(level);player.setGameMode(GameType.CREATIVE);
        double[] expectedVolume={1,17D/32,1,.5,1D/16,1D/32,9D/64};
        ScreenSelectorDialog.Profile[] profiles={ScreenSelectorDialog.Profile.SCREEN,ScreenSelectorDialog.Profile.SCREEN,ScreenSelectorDialog.Profile.CONSOLE,ScreenSelectorDialog.Profile.HALF_CONSOLE,ScreenSelectorDialog.Profile.SCREEN,ScreenSelectorDialog.Profile.INPUT,ScreenSelectorDialog.Profile.DIAGONAL_HALF};
        for(int i=0;i<blocks().size();i++){
            var block=blocks().get(i);var pos=pos(i);level.getChunkAt(pos);level.setBlock(pos,Blocks.AIR.defaultBlockState(),3);level.setBlock(pos,block.defaultBlockState(),3);
            var screen=(ProgrammableScreenEntity)level.getBlockEntity(pos);for(var face:block.facingProperty().getPossibleValues()){var facingDefault=block.defaultBlockState().setValue(block.facingProperty(),face);require(!block.keyboard(facingDefault)&&!block.upper(facingDefault)&&!block.inverted(facingDefault),"legacy default flags must all be false "+block.family()+" "+face);}
            require(screen.facing()==net.minecraft.core.Direction.NORTH&&!screen.data().smallInput&&!screen.ceilingMounted()&&screen.wallPosition()==0,"legacy facing/size/mount defaults "+block.family());require(screen.sides()==1&&screen.data().displayMode==2&&screen.data().animationSpeedIndex==1&&screen.data().framed,"legacy fresh screen display defaults "+block.family());require(screen.data().inputPanel.equals(InputArtwork.DEFAULT)&&screen.data().secondaryInputPanel.equals(InputArtwork.DEFAULT),"legacy fresh input defaults "+block.family());require(screen!=null,"registered block lacks screen entity "+block.family());player.setPos(pos.getX()+.5,81,-2);
            var menu=new ScreenMenu(40+i,player.getInventory(),pos);player.containerMenu=menu;require(menu.stillValid(player),"family menu authorization "+block.family());
            require(screen.getBlockState().getMapColor(level,pos)==net.minecraft.world.level.material.MapColor.METAL,"legacy metal map color "+block.family());require(screen.getBlockState().blocksMotion()&&!canHoldFluid(level,pos,net.minecraft.world.level.material.Fluids.WATER)&&!canHoldFluid(level,pos,net.minecraft.world.level.material.Fluids.LAVA),"legacy iron family must reject water/lava admission "+block.family());
            require(block.getExplosionResistance()==6,"legacy effective resistance "+block.family());require(screen.getBlockState().canOcclude()==(i==0),"legacy opacity "+block.family());
            require(Math.abs(volume(screen.getBlockState().getCollisionShape(level,pos))-expectedVolume[i])<1e-9,"native collision volume "+block.family());
            require(!screen.getBlockState().hasProperty(net.minecraft.world.level.block.state.properties.BlockStateProperties.WATERLOGGED)&&level.getFluidState(pos).isEmpty(),"legacy dry family "+block.family());require(screen.dialogProfile()==profiles[i],"shared dialog profile "+block.family());
            var packet=new ScreenPayload(menu.containerId,pos,"engineering_screen",0,2,0,2,true,"101, 102",3,-1,firstInput(),secondInput(),false,-1,-1);
            require(ScreenPayload.apply(player,packet),"authorized family settings "+block.family());
            require(screen.data().inputPanel.equals(firstInput())&&screen.data().secondaryInputPanel.equals(secondInput()),"independent input art "+block.family());
            require(screen.redstone().applyRowConfiguration("PRIMARY",rows("PRIMARY ROW")),"primary row installation");screen.redstone().setEnabled(true);
            if(screen.supportsSlot(1)){require(screen.redstone(1).applyRowConfiguration("SECONDARY",rows("SECONDARY ROW")),"secondary row installation");screen.redstone(1).setEnabled(true);}
            int expectedLight=block.emitsLight()?11:0;require(screen.getBlockState().getLightEmission(level,pos)==expectedLight,"family overlay light "+block.family());
            int expectedRows=ScreenFamilyPolicy.half(block.family(),0)?4:8;require(screen.rowCapacity(0)==expectedRows,"primary row capacity");
            ListTag over=rows("EXTRA");while(over.size()<=expectedRows)over.add(over.getCompound(0).copy());require(!screen.redstone().applyRowConfiguration("OVER",over),"server accepted too many rows "+block.family());
            var saved=screen.saveWithoutMetadata(level.registryAccess());var restored=new ProgrammableScreenEntity(pos,screen.getBlockState());restored.loadWithComponents(saved,level.registryAccess());
            assertRestored(restored,screen.supportsSlot(1),"local roundtrip "+block.family());
            var drops=net.minecraft.world.level.block.Block.getDrops(screen.getBlockState(),level,pos,screen,player,new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.IRON_PICKAXE));require(drops.size()==1&&drops.get(0).is(block.asItem()),"family configured drop identity "+block.family());
            var data=drops.get(0).get(net.minecraft.core.component.DataComponents.BLOCK_ENTITY_DATA);require(data!=null,"family configured drop missing data");CompoundTag drop=data.copyTag();require(drop.getCompound("RedstonePrimary").getList("Rows",10).size()==1&&!drop.contains("ChannelLevel"),"family primary drop options or transient power");
            if(screen.supportsSlot(1))require(drop.contains("RedstoneSecondary",10),"secondary configured drop options missing");
            player.setPos(pos.getX()+100,81,-2);require(!menu.stillValid(player),"family distant menu accepted");
        }
        player.containerMenu=player.inventoryMenu;
        System.out.println("[vandorlabs][modern] screen-families-server PASS seven registries, collision, opacity, resistance, dry volumes, light, shared dialogs, native sessions and dual-surface persistence");
    }
    private static void assertRestored(ProgrammableScreenEntity screen,boolean secondary,String message){
        require(screen.data().displayMode==0&&screen.data().animationSpeedIndex==2&&screen.sides()==2&&screen.data().housingTexture==3,message+" common options");require(screen.data().inputPanel.equals(firstInput())&&screen.data().secondaryInputPanel.equals(secondInput()),message+" independent artwork");require(screen.redstone().enabled()&&screen.redstone().title().equals("PRIMARY")&&screen.redstone().rows().size()==1&&screen.redstone().rows().get(0).label.equals("PRIMARY ROW"),message+" primary controls");
        if(secondary)require(screen.redstone(1).enabled()&&screen.redstone(1).title().equals("SECONDARY")&&screen.redstone(1).rows().size()==1&&screen.redstone(1).rows().get(0).label.equals("SECONDARY ROW"),message+" secondary controls");
    }
    /** Client-only opt-in disposable gallery; never invoked by ordinary server startup. */
    public static void clientFixture(ServerLevel level){
        level.setDayTime(6000);level.getGameRules().getRule(net.minecraft.world.level.GameRules.RULE_DAYLIGHT).set(false,level.getServer());
        for(int x=37;x<=67;x++)for(int z=-5;z<=3;z++)level.setBlock(new BlockPos(x,79,z),Blocks.SMOOTH_STONE.defaultBlockState(),3);
        for(int i=0;i<blocks().size();i++){
            var block=blocks().get(i);var pos=pos(i);level.getChunkAt(pos);level.setBlock(pos,Blocks.AIR.defaultBlockState(),3);level.setBlock(pos,block.defaultBlockState(),3);var screen=(ProgrammableScreenEntity)level.getBlockEntity(pos);
            screen.configureFamily("engineering_screen",firstInput(),secondInput(),new ScreenSelectorDialog.State(screen.supportsSlot(1),0,1,1,0,1,true,true),ChannelList.EMPTY,0,-1,-1);
            for(int slot=0;slot<2;slot++)if(screen.supportsSlot(slot)){ListTag rows=new ListTag();for(int row=0;row<4;row++){CompoundTag tag=rows("CONTROL "+(row+1)).getCompound(0);tag.putIntArray("RedstoneChannels",new int[]{200+i*8+slot*4+row});rows.add(tag);}require(screen.redstone(slot).applyRowConfiguration(slot==0?"PRIMARY":"SECONDARY",rows),"client family rows");screen.redstone(slot).setEnabled(false);}
        }
    }
    public static void read(ServerLevel level){for(int i=0;i<blocks().size();i++){var pos=pos(i);level.getChunkAt(pos);require(level.getBlockEntity(pos) instanceof ProgrammableScreenEntity,"saved family missing "+i);var screen=(ProgrammableScreenEntity)level.getBlockEntity(pos);require(screen.getBlockState().is(blocks().get(i)),"saved family identity "+i);assertRestored(screen,screen.supportsSlot(1),"disk roundtrip "+screen.family());}System.out.println("[vandorlabs][modern] screen-families-read PASS all seven saved identities and independent input/control options");}
}
