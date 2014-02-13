package org.skife.gressil;

import java.io.File;
import java.io.IOException;

/**
 * Class for testing, will be main class for jar
 */
public class Main
{
    public static void main(String[] args) throws IOException
    {
        if (args.length != 1) {
            System.err.println("java -jar gressil.jar start|stop|status");
            System.exit(1);
        }
        new Daemon().withPidFile(new File("gressil.pid"))
                    .execute(DaemonCommand.valueOf(args[0]));

        try
        {
            Thread.currentThread().join();
        }
        catch (InterruptedException e)
        {
            System.exit(0);
        }
    }
}
