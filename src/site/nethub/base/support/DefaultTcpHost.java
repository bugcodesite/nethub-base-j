package site.nethub.base.support;

import java.net.SocketAddress;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectableChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map.Entry;

import site.nethub.base.TcpHost;

import java.util.UUID;

public class DefaultTcpHost implements TcpHost, Runnable {
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
	 
	private static long lastid = 0;

	public synchronized static String allocId() {
		/*long t = System.currentTimeMillis() / 86400000 * 86400000 + 1;
		if (lastid < t) {
			return lastid=t;
		} else {
			lastid++;
			return lastid;
		}*/
		return UUID.randomUUID().toString();
	}

	public static SocketAddress listenaddr(String addr) {
		if (addr == null) {
			return null;
		}
		String[] ss = addr.split(":");
		try {
			return new java.net.InetSocketAddress(java.net.InetAddress.getByName(ss.length>1?ss[0]:"0.0.0.0"),
					ss.length > 1 ? (Integer.parseInt(ss[1])) : Integer.parseInt(ss[0]));
		} catch (NumberFormatException e) {
			e.printStackTrace();
		} catch (UnknownHostException e) {
			e.printStackTrace();
		}
		return null;
	}

	public static DefaultTcpHost newInstance() {
		return new DefaultTcpHost();
	}

	private HashMap<String,Object> insts=new HashMap<String,Object>();
	private HashMap<SelectableChannel, ArrayList<SelectableChannel>> servers=new HashMap<SelectableChannel,ArrayList<SelectableChannel>>();
	private HashMap<String,String> client_servers=new HashMap<String,String>();
	private Selector selector;
	private boolean isrun_=false;
	private ArrayList<CacheEvent> caches = new ArrayList<CacheEvent>();
	private byte[] selectwaiter=new byte[0];
	private Reactor reactor=null;
	@Override
	public String listen(String addr){
		ServerSocketChannel ss=null;
		try{
			ss = java.nio.channels.ServerSocketChannel.open();
			ss.bind(listenaddr(addr));
			synchronized(this) {
				if(selector==null){
					selector=Selector.open();
					ss.configureBlocking(false);
					ss.register(selector, SelectionKey.OP_ACCEPT);
					isrun_=true;
					new Thread(this).start();
				}else {
					ss.register(selector.wakeup(), SelectionKey.OP_ACCEPT);
				}
			}
			String sid=null;
			insts.put(sid=socketaddr(ss), ss);
			return sid;
		}catch(Exception e) {
			if(ss!=null) {
				try {
					ss.close();
				}catch(Exception e1) {
					
				}
			}
		}finally {
		
		}
		return null;
	}
	private static String socketaddr(java.nio.channels.SelectableChannel s) {
		try {
			if(s instanceof java.nio.channels.ServerSocketChannel) {
				return ((ServerSocketChannel)s).getLocalAddress().toString();
			}else if(s instanceof SocketChannel) {
				return ((SocketChannel)s).getRemoteAddress().toString();
			}
		}catch(Exception e) {
			
		}
		return null;
	}

	/**
	 * no listener
	 */
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
	public byte[] read(String addr) {
		synchronized (this) {
			for (CacheEvent e : caches) {
				if (addr.equals(e.s)) {
					caches.remove(e);
					return e.data;
				}
			}
		}
		return null;
	}

	@Override
	public int write(String addr, byte[] data) {
		Object c = null;
		synchronized (this) {
			c = (SocketChannel) insts.get(addr);
		}
		if (c != null) {
			try {
				if(c instanceof SocketChannel) {
					return ((SocketChannel)c).write(ByteBuffer.wrap(data));
				}
			} catch (Exception e) {
				// e.printStackTrace();
			}
		}
		return -1;
	}

	@SuppressWarnings("unlikely-arg-type")
	@Override
	public void close(String addr) {
		HashSet<Object> c = new HashSet<Object>();
		try {
			synchronized (this) {
				if (addr!=null) {
					c.add(insts.get(addr));
				} else {
					isrun_=false;
	
					if(selector!=null) {
						selector.wakeup();
					}
					return;
				}
			}
		}catch(Exception e) {
			e.printStackTrace();
		}
		for(Object o:c) {
			if(o instanceof ServerSocketChannel) {
				ServerSocketChannel ss = (ServerSocketChannel)o;
				synchronized (this) {
					//insts.remove(o);
					if(this.servers.containsKey(ss)) {
						for(SelectableChannel s1:this.servers.get(ss)) {
							try {
								s1.close();
							} catch (Exception e) {
								//e.printStackTrace();
							}
						}
						try {
							ss.close();
						} catch (Exception e) {
							//e.printStackTrace();
						}
						this.servers.remove(addr);
						
					}
					this.insts.remove(addr);
					selector.wakeup();
				}
			}else if(o instanceof SocketChannel) {
				SocketChannel sc = (SocketChannel)o;
				try {
					sc.close();
				} catch (Exception e) {
					e.printStackTrace();
				}
				this.insts.remove(addr);
			}
		}
	}

	@Override
	public void run() {
		ByteBuffer buf=ByteBuffer.allocate(MAX_READ_BUF_SIZE);
		while(isrun()) {
			try {
				if(0<selector.select()) {
					HashSet<SelectionKey> ks = new HashSet<SelectionKey>(selector.selectedKeys());
					selector.selectedKeys().removeAll(ks);
					for(SelectionKey k:ks) {
						if(k.isAcceptable()) {
							//doaccept(k);
							SocketChannel c = ((ServerSocketChannel)k.channel()).accept();
							if(onaccept(k.channel(),c)) {
								try {
									c.configureBlocking(false);
									synchronized (this) {
										c.register(selector.wakeup(), SelectionKey.OP_READ);
									}
								}catch(Exception e1) {
									
								}
							}else {
								c.close();
							}
						}else if(k.isReadable()) {
							//if(!doread(k)) {
							//	doclose(k);
							//}
							try {
								int rl=((SocketChannel)k.channel()).read(buf);
								if(rl>0) {
									buf.rewind();
									onrecv(k.channel(),buf,rl);
								}else {
									try {
										onclose(k.channel());
										k.channel().close();
									}catch(Exception e2) {
										
									}
								}
							}catch(Exception e1) {
								try {
									onclose(k.channel());
									k.channel().close();
								}catch(Exception e2) {
									
								}
							}finally {
								buf.clear();
							}
						}
					}
					synchronized(selectwaiter) {
						selectwaiter.notifyAll();
					}
				}else if(selector.keys().size()<=0){
					break;
				}
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
		for(SelectionKey k:selector.keys()) {
			if(!(k.channel() instanceof ServerSocketChannel)) {
				try {
					k.channel().close();
				} catch (Exception e) {
					//e.printStackTrace();
				}
			}
		}
		for(SelectionKey k:selector.keys()) {
			if((k.channel() instanceof ServerSocketChannel)) {
				try {
					k.channel().close();
				} catch (Exception e) {
					//e.printStackTrace();
				}
			}
		}
		synchronized (this) {
			try {
				selector.close();
			} catch (Exception e) {
				//e.printStackTrace();
			}
			isrun_=false;
			selector=null;
		}
	}
	private boolean onaccept(SelectableChannel channel, SocketChannel c) {
		String listenerid=getIdByChannel(channel);
		synchronized (this) {
			if(channel instanceof ServerSocketChannel) {
				ArrayList<SelectableChannel> l=this.servers.get(channel);
				if(l==null) {
					this.servers.put(channel, l=new ArrayList<SelectableChannel>());
				}
				l.add(c);
				String sid=this.socketaddr(c);
				if(listenerid!=null) {
					this.insts.put(sid,c);
					this.client_servers.put(sid, listenerid);
					this.fireReactor(sid,1,new byte[0]);
					return true;
				}
			}
		}
		return false;
	}
	private String getIdByChannel(SelectableChannel channel) {
		synchronized (this) {
			for(Entry<String, Object> e:this.insts.entrySet()) {
				if(channel.equals(e.getValue())) {
					return e.getKey();
				}
			}
		}
		return null;
	}

	private void onclose(SelectableChannel channel) {
		String sid=getIdByChannel(channel);
		if(sid==null) {
			return;
		}
		synchronized (this) {
			this.insts.remove(sid);
			String serverid=this.client_servers.remove(sid);
			if(this.servers.containsKey(serverid)) {
				try {
					this.servers.get(serverid).remove(channel);
				}catch(Exception e) {
					//e.printStackTrace();
				}
			}
			this.fireReactor(sid,0,null);
		}
		
	}

	private void onrecv(SelectableChannel channel, ByteBuffer buf, int rl) {
		byte[] data=new byte[rl];
		buf.get(data);
		String sid=this.getIdByChannel(channel);
		fireReactor(sid,data==null?0:2,data);
	}



	@Override
	public boolean isrun() {
		synchronized (this) {
			return this.isrun_;
		}
	}

	@Override
	public SelectableChannel findChannel(String addr) {
		try {
			synchronized (this) {
				return (SelectableChannel) insts.get(addr);
			}
		}catch(Exception e) {
			
		}
		return null;
	}
	protected void fireReactor(String netid,int ev,byte[] data) {
		Reactor r=null;
		synchronized(this) {
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
	public TcpHost setReactor(Reactor reactor_) {
		synchronized(this) {
			this.reactor=reactor_;
		}
		return this;
	}
}
