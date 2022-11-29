package site.nethub.base.support;

import java.io.IOException;
import java.net.SocketAddress;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map.Entry;

import site.nethub.base.TcpConnector;
import site.nethub.base.support.DefaultTcpConnector.CacheEvent;

public class DefaultTcpConnector implements TcpConnector, Runnable {
	public class CacheEvent {
		public String s;
		public int ev;
		public byte[] data;

		public CacheEvent(String s, int ev, byte[] data) {
			super();
			this.s = s;
			this.ev = ev;
			this.data = data;
		}
	}

	private static final int DEFAULT_HOST_PORT = 80;
	private static long lastid = 0;
	private Reactor reactor=null;
	public synchronized static long allocId() {
		long t = System.currentTimeMillis() / 86400000 * 86400000 + 1;
		if (lastid < t) {
			return lastid=t;
		} else {
			lastid++;
			return lastid;
		}
	}

	public static SocketAddress hostaddr(String addr) {
		if (addr == null) {
			return null;
		}
		String[] ss = addr.split(":");
		try {
			return new java.net.InetSocketAddress(java.net.InetAddress.getByName(ss[0]),
					ss.length > 1 ? (Integer.parseInt(ss[1])) : DEFAULT_HOST_PORT);
		} catch (NumberFormatException e) {
			e.printStackTrace();
		} catch (UnknownHostException e) {
			e.printStackTrace();
		}
		return null;
	}

	public static TcpConnector newInstance() {
		return new DefaultTcpConnector();
	}

	private HashMap<String, java.nio.channels.SocketChannel> clients = new HashMap<String, java.nio.channels.SocketChannel>();
	private ArrayList<CacheEvent> caches = new ArrayList<CacheEvent>();
	private java.nio.channels.Selector selector = null;
	private byte[] selectwaiter=new byte[0];
	protected DefaultTcpConnector() {

	}

	@Override
	public void close(String s) {
		HashSet<java.nio.channels.SocketChannel> c = new HashSet<java.nio.channels.SocketChannel>();
		synchronized (this) {
			if (s!=null) {
				c.add(clients.get(s));
			} else {
				for (Entry<String, SocketChannel> e : clients.entrySet()) {
					c.add(e.getValue());
				}
			}
		}
		for (SocketChannel channel : c) {
			if (channel != null) {
				try {
					channel.close();
				} catch (Exception e) {
					// e.printStackTrace();
				}
			}
		}
		if(s==null) {
			synchronized (this) {
				this.clients.clear();
				this.caches.clear();
			}
		}
	}

	@Override
	public int write(String s, byte[] data) {
		if(s==null) {
			return -1;
		}
		java.nio.channels.SocketChannel c = null;
		synchronized (this) {
			c = clients.get(s);
		}
		if (c != null) {
			try {
				if(data==null) {
					c.close();
					return 0;
				}else {
					return c.write(ByteBuffer.wrap(data));
				}
			} catch (Exception e) {
				// e.printStackTrace();
				//error auto close;
				fireReactor(s,0,null);
				try {
					c.close();
				}catch(Exception e1) {
					
				}
			}
		}else {
			fireReactor(s,0,null);
		}
		return -1;
	}

	@Override
	public byte[] read(String s) {
		synchronized (this) {
			for (CacheEvent e : caches) {
				if (s.equals(e.s)) {
					caches.remove(e);
					return e.data;
				}
			}
		}
		return new byte[0];
	}

	@Override
	public String ready(long tmout) {
		synchronized (this) {
			if (caches.size() > 0) {
				return caches.get(0).s;
			}
		}
		long etm=System.currentTimeMillis()+tmout;
		while(System.currentTimeMillis()<etm) {
			synchronized(selectwaiter){
				try {
					selectwaiter.wait(tmout);
				} catch (Exception e) {
					//e.printStackTrace();
				}
			}
			// 等待
			synchronized (this) {
				if (caches.size() > 0) {
					return caches.get(0).s;
				}
			}
		}
		return null;
	}

	@Override
	public String connect(String addr) {
		// TODO Auto-generated method stub
		SocketChannel channel = null;
		try {
			channel = java.nio.channels.SocketChannel.open();
			if (channel.connect(hostaddr(addr))) {
				channel.configureBlocking(false);
				synchronized (this) {
					if (selector == null||(!selector.isOpen())) {
						selector = Selector.open();
						channel.register(selector, SelectionKey.OP_READ);
						new Thread(this).start();
					} else {
						channel.register(selector.wakeup(), SelectionKey.OP_READ);
					}
					String cid = socketaddr(channel);
					//channel.write(ByteBuffer.wrap(new byte[] {0x00}));
					fireReactor(cid,1,new byte[0]);
					clients.put(cid, channel);
					return cid;
				}
			}
		} catch (Exception e) {
			//e.printStackTrace();
			if (channel != null) {
				try {
					channel.close();
				} catch (Exception e1) {

				}
			}
		}
		return null;
	}

	private String socketaddr(java.nio.channels.SelectableChannel s) {
		try {
			if(s instanceof java.nio.channels.ServerSocketChannel) {
				return ((ServerSocketChannel)s).getLocalAddress().toString();
			}else if(s instanceof SocketChannel) {
				return ((SocketChannel)s).getRemoteAddress().toString()+"@"
			+((java.net.InetSocketAddress)((SocketChannel)s).getLocalAddress()).getPort();
			}
		}catch(Exception e) {
			
		}
		return null;
	}

	@Override
	public void run() {
		boolean nostop = true;
		ByteBuffer buf=ByteBuffer.allocate(102400);
		try {
			while (nostop) {
				synchronized (this) {
					
				}
				if (0 < (selector.keys().size()>0?selector.select():1)) {
					synchronized (this) {
						ArrayList<SocketChannel> closechannels=new ArrayList<SocketChannel>();
						for(SelectionKey k:selector.selectedKeys()) {
							if(k.isReadable()) {
								SocketChannel channel = ((SocketChannel)k.channel());
								try {
									int rl=channel.read(buf);
									byte[] data=null;
									if(rl>0) {
										buf.rewind();
										data=new byte[rl];
										buf.get(data);
									}else {
										closechannels.add(channel);
									}
									for(Entry<String, SocketChannel> e:this.clients.entrySet()) {
										if(e.getValue().equals(channel)) {
											fireReactor(e.getKey(),data==null?0:2,data);
											break;
										}
									}
								}catch(Exception e1) {
									closechannels.add(channel);
								}
								buf.clear();
							}
						}
						selector.selectedKeys().removeAll(selector.selectedKeys());
						for(SocketChannel c:closechannels) {
							c.close();
						}
					}
					synchronized(selectwaiter) {
						selectwaiter.notifyAll();
					}
				}
				synchronized (this) {
					nostop = (selector.isOpen()) && (this.selector.keys().size()>0);
				}
			}
			for (SelectionKey k : selector.keys()) {
				try {
					k.channel().close();
				} catch (IOException e) {
					// e.printStackTrace();
				}
			}
			selector.close();
		} catch (Exception e) {
			 e.printStackTrace();
		} finally {
			try {
				selector.close();
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}finally {
				selector=null;
			}
		}
	}
	protected void fireReactor(String netid,int ev,byte[] data) {
		Reactor r=null;
		synchronized(this) {
			if(data==null) {
				clients.remove(netid);
			}
			if(this.reactor==null) {
				caches.add(new CacheEvent(netid,ev,data));
				while(caches.size()>MAX_CACHE_SIZE) {
					caches.remove(0);
				}
				return;
			}else {
				r=this.reactor;
			}
		}
		r.onmsg(this, ev, netid, data);
	}
	@Override
	public TcpConnector setReactor(Reactor reactor_) {
		synchronized(this) {
			this.reactor=reactor_;
		}
		return this;
	}
}
