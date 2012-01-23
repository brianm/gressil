package org.skife.gressil;

import com.google.common.base.Charsets;
import com.google.common.io.Files;
import jnr.ffi.Library;

import java.io.File;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

public class DaemonTest
{
    public static void main(String[] args) throws Exception
    {
        File out = new File("/tmp/gressil.out");
        File err = new File("/tmp/gressil.err");
        File pid = new File("/tmp/gressil.pid");
        File extra = new File("/tmp/gressil.touchme");
        File done = new File("/tmp/gressil.done");
        out.delete();
        err.delete();
        pid.delete();
        extra.delete();
        done.delete();

        if (args.length > 0) {
            for (String arg : args) {
                Files.touch(new File(arg));
            }
        }

        Status status = new Daemon().withPidFile(pid)
                                    .withStdout(out)
                                    .withStderr(err)
                                    .withExtraMainArgs(extra.getAbsolutePath())
                                    .forkish();

        if (status.isParent()) {
            // parent
            while (!done.exists()) {
                Thread.sleep(10);
            }

            String out_msg = Files.readFirstLine(out, Charsets.US_ASCII);
            assertThat(out_msg, equalTo("out out"));

            String err_msg = Files.readFirstLine(err, Charsets.US_ASCII);
            assertThat(err_msg, equalTo("err err"));

            int child_pid = Integer.parseInt(Files.readFirstLine(pid, Charsets.US_ASCII));
            assertThat(child_pid, equalTo(status.getChildPid()));

            assertThat(extra.exists(), is(true));

            System.out.printf("child pid is %d\n", status.getChildPid());
            Library.loadLibrary("c", MicroC.class).kill(status.getChildPid(), 15);

            out.delete();
            err.delete();
            pid.delete();
            extra.delete();

            System.out.println("PASSED");
        }
        else {
            // child
            System.out.println("out out");
            System.err.println("err err");
            Files.touch(done);
            Thread.currentThread().join();
        }
    }
}
