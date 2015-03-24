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
		CAPACITY = in.nextInt();
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
		tcpThread.start();
	}

	public static class Clock {
		private int m, id;
		private int[] v;

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
		/* send some messages */
		/* using server Clock */
	}

	public static class HandleConnection extends Thread {

		private ConcurrentHashMap<Integer, Integer> store;
		private Socket tcpServer = null;

		public HandleConnection(ConcurrentHashMap<Integer, Integer> bookstore, Socket server) {
			store = bookstore;
			tcpServer = server;
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
