package org.skife.gressil;

import java.io.File;
import java.io.IOException;
import java.util.Date;

import static org.skife.gressil.Spawn.remoteDebugOnPort;

public class ChattyDaemon
{
    public static void main(String[] args) throws IOException
    {
        new Spawn().withPidFile(new File("/tmp/chatty.pid"))
                   .withStdout(new File("/tmp/chatty.out"))
                   .withExtraVmArgs(remoteDebugOnPort(5005))
            .daemonize();

        while (!Thread.currentThread().isInterrupted()) {
            System.out.println(new Date());
            try {
                Thread.sleep(1000);
            }
            catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }
}
