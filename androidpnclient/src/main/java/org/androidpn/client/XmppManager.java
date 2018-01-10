/*
 * Copyright (C) 2010 Moduad Co., Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.androidpn.client;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.os.Handler;
import android.util.Log;

import org.jivesoftware.smack.ConnectionConfiguration;
import org.jivesoftware.smack.ConnectionConfiguration.SecurityMode;
import org.jivesoftware.smack.ConnectionListener;
import org.jivesoftware.smack.PacketListener;
import org.jivesoftware.smack.XMPPConnection;
import org.jivesoftware.smack.XMPPException;
import org.jivesoftware.smack.filter.AndFilter;
import org.jivesoftware.smack.filter.PacketFilter;
import org.jivesoftware.smack.filter.PacketIDFilter;
import org.jivesoftware.smack.filter.PacketTypeFilter;
import org.jivesoftware.smack.packet.IQ;
import org.jivesoftware.smack.packet.Packet;
import org.jivesoftware.smack.packet.Registration;
import org.jivesoftware.smack.provider.ProviderManager;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Future;

/**
 * 注：此处任务使用了单队列模型。
 * 有一下若干变量:
 * taskList
 * taskTracker
 * taskSubmitter.
 * 处理逻辑：
 * 1、开始 taskList 为空，没有可运行任务；
 * 2、之后会一次添加connectionTask,registerTask,loginTask;
 * 3、
 *      a、添加第一个任务时，调用 addTask 方法，先做taskTracker.increase()；
 *      若任务submit失败，则直接做taskTracker.decrease()逻辑，否则调用第一个任务的run方法，执行第一个任务。
 *      b、第一个任务执行逻辑中，必须要有xmppManager.runTask()逻辑的执行。
 *         因为，runTask()中会做相应处理，第一，若还有其他任务，会继续拿出任务，接着执行；第二，也是最重要的，
 *         还会做taskTracker.decrease()，它减掉的是已经成功执行的任务的代表个数，也就是一个。
 * 4、在3的基础上，重复运行taskList的其他任务。
 */

/**
 * This class is to manage the XMPP connection between client and server.
 * 
 * @author Sehwan Noh (devnoh@gmail.com)
 */
public class XmppManager {

    private static final String LOGTAG = LogUtil.makeLogTag(XmppManager.class);

    private static final String XMPP_RESOURCE_NAME = "AndroidpnClient";

    private Context context;

    private NotificationService.TaskSubmitter taskSubmitter;

    private NotificationService.TaskTracker taskTracker;

    private SharedPreferences sharedPrefs;

    private String xmppHost;

    private int xmppPort;

    private XMPPConnection connection;

    private String username;

    private String password;

    private ConnectionListener connectionListener;

    private PacketListener notificationPacketListener;

    private Handler handler;

    private List<Runnable> taskList;

    private boolean running = false;

    private Future<?> futureTask;

    private Thread reconnection;

    public XmppManager(NotificationService notificationService) {
        context = notificationService;
        taskSubmitter = notificationService.getTaskSubmitter();
        taskTracker = notificationService.getTaskTracker();
        sharedPrefs = notificationService.getSharedPreferences();

        xmppHost = sharedPrefs.getString(Constants.XMPP_HOST, "localhost");
        xmppPort = sharedPrefs.getInt(Constants.XMPP_PORT, 8888);
        username = sharedPrefs.getString(Constants.XMPP_USERNAME, "");
        password = sharedPrefs.getString(Constants.XMPP_PASSWORD, "");

        connectionListener = new PersistentConnectionListener(this);
        notificationPacketListener = new NotificationPacketListener(this);

        handler = new Handler();
        taskList = new ArrayList<Runnable>();
        reconnection = new ReconnectionThread(this);
    }

    public Context getContext() {
        return context;
    }

    public void connect() {
        Log.d(LOGTAG, "connect()...");
        submitLoginTask();
    }

    public void disconnect() {
        Log.d(LOGTAG, "disconnect()...");
        terminatePersistentConnection();
    }

    public void terminatePersistentConnection() {
        Log.d(LOGTAG, "terminatePersistentConnection()...");
        Runnable runnable = new Runnable() {

            final XmppManager xmppManager = XmppManager.this;

            public void run() {
                if (xmppManager.isConnected()) {
                    Log.d(LOGTAG, "terminatePersistentConnection()... run()");
                    xmppManager.getConnection().removePacketListener(
                            xmppManager.getNotificationPacketListener());
                    xmppManager.getConnection().disconnect();
                }
                xmppManager.runTask();
            }

        };
        addTask(runnable);
    }

    public XMPPConnection getConnection() {
        return connection;
    }

    public void setConnection(XMPPConnection connection) {
        this.connection = connection;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public ConnectionListener getConnectionListener() {
        return connectionListener;
    }

    public PacketListener getNotificationPacketListener() {
        return notificationPacketListener;
    }

    public void startReconnectionThread() {
        synchronized (reconnection) {
            if (reconnection == null || !reconnection.isAlive()) {//add by rongwenzhao  reconnection == null condition
                reconnection = new ReconnectionThread(this);//add by rongwenzhao new thread 防止Thread被多次start的bug.
                reconnection.setName("Xmpp Reconnection Thread");
                reconnection.start();
            }
        }
    }

    public Handler getHandler() {
        return handler;
    }

    public void reregisterAccount() {
        removeAccount();
        submitLoginTask();
        runTask();
    }

    public List<Runnable> getTaskList() {
        return taskList;
    }

    public Future<?> getFutureTask() {
        return futureTask;
    }

    public void runTask() {
        Log.d(LOGTAG, "runTask()...");
        synchronized (taskList) {
            Log.d(LOGTAG, "runTask()...&& task size = " + taskList.size());
            running = false;
            futureTask = null;
            if (!taskList.isEmpty()) {
                Runnable runnable = (Runnable) taskList.get(0);
                taskList.remove(0);
                running = true;
                futureTask = taskSubmitter.submit(runnable);
                Log.d("rwz","futureTask = " + futureTask);
                if (futureTask == null) {//没添加任务成功，直接任务列表减一。
                    taskTracker.decrease();
                }
            }
        }
        taskTracker.decrease();//每次执行完一个任务之后，才会调用xmppManager.runTask()方法，此时才会将任务个数减一。
        Log.d(LOGTAG, "runTask()...done");
    }

    private String newRandomUUID() {
        String uuidRaw = UUID.randomUUID().toString();
        return uuidRaw.replaceAll("-", "");
    }

    private boolean isConnected() {
        return connection != null && connection.isConnected();
    }

    private boolean isAuthenticated() {
        return connection != null && connection.isConnected()
                && connection.isAuthenticated();
    }

    private boolean isRegistered() {
        return sharedPrefs.contains(Constants.XMPP_USERNAME)
                && sharedPrefs.contains(Constants.XMPP_PASSWORD);
    }

    private void submitConnectTask() {
        Log.d(LOGTAG, "submitConnectTask()...");
        addTask(new ConnectTask());
    }

    private void submitRegisterTask() {
        Log.d(LOGTAG, "submitRegisterTask()...");
        submitConnectTask();
        addTask(new RegisterTask());
    }

    private void submitLoginTask() {
        Log.d(LOGTAG, "submitLoginTask()...");
        submitRegisterTask();
        addTask(new LoginTask());
    }

    private void addTask(Runnable runnable) {
        Log.d(LOGTAG, "addTask(runnable)...");
        taskTracker.increase();
        synchronized (taskList) {
            if (taskList.isEmpty() && !running) {
                running = true;
                futureTask = taskSubmitter.submit(runnable);
                if (futureTask == null) {
                    taskTracker.decrease();
                }
            } else {
                taskList.add(runnable);
            }
        }
        Log.d(LOGTAG, "addTask(runnable)... done");
    }

    private void removeAccount() {
        Editor editor = sharedPrefs.edit();
        editor.remove(Constants.XMPP_USERNAME);
        editor.remove(Constants.XMPP_PASSWORD);
        editor.commit();
    }

    /**
     *  删除任务列表中任务。
     * @param dropCount 删除任务的个数
     */
    private void dropTask(int dropCount){
        synchronized (taskList){
            if(taskList.size() >= dropCount){
                for (int i = 0; i < dropCount; i++) {
                    taskList.remove(0);//每次循环，删除任务列表头的一个任务。
                    taskTracker.decrease();
                }
            }
        }
    }

    /**
     * A runnable task to connect the server. 
     */
    private class ConnectTask implements Runnable {

        final XmppManager xmppManager;

        private ConnectTask() {
            this.xmppManager = XmppManager.this;
        }

        public void run() {
            Log.i(LOGTAG, "ConnectTask.run()...");

            if (!xmppManager.isConnected()) {
                // Create the configuration for this new connection
                ConnectionConfiguration connConfig = new ConnectionConfiguration(
                        xmppHost, xmppPort);
                // connConfig.setSecurityMode(SecurityMode.disabled);
                connConfig.setSecurityMode(SecurityMode.required);
                connConfig.setSASLAuthenticationEnabled(false);
                connConfig.setCompressionEnabled(false);

                XMPPConnection connection = new XMPPConnection(connConfig);
                xmppManager.setConnection(connection);

                try {
                    // Connect to the server
                    connection.connect();
                    Log.i(LOGTAG, "XMPP connected successfully");

                    // packet provider
                    ProviderManager.getInstance().addIQProvider("notification",
                            "androidpn:iq:notification",
                            new NotificationIQProvider());
                    xmppManager.runTask();//连接成功，直接调用运行任务列表的代码

                } catch (XMPPException e) {
                    Log.e(LOGTAG, "XMPP connection failed", e);
                    xmppManager.dropTask(2);//删除后面的 注册，登录两个任务
                    xmppManager.startReconnectionThread();//启用断线重连
                    xmppManager.runTask();//此时已经drop掉后面的任务了，但tracker还没有减掉自己的任务。此处调用，做tracker减一操作。
                }

            } else {
                Log.i(LOGTAG, "XMPP connected already");
                xmppManager.runTask();
            }
        }
    }

    /**
     * A runnable task to register a new user onto the server. 
     */
    private class RegisterTask implements Runnable {

        final XmppManager xmppManager;
        boolean isRegisterSucceed;
        boolean hasDropTask;

        private RegisterTask() {
            xmppManager = XmppManager.this;
        }

        public void run() {
            Log.i(LOGTAG, "RegisterTask.run()...");

            if (!xmppManager.isRegistered()) {
                isRegisterSucceed = false;
                hasDropTask = false;
                final String newUsername = newRandomUUID();
                final String newPassword = newRandomUUID();

                Registration registration = new Registration();

                PacketFilter packetFilter = new AndFilter(new PacketIDFilter(
                        registration.getPacketID()), new PacketTypeFilter(
                        IQ.class));

                PacketListener packetListener = new PacketListener() {

                    public void processPacket(Packet packet) {
                        //添加锁，保证isRegisterSucceed的同步性。 add by rongwenzhao
                        synchronized (xmppManager) {
                            if (packet instanceof IQ) {
                                IQ response = (IQ) packet;
                                if (response.getType() == IQ.Type.ERROR) {
                                    if (!response.getError().toString().contains(
                                            "409")) {
                                        Log.e(LOGTAG,
                                                "Unknown error while registering XMPP account! "
                                                        + response.getError()
                                                        .getCondition());
                                    }
                                } else if (response.getType() == IQ.Type.RESULT) {
                                    xmppManager.setUsername(newUsername);
                                    xmppManager.setPassword(newPassword);
                                    Log.d(LOGTAG, "username=" + newUsername);
                                    Log.d(LOGTAG, "password=" + newPassword);

                                    Editor editor = sharedPrefs.edit();
                                    editor.putString(Constants.XMPP_USERNAME,
                                            newUsername);
                                    editor.putString(Constants.XMPP_PASSWORD,
                                            newPassword);
                                    editor.commit();

                                    isRegisterSucceed = true;

                                    Log
                                            .i(LOGTAG,
                                                    "Account registered successfully");
                                    if(!hasDropTask) {//没有dropTask，则继续执行登录任务。
                                        xmppManager.runTask();
                                    }
                                }
                            }
                        }
                    }
                };

                connection.addPacketListener(packetListener, packetFilter);

                registration.setType(IQ.Type.SET);
                // registration.setTo(xmppHost);
                // Map<String, String> attributes = new HashMap<String, String>();
                // attributes.put("username", rUsername);
                // attributes.put("password", rPassword);
                // registration.setAttributes(attributes);
                registration.addAttribute("username", newUsername);
                registration.addAttribute("password", newPassword);
                connection.sendPacket(registration);//将注册请求发送到服务器

                //bugfix[原来代码存在严重问题:在执行注册的task的时候，服务器没有返回，
                // 此时，没法继续执行任务列表里面的任务。解决方案:线程挺10s钟，10s之后，认为服务器已经处理结束，接着做下一步处理。
                // 当然，10s之后返回成功了，drop了任务，用了hasDropTask标志位，
                // 使得返回回调里面也不会再执行任务列表里面的任务。] add by rongwenzhao begin
                //睡眠线程
                try {
                    Thread.sleep(10*1000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                synchronized (xmppManager) {
                    if (!isRegisterSucceed) {
                        xmppManager.dropTask(1);//删后面的登录任务
                        xmppManager.startReconnectionThread();
                        xmppManager.runTask();
                        hasDropTask = true;
                    }
                }
                //bugfix add by rongwenzhao end

            } else {
                Log.i(LOGTAG, "Account registered already");
                xmppManager.runTask();
            }
        }
    }

    /**
     * A runnable task to log into the server. 
     */
    private class LoginTask implements Runnable {

        final XmppManager xmppManager;

        private LoginTask() {
            this.xmppManager = XmppManager.this;
        }

        public void run() {
            Log.i(LOGTAG, "LoginTask.run()...");

            if (!xmppManager.isAuthenticated()) {
                Log.d(LOGTAG, "username=" + username);
                Log.d(LOGTAG, "password=" + password);

                try {
                    xmppManager.getConnection().login(
                            xmppManager.getUsername(),
                            xmppManager.getPassword(), XMPP_RESOURCE_NAME);
                    Log.d(LOGTAG, "Loggedn in successfully");

                    // connection listener
                    if (xmppManager.getConnectionListener() != null) {
                        xmppManager.getConnection().addConnectionListener(
                                xmppManager.getConnectionListener());
                    }

                    // packet filter
                    PacketFilter packetFilter = new PacketTypeFilter(
                            NotificationIQ.class);
                    // packet listener
                    PacketListener packetListener = xmppManager
                            .getNotificationPacketListener();
                    connection.addPacketListener(packetListener, packetFilter);

                    //add heartBeat thread start logic
                    connection.startHeartBeat();

                } catch (XMPPException e) {
                    Log.e(LOGTAG, "LoginTask.run()... xmpp error");
                    Log.e(LOGTAG, "Failed to login to xmpp server. Caused by: "
                            + e.getMessage());
                    String INVALID_CREDENTIALS_ERROR_CODE = "401";
                    String errorMessage = e.getMessage();
                    if (errorMessage != null
                            && errorMessage
                                    .contains(INVALID_CREDENTIALS_ERROR_CODE)) {
                        xmppManager.reregisterAccount();
                        return;
                    }
                    xmppManager.startReconnectionThread();

                } catch (Exception e) {
                    Log.e(LOGTAG, "LoginTask.run()... other error");
                    Log.e(LOGTAG, "Failed to login to xmpp server. Caused by: "
                            + e.getMessage());
                    xmppManager.startReconnectionThread();
                }finally {
                    //就算try或catch中有return语句，finally还是会执行。
                    //mod by rongwenzhao 保证出任何异常都可以执行runTask方法。
                    xmppManager.runTask();
                }

            } else {
                Log.i(LOGTAG, "Logged in already");
                xmppManager.runTask();
            }

        }
    }

}
