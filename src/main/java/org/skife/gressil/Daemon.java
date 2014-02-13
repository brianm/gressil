package org.skife.gressil;

import jnr.ffi.Library;
import jnr.ffi.Pointer;
import jnr.ffi.byref.IntByReference;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static java.lang.String.format;
import static java.util.Arrays.asList;

public class Daemon
{
    private final List<String> programArgs;
    private final File pidfile;
    private final File out;
    private final File err;
    private final List<String> extraVmArgs;
    private final List<String> extraProgramArgs;

    private static final MicroC posix = Library.loadLibrary("c", MicroC.class);

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
    public Daemon withMainArgs(String... args)
    {
        return withArgv(asList(args));
    }

    public Daemon withArgv(List<String> args)
    {
        return new Daemon(args, pidfile, out, err, extraVmArgs, extraProgramArgs);
    }

    public Daemon withExtraJvmArgs(List<String> extraVmArgs)
    {
        return new Daemon(programArgs, pidfile, out, err, extraVmArgs, extraProgramArgs);
    }

    public Daemon withExtraJvmArgs(String... extraVmArgs)
    {
        return new Daemon(programArgs, pidfile, out, err, asList(extraVmArgs), extraProgramArgs);
    }

    public Daemon withExtraMainArgs(List<String> extraProgramArgs)
    {
        return new Daemon(programArgs, pidfile, out, err, extraVmArgs, extraProgramArgs);
    }

    public Daemon withExtraMainArgs(String... extraProgramArgs)
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

    Status forkish() throws IOException
    {
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
                pidfile.deleteOnExit();
            }

            return Status.child(posix.getpid());
        }
        else
        {
            String[] envp = getEnv(Daemon.class.getName() + "=daemon");
            List<String> argv = buildARGV(posix);

            jnr.ffi.Runtime runtime = jnr.ffi.Runtime.getSystemRuntime();
            Pointer NULL = Pointer.wrap(runtime, 0L);
            IntByReference child_pid = new IntByReference();

            int rs = posix.posix_spawnp(child_pid, argv.get(0), NULL, NULL,
                                        argv.toArray(new String[argv.size()]), envp);
            if (rs != 0) {
                throw new RuntimeException(posix.strerror(rs));
            }
            return Status.parent(child_pid.getValue());
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

    public List<String> buildARGV(MicroC posix)
    {
        List<String> argv;
        String os = System.getProperty("os.name");
        if (this.programArgs != null) {
            // if we had args passed to us, don't mess around, just use them
            argv = new JvmBasedArgvFinder(this.programArgs).getArgv();
        }
        else if ("Linux".equals(os)) {
            argv = new LinuxArgvFinder(posix.getpid()).getArgv();
        }
//        else if ("Mac OS X".equals(os)) {
//            argv = new MacARGVFinder().getArgv();
//            if (!argv.get(0).endsWith("java")) {
//                this works sometimes, not others, needs debugging. For now this heuristic seems to work
//                argv = new JvmBasedArgvFinder(this.programArgs).getArgv();
//            }
//        }
        else
        {
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
    public static List<String> waitForRemoteDebugOnPort(int port)
    {
        return asList("-Xdebug", format("-Xrunjdwp:transport=dt_socket,server=y,suspend=y,address=%d", port));
    }

    /**
     * Creates vm arguments for jdwp remote debugging, without suspending the VM on startup
     *
     * @param port port to listen for remote debugger on
     */
    public static List<String> remoteDebugOnPort(int port)
    {
        return asList("-Xdebug", format("-Xrunjdwp:transport=dt_socket,server=y,suspend=n,address=%d", port));
    }

    public static String[] getEnv(String... additions)
    {
        String[] envp = new String[System.getenv().size() + additions.length];
        int i = 0;
        for (Map.Entry<String, String> pair : System.getenv().entrySet()) {
            envp[i++] = new StringBuilder(pair.getKey()).append("=").append(pair.getValue()).toString();
        }
        System.arraycopy(additions, 0, envp, envp.length - 1, additions.length);
        return envp;
    }

    /**
     * 0	program is running or service is OK
     * 1	program is dead and /var/run pid file exists
     * 2	program is dead and /var/lock lock file exists
     * 3	program is not running
     * 4	program or service status is unknown
     * 5-99	reserved for future LSB use
     * 100-149	reserved for distribution use
     * 150-199	reserved for application use
     * 200-254	reserved
     */
    public DaemonStatus checkStatus()
    {
        if (this.pidfile == null) {
            throw new IllegalStateException("No pidfile specified, cannot check status!");
        }
        if (!pidfile.exists()) {
            return DaemonStatus.STATUS_NOT_RUNNING;
        }

        final int pid;
        try
        {
            byte[] content = Files.readAllBytes(pidfile.toPath());
            String s = new String(content, StandardCharsets.UTF_8).trim();
            pid = Integer.parseInt(s);

        }
        catch (Exception e)
        {
            System.err.println(e.getMessage());
            return DaemonStatus.STATUS_UNKNOWN;
        }

        int rs = posix.kill(pid, 0);
        if (rs == 0) {
            return DaemonStatus.STATUS_RUNNING;
        }
        else
        {
            return DaemonStatus.STATUS_DEAD;
        }
    }

    public DaemonStatus stop()
    {
        /*
         1	generic or unspecified error (current practice)
         2	invalid or excess argument(s)
         3	unimplemented feature (for example, "reload")
         4	user had insufficient privilege
         5	program is not installed
         6	program is not configured
         7	program is not running
         8-99	reserved for future LSB use
         100-149	reserved for distribution use
         150-199	reserved for application use
         200-254	reserved
         */
        if (this.pidfile == null) {
            throw new IllegalStateException("No pidfile specified, cannot stop!");
        }
        if (!pidfile.exists()) {
            return DaemonStatus.STOP_NOT_RUNNING;
        }

        final int pid;
        try
        {
            byte[] content = Files.readAllBytes(pidfile.toPath());
            String s = new String(content, StandardCharsets.UTF_8).trim();
            pid = Integer.parseInt(s);

        }
        catch (Exception e)
        {
            System.err.println(e.getMessage());
            return DaemonStatus.STOP_GENERAL_ERROR;
        }

        int rs = posix.kill(pid, 2);
        if (rs == 0) {
            return DaemonStatus.STOP_SUCCESS;
        }
        else
        {
            return DaemonStatus.STOP_GENERAL_ERROR;
        }
    }

    public void execute(DaemonCommand cmd) throws IOException
    {
        final DaemonStatus status;
        switch (cmd) {
            case start:
                daemonize();
                break;
            case status:
                status = checkStatus();
                System.exit(status.getExitCode());
                break;
            case stop:
                status = stop();
                System.exit(status.getExitCode());
                break;
        }
    }
}
