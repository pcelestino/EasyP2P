package com.ffb.pedrosilveira.easyp2p;

import android.util.Log;

import com.arasthel.asyncjob.AsyncJob;

import org.apache.commons.io.IOUtils;

import java.io.BufferedInputStream;
import java.net.Socket;


@SuppressWarnings("WeakerAccess")
public class BackgroundDataJob implements AsyncJob.OnBackgroundJob {

    private EasyP2p easyP2pInstance;
    private Socket clientSocket;
    private String data;

    public BackgroundDataJob(EasyP2p easyP2pInstance, Socket clientSocket) {
        this.clientSocket = clientSocket;
        this.easyP2pInstance = easyP2pInstance;
    }


    @Override
    public void doOnBackground() {
        try {
            //Se esse código for atingido, um cliente conectou e transferiu dados.
            Log.v(EasyP2p.TAG, "Um dispositivo está enviando dados...");

            BufferedInputStream dataStreamFromOtherDevice = new BufferedInputStream(clientSocket.getInputStream());
            data = new String(IOUtils.toByteArray(dataStreamFromOtherDevice));
            dataStreamFromOtherDevice.close();

            Log.d(EasyP2p.TAG, "\nDados recebidos com êxito.\n");

            if (!data.isEmpty()) {
                easyP2pInstance.dataReceiver.activity.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        easyP2pInstance.dataReceiver.dataCallback.onDataReceived(data);
                    }
                });
            }
        } catch (Exception ex) {
            Log.e(EasyP2p.TAG, "Ocorreu um erro ao tentar receber os dados.");
            ex.printStackTrace();
        } finally {
            try {
                clientSocket.close();
            } catch (Exception ex) {
                Log.e(EasyP2p.TAG, "Falha ao fechar o socket de dados.");
            }
        }
    }
}
