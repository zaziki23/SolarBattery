package com.solarbattery.battery;

import ws.palladian.helper.ThreadHelper;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.util.Arrays;

public class Battery {
    private Boolean chargeable = true;
    private Boolean loadable = false;
    private Integer numberOfCells = null;
    private Double voltage;

    final private Double CELL_SHUTDOWN_MAX_VOLTAGE = 4.20; // FIXME
    final private Double CELL_SHUTDOWN_MIN_VOLTAGE = 2.85; // FIXME
    final private Double CELL_MAX_VOLTAGE = CELL_SHUTDOWN_MAX_VOLTAGE - 0.15; // FIXME
    final private Double CELL_MIN_VOLTAGE = CELL_SHUTDOWN_MIN_VOLTAGE + 0.25; // FIXME
    final private Double MAX_VOLTAGE;
    final private Double MIN_VOLTAGE;
    final private Double SHUTDOWN_MIN_VOLTAGE;
    final private Double SHUTDOWN_MAX_VOLTAGE;


    public Battery(Integer numberOfCells) {
        this.numberOfCells = numberOfCells;
        MAX_VOLTAGE = numberOfCells * CELL_MAX_VOLTAGE;
        MIN_VOLTAGE = numberOfCells * CELL_MIN_VOLTAGE;
        SHUTDOWN_MAX_VOLTAGE = numberOfCells * CELL_SHUTDOWN_MAX_VOLTAGE;
        SHUTDOWN_MIN_VOLTAGE = numberOfCells * CELL_SHUTDOWN_MIN_VOLTAGE;
        voltage = 55.0; // FIXME
    }

    public void evaluateStatus() {

    }

    public boolean isLoadable() {
        return loadable;
    }

    public void setLoadable(boolean loadable) {
        this.loadable = loadable;
    }

    public boolean isChargeable() {
        return chargeable;
    }


    public void setChargeable(boolean chargeable) {
        this.chargeable = chargeable;
    }

    public Double getVoltage() {
        return voltage;
    }

    public void setVoltage(Double voltage) {
        this.voltage = voltage;
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

    public static int getInt(byte[] arr, int off) {
        return arr[off]<<8 &0xFF00 | arr[off+1]&0xFF;
    }

    public static void sendMessage(Socket socket, byte[] message, byte[] data){
        try {
            OutputStream outputStream = socket.getOutputStream();
            System.out.println("write message to bms");
            outputStream.write(message);

            InputStream inputStream = socket.getInputStream();

            Integer response = 0;
            System.out.println("now reading for a response");
            int i = 0;
            while (response != -1) {
                response = inputStream.read();
                if (response == -1) {
                    System.out.println("Ende");
                    break;
                } else {
                    data[i] = response.byteValue();
                    i++;
                }
            }
            ThreadHelper.deepSleep(100);
            response = 0;
            // fixme now try to read again
            while (response != -1) {
                response = inputStream.read();
                if (response == -1) {
                    System.out.println("Ende");
                    break;
                } else {
                    data[i] = response.byteValue();
                    i++;
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void main(String[] args) {
        try {
            Socket socket = new Socket("localhost", 9998);

            byte[] message = hexStringToByteArray("DDA50300FFFD77");
            byte[] data = new byte[600];

            sendMessage(socket, message, data);
            parseGeneric(data);

            System.exit(0);
        } catch (IOException e) {
            e.printStackTrace();
        }

    }

    private static void parseGeneric(byte[] data) {

        int anInt = ((data[4] & 0xff) << 8) | (data[5] & 0xff);
        System.out.println("Voltage: :" + (anInt / 100.00));
        anInt = ((data[6] & 0xff) << 8) | (data[7] & 0xff);
        System.out.println("Current: :" + (anInt / 100.00));
        anInt = ((data[16] & 0xff) << 8) | (data[17] & 0xff);
        System.out.println("Balance: :" + anInt);
        anInt = data[23];
        System.out.println("SoC: :" + anInt);

        System.out.println(Arrays.toString(data));
        for (int i = 0; i < 14; i++) {
            anInt = ((data[4+i] & 0xff) << 8) | (data[5+i] & 0xff);
            System.out.println("Cell(" + (i+1) + ")-Voltage: :" + (anInt / 100.00));
        }
    }
}