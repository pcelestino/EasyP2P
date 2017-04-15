package com.ffb.pedrosilveira.easyp2p;

import android.util.Log;

import com.arasthel.asyncjob.AsyncJob;
import com.bluelinelabs.logansquare.LoganSquare;
import com.ffb.pedrosilveira.easyp2p.payloads.device.DeviceInfo;

import org.apache.commons.io.Charsets;

import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.net.Socket;

@SuppressWarnings("WeakerAccess")
public class BackgroundServerRegistrationJob implements AsyncJob.OnBackgroundJob {

    private EasyP2p easyP2pInstance;
    private Socket clientSocket;

    public BackgroundServerRegistrationJob(EasyP2p easyP2pInstance, Socket clientSocket) {
        this.clientSocket = clientSocket;
        this.easyP2pInstance = easyP2pInstance;
    }

    @Override
    public void doOnBackground() {
        try {
            //Se esse código for atingido, um cliente conectou e transferiu dados.
            Log.d(EasyP2p.TAG, "Um dispositivo está conectado ao servidor, transferindo dados...");
            BufferedOutputStream toClient = new BufferedOutputStream(clientSocket.getOutputStream());

            Log.v(EasyP2p.TAG, "Recebimento de dados de registro do cliente...");
            EasyP2pDevice clientDevice = LoganSquare.parse(clientSocket.getInputStream(), EasyP2pDevice.class);
            clientDevice.serviceAddress = clientSocket.getInetAddress().toString().replace("/", "");

            if (!clientDevice.isRegistered) {

                Log.v(EasyP2p.TAG, "Sending server registration data...");
                DeviceInfo deviceInfo = new DeviceInfo();
                deviceInfo.message = clientDevice.serviceAddress;
                deviceInfo.device = easyP2pInstance.thisDevice;
                deviceInfo.devices = easyP2pInstance.registeredClients;

                String serializedServer = LoganSquare.serialize(deviceInfo);
                toClient.write(serializedServer.getBytes(Charsets.UTF_8));
                toClient.flush();

                Log.d(EasyP2p.TAG, "Enviando dados de registro do servidor: " + clientDevice);
                clientDevice.isRegistered = true;
                final EasyP2pDevice finalDevice = clientDevice; //Allows us to get around having to add the final modifier earlier.
                if (easyP2pInstance.registeredClients.isEmpty()) {
                    easyP2pInstance.startListeningForData();
                }
                easyP2pInstance.registeredClients.add(clientDevice);

                if (easyP2pInstance.onDeviceRegisteredWithHost != null) {
                    easyP2pInstance.dataReceiver.activity.runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            easyP2pInstance.onDeviceRegisteredWithHost.call(finalDevice);
                        }
                    });
                }

            } else {
                Log.d(EasyP2p.TAG, "\nSolicitação recebida para cancelar o registro do dispositivo.\n");

                Log.v(EasyP2p.TAG, "Enviando código de registro...");
                DeviceInfo deviceInfo = new DeviceInfo();
                deviceInfo.message = EasyP2p.UNREGISTER_CODE;
                String serializedMessage = LoganSquare.serialize(deviceInfo);

                toClient.write(serializedMessage.getBytes(Charsets.UTF_8));
                toClient.flush();

                for (EasyP2pDevice registered : easyP2pInstance.registeredClients) {
                    if (registered.serviceAddress.equals(clientSocket.getInetAddress().toString().replace("/", ""))) {
                        easyP2pInstance.registeredClients.remove(registered);

                        final EasyP2pDevice finalDevice = registered;
                        if (easyP2pInstance.onDeviceUnregisteredWithHost != null) {
                            easyP2pInstance.dataReceiver.activity.runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    easyP2pInstance.onDeviceUnregisteredWithHost.call(finalDevice);
                                }
                            });
                        }
                        Log.d(EasyP2p.TAG, "\nRemovido o registro do dispositivo com sucesso.\n");
                    }
                }
            }

            toClient.close();

        } catch (Exception ex) {
            ex.printStackTrace();
            Log.e(EasyP2p.TAG, "Ocorreu um erro ao lidar com o registro de um cliente.");
        } finally {
            try {
                clientSocket.close();
            } catch (Exception ex) {
                Log.e(EasyP2p.TAG, "Falha ao fechar o socket de registro.");
            }
        }
    }
}
