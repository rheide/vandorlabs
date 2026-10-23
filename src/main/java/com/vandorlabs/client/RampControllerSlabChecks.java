package com.vandorlabs.client;

import com.vandorlabs.blocks.*;
import com.vandorlabs.tiles.TileEntityRampController;
import net.minecraft.block.BlockSlab;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.model.*;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.*;
import net.minecraft.world.WorldServer;
import java.io.File;

/** Real slab placement, platform capture, baked geometry and configured item padding. */
final class RampControllerSlabChecks {
    static void run(Minecraft mc,File output)throws Exception{
        mc.getIntegratedServer().addScheduledTask(()->{
            WorldServer world=mc.getIntegratedServer().getWorld(0);EntityPlayerMP player=mc.getIntegratedServer().getPlayerList().getPlayers().get(0);BlockRampControllerSlab block=(BlockRampControllerSlab)ModBlocks.PROGRAMMABLE_RAMP_SLAB;BlockPos pos=new BlockPos(44,80,28);Vec3d before=player.getPositionVector();float yaw=player.rotationYaw,pitch=player.rotationPitch;int cases=0;
            try{for(EnumFacing heading:EnumFacing.HORIZONTALS)for(int vertical=-1;vertical<=1;vertical++)for(boolean top:new boolean[]{false,true}){
                for(BlockPos p:BlockPos.getAllInBox(pos.add(-4,-5,-4),pos.add(4,5,4)))world.setBlockToAir(p);
                player.setPosition(pos.getX()+.5,pos.getY()+1,pos.getZ()+2);player.rotationYaw=heading.getHorizontalAngle();player.rotationPitch=vertical*80;
                IBlockState state=block.getStateForPlacement(world,pos,EnumFacing.EAST,.5F,top?.75F:.25F,.5F,0,player);EnumFacing scan=vertical<0?EnumFacing.UP:vertical>0?EnumFacing.DOWN:heading;
                require(state.getValue(BlockRampControllerSlab.HALF)==(top?BlockSlab.EnumBlockHalf.TOP:BlockSlab.EnumBlockHalf.BOTTOM),"side-hit top/bottom placement");
                require(block.getStateForPlacement(world,pos,EnumFacing.DOWN,.5F,.1F,.5F,0,player).getValue(BlockRampControllerSlab.HALF)==BlockSlab.EnumBlockHalf.TOP,"underside placement selects top slab");
                require(block.getStateForPlacement(world,pos,EnumFacing.UP,.5F,.9F,.5F,0,player).getValue(BlockRampControllerSlab.HALF)==BlockSlab.EnumBlockHalf.BOTTOM,"upper surface selects bottom slab");
                IBlockState decoded=block.getStateFromMeta(block.getMetaFromState(state));require(BlockRampController.scanFacing(decoded)==scan&&decoded.getValue(BlockRampControllerSlab.HALF)==state.getValue(BlockRampControllerSlab.HALF),"six faces and halves survive metadata");
                world.setBlockState(pos,state,2);block.onBlockPlacedBy(world,pos,state,player,new ItemStack(block));TileEntityRampController tile=(TileEntityRampController)world.getTileEntity(pos);require(tile.rampDirection()==heading,"horizontal travel retained");
                java.util.List<BlockPos> sources=new java.util.ArrayList<>();BlockPos seed=pos.offset(scan);for(int i=0;i<2;i++)for(int j=0;j<2;j++){BlockPos source=seed.offset(heading,i).offset(heading.rotateY(),j);sources.add(source);world.setBlockState(source,Blocks.STONE.getDefaultState(),2);}
                require(tile.configure(player,2,8,scan==EnumFacing.DOWN,true,false,false),"same ramp dialog settings");world.setBlockState(pos.offset(scan.getOpposite()),Blocks.REDSTONE_BLOCK.getDefaultState(),3);tile.updatePower();require(tile.attached(),"platform captured "+scan+" "+tile.status);
                require(block.getActualState(decoded,world,pos).getValue(BlockRampController.ACTIVE),"activity restored from tile with twelve-value metadata");
                NBTTagCompound settings=block.getPickBlock(state,null,world,pos,player).getSubCompound("RampSettings");require(settings!=null&&settings.hasKey("Direction"),"configured pick retains ramp settings");
                require(tile.recover(true),"safe source recovery");for(BlockPos source:sources)require(world.getBlockState(source).getBlock()==Blocks.STONE,"source restored");cases++;
            }}finally{for(BlockPos p:BlockPos.getAllInBox(pos.add(-4,-5,-4),pos.add(4,5,4)))world.setBlockToAir(p);player.setPosition(before.x,before.y,before.z);player.rotationYaw=yaw;player.rotationPitch=pitch;}
            System.out.println("[vandorlabs][reprolab] ramp-controller-slab-placement PASS cases="+cases+" six scan faces, top/bottom slabs, metadata, tile activity, capture and recovery");
        }).get();
        int models=0;BlockRampControllerSlab block=(BlockRampControllerSlab)ModBlocks.PROGRAMMABLE_RAMP_SLAB;
        for(BlockSlab.EnumBlockHalf half:BlockSlab.EnumBlockHalf.values())for(EnumFacing face:EnumFacing.values())for(int mode=0;mode<7;mode++)for(boolean active:new boolean[]{false,true}){
            IBlockState state=block.getDefaultState().withProperty(BlockRampControllerSlab.HALF,half).withProperty(BlockRampController.ACTIVE_FACE,face).withProperty(BlockRampController.FACING,face.getAxis()==EnumFacing.Axis.Y?EnumFacing.NORTH:face).withProperty(BlockRampController.MODE,mode).withProperty(BlockRampController.ACTIVE,active);
            IBakedModel model=mc.getBlockRendererDispatcher().getModelForState(state);double min=2,max=-1;
            for(EnumFacing side:new EnumFacing[]{null,EnumFacing.NORTH,EnumFacing.SOUTH,EnumFacing.WEST,EnumFacing.EAST,EnumFacing.UP,EnumFacing.DOWN})for(BakedQuad quad:model.getQuads(state,side,0)){int[] data=quad.getVertexData();int stride=data.length/4;for(int i=0;i<4;i++){double y=Float.intBitsToFloat(data[i*stride+1]);min=Math.min(min,y);max=Math.max(max,y);}}
            if(face.getAxis()==EnumFacing.Axis.Y){for(EnumFacing side:EnumFacing.HORIZONTALS)require(model.getQuads(state,side,0).stream().anyMatch(q->q.getSprite().getIconName().equals("vandorlabs:block/ramp_slab_side_"+face.getName()+"_"+(active?"on":"off"))),"vertical scan arrows on all four side panels");}
            else{require(model.getQuads(state,face.rotateYCCW(),0).stream().anyMatch(q->q.getSprite().getIconName().equals("vandorlabs:block/ramp_slab_side_left_"+(active?"on":"off"))),"left side arrow points toward active face");require(model.getQuads(state,face.rotateY(),0).stream().anyMatch(q->q.getSprite().getIconName().equals("vandorlabs:block/ramp_slab_side_right_"+(active?"on":"off"))),"right side arrow points toward active face");}
            require(Math.abs(max-min-.5)<1e-6&&Math.abs(min-(half==BlockSlab.EnumBlockHalf.TOP?.5:0))<1e-6,"every scan face keeps a horizontal half-height housing");models++;
        }
        for(int mode=0;mode<7;mode++){
            ItemStack stack=item(mode);
            IBakedModel model=mc.getRenderItem().getItemModelWithOverrides(stack,mc.world,mc.player);boolean front=false;java.util.Set<String> sprites=new java.util.HashSet<>();
            for(EnumFacing side:new EnumFacing[]{null,EnumFacing.NORTH,EnumFacing.SOUTH,EnumFacing.WEST,EnumFacing.EAST,EnumFacing.UP,EnumFacing.DOWN})for(BakedQuad quad:model.getQuads(null,side,0)){
                sprites.add(quad.getSprite().getIconName());front|=quad.getSprite().getIconName().equals("vandorlabs:block/ramp_slab_"+BlockRampController.MODE_TEXTURES[mode]+"_off");int[] data=quad.getVertexData();int stride=data.length/4;for(int i=0;i<4;i++){Vec3d projected=ControlItemPadding.project(new Vec3d(Float.intBitsToFloat(data[i*stride]),Float.intBitsToFloat(data[i*stride+1]),Float.intBitsToFloat(data[i*stride+2])),model.getItemCameraTransforms().gui);require(Math.abs(projected.x)<.49&&Math.abs(projected.y+model.getItemCameraTransforms().gui.translation.y)<.49,"configured slab icon has padding");}}
            require(front,"configured item retains selected mode "+mode+" class="+model.getClass()+" sprites="+sprites);
        }
        net.minecraft.client.gui.GuiScreen previous=mc.currentScreen;net.minecraft.client.gui.GuiScreen icons=new net.minecraft.client.gui.GuiScreen(){@Override public void drawScreen(int x,int y,float partial){drawDefaultBackground();net.minecraft.client.renderer.RenderHelper.enableGUIStandardItemLighting();for(int mode=0;mode<7;mode++){int left=width/2-90,top=20+mode*24;drawRect(left-1,top-1,left+17,top+17,0xff344354);mc.getRenderItem().renderItemAndEffectIntoGUI(item(mode),left,top);fontRenderer.drawString(BlockRampController.MODE_TEXTURES[mode],left+26,top+5,0xd7f4f5);}net.minecraft.client.renderer.RenderHelper.disableStandardItemLighting();}};
        try{mc.displayGuiScreen(icons);mc.getFramebuffer().bindFramebuffer(true);mc.entityRenderer.setupOverlayRendering();icons.drawScreen(0,0,0);javax.imageio.ImageIO.write(net.minecraft.util.ScreenShotHelper.createScreenshot(mc.displayWidth,mc.displayHeight,mc.getFramebuffer()),"png",new File(output,"ramp-controller-slab-items.png"));}finally{mc.displayGuiScreen(previous);}
        System.out.println("[vandorlabs][reprolab] ramp-controller-slab-models PASS models="+models+" horizontal geometry, seven configured item fronts and padded GUI icons");
    }
    private static ItemStack item(int mode){ItemStack stack=new ItemStack(ModBlocks.PROGRAMMABLE_RAMP_SLAB);NBTTagCompound settings=new NBTTagCompound();settings.setBoolean("MoatMode",mode==6);settings.setInteger("EscalatorMode",mode>=4&&mode<6?mode-3:0);settings.setBoolean("Elevator",mode==2||mode==3);settings.setBoolean("ExtendSegments",mode<4&&(mode&1)!=0);stack.setTagInfo("RampSettings",settings);return stack;}
    private static void require(boolean ok,String message){if(!ok)throw new IllegalStateException("Ramp Controller Slab: "+message);}
}
