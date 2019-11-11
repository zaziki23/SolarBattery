package com.solarbattery.charger;

import com.pi4j.io.gpio.*;
import ws.palladian.helper.ThreadHelper;

public class Charger {
    private final Double OUTPUT_POWER_MAX;
    private final Double OUTPUT_VOLTAGE;
    private GpioPinDigitalOutput myAcSwitch;
    private GpioPinDigitalOutput myDcSwitch;

    Charger(Double outputPower, Double outputVoltage, GpioPinDigitalOutput myAcSwitch, GpioPinDigitalOutput myDcSwitch) {
        this.OUTPUT_POWER_MAX = outputPower;
        this.OUTPUT_VOLTAGE = outputVoltage;
        this.myAcSwitch = myAcSwitch;
        this.myDcSwitch = myDcSwitch;
    }

    public void swichAcOn() {
        if(myAcSwitch.isLow() && myDcSwitch.isHigh()){
            return;
        }

        // first active ac
        myAcSwitch.low();

        // maybe we should wait for the switch to come on
        ThreadHelper.deepSleep(250);
        myDcSwitch.high();
    }

    public void switchAcOff() {
        if(myAcSwitch.isHigh() && myDcSwitch.isLow()){
            return;
        }

        // switch ac off
        myAcSwitch.high();

        // maybe we should wait for the switch to come off
        ThreadHelper.deepSleep(250);
        myDcSwitch.isLow();
    }

    public Double getOUTPUT_POWER_MAX() {
        return OUTPUT_POWER_MAX;
    }
}
