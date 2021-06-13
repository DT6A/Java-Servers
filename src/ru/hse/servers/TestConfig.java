package ru.hse.servers;

public class TestConfig {
     public enum ArchitectureType {
         ASYNC,
         BLOCKING,
         NON_BLOCKING
     }

     public enum VaryingParameter {
         LENGTH,
         CLIENTS,
         PAUSE
     }

    public int arraysSize;
    public int numberOfClients;
    public int pauseBetweenQueries;
    public final int numberOfQueriesFromEachClient;
    public final int lowerBound;
    public final int upperBound;
    public final int step;
    public final ArchitectureType architectureType;
    public final VaryingParameter varyingParameter;

    public TestConfig(int arraysSize,
                      int numberOfClients,
                      int pauseBetweenQueries,
                      int numberOfQueriesFromEachClient,
                      int lowerBound, int upperBound, int step, ArchitectureType architectureType, VaryingParameter varyingParameter) {
        this.arraysSize = arraysSize;
        this.numberOfClients = numberOfClients;
        this.pauseBetweenQueries = pauseBetweenQueries;
        this.numberOfQueriesFromEachClient = numberOfQueriesFromEachClient;
        this.lowerBound = lowerBound;
        this.upperBound = upperBound;
        this.step = step;
        this.architectureType = architectureType;
        this.varyingParameter = varyingParameter;
    }

    @Override
    public String toString() {
        return "TestConfig{" +
                "arraysSize=" + arraysSize +
                ", numberOfClients=" + numberOfClients +
                ", pauseBetweenQueries=" + pauseBetweenQueries +
                ", numberOfQueriesFromEachClient=" + numberOfQueriesFromEachClient +
                ", lowerBound=" + lowerBound +
                ", upperBound=" + upperBound +
                ", step=" + step +
                ", architectureType=" + architectureType +
                ", varyingParameter=" + varyingParameter +
                '}';
    }

    public String toCSVFileName() {
        return architectureType + "_" + varyingParameter + ".csv";
    }

    public void initStepping() {
        if (varyingParameter == VaryingParameter.LENGTH) {
            arraysSize = lowerBound;
        }
        else if (varyingParameter == VaryingParameter.CLIENTS) {
            numberOfClients = lowerBound;
        }
        else {
            pauseBetweenQueries = lowerBound;
        }
    }

    public boolean step() {
        if (varyingParameter == VaryingParameter.LENGTH) {
            arraysSize += step;
            return arraysSize <= upperBound;
        }
        else if (varyingParameter == VaryingParameter.CLIENTS) {
            numberOfClients += step;
            return numberOfClients <= upperBound;
        }
        else {
            pauseBetweenQueries += step;
            return pauseBetweenQueries <= upperBound;
        }
    }
}
