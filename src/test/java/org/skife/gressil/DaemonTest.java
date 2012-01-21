package org.skife.gressil;

import jnr.posix.POSIX;
import jnr.posix.POSIXFactory;
import org.junit.Test;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.management.ManagementFactory;
import java.util.Arrays;
import java.util.List;

import static java.util.Arrays.asList;

public class DaemonTest
{
    @Test
    public void testFoo() throws Exception
    {
        POSIX posix = POSIXFactory.getPOSIX(new MyPosixHandler(), true);

        List<POSIX.SpawnFileAction> actions = asList();

        List<String> argv = asList("/bin/ls", "/tmp");

        List<String> envp = asList();

        String jh = System.getProperty("java.home");
        System.out.println(jh);


        int rs = posix.posix_spawnp("/bin/ls", actions, argv, envp);
        System.out.println(rs);
    }

    @Test
    public void testGetCommandLine() throws Exception
    {
        String pid = String.valueOf(POSIXFactory.getPOSIX().getpid());

        Process p = new ProcessBuilder().command("ps", "-p", pid, "-o", "command").start();
        BufferedReader in = new BufferedReader(new InputStreamReader(p.getInputStream()));
        String line;
        while ((line = in.readLine()) != null) {
            System.out.println(line);
        }
    }

    public static void main(String[] args) throws Exception
    {
        POSIX posix = POSIXFactory.getPOSIX();
        System.out.println(posix.getppid() + " -> " + posix.getpid());
        Daemon d = Daemon.builder(args)
                         .build();
        int child = d.daemonize();
        if (child > 0) {
            // parent
            posix.waitpid(child, new int[]{0}, 0);
        }
        System.out.println("WOOOT");
    }

}
