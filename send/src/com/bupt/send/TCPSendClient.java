package com.bupt.send;

import com.bupt.Util.Contans;
import com.bupt.Util.ExcelUtil;
import com.bupt.Util.LogUtil;
import com.bupt.Util.SocketUtil;
import com.bupt.model.ExcelModel;
import com.bupt.model.Packet;
import com.sun.xml.internal.bind.v2.runtime.reflect.Lister;

import java.util.*;
import java.util.concurrent.*;

//TCP发送的宿主机
//这里窗口大小的单位是MSS，即路由所能接收的最大报文段大小
public class TCPSendClient {
    //维护拥塞窗口的大小
    public volatile  int cwd;
    //维护拥塞窗口大小，浮点
    public volatile double cwdDouble;
    //拥塞窗口小数部分
    public volatile double cwd_dot = 0.0;
    //传输文件的上限
    public int totalCount;
    //拥塞窗口的起始坐标点
    public volatile int start;
    //拥塞窗口中，已发送但是尚未确认的字节
    public volatile int left;
    // 拥塞窗口的终点坐标
    public  volatile  int end;

    //阻塞队列中发送出去的数据包的数量
    public static int sendCount = 0;
    //接收ack数据包的数量
    public static int ackCount = 0;
    //线程添加到阻塞队列中数据包的数量
    public static int sendTotal = 0;

    //管理发送数据包的延迟队列
    DelayQueue<Packet> delayQueue = new DelayQueue<Packet>();

    //发送数据包的线程池,保证数据包是顺序出去的。
    ExecutorService  exector = Executors.newSingleThreadExecutor();


    /**
     * 慢启动阈值
     */
     public int slow_strench = Integer.MAX_VALUE;


    //当前收到的ack次数
    int count = 0;
    //记录拥塞窗口,时间，以及对应的ack次数
    ExcelModel excelModel = new ExcelModel();
    //记录对应ack报文的map
    Map<String, Integer> ackMap = new HashMap<>();

    public static TCPSendClient instance = new TCPSendClient();

    public static TCPSendClient getInstance(){
        return instance;
    }
    //构造方法
    private TCPSendClient(){
    }

    //参数的初始化
    public void init(){
        cwd= 1;
        totalCount = Contans.totalCount;
        start = cwd;
        left = start;
        end  =  start + cwd;
        SocketUtil.initClientSocket();
        //循环发包
        loopDelayQueue();
        //开始通信
        LogUtil.sendLog("TCP流加入流量竞争");
        startSend();
        //日志信息
        sendLog();
        //开启一个接受线程
        startRec();
    }

    //从延迟队列不间断拿包发送
    public void loopDelayQueue(){
        new Thread(new Runnable() {
            @Override
            public void run() {
                while(true){
                    Packet packet =null;
                    try {
                       packet = delayQueue.poll();
                    }catch (Exception e){
                        e.printStackTrace();
                    }
                    if(packet == null){
                        continue;
                    }
                    final Packet pk = packet;
                    Future<Boolean> task =exector.submit(new Callable<Boolean>() {
                        @Override
                        public Boolean call() {
                            LogUtil.writerToFile(1,"data:"+pk.toString());
                            sendCount ++;
                            return SocketUtil.setPacketInForwardLink(pk);
                        }
                    });
                }
            }
        }).start();
    }

    public void sendLog(){
        new Timer().schedule(new TimerTask() {
            @Override
            public void run() {
                System.out.println("left = "+left+",cwd = "+cwd +",cwd_dot=" +cwd_dot+",start = "+ start + ",end = " +end);
                excelModel.add(cwdDouble,System.currentTimeMillis(),sendCount);
            }
        },0L,300L);
        new Timer().schedule(new TimerTask() {
            @Override
            public void run() {
                System.out.println("sendCount = "+sendCount+",ackCount = "+ackCount +",sendTotal="+sendTotal);
            }
        },3000L,3000L);
    }


    //发送数据包的程度启动一个线程来控制
    public void startSend(){
        new Thread(new Runnable() {
            @Override
            public void run() {
                System.out.println("不断发送数据的线程");
                while(true){
                    for(int i = left; i <=end;i++){
                        final int data_num = i;
                        //延迟队列中添加任务
                        Packet packet = new Packet(true, data_num, System.currentTimeMillis(), Contans.forwardLinkDelay);
                        delayQueue.put(packet);
                        sendTotal++;
                        left++;
                    }
                    if(start >= totalCount){
                        System.out.println("会话结束");
                        LogUtil.sendLog("会话结束");
                        process();
                        ExcelUtil.writeExcel(excelModel.resultList,"C:\\Users\\Administrator\\Desktop\\hello.xls");
                       // System.exit(0);
                    }
                }
            }
        }).start();
    }

    //接收ACK报文的线程,主要在不断的更新窗口
    public void recACK(Packet packet){
        ackCount ++;
        if(!packet.flag){
            Integer ackNum = packet.ack;
            LogUtil.sendLog("收到ACK报文:"+ackNum);
            //统计ack报文的出现次数
            if(null == ackMap.get("ack_"+ackNum)){
                ackMap.put("ack_"+ackNum, 1);
            }else{
                ackMap.put("ack_"+ackNum, ackMap.get("ack_"+ackNum) + 1);
            }
            /**
             * 重复三次ack报文,触发快重传
             * 1.重传缺失报文
             * 2.窗口减半
             */
             if(ackMap.get("ack_"+ackNum) == 3){
                 int repeaatPacket = ackNum + 1;
                 //重传repeatPacket  - left-1之间的所有的报文。
                 for(int i = repeaatPacket ;i<left;i++){
                    Packet reapeat_packet = new Packet(true,i,System.currentTimeMillis(), Contans.forwardLinkDelay);
                    delayQueue.add(reapeat_packet);
                    //  采用作弊的方式直达
                    //Router.strightArrive(reapeat_packet);
                    LogUtil.writerToFile(1,"重传:"+reapeat_packet.toString());
                 }

                 LogUtil.sendLog("快重传报文段"+repeaatPacket);
                 //慢启动阈值窗口大小减半
               //  System.out.println("丢包前cwd="+cwd);
                 slow_strench = cwd/2+2;
                // System.out.println("丢包后慢启动阈值slow_strench="+slow_strench);
                 cwd = slow_strench +3;
                // System.out.println("丢包后cwd="+cwd);
                 //重传之后，重置ack计数器
                 ackMap.put("ack_"+ackNum,-20);
             }

            //更新窗口
            start = ackNum +1;
            //慢启动
            if(cwd <= slow_strench){
               // cwdDouble
                cwdDouble = cwdDouble + 1;
                cwd = (int)cwdDouble;
                //cwd = cwd + 1;
            }
            else{
                LogUtil.sendLog("进入拥塞避免过程");
             /*   //避免过程
                cwd_dot += 1.0/cwd;
              //  System.out.println("cwd = "+cwd +"cwd_dot="+ cwd_dot);
                if((int)cwd_dot > 0){
                    cwd =cwd+(int)cwd_dot;
                    cwd_dot = cwd_dot - (int)cwd_dot;
                }*/
                cwdDouble = cwdDouble + 1/cwdDouble;
                cwd = (int)cwdDouble;
            }
            end = Math.min(start +cwd,totalCount);

           // excelModel.add(cwdDouble,System.currentTimeMillis(),count);
        }else{
            LogUtil.sendLog("收到非法ack报文");
        }
    }


    //对统计的Unix时间做一个简单的处理
    public void process(){
        ArrayList<Long> processList = new ArrayList<Long>();
        for(int i = 0; i< excelModel.resultList.get(1).size(); i++){
            processList.add((Long) excelModel.resultList.get(1).get(i)-(Long) excelModel.resultList.get(1).get(0));
        }
        excelModel.resultList.get(1).clear();
        excelModel.resultList.get(1).addAll(processList);
    }

    //开启一个接受线程
    public void startRec(){
        new Thread(new Runnable() {
            @Override
            public void run() {
                System.out.println("不断接受数据的线程");
                while(true){
                  //  System.out.println("ack");
                    //此处应该是会阻塞线程的
                    Packet ackPacket = SocketUtil.recAck();
                    if(ackPacket != null)
                        recACK(ackPacket);
                }
            }
        }).start();
    }

}
