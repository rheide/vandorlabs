package com.vandorlabs.surface;

/** Axis restrictions and owner-directed coordinate filters, including negative coordinates. */
public final class TextureJoinSettingsTest {
    private static void need(boolean value,String stage){if(!value)throw new AssertionError(stage);}
    public static void main(String[] args){
        com.vandorlabs.persistence.TextureJoinDataTest.run();
        int checks=0;
        for(int direction=0;direction<TextureJoinSettings.COUNT;direction++)for(int parity=0;parity<3;parity++)
            for(int coordinate=-6;coordinate<=6;coordinate++)for(int axis=0;axis<3;axis++)for(int step:new int[]{-1,1}){
                int[] from={coordinate,coordinate,coordinate},to=from.clone();to[axis]+=step;
                boolean allowed=direction==1||direction==2&&axis==1||direction==3&&axis!=1||direction==4&&axis==2||direction==5&&axis==0;
                if(direction>=2&&parity!=0)allowed&=Math.floorMod(to[axis],2)==(parity==1?0:1);
                boolean actual=new TextureJoinSettings(direction,parity).connects(from[0],from[1],from[2],to[0],to[1],to[2]);
                need(actual==allowed,"direction="+direction+" parity="+parity+" coordinate="+coordinate+" axis="+axis+" step="+step);checks++;
            }
        TextureJoinSettings even=new TextureJoinSettings(TextureJoinSettings.EAST_WEST,TextureJoinSettings.EVEN);
        need(even.connects(3,0,0,2,0,0)&&even.connects(3,0,0,4,0,0),"owner Even at 3 joins both even neighbours");
        need(!even.connects(2,0,0,3,0,0),"candidate parity is directed");
        need(!even.connects(3,0,0,6,0,0),"nonadjacent candidate refused");
        need(new TextureJoinSettings(2,1).connects(0,3,0,0,4,1),"sloped vertical edge filters Y rather than incidental depth");
        need(!new TextureJoinSettings(3,0).connects(0,3,0,0,4,1),"horizontal refuses rising sloped edge");
        need(even.cycleDirection(-1).direction==TextureJoinSettings.NORTH_SOUTH,"reverse direction cycling");
        need(even.disabled()==new TextureJoinSettings(2,1).disabled(),"inactive host lookups reuse immutable parity profiles");
        need(new TextureJoinSettings(2,0).cycleParity(-1).parity==2,"reverse parity cycling");
        System.out.println("Texture join settings PASS "+checks+" axis/negative-coordinate cases and owner-directed Even/Odd examples");
    }
}
