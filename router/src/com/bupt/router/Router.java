package com.bupt.router;

import com.bupt.Util.LogUtil;
import com.bupt.Util.SocketUtil;
import com.bupt.model.Packet;

import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.*;

/**
 * 利用有界队列的单线程池
 * 模拟路由器排队处理过程
 */
public class Router {
    /**
     * 单一的服务线程，100个阻塞队列长度
     * 如果路由队列中的阻塞包大小长度超过100那么拒绝策略---拥塞丢包
     */
    public  ExecutorService forwardRouter = new ThreadPoolExecutor(1,1,0L
                                            , TimeUnit.MILLISECONDS, new ArrayBlockingQueue<>(1000));
    /**
     * 同上，模拟ack反向链路
     */
    public  ExecutorService returnRouter = new ThreadPoolExecutor(1,1,0L
            , TimeUnit.MILLISECONDS, new ArrayBlockingQueue<>(3000));

    //总共接收的数据报文数量
    public static int totalData = 0;
    //总共接收的ACK报文数量
    public static int totalAck = 0;


    //转发数据报文的数量
    public static int sendCount = 0;
    //转发ACK报文的数量
    public static int ackCount = 0;
    //丢弃的数据报文数量
    public static int lossData = 0;
    //丢弃的ACK报文数量
    public static int lossAck = 0;


    //暴露给外界调用的构造方法，serverSocket不恰当会阻塞线程
    public void init(){
        //1.初始化服务端以及客户端的套接字连接情况
        initClientSocket();
        initServerSocket();
        initThread();
        initLog();
    }
    /**
     * 统计日志行为
     */
    public void initLog(){
        new Timer().schedule(new TimerTask() {
            @Override
            public void run() {
                System.out.println("sendCount="+sendCount+",ackCount=" +ackCount+",lossData="+
                        lossData+",lossAck="+lossAck+",totalData=" + totalData+",totalAck="+totalAck);
            }
        },30000L,3000L);
    }
    /**
     * 初始化发送端和路由器之间套接字
     */
    private void initServerSocket(){
        SocketUtil.initServerSocket();
    }
    /**
     * 初始化路由器和接收端之间的套接字
     */
    private void initClientSocket(){
        SocketUtil.initClientSocket();
    }

    private  void initThread(){
        //2.开启接收线程，循环从套接字中拿信息
        //2.1 开启发送端接收线程，从发送端那报文，然后交付线程池，线程池交付给接收端
        new Thread(new Runnable() {
            @Override
            public void run() {
                System.out.println("从发送端拿数据的线程");
                while(true){
                //    System.out.println("send");
                    Packet packet = SocketUtil.getPacketFromSource();
                    if(null != packet)
                        sendPacketInForwardLink(packet);
                }
            }
        }).start();
        //2.2 开启接收端接收线程，从接收端拿报文，然后交付线程池，线程池交付给发送端
        new Thread(new Runnable() {
            @Override
            public void run() {
                System.out.println("从接受端拿数据的线程");
                while(true){
             //       System.out.println("rec");
                    Packet packet = SocketUtil.getPacketFromDest();
                    if(packet != null){
                        forwardRouter.execute(new Runnable() {
                            @Override
                            public void run() {
                                sendPacketInReturnLink(packet);
                            }
                        });
                    }
                }
            }
        }).start();
    }

    /**
     * 前向数据链路发送数据包
     * @return fasle 代表，路由器已经拥塞丢包
     *          true  代表，路由器成功发包
     */
    public  boolean sendPacketInForwardLink(Packet packet){
        boolean result = true;
        totalData++;
        try {
            forwardRouter.execute(new Runnable() {
                @Override
                public void run() {
                    try {
                        //此处模仿数据包的解包，重发包,持续时间大概为100ms
                        Thread.sleep(15);
                        SocketUtil.sendPacketToDest(packet);
                        System.out.println(packet.toString());
                        sendCount++;
                    }catch (Exception e){
                        e.printStackTrace();
                    }
                }
            });
        }catch (RejectedExecutionException e){
            //线程池如果发生拒绝策略了，那么发生拥塞丢包
            System.out.println("拥塞丢包:"+packet.toString());
            //LogUtil.router("拥塞丢包:"+packet.seq);
            LogUtil.writerToFile(2,"routeloss:"+packet.toString());
            result = false;
            lossData++;
        }
        return result;
    }

    public  boolean sendPacketInReturnLink(Packet packet){
        boolean result = true;
        totalAck++;
        System.out.println(packet.toString());
        try {
            returnRouter.execute(new Runnable() {
                @Override
                public void run() {
                    try {
                        //此处模仿数据包的解包，重发包,持续时间大概为100ms
                        //targetClient.arrivePacket(packet);
                        SocketUtil.sendPacketToSource(packet);
                        System.out.println(packet.toString());
                        ackCount++;
                    }catch (Exception e){
                        e.printStackTrace();
                    }
                }
            });
        }catch (RejectedExecutionException e){
            //线程池如果发生拒绝策略了，那么发生拥塞丢包
            System.out.println("ACK拥塞丢包");
            result = false;
            lossAck++;
        }
        return result;
    }

}
