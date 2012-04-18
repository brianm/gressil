package org.skife.gressil;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

class LinuxArgvFinder implements ArgvFinder
{
    private final int pid;

    LinuxArgvFinder(int pid)
    {
        this.pid = pid;
    }

    public List<String> getArgv()
    {
        final String cmdline;
        File procfs_file = new File("/proc/" + pid + "/cmdline");
        try {
            cmdline = readFile(procfs_file);
        }
        catch (IOException e) {
            throw new IllegalStateException("Unable to access " + procfs_file.getAbsolutePath());
        }
        return new ArrayList<String>(Arrays.asList(cmdline.split("\0")));
    }

    private static String readFile(File f) throws IOException
    {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        FileInputStream fin = new FileInputStream(f);
        try {
            int sz;
            byte[] buf = new byte[1024];

            while ((sz = fin.read(buf)) >= 0) {
                baos.write(buf, 0, sz);
            }

            return baos.toString();
        }
        finally {
            fin.close();
        }
    }
}
