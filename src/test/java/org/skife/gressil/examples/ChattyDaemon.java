package org.skife.gressil.examples;

import org.skife.gressil.Daemon;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Date;

import static org.skife.gressil.Daemon.waitForRemoteDebuggerOnPort;

public class ChattyDaemon
{
    public static void main(String[] args) throws IOException
    {
        new Daemon().withMainArguments(args)
                    .withPidFile(new File("/tmp/chatty.pid"))
                    .withStdout(new File("/tmp/chatty.out"))
                    .withExtraMainArguments("hello", "world,", "how are you?")
                    .withExtraJvmArguments(waitForRemoteDebuggerOnPort(5005))
                    .daemonize();

        while (!Thread.currentThread().isInterrupted()) {
            System.out.println(new Date() + " " + Arrays.toString(args));
            try {
                Thread.sleep(1000);
            }
            catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }
}
