package org.skife.gressil;

public enum DaemonStatus
{
    // for status
    STATUS_RUNNING(0), STATUS_DEAD(1), STATUS_NOT_RUNNING(3), STATUS_UNKNOWN(4),

    // for stop
    STOP_NOT_RUNNING(7), STOP_GENERAL_ERROR(1), STOP_SUCCESS(0);

    private final int exitCode;

    public int getExitCode() {
        return exitCode;
    }

    DaemonStatus(final int code) {exitCode = code;}

    /*
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
}
