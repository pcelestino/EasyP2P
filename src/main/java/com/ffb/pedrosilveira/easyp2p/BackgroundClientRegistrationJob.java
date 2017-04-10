package com.ffb.pedrosilveira.easyp2p;

import android.util.Log;

import com.arasthel.asyncjob.AsyncJob;
import com.bluelinelabs.logansquare.LoganSquare;
import com.ffb.pedrosilveira.easyp2p.callbacks.EasyP2pCallback;
import com.ffb.pedrosilveira.easyp2p.payloads.device.DeviceInfo;

import org.apache.commons.io.Charsets;

import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;

@SuppressWarnings({"WeakerAccess", "FieldCanBeLocal"})
public class BackgroundClientRegistrationJob implements AsyncJob.OnBackgroundJob {

    private EasyP2p easyP2pInstance;
    private InetSocketAddress hostDeviceAddress;
    private final int BUFFER_SIZE = 65536;
    protected static boolean disableWiFiOnUnregister;
    protected static EasyP2pCallback onRegistered;
    protected static EasyP2pCallback onRegistrationFail;
    protected static EasyP2pCallback onUnregisterSuccess;
    protected static EasyP2pCallback onUnregisterFailure;


    public BackgroundClientRegistrationJob(EasyP2p easyP2pInstance, InetSocketAddress hostDeviceAddress) {
        this.hostDeviceAddress = hostDeviceAddress;
        this.easyP2pInstance = easyP2pInstance;
    }

    @Override
    public void doOnBackground() {
        Log.d(EasyP2p.TAG, "\nTentativa de transferência de dados de registro com o servidor...");
        Socket registrationSocket = new Socket();

        try {
            registrationSocket.connect(hostDeviceAddress);
            registrationSocket.setReceiveBufferSize(BUFFER_SIZE);
            registrationSocket.setSendBufferSize(BUFFER_SIZE);

            //Se esse código for alcançado, nós nos conectaremos ao servidor e transferiremos dados.
            Log.d(EasyP2p.TAG, easyP2pInstance.thisDevice.deviceName + " Está conectado ao servidor, transferindo dados de registo...");

            BufferedOutputStream toClient = new BufferedOutputStream(registrationSocket.getOutputStream());

            Log.v(EasyP2p.TAG, "Enviando dados de registro do cliente para o servidor...");
            String serializedClient = LoganSquare.serialize(easyP2pInstance.thisDevice);
            toClient.write(serializedClient.getBytes(Charsets.UTF_8));
            toClient.flush();


            if (!easyP2pInstance.thisDevice.isRegistered) {
                Log.v(EasyP2p.TAG, "Recebimento de dados de registro do servidor...");
                DeviceInfo serverDeviceInfo = LoganSquare.parse(registrationSocket.getInputStream(), DeviceInfo.class);

                serverDeviceInfo.device.serviceAddress = registrationSocket.getInetAddress().toString().replace("/", "");
                easyP2pInstance.registeredHost = serverDeviceInfo.device;
                easyP2pInstance.registeredClients = serverDeviceInfo.devices;

                Log.d(EasyP2p.TAG, "Host Registrado | " + easyP2pInstance.registeredHost.deviceName);

                easyP2pInstance.thisDevice.isRegistered = true;
                easyP2pInstance.dataReceiver.activity.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (onRegistered != null)
                            onRegistered.call();
                    }
                });

                // Informa a todos os outros dispositivos as informações do dispositivo atual
                DeviceInfo deviceInfo = new DeviceInfo();
                deviceInfo.message = DeviceInfo.INFORM_DEVICE;
                deviceInfo.device = easyP2pInstance.thisDevice;
                easyP2pInstance.sendToAllDevices(deviceInfo, null, null);

                easyP2pInstance.startListeningForData();
            } else {

                DeviceInfo deviceInfo = LoganSquare.parse(registrationSocket.getInputStream(), DeviceInfo.class);
                Log.d(EasyP2p.TAG, "Código de desregistro: " + deviceInfo.message);

                easyP2pInstance.thisDevice.isRegistered = false;
                easyP2pInstance.registeredHost = null;
                easyP2pInstance.closeDataSocket();
                easyP2pInstance.disconnectFromDevice();

                if (onUnregisterSuccess != null) //Success Callback.
                {
                    easyP2pInstance.dataReceiver.activity.runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            onUnregisterSuccess.call();
                        }
                    });
                }

                Log.d(EasyP2p.TAG, "Este dispositivo foi removido com êxito do servidor.");

            }

            toClient.close();

        } catch (IOException ex) {
            ex.printStackTrace();

            Log.e(EasyP2p.TAG, "Ocorreu um erro ao tentar registrar ou cancelar o registro.");
            easyP2pInstance.dataReceiver.activity.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    if (onRegistrationFail != null && !easyP2pInstance.thisDevice.isRegistered) //Prevents both callbacks from being called.
                        onRegistrationFail.call();
                    if (onUnregisterFailure != null)
                        onUnregisterFailure.call();

                }
            });


            if (easyP2pInstance.thisDevice.isRegistered && easyP2pInstance.isConnectedToAnotherDevice) {
                //Failed to unregister so an outright disconnect is necessary.
                easyP2pInstance.disconnectFromDevice();
            }
        } finally {

            if (disableWiFiOnUnregister) {
                EasyP2p.disableWiFi(easyP2pInstance.dataReceiver.activity);
            }
            try {
                registrationSocket.close();
            } catch (Exception ex) {
                Log.e(EasyP2p.TAG, "Falha ao fechar o soquete de registro.");
            }
        }
    }
}
