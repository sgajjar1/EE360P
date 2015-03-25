import java.util.concurrent.*; import java.util.*; import java.net.*; import java.io.*;

public class Server {
	private static int CAPACITY;

	public static void main(String[] args) {
		/* 
		 * Store books in a HashMap of <Book, Client>
		 * Book to Client is one-to-one. Client to Book is one-to-many.
		 */
		final ConcurrentHashMap<Integer, Integer> store = new ConcurrentHashMap<Integer, Integer>();

		Scanner in = new Scanner(System.in);

		/* initialize server */
		int serverId = in.nextInt();
		int instances = in.nextInt();
		CAPACITY = in.nextInt();
		in.nextLine(); // Consume newline left-over

		InetSocketAddress[] servers = new InetSocketAddress[instances];

		for (int i = 0; i < instances; i++) {
			String ip_port[] = in.nextLine().trim().split(":");
			String ip = ip_port[0];
			int port = Integer.parseInt(ip_port[1]);
			servers[i] = new InetSocketAddress(ip, port);
		}

		in.close();

		LamportMutex lamport = new LamportMutex(servers, serverId);

		/* set up TCP thread using HandleConnection */
		Thread tcpThread = new Thread(new Runnable() {
			public void run() {
				ServerSocket server = null;
				try {
					server = new ServerSocket(servers[serverId].getPort());
					Socket s;
					while ((s = server.accept()) != null) {
						/* TCP requires a socket (which gives two IO streams) */
						Thread t = new HandleConnection(store, s, lamport);
						t.start();
					}
				} catch (Exception e) { } finally {
					if (server != null) {
						try {
							server.close();
						} catch (IOException e) {}
					}
				}
			}
		});
		tcpThread.start();
	}

	public static class Clock {
		private int m, id;
		public int[] v;

		public Clock(int maxInstances, int self) {
			m = maxInstances;
			id = self;
			v = new int[m];
			for (int i = 0; i < m; i++) {
				v[i] = 0;
			}
			v[id] = 1;
		}

		void update() {
			v[id]++;
		}

		void recieve(int[] r) {
			for (int i = 0; i < m; i++) {
				if (r[i] > v[i]) {
					v[i] = r[i];
				}
			}
			v[id]++;
		}

		int[] send() {
			v[id]++;
			return v;
		}
	}

	public static class LamportMutex {
		private SocketAddress[] servers;
		private PriorityQueue<Request> requests;
		private boolean[] ack;
		private Clock cl;
		private int id;

		public LamportMutex(SocketAddress[] servers, int self) {
			this.servers = servers;
			this.id = self;
			this.ack = new boolean[servers.length];
			this.cl = new Clock(servers.length, id);
		}

		public synchronized void request() {
			/* clear all acks */
			Arrays.fill(ack, false);

			for (int i = 0; i < servers.length; i++) {
				SocketAddress addr = servers[i];
				Socket c = new Socket();
				try {
					c.connect(addr);
					OutputStream os = c.getOutputStream();
					PrintWriter out = new PrintWriter(os);
					out.println(id);
					out.println("0 lamport");
					for (int ts : cl.send()) {
						out.println(ts);
					}
					out.flush();
				} catch (IOException e) {} finally {
					try {
						c.close();
					} catch (IOException e) { }
				}
			}
			try {
				while (canNotEnter()) {
					this.wait();
				}
			} catch (InterruptedException e) { }
		}

		private boolean canNotEnter() {
			if (requests.peek().sid != id) return false;
			for (boolean a : ack) {
				if (!a) return false;
			}
			return true;
		}

		public synchronized void recvAck(int sid) {
			ack[sid] = true;

			this.notify();
		}

		public synchronized void sendAck(int sid) {
			SocketAddress addr = servers[sid];
			Socket c = new Socket();
			try {
				c.connect(addr);
				OutputStream os = c.getOutputStream();
				PrintWriter out = new PrintWriter(os);
				out.println(id);
				out.println("0 ack");
				for (int ts : cl.send()) {
					out.println(ts);
				}
				out.flush();
			} catch (IOException e) {} finally {
				try {
					c.close();
				} catch (IOException e) { }
			}
		}

		public synchronized void receive(int sid, Scanner in) {
			int[] r = new int[servers.length];
			for (int i = 0; i < r.length; i++) {
				r[i] = in.nextInt();
			}
			cl.recieve(r);
			requests.add(new Request(sid, cl.v[sid]));

			this.notify();
		}

		public static class Request implements Comparable<Request> {
			private int sid, ts;
			public Request(int sid, int ts) {
				this.sid = sid;
				this.ts = ts;
			}
			public int compareTo(Request o) {
				if (this.ts == o.ts) {
					return this.sid - o.sid;
				} else {
					return this.ts - o.ts;
				}
			}
		}
	}

	public static class HandleConnection extends Thread {

		private ConcurrentHashMap<Integer, Integer> store;
		private Socket tcpServer = null;
		private LamportMutex mutex;

		public HandleConnection(ConcurrentHashMap<Integer, Integer> bookstore,
				Socket server, LamportMutex lamport) {
			store = bookstore;
			tcpServer = server;
			mutex = lamport;
		}

		public void run() {
			try {
				Scanner netInput = new Scanner(tcpServer.getInputStream());
				OutputStream os = tcpServer.getOutputStream();
				PrintWriter netOut = new PrintWriter(os);

				int clientId = netInput.nextInt();
				int bookId = netInput.nextInt();
				String command = netInput.next();

				if (command.equals("reserve")) {
					/* handle reserve command */
					// TODO: add mutex support
					if (bookId >= 1 && bookId <= CAPACITY &&
							store.putIfAbsent(bookId, clientId) == null) {
						netOut.println(clientId + " " + bookId);
					} else {
						netOut.println("fail " + clientId + " " + bookId);
					}
				} else if (command.equals("return")) {
					/* handle return command */
					// TODO: add mutex support
					if (bookId >= 1 && bookId <= CAPACITY &&
							store.remove(bookId, clientId)) {
						netOut.println("free " + clientId + " " + bookId);
					} else {
						netOut.println("fail " + clientId + " " + bookId);
					}
				} else if (command.equals("lamport")) {
					mutex.receive(clientId, netInput);
				} else if (command.equals("ack")) {
					mutex.recvAck(clientId);
				}

				/* always flush your output streams */
				netOut.flush();

				/* close all streams */
				netOut.close();
				netInput.close();
			} catch (Exception e) {} finally {
				try {
					tcpServer.close();
				} catch (IOException e) { }
			}
		}
	}

}
