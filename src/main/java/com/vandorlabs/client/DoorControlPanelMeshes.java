package com.vandorlabs.client;

import com.vandorlabs.render.SpaceDoorControlPanel;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import java.util.Arrays;
import java.util.function.Function;

/** Eight tiny immutable pad variants; world lighting and shader records stay live. */
final class DoorControlPanelMeshes {
    private static final StaticSurfaceMesh[] MESHES=new StaticSurfaceMesh[8];
    private static final StaticSurfaceMesh EMPTY=StaticSurfaceMesh.independentCapture().finish();
    private static final Function<String,TextureAtlasSprite> ATLAS=
            name->Minecraft.getMinecraft().getTextureMapBlocks().getAtlasSprite(name);
    private DoorControlPanelMeshes(){}
    static StaticSurfaceMesh get(boolean sliding,boolean farEdge,SpaceDoorControlPanel.Side side){
        return get(sliding,farEdge,side,ATLAS);
    }
    static StaticSurfaceMesh get(boolean sliding,boolean farEdge,SpaceDoorControlPanel.Side side,
            Function<String,TextureAtlasSprite> sprites){
        if(side==SpaceDoorControlPanel.Side.NONE)return EMPTY;
        int key=side.ordinal()*4+(sliding?2:0)+(farEdge?1:0);
        StaticSurfaceMesh mesh=MESHES[key];
        if(mesh==null){
            StaticSurfaceMesh.Capture capture=StaticSurfaceMesh.independentCapture();
            TESlidingDoor.panelGeometry(capture,sliding,farEdge,side,
                    sprites.apply("vandorlabs:blocks/control_buttons"),
                    sprites.apply("vandorlabs:blocks/dark_wall_panel"));
            mesh=capture.finish();MESHES[key]=mesh;
        }
        return mesh;
    }
    static void clear(){Arrays.fill(MESHES,null);}
}
