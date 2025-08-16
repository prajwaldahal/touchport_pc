import java.awt.*;
import java.awt.event.InputEvent;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.*;
import java.time.Duration;
import java.time.Instant;

public class RemoteControlServer {
    private static final int TCP_PORT = 12346;
    private static final int UDP_PORT = 12347;

    private ServerSocket serverSocket;
    private DatagramSocket udpSocket;
    private Robot robot;
    private Dimension screenSize;
    private Thread tcpThread;
    private Thread udpThread;
    private volatile boolean running = false;

    private ConnectionListener connectionListener;
    private Instant connectionStartTime;

    public interface ConnectionListener {
        void onClientConnected(Socket clientSocket);
        void onClientDisconnected();
    }

    public void setConnectionListener(ConnectionListener listener) {
        this.connectionListener = listener;
    }

    public void start() throws Exception {
        running = true;

        robot = new Robot();
        screenSize = Toolkit.getDefaultToolkit().getScreenSize();

        udpSocket = new DatagramSocket(null);
        udpSocket.bind(new InetSocketAddress("0.0.0.0", UDP_PORT));

        udpThread = new Thread(() -> {
    byte[] buffer = new byte[1024];
    System.out.println("UDP discovery responder started on port " + UDP_PORT);
    while (running) {
        try {
            DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
            udpSocket.receive(packet);
            String received = new String(packet.getData(), 0, packet.getLength()).trim();
            System.out.println("UDP packet received from " + packet.getAddress().getHostAddress() + ":" + packet.getPort() + " -> " + received);
            
            if (received.equals("DISCOVER_REMOTE_CONTROL_SERVER")) {
                String hostname = InetAddress.getLocalHost().getHostName();
                String response = "DISCOVER_RESPONSE:" + hostname;
                byte[] responseData = response.getBytes();
                DatagramPacket responsePacket = new DatagramPacket(
                        responseData, responseData.length,
                        packet.getAddress(), packet.getPort());
                udpSocket.send(responsePacket);
                System.out.println("Sent response to " + packet.getAddress().getHostAddress() + ":" + packet.getPort() + " -> " + response);
            }
        } catch (IOException e) {
            if (running) {
                System.out.println("UDP error: " + e.getMessage());
            } else {
                System.out.println("UDP socket closed.");
            }
        }
    }
    udpSocket.close();
});
udpThread.start();

        serverSocket = new ServerSocket(TCP_PORT);
        System.out.println("TCP Server listening on port " + TCP_PORT);
        tcpThread = new Thread(() -> {
            while (running) {
                try {
                    try (Socket clientSocket = serverSocket.accept()) {
                        System.out.println("Connected: " + clientSocket.getInetAddress());
                        
                        connectionStartTime = Instant.now();
                        if (connectionListener != null) {
                            connectionListener.onClientConnected(clientSocket);
                        }
                        
                        BufferedReader in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
                        PrintWriter out = new PrintWriter(clientSocket.getOutputStream(), true);
                        
                        out.println("RES:" + screenSize.width + "," + screenSize.height);
                        
                        String line;
                        while ((line = in.readLine()) != null && running) {
                            handleCommand(line);
                        }
                    }
                    System.out.println("Disconnected");

                    if (connectionListener != null) {
                        connectionListener.onClientDisconnected();
                    }
                } catch (IOException e) {
                    System.out.println("Something went wrong: " + e.getMessage());
                }
            }
            try {
                serverSocket.close();
            } catch (IOException e) {
                System.out.println("Something went wrong: " + e.getMessage());

            }
        });
        tcpThread.start();
    }

    private void handleCommand(String line) {
    try {
        if (line.startsWith("MOVE:")) {
            String[] parts = line.substring(5).split(",");
            int dx = Integer.parseInt(parts[0]);
            int dy = Integer.parseInt(parts[1]);
            Point location = MouseInfo.getPointerInfo().getLocation();
            int newX = Math.max(0, Math.min(screenSize.width - 1, location.x + dx));
            int newY = Math.max(0, Math.min(screenSize.height - 1, location.y + dy));
            robot.mouseMove(newX, newY);
        } else if (line.startsWith("CLICK:")) {
            String button = line.substring(6);
            int btn = button.equals("right") ? InputEvent.BUTTON3_DOWN_MASK : InputEvent.BUTTON1_DOWN_MASK;
            robot.mousePress(btn);
            robot.mouseRelease(btn);
        } else if (line.startsWith("DOUBLE:")) {
            String button = line.substring(7);
            int btn = button.equals("left") ? InputEvent.BUTTON1_DOWN_MASK : InputEvent.BUTTON3_DOWN_MASK;
            
            new Thread(() -> {
                try {
                    for (int i = 0; i < 2; i++) {
                        robot.mousePress(btn);
                        robot.mouseRelease(btn);
                        Thread.sleep(100);
                    }
                } catch (InterruptedException e) {
                    System.out.println("Something went wrong: " + e.getMessage());

                }
            }).start();
            
        } else if (line.startsWith("KEY:")) {
            int keyCode = Integer.parseInt(line.substring(4));
            robot.keyPress(keyCode);
            robot.keyRelease(keyCode);
        } else if (line.startsWith("PRESS:")) {
            String button = line.substring(6);
            int btn = button.equals("right") ? InputEvent.BUTTON3_DOWN_MASK : InputEvent.BUTTON1_DOWN_MASK;
            robot.mousePress(btn);
        } else if (line.startsWith("RELEASE:")) {
            String button = line.substring(8);
            int btn = button.equals("right") ? InputEvent.BUTTON3_DOWN_MASK : InputEvent.BUTTON1_DOWN_MASK;
            robot.mouseRelease(btn);
        } else if (line.startsWith("SCROLL:")) {
            String[] parts = line.substring(7).split(",");
            int dy = Integer.parseInt(parts[1]);
            robot.mouseWheel(-dy);
        }
    } catch (HeadlessException | NumberFormatException e) {
        System.err.println("Error handling command: " + line);
    }
}

    public void stop() {
        running = false;
        try {
            if (udpSocket != null && !udpSocket.isClosed()) udpSocket.close();
            if (serverSocket != null && !serverSocket.isClosed()) serverSocket.close();
        } catch (IOException e) {
            System.out.println("Error stopping server: " + e.getMessage());
        }
        if (tcpThread != null) tcpThread.interrupt();
        if (udpThread != null) udpThread.interrupt();
        System.out.println("Server stopped.");
        if (connectionListener != null) {
            connectionListener.onClientDisconnected();
        }
    }

    public Duration getConnectionDuration() {
        if (connectionStartTime == null) return Duration.ZERO;
        return Duration.between(connectionStartTime, Instant.now());
    }
}
