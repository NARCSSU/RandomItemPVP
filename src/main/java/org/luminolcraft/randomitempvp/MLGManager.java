package org.luminolcraft.randomitempvp;

public class MLGManager {
    private boolean running = false;

    public boolean isRunning() {
        return running;
    }

    public void start() {
        running = true;
    }

    public void stop() {
        running = false;
    }
}

