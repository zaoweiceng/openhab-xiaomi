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
package org.openhab.binding.myxiaomi.annotation;

import org.eclipse.smarthome.core.thing.ThingStatus;
import org.eclipse.smarthome.core.thing.ThingStatusDetail;
import org.openhab.binding.myxiaomi.entity.MiIoCommand;
import org.openhab.binding.myxiaomi.entity.json.MiIoSendCommand;
/**
 * The {@link MessageListener} is responsible for creating Xiaomi messages.
 *
 * @author zaoweiceng
 */
public interface MessageListener {
    void messageReceived(MiIoSendCommand cmd);
    void statusUpdate(ThingStatus status, ThingStatusDetail thingStatusDetail);
}
