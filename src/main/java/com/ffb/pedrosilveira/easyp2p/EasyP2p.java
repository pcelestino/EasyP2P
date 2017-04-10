package com.ffb.pedrosilveira.easyp2p;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.IntentFilter;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.net.wifi.p2p.WifiP2pConfig;
import android.net.wifi.p2p.WifiP2pDevice;
import android.net.wifi.p2p.WifiP2pGroup;
import android.net.wifi.p2p.WifiP2pInfo;
import android.net.wifi.p2p.WifiP2pManager;
import android.net.wifi.p2p.nsd.WifiP2pDnsSdServiceInfo;
import android.net.wifi.p2p.nsd.WifiP2pDnsSdServiceRequest;
import android.net.wifi.p2p.nsd.WifiP2pServiceInfo;
import android.os.Build;
import android.os.Handler;
import android.support.annotation.Nullable;
import android.util.Log;

import com.arasthel.asyncjob.AsyncJob;
import com.ffb.pedrosilveira.easyp2p.callbacks.EasyP2pCallback;
import com.ffb.pedrosilveira.easyp2p.callbacks.EasyP2pDeviceCallback;
import com.ffb.pedrosilveira.easyp2p.payloads.bully.BullyElection;

import java.io.IOException;
import java.lang.reflect.Method;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;


@SuppressWarnings("WeakerAccess")
public class EasyP2p implements WifiP2pManager.ConnectionInfoListener {

    protected static final String TAG = "EasyP2p";
    private static final int EASY_P2P_SERVER_PORT = 37500;
    private static final int MAX_SERVER_CONNECTIONS = 80;
    private static final int BUFFER_SIZE = 65536;
    protected static final String STRING_ENCODING = "UTF-8";
    protected static final String UNREGISTER_CODE = "UNREGISTER_EASY_P2P_DEVICE";
    protected String TTP = "._tcp";
    protected EasyP2pDataReceiver dataReceiver;
    protected boolean receiverRegistered = false;

    private static WifiManager wifiManager;
    private boolean respondersAlreadySet = false;
    private boolean firstDeviceAlreadyFound = false;
    private boolean connectingIsCanceled = false;
    private EasyP2pCallback deviceNotSupported;
    protected boolean registrationIsRunning = false;
    protected EasyP2pDeviceCallback onDeviceRegisteredWithHost;
    protected EasyP2pDeviceCallback onDeviceUnregisteredWithHost;
    protected EasyP2pCallback unexpectedDisconnect;

    public EasyP2pDevice thisDevice;
    public EasyP2pDevice registeredHost;
    public boolean isRunningAsHost = false;
    public boolean isConnectedToAnotherDevice = false;
    public boolean isDiscovering = false;
    private ServerSocket listenerServiceSocket;
    private ServerSocket easyP2pServerSocket;

    //WiFi P2P Objects
    private WifiP2pServiceInfo serviceInfo;
    private WifiP2pDnsSdServiceRequest serviceRequest;
    private WifiP2pManager manager;
    private WifiP2pManager.Channel channel;

    protected IntentFilter intentFilter = new IntentFilter();
    protected BroadcastReceiver receiver = null;

    //Found Service Objects
    protected EasyP2pDevice lastConnectedDevice;
    public ArrayList<EasyP2pDevice> foundDevices;
    public ArrayList<EasyP2pDevice> registeredClients;


    @SuppressLint("HardwareIds")
    public EasyP2p(EasyP2pDataReceiver dataReceiver, EasyP2pServiceData easyP2pServiceData, EasyP2pCallback deviceNotSupported) {
        WifiManager wifiManager = (WifiManager) dataReceiver.context.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        WifiInfo wifiInfo = wifiManager.getConnectionInfo();

        this.dataReceiver = dataReceiver;
        this.deviceNotSupported = deviceNotSupported;
        this.TTP = easyP2pServiceData.serviceData.get("SERVICE_NAME") + TTP;

        thisDevice = new EasyP2pDevice();
        int deviceCode = wifiInfo.getMacAddress().hashCode();
        thisDevice.id = deviceCode;
        thisDevice.serviceName = easyP2pServiceData.serviceData.get("SERVICE_NAME");
        thisDevice.readableName = easyP2pServiceData.serviceData.get("INSTANCE_NAME");
        thisDevice.instanceName = String.valueOf(deviceCode);
        thisDevice.macAddress = wifiInfo.getMacAddress();
        thisDevice.TTP = thisDevice.serviceName + TTP;
        thisDevice.servicePort = Integer.valueOf(easyP2pServiceData.serviceData.get("SERVICE_PORT"));
        thisDevice.txtRecord = easyP2pServiceData.serviceData;

        foundDevices = new ArrayList<>();

        intentFilter.addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION);
        intentFilter.addAction(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION);
        intentFilter.addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION);
        intentFilter.addAction(WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION);

        manager = (WifiP2pManager) dataReceiver.context.getSystemService(Context.WIFI_P2P_SERVICE);
        channel = manager.initialize(dataReceiver.context, dataReceiver.context.getMainLooper(), new WifiP2pManager.ChannelListener() {
            @Override
            public void onChannelDisconnected() {
                Log.d(TAG, "Reinicializando o canal.");
                channel = manager.initialize(EasyP2p.this.dataReceiver.context, EasyP2p.this.dataReceiver.context.getMainLooper(), this);
            }
        });

        receiver = new EasyP2pBroadcastReceiver(this, manager, channel);
    }

    private void obtainEasyP2pPortLock() {
        if (easyP2pServerSocket == null || easyP2pServerSocket.isClosed()) {
            try {
                easyP2pServerSocket = new ServerSocket(EASY_P2P_SERVER_PORT, MAX_SERVER_CONNECTIONS);
                easyP2pServerSocket.setReuseAddress(true);
                easyP2pServerSocket.setReceiveBufferSize(BUFFER_SIZE);
                thisDevice.txtRecord.put("EASY_P2P_SERVER_PORT", String.valueOf(EASY_P2P_SERVER_PORT));
            } catch (IOException ex) {
                Log.e(TAG, "Falha ao usar porta padrão, outra será usada em vez disso.");

                try {
                    easyP2pServerSocket = new ServerSocket(0, MAX_SERVER_CONNECTIONS);
                    easyP2pServerSocket.setReuseAddress(true);
                    easyP2pServerSocket.setReceiveBufferSize(BUFFER_SIZE);
                    thisDevice.txtRecord.put("EASY_P2P_SERVER_PORT", String.valueOf(easyP2pServerSocket.getLocalPort()));
                } catch (IOException ioEx) {
                    Log.e(TAG, "Falha ao obter uma porta aleatória, EasyP2p não funcionará corretamente.");
                }

            }
        }
    }

    private void obtainServicePortLock() {
        if (listenerServiceSocket == null || listenerServiceSocket.isClosed()) {
            try {
                listenerServiceSocket = new ServerSocket(thisDevice.servicePort, MAX_SERVER_CONNECTIONS);
                listenerServiceSocket.setReuseAddress(true);
                listenerServiceSocket.setReceiveBufferSize(BUFFER_SIZE);
                thisDevice.txtRecord.put("SERVICE_PORT", String.valueOf(thisDevice.servicePort));
            } catch (IOException ex) {
                Log.e(TAG, "Falha ao usar porta padrão, outra será usada em vez disso.");

                try {
                    listenerServiceSocket = new ServerSocket(0, MAX_SERVER_CONNECTIONS);
                    listenerServiceSocket.setReuseAddress(true);
                    listenerServiceSocket.setReceiveBufferSize(BUFFER_SIZE);
                    thisDevice.txtRecord.put("SERVICE_PORT", String.valueOf(listenerServiceSocket.getLocalPort()));
                } catch (IOException ioEx) {
                    Log.e(TAG, "Falha ao obter uma porta aleatória, " + thisDevice.serviceName + " não funcionará corretamente.");
                }

            }
        }
    }

    public ArrayList<String> getReadableFoundNames() {
        ArrayList<String> foundHostNames = new ArrayList<>(foundDevices.size());
        for (EasyP2pDevice device : foundDevices) {
            foundHostNames.add(device.readableName);
        }

        return foundHostNames;
    }

    public ArrayList<String> getReadableRegisteredNames() {
        ArrayList<String> registeredNames = new ArrayList<>(registeredClients.size());
        for (EasyP2pDevice device : registeredClients) {
            registeredNames.add(device.readableName);
        }

        return registeredNames;
    }

    @Override
    public void onConnectionInfoAvailable(final WifiP2pInfo info) {
        /* Este método é automaticamente chamado quando nos conectamos a um dispositivo.
           O proprietário do grupo aceita conexões usando um server socket e, em seguida,
           gera um client socket para cada cliente. Isso é tratado pelos registration jobs.
           Isto irá lidar automaticamente com as primeiras conexões. */

        manager.requestGroupInfo(channel, new WifiP2pManager.GroupInfoListener() {
            @Override
            public void onGroupInfoAvailable(WifiP2pGroup group) {

                if (isRunningAsHost && !registrationIsRunning) {
                    if (info.groupFormed && !group.getClientList().isEmpty()) {
                        startHostRegistrationServer();
                    }
                } else if (!thisDevice.isRegistered && !info.isGroupOwner) {
                    if (serviceRequest == null) {
                        //Isso significa que discoverNetworkServices nunca foi chamado e ainda estamos conectados a um host antigo por algum motivo.
                        Log.e(EasyP2p.TAG, "Este dispositivo ainda está conectado a um host antigo por algum motivo. Uma desconexão forçada será tentada.");
                        forceDisconnect();
                    }
                    Log.v(EasyP2p.TAG, "Conectado com êxito a outro dispositivo.");
                    startRegistrationForClient(new InetSocketAddress(info.groupOwnerAddress.getHostAddress(), EASY_P2P_SERVER_PORT));
                }
            }
        });
    }

    public static void enableWiFi(Context context) {
        wifiManager = (WifiManager) context.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        wifiManager.setWifiEnabled(true);
    }

    public static boolean isWiFiEnabled(Context context) {
        if (hotspotIsEnabled(context)) {
            return false;
        }

        wifiManager = (WifiManager) context.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        return wifiManager.isWifiEnabled();
    }

    public static void disableWiFi(Context context) {
        wifiManager = (WifiManager) context.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        wifiManager.setWifiEnabled(false);
    }

    public static boolean hotspotIsEnabled(Context context) {
        try {
            wifiManager = (WifiManager) context.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
            Method method = wifiManager.getClass().getDeclaredMethod("isWifiApEnabled");
            method.setAccessible(true);

            return (Boolean) method.invoke(wifiManager, (Object[]) null);
        } catch (Exception ex) {
            ex.printStackTrace();
            Log.d(TAG, "Falha ao verificar o estado de tethering, ou ele não está habilitado.");
        }

        return false;
    }

    protected void closeRegistrationSocket() {
        try {
            if (registrationIsRunning) {
                easyP2pServerSocket.close();
                Log.v(TAG, "Registration sockets fechados.");
            }
        } catch (Exception ex) {
            Log.e(TAG, "Falha ao fechar o registration socket.");
        }

        registrationIsRunning = false;
    }

    protected void closeDataSocket() {
        try {
            listenerServiceSocket.close();
            Log.v(TAG, "Parou de ouvir os dados do serviço.");
        } catch (Exception ex) {
            Log.e(TAG, "Falha ao fechar o listening socket.");
        }
    }

    private void startRegistrationForClient(final InetSocketAddress hostDeviceAddress) {

        BackgroundClientRegistrationJob registrationJob = new BackgroundClientRegistrationJob(this, hostDeviceAddress);
        AsyncJob.doInBackground(registrationJob);
    }

    private void sendData(final EasyP2pDevice device, final Object data, @Nullable EasyP2pCallback onSuccess, @Nullable EasyP2pCallback onFailure) {
        BackgroundDataSendJob sendDataToDevice = new BackgroundDataSendJob(device, EasyP2p.this, data, onSuccess, onFailure);
        AsyncJob.doInBackground(sendDataToDevice);
    }

    private void startHostRegistrationServer() {
        obtainEasyP2pPortLock();

        AsyncJob.doInBackground(new AsyncJob.OnBackgroundJob() {
            @Override
            public void doOnBackground() {

                try {
                    //Create a server socket and wait for client connections. This
                    //call blocks until a connection is accepted from a client.
                    registrationIsRunning = true;
                    while (isRunningAsHost) {
                        Log.d(TAG, "\nOuvindo dados de registro...");
                        Socket clientSocket = easyP2pServerSocket.accept();
                        BackgroundServerRegistrationJob registrationJob = new BackgroundServerRegistrationJob(EasyP2p.this, clientSocket);

                        AsyncJob.doInBackground(registrationJob);
                    }
                    registrationIsRunning = false;
                } catch (Exception ex) {
                    Log.e(TAG, "Ocorreu um erro na thread de registro do servidor.");
                    ex.printStackTrace();
                }
            }
        });
    }

    protected void startListeningForData() {
        obtainServicePortLock();

        AsyncJob.doInBackground(new AsyncJob.OnBackgroundJob() {
            @Override
            public void doOnBackground() {
                try {
                    //Create a server socket and wait for client connections. This
                    //call blocks until a connection is accepted from a client.

                    while (isRunningAsHost || thisDevice.isRegistered) {

                        Log.d(TAG, "\nOuvindo dados de serviço...");

                        Socket dataListener = listenerServiceSocket.accept();
                        BackgroundDataJob dealWithData = new BackgroundDataJob(EasyP2p.this, dataListener);

                        AsyncJob.doInBackground(dealWithData);
                    }

                } catch (Exception ex) {
                    Log.e(TAG, "Ocorreu um erro na thread de escuta de dados do servidor.");
                    ex.printStackTrace();
                }
            }
        });
    }

    public void registerWithHost(final EasyP2pDevice device, @Nullable EasyP2pCallback onRegistered, @Nullable final EasyP2pCallback onRegistrationFail) {
        BackgroundClientRegistrationJob.onRegistered = onRegistered;
        BackgroundClientRegistrationJob.onRegistrationFail = onRegistrationFail;
        this.unexpectedDisconnect = onRegistrationFail;
        connectToDevice(device, null, onRegistrationFail);
    }

    public void sendToAllDevices(final Object data, @Nullable EasyP2pCallback onSuccess, @Nullable EasyP2pCallback onFailure) {
        for (EasyP2pDevice registered : registeredClients) {
            sendData(registered, data, onSuccess, onFailure);
        }
    }

    public void sendToHost(final Object data, @Nullable EasyP2pCallback onSuccess, @Nullable EasyP2pCallback onFailure) {
        if (!isRunningAsHost && thisDevice.isRegistered) {
            sendData(registeredHost, data, onSuccess, onFailure);
        } else {
            Log.e(TAG, "Este dispositivo não é o host e, portanto, não pode invocar este método.");
        }
    }

    public void sendToDevice(final EasyP2pDevice device, final Object data, @Nullable EasyP2pCallback onSuccess, @Nullable EasyP2pCallback onFailure) {
        sendData(device, data, onSuccess, onFailure);
    }

    public void cancelConnecting() {
        manager.cancelConnect(channel, new WifiP2pManager.ActionListener() {
            @Override
            public void onSuccess() {
                Log.v(TAG, "Tentando cancelar a conexão.");
            }

            @Override
            public void onFailure(int reason) {
                Log.v(TAG, "Falha ao cancelar a conexão, o dispositivo pode não estar tentando se conectar.");
            }
        });

        stopServiceDiscovery(true);
        connectingIsCanceled = true;
    }

    private void connectToDevice(final EasyP2pDevice device, @Nullable final EasyP2pCallback onSuccess, @Nullable final EasyP2pCallback onFailure) {

        WifiP2pConfig config = new WifiP2pConfig();
        config.deviceAddress = device.macAddress;
        manager.connect(channel, config, new WifiP2pManager.ActionListener() {

            @Override
            public void onSuccess() {
                Log.d(TAG, "Tentando se conectar a outro dispositivo.");
                lastConnectedDevice = device;
                if (onSuccess != null)
                    onSuccess.call();
            }

            @Override
            public void onFailure(int reason) {
                if (onFailure != null)
                    onFailure.call();
                Log.e(TAG, "Falha ao conectar ao dispositivo.");
            }
        });
    }

    private void deleteGroup(WifiP2pManager manager, WifiP2pManager.Channel channel, WifiP2pGroup wifiP2pGroup) {
        try {
            Method getNetworkId = WifiP2pGroup.class.getMethod("getNetworkId");
            Integer networkId = (Integer) getNetworkId.invoke(wifiP2pGroup);
            Method deletePersistentGroup = WifiP2pManager.class.getMethod("deletePersistentGroup",
                    WifiP2pManager.Channel.class, Integer.class, WifiP2pManager.ActionListener.class);
            deletePersistentGroup.invoke(manager, channel, networkId, null);
        } catch (Exception ex) {
            Log.v(EasyP2p.TAG, "Falha ao excluir o persistent group.");
        }
    }

    protected void forceDisconnect() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
            WifiP2pManager.ActionListener doNothing = new WifiP2pManager.ActionListener() {
                @Override
                public void onSuccess() {

                }

                @Override
                public void onFailure(int reason) {

                }
            };

            stopServiceDiscovery(false);
            manager.cancelConnect(channel, doNothing);
            manager.clearLocalServices(channel, doNothing);
            manager.clearServiceRequests(channel, doNothing);
            manager.stopPeerDiscovery(channel, doNothing);
        }
    }

    protected void disconnectFromDevice() {
        manager.requestGroupInfo(channel, new WifiP2pManager.GroupInfoListener() {
            @Override
            public void onGroupInfoAvailable(final WifiP2pGroup group) {
                if (group != null) {
                    manager.removeGroup(channel, new WifiP2pManager.ActionListener() {
                        @Override
                        public void onSuccess() {
                            isConnectedToAnotherDevice = false;
                            deleteGroup(manager, channel, group);
                            Log.d(TAG, "WiFi Direct Group Removido.");
                        }

                        @Override
                        public void onFailure(int reason) {
                            Log.e(TAG, "Falha ao remover o WiFi Direct Group. Razão: " + reason);
                        }
                    });
                }
            }
        });
    }

    @TargetApi(Build.VERSION_CODES.JELLY_BEAN)
    private void createService(final EasyP2pCallback onSuccess, final EasyP2pCallback onFailure) {

        manager.clearLocalServices(channel, null);

        Log.d(TAG, "Iniciando " + thisDevice.serviceName + " Protocolo de Transferência " + TTP);

        //Injeta a porta de escuta junto com qualquer outro dado que vai ser enviado.
        thisDevice.txtRecord.put("LISTEN_PORT", String.valueOf(thisDevice.servicePort));

        //Criar um objeto de informação de serviço será android realmente entregará para os clientes.
        serviceInfo = WifiP2pDnsSdServiceInfo.newInstance(thisDevice.instanceName, TTP, thisDevice.txtRecord);

        // Registre nosso serviço. Os callbacks aqui apenas nos deixe saber se o serviço foi
        // registrado corretamente, não necessariamente se ou não nós conectado a um dispositivo.
        manager.addLocalService(channel, serviceInfo, new WifiP2pManager.ActionListener() {
            @Override
            public void onSuccess() {
                Log.v(TAG, "O serviço local foi adicionado com sucesso.");
                manager.createGroup(channel, new WifiP2pManager.ActionListener() {
                    @Override
                    public void onSuccess() {
                        Log.v(TAG, "Grupo criado com sucesso.");
                        Log.d(TAG, thisDevice.serviceName + " criado com sucesso e está rodando na porta " + thisDevice.servicePort);
                        isRunningAsHost = true;
                        thisDevice.isHost = true;
                        if (onSuccess != null) {
                            onSuccess.call();
                        }
                    }

                    @Override
                    public void onFailure(int reason) {
                        Log.e(TAG, "Falha ao criar o grupo. Razão: " + reason);
                        if (onFailure != null)
                            onFailure.call();
                    }
                });
            }

            @Override
            public void onFailure(int error) {
                Log.e(TAG, "Falha ao criar o " + thisDevice.serviceName + " : Código de erro: " + error);
                if (onFailure != null)
                    onFailure.call();
            }
        });
    }

    public void unregisterClient(@Nullable EasyP2pCallback onSuccess, @Nullable EasyP2pCallback onFailure, boolean disableWiFi) {

        BackgroundClientRegistrationJob.onUnregisterSuccess = onSuccess;
        BackgroundClientRegistrationJob.onUnregisterFailure = onFailure;
        BackgroundClientRegistrationJob.disableWiFiOnUnregister = disableWiFi;

        if (receiverRegistered) {
            dataReceiver.context.unregisterReceiver(receiver);
            receiverRegistered = false;
        }

        if (!isConnectedToAnotherDevice) {
            Log.d(TAG, "Tentativa de cancelar o registro, mas não conectado ao grupo. O serviço remoto pode já estar gesligado.");
            thisDevice.isRegistered = false;
            registeredHost = null;
            closeDataSocket();
            disconnectFromDevice();
            if (onSuccess != null) {
                onSuccess.call();
            }
        } else {
            startRegistrationForClient(new InetSocketAddress(registeredHost.serviceAddress, EASY_P2P_SERVER_PORT));
        }
    }

    public void unregisterClient(boolean disableWiFi) {
        unregisterClient(null, null, disableWiFi);
    }

    public void startNetworkService(EasyP2pDeviceCallback onDeviceRegisteredWithHost, EasyP2pDeviceCallback onDeviceUnregisteredWithHost) {
        startNetworkService(onDeviceRegisteredWithHost, onDeviceUnregisteredWithHost, null, null);
    }

    public void startNetworkService(@Nullable EasyP2pDeviceCallback onDeviceRegisteredWithHost, @Nullable EasyP2pDeviceCallback onDeviceUnregisteredWithHost, @Nullable EasyP2pCallback onSuccess, @Nullable EasyP2pCallback onFailure) {
        //A fim de ter um serviço que você criar ser visto, você também deve ativamente procurar outros serviços. Este é um bug do Android.
        //Para mais informações, leia aqui. https://code.google.com/p/android/issues/detail?id=37425
        //Não precisamos configurar os DNS responders.
        registeredClients = new ArrayList<>();

        this.onDeviceRegisteredWithHost = onDeviceRegisteredWithHost;
        this.onDeviceUnregisteredWithHost = onDeviceUnregisteredWithHost;

        if (!receiverRegistered) {
            dataReceiver.context.registerReceiver(receiver, intentFilter);
            receiverRegistered = true;
        }

        createService(onSuccess, onFailure);
        discoverNetworkServices(deviceNotSupported);
    }

    @TargetApi(Build.VERSION_CODES.JELLY_BEAN)
    private void setupDNSResponders() {
         /* Aqui, registramos um ouvinte para quando os serviços são realmente encontrados.
            A especificação WiFi P2P observa que precisamos de dois tipos de ouvintes, um para um
            serviço DNS e outro para um registro TXT. O ouvinte do serviço DNS é invocado sempre que
            um serviço é encontrado, independentemente de ser ou não seu. Para isso determinar se é,
            temos de comparar o nosso nome de serviço com o nome do serviço. Se for o nosso serviço,
            simplesmente registramos. */

        WifiP2pManager.DnsSdServiceResponseListener serviceListener = new WifiP2pManager.DnsSdServiceResponseListener() {
            @Override
            public void onDnsSdServiceAvailable(String instanceName, String serviceNameAndTP, WifiP2pDevice sourceDevice) {

                Log.d(TAG, "Found " + instanceName + " " + serviceNameAndTP);

            }
        };

        /* O registro TXT contém informações específicas sobre um serviço e seu ouvinte também pode
           ser invocado independentemente do dispositivo. Aqui, nós verificamos se o dispositivo é
           nosso, e então nós seguimos em frente e puxamos aquela informação específica dele e a
           colocamos em um Mapa. A função que foi passada no início também é chamada */
        WifiP2pManager.DnsSdTxtRecordListener txtRecordListener = new WifiP2pManager.DnsSdTxtRecordListener() {
            @Override
            public void onDnsSdTxtRecordAvailable(String serviceFullDomainName, Map<String, String> record, WifiP2pDevice device) {

                if (!foundDevices.isEmpty()) {
                    for (EasyP2pDevice found : foundDevices) {
                        if (found.deviceName.equals(device.deviceName)) {
                            return;
                        }
                    }
                }

                if (record.containsValue(thisDevice.serviceName)) {
                    EasyP2pDevice foundDevice = new EasyP2pDevice(device, record);
                    foundDevices.add(foundDevice);
                }
            }
        };

        manager.setDnsSdResponseListeners(channel, serviceListener, txtRecordListener);
        respondersAlreadySet = true;
    }

    @TargetApi(Build.VERSION_CODES.JELLY_BEAN)
    private void setupDNSResponders(final EasyP2pCallback onDeviceFound, final boolean callContinously) {
         /* Aqui, registramos um ouvinte para quando os serviços são realmente encontrados. A
            especificação WiFi P2P observa que precisamos de dois tipos de ouvintes, um para um
            serviço DNS e outro para um registro TXT. O ouvinte do serviço DNS é invocado sempre que
            um serviço é encontrado, independentemente de ser ou não seu. Para isso determinar se é,
            temos de comparar o nosso nome de serviço com o nome do serviço. Se for o nosso serviço,
            simplesmente registramos. */

        WifiP2pManager.DnsSdServiceResponseListener serviceListener = new WifiP2pManager.DnsSdServiceResponseListener() {
            @Override
            public void onDnsSdServiceAvailable(String instanceName, String serviceNameAndTP, WifiP2pDevice sourceDevice) {

                Log.d(TAG, "Found " + instanceName + " " + serviceNameAndTP);

            }
        };

        /* O registro TXT contém informações específicas sobre um serviço e seu ouvinte também pode
           ser invocado independentemente do dispositivo. Aqui, nós verificamos se o dispositivo é
           nosso, e então nós seguimos em frente e puxamos aquela informação específica dele e a
           colocamos em um Mapa. A função que foi passada no início também é chamada */
        WifiP2pManager.DnsSdTxtRecordListener txtRecordListener = new WifiP2pManager.DnsSdTxtRecordListener() {
            @Override
            public void onDnsSdTxtRecordAvailable(String serviceFullDomainName, Map<String, String> record, WifiP2pDevice device) {

                if (!foundDevices.isEmpty()) {
                    for (EasyP2pDevice found : foundDevices) {
                        if (found.deviceName.equals(device.deviceName)) {
                            return;
                        }
                    }
                }

                if (record.containsValue(thisDevice.serviceName)) {
                    EasyP2pDevice foundDevice = new EasyP2pDevice(device, record);
                    foundDevices.add(foundDevice);

                    if (callContinously) {
                        onDeviceFound.call();
                    } else {
                        if (!firstDeviceAlreadyFound) {
                            onDeviceFound.call();
                            firstDeviceAlreadyFound = true;
                        }
                    }
                }
            }
        };

        manager.setDnsSdResponseListeners(channel, serviceListener, txtRecordListener);
        respondersAlreadySet = true;
    }

    @TargetApi(Build.VERSION_CODES.JELLY_BEAN)
    private void setupDNSRespondersWithDevice(final EasyP2pDeviceCallback onDeviceFound, final boolean callContinously) {
         /* Aqui, registramos um ouvinte para quando os serviços são realmente encontrados. A
            especificação WiFi P2P observa que precisamos de dois tipos de ouvintes, um para um
            serviço DNS e outro para um registro TXT. O ouvinte do serviço DNS é invocado sempre que
            um serviço é encontrado, independentemente de ser ou não seu. Para isso determinar se é,
            temos de comparar o nosso nome de serviço com o nome do serviço. Se for o nosso serviço,
            simplesmente registramos. */

        WifiP2pManager.DnsSdServiceResponseListener serviceListener = new WifiP2pManager.DnsSdServiceResponseListener() {
            @Override
            public void onDnsSdServiceAvailable(String instanceName, String serviceNameAndTP, WifiP2pDevice sourceDevice) {

                Log.d(TAG, "Found " + instanceName + " " + serviceNameAndTP);
            }
        };

        /* O registro TXT contém informações específicas sobre um serviço e seu ouvinte também pode
           ser invocado independentemente do dispositivo. Aqui, nós verificamos se o dispositivo é
           nosso, e então nós seguimos em frente e puxamos aquela informação específica dele e a
           colocamos em um Mapa. A função que foi passada no início também é chamada */
        WifiP2pManager.DnsSdTxtRecordListener txtRecordListener = new WifiP2pManager.DnsSdTxtRecordListener() {
            @Override
            public void onDnsSdTxtRecordAvailable(String serviceFullDomainName, Map<String, String> record, WifiP2pDevice device) {


                if (!foundDevices.isEmpty()) {
                    for (EasyP2pDevice found : foundDevices) {
                        if (found.deviceName.equals(device.deviceName)) {
                            return;
                        }
                    }
                }

                if (record.containsValue(thisDevice.serviceName)) {
                    EasyP2pDevice foundDevice = new EasyP2pDevice(device, record);

                    foundDevices.add(foundDevice);
                    if (callContinously) {
                        onDeviceFound.call(foundDevice);
                    } else {
                        if (!firstDeviceAlreadyFound) {
                            onDeviceFound.call(foundDevice);
                            firstDeviceAlreadyFound = true;
                        }
                    }
                }
            }
        };

        manager.setDnsSdResponseListeners(channel, serviceListener, txtRecordListener);
        respondersAlreadySet = true;
    }

    private void devicesNotFoundInTime(final EasyP2pCallback cleanUpFunction, final EasyP2pCallback devicesFound, int timeout) {
        new Handler().postDelayed(new Runnable() {
            @Override
            public void run() {
                if (connectingIsCanceled) {
                    connectingIsCanceled = false;
                    cleanUpFunction.call();
                } else {
                    if (foundDevices.isEmpty()) {
                        cleanUpFunction.call();
                    } else {
                        devicesFound.call();
                    }
                    stopServiceDiscovery(false);
                }
            }
        }, timeout);
    }

    @TargetApi(Build.VERSION_CODES.JELLY_BEAN)
    private void discoverNetworkServices(final EasyP2pCallback deviceNotSupported) {
        isDiscovering = true;

        foundDevices.clear();

        if (!receiverRegistered) {
            Log.v(EasyP2p.TAG, "EasyP2p reciever registrado.");
            dataReceiver.context.registerReceiver(receiver, intentFilter);
            receiverRegistered = true;
        }

        // Após anexar ouvintes, crie um pedido de serviço e inicie
        // descoberta.
        serviceRequest = WifiP2pDnsSdServiceRequest.newInstance();

        manager.addServiceRequest(channel, serviceRequest,
                new WifiP2pManager.ActionListener() {
                    @Override
                    public void onSuccess() {
                        Log.v(TAG, "Solicitação de descoberta de serviço reconhecida.");
                    }

                    @Override
                    public void onFailure(int arg0) {
                        Log.e(TAG, "Falha ao adicionar solicitação de descoberta de serviço.");
                    }
                });

        manager.discoverServices(channel, new WifiP2pManager.ActionListener() {
            @Override
            public void onSuccess() {
                Log.d(TAG, "Início da descoberta do serviço.");
            }

            @Override
            public void onFailure(int arg0) {
                Log.e(TAG, "A descoberta do serviço falhou. Código: " + arg0);
                if (arg0 == WifiP2pManager.P2P_UNSUPPORTED)
                    deviceNotSupported.call();
                if (arg0 == WifiP2pManager.NO_SERVICE_REQUESTS) {
                    disableWiFi(dataReceiver.context);
                    enableWiFi(dataReceiver.context);
                }
            }
        });

    }

    public void discoverNetworkServices(EasyP2pDeviceCallback onDeviceFound, boolean callContinously) {
        if (!respondersAlreadySet) {
            setupDNSRespondersWithDevice(onDeviceFound, callContinously);
        }

        discoverNetworkServices(deviceNotSupported);
    }

    public void discoverNetworkServices(EasyP2pCallback onDeviceFound, boolean callContinously) {
        if (!respondersAlreadySet) {
            setupDNSResponders(onDeviceFound, callContinously);
        }

        discoverNetworkServices(deviceNotSupported);
    }

    public void discoverWithTimeout(EasyP2pCallback onDevicesFound, EasyP2pCallback onDevicesNotFound, int timeout) {
        if (!respondersAlreadySet) {
            setupDNSResponders();
        }

        discoverNetworkServices(deviceNotSupported);
        devicesNotFoundInTime(onDevicesNotFound, onDevicesFound, timeout);
    }

    @TargetApi(Build.VERSION_CODES.JELLY_BEAN)
    public void stopNetworkService(final boolean disableWiFi, @Nullable final EasyP2pCallback onFinish) {
        if (isRunningAsHost) {
            Log.v(TAG, "Parando o serviço de rede...");
            stopServiceDiscovery(true);
            closeDataSocket();
            closeRegistrationSocket();

            if (manager != null && channel != null && serviceInfo != null) {

                manager.removeLocalService(channel, serviceInfo, new WifiP2pManager.ActionListener() {
                    @Override
                    public void onFailure(int reason) {
                        Log.d(TAG, "Não foi possível terminar o serviço. Razão : " + reason);
                        if (onFinish != null)
                            onFinish.call();
                    }

                    @Override
                    public void onSuccess() {
                        Log.v(TAG, "Serviço encerrado com sucesso.");
                        if (disableWiFi) {
                            disableWiFi(dataReceiver.context); //Called here to give time for request to be disposed.
                        }
                        isRunningAsHost = false;
                        thisDevice.isHost = false;
                        if (onFinish != null)
                            onFinish.call();
                    }
                });

                respondersAlreadySet = false;
            } else {
                if (onFinish != null)
                    onFinish.call();
            }

        } else {
            Log.d(TAG, "O serviço de rede não está em execução.");
            if (onFinish != null)
                onFinish.call();
        }
    }

    @TargetApi(Build.VERSION_CODES.JELLY_BEAN)
    public void stopServiceDiscovery(boolean shouldUnregister) {
        isDiscovering = false;
        firstDeviceAlreadyFound = false;

        if (isConnectedToAnotherDevice)
            disconnectFromDevice();

        if (shouldUnregister) {
            Log.v(EasyP2p.TAG, "Removido o registro de EasyP2p reciever.");
            dataReceiver.context.unregisterReceiver(receiver);
            receiverRegistered = false;
        }

        if (manager != null && channel != null) {
            manager.removeServiceRequest(channel, serviceRequest, new WifiP2pManager.ActionListener() {
                @Override
                public void onSuccess() {
                    Log.v(TAG, "Solicitação de descoberta de serviço removido com êxito.");
                }

                @Override
                public void onFailure(int reason) {
                    Log.v(TAG, "Falha ao remover solicitação de descoberta de serviço. Razão : " + reason);
                }
            });
        }
    }

    // BULLY ELECTION
    public void startElection(final EasyP2pDeviceCallback onSuccess, final EasyP2pDeviceCallback onFailure) {

        BullyElection bullyElection = new BullyElection();
        bullyElection.message = BullyElection.START_ELECTION;
        bullyElection.device = thisDevice;

        // Executa as threads de forma síncrona
        ExecutorService executorService = Executors.newSingleThreadExecutor();

        // Ordena a lista de forma ascendente
        Collections.sort(registeredClients, new Comparator<EasyP2pDevice>() {
            @Override
            public int compare(EasyP2pDevice d1, EasyP2pDevice d2) {
                return d1.id - d2.id;
            }
        });

        List<EasyP2pDevice> filteredListClients;

        if (isRunningAsHost) {
            filteredListClients = registeredClients;
        } else {
            int deviceNextIndex = registeredClients.indexOf(thisDevice) + 1;
            filteredListClients = registeredClients.subList(deviceNextIndex, registeredClients.size());
        }

        for (final EasyP2pDevice client : filteredListClients) {
            sendDataSync(executorService, client, bullyElection,
                    new EasyP2pCallback() {
                        @Override
                        public void call() {
                            onSuccess.call(client);
                        }
                    }, new EasyP2pCallback() {
                        @Override
                        public void call() {
                            onFailure.call(client);
                        }
                    });
        }
    }

    public void repondElection(EasyP2pDevice targetDevice, EasyP2pCallback onSuccess, EasyP2pCallback onFailure) {

        BullyElection bullyElection = new BullyElection();
        bullyElection.message = BullyElection.RESPOND_OK;
        bullyElection.device = thisDevice;

        if (targetDevice.isHost) {
            sendToHost(bullyElection, onSuccess, onFailure);
        } else {
            sendToDevice(targetDevice, bullyElection, onSuccess, onFailure);
        }
    }

    public void informLeader(EasyP2pCallback onSuccess, EasyP2pCallback onFailure) {

        BullyElection bullyElection = new BullyElection();
        bullyElection.message = BullyElection.INFORM_LEADER;
        bullyElection.device = thisDevice;

        // Ordena a lista de forma decrescente
        Collections.sort(registeredClients, new Comparator<EasyP2pDevice>() {
            @Override
            public int compare(EasyP2pDevice d1, EasyP2pDevice d2) {
                return d2.id - d1.id;
            }
        });

        int deviceNextIndex = registeredClients.indexOf(thisDevice) + 1;
        List<EasyP2pDevice> filteredListClients = registeredClients.subList(deviceNextIndex, registeredClients.size());

        for (EasyP2pDevice client : filteredListClients) {
            sendData(client, bullyElection, onSuccess, onFailure);
        }
    }

    private void sendDataSync(ExecutorService executorService, EasyP2pDevice device, Object data, @Nullable EasyP2pCallback onSuccess, @Nullable EasyP2pCallback onFailure) {
        BackgroundDataSendJob sendDataToDevice = new BackgroundDataSendJob(device, EasyP2p.this, data, onSuccess, onFailure);
        AsyncJob.doInBackground(sendDataToDevice, executorService);
    }
}
