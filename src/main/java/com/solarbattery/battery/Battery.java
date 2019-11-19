package com.solarbattery.battery;

import com.solarbattery.charger.ChargeManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ws.palladian.helper.ThreadHelper;
import ws.palladian.helper.math.SlimStats;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.net.SocketException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class Battery {
    private Boolean chargeable = false;
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
    final private Double CELL_MAX_VOLTAGE = CELL_SHUTDOWN_MAX_VOLTAGE - 0.10; // FIXME
    final private Double CELL_MIN_VOLTAGE = CELL_SHUTDOWN_MIN_VOLTAGE + 0.25; // FIXME
    final private Double MAX_VOLTAGE;
    final private Double MIN_VOLTAGE;
    final private Double SHUTDOWN_MIN_VOLTAGE;
    final private Double SHUTDOWN_MAX_VOLTAGE;

    private static final Logger LOGGER = LoggerFactory.getLogger(ChargeManager.class);

    public void setCellVoltages(Map<Integer, Double> cellVoltages) {
        this.cellVoltages = cellVoltages;
    }

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
        evaluateStatus();
    }

    public void createSocket() throws IOException {
        socket = new Socket("localhost", 9998);
        socket.setSoTimeout(5000);
    }


    public void evaluateStatus() {
        Thread ct = new Thread("BatteryMonitor") {
            @Override
            public void run() {

                try {
                    createSocket();
                } catch (IOException e) {
                    System.exit(0);
                }
                while (true) {
                    try {
                        long now = System.currentTimeMillis();
                        long secondsAgo = now - TimeUnit.SECONDS.toMillis(3);
                        if (lastTime < secondsAgo) {
                            Byte[] first = new Byte[1024];
                            Byte[] second = new Byte[1024];

                            byte[] message = hexStringToByteArray("DDA50400FFFC77");
                            boolean b1 = sendMessage(socket, message, first);
                            message = hexStringToByteArray("DDA50300FFFD77");
                            boolean b = sendMessage(socket, message, second);

                            LOGGER.info("parsing: " + b1 + ", " + b + ", V:" + voltage);
                            if (b && b1) {

                                if (voltage > MAX_VOLTAGE) {
                                    setChargeable(false);
                                    setLoadable(true);
                                    LOGGER.info("Voltage is to high: " + voltage);
                                    continue;
                                }
                                if (voltage < MIN_VOLTAGE) {
                                    setChargeable(true);
                                    setLoadable(false);
                                    LOGGER.info("Voltage is to low: " + voltage);
                                    continue;
                                }
                                LOGGER.info("CellVoltages: " + cellVoltages.toString());
                                for (Integer cellNumber : cellVoltages.keySet()) {
                                    Double aDouble = cellVoltages.get(cellNumber);
                                    if (aDouble > CELL_SHUTDOWN_MAX_VOLTAGE) {
                                        LOGGER.error(cellVoltages.toString());
                                        setLoadable(false);
                                        setChargeable(false);
                                        return;
                                    }
                                    if (aDouble < CELL_SHUTDOWN_MIN_VOLTAGE) {
                                        LOGGER.error(cellVoltages.toString());
                                        setLoadable(false);
                                        setChargeable(true);
                                        return;
                                    }
                                    if (aDouble > CELL_MAX_VOLTAGE) {
                                        // maybe balance issue
                                        LOGGER.error(cellVoltages.toString());
                                        setChargeable(false);
                                        setLoadable(true);
                                        LOGGER.info("Cell " + cellNumber + " reached " + aDouble + "V");
                                        return;
                                    }
                                    if (aDouble < CELL_MIN_VOLTAGE) {
                                        LOGGER.error(cellVoltages.toString());
                                        setChargeable(true);
                                        setLoadable(false);
                                        LOGGER.info("Cell " + cellNumber + " reached " + aDouble + "V");
                                        return;
                                    }
                                }
                                setChargeable(true);
                                setLoadable(true);
                                lastTime = System.currentTimeMillis();
                            } else {
                                LOGGER.error("BMS message timed out, was invalid or just makes no sense");
                            }
                        }
                    } catch (Throwable t) {
                        t.printStackTrace();
                    }
                }
            }
        };
        ct.start();
    }

    public boolean isLoadable() {
        return loadable;
    }

    public void setLoadable(boolean loadable) {
        if (!loadable) {
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

        if (this.chargeable && !chargeable) {
            LOGGER.info("battery is not ready for charge");
        }
        if (!this.chargeable && chargeable) {
            LOGGER.info("battery is ready for charge");
        }
        this.chargeable = chargeable;
    }

    public String getCellVoltages() {
        StringBuilder stringBuilder = new StringBuilder();
        for (Integer integer : cellVoltages.keySet()) {
            stringBuilder.append(integer + ": " + cellVoltages.get(integer) + "  ");
        }
        return stringBuilder.toString();
    }

    public Integer analyzePowerForCharging(Integer powerlevel) {
        SlimStats slimStats = new SlimStats();
        for (Integer integer : cellVoltages.keySet()) {
            Double aDouble = cellVoltages.get(integer);
            slimStats.add(aDouble);
        }
        double mean = slimStats.getMean();
        double max = slimStats.getMax();
        if(max - mean > 0.075) {
            powerlevel = powerlevel - 5;
        }
        return powerlevel;
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

    private boolean sendMessage(Socket socket, byte[] message, Byte[] first) {
        boolean returnCode = false;
        try {
            try {
                OutputStream outputStream = socket.getOutputStream();
                outputStream.write(message);
            } catch (SocketException se) {
                createSocket();
                ThreadHelper.deepSleep(5000);
                OutputStream outputStream = socket.getOutputStream();
                outputStream.write(message);
            }

            InputStream inputStream = socket.getInputStream();

            Integer response = 0;
            String data = "";
            int i = 0;
            try {
                while (response != -1) {
                    response = inputStream.read();
                    if (response == -1) {
                        LOGGER.error("no valid response");
                        break;
                    } else {
                        String hexString = Integer.toHexString(response);
                        data = data + hexString;
                        if (hexString.equals("77")) {
                            break;
                        }
                        first[i] = response.byteValue();
                        i++;
                    }
                }
                if (data.startsWith("dd30")) {
                    returnCode = parseData(first, null);
                } else if (data.startsWith("dd40")) {
                    returnCode = parseData(null, first);
                }

                LOGGER.info("got " + data + " from bms, parsing: " + returnCode);

                // read as much as you want - blocks until timeout elapses
            } catch (java.net.SocketTimeoutException e) {
                // read timed out - you may throw an exception of your choice
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return returnCode;
    }

    private boolean parseData(Byte[] first, Byte[] second) {

        if (first != null) {
            double voltage = (((first[4] & 0xff) << 8) | (first[5] & 0xff)) / 100.00;
            double current = (((first[6] & 0xff) << 8) | (first[7] & 0xff)) / 100.00;

            this.setVoltage(voltage);
            this.setCurrent(current);

            this.setBalance(((first[16] & 0xff) << 8) | (first[17] & 0xff));
            this.setSoC(first[23]);
        }

        if (second != null) {
            for (int i = 0; i < 14; i++) {
                int anInt = ((second[4 + (2 * i)] & 0xff) << 8) | (second[5 + (2 * i)] & 0xff);
                if (anInt == 0.0) {
                    break;
                }
                cellVoltages.put(i + 1, (anInt / 1000.0));
            }
        }

        return true;
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