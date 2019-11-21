package com.solarbattery.meter;

import ws.palladian.retrieval.DocumentRetriever;
import ws.palladian.retrieval.parser.json.JsonArray;
import ws.palladian.retrieval.parser.json.JsonObject;

import java.util.Optional;

public class PowerMeter {
    private double power = 0.0;
    private double totalPower = 0.0;
    private String url = null;

    public PowerMeter(String url) {
        this.url = url;
    }


    public double getPower() {
        Double power = null;

        try {
            DocumentRetriever documentRetriever = new DocumentRetriever();
            documentRetriever.getHttpRetriever().setConnectionTimeout(500);
            JsonObject jsonObject = documentRetriever.tryGetJsonObject(url + "/status");
            JsonArray meters = jsonObject.tryGetJsonArray("meters");
            JsonObject firstMeter = meters.tryGetJsonObject(0);
            power = firstMeter.tryGetDouble("power");
            Double total = firstMeter.tryGetDouble("total");
            this.power = power;
            this.totalPower = total;
        } catch ( Exception e) {
            e.printStackTrace();
        }
        return Optional.ofNullable(power).orElse(0.0);
    }
}
