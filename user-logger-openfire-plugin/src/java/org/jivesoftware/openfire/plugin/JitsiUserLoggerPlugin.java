/*
 * Copyright (C) 2004-2008 Jive Software. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jivesoftware.openfire.plugin;

import java.io.File;
import java.util.*;
import java.util.regex.PatternSyntaxException;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.HttpClient;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.message.BasicNameValuePair;
import org.dom4j.Element;
import org.jivesoftware.openfire.MessageRouter;
import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.openfire.container.Plugin;
import org.jivesoftware.openfire.container.PluginManager;
import org.jivesoftware.openfire.interceptor.InterceptorManager;
import org.jivesoftware.openfire.interceptor.PacketInterceptor;
import org.jivesoftware.openfire.interceptor.PacketRejectedException;
import org.jivesoftware.openfire.muc.MUCEventDispatcher;
import org.jivesoftware.openfire.muc.MUCEventListener;
import org.jivesoftware.openfire.session.Session;
import org.jivesoftware.openfire.user.User;
import org.jivesoftware.openfire.user.UserManager;
import org.jivesoftware.util.EmailService;
import org.jivesoftware.util.JiveGlobals;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xmpp.packet.JID;
import org.xmpp.packet.Message;
import org.xmpp.packet.Packet;
import org.xmpp.packet.Presence;

/**
 * Content filter plugin.
 * 
 * @author Conor Hayes
 */
public class JitsiUserLoggerPlugin implements Plugin, PacketInterceptor, MUCEventListener {

    private static final Logger Log = LoggerFactory.getLogger(JitsiUserLoggerPlugin.class);

    private static final String userLoggerEndpoint = "http://user-log:8199";

    private Set<String> activeRooms = new HashSet<>();
    private Map<String, Map<String, String>> nicknames = new HashMap<>();

    /**
     * the hook into the inteceptor chain
     */
    private InterceptorManager interceptorManager;

    /**
     * used to send violation notifications
     */
    private MessageRouter messageRouter;

    public JitsiUserLoggerPlugin() {
        interceptorManager = InterceptorManager.getInstance();
//        violationNotificationFrom = new JID(XMPPServer.getInstance()
//                .getServerInfo().getXMPPDomain());
        messageRouter = XMPPServer.getInstance().getMessageRouter();
    }

    /**
     * Restores the plugin defaults.
     */
    public void reset() {
    }
    
    public void initializePlugin(PluginManager pManager, File pluginDirectory) {
        // register with interceptor manager
        interceptorManager.addInterceptor(this);
        MUCEventDispatcher.addListener(this);
    }

    /**
     * @see org.jivesoftware.openfire.container.Plugin#destroyPlugin()
     */
    public void destroyPlugin() {
        // unregister with interceptor manager
        interceptorManager.removeInterceptor(this);
        MUCEventDispatcher.removeListener(this);
    }

    public void interceptPacket(Packet packet, Session session, boolean read,
            boolean processed) throws PacketRejectedException {

        if (packet == null || packet.getTo() == null) {
            return;
        }

        processNicknameUpdatePacket(packet);
    }

    private void processNicknameUpdatePacket(Packet packet) {
        String packetRoomAddress = getJidNodeAndDomain(packet.getTo());
        String packetUserAddress = getJidNodeAndDomain(packet.getFrom());

        if (packetRoomAddress == null || packetUserAddress == null)
            return;

        if (activeRooms.contains(packetRoomAddress)) {
            Element elem = packet.getElement();
            if (elem.getQualifiedName().equals("presence")) {
                Iterator<Element> iter = elem.elementIterator();
                while (iter.hasNext()) {
                    Element child = iter.next();
                    if (child.getQualifiedName().equals("nick")) {
                        String nicknameText = child.getText();

                        if (nicknameText == null || nicknameText.length() == 0) {
                            return;
                        }

                        String savedNickname = this.getNicknameForUserInRoom(packetRoomAddress, packetUserAddress);
//                        Log.error("Saved nickname for user " + packetUserAddress + " in room " + packetRoomAddress
//                            + " was '" + (savedNickname == null ? "NULL" : savedNickname)
//                            + "', current nickname is '" + nicknameText + "'");
                        if (nicknameText.equals(savedNickname)) {
                            return; // Avoid duplicate logging
                        }

                        this.saveNicknameForUserInRoom(packetRoomAddress, packetUserAddress, nicknameText);

                        List<NameValuePair> params = new ArrayList<NameValuePair>();
                        params.add(new BasicNameValuePair("room", packetRoomAddress));
                        params.add(new BasicNameValuePair("user", packetUserAddress));
                        params.add(new BasicNameValuePair("nickname", nicknameText));

                        this.sendHttpLogRequest("log/nicknameUpdate", params);
                    }
                }
            }
        }
    }

    private String getJidNodeAndDomain(JID jid) {
        if (jid == null) {
            return null;
        }
        String packetNode = jid.getNode();
        if (packetNode == null) {
            return null;
        }
        String packetDomain = jid.getDomain();
        String packetRoomAddress = packetNode + "@" + packetDomain;
        return packetRoomAddress;
    }

    @Override
    public void roomCreated(JID roomJid) {
        try {
            activeRooms.add(roomJid.toString());

            List<NameValuePair> params = new ArrayList<NameValuePair>();
            params.add(new BasicNameValuePair("room", roomJid.toString()));

            this.sendHttpLogRequest("log/roomCreated", params);
        } catch (Exception ex) {
            Log.error(ex.toString());
        }
    }

    @Override
    public void roomDestroyed(JID roomJid) {
        try {
            activeRooms.remove(roomJid.toString());

            List<NameValuePair> params = new ArrayList<NameValuePair>();
            params.add(new BasicNameValuePair("room", roomJid.toString()));

            this.sendHttpLogRequest("log/roomDestroyed", params);
        } catch (Exception ex) {
            Log.error(ex.toString());
        }
    }

    @Override
    public void occupantJoined(JID roomJid, JID userJid, String userNickname) {
        try {
            List<NameValuePair> params = new ArrayList<NameValuePair>();
            params.add(new BasicNameValuePair("room", roomJid.toString()));
            params.add(new BasicNameValuePair("user", this.getJidNodeAndDomain(userJid)));

            this.sendHttpLogRequest("log/userJoined", params);
        } catch (Exception ex) {
            Log.error(ex.toString());
        }
    }

    @Override
    public void occupantLeft(JID roomJid, JID userJid) {
        try {
            List<NameValuePair> params = new ArrayList<NameValuePair>();
            params.add(new BasicNameValuePair("room", roomJid.toString()));
            params.add(new BasicNameValuePair("user", this.getJidNodeAndDomain(userJid)));

            this.sendHttpLogRequest("log/userLeft", params);
        } catch (Exception ex) {
            Log.error(ex.toString());
        }
    }

    @Override
    public void nicknameChanged(JID jid, JID jid1, String s, String s1) {
    }

    @Override
    public void messageReceived(JID jid, JID jid1, String s, Message message) {
    }

    @Override
    public void privateMessageRecieved(JID jid, JID jid1, Message message) {
    }

    @Override
    public void roomSubjectChanged(JID jid, JID jid1, String s) {
    }

    private void sendHttpLogRequest(String endpoint, List<NameValuePair> params) {
        Runnable runnable = () -> {
            try {
                HttpClient httpclient = HttpClients.createDefault();
                HttpPost httppost = new HttpPost(userLoggerEndpoint + "/" + endpoint);

                httppost.setEntity(new UrlEncodedFormEntity(params, "UTF-8"));

                HttpResponse response = httpclient.execute(httppost);

                Log.info("Received response from log endpoint '" + userLoggerEndpoint + "/" + endpoint + "': " + response.toString());
            }
            catch (Exception e) {
                Log.error(e.toString());
            }
        };

        Thread thread = new Thread(runnable);
        thread.start();
    }

    private void saveNicknameForUserInRoom(String room, String user, String nickname) {
        Map<String, String> roomNicknames = getNicknamesMapForRoom(room);
        roomNicknames.put(user, nickname);
    }

    private String getNicknameForUserInRoom(String room, String user) {
        Map<String, String> roomNicknames = getNicknamesMapForRoom(room);
        if (roomNicknames.containsKey(user)) {
            return roomNicknames.get(user);
        }
        return null;
    }

    private Map<String, String> getNicknamesMapForRoom(String room) {
        if (!nicknames.containsKey(room)) {
            nicknames.put(room, new HashMap<String, String>());
        }
        return nicknames.get(room);
    }
}
