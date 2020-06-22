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

/**
 * The {@link MiIoCommand} is responsible for creating Xiaomi messages.
 *
 * @author zaoweiceng
 */
public enum MiIoCommand {
    MIIO_INFO("miIO.info"),
    MIIO_WIFI("miIO.wifi_assoc_state"),
    MIIO_ROUTERCONFIG("miIO.miIO.config_router"),
    GET_PROPERTY("get_prop"),
    GET_VALUE("get_value"),
    SET_MODE_BASIC("set_mode"),
    SET_POWER("set_power"),
    SET_BRIGHT("set_bright"),
    UNKNOWN("");

    public String getCommand() {
        return command;
    }

    private final String command;

    MiIoCommand(String s) {
        this.command = s;
    }

    public static MiIoCommand getCommand(String s){
        for(MiIoCommand m : MiIoCommand.values()){
            if (m.getCommand().equals(s)){
                return m;
            }
        }
        return UNKNOWN;
    }
}
