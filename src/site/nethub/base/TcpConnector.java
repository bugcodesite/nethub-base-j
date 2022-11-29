package site.nethub.base;

import site.nethub.base.support.DefaultTcpConnector;

public interface TcpConnector {
	public static final int MAX_CACHE_SIZE = 102400;

	public interface Reactor {
		public void onmsg(Object owner, int ev, String netid, byte[] data);
	}
 
	public static TcpConnector create() {
		return DefaultTcpConnector.newInstance();
	}

	public abstract void close(String s);

	public abstract int write(String s, byte[] data);

	public abstract byte[] read(String s);

	public abstract String ready(long tmout);

	public abstract String connect(String addr);

	public abstract TcpConnector setReactor(Reactor reactor);
}
