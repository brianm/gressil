package org.skife.gressil;

public final class Status
{
    private final boolean child;

    private final int childPid;

    private Status(boolean child, int pid)
    {
        this.child = child;
        childPid = pid;
    }

    public int getChildPid()
    {
        return childPid;
    }

    public boolean isChild()
    {
        return child;
    }

    public boolean isParent()
    {
        return !child;
    }


    @Override
    public boolean equals(Object o)
    {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Status status = (Status) o;

        if (child != status.child) return false;
        if (childPid != status.childPid) return false;

        return true;
    }

    @Override
    public int hashCode()
    {
        int result = (child ? 1 : 0);
        result = 31 * result + childPid;
        return result;
    }

    static Status child(int pid)
    {
        return new Status(true, pid);
    }

    static Status parent(int pid)
    {
        return new Status(false, pid);
    }
}
