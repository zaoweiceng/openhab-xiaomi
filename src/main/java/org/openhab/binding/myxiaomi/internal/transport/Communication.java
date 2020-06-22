/**
 * Copyright (c) 2010-2020 Contributors to the openHAB project
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.openhab.binding.myxiaomi.internal.transport;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.eclipse.smarthome.core.thing.ThingStatus;
import org.eclipse.smarthome.core.thing.ThingStatusDetail;
import org.openhab.binding.myxiaomi.annotation.MessageListener;
import org.openhab.binding.myxiaomi.entity.MiIoCommand;
import org.openhab.binding.myxiaomi.entity.json.MiIoSendCommand;
import org.openhab.binding.myxiaomi.internal.Message;
import org.openhab.binding.myxiaomi.internal.MiIoCrypto;
import org.openhab.binding.myxiaomi.internal.MyXiaoMiBindingConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import java.io.IOException;
import java.net.*;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Calendar;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
/**
 * The {@link Communication} is responsible for creating Xiaomi messages.
 *
 * @author zaoweiceng
 */
public class Communication {
    private static final int MSG_BUFFER_SIZE = 2048;
    private final Logger logger = LoggerFactory.getLogger(Communication.class);

    private final String ip;
    private final byte[] token;
    private byte[] deviceID;
    private DatagramSocket socket;

    private List<MessageListener> listeners = new CopyOnWriteArrayList<>();

    private AtomicInteger id = new AtomicInteger(-1);
    private int timeDelta;
    private int timeStamp;
    private final JsonParser parser;
    private MessageSenderThread senderThread;
    private boolean connected;
    private ThingStatusDetail status;
    private int errorCounter;
    private int timeout;
    private boolean needPing = true;
    private static final int MAX_ERRORS = 3;
    private static final int MAX_ID = 15000;
    private ConcurrentLinkedQueue<MiIoSendCommand> commandConcurrentLinkedQueue = new ConcurrentLinkedQueue<>();
    public Communication(String ip, byte[] token, byte[] did,  int id, int timeout) {
        this.ip = ip;
        this.token = token;
        this.deviceID = did;
        this.timeout = timeout;
        setId(id);
        parser = new JsonParser();
        senderThread = new MessageSenderThread();
        senderThread.start();
    }

    public void setId(int id) {
        this.id.set(id);
    }
    public int getId(){
        return id.incrementAndGet();
    }
    public int getTimeDelta(){
        return timeDelta;
    }
    public byte[] getDeviceID(){
        return deviceID;
    }
    public void setDeviceID(byte[] deviceID){
        this.deviceID = deviceID;
    }
    public int getQueueLenth(){
        return commandConcurrentLinkedQueue.size();
    }
    private List<MessageListener> getListeners(){
        return listeners;
    }

    public synchronized void registerLisener(MessageListener listener){
        needPing = true;
        startReciver();
        if(!getListeners().contains(listener)){
            getListeners().add(listener);
        }
    }

    public synchronized void unregisterListener(MessageListener listener){
        getListeners().remove(listener);
        if(getListeners().isEmpty()){
            commandConcurrentLinkedQueue.clear();
            close();
        }
    }

    private DatagramSocket getSocket() throws SocketException {
        if (socket == null || socket.isClosed()){
            socket = new DatagramSocket();
            socket.setSoTimeout(timeout);
        }
        return socket;
    }

    public void close(){
        if (socket != null){
            socket.close();
        }
        senderThread.interrupt();
    }

    public synchronized void startReciver(){
        if (senderThread == null){
            senderThread = new MessageSenderThread();
        }
        if (!senderThread.isAlive()){
            senderThread.start();
        }
    }

    public int queueCommand(MiIoCommand command) throws  IOException {
        return queueCommand(command, "[]");
    }

    public int queueCommand(MiIoCommand command, String params) throws IOException {
        return queueCommand(command.getCommand(), params);
    }

    public int queueCommand(String command, String params) throws IOException {
        JsonObject fullCommand = new JsonObject();
        int cmdId = id.incrementAndGet();
        if(cmdId > MAX_ID){
            id.set(0);
        }
        fullCommand.addProperty("id", cmdId);
        fullCommand.addProperty("method", command);
        fullCommand.add("params", parser.parse(params));
        MiIoSendCommand sendCmd = new MiIoSendCommand(cmdId, MiIoCommand.getCommand(command), fullCommand.toString());
        commandConcurrentLinkedQueue.add(sendCmd);
        if (needPing){
            sendPing(ip);
        }
        return cmdId;
    }

    MiIoSendCommand sendMiIosendCommand(MiIoSendCommand miIoSendCommand) {
        String error = "unknown error";
        String response = "";
        try {
            response = sendCommand(miIoSendCommand.getCommandString(), token, ip, deviceID);
            response = response.replace(",,", ",");
            JsonElement res;
            res = parser.parse(response);
            if(res.isJsonObject()){
                needPing = false;
                return miIoSendCommand;
            }else{
                error = "message is not JSON";
            }
        }catch (Exception e){
            error =    e.getMessage();
        }
        JsonObject errorOBJ = new JsonObject();
        errorOBJ.addProperty("error", error);
        miIoSendCommand.setResponse(errorOBJ);
        return miIoSendCommand;
    }

    private String sendCommand(String command, byte[] token, String ip, byte[] deviceId) throws BadPaddingException, InvalidAlgorithmParameterException, NoSuchAlgorithmException, IllegalBlockSizeException, NoSuchPaddingException, InvalidKeyException, IOException {
        byte[] encr;
        encr = MiIoCrypto.encrypt(command.getBytes(), token);
        timeStamp = (int) TimeUnit.MILLISECONDS.toSeconds(Calendar.getInstance().getTime().getTime());
        byte[] sendMsg = Message.creatMsgData(encr, token, deviceId, timeStamp + timeDelta);
        Message miIoResponseMsg = sendData(sendMsg, ip);
        if (miIoResponseMsg == null){
            errorCounter++;
            if (errorCounter > MAX_ERRORS){
                status = ThingStatusDetail.CONFIGURATION_ERROR;
                sendPing(ip);
            }
            return "{\"error\":\"No Response\"}";
        }
        if (!miIoResponseMsg.isChecksumValid()){
            return "{\"error\":\"Message has invalid checksum\"}";
        }
        if (errorCounter > 0){
            errorCounter = 0;
            status = ThingStatusDetail.NONE;
            updateStatus(ThingStatus.ONLINE, status);
        }
        if (!connected){
            pingSuccess();
        }
        String decrytedResponse = new String(MiIoCrypto.decrypt(miIoResponseMsg.getData(), token));
        return decrytedResponse;
    }

    private void pingSuccess() {
        if (!connected){
            connected = true;

            status = ThingStatusDetail.NONE;
            updateStatus(ThingStatus.ONLINE, ThingStatusDetail.NONE);
        }else {
            if (ThingStatusDetail.CONFIGURATION_ERROR.equals(status)){
                updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_ERROR);
            }else{
                status = ThingStatusDetail.NONE;
                updateStatus(ThingStatus.ONLINE, status);
            }
        }
    }

    public Message sendPing(String ip) throws IOException {
        for (int i = 0; i < 3; i++) {
            Message resp = sendData(MyXiaoMiBindingConstants.DISCOVER_STRING, ip);
            if (resp != null){
                pingSuccess();
                return resp;
            }
        }
        connected = false;
        status = ThingStatusDetail.COMMUNICATION_ERROR;
        updateStatus(ThingStatus.OFFLINE, status);
        return null;
    }

    private void updateStatus(ThingStatus status, ThingStatusDetail statusDetail){
        for(MessageListener listener:listeners){
            try {
                listener.statusUpdate(status, statusDetail);
            }catch (Exception e){
                logger.debug("Could not inform listener {}: {}", listener, e.getMessage(), e);
            }
        }
    }

    private Message sendData(byte[] sendMsg, String ip) throws IOException {
        byte[] response = comms(sendMsg, ip);
        if(response.length >= 32){
            Message miIoResponse = new Message(response);
            timeStamp = (int) TimeUnit.MILLISECONDS.toSeconds(Calendar.getInstance().getTime().getTime());
            timeDelta = miIoResponse.getTimestampAsInt() - timeStamp;
            return miIoResponse;
        }else{
            return null;
        }
    }

    private synchronized byte[] comms(byte[] msg, String ip) throws IOException {
        InetAddress ipAddress = InetAddress.getByName(ip);
        DatagramSocket socket = getSocket();
        DatagramPacket receive = new DatagramPacket(new byte[MSG_BUFFER_SIZE], MSG_BUFFER_SIZE);
        try{
            byte[] sendData = new byte[MSG_BUFFER_SIZE];
            sendData = msg;
            DatagramPacket sendPacket = new DatagramPacket(sendData, sendData.length, ipAddress, MyXiaoMiBindingConstants.PORT);
            socket.send(sendPacket);
            sendPacket.setData(new byte[MSG_BUFFER_SIZE]);
            socket.receive(receive);
            byte[] responce = Arrays.copyOfRange(receive.getData(), receive.getOffset(), receive.getOffset()+receive.getLength());
            return responce;
        } catch (SocketTimeoutException e){
            needPing = true;
            return new byte[0];
        }
    }

    public class MessageSenderThread extends Thread{
        public MessageSenderThread(){
            super("Mi IO MessageSenderThread");
            setDaemon(true);
        }

        public void run(){
            logger.debug("Starting Mi IO MessageSenderThread");
            while (!interrupted()){
                try {
                    if (commandConcurrentLinkedQueue.isEmpty()){
                        Thread.sleep(100);
                        continue;
                    }
                    MiIoSendCommand queueMessage = commandConcurrentLinkedQueue.remove();
                    MiIoSendCommand miIoSendCommand = sendMiIosendCommand(queueMessage);
                    for (MessageListener listener : listeners){
                        try{
                            listener.messageReceived(miIoSendCommand);
                        }catch (Exception e){
                            logger.debug("Could not inform listener {}: {}: ", listener, e.getMessage(), e);
                        }
                    }
                }catch (Exception e){
                    logger.warn("something error while send message");
                }
            }
        }
    }


}
