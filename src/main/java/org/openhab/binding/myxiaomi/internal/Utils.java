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

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.apache.commons.io.IOUtils;
import org.apache.commons.net.util.Base64;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.junit.Test;

import java.io.IOException;
import java.net.URL;
/**
 * The {@link Utils} is responsible for creating Xiaomi messages.
 *
 * @author zaoweiceng
 */
@NonNullByDefault
public class Utils {
    public static byte[] hexStringToByteArray(String hex){
        String s = hex.replace(" ", "");
        int len = s.length();
        byte[] data = new byte[len/2];
        for (int i = 0; i < len; i += 2) {
            data[i/2] = (byte) ((Character.digit(s.charAt(i), 16) << 4) + Character.digit(s.charAt(i+1), 16));
        }
        return data;
    }

    public static final String HEXES = "0123456789ABCDEF";


    public static String getSpacedHex(byte[] raw){
        final StringBuilder hex = new StringBuilder(3*raw.length);
        for (byte b : raw) {
            hex.append(HEXES.charAt((b & 0xF0) >> 4)).append(HEXES.charAt(b & 0x0F)).append(" ");
        }
        hex.delete(hex.length()-1, hex.length());
        return hex.toString();
    }

    public static String getHex(byte[] raw){
        final StringBuilder hex = new StringBuilder(3*raw.length);
        for (byte b : raw) {
            hex.append(HEXES.charAt((b & 0xF0) >> 4)).append(HEXES.charAt(b & 0x0F));
        }
        return hex.toString();
    }

    public static JsonObject convertFileToJSON(URL fileName) throws IOException {
        JsonObject jsonObject = new JsonObject();
        JsonParser parser = new JsonParser();
        JsonElement jsonElement = parser.parse(IOUtils.toString(fileName));
        jsonObject = jsonElement.getAsJsonObject();
        return jsonObject;
    }

}
