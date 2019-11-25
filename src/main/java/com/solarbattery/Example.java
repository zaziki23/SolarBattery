package com.solarbattery;

import com.solarbattery.charger.ChargeManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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

        Logger logger = LoggerFactory.getLogger(ChargeManager.class.toString());
        logger.info("blalba");
// SpringApplication.run(Example.class, args);
    }

}