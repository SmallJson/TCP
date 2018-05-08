package com.bupt;


import com.bupt.send.TCPSendClient;


public class Main {

    public static void main(String[] args) {
        //所有的操纵都在子线程中进行
        TCPSendClient sendClient  = TCPSendClient.getInstance();
        sendClient.init();
        while (true){
            ;
        }
    }
}
