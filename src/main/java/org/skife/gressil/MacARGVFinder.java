package org.skife.gressil;

import jnr.ffi.*;
import jnr.ffi.Runtime;
import jnr.ffi.byref.IntByReference;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;

public class MacARGVFinder implements ArgvFinder
{
    public List<String> getArgv()
    {
        final int CTL_KERN = 1;
        final int KERN_PROCARGS2 = 49;
        int rs;

        jnr.ffi.Runtime runtime = Runtime.getSystemRuntime();
        Pointer NULL = Pointer.wrap(runtime, 0L);

        SmallC sc = Library.loadLibrary("c", SmallC.class);

        final IntByReference oldlenp = new IntByReference(4);
        rs = sc.sysctl(new int[]{CTL_KERN, KERN_PROCARGS2, sc.getpid()}, 3,
                       NULL, oldlenp,
                       NULL, 0);
        if (rs != 0) {
            throw new IllegalStateException(sc.strerror(rs));
        }

        final Pointer oldp = Memory.allocateDirect(runtime, oldlenp.getValue());
        rs = sc.sysctl(new int[]{CTL_KERN, KERN_PROCARGS2, sc.getpid()}, 3,
                       oldp, oldlenp,
                       NULL, 0);
        if (rs != 0) {
            throw new IllegalStateException(sc.strerror(rs));
        }

        class ProcessStuffReader
        {
            private int offset = 0;

            private final int          argc;
            private final int          size;
            private final List<String> argv;

            ProcessStuffReader()
            {
                this.size = oldlenp.getValue();
                argc = readInt();
                readString(); // exec path?
                skipNulls();
                List<String> argv = new ArrayList<String>(argc);
                for (int i = 0; i < argc; i++) {
                    argv.add(readString());
                }
                this.argv = argv;
            }

            private int readInt()
            {
                int i = oldp.getInt(offset);
                offset += 4;
                return i;
            }

            private void skipNulls()
            {
                while (offset < size && oldp.getByte(offset) == '\0') {
                    offset++;
                }
            }

            private String readString()
            {
                ByteArrayOutputStream bout = new ByteArrayOutputStream();
                byte c;
                while ((c = oldp.getByte(offset++)) != '\0') {
                    bout.write(c);
                }
                return new String(bout.toByteArray());
            }

            public List<String> getArgv()
            {
                return argv;
            }
        }

        return new ProcessStuffReader().getArgv();
    }

    public static interface SmallC
    {
        // int sysctl(int *name, u_int namelen, void *oldp, size_t *oldlenp, void *newp, size_t newlen);
        int sysctl(int[] name, int namelen,
                   Pointer oldp, IntByReference oldlenp,
                   Pointer newp, int newlen);

        int getpid();

        String strerror(int errno);
    }

}
