package com.solarbattery.battery;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

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

    public static void main(String[] args) {
        try {
            Socket socket = new Socket("localhost", 9998);

            byte[] message = hexStringToByteArray("DDA50400FFFC77");
            System.out.println(message);
            OutputStream outputStream = socket.getOutputStream();
            System.out.println("write message to bms");
            outputStream.write(message);

            InputStream inputStream = socket.getInputStream();

            Integer response = 0;
            String hexString = "";
            byte[] data = new byte[200];
            System.out.println("now reading for a response");
            int i = 0;
            while (response != -1 && !hexString.equals("77")) {
                response = inputStream.read();
                if (response == -1) {
                    System.out.println("ende");
                } else {
                    hexString = Integer.toHexString(response);
                    if (!hexString.equals("77")) {
                        data[i] = response.byteValue();
                        i++;
                    }
                }
            }

            ByteBuffer byteBuffer = ByteBuffer.allocateDirect(4);
            byteBuffer.order(ByteOrder.BIG_ENDIAN);
            byteBuffer.put(data[2]);
            byteBuffer.put(data[3]);
            byteBuffer.flip();
            int anInt = byteBuffer.getInt();
            System.out.println("Voltage: :" + (anInt / 100.00));
            System.exit(0);
        } catch (IOException e) {
            e.printStackTrace();
        }

    }
}