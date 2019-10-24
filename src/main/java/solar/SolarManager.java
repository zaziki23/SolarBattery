package solar;

import ws.palladian.helper.ThreadHelper;

import java.util.concurrent.TimeUnit;

public class SolarManager {
    private long stopTime = TimeUnit.SECONDS.toMillis(1);
    private Double power;
    private Double powerAvg;

    public SolarManager() {
        init();
    }

    public void init(){
        readSolarPowerFromDB();
    }

    private void readSolarPowerFromDB() {
        Thread ct = new Thread("SolarData") {
            @Override
            public void run() {

                SolarDatabaseManager dbm = SolarDatabaseManager.getSDBM();

                try {
                    power = dbm.getWattage();
                    powerAvg = dbm.getWattageAvg();
                    ThreadHelper.deepSleep(stopTime);
                } catch (Throwable t) {
                    t.printStackTrace();
                }
            }
        };
        ct.start();
    }

    public Double getCurrentPower() {
        return power;
    }

    public Double getPowerAvg(){
        return powerAvg;
    }
}
