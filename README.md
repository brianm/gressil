Gressil uses [jnr-posix](https://github.com/jnr/jnr-posix) to provide
daemonization and "forking" for Java processes. It uses
<code>posix_spawn</code> to achieve this, rather than
<code>fork</code> and <code>exec</code>. Spawn is used, rather than
the standard C world idiom of <code>fork</code> followed by
<code>exec</code> as <code>fork</code> is very unsafe on the JVM --
there is no such thing as a critical section which cannot get splatted
by GC reshuffling pointers.

Usage for daemonization looks like:

```java
package org.skife.gressil;

import java.io.File;
import java.io.IOException;
import java.util.Date;

public class ChattyDaemon
{
    public static void main(String[] args) throws IOException
    {
        new Daemon().withPidFile(new File("/tmp/chatty.pid"))
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
```
In the parent process the call to <code>Daemon#daemonize()</code> will
call <code>System.exit()</code>, in the child process it will return
normally.
            
The child process is started by spawning a new process complete with
command line, *not* by forking the state of the parent process.        
