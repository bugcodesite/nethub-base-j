# nethub-base-j
//a helper for tcp socket work
# summary
###static create
```
	//create new instance
	TCPConnector c=TCPConnector.create();
	TCPHost server=TCPHost.create();
```
###listen for TCP server,or connect for TCP client
####return instanceid,we call netid
####addr looks like [ip]:[port],127.0.0.1:2000
```
	String listen(addr);
	String connect(addr);
###auto work
```
	...
```
###ready
####@return netid,null for none,String for read
####tm=0,or wait millonsecond
```
	String ready(long t);
```
###read
####@netid:ready return
####@return data,byte[0] for no data,byte[+1] for recived,null for closed
```
	byte[] read(String netid);
```
###write
####@netid,@see ready
####@data,byte[] for send,null for close
```
	byte[] write(String netid);
```
###close
####@netid,null for close all connection and listener,netid for close one socket
```
	void close(String netid);
```

#demo	

	public static void main(String...args) {
			System.out.println("start");
			TcpHost c=TcpHost.create();
			long s0=c.listen("192.168.0.82:1118");
			long s;
			while((s=c.ready(60000))>0) {
				byte[] data=c.read(s);
				c.write(s,data);
	      //...
			}
			c.close(s0);
			System.out.println("stop");
	}
//
