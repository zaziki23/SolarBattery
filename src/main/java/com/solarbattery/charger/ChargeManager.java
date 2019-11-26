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
    private double loadOffset = 200.0;
    private double adjustOffset = 250.0;
    private boolean stop = false;
    private int offset = 1;
    private GridInverter inverter = null;
    private PowerMeter inputMeter = null;
    private PowerMeter outputMeter = null;

    final Integer pwmPinCharger = 28;
    final Integer pwmInverter = 25;
    final GpioController gpio = GpioFactory.getInstance();

    private static final Logger LOGGER = LoggerFactory.getLogger(ChargeManager.class.getName());

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
                            boolean lastShouldWeCharge = false;
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

                                    boolean shouldWeCharge = shouldWeCharge(lastShouldWeCharge);
                                    if (shouldWeCharge) {
                                        if (inverting) {
                                            LOGGER.info("LOAD off");
                                            inverter.switchOff(loadPreLoader);
                                            inverting = false;
                                        }
                                        if (charging) {
                                            adjustChargers(meanwell, battery);
                                        } else {
                                            startCharging();
                                        }
                                    } else if (charging) {
                                        stopCharging();
                                        // maybe it is useful to sleep if it is cloudy?
                                        ThreadHelper.deepSleep(downTime);
                                    }
                                    if (!shouldWeCharge) {
                                        boolean shouldWeInvert = shouldWeInvert();
                                        if (shouldWeInvert) {
                                            if (loadable && !inverting) {
                                                inverter.switchOn(loadPreLoader);
                                                LOGGER.info("start to invert power");
                                                inverting = true;
                                            }
                                            if (inverting) {
                                                if (inverter.getPowerLevel() < 4) {
                                                    LOGGER.info("more invert power now");
                                                    offset = 1;
                                                }
                                                if (inverter.getPowerLevel() > 95) {
                                                    LOGGER.info("less power now");
                                                    offset = -1;
                                                }
                                                ThreadHelper.deepSleep(500);
                                                LOGGER.info("PWM now: " + inverter.getPowerLevel() + ", offset: " + offset);
                                                inverter.adjustCurrent(inverter.getPowerLevel() + offset);
                                            }
                                        } else if (inverting) {
                                            LOGGER.info("LOAD off");
                                            inverter.switchOff(loadPreLoader);
                                            inverting = false;
                                        }
                                    }
                                    lastShouldWeCharge = shouldWeCharge;
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

    public boolean shouldWeInvert() {
        Double powerAvg = solarManager.getPowerAvg();

        if (powerAvg <= 0.1) {
            if (battery.isLoadable()) {
                return true;
            }
        }
        return false;
    }

    private boolean shouldWeCharge(boolean lastShouldWeCharge) {
        Double powerAvg = solarManager.getPowerAvg();
        Double currentPower = solarManager.getCurrentPower();
        boolean enoughPower = false;


        if ((load + loadOffset) < currentPower) {
            enoughPower = true;
        }

        LOGGER.info("pAVG:" + powerAvg + "W, cP:" + currentPower + "W, load:" + (load) + "W, c: " + charging + ", V:" + battery.getVoltage() + ", A:" + battery.getCurrent(), ", c:" + battery.isChargeable() + ", l:" + battery.isLoadable());
        LOGGER.info("Eff: " + (battery.getVoltage() * battery.getCurrent() / load) + ", cells: " + battery.getCellVoltages());
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

    private void adjustChargers(AdjustableCharger charger, Battery battery) {
        Double currentPower = solarManager.getCurrentPower();
        if (currentPower > load + loadOffset && charger.getPowerLevel() >= 99) {
            return;
        }
        double surplus = (currentPower - load - loadOffset) * 0.75;

        Double powerLevel = ((surplus / charger.getOUTPUT_POWER_MAX()) * 100);
        int calculatedPower = Math.min(100, Math.max(0, powerLevel.intValue()));


        Integer currentPowerLevel = charger.getPowerLevel();

        if (calculatedPower - currentPowerLevel > 30) {
            calculatedPower = currentPowerLevel + 10;
        }
        // battery can decrease power if cells are drifting
        calculatedPower = battery.analyzePowerForCharging(currentPowerLevel, calculatedPower);

        charger.adjustCurrent(calculatedPower);
        if (charger.getPowerLevel() == 100 && !currentPowerLevel.equals(charger.getPowerLevel())) {
            LOGGER.info("we reached maximum power of charger, now: " + charger.getPowerLevel() + " %");
        } else if (!currentPowerLevel.equals(charger.getPowerLevel())) {
            LOGGER.info("we changed charging power, now: " + charger.getPowerLevel() + " %");
        }

    }

    public void setStop(boolean stop) {
        this.stop = stop;
    }
}
