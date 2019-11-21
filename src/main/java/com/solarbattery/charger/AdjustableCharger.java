package com.solarbattery.charger;

import com.pi4j.io.gpio.GpioPinDigitalOutput;
import com.pi4j.wiringpi.SoftPwm;

public class AdjustableCharger extends Charger {

    private Integer pwmPin;
    private Double outputPower;
    private Integer powerLevel;

    AdjustableCharger(Double outputPower, Double outputVoltage, GpioPinDigitalOutput myAcSwitch, GpioPinDigitalOutput myDcSwitch, Integer pinNumber) {
        super(outputPower, outputVoltage, myAcSwitch, myDcSwitch);
        pwmPin = pinNumber;
        SoftPwm.softPwmCreate(pinNumber, 0, 100);
        powerLevel = 0;
        this.outputPower = 0.0;
    }

    public void adjustCurrent(Integer powerLevel) {
        this.powerLevel = powerLevel;
        SoftPwm.softPwmWrite(pwmPin, this.powerLevel);
        double powerPerVolt = getOUTPUT_POWER_MAX() / 5.0;
        double i = (this.powerLevel / 100.0) * 3.3;
        setOutputPower(i*powerPerVolt);
    }

    public Integer getPowerLevel(){
        return powerLevel;
    }

    private void setOutputPower(Double outputPower){
        this.outputPower = outputPower;
    }

    public Double getOutputPower() {
        return outputPower;
    }
}
