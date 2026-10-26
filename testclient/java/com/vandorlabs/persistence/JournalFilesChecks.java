package com.vandorlabs.persistence;

import java.io.IOException;
import java.nio.file.*;

public final class JournalFilesChecks {
    public static void main(String[] args)throws Exception {
        Path directory=Files.createTempDirectory("vandor-journal-platform-");
        try {
            Path absent=directory.resolve("absent");
            // Windows must never attempt the unsupported directory open, even after a valid rename.
            JournalFiles.syncDirectory(absent,true);
            JournalFiles.syncDirectory(directory);
            if(java.io.File.separatorChar!='\\') {
                JournalFiles.syncDirectory(directory,false);
                boolean failed=false;
                try{JournalFiles.syncDirectory(absent,false);}catch(IOException expected){failed=true;}
                if(!failed)throw new AssertionError("Linux directory I/O failures must propagate");
            }
            System.out.println("Journal filesystem PASS Windows no-directory-handle policy Linux real directory sync and I/O failure propagation");
        } finally {Files.delete(directory);}
    }
}
