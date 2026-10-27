package com.vandorlabs.client;

import net.minecraft.client.Minecraft;

/** Narrow integrated-client entry for the native Mekanism glass transfer regression. */
final class MekanismReactorGlassLiveChecks {
    private static int ticks;private static boolean done;
    static boolean tick(Minecraft mc)throws Exception{
        if(done)return true;mc.displayGuiScreen(null);mc.gameSettings.pauseOnLostFocus=false;
        if(++ticks<40)return false;
        com.vandorlabs.vehicle.VehicleStructure snapshot=mc.getIntegratedServer().callFromMainThread(()->com.vandorlabs.vehicle.MekanismReactorGlassChecks.run(mc.getIntegratedServer().getWorld(0),mc.getIntegratedServer().getPlayerList().getPlayers().get(0))).get();
        com.vandorlabs.vehicle.VehicleWorld view=new com.vandorlabs.vehicle.VehicleWorld(mc.world,snapshot);int panes=0,quads=0;
        for(com.vandorlabs.vehicle.VehicleStructure.Cell cell:snapshot.cells)if(com.vandorlabs.vehicle.MekanismTankCompat.reactorGlass(view.getTileEntity(cell.pos))){
            panes++;net.minecraft.block.state.IBlockState actual=cell.state.getActualState(view,cell.pos),extended=cell.state.getBlock().getExtendedState(actual,view,cell.pos);
            net.minecraft.client.renderer.block.model.IBakedModel model=mc.getBlockRendererDispatcher().getModelForState(actual);
            quads+=model.getQuads(extended,null,0).size();for(net.minecraft.util.EnumFacing face:net.minecraft.util.EnumFacing.values())quads+=model.getQuads(extended,face,0).size();
        }
        if(panes!=2||quads==0)throw new IllegalStateException("Carried reactor glass has no native client geometry: panes="+panes+" quads="+quads);
        System.out.println("[vandorlabs] reactor-glass-client PASS panes="+panes+" native-quads="+quads);
        done=true;return true;
    }
    private MekanismReactorGlassLiveChecks(){}
}
