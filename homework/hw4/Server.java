import java.util.concurrent.*; import java.util.*; import java.net.*; import java.io.*;

public class Server {
	private static int CAPACITY;

	public static void main(String[] args) {
		try {testConnectivity();} catch (Exception e) {}
		/* 
		 * Store books in a HashMap of <Book, Client>
		 * Book to Client is one-to-one. Client to Book is one-to-many.
		 */
		final ConcurrentHashMap<Integer, Integer> store = new ConcurrentHashMap<Integer, Integer>();

		Scanner in = new Scanner(System.in);

		/* initialize server */
		CAPACITY = in.nextInt();
		int udpPort = in.nextInt();
		int tcpPort = in.nextInt();
		in.close();

		/* set up TCP thread using HandleConnection */
		Thread tcpThread = new Thread(new Runnable() {
			public void run() {
				ServerSocket server = null;
				try {
					server = new ServerSocket(tcpPort);
					Socket s;
					while ((s = server.accept()) != null) {
						/* TCP requires a socket (which gives two IO streams) */
						Thread t = new HandleConnection(store, s);
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

		/* set up UDP thread using HandleConnection */
		Thread udpThread = new Thread(new Runnable() {
			public void run() {
				DatagramSocket server = null;
				try {
					server = new DatagramSocket(udpPort);
					while (true) {
						/* UDP requires a socket and packet */
						DatagramPacket p = new DatagramPacket(new byte[1024], 1024);
						server.receive(p);

						Thread t = new HandleConnection(store, server, p);
						t.start();
					}
				} catch (Exception e) {} finally {
					if (server != null) {
						server.close();
					}
				}
			}
		});

		tcpThread.start();
		udpThread.start();
	}

	public static class HandleConnection extends Thread {

		private ConcurrentHashMap<Integer, Integer> store;
		private Socket tcpServer = null;
		
		/* extra info needed for UDP packets */
		private DatagramSocket udpServer = null;
		private SocketAddress udpAddr = null;
		private byte[] udpData = null;

		public HandleConnection(ConcurrentHashMap<Integer, Integer> bookstore, Socket server) {
			store = bookstore;
			tcpServer = server;
		}

		public HandleConnection(ConcurrentHashMap<Integer, Integer> bookstore,
				DatagramSocket server,
				DatagramPacket packet) {
			store = bookstore;
			udpServer = server;

			/* don't bother storing Datagram packet, we can just harvest important info */
			udpAddr = packet.getSocketAddress();
			udpData = packet.getData();
		}

		public void run() {
			try {
				Scanner netInput;
				OutputStream os;
				if (tcpServer != null) {
					netInput = new Scanner(tcpServer.getInputStream());
					os = tcpServer.getOutputStream();
				} else {
					InputStream is = new ByteArrayInputStream(udpData);
					netInput = new Scanner(is);
					os = new ByteArrayOutputStream();
				}
				PrintWriter netOut = new PrintWriter(os);

				int clientId = netInput.nextInt();
				int bookId = netInput.nextInt();
				String command = netInput.next();

				if (command.equals("reserve")) {
					/* handle reserve command */
					if (bookId >= 1 && bookId <= CAPACITY &&
							store.putIfAbsent(bookId, clientId) == null) {
						netOut.println(clientId + " " + bookId);
					} else {
						netOut.println("fail " + clientId + " " + bookId);
					}
				} else if (command.equals("return")) {
					/* handle return command */
					if (bookId >= 1 && bookId <= CAPACITY &&
							store.remove(bookId, clientId)) {
						netOut.println("free " + clientId + " " + bookId);
					} else {
						netOut.println("fail " + clientId + " " + bookId);
					}
				}

				/* always flush your output streams */
				netOut.flush();
				if (udpServer != null) {
					/* UDP requires explicitly sending the output stream as a byte[] */
					byte[] tmp = ((ByteArrayOutputStream) os).toByteArray();
					DatagramPacket p = new DatagramPacket(tmp, tmp.length, udpAddr);
					udpServer.send(p);
				}

				/* close all streams */
				netOut.close();
				netInput.close();
			} catch (Exception e) {} finally {
				/*
				 * Should have mirrored client implementation and used a Closeable interface.
				 * This code works perfectly fine, it's just inelegant.
				 */
				if (tcpServer != null) {
					try {
						tcpServer.close();
					} catch (IOException e) { }
				}
			}
		}
	}
	

	/*
	 * This method attempts to test network connectivity and tries sending a packet.
	 * Should be removed for submission.
	 */
	public static void testConnectivity() throws Exception {
		InetAddress addr = InetAddress.getByName("lf.lc"); /* apartment server */
		Socket client = new Socket(addr, 7776);
		client.setSoTimeout(1000);

		PrintWriter netOut = new PrintWriter(client.getOutputStream());
		/* some nice test data, helpful for debugging */
		netOut.print("Server: " + Math.random());
		netOut.print("\tSystem time: " + System.currentTimeMillis());
		netOut.println("\tLocal address: " + client.getLocalSocketAddress());
		netOut.flush();
		client.close();
	}

}
