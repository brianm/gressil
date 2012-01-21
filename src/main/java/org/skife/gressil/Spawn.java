package org.skife.gressil;

import jnr.posix.POSIX;
import jnr.posix.POSIXFactory;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static java.lang.String.format;
import static java.util.Arrays.asList;

public class Spawn
{
    private final File pidfile;
    private final File out;
    private final File err;
    private final List<String> extraVmArgs;
    private final List<String> extraProgramArgs;

    public Spawn()
    {
        this(null,
             new File("/dev/null"),
             new File("/dev/null"),
             Collections.<String>emptyList(),
             Collections.<String>emptyList());
    }

    private Spawn(File pidfile, File out, File err, List<String> extraVmArgs, List<String> extraProgramArgs)
    {
        this.pidfile = pidfile;
        this.out = out;
        this.err = err;
        this.extraVmArgs = extraVmArgs;
        this.extraProgramArgs = extraProgramArgs;
    }

    public Spawn withExtraVmArgs(List<String> extraVmArgs)
    {
        return new Spawn(pidfile, out, err, extraVmArgs, extraProgramArgs);
    }

    public Spawn withExtraVmArgs(String... extraVmArgs)
    {
        return new Spawn(pidfile, out, err, asList(extraVmArgs), extraProgramArgs);
    }

    public Spawn withExtraProgramArgs(List<String> extraProgramArgs)
    {
        return new Spawn(pidfile, out, err, extraVmArgs, extraProgramArgs);
    }

    public Spawn withExtraProgramArgs(String... extraProgramArgs)
    {
        return new Spawn(pidfile, out, err, extraVmArgs, asList(extraProgramArgs));
    }

    public Spawn withPidFile(File pidfile)
    {
        return new Spawn(pidfile, out, err, extraVmArgs, extraProgramArgs);
    }

    public Spawn withStdout(File out)
    {
        return new Spawn(pidfile, out, err, extraVmArgs, extraProgramArgs);
    }

    public Spawn withStderr(File err)
    {
        return new Spawn(pidfile, out, err, extraVmArgs, extraProgramArgs);
    }

    public Status spawnSelf() throws IOException
    {
        POSIX posix = POSIXFactory.getPOSIX();
        if (isDaemon()) {
            posix.setsid();

            OutputStream old_out = System.out;
            OutputStream old_err = System.err;

            System.setOut(new PrintStream(new FileOutputStream(out, true)));
            System.setErr(new PrintStream(new FileOutputStream(err, true)));
            old_err.close();
            old_out.close();

            if (pidfile != null) {
                FileOutputStream p_out = new FileOutputStream(pidfile);
                p_out.write(String.valueOf(posix.getpid()).getBytes());
                p_out.close();
            }

            return Status.child(posix.getpid());
        }
        else {
            List<POSIX.SpawnFileAction> close_streams = asList();

            List<String> envp = getEnv();
            envp.add(Spawn.class.getName() + "=daemon");

            List<String> argv = buildARGV();
            int child_pid = posix.posix_spawnp(argv.get(0), close_streams, buildARGV(), envp);
            return Status.parent(child_pid);
        }
    }

    public void daemonize() throws IOException
    {
        if (spawnSelf().isParent()) {
            System.exit(0);
        }
    }

    public static boolean isDaemon()
    {
        return "daemon".equals(System.getenv(Spawn.class.getName()));
    }

    public List<String> buildARGV()
    {
        List<String> ARGV = new ArrayList<String>();
        String java = System.getProperty("java.home") + "/bin/java";
        ARGV.add(java);
        List<String> its = ManagementFactory.getRuntimeMXBean().getInputArguments();
        List<String> fixed = new ArrayList<String>();
        StringBuilder current = new StringBuilder();
        for (String it : its) {
            if (it.startsWith("-")) {
                if (current.length() > 0) {
                    fixed.add(current.toString());
                    current = new StringBuilder();
                }
                current.append(it);
            }
            else {
                current.append("\\ ").append(it);
            }
        }
        ARGV.addAll(fixed);
        ARGV.addAll(extraVmArgs);
        String[] java_sun_command = System.getProperty("sun.java.command").split(" ");
        if (java_sun_command[0].endsWith(".jar")) {
            ARGV.add("-jar");
            ARGV.add(new File(java_sun_command[0]).getPath());
        }
        else {
            // else it's a .class, add the classpath and mainClass
            ARGV.add("-cp");
            String raw_cp = System.getProperty("java.class.path").replaceAll(" ", "\\ ");
            ARGV.add(raw_cp);
            ARGV.add(java_sun_command[0]);
        }
        ARGV.addAll(Arrays.asList(java_sun_command).subList(1, java_sun_command.length));
        ARGV.addAll(extraProgramArgs);
        System.out.println(ARGV);
        return ARGV;
    }

    public static List<String> waitForRemoteDebuggerOnPort(int port) {
        return asList("-Xdebug", format("-Xrunjdwp:transport=dt_socket,server=y,suspend=y,address=%d", port));
    }

    public List<String> getEnv()
    {
        String[] envp = new String[System.getenv().size()];
        int i = 0;
        for (Map.Entry<String, String> pair : System.getenv().entrySet()) {
            envp[i++] = new StringBuilder(pair.getKey()).append("=").append(pair.getValue()).toString();
        }
        return new ArrayList<String>(asList(envp));
    }
}
