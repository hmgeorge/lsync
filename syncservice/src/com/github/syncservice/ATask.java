package com.github.syncservice;

abstract public class ATask implements Runnable {
    static int mNextID = 0;
    public ATask() { mID = mNextID++; }
    public int id() { return mID; }
    public abstract Object result();
    public abstract int status();
    private int mID;
};
