package com.vandorlabs.client;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.command.WrongUsageException;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentString;

/** Client-only capture; it does not move the camera, change graphics or edit the world. */
public final class CommandGlassProfile extends CommandBase {
    @Override public String getName(){return "vandorglassprofile";}
    @Override public String getUsage(ICommandSender sender){return "/vandorglassprofile start [frames]|stop|status";}
    @Override public int getRequiredPermissionLevel(){return 0;}
    @Override public void execute(MinecraftServer server,ICommandSender sender,String[] args)throws CommandException{
        if(args.length==0||args.length>2)throw new WrongUsageException(getUsage(sender));
        if(args[0].equals("start")){
            int frames=args.length==2?parseInt(args[1],1,10000):600;
            if(GlassPaneGpuTimers.active()||GlassPaneGpuTimers.draining())throw new CommandException("A glass profile is already recording or awaiting GPU results");
            GlassPaneGpuTimers.start(frames);
            String name="glass-profile-"+LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"))+".json";
            Path path=Minecraft.getMinecraft().mcDataDir.toPath().resolve("vandorlabs-profiles").resolve(name);
            GlassPaneGpuTimers.automaticOutput(path);
            sender.sendMessage(new TextComponentString("Recording "+frames+" frames. Keep the same view and settings for comparisons. Result: "+path));
        }else if(args[0].equals("stop")&&args.length==1){
            GlassPaneGpuTimers.stop();sender.sendMessage(new TextComponentString(GlassPaneGpuTimers.status()));
        }else if(args[0].equals("status")&&args.length==1){
            GlassPaneGpuTimers.poll();sender.sendMessage(new TextComponentString(GlassPaneGpuTimers.status()));
            sender.sendMessage(new TextComponentString(GlassPaneDiagnostics.report()));
        }else throw new WrongUsageException(getUsage(sender));
    }
    @Override public List<String> getTabCompletions(MinecraftServer server,ICommandSender sender,String[] args,BlockPos pos){
        return args.length==1?getListOfStringsMatchingLastWord(args,Arrays.asList("start","stop","status")):Collections.emptyList();
    }
}
