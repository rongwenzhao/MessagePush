package com.nicro.socketclient;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.Socket;

/**
 * Created by rongwenzhao on 2017/12/31.
 */

public class SocketClient {
    public static void main(String[] args) {
        SocketClient socketClient = new SocketClient();
        socketClient.start();
    }

    public void start() {
        BufferedReader inputReader = null;
        BufferedReader reader;
        BufferedWriter writer = null;
        Socket socket = null;
        try {
            socket = new Socket("127.0.0.1", 9898);
            reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));//从服务器获取数据的能力
            writer = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()));
            inputReader = new BufferedReader(new InputStreamReader(System.in));
            startServerReplyListener(reader);
            String inputContent;
            int count = 0;
            while (!(inputContent = inputReader.readLine()).equals("bye")) {
                //writer.write(inputContent + "\n");

                //test no \n begin
                writer.write(inputContent);
                if (count % 2 == 0) {
                    writer.write("\n");
                }

                //test no \n end
                count++;
                writer.flush();
                /*String response = reader.readLine();
                System.out.println(response);*/
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            try {
                inputReader.close();
                writer.close();
                socket.close();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * 监听服务器的返回数据
     */
    public void startServerReplyListener(final BufferedReader reader) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    String response;
                    //该循环会一直阻塞，等待服务器的返回数据
                    while ((response = reader.readLine()) != null) {
                        System.out.println(response);
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }).start();
    }
}
