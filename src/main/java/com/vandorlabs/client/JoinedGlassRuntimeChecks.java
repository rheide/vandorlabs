package com.vandorlabs.client;

import com.vandorlabs.blocks.*;
import com.vandorlabs.items.*;
import com.vandorlabs.tiles.*;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import java.util.*;

/** Real saved tiles and rendered UV seams for joined glass-block and porthole materials. */
final class JoinedGlassRuntimeChecks {
    static final String[] IDS={"programmable_glass","programmable_diagonal_glass","programmable_porthole_wall","programmable_diagonal_porthole","programmable_porthole_block"};
    private static final BlockPos BASE=new BlockPos(120,30,120);
    private static void require(boolean ok,String message){if(!ok)throw new IllegalStateException("joined glass: "+message);}
    private static void clear(World world){for(BlockPos p:BlockPos.getAllInBox(BASE.add(-5,-2,-5),BASE.add(5,5,5)))world.setBlockToAir(p);}
    static NBTTagCompound settings(int material,int tint,boolean stretch){
        NBTTagCompound values=new NBTTagCompound();values.setInteger(ProgrammableSettings.GLASS_MATERIAL,material);values.setInteger(ProgrammableSettings.GLASS_SHADE,12);values.setInteger(ProgrammableSettings.GLASS_TINT,tint);values.setBoolean(ProgrammableSettings.JOIN,true);
        NBTTagCompound layout=new NBTTagCompound();layout.setBoolean("Stretch",stretch);layout.setBoolean("TextureJoin",true);values.setTag(ProgrammableSettings.GLASS_LAYOUT,layout);return values;
    }
    static void run(EntityPlayer player){
        World world=player.world;int count=0;
        try{
            for(String id:IDS){
                Block block=Block.getBlockFromName("vandorlabs:"+id);world.setBlockState(BASE,block.getDefaultState(),3);
                TileEntity raw=world.getTileEntity(BASE);if(raw instanceof TileEntityAnimatedScreenSelector){((TileEntityAnimatedScreenSelector)raw).setHousingTexture(8);((TileEntityAnimatedScreenSelector)raw).setSideTexture(26);}
                for(int material:new int[]{-1,0})for(int tint=0;tint<3;tint++)for(boolean stretch:new boolean[]{false,true})for(int shade=0;shade<GlassTextures.IDS.length;shade++){
                    NBTTagCompound values=settings(material,tint,stretch);values.setInteger(ProgrammableSettings.GLASS_SHADE,shade);require(ProgrammableSettings.apply(world,BASE,values,player),"apply "+id);
                    NBTTagCompound captured=ProgrammableSettings.capture(world,BASE);require(captured.getInteger(ProgrammableSettings.GLASS_MATERIAL)==material && captured.getInteger(ProgrammableSettings.GLASS_SHADE)==shade && captured.getInteger(ProgrammableSettings.GLASS_TINT)==tint,"palette capture "+id);
                    require(captured.getCompoundTag(ProgrammableSettings.GLASS_LAYOUT).getBoolean("Stretch")==stretch,"layout capture");
                    TileEntity restored=block.createTileEntity(world,block.getDefaultState());restored.readFromNBT(raw.writeToNBT(new NBTTagCompound()));
                    if(restored instanceof TileEntityProgrammableGlass)require(((TileEntityProgrammableGlass)restored).isStretch()==stretch,"regular NBT layout");
                    else {TileEntityAnimatedScreenSelector tile=(TileEntityAnimatedScreenSelector)restored;require(tile.isGlassStretch()==stretch && tile.getGlassShade()==shade && tile.getGlassTint()==tint && tile.getGlassMaterial()==material,"window NBT");require(tile.getHousingTexture()==8 && tile.getSideTexture()==26,"window configuration changed main/side");}
                    ItemStack picked=block.getPickBlock(world.getBlockState(BASE),null,world,BASE,player),crafted=ProgrammableSettings.applyToItem(picked,values);
                    NBTTagCompound item=crafted.getSubCompound(raw instanceof TileEntityProgrammableGlass?"ProgrammableGlassSettings":"BlockEntityTag");
                    require(item!=null && item.getBoolean(raw instanceof TileEntityProgrammableGlass?"Stretch":"GlassStretch")==stretch,"configured item layout");count++;
                }
                world.setBlockToAir(BASE);
            }
            for(EnumFacing facing:EnumFacing.HORIZONTALS)for(boolean inverted:new boolean[]{false,true})for(int mode=-1;mode<3;mode++){
                clear(world);Block block=Block.getBlockFromName("vandorlabs:"+IDS[mode<0?0:1]);int rows=mode<0 || mode==1?2:1;
                IBlockState state=mode<0?block.getDefaultState().withProperty(BlockGlassWall.ROTATED,facing.getAxis()==EnumFacing.Axis.X):block.getDefaultState().withProperty(BlockProgrammableWall.FACING,facing).withProperty(BlockProgrammableWall.INVERTED,inverted);
                for(int x=0;x<2;x++)for(int y=0;y<rows;y++){
                    BlockPos p=BASE.offset(facing.rotateY(),x).up(y);if(mode==1)p=p.offset(inverted?facing:facing.getOpposite(),y);
                    world.setBlockState(p,state,3);TileEntity tile=world.getTileEntity(p);if(tile instanceof TileEntityAnimatedScreenSelector)((TileEntityAnimatedScreenSelector)tile).setDiagonalGeometry(mode,0);
                    require(ProgrammableSettings.apply(world,p,settings(0,2,true),player),"group setup");
                }
                TileEntity root=world.getTileEntity(BASE);GlassSurfaceGroups.Layout layout=GlassSurfaceGroups.layout(root);require(layout.columns==2 && layout.rows==rows,"joined rectangle "+facing+":"+mode+":"+inverted+" actual="+layout.key());
                if(root instanceof TileEntityProgrammableGlass)((TileEntityProgrammableGlass)root).setJoin(false);else ((TileEntityAnimatedScreenSelector)root).setGlassLayout(true,false);
                layout=GlassSurfaceGroups.layout(root);require(layout.columns==1 && layout.rows==1,"Join Off has local artwork");
            }
            NBTTagCompound captured=settings(0,1,true);int index=-1;for(int i=0;i<DuplifierApplyOptions.OPTIONS.length;i++)if(DuplifierApplyOptions.OPTIONS[i].key.equals(ProgrammableSettings.GLASS_MATERIAL))index=i;
            NBTTagCompound masked=DuplifierApplyOptions.selected(captured,DuplifierApplyOptions.ALL&~(1L<<index));require(!masked.hasKey(ProgrammableSettings.GLASS_MATERIAL) && !masked.hasKey(ProgrammableSettings.GLASS_LAYOUT),"material copy mask includes layout");
        }finally{clear(world);}
        System.out.println("[vandorlabs][reprolab] joined-glass-settings PASS families=5 variants="+count+" groups=32 facings=4 slopes=3 Join-off main-side-independent configured-items copy-mask");
    }
    static void buildGallery(World world){
        for(int type=0;type<4;type++)for(int mode=0;mode<2;mode++)for(int x=0;x<3;x++)for(int y=0;y<2;y++){
            Block block=Block.getBlockFromName("vandorlabs:"+IDS[type]);BlockPos p=new BlockPos(88+mode*6+x,5+y,40+type*8+(type%2==1?y:0));
            world.setBlockState(p,block.getDefaultState(),3);TileEntity tile=world.getTileEntity(p);
            if(tile instanceof TileEntityAnimatedScreenSelector){TileEntityAnimatedScreenSelector window=(TileEntityAnimatedScreenSelector)tile;if(type%2==1)window.setDiagonalGeometry(1,0);window.setHousingTexture(8);window.setSideTexture(26);}
            ProgrammableSettings.apply(world,p,settings(0,2,mode==1));if(y==0)world.setBlockState(p.down(),net.minecraft.init.Blocks.STONE.getDefaultState(),3);
        }
        for(int type=2;type<4;type++){BlockPos p=new BlockPos(106,5,40+type*8);world.setBlockState(p,Block.getBlockFromName("vandorlabs:"+IDS[type]).getDefaultState(),3);TileEntityAnimatedScreenSelector tile=(TileEntityAnimatedScreenSelector)world.getTileEntity(p);tile.setHousingTexture(8);tile.setSideTexture(26);ProgrammableSettings.apply(world,p,settings(0,2,false));}
        int pipes=Arrays.asList(ScreenHousingTextures.IDS).indexOf("wall_pipes");require(pipes>=0,"Wall Pipes material exists");
        for(int x=0;x<3;x++)for(int y=0;y<3;y++){
            BlockPos p=new BlockPos(140+x,5+y,40);world.setBlockState(p,ModBlocks.PROGRAMMABLE_PORTHOLE_WALL.getDefaultState(),3);
            TileEntityAnimatedScreenSelector tile=(TileEntityAnimatedScreenSelector)world.getTileEntity(p);
            tile.setHousingTexture(2);tile.setSideTexture(26);tile.setPortholeShape(PortholeHex.OCTAGON);
            ProgrammableSettings.apply(world,p,settings(pipes,2,true));
            if(y==0)world.setBlockState(p.down(),net.minecraft.init.Blocks.STONE.getDefaultState(),3);
        }
    }
    static void renderChecks(World world)throws Exception{
        net.minecraft.client.renderer.texture.TextureAtlasSprite sprite=net.minecraft.client.Minecraft.getMinecraft().getTextureMapBlocks().getAtlasSprite(ScreenHousingTextures.texture(0));
        StaticSurfaceMesh.Capture capture=StaticSurfaceMesh.capture();WorldSurface.quad(capture,sprite,false,new double[]{0,0,0,0,16},new double[]{16,0,0,16,16},new double[]{16,16,0,16,0},new double[]{0,16,0,0,0});StaticSurfaceMesh source=capture.finish();
        StaticSurfaceMesh left=GlassSurfaceMapping.map(source,sprite,new GlassSurfaceGroups.Layout(0,0,2,2),false,0,0),right=GlassSurfaceMapping.map(source,sprite,new GlassSurfaceGroups.Layout(1,0,2,2),false,0,0),above=GlassSurfaceMapping.map(source,sprite,new GlassSurfaceGroups.Layout(0,1,2,2),false,0,0);
        java.lang.reflect.Field field=StaticSurfaceMesh.class.getDeclaredField("vertices");field.setAccessible(true);float[] a=(float[])field.get(left),b=(float[])field.get(right),c=(float[])field.get(above),original=(float[])field.get(source);
        require(Math.abs(a[11]-b[3])<1e-6 && Math.abs(a[20]-c[4])<1e-6,"horizontal/vertical UV seams");
        for(int i=0;i<a.length;i++)if(i%8!=3 && i%8!=4)require(a[i]==original[i],"UV mapping moved a vertex or normal");
        require(left.samplesOnly(sprite) && right.samplesOnly(sprite) && above.samplesOnly(sprite),"stretched UV leaves atlas sprite");
        for(int type=2;type<4;type++)for(int x=0;x<3;x++)for(int y=0;y<2;y++){
            BlockPos pos=new BlockPos(94+x,5+y,40+type*8+(type%2==1?y:0));TileEntityAnimatedScreenSelector tile=(TileEntityAnimatedScreenSelector)world.getTileEntity(pos);IBlockState state=world.getBlockState(pos);
            TEAnimatedScreenSelector.PortholeGroup group=TEAnimatedScreenSelector.portholeGroup(tile,state);require(group.columns==3 && group.rows==2,"joined porthole window extent");
            net.minecraft.client.renderer.texture.TextureAtlasSprite wall=net.minecraft.client.Minecraft.getMinecraft().getTextureMapBlocks().getAtlasSprite(ScreenHousingTextures.texture(tile.getHousingTexture()));
            PortholeHex.Slice slice=group.slice(pos,state.getValue(BlockProgrammableWall.FACING).rotateY(),EnumFacing.UP);StaticSurfaceMesh raw=DiagonalWallMeshCache.glass(tile,state,wall,wall,slice);
            require(PortholePane.mesh(tile,state,raw).samplesOnly(sprite),"porthole material atlas bounds");
        }
        System.out.println("[vandorlabs][reprolab] joined-glass-UV PASS horizontal-vertical-seams unchanged-geometry atlas-bounds port-window=3x2");
    }
    private JoinedGlassRuntimeChecks(){}
}
