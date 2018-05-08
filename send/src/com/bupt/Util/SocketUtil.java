package com.bupt.Util;

import com.bupt.model.Packet;
import java.io.*;
import java.net.Socket;

public class SocketUtil {

    public static Socket routerSocket;
    public static ObjectOutputStream obRouterWriter;
    public static ObjectInputStream obRouterReader;
    public static BufferedReader routerReader;
    public static BufferedWriter routerWriter;

    public static void initClientSocket(){
        //启动通往路由器的连
        System.out.println("客户端准备连接路由器");
        try {
            routerSocket = new Socket(Contans.routeIP,Contans.routePort);
            InputStream inputStream = routerSocket.getInputStream();
            OutputStream outputStream = routerSocket.getOutputStream();
            routerReader = new BufferedReader(new InputStreamReader(inputStream));
            routerWriter = new BufferedWriter(new OutputStreamWriter(outputStream));
            //obRouterReader = new ObjectInputStream(inputStream);
            //obRouterWriter = new ObjectOutputStream(outputStream);
            System.out.println("客户端成功连接路由器");
        }catch (IOException e){
            e.printStackTrace();
        }
    }


    //给路由器发送一个数据报文
    public static boolean setPacketInForwardLink(Packet packet){
        Boolean result = true;
        try{
            routerWriter.write(JacksonUtil.writeValueAsString(packet)+"\r\n");
            routerWriter.flush();
            System.out.println("发送端:"+packet.toString());
        } catch (Exception e){
            System.out.println("客户端发送数据报文失败");
            e.printStackTrace();
            result = false;
        }
        return result;
    }


    //获取一个ack报文信息
    public static Packet recAck(){
        Packet ackPacket = null;
        try{
           // ackPacket = (Packet) obRouterReader.readObject();
            ackPacket = JacksonUtil.readValue(routerReader.readLine(),Packet.class);
        }catch (Exception e){
            e.printStackTrace();
        }
        return ackPacket;
    }
}
