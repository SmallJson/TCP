package com.bupt.Util;

import com.bupt.model.Packet;
import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;

public class SocketUtil {

    //接收端的socket以及对应的TCP,IO流信息
    public static Socket recSocket;
   // public static ObjectOutputStream obRecWriter;
   // public static ObjectInputStream obRecReader;
    public static BufferedReader recReader;
    public static BufferedWriter recWriter;


    //路由器服务端的socket
    public static ServerSocket routerSocket;
    //发送端的socket地址
    public static Socket sendSocket;
    public static ObjectOutputStream obSendWriter;
    public static ObjectInputStream obSendReader;
    public static BufferedWriter sendWriter;
    public static BufferedReader sendReader;

    //转发报文给接收端
    public static boolean sendPacketToDest(Packet packet){
        Boolean result = true;
        try{
           // obRecWriter.writeObject(packet);
            recWriter.write(JacksonUtil.writeValueAsString(packet)+"\r\n");
            recWriter.flush();
        } catch (Exception e){
            System.out.println("路由器转发数据报文失败");
            e.printStackTrace();
            result = false;
        }
        return result;
    }

    //从接收端获取报文
    public static Packet getPacketFromDest(){
        Packet packet = null;
        try {
            //packet = (Packet) obRecReader.readObject();
            packet = JacksonUtil.readValue(recReader.readLine(),Packet.class);
        }catch (Exception e){
            e.printStackTrace();
        }
        return  packet;
    }

    //转发报文给发送端
    public static void sendPacketToSource(Packet packet){
        if(null  !=  sendSocket && null != sendWriter){
            try {
                //obSendWriter.writeObject(packet);
                sendWriter.write(JacksonUtil.writeValueAsString(packet)+"\r\n");
                sendWriter.flush();
            }catch (Exception e){
                e.printStackTrace();
                System.out.println("路由器监听端口失败2");
            }
        }else{
            System.out.println("路由器监听端口失败3");
        }
    }
    //从发送端读取报文
    public static Packet getPacketFromSource(){
        Packet packet = null;
        try {
            packet = JacksonUtil.readValue(sendReader.readLine(),Packet.class);
        }catch (Exception e){
            e.printStackTrace();
        }
        return  packet;
    }

    //
    public  static void initServerSocket(){
        //路由器开始监听
        System.out.println("路由器开始监听");
        try {
            routerSocket = new ServerSocket(Contans.routePort);
            sendSocket = routerSocket.accept();
            InputStream inputStream = sendSocket.getInputStream();
            OutputStream outputStream = sendSocket.getOutputStream();
            sendReader = new BufferedReader(new InputStreamReader(inputStream));
            sendWriter = new BufferedWriter(new OutputStreamWriter(outputStream));

          //  obSendReader = new ObjectInputStream(inputStream);
          //  obSendWriter = new ObjectOutputStream(outputStream);
            System.out.println("路由器和发送端对接成功");
        }catch (Exception e){
            e.printStackTrace();
            System.out.println("路由器监听端口失败1");
        }
    }

    public static void initClientSocket(){
        //启动通往目的地址的连接
        System.out.println("开始连接接收端");
        try {
            //连接目的地址的socket
            recSocket = new Socket(Contans.recIP, Contans.recPort);
            InputStream inputStream = recSocket.getInputStream();
            OutputStream outputStream = recSocket.getOutputStream();
            recReader = new BufferedReader(new InputStreamReader(inputStream));
            recWriter = new BufferedWriter(new OutputStreamWriter(outputStream));

            //obRecReader = new ObjectInputStream(inputStream);
          //  obRecWriter = new ObjectOutputStream(outputStream);
        }catch (IOException e){
            e.printStackTrace();
        }
    }
}
