package org.skife.gressil;

import com.google.common.base.Charsets;
import com.google.common.io.Files;
import jnr.posix.POSIX;
import jnr.posix.POSIXFactory;
import jnr.posix.util.DefaultPOSIXHandler;
import org.junit.Test;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.util.Arrays;
import java.util.List;

import static java.util.Arrays.asList;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

public class DaemonTest
{
    public static void main(String[] args) throws Exception
    {
        if (args.length > 0) {
            for (String arg : args) {
                File file = new File(arg);
                Files.touch(file);
            }
        }

        File out = new File("/tmp/gressil.out");
        File err = new File("/tmp/gressil.err");
        File pid = new File("/tmp/gressil.pid");
        out.delete();
        err.delete();
        pid.delete();

        Status status = new Daemon().withPidFile(pid)
                                   .withStdout(out)
                                   .withStderr(err)
                                   .withExtraProgramArgs("/tmp/touchme")
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

            assertThat(new File("/tmp/touchme").exists(), is(true));

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
