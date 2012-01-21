package org.skife.gressil;

import com.google.common.base.Charsets;
import com.google.common.io.Files;
import jnr.posix.POSIX;
import jnr.posix.POSIXFactory;
import org.junit.Test;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.util.List;

import static java.util.Arrays.asList;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.junit.Assert.assertThat;

public class SpawnTest
{
    @Test
    public void testFoo() throws Exception
    {
        POSIX posix = POSIXFactory.getPOSIX();

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
        File out = new File("/tmp/gressil.out");
        File err = new File("/tmp/gressil.err");
        File pid = new File("/tmp/gressil.pid");
        out.delete();
        err.delete();
        pid.delete();

        Status status = new Spawn().withPidFile(pid)
            .withStdout(out)
            .withStderr(err)
            .spawnSelf();

        if (status.isParent()) {
            // parent
            while ((!out.exists()) && (!err.exists()) && (!pid.exists())) {
                Thread.sleep(10);
            }

            String out_msg = Files.readFirstLine(out, Charsets.US_ASCII);
            assertThat(out_msg, equalTo("out out"));

            String err_msg = Files.readFirstLine(err, Charsets.US_ASCII);
            assertThat(err_msg, equalTo("err err"));

            int child_pid = Integer.parseInt(Files.readFirstLine(pid, Charsets.US_ASCII));
            assertThat(child_pid, equalTo(status.getChildPid()));

            System.out.printf("child pid is %d\n", status.getChildPid());
            POSIXFactory.getPOSIX().kill(status.getChildPid(), 15);

            out.delete();
            err.delete();
            pid.delete();

            System.out.println("PASSED");
        }
        else {
            // child
            System.out.println("out out");
            System.err.println("err err");
            Thread.currentThread().join();
        }

    }

}
