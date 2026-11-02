package com.vandorlabs.client;

import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.util.math.BlockPos;
import org.lwjgl.opengl.*;
import java.io.File;
import java.nio.FloatBuffer;

/** Native rain-column invalidation and the actual scoped shader uniform operation. */
final class CanopyWeatherChecks {
    private static void require(boolean ok,String message){if(!ok)throw new IllegalStateException("canopy-weather: "+message);}
    private static float uniform(int program,int location){FloatBuffer value=org.lwjgl.BufferUtils.createFloatBuffer(1);GL20.glGetUniform(program,location,value);return value.get(0);}
    private static int shader(int type,String code){int shader=GL20.glCreateShader(type);GL20.glShaderSource(shader,code);GL20.glCompileShader(shader);require(GL20.glGetShaderi(shader,GL20.GL_COMPILE_STATUS)!=0,GL20.glGetShaderInfoLog(shader,4096));return shader;}
    static void run(Minecraft mc,File output){
        BlockPos roof=new BlockPos(mc.player.getPosition().getX()+2,180,mc.player.getPosition().getZ()+2),inside=roof.down(2);mc.world.getPrecipitationHeight(inside);
        try{mc.world.setBlockState(roof,Block.getBlockFromName("vandorlabs:regular_canopy_glass").getDefaultState(),3);require(mc.world.getPrecipitationHeight(inside).getY()==181,"fixed glass did not stop the native rain column");require(CanopyRainShelter.sheltered(mc.world,inside),"transparent fixed roof not recognized");require(!CanopyRainShelter.sheltered(mc.world,roof.up(2)),"sky above roof considered sheltered");}
        finally{mc.world.setBlockToAir(roof);}require(!CanopyRainShelter.sheltered(mc.world,inside),"removed roof retained rain shelter");
        int previous=GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM),vertex=shader(GL20.GL_VERTEX_SHADER,"#version 120\nvoid main(){gl_Position=ftransform();}"),fragment=shader(GL20.GL_FRAGMENT_SHADER,"#version 120\nuniform float eyeBrightnessM2; void main(){gl_FragColor=vec4(eyeBrightnessM2,0.0,0.0,1.0);}"),program=GL20.glCreateProgram();
        try{GL20.glAttachShader(program,vertex);GL20.glAttachShader(program,fragment);GL20.glLinkProgram(program);require(GL20.glGetProgrami(program,GL20.GL_LINK_STATUS)!=0,"uniform test program did not link");GL20.glUseProgram(program);int location=GL20.glGetUniformLocation(program,"eyeBrightnessM2");GL20.glUniform1f(location,.75F);
            CanopyRainShelter.withoutWind(()->require(uniform(program,location)==0,"rain wind not disabled in active pass"));require(uniform(program,location)==.75F,"rain uniform leaked into another pass");
            try{CanopyRainShelter.withoutWind(()->{throw new IllegalArgumentException("fixture failure");});throw new IllegalStateException("expected failure missing");}catch(IllegalArgumentException expected){}require(uniform(program,location)==.75F,"exception did not restore rain uniform");
        }finally{GL20.glUseProgram(previous);GL20.glDeleteProgram(program);GL20.glDeleteShader(vertex);GL20.glDeleteShader(fragment);}
        System.out.println("[vandorlabs][reprolab] canopy-weather PASS native-rain-height transparent-roof removal scoped-weather-uniform exception-restoration hardware-pack-pending");
    }
    private CanopyWeatherChecks(){}
}
