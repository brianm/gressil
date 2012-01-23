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

public class Daemon
{
    private final List<String> programArgs;
    private final File         pidfile;
    private final File         out;
    private final File         err;
    private final List<String> extraVmArgs;
    private final List<String> extraProgramArgs;

    public Daemon()
    {
        this(null,
             null,
             new File("/dev/null"),
             new File("/dev/null"),
             Collections.<String>emptyList(),
             Collections.<String>emptyList());
    }

    private Daemon(List<String> argv, File pidfile, File out, File err, List<String> extraVmArgs, List<String> extraProgramArgs)
    {
        this.programArgs = argv;
        this.pidfile = pidfile;
        this.out = out;
        this.err = err;
        this.extraVmArgs = extraVmArgs;
        this.extraProgramArgs = extraProgramArgs;
    }

    /**
     * tl;dr pass the String[] received in your public static void main(String[] args) call here.
     * <p/>
     * This is the preferred way to get the program arguments. The argument here should be the same
     * array of strings as was passed to main(String[] args).
     * <p/>
     * If the args are *not* passed here, we will attempt to figure out what they are by poking
     * around the JVM, but the "poke around the JVM" method gets confused by whitespace in argument
     * names (ie, quoted arguments) which in the real ARGV will be one element, but via the poke around
     * the JVM method of figuring out program args the whitespace will lead to it being two arguments.
     * This usually leads to undesired behavior.
     */
    public Daemon withArgv(String... args)
    {
        return withArgv(asList(args));
    }

    public Daemon withArgv(List<String> args)
    {
        return new Daemon(args, pidfile, out, err, extraVmArgs, extraProgramArgs);
    }

    public Daemon withExtraVmArgs(List<String> extraVmArgs)
    {
        return new Daemon(programArgs, pidfile, out, err, extraVmArgs, extraProgramArgs);
    }

    public Daemon withExtraVmArgs(String... extraVmArgs)
    {
        return new Daemon(programArgs, pidfile, out, err, asList(extraVmArgs), extraProgramArgs);
    }

    public Daemon withExtraProgramArgs(List<String> extraProgramArgs)
    {
        return new Daemon(programArgs, pidfile, out, err, extraVmArgs, extraProgramArgs);
    }

    public Daemon withExtraProgramArgs(String... extraProgramArgs)
    {
        return new Daemon(programArgs, pidfile, out, err, extraVmArgs, asList(extraProgramArgs));
    }

    public Daemon withPidFile(File pidfile)
    {
        return new Daemon(programArgs, pidfile, out, err, extraVmArgs, extraProgramArgs);
    }

    public Daemon withStdout(File out)
    {
        return new Daemon(programArgs, pidfile, out, err, extraVmArgs, extraProgramArgs);
    }

    public Daemon withStderr(File err)
    {
        return new Daemon(programArgs, pidfile, out, err, extraVmArgs, extraProgramArgs);
    }

    public Status forkish() throws IOException
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
            envp.add(Daemon.class.getName() + "=daemon");
            List<String> argv = buildARGV(posix);
            int child_pid = posix.posix_spawnp(argv.get(0), close_streams, argv, envp);
            return Status.parent(child_pid);
        }
    }

    public void daemonize() throws IOException
    {
        if (forkish().isParent()) {
            System.exit(0);
        }
    }

    public static boolean isDaemon()
    {
        return "daemon".equals(System.getenv(Daemon.class.getName()));
    }

    public List<String> buildARGV(POSIX posix)
    {
        List<String> argv;
        String os = System.getProperty("os.name");
        if ("Linux".equals(os)) {
            argv = new LinuxArgvFinder(posix.getpid()).getArgv();
        }
        else if ("Mac OS X".equals(os)) {
            argv = new MacARGVFinder().getArgv();
        }
        else {
            argv = new JvmBasedArgvFinder(this.programArgs).getArgv();
        }

        if (this.extraVmArgs.size() > 0) {
            List<String> new_argv = new ArrayList<String>(argv.size() + extraVmArgs.size());
            new_argv.add(argv.get(0));
            new_argv.addAll(extraVmArgs);
            new_argv.addAll(argv.subList(1, argv.size()));
            argv = new_argv;
        }

        if (this.extraProgramArgs.size() > 0) {
            argv.addAll(extraProgramArgs);
        }

        return argv;
    }

    /**
     * Creates vm arguments for jdwp remote debugging, suspending the VM on startup
     *
     * @param port port to listen for remote debugger on
     */
    public static List<String> waitForRemoteDebuggerOnPort(int port)
    {
        return asList("-Xdebug", format("-Xrunjdwp:transport=dt_socket,server=y,suspend=y,address=%d", port));
    }

    /**
     * Creates vm arguments for jdwp remote debugging, without suspending the VM on startup
     *
     * @param port port to listen for remote debugger on
     */
    public static List<String> remoteDebuggerOnPort(int port)
    {
        return asList("-Xdebug", format("-Xrunjdwp:transport=dt_socket,server=y,suspend=n,address=%d", port));
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
