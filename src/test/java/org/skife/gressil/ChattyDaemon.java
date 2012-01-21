package org.skife.gressil;

import java.io.File;
import java.io.IOException;
import java.util.Date;

public class ChattyDaemon
{
    public static void main(String[] args) throws IOException
    {
        new Spawn().withPidFile(new File("/tmp/chatty.pid"))
                    .withStdout(new File("/tmp/chatty.out"))
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
