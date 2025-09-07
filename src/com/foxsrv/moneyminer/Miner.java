package com.foxsrv.moneyminer;

public class Miner {

    private final String owner;
    private final int x, y, z;

    public Miner(String owner, int x, int y, int z) {
        this.owner = owner;
        this.x = x;
        this.y = y;
        this.z = z;
    }

    public String getOwner() {
        return owner;
    }

    public int getX() {
        return x;
    }

    public int getY() {
        return y;
    }

    public int getZ() {
        return z;
    }
}
