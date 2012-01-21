Gressil uses [jnr-posix](https://github.com/jnr/jnr-posix) to provide
daemonization and "forking" for Java processes. It uses
<code>posix_spawn</code> to achieve this, rather than
<code>fork</code> and <code>exec</code>. Spawn is used, rather than
the standard C world idiom of <code>fork</code> followed by
<code>exec</code> as <code>fork</code> is very unsafe on the JVM --
there is no such thing as a critical section which cannot get splatted
by GC reshuffling pointers. Here, the child process is started by spawning
a new process complete with command line, *not* by forking the state of the
parent process.

Usage for daemonization looks like:

```java
package org.skife.gressil.examples;

import org.skife.gressil.Spawn;

import java.io.File;
import java.io.IOException;
import java.util.Date;

import static waitForRemoteDebugOnPort;

public class ChattyDaemon
{
    public static void main(String[] args) throws IOException
    {
        new Spawn().withPidFile(new File("/tmp/chatty.pid"))
                   .withStdout(new File("/tmp/chatty.out"))
                   .withExtraVmArgs(waitForRemoteDebuggerOnPort(5005))
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
```
In the parent process the call to <code>Daemon#daemonize()</code> will
call <code>System.exit()</code>, in the child process it will return
normally.

The child process, in this case, will also wait for a Java debugger to
attach on port 5005. It will attach stdout to <code>/tmp/chatty.out</code>,
and stdin and stderr will default to <code>/dev/null</code> (which stdout would also attach to
by default if it were not specified).