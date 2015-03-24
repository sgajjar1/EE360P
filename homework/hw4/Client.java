import java.util.*; import java.net.*; import java.io.*;

public class Client {

	public static void main(String[] args) {
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
					
					/* we're going to use one Interface, which supports both UDP and TCP */
					Closeable client = null;
					Scanner netInput;
					
					try {
						/* handle TCP connection to server */
						Socket tcpClient = new Socket(addr, serverPort);
						client = tcpClient;
						tcpClient.setSoTimeout(2000);

						PrintWriter netOut = new PrintWriter(tcpClient.getOutputStream());
						netInput = new Scanner(tcpClient.getInputStream());

						netOut.println(msg);
						netOut.flush();

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

}
