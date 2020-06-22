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
package org.openhab.binding.myxiaomi.entity;


import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.smarthome.core.thing.ThingTypeUID;

import static org.openhab.binding.myxiaomi.internal.MyXiaoMiBindingConstants.*;
/**
 * The {@link Devices} is responsible for creating Xiaomi messages.
 *
 * @author zaoweiceng
 */
@NonNullByDefault
public enum Devices {
    POWERPLUG("chuangmi.plug.m1", "Mi Power-plug", THING_TYPE_MIIO),
    POWERPLUG1("chuangmi.plug.v1", "Mi Power-plug v1", THING_TYPE_MIIO),
    POWERPLUG2("chuangmi.plug.v2", "Mi Power-plug v2", THING_TYPE_MIIO),
    POWERPLUG3("chuangmi.plug.v3", "Mi Power-plug v3", THING_TYPE_MIIO),
    POWERPLUGM3("chuangmi.plug.m3", "Mi Power-plug", THING_TYPE_MIIO),
    POWERPLUG_HMI205("chuangmi.plug.hmi205", "Mi Smart Plug", THING_TYPE_MIIO),
    UNKNOWN("unknown", "Unknown Mi IO Device", THING_TYPE_UNSUPPORTED);
    private final String model;
    private final String description;
    private final ThingTypeUID thingType;

    @Override
    public String toString() {
        return description + " (" + model + ")";
    }

    public static Devices getType(String modelString){
        for (Devices device:Devices.values()){
            if (device.getModel().equals(modelString)){
                return device;
            }
        }
        return UNKNOWN;
    }

    public String getModel() {
        return model;
    }

    public String getDescription() {
        return description;
    }

    public ThingTypeUID getThingType() {
        return thingType;
    }


    Devices(String model, String description, ThingTypeUID thingTypeMi) {
        this.model = model;
        this.description = description;
        this.thingType = thingTypeMi;
    }
}
