package org.skife.gressil;

import jnr.ffi.Pointer;
import jnr.ffi.annotations.In;
import jnr.ffi.annotations.Out;
import jnr.ffi.byref.IntByReference;

public interface MicroC
{
    int getpid();
    int setsid();
    String strerror(int errno);
    int kill(int pid, int signal);

    int posix_spawnp(@Out IntByReference pid, @In CharSequence path,
                     @In Pointer fileActions, @In Pointer attr,
                     @In CharSequence[] argv, @In CharSequence[] envp);

}
