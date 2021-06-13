package ru.hse.servers;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class TimeCollector {
    public volatile boolean isAccepting = true;
    private final List<Long> clientTimes = new CopyOnWriteArrayList<>();
    private final List<Long> serverTimes = new CopyOnWriteArrayList<>();

    public void putFromClient(long time) {
        if (isAccepting) {
            clientTimes.add(time);
        }
    }

    public void putFromServer(long time) {
        if (isAccepting) {
            serverTimes.add(time);
        }
    }

    public double getClientMeanTime() {
        return ((double) clientTimes.stream().reduce(0L, Long::sum)) / clientTimes.size();
    }

    public double getServerMeanTime() {
        return ((double) serverTimes.stream().reduce(0L, Long::sum)) / serverTimes.size();
    }
}
