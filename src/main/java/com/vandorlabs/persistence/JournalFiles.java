package com.vandorlabs.persistence;

import java.io.File;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

/** Platform-specific final step after syncing a journal file and atomically renaming it. */
public final class JournalFiles {
    public static void syncDirectory(Path directory)throws IOException {
        syncDirectory(directory,File.separatorChar=='\\');
    }
    static void syncDirectory(Path directory,boolean windows)throws IOException {
        // Windows Java 8 NIO rejects directory handles with AccessDeniedException.
        // FileDescriptor.sync and the atomic rename remain mandatory on both platforms.
        // Do not swallow real permission or I/O failures on platforms supporting directory fsync.
        if(windows)return;
        try(FileChannel channel=FileChannel.open(directory,StandardOpenOption.READ)){channel.force(true);}
    }
    private JournalFiles(){}
}
