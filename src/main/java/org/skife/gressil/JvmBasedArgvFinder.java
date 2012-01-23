package org.skife.gressil;

import java.io.File;
import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

class JvmBasedArgvFinder implements ArgvFinder
{
    private final List<String> programArgs;

    public JvmBasedArgvFinder(List<String> programArgs)
    {
        this.programArgs = programArgs;
    }

    public List<String> getArgv()
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
         * sun.java.command contains the main class and program arguments. Instead of the
         * main class it may have "-jar jarname"
         */
        String whole_command_line = System.getProperty("sun.java.command");

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

        return ARGV;
    }
}
