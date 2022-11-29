package site.nethub.base;

import java.nio.channels.SelectableChannel;

import site.nethub.base.support.DefaultTcpHost;

public interface TcpHost {
	public static final int MAX_CACHE_SIZE = 102400;
	public static final int MAX_READ_BUF_SIZE = 102400;
	public interface Reactor{
		public void onmsg(Object owner,int ev,String netid,byte[] data);
	} 
	public static TcpHost create() {
		return DefaultTcpHost.newInstance();
	}

	public SelectableChannel findChannel(String addr);

	public String listen(String addr);

	public String ready(long tmout);

	public byte[] read(String addr);

	public int write(String addr, byte[] data);

	public void close(String addr);

	boolean isrun();

	public TcpHost setReactor(Reactor reactor_);
}
