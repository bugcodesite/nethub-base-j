# nethub-base-j
//a helper for tcp socket work

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
