import java.util.*; import java.net.*; import java.io.*;

public class Client {

	public static void main(String[] args) {
		try {testConnectivity();} catch (Exception e) {}
		Scanner stdin = new Scanner(System.in);

		int clientId = stdin.nextInt();

		InetAddress addr = null;
		String host = stdin.nextLine().trim();
		try {
			/* attempt to resolve IP address or hostname */
			addr = InetAddress.getByName(host);
		} catch (UnknownHostException e) {
			System.out.println("Unable to resolve host: " + host);
			System.exit(1);
		}
		
		while (stdin.hasNextLine()) {
			try {
				if (stdin.findInLine("b") != null) {
					/* handle b# command */
					int bookId = stdin.nextInt();
					String command = stdin.next();
					int serverPort = stdin.nextInt();
					String proto = stdin.next();

					/* create book command for server */
					String msg = new String(clientId + " " + bookId + " " + command + "\n");
					
					/* we're going to use one Interface for both UDP and TCP */
					Closeable client = null;
					Scanner netInput;
					
					try {
						if (proto.equals("T")) {
							/* handle TCP connection to server */
							Socket tcpClient = new Socket(addr, serverPort);
							client = tcpClient;
							tcpClient.setSoTimeout(2000);

							PrintWriter netOut = new PrintWriter(tcpClient.getOutputStream());
							netInput = new Scanner(tcpClient.getInputStream());
	
							netOut.println(msg);
							netOut.flush();
						} else if (proto.equals("U")) {
							/* handle UDP connection to server */
							DatagramSocket udpClient = new DatagramSocket();
							client = udpClient;
							udpClient.setSoTimeout(2000);
							
							byte[] sendBuf = msg.getBytes();
							DatagramPacket sendP = new DatagramPacket(sendBuf,
									sendBuf.length, addr, serverPort);
							
							/* ... packets traveling over the Internet here ... */
							
							byte[] recvBuf = new byte[1024];
							DatagramPacket recvP = new DatagramPacket(recvBuf,
									recvBuf.length, addr, serverPort);
							
							udpClient.send(sendP);
							udpClient.receive(recvP);
	
							String received = new String(recvP.getData(), 0, recvP.getLength());
							netInput = new Scanner(received);
						} else {
							continue;
						}
						String smsg;
						if (netInput.findInLine("free") != null) {
							smsg = "free ";
						} else if (netInput.findInLine("fail") != null) {
							smsg = "fail ";
						} else {
							smsg = "";
						}
						int sclient = netInput.nextInt();
						int sbook = netInput.nextInt();

						System.out.println(smsg + "c" + sclient + " b" + sbook);

						netInput.close();
					} catch (Exception e) {
						System.out.println("Networking error.");
					} finally {
						if (client != null) {
							try {
								client.close();
							} catch (IOException e) {
								System.out.println("IO error while closing socket.");
							}
						}
					}
				} else if (stdin.findInLine("sleep") != null) {
					/* handle sleep command */
					int sleep = stdin.nextInt();
					try {
						Thread.sleep(sleep);
					} catch (InterruptedException e) {}
				} else {
					/* chomp chomp, let's grab the next line */
					stdin.nextLine();
				}
			} catch (InputMismatchException e) {
				System.out.println("Input error while reading command.");
				stdin.nextLine();
			}
		}

		stdin.close();
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
		netOut.print("Client: " + Math.random());
		netOut.print("\tSystem time: " + System.currentTimeMillis());
		netOut.println("\tLocal address: " + client.getLocalSocketAddress());
		netOut.flush();
		client.close();
	}

}
