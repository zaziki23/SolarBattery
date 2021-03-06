package com.solarbattery.solar;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ws.palladian.helper.ThreadHelper;

import java.util.concurrent.TimeUnit;

public class SolarManager {
    private long stopTime = TimeUnit.SECONDS.toMillis(1);
    private Double power = 0.0;
    private Double powerAvg = 0.0;
    private static final Logger LOGGER = LoggerFactory.getLogger(SolarManager.class);
    private SolarDatabaseManager dbm;

    public SolarManager() {
        dbm = SolarDatabaseManager.getSDBM();
        init();
    }

    public void init(){
        readSolarPowerFromDB();
    }

    private void readSolarPowerFromDB() {
        Thread ct = new Thread("SolarData") {
            @Override
            public void run() {

                while (true) {
                    try {
                        power = dbm.getWattage();
                        powerAvg = dbm.getWattageAvg();
                        ThreadHelper.deepSleep(stopTime);
                    } catch (Throwable t) {
                        t.printStackTrace();
                    }
                }
            }
        };
        ct.start();
        // that should block startup until dbm is ready
        dbm.getWattage();
        dbm.getWattageAvg();
    }

    public Double getCurrentPower() {
        return power;
    }

    public Double getPowerAvg(){
        return powerAvg;
    }
}
