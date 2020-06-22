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

import static org.openhab.binding.myxiaomi.internal.MyXiaoMiBindingConstants.*;

import com.google.gson.JsonParser;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.smarthome.core.thing.ChannelUID;
import org.eclipse.smarthome.core.thing.Thing;
import org.eclipse.smarthome.core.thing.ThingStatus;
import org.eclipse.smarthome.core.thing.binding.BaseThingHandler;
import org.eclipse.smarthome.core.thing.binding.ThingHandlerService;
import org.eclipse.smarthome.core.types.Command;
import org.eclipse.smarthome.core.types.RefreshType;
import org.openhab.binding.myxiaomi.entity.Devices;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.concurrent.ScheduledFuture;

/**
 * The {@link MyXiaoMiHandler} is responsible for handling commands, which are
 * sent to one of the channels.
 *
 * @author zaoweiceng - Initial contribution
 */
@NonNullByDefault
public class MyXiaoMiHandler extends MiIoHandler {

    private final Logger logger = LoggerFactory.getLogger(MyXiaoMiHandler.class);

    private @Nullable MyXiaoMiConfiguration config;

    private @Nullable ScheduledFuture<?> pollingJob;
    private @Nullable MyXiaoMiConfiguration configuration;
    private Devices miDevice = Devices.UNKNOWN;
    private boolean isIdentified;

    private @Nullable JsonParser parser;
    private  byte[] tocken = new byte[32];



    public MyXiaoMiHandler(Thing thing) {
        super(thing);
    }

    @Override
    public void handleCommand(ChannelUID channelUID, Command command) {
       if (command == RefreshType.REFRESH){
           logger.debug("refreshing {}", channelUID);
           updateData();
           return;
       }
       if (channelUID.getId().equals(CHANNEL_COMMAND)){
           cmds.put(sendCommand(command.toString()), command.toString());
       }
    }


    @Override
    protected void updateData() {
        if (skipUpdate()){
            return;
        }
        try {
            refreshNetwork();
        }catch (Exception e){
            logger.debug("Error while updating '{}'", getThing().getUID().toString(), e);
        }
    }
}
