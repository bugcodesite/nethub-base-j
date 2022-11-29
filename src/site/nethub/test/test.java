package site.nethub.test;
import site.nethub.base.TcpConnector;
public class test{
    public static void main(String...args){
        System.out.println("start");
        long starttm=System.currentTimeMillis();
        TcpConnector cc=TcpConnector.create();
        String nid=cc.connect("www.baidu.com:80");
        if(nid!=null){
            while(System.currentTimeMillis()-starttm<1000L*60*1){
                nid=cc.ready(1000);
                if(nid!=null){
                    byte[] data=cc.read(nid);
                    if(data==null){
                        //connection close
                    }else if(data.length==0){
                        //open,or no new data
                    }else{
                        //recive data
                    }
                }
            }
        }else{
            System.out.println("connect failed");
        }
        cc.close(null/*close all connection */);
        System.out.println("stop");
    }
}