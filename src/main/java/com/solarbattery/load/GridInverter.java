package com.solarbattery.load;

import com.pi4j.io.gpio.GpioPinDigitalOutput;
import com.pi4j.wiringpi.SoftPwm;
import com.solarbattery.charger.ChargeManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ws.palladian.helper.ThreadHelper;

public class GridInverter {
    private static final Logger LOGGER = LoggerFactory.getLogger(GridInverter.class);

    private final Double OUTPUT_POWER_MAX;
    private Integer pwmPin;

    public Integer getPowerLevel() {
        return powerLevel;
    }

    public void setPowerLevel(Integer powerLevel) {
        this.powerLevel = powerLevel;
    }

    private Integer powerLevel = 0;
    private boolean on = false;

    public GridInverter(Double outputPower, int myPWMPin) {
        this.OUTPUT_POWER_MAX = outputPower;
        this.pwmPin = myPWMPin;
        SoftPwm.softPwmCreate(pwmPin, 0, 100);
        on = false;
    }

    public void switchOn(GpioPinDigitalOutput pinDigitalOutput) {
        if(on) {
            return;
        }
        on = true;
        pinDigitalOutput.high();
        ThreadHelper.deepSleep(500);
        LOGGER.info("preLoading active");
        ThreadHelper.deepSleep(500);
        LOGGER.info("preLoading active");
        ThreadHelper.deepSleep(500);
        LOGGER.info("preLoading active");
        ThreadHelper.deepSleep(500);
        LOGGER.info("preLoading active");
        ThreadHelper.deepSleep(500);
        LOGGER.info("preLoading active");
        ThreadHelper.deepSleep(500);
        LOGGER.info("preLoading active");
        ThreadHelper.deepSleep(500);
        LOGGER.info("preLoading active");
        adjustCurrent(10);
        pinDigitalOutput.low();
    }

    public void switchOff(GpioPinDigitalOutput pinDigitalOutput) {
        pinDigitalOutput.low();
        adjustCurrent(0);
    }

    public void adjustCurrent(Integer powerLevel) {
        this.powerLevel = powerLevel;
        SoftPwm.softPwmWrite(pwmPin, this.powerLevel);
    }
}
