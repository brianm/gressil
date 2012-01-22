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
    private final List<String> programArgs;
    private final File pidfile;
    private final File out;
    private final File err;
    private final List<String> extraVmArgs;
    private final List<String> extraProgramArgs;

    public Spawn()
    {
        this(null,
             null,
             new File("/dev/null"),
             new File("/dev/null"),
             Collections.<String>emptyList(),
             Collections.<String>emptyList());
    }

    private Spawn(List<String> argv, File pidfile, File out, File err, List<String> extraVmArgs, List<String> extraProgramArgs)
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
     *
     * This is the preferred way to get the program arguments. The argument here should be the same
     * array of strings as was passed to main(String[] args).
     *
     * If the args are *not* passed here, we will attempt to figure out what they are by poking
     * around the JVM, but the "poke around the JVM" method gets confused by whitespace in argument
     * names (ie, quoted arguments) which in the real ARGV will be one element, but via the poke around
     * the JVM method of figuring out program args the whitespace will lead to it being two arguments.
     * This usually leads to undesired behavior.
     */
    public Spawn withArgv(String... args)
    {
        return withArgv(asList(args));
    }

    public Spawn withArgv(List<String> args)
    {
        return new Spawn(args, pidfile, out, err, extraVmArgs, extraProgramArgs);
    }

    public Spawn withExtraVmArgs(List<String> extraVmArgs)
    {
        return new Spawn(programArgs, pidfile, out, err, extraVmArgs, extraProgramArgs);
    }

    public Spawn withExtraVmArgs(String... extraVmArgs)
    {
        return new Spawn(programArgs, pidfile, out, err, asList(extraVmArgs), extraProgramArgs);
    }

    public Spawn withExtraProgramArgs(List<String> extraProgramArgs)
    {
        return new Spawn(programArgs, pidfile, out, err, extraVmArgs, extraProgramArgs);
    }

    public Spawn withExtraProgramArgs(String... extraProgramArgs)
    {
        return new Spawn(programArgs, pidfile, out, err, extraVmArgs, asList(extraProgramArgs));
    }

    public Spawn withPidFile(File pidfile)
    {
        return new Spawn(programArgs, pidfile, out, err, extraVmArgs, extraProgramArgs);
    }

    public Spawn withStdout(File out)
    {
        return new Spawn(programArgs, pidfile, out, err, extraVmArgs, extraProgramArgs);
    }

    public Spawn withStderr(File err)
    {
        return new Spawn(programArgs, pidfile, out, err, extraVmArgs, extraProgramArgs);
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
            int child_pid = posix.posix_spawnp(argv.get(0), close_streams, argv, envp);
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
        // much cribbed from java.dzone.com/articles/programmatically-restart-java

        List<String> ARGV = new ArrayList<String>();
        String java = System.getProperty("java.home") + "/bin/java";
        ARGV.add(java);

        /**
         * This bit retrieves the jvm arguments (ie, -Dwaffles=good -Xmx1G0)
         * unfortunately, it seems to split it on whitespace, not ARGV style, so
         * we need to reconstruct the arguments. Luckily, all jvm arguments begin
         * with a - so we can basically start with a - and grab everything up to
         * the next - and know it is one argument.
         */
        List<String> raw_jvm_args = ManagementFactory.getRuntimeMXBean().getInputArguments();
        List<String> fixed_jvm_args = new ArrayList<String>();
        StringBuilder current = new StringBuilder();
        for (String raw_arg : raw_jvm_args) {
            if (raw_arg.startsWith("-")) {
                if (current.length() > 0) {
                    fixed_jvm_args.add(current.toString());
                    current = new StringBuilder();
                }
                current.append(raw_arg);
            }
            else {
                // escape whitespace in an argument, dir with space in name for example
                current.append("\\ ").append(raw_arg);
            }
        }
        ARGV.addAll(fixed_jvm_args);

        /**
         * Append any vm arguments user has asked us to.
         */
        ARGV.addAll(extraVmArgs);

        /**
         * sun.java.command contains the main class and program arguments. Instead of the
         * main class it may have "-jar jarname"
         */
        String whole_command_line = System.getProperty("sun.java.command");

        System.out.println(whole_command_line);
        // we need a better way to split this, one that respects spaces in an argument
        String[] java_sun_command = whole_command_line.split("\\s+");
        if (java_sun_command[0].endsWith(".jar")) {
            // this is a frighteningly weak test :-(
            // java -jar ./waffles.jar
            ARGV.add("-jar");
            ARGV.add(new File(java_sun_command[0]).getPath());
        }
        else {
            // java -cp waffles.jar hello.Main
            ARGV.add("-cp");

            // escape whitespace in the classpath, ie dirs with spaces in names
            String raw_cp = System.getProperty("java.class.path").replaceAll(" ", "\\ ");
            ARGV.add(raw_cp);
            ARGV.add(java_sun_command[0]);
        }

        if (this.programArgs == null) {
            // we were not given program args, so we have to hope there were no spaces
            // in arguments (escaped spaces and quotes are lost from java.class.path)

            // append all but the first, which was the jar name or main class
            ARGV.addAll(Arrays.asList(java_sun_command).subList(1, java_sun_command.length));
        }
        else {
            // we have the program args given, no need to infer them. Yea!
            ARGV.addAll(this.programArgs);
        }

        ARGV.addAll(extraProgramArgs);
        System.out.println("ARGV= " + ARGV);
        System.out.println("whole_command_line= " + whole_command_line);
        return ARGV;
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
