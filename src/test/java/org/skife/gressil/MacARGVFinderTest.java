package org.skife.gressil;

import org.junit.Ignore;
import org.junit.Test;

public class MacARGVFinderTest
{
    @Test
    @Ignore
    public void testFoo() throws Exception
    {
        JvmBasedArgvFinder jvm = new JvmBasedArgvFinder(null);
        MacARGVFinder mf = new MacARGVFinder();

        System.out.println(mf.getArgv());
    }
}
