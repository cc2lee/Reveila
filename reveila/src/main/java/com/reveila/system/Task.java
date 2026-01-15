package com.reveila.system;

public interface Task {
    public void run();
    public long getInterval();
    public long getInitialDelay();
}
