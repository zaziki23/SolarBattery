package com.solarbattery.load;

import com.pi4j.io.gpio.GpioPinDigitalOutput;
import com.pi4j.wiringpi.SoftPwm;
import ws.palladian.helper.ThreadHelper;

public class GridInverter {
    private final Double OUTPUT_POWER_MAX;
    private Integer pwmPin;
    private Integer powerLevel = 0;

    public GridInverter(Double outputPower, int myPWMPin) {
        this.OUTPUT_POWER_MAX = outputPower;
        this.pwmPin = myPWMPin;
        SoftPwm.softPwmCreate(pwmPin, 0, 100);
    }

    public void switchOn(GpioPinDigitalOutput pinDigitalOutput) {
        pinDigitalOutput.high();
        ThreadHelper.deepSleep(2000);
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
