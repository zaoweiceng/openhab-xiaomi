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
package org.openhab.binding.myxiaomi.internal;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.smarthome.config.core.Configuration;
import org.eclipse.smarthome.core.cache.ExpiringCache;
import org.eclipse.smarthome.core.library.types.DecimalType;
import org.eclipse.smarthome.core.library.types.StringListType;
import org.eclipse.smarthome.core.library.types.StringType;
import org.eclipse.smarthome.core.thing.ChannelUID;
import org.eclipse.smarthome.core.thing.Thing;
import org.eclipse.smarthome.core.thing.ThingStatus;
import org.eclipse.smarthome.core.thing.ThingStatusDetail;
import org.eclipse.smarthome.core.thing.binding.BaseThingHandler;
import org.eclipse.smarthome.core.thing.binding.builder.ThingBuilder;
import org.eclipse.smarthome.core.types.Command;
import org.openhab.binding.myxiaomi.annotation.MessageListener;
import org.openhab.binding.myxiaomi.entity.Devices;
import org.openhab.binding.myxiaomi.entity.MiIoCommand;
import org.openhab.binding.myxiaomi.entity.json.MiIoSendCommand;
import org.openhab.binding.myxiaomi.internal.transport.Communication;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import static org.openhab.binding.myxiaomi.internal.MyXiaoMiBindingConstants.*;
/**
 * The {@link MiIoHandler} is responsible for creating Xiaomi messages.
 *
 * @author zaoweiceng
 */
public abstract class MiIoHandler extends BaseThingHandler implements MessageListener {
    protected static final int MAX_QUEUE = 5;

    protected  ScheduledFuture<?> pollingJob;
    protected  MyXiaoMiConfiguration configuration;
    protected Devices miDevices = Devices.UNKNOWN;
    protected boolean isIdentified;

    protected JsonParser parser;
    protected  byte[] token;
    protected  Communication miioCom;
    protected int lastId;
    protected Map<Integer, String> cmds = new ConcurrentHashMap<Integer, String>();
    protected  ExpiringCache<String> network;
    protected static final long CACHE_EXPIRY = TimeUnit.SECONDS.toMillis(5);
    protected static final long CACHE_EXPIRY_NETWORK = TimeUnit.SECONDS.toMillis(60);
    private final Logger logger = LoggerFactory.getLogger(MiIoHandler.class);

    /**
     * Creates a new instance of this class for the {@link Thing}.
     *
     * @param thing the thing that should be handled, not null
     */
    @NonNullByDefault
    public MiIoHandler(Thing thing) {
        super(thing);
        parser = new JsonParser();
    }

    @Override
    public abstract void handleCommand(ChannelUID channelUID, Command command);

    @Override
    public void initialize() {
        configuration = getConfigAs(MyXiaoMiConfiguration.class);
        if (!tokenCheckPass(configuration.token)) {
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_ERROR, "Token required. Configure token");
            return;
        }
        isIdentified = false;
        scheduler.schedule(this::initialize, 1, TimeUnit.SECONDS);
        int pollingPeriod = configuration.refreshInterval;
        if (pollingPeriod > 0) {
            pollingJob = scheduler.scheduleWithFixedDelay(() -> {
                try {
                    updateData();
                } catch (Exception e) {
                    logger.debug("Unexpected error during refresh.", e);
                }
            }, 10, pollingPeriod, TimeUnit.SECONDS);
        } else {
            logger.debug("Polling job disabled. for '{}'", getThing().getUID());
            scheduler.schedule(this::updateData, 10, TimeUnit.SECONDS);
        }
        updateStatus(ThingStatus.OFFLINE);
    }

    protected abstract void updateData();

    private boolean tokenCheckPass(String tokenString) {
        switch (tokenString.length()) {
            case 16:
                token = tokenString.getBytes();
                return true;
            case 32:
                if (!IGNORED_TOKENS.contains(tokenString)) {
                    token = Utils.hexStringToByteArray(tokenString);
                    return true;
                }
                return false;
            case 96:
                token = Utils.hexStringToByteArray(MiIoCrypto.decryptToken(Utils.hexStringToByteArray(tokenString)));
                return true;
            default:
                return false;

        }
    }

    @Override
    public void dispose() {
        if (pollingJob != null) {
            pollingJob.cancel(true);
            pollingJob = null;
        }
        if (miioCom != null) {
            lastId = miioCom.getId();
            miioCom.unregisterListener(this);
            miioCom.close();
            miioCom = null;
        }
    }

    protected int sendCommand(MiIoCommand command) throws IOException {
        return sendCommand(command, "[]");
    }

    protected int sendCommand(MiIoCommand command, String params) throws IOException {
        if (!hasConnection()) {
            return 0;
        }
        return getConnection().queueCommand(command, params);
    }

    protected int sendCommand(String commandString) {
        if (!hasConnection()) {
            return 0;
        }
        String command = commandString.trim();
        String param = "[]";
        int loc = command.indexOf("[");
        loc = (loc > 0 ? loc : command.indexOf("{"));
        if (loc > 0) {
            param = command.substring(loc).trim();
            command = command.substring(0, loc).trim();
        }
        try {
            return miioCom.queueCommand(command, param);
        } catch (IOException e) {
            disconnected(e.getMessage());
        }
        return 0;
    }

    protected void disconnected(String message) {
        updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.OFFLINE.COMMUNICATION_ERROR, message);
        try {
            lastId = miioCom.getId();
            lastId += 10;
        } catch (Exception e) {

        }
    }

    protected boolean hasConnection() {
        return getConnection() != null;
    }

    protected synchronized Communication getConnection() {
        if (miioCom != null) {
            return miioCom;
        }
        String deviceId = configuration.deviceId;
        try {
            if (deviceId != null && deviceId.length() == 8 && tokenCheckPass(configuration.token)) {
                miioCom = new Communication(configuration.host, token, Utils.hexStringToByteArray(deviceId), lastId, configuration.timeout);
                Message miIOResponse = miioCom.sendPing(configuration.host);
                if (miIOResponse != null) {
                    miioCom.registerLisener(this);
                    return miioCom;
                } else {
                    Communication miioCom = new Communication(configuration.host, token, new byte[0], lastId, configuration.timeout);
                    Message miIoResponse = miioCom.sendPing(configuration.host);
                    if (miIoResponse != null) {
                        deviceId = Utils.getHex(miIoResponse.getDeviceId());
                        miioCom.setDeviceID(miIoResponse.getDeviceId());
                        updateDeviceIdConfig(deviceId);
                        miioCom.registerLisener(this);
                        return miioCom;
                    }
                }
            }
            return null;
        } catch (Exception e) {
            logger.debug("error {}", e.getMessage());
            return null;
        }
    }

    private void updateDeviceIdConfig(String deviceId) {
        if (deviceId != null) {
            updateProperty(Thing.PROPERTY_SERIAL_NUMBER, deviceId);
            Configuration config = editConfiguration();
            config.put(PROPERTY_DID, deviceId);
            updateConfiguration(config);
            configuration = getConfigAs(MyXiaoMiConfiguration.class);
        }
    }

    protected boolean skipUpdate() {
        if (!hasConnection()) {
            return true;
        }
        if (getThing().getStatusInfo().getStatusDetail().equals(ThingStatusDetail.COMMUNICATION_ERROR)) {
            try {
                miioCom.queueCommand(MiIoCommand.MIIO_INFO);
            } catch (IOException e) {

            }
            return true;
        }
        if (miioCom.getQueueLenth() > MAX_QUEUE) {
            return true;
        }
        return false;
    }

    protected boolean updateNetwork(JsonObject networkData) {
        try {
            updateState(CHANNEL_SSID, new StringType(networkData.getAsJsonObject("ap").get("ssid").getAsString()));
            updateState(CHANNEL_BSSID, new StringType(networkData.getAsJsonObject("ap").get("bssid").getAsString()));
            if (networkData.getAsJsonObject("ap").get("rssi") != null) {
                updateState(CHANNEL_RSSI, new DecimalType(networkData.getAsJsonObject("ap").get("rssi").getAsLong()));
            } else if (networkData.getAsJsonObject("ap").get("wifi_rssi") != null) {
                updateState(CHANNEL_RSSI, new DecimalType(networkData.getAsJsonObject("ap").get("wifi_rssi").getAsLong()));
            } else {
                logger.debug("No RSSI info in response");
            }
            updateState(CHANNEL_LIFE, new DecimalType(networkData.get("life").getAsLong()));
            return true;
        } catch (Exception e) {
            logger.debug("Could not parse network response: {}", networkData, e);
        }
        return false;
    }

    protected void disconnectedNoResponce() {
        disconnected("No Response from device");
    }

    protected boolean initializeData() {
        initializeNetwoekCache();
        this.miioCom = getConnection();
        return true;
    }

    protected void initializeNetwoekCache() {
        network = new ExpiringCache<String>(CACHE_EXPIRY_NETWORK, () -> {
            try {
                int ret = sendCommand(MiIoCommand.MIIO_INFO);
                if (ret != 0) {
                    return "id:" + ret;
                }
            } catch (Exception e) {
                logger.debug("Error during network status refresh: {}", e.getMessage(), e);
            }
            return null;
        });
    }

    protected void refreshNetwork() {
        if (network == null) {
            initializeNetwoekCache();
        }
        network.getValue();
    }

    protected void defineDeviceType(JsonObject miioInfo) {
        updateProperties(miioInfo);
        isIdentified = updateThingType(miioInfo);
    }

    private void updateProperties(JsonObject miioInfo) {
        Map<String, String> properties = editProperties();
        properties.put(Thing.PROPERTY_MODEL_ID, miioInfo.get("model").getAsString());
        properties.put(Thing.PROPERTY_FIRMWARE_VERSION, miioInfo.get("fw_ver").getAsString());
        properties.put(Thing.PROPERTY_HARDWARE_VERSION, miioInfo.get("hw_ver").getAsString());
        if (miioInfo.get("wifi_fw_ver") != null) {
            properties.put("wifiFiremware", miioInfo.get("wifi_fw_ver").getAsString());
        }
        if (miioInfo.get("mcu_fw_ver") != null) {
            properties.put("mcuFirmware", miioInfo.get("mcu_fw_ver").getAsString());
        }
        updateProperties(properties);
    }

    protected boolean updateThingType(JsonObject miioInfo) {
        String model = miioInfo.get("model").getAsString();
        miDevices = Devices.getType(model);
        if (configuration.model == null || configuration.model.isEmpty()) {
            Configuration config = editConfiguration();
            config.put(PROPERTY_MODEL, model);
            updateConfiguration(config);
            configuration = getConfigAs(MyXiaoMiConfiguration.class);
        }
        if (!configuration.model.equals(model)) {
            logger.info("Mi Device model {} has model config: {}. Unexpected unless manual override", model,
                    configuration.model);
        }
        if (miDevices.getThingType().equals(getThing().getThingTypeUID())) {
            logger.info("Mi Device model {} identified as: {}. Matches thingtype {}", model, miDevices.toString(),
                    miDevices.getThingType().toString());
            return true;
        } else {
            if (getThing().getThingTypeUID().equals(THING_TYPE_MIIO) || getThing().getThingTypeUID().equals(THING_TYPE_UNSUPPORTED)) {
                changeType(model);
            } else {
                logger.warn(
                        "Mi Device model {} identified as: {}, thingtype {}. Does not matches thingtype {}. Unexpected, unless unless manual override.",
                        miDevices.toString(), miDevices.getThingType(), getThing().getThingTypeUID().toString(),
                        miDevices.getThingType().toString());
                return true;
            }
        }
        return false;
    }

    private void changeType(final String modelId) {
        if (pollingJob != null) {
            pollingJob.cancel(true);
            pollingJob = null;
        }
        scheduler.schedule(() -> {
            ThingBuilder thingBuilder = editThing();
            thingBuilder.withLabel(miDevices.getDescription());
            updateThing(thingBuilder.build());
            logger.info("Mi Device model {} identified as: {}. Does not match thingtype {}. Changing thingtype to {}",
                    modelId, miDevices.toString(), getThing().getThingTypeUID().toString(),
                    miDevices.getThingType().toString());
            changeThingType(Devices.getType(modelId).getThingType(), getConfig());
        }, 10, TimeUnit.SECONDS);
    }

    public void messageReceived(MiIoSendCommand response) {
        if (response.isError()) {
            if (MiIoCommand.MIIO_INFO.equals(response.getCommand()) && network != null) {
                network.invalidateValue();
            }
            return;
        }
        try {
            switch (response.getCommand()) {
                case MIIO_INFO:
                    if (!isIdentified) {
                        defineDeviceType(response.getResult().getAsJsonObject());
                    }
                    updateNetwork(response.getResult().getAsJsonObject());
                    break;
                default:
                    break;
            }
            if (cmds.containsKey(response.getId())){
                updateState(CHANNEL_COMMAND, new StringType(response.getResponse().toString()));
                cmds.remove(response.getId());
            }
        }catch (Exception e){
            logger.debug("Error while handing message {}", response.getResponse(), e);
        }
    }

    public void statusUpdate(ThingStatus status, ThingStatusDetail thingStatusDetail) {
        updateStatus(status, thingStatusDetail);
    }

}
