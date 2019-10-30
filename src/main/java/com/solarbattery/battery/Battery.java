package com.solarbattery.battery;

import com.solarbattery.charger.ChargeManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class Battery {
    private Boolean chargeable = true;
    private Boolean loadable = false;
    private Integer numberOfCells;
    private Double voltage;
    private Double current;
    private Integer SoC;
    private Integer balance;
    private Map<Integer, Double> cellVoltages;
    private Socket socket;
    private long lastTime;

    final private Double CELL_SHUTDOWN_MAX_VOLTAGE = 4.20; // FIXME
    final private Double CELL_SHUTDOWN_MIN_VOLTAGE = 2.85; // FIXME
    final private Double CELL_MAX_VOLTAGE = CELL_SHUTDOWN_MAX_VOLTAGE - 0.15; // FIXME
    final private Double CELL_MIN_VOLTAGE = CELL_SHUTDOWN_MIN_VOLTAGE + 0.25; // FIXME
    final private Double MAX_VOLTAGE;
    final private Double MIN_VOLTAGE;
    final private Double SHUTDOWN_MIN_VOLTAGE;
    final private Double SHUTDOWN_MAX_VOLTAGE;

    private static final Logger LOGGER = LoggerFactory.getLogger(ChargeManager.class);

    public Battery(Integer numberOfCells) {
        this.numberOfCells = numberOfCells;
        cellVoltages = new HashMap<>();
        for (int i = 0; i < numberOfCells; i++) {
            cellVoltages.put(i + 1, 3.7);
        }
        MAX_VOLTAGE = numberOfCells * CELL_MAX_VOLTAGE;
        MIN_VOLTAGE = numberOfCells * CELL_MIN_VOLTAGE;
        SHUTDOWN_MAX_VOLTAGE = numberOfCells * CELL_SHUTDOWN_MAX_VOLTAGE;
        SHUTDOWN_MIN_VOLTAGE = numberOfCells * CELL_SHUTDOWN_MIN_VOLTAGE;
        voltage = 55.0; // FIXME
        lastTime = 0;
    }

    public void createSocket() throws IOException {
        socket = new Socket("localhost", 9998);
    }

    public int evaluateStatus() {
        long now = System.currentTimeMillis();
        long tenSecondsAgo = now - TimeUnit.SECONDS.toMillis(10);
        if (lastTime < tenSecondsAgo) {
            byte[] message = hexStringToByteArray("DDA50300FFFD77");
            byte[] first = new byte[600];
            byte[] second = new byte[600];

            sendMessage(socket, message, first, second);
            parseData(first, second);

            if (voltage > MAX_VOLTAGE || voltage < MIN_VOLTAGE) {
                setChargeable(false);
                setLoadable(false);
            }

            for (Integer cellNumber : cellVoltages.keySet()) {
                Double aDouble = cellVoltages.get(cellNumber);
                if (aDouble > CELL_SHUTDOWN_MAX_VOLTAGE || aDouble < CELL_SHUTDOWN_MIN_VOLTAGE) {
                    LOGGER.error(cellVoltages.toString());
                    setLoadable(false);
                    setChargeable(false);
                    return -1; // FIXME
                }
                if (aDouble > CELL_MAX_VOLTAGE) {
                    // maybe balance issue
                    setChargeable(false);
                    setLoadable(true);
                    LOGGER.info("Cell "+ cellNumber + " reached " + aDouble + "V");
                    return 1;
                }
                if (aDouble < CELL_MIN_VOLTAGE) {
                    setChargeable(true);
                    setLoadable(false);
                    LOGGER.info("Cell "+ cellNumber + " reached " + aDouble + "V");
                    return 2;
                }
            }
            lastTime = now;
            return 0;
        }
        return 0;
    }

    public boolean isLoadable() {
        return loadable;
    }

    public void setLoadable(boolean loadable) {
        if(!loadable) {
            LOGGER.info("battery is not ready for load");
        } else {
            LOGGER.info("battery is ready for load");
        }
        this.loadable = loadable;
    }

    public boolean isChargeable() {
        return chargeable;
    }


    public void setChargeable(boolean chargeable) {

        if(!chargeable) {
            LOGGER.info("battery is not ready for charge");
        } else {
            LOGGER.info("battery is ready for charge");
        }
        this.chargeable = chargeable;
    }

    public String getCellVoltages(){
        StringBuilder stringBuilder = new StringBuilder();
        for (Integer integer : cellVoltages.keySet()) {
            stringBuilder.append(integer + ": " + cellVoltages.get(integer) + "  ");
        }
        return stringBuilder.toString();
    }

    public Double getVoltage() {
        return voltage;
    }

    public void setVoltage(Double voltage) {
        if (voltage > 24 && voltage < 60) {
            this.voltage = voltage;
        }
    }

    public static byte[] hexStringToByteArray(String s) {
        int len = s.length();
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(s.charAt(i), 16) << 4)
                    + Character.digit(s.charAt(i + 1), 16));
        }
        return data;
    }

    private static void sendMessage(Socket socket, byte[] message, byte[] first, byte[] second) {
        try {
            OutputStream outputStream = socket.getOutputStream();
            System.out.println("write message to bms");
            outputStream.write(message);

            InputStream inputStream = socket.getInputStream();

            Integer response = 0;
            System.out.println("now reading for a response");
            int i = 0;
            byte[] data = first;
            int done = 0;
            while (response != -1 && done != 2) {
                response = inputStream.read();
                if (response == -1) {
                    System.out.println("Ende");
                    break;
                } else {
                    String hexString = Integer.toHexString(response);
                    if (hexString.equals("77")) {
                        data = second;
                        i = 0;
                        done++;
                        continue;
                    }
                    data[i] = response.byteValue();
                    i++;
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void parseData(byte[] first, byte[] second) {

        int anInt = ((first[4] & 0xff) << 8) | (first[5] & 0xff);
        this.setVoltage(anInt / 100.00);
        anInt = ((first[6] & 0xff) << 8) | (first[7] & 0xff);
        this.setCurrent(anInt / 100.00);
        anInt = ((first[16] & 0xff) << 8) | (first[17] & 0xff);
        this.setBalance(anInt);
        anInt = first[23];
        this.setSoC(anInt);

        for (int i = 0; i < 14; i++) {
            anInt = ((second[4 + (2*i)] & 0xff) << 8) | (second[5 + (2*i)] & 0xff);
            cellVoltages.put(i + 1, (anInt / 1000.0));
        }
    }

    private void setSoC(int anInt) {
        this.SoC = anInt;
    }

    private void setBalance(int anInt) {
        this.balance = anInt;
    }

    private void setCurrent(double current) {
        this.current = current;
    }

    public Double getCurrent() {
        return current;
    }

    public Integer getBalance() {
        return balance;
    }
}