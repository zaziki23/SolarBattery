package com.solarbattery.charger;

import com.solarbattery.battery.Battery;
import com.pi4j.io.gpio.*;
import com.solarbattery.load.GridInverter;
import com.solarbattery.meter.PowerMeter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.solarbattery.solar.SolarManager;
import ws.palladian.helper.ThreadHelper;

import java.util.concurrent.TimeUnit;


public class ChargeManager {
    private Battery battery;
    private AdjustableCharger meanwell;
    private SolarManager solarManager;
    private Double chargeThreshold = 1500.0;
    private Boolean charging = false;
    private Boolean inverting = false;
    private long downTime = TimeUnit.SECONDS.toMillis(10);
    private long sleep = TimeUnit.SECONDS.toMillis(1);
    private double surplus = 0.0;
    private double load = 0.0;
    private double loadOffset = 500.0;
    private double adjustOffset = 250.0;
    private boolean stop = false;
    private int offset = 1;
    private GridInverter inverter = null;
    private PowerMeter inputMeter = null;
    private PowerMeter outputMeter = null;

    final Integer pwmPinCharger = 28;
    final Integer pwmInverter = 25;
    final GpioController gpio = GpioFactory.getInstance();

    private static final Logger LOGGER = LoggerFactory.getLogger(ChargeManager.class);

    // not that we need to use HIGH to pull the switch to switch off actually
    private final GpioPinDigitalOutput acMeanwell = gpio.provisionDigitalOutputPin(RaspiPin.GPIO_27, "AC Meanwell", PinState.HIGH);
    private final GpioPinDigitalOutput dcMeanwell = gpio.provisionDigitalOutputPin(RaspiPin.GPIO_29, "DC Meanwell", PinState.LOW);
    private final GpioPinDigitalOutput loadPreLoader = gpio.provisionDigitalOutputPin(RaspiPin.GPIO_07, "LOAD PreLoader", PinState.LOW);
//    final GpioPinDigitalOutput meanwellSwitch = null;

    private static ChargeManager INSTANCE = new ChargeManager();

    public static ChargeManager getInstance() {
        return INSTANCE;
    }


    public ChargeManager() {
        loadPreLoader.low();
        inverter = new GridInverter(600.0, pwmInverter);
        battery = new Battery(14);
        meanwell = new AdjustableCharger(750.0, 57.0, acMeanwell, dcMeanwell, pwmPinCharger);
        solarManager = new SolarManager();
        inputMeter = new PowerMeter("http://192.168.178.3");
    }

    public void run() {
        Thread ct = new Thread("ChargeManager") {
            @Override
            public void run() {
                try {
                    while (true) {
                        try {
                            while (true) {
                                try {
                                    ThreadHelper.deepSleep(sleep);
                                    if (stop) {
                                        LOGGER.info("stop was forced, i will shut down");
                                        stopCharging();
                                        return;
                                    }
                                    boolean chargeable = battery.isChargeable();
                                    boolean loadable = battery.isLoadable();
                                    if (!loadable && !chargeable) {
                                        LOGGER.info("battery is not ready");
                                        stopCharging();
                                        inverter.switchOff(loadPreLoader);
                                        meanwell.switchAcOff();
                                        inverting = false;
                                        continue;
                                    }
                                    load = inputMeter.getPower();

                                    boolean shouldWeCharge = shouldWeCharge();
                                    if (shouldWeCharge) {
                                        inverter.switchOff(loadPreLoader);
                                        inverting = false;
                                        LOGGER.info("deactivate LOAD NOW");
                                        if (charging) {
                                            adjustChargers(meanwell);
                                        } else {
                                            startCharging();
                                        }
                                    } else if (charging) {
                                        stopCharging();
                                        // maybe it is useful to sleep if it is cloudy?
                                        ThreadHelper.deepSleep(downTime);
                                    }
                                    if (!shouldWeCharge) {
                                        if (loadable && !inverting) {
                                            inverter.switchOn(loadPreLoader);
                                            inverting = true;
                                            offset = 1;
                                        }
                                        if (inverting) {
                                            if(inverter.getPowerLevel() < 5) {
                                                offset = 1;
                                            }
                                            if(inverter.getPowerLevel() == 100) {
                                                offset = -1;
                                            }
                                            LOGGER.info("PWM now: " + inverter.getPowerLevel() + offset);
                                            inverter.adjustCurrent(inverter.getPowerLevel() + offset);
                                        }
                                    }
                                } catch (Throwable t) {
                                    t.printStackTrace();
                                }
                            }
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        };
        ct.start();
    }

    private void startCharging() {
        meanwell.swichAcOn();
        meanwell.adjustCurrent(0);
        charging = true;
        LOGGER.info("start charging");
    }

    private void stopCharging() {
        meanwell.switchAcOff();
        meanwell.adjustCurrent(0);
        charging = false;
        LOGGER.info("stop charging");
    }

    private boolean shouldWeCharge() {
        Double powerAvg = solarManager.getPowerAvg();
        Double currentPower = solarManager.getCurrentPower();
        boolean enoughPower = false;


        if ((load + loadOffset) < currentPower) {
            enoughPower = true;
        }

        LOGGER.info("powerAvg: " + powerAvg + "W, cP: " + currentPower + "W vs load : " + (load) + "W -- charge: " + charging + ", enoughPower: " + enoughPower);
        LOGGER.info("Battery: Voltage: " + battery.getVoltage() + "V, current: " + battery.getCurrent() + "A, balance: " + battery.getBalance() + ", cells: " + battery.getCellVoltages() + ", chargeable: " + battery.isChargeable());
        // if we are charging, it is enough to have good power
        if (battery.isChargeable() && charging && enoughPower) {
            return true;
        }

        // if we are not charging right now, power should be stable
        if (battery.isChargeable() && enoughPower && !charging && (powerAvg > (load + loadOffset))) {
            LOGGER.info("we should start charging");
            return true;
        }
        return false;
    }

    private void adjustChargers(AdjustableCharger charger) {
        Double currentPower = solarManager.getCurrentPower();
        if (currentPower > load + loadOffset && charger.getPowerLevel() >= 99) {
            return;
        }
        double surplus = (currentPower - load - loadOffset) * 0.75;

        Double powerLevel = ((surplus / charger.getOUTPUT_POWER_MAX()) * 100);
        charger.adjustCurrent(Math.min(100, Math.max(0, powerLevel.intValue())));
        if (charger.getPowerLevel() == 100) {
            LOGGER.info("we reached maximum power of charger, now: " + charger.getPowerLevel() + " %");
        } else {
            LOGGER.info("we changed charging power, now: " + charger.getPowerLevel() + " %");
        }

    }

    public void setStop(boolean stop) {
        this.stop = stop;
    }
}
