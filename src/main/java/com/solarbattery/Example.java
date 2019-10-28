package com.solarbattery;

import com.solarbattery.charger.ChargeManager;
import org.springframework.boot.*;
import org.springframework.boot.autoconfigure.*;
import org.springframework.web.bind.annotation.*;

@RestController
@EnableAutoConfiguration
public class Example {

    @RequestMapping("/com/solarbattery/charger/run")
    String run() {
        ChargeManager chargeManager = ChargeManager.getInstance();
        chargeManager.run();
        return "Charger started";
    }
    @RequestMapping("/com/solarbattery/charger/stop")
    String stop() {
        ChargeManager chargeManager = ChargeManager.getInstance();
        chargeManager.run();
        return "Charger started";
    }

    public static void main(String[] args) {
        SpringApplication.run(Example.class, args);
    }

}