package com.vandorlabs.copier.core;

import com.vandorlabs.redstone.ChannelList;
import com.vandorlabs.redstone.ChannelRange;
import com.vandorlabs.turntable.TurntableGeometry;
import com.vandorlabs.surface.core.SurfaceLayout;
import java.io.*;
import java.util.*;
import java.util.zip.*;

/** Independent released algorithms, retained here rather than comparing two adapters to the same implementation. */
public final class CopyPolicyTest {
    private static long checks;
    private static void need(boolean value,String message){checks++;if(!value)throw new AssertionError(message);}
    public static void main(String[] ignored)throws Exception{geometry();allocation();settings();codec();System.out.println("Copy shared policy PASS "+checks+" checks (released volume/centering/withdrawal/settings/chip codec)");}
    private static int[] oldVolume(TurntableGeometry.Face face,int u,int v,int plane,int width,int height,int depth){
        double[] first=face.point(u,v,plane),last=face.point(u+width-1,v+height-1,plane);int[] a={(int)Math.floor(first[0])+face.x,(int)Math.floor(first[1])+face.y,(int)Math.floor(first[2])+face.z};int[] b={(int)Math.floor(last[0])+face.x*depth,(int)Math.floor(last[1])+face.y*depth,(int)Math.floor(last[2])+face.z*depth};return new int[]{Math.min(a[0],b[0]),Math.min(a[1],b[1]),Math.min(a[2],b[2]),Math.max(a[0],b[0]),Math.max(a[1],b[1]),Math.max(a[2],b[2])};
    }
    private static int[] oldCenter(int[] source,int[] volume,int normal){int[] shift=new int[3];for(int axis=0;axis<3;axis++){int available=volume[axis+3]-volume[axis]+1,span=source[axis+3]-source[axis]+1;if((normal==axis?source[axis+3]+1:span)>available)throw new IllegalArgumentException();if(normal!=axis)shift[axis]=(available-span)/2-source[axis];}return shift;}
    private static void geometry(){
        for(TurntableGeometry.Face face:TurntableGeometry.Face.values())for(int width=1;width<=32;width++)for(int height=1;height<=32;height++)for(int depth=1;depth<=16;depth++){
            int u=width%2==0?-31:47,v=height%2==0?19:-23,plane=depth%2==0?-63:70;
            need(Arrays.equals(oldVolume(face,u,v,plane,width,height,depth),SurfaceLayout.volume(face,u,v,plane,width,height,depth)),"released two-corner scan prism parity");
        }
        for(int axis=0;axis<3;axis++)for(int width=1;width<=32;width++)for(int height=1;height<=16;height++)for(int source=1;source<=8;source++){
            int[] box={13,70,-45,13+width-1,70+height-1,-45+width-1},design={2,0,3,2+source-1,source-1,3+source-1};int[] expected=null,actual=null;boolean oldRefused=false,newRefused=false;try{expected=oldCenter(design,box,axis);}catch(IllegalArgumentException noFit){oldRefused=true;}try{actual=CopyGeometry.center(design,box,axis);}catch(IllegalArgumentException noFit){newRefused=true;}need(oldRefused==newRefused&&(oldRefused||Arrays.equals(expected,actual)),"released blueprint normal/plane centering parity");
        }
    }
    private static final class Failure {final int need;final long count;Failure(int need,long count){this.need=need;this.count=count;}}
    private static Object oldRemaining(long[] needs,int[] available,boolean[][] matching){int[] result=available.clone();for(int need=0;need<needs.length;need++){long missing=needs[need];for(int slot=0;slot<result.length;slot++){if(!matching[need][slot])continue;int used=(int)Math.min(missing,result[slot]);result[slot]-=used;missing-=used;if(missing==0)break;}if(missing>0)return new Failure(need,missing);}return result;}
    private static void allocation(){
        Random random=new Random(0x51ccab);for(int attempt=0;attempt<1024;attempt++){int slots=random.nextInt(55),kinds=random.nextInt(12);int[] available=new int[slots];for(int i=0;i<slots;i++)available[i]=random.nextInt(65);long[] costs=new long[kinds];boolean[][] matching=new boolean[kinds][slots];for(int n=0;n<kinds;n++){costs[n]=random.nextInt(193);for(int s=0;s<slots;s++)matching[n][s]=random.nextBoolean();}int[] original=available.clone();Object expected=oldRemaining(costs,available,matching),actual;try{actual=MaterialWithdrawal.remaining(costs,available,(n,s)->matching[n][s]);}catch(MaterialWithdrawal.Missing missing){actual=new Failure(missing.need,missing.count);}need(Arrays.equals(available,original),"preflight never withdraws from caller's slots");need(expected instanceof Failure?actual instanceof Failure&&((Failure)expected).need==((Failure)actual).need&&((Failure)expected).count==((Failure)actual).count:actual instanceof int[]&&Arrays.equals((int[])expected,(int[])actual),"released greedy slot ordering and exact missing count");}
    }
    private static void settings(){
        for(int height:new int[]{0,1,4,16,17})for(int increment:new int[]{Integer.MIN_VALUE,-ChannelRange.MAX_INCREMENT,-1,0,1,ChannelRange.MAX_INCREMENT,Integer.MAX_VALUE})for(int mode=-1;mode<=3;mode++)for(ChannelList bank:new ChannelList[]{null,ChannelList.EMPTY,ChannelList.of(1,7)}){
            boolean valid=height>=1&&height<=16&&ChannelRange.validIncrement(increment)&&mode>=0&&mode<=2&&bank!=null;CopySettings value=null;try{value=new CopySettings(height,increment,mode,bank,false);}catch(IllegalArgumentException expected){need(!valid,"released settings validation");}if(value!=null){need(valid,"released settings acceptance");for(boolean signal:new boolean[]{false,true})need(value.active(signal)==(mode!=0&&(mode==1)==signal),"released trigger rule");for(int otherMode=0;otherMode<=2;otherMode++)for(ChannelList otherBank:new ChannelList[]{ChannelList.EMPTY,ChannelList.of(7,1),ChannelList.of(8)})need(value.changesEdgeBank(new CopySettings(2,0,otherMode,otherBank,true))==(mode!=otherMode||!bank.equals(otherBank)),"only activation/channel changes reset edge history");}
        }
    }
    private static byte[] oldDecode(int codec,byte[] source)throws IOException{if(codec==0){if(source.length>4*1024*1024)throw new IOException();return source.clone();}if(codec!=1||source.length>768*1024)throw new IllegalArgumentException();ByteArrayOutputStream output=new ByteArrayOutputStream();try(InputStream input=new GZIPInputStream(new ByteArrayInputStream(source))){byte[] buffer=new byte[8192];int count;while((count=input.read(buffer))!=-1){if(output.size()>4*1024*1024-count)throw new IOException();output.write(buffer,0,count);}}return output.toByteArray();}
    private static byte[] gzip(byte[] input)throws IOException{ByteArrayOutputStream bytes=new ByteArrayOutputStream();try(OutputStream output=new GZIPOutputStream(bytes)){output.write(input);}return bytes.toByteArray();}
    private static int result(int codec,byte[] input,boolean old)throws IOException{try{byte[] result=old?oldDecode(codec,input):BlueprintCompression.decode(codec,input);return Arrays.hashCode(result);}catch(IllegalArgumentException refused){return 0x123456;}catch(IOException malformed){return 0x654321;}}
    private static void codec()throws IOException{
        Random random=new Random(391);for(int length:new int[]{0,1,32,8191,8192,8193,65536}){byte[] bytes=new byte[length];random.nextBytes(bytes);for(int codec:new int[]{0,1,2}){byte[] input=codec==1?gzip(bytes):bytes;need(result(codec,input,true)==result(codec,input,false),"released raw/GZIP codec behavior");}byte[] output=BlueprintCompression.decode(0,bytes);need(output!=bytes&&Arrays.equals(output,bytes),"chip raw decode ownership copy");}
        for(byte[] invalid:new byte[][]{new byte[0],new byte[]{1,2,3},gzip(new byte[4*1024*1024+1]),new byte[768*1024+1]})need(result(1,invalid,true)==result(1,invalid,false),"released corrupt/compressed/expanded bounds");need(result(0,new byte[4*1024*1024+1],true)==result(0,new byte[4*1024*1024+1],false),"released raw design bound");
    }
    private CopyPolicyTest(){}
}
