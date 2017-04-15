package com.ffb.pedrosilveira.easyp2p;

import android.support.annotation.Nullable;
import android.util.Log;

import com.arasthel.asyncjob.AsyncJob;
import com.bluelinelabs.logansquare.LoganSquare;
import com.ffb.pedrosilveira.easyp2p.callbacks.EasyP2pCallback;

import org.apache.commons.io.Charsets;

import java.io.BufferedOutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;

@SuppressWarnings({"WeakerAccess", "FieldCanBeLocal"})
public class BackgroundDataSendJob implements AsyncJob.OnBackgroundJob {

    private final int BUFFER_SIZE = 65536;
    private EasyP2p easyP2pInstance;
    private Object data;
    private EasyP2pCallback onSuccess;
    private EasyP2pCallback onFailure;
    private EasyP2pDevice device;

    public BackgroundDataSendJob(EasyP2pDevice device, EasyP2p easyP2pInstance, Object data, @Nullable EasyP2pCallback onSuccess, @Nullable EasyP2pCallback onFailure) {
        this.data = data;
        this.device = device;
        this.easyP2pInstance = easyP2pInstance;
        this.onSuccess = onSuccess;
        this.onFailure = onFailure;
    }

    @Override
    public void doOnBackground() {

        if (device.serviceAddress != null) {
            Log.d(EasyP2p.TAG, "\nTentando enviar dados para um dispositivo.");
            Socket dataSocket = new Socket();

            try {
                dataSocket.connect(new InetSocketAddress(device.serviceAddress, device.servicePort));
                dataSocket.setReceiveBufferSize(BUFFER_SIZE);
                dataSocket.setSendBufferSize(BUFFER_SIZE);

                //If this code is reached, a client has connected and transferred data.
                Log.d(EasyP2p.TAG, "Conectado, transferindo dados...");
                BufferedOutputStream dataStreamToOtherDevice = new BufferedOutputStream(dataSocket.getOutputStream());

                String dataToSend = LoganSquare.serialize(data);

                dataStreamToOtherDevice.write(dataToSend.getBytes(Charsets.UTF_8));
                dataStreamToOtherDevice.flush();
                dataStreamToOtherDevice.close();

                Log.d(EasyP2p.TAG, "Dados enviados com êxito.");
                if (onSuccess != null)
                    onSuccess.call();

            } catch (Exception ex) {
                Log.d(EasyP2p.TAG, "Ocorreu um erro ao enviar dados para um dispositivo.");
                if (onFailure != null)
                    onFailure.call();
                ex.printStackTrace();
            } finally {
                try {
                    dataSocket.close();
                } catch (Exception ex) {
                    Log.e(EasyP2p.TAG, "Falha ao fechar o socket de dados.");
                }

            }
        } else {
            Log.d(EasyP2p.TAG, "SERVICE ADDRES NULL: " + device.readableName);
        }
    }
}
